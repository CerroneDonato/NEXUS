"""Merge rules and the one exchange between the phone and the PC.

The phone sends what it changed. This PC applies it (newest edit wins, the PC
wins a tie) and answers with everything it changed after the phone's last
revision. Chat messages are only added. A deleted conversation stays deleted.
"""

from __future__ import annotations

import hashlib
from datetime import datetime
from typing import Any, Callable
from zoneinfo import ZoneInfo

from pydantic import BaseModel, Field

from .db import Database

SETTING_KEYS = ("city", "latitude", "longitude", "assistant_name", "gemini_model")


class SyncTask(BaseModel):
    uid: str
    title: str
    due: str | None = None
    done: bool = False
    createdAt: datetime
    completedAt: datetime | None = None
    updatedAt: datetime
    deleted: bool = False


class SyncMessage(BaseModel):
    uid: str
    conversationId: str
    at: datetime
    role: str
    text: str
    actions: list[str] = Field(default_factory=list)
    updatedAt: datetime
    deleted: bool = False


class SyncSetting(BaseModel):
    key: str
    value: str
    updatedAt: datetime


class DeletedConversation(BaseModel):
    id: str
    updatedAt: datetime


class SyncRequest(BaseModel):
    since: int = 0
    tasks: list[SyncTask] = Field(default_factory=list)
    messages: list[SyncMessage] = Field(default_factory=list)
    deletedConversations: list[DeletedConversation] = Field(default_factory=list)
    settings: list[SyncSetting] = Field(default_factory=list)
    alarm: str | None = None


class SyncResponse(BaseModel):
    revision: int
    tasks: list[SyncTask]
    messages: list[SyncMessage]
    deletedConversations: list[DeletedConversation]
    settings: list[SyncSetting]
    geminiKey: str | None = None
    geminiKeyUpdatedAt: datetime | None = None
    syncedAt: datetime
    version: str = ""


def prefer_incoming(local_at: datetime, incoming_at: datetime, incoming_from_pc: bool) -> bool:
    """True when the incoming edit replaces the local one. Equal times: the PC wins."""
    if incoming_at > local_at:
        return True
    if incoming_at < local_at:
        return False
    return incoming_from_pc


def merge_task(local: SyncTask | None, incoming: SyncTask, incoming_from_pc: bool) -> SyncTask:
    if local is None or prefer_incoming(local.updatedAt, incoming.updatedAt, incoming_from_pc):
        return incoming
    return local


def merge_message(local: SyncMessage | None, incoming: SyncMessage) -> SyncMessage:
    """Chat is append-only: the first copy of a message is the one that stays."""
    return local if local is not None else incoming


class SyncEngine:
    def __init__(self, db: Database, now: Callable[[], datetime], tz: ZoneInfo,
                 city: str, latitude: float, longitude: float, assistant_name: str, gemini_api_key: str,
                 gemini_model: str = ""):
        self._db = db
        self._now = now
        self._tz = tz
        self._env = {
            "city": city,
            "latitude": str(latitude),
            "longitude": str(longitude),
            "assistant_name": assistant_name,
        }
        if gemini_model:
            self._env["gemini_model"] = gemini_model
        self._gemini = gemini_api_key

    def apply(self, request: SyncRequest) -> tuple[SyncResponse, str | None, bool]:
        """Returns the response, the alarm to schedule (or None), and whether the alarm changed."""
        self._adopt_env_edits()
        self._note_gemini_key()
        for item in request.settings:
            if item.key in SETTING_KEYS:
                self._merge_setting(item)
        for item in request.deletedConversations:
            self._merge_tombstone(item)
        echoed_tasks = [self._merge_task(item) for item in request.tasks]
        echoed_messages = [self._merge_message(item) for item in request.messages]
        alarm_changed = self._take_alarm(request.alarm)
        synced_at = self._now()
        self._db.set_value("last_sync_at", synced_at.isoformat(timespec="seconds"))
        response = SyncResponse(
            revision=self._db.revision(),
            tasks=self._tasks_since(request.since, echoed_tasks),
            messages=self._messages_since(request.since, echoed_messages),
            deletedConversations=self._tombstones_since(request.since, request.deletedConversations),
            settings=self._settings(),
            geminiKey=self._gemini or None,
            geminiKeyUpdatedAt=self._parse(self._db.get_value("gemini_key_updated_at")),
            syncedAt=synced_at,
        )
        return response, request.alarm, alarm_changed

    def _aware(self, value: datetime) -> datetime:
        if value.tzinfo is None:
            value = value.replace(tzinfo=self._tz)
        return value.replace(microsecond=0)

    def _parse(self, raw: str | None) -> datetime | None:
        if not raw:
            return None
        return self._aware(datetime.fromisoformat(raw))

    def _stamp(self, value: datetime) -> str:
        return self._aware(value).isoformat(timespec="seconds")

    def _adopt_env_edits(self) -> None:
        """A change in .env is a new edit made on the PC. An unchanged .env leaves a phone edit in place."""
        for key, value in self._env.items():
            seen = self._db.get_value(f"setting:{key}:env")
            if seen is None:
                if self._db.get_value(f"setting:{key}") is None:
                    self._db.set_value(f"setting:{key}", value)
                    self._db.set_value(f"setting:{key}:at", self._now().isoformat(timespec="seconds"))
                self._db.set_value(f"setting:{key}:env", value)
            elif seen != value:
                self._db.set_value(f"setting:{key}", value)
                self._db.set_value(f"setting:{key}:at", self._now().isoformat(timespec="seconds"))
                self._db.set_value(f"setting:{key}:env", value)

    def _note_gemini_key(self) -> None:
        digest = hashlib.sha256(self._gemini.encode()).hexdigest() if self._gemini else ""
        if self._db.get_value("gemini_key_hash") != digest:
            self._db.set_value("gemini_key_hash", digest)
            self._db.set_value("gemini_key_updated_at", self._now().isoformat(timespec="seconds"))

    def _merge_setting(self, item: SyncSetting) -> None:
        current = self._parse(self._db.get_value(f"setting:{item.key}:at"))
        incoming = self._aware(item.updatedAt)
        if current is not None and not prefer_incoming(current, incoming, incoming_from_pc=False):
            return
        self._db.set_value(f"setting:{item.key}", item.value)
        self._db.set_value(f"setting:{item.key}:at", self._stamp(incoming))

    def _merge_tombstone(self, item: DeletedConversation) -> None:
        row = self._db.query(
            "SELECT updated_at FROM conversation_deletions WHERE conversation_id = ?", (item.id,)
        )
        if row:
            current = self._parse(row[0]["updated_at"])
            if current is not None and not prefer_incoming(current, self._aware(item.updatedAt), False):
                return
        from .history import ChatHistory

        ChatHistory(self._db, self._now).tombstone(item.id, self._aware(item.updatedAt))

    def _merge_task(self, incoming: SyncTask) -> SyncTask:
        rows = self._db.query("SELECT * FROM tasks WHERE uid = ?", (incoming.uid,))
        local = self._task_from_row(rows[0]) if rows else None
        winner = merge_task(local, self._normalized_task(incoming), incoming_from_pc=False)
        if local is not None and winner == local:
            return local
        if local is None and winner.deleted:
            self._insert_task(winner)
            return winner
        if local is None:
            self._insert_task(winner)
            return winner
        self._update_task(winner)
        return winner

    def _normalized_task(self, item: SyncTask) -> SyncTask:
        return item.model_copy(update={
            "createdAt": self._aware(item.createdAt),
            "completedAt": self._aware(item.completedAt) if item.completedAt else None,
            "updatedAt": self._aware(item.updatedAt),
        })

    def _insert_task(self, item: SyncTask) -> None:
        rev = self._db.bump()
        self._db.execute(
            "INSERT INTO tasks (title, due, done, created_at, completed_at, uid, updated_at, deleted, rev) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                item.title, item.due, int(item.done), self._stamp(item.createdAt),
                self._stamp(item.completedAt) if item.completedAt else None,
                item.uid, self._stamp(item.updatedAt), int(item.deleted), rev,
            ),
        )

    def _update_task(self, item: SyncTask) -> None:
        rev = self._db.bump()
        self._db.execute(
            "UPDATE tasks SET title = ?, due = ?, done = ?, created_at = ?, completed_at = ?, "
            "updated_at = ?, deleted = ?, rev = ? WHERE uid = ?",
            (
                item.title, item.due, int(item.done), self._stamp(item.createdAt),
                self._stamp(item.completedAt) if item.completedAt else None,
                self._stamp(item.updatedAt), int(item.deleted), rev, item.uid,
            ),
        )

    def _merge_message(self, incoming: SyncMessage) -> SyncMessage:
        if self._db.query(
            "SELECT 1 FROM conversation_deletions WHERE conversation_id = ?", (incoming.conversationId,)
        ):
            return incoming.model_copy(update={"deleted": True})
        rows = self._db.query("SELECT * FROM chat_messages WHERE uid = ?", (incoming.uid,))
        if rows:
            return self._message_from_row(rows[0])
        item = incoming.model_copy(update={
            "at": self._aware(incoming.at),
            "updatedAt": self._aware(incoming.updatedAt),
        })
        rev = self._db.bump()
        self._db.execute(
            "INSERT INTO chat_messages (conversation_id, at, role, text, actions, uid, updated_at, deleted, rev) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                item.conversationId, self._stamp(item.at), item.role, item.text,
                _dump_actions(item.actions), item.uid, self._stamp(item.updatedAt), int(item.deleted), rev,
            ),
        )
        return item

    def _take_alarm(self, alarm: str | None) -> bool:
        previous = self._db.get_value("next_alarm")
        if alarm == previous:
            return False
        if alarm is None and previous is None:
            return False
        return True

    def _tasks_since(self, since: int, echoed: list[SyncTask]) -> list[SyncTask]:
        rows = self._db.query("SELECT * FROM tasks WHERE rev > ?", (since,))
        by_uid = {item.uid: item for item in echoed}
        for row in rows:
            by_uid[row["uid"]] = self._task_from_row(row)
        return list(by_uid.values())

    def _messages_since(self, since: int, echoed: list[SyncMessage]) -> list[SyncMessage]:
        rows = self._db.query("SELECT * FROM chat_messages WHERE rev > ?", (since,))
        by_uid = {item.uid: item for item in echoed}
        for row in rows:
            by_uid[row["uid"]] = self._message_from_row(row)
        return [item for item in by_uid.values() if not self._hidden(item)]

    def _hidden(self, item: SyncMessage) -> bool:
        if item.deleted:
            return True
        return bool(self._db.query(
            "SELECT 1 FROM conversation_deletions WHERE conversation_id = ?", (item.conversationId,)
        ))

    def _tombstones_since(self, since: int, echoed: list[DeletedConversation]) -> list[DeletedConversation]:
        rows = self._db.query("SELECT * FROM conversation_deletions WHERE rev > ?", (since,))
        by_id = {item.id: item for item in echoed}
        for row in rows:
            by_id[row["conversation_id"]] = DeletedConversation(
                id=row["conversation_id"], updatedAt=self._parse(row["updated_at"])
            )
        return list(by_id.values())

    def _settings(self) -> list[SyncSetting]:
        found = []
        for key in SETTING_KEYS:
            value = self._db.get_value(f"setting:{key}")
            stamp = self._parse(self._db.get_value(f"setting:{key}:at"))
            if value is not None and stamp is not None:
                found.append(SyncSetting(key=key, value=value, updatedAt=stamp))
        return found

    def _task_from_row(self, row: Any) -> SyncTask:
        return SyncTask(
            uid=row["uid"],
            title=row["title"],
            due=row["due"],
            done=bool(row["done"]),
            createdAt=self._parse(row["created_at"]),
            completedAt=self._parse(row["completed_at"]),
            updatedAt=self._parse(row["updated_at"] or row["created_at"]),
            deleted=bool(row["deleted"]),
        )

    def _message_from_row(self, row: Any) -> SyncMessage:
        import json

        return SyncMessage(
            uid=row["uid"],
            conversationId=row["conversation_id"],
            at=self._parse(row["at"]),
            role=row["role"],
            text=row["text"],
            actions=json.loads(row["actions"]) if row["actions"] else [],
            updatedAt=self._parse(row["updated_at"] or row["at"]),
            deleted=bool(row["deleted"]),
        )


def _dump_actions(actions: list[str]) -> str | None:
    import json

    return json.dumps(actions) if actions else None
