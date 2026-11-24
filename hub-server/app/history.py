"""Persistent log of the conversations with the assistant, shared by the dashboard and the phone."""

from __future__ import annotations

import json
import re
import uuid
from datetime import datetime, timedelta
from typing import Any, Callable

from .db import Database

KEEP = timedelta(days=180)
TITLE_LENGTH = 60

# Sport mode prefixes its questions with a note for the model, e.g. "(Mi sto allenando ...) ...".
_LEADING_NOTE = re.compile(r"^\s*\([^)]*\)\s*")


def source_of(conversation_id: str) -> str:
    """Which client started the conversation, from the id prefix the clients use."""
    if conversation_id.startswith("sport-"):
        return "sport"
    if conversation_id.startswith("android-"):
        return "phone"
    return "pc"


def _title(text: str) -> str:
    one_line = " ".join(text.split())
    return one_line if len(one_line) <= TITLE_LENGTH else one_line[: TITLE_LENGTH - 1].rstrip() + "…"


class ChatHistory:
    def __init__(self, db: Database, now: Callable[[], datetime]):
        self._db = db
        self._now = now

    def record(self, conversation_id: str, question: str, reply: str, actions: list[str]) -> None:
        at = self._now().isoformat(timespec="seconds")
        user_text = _LEADING_NOTE.sub("", question).strip() or question.strip()
        self._insert(conversation_id, at, "user", user_text, None)
        self._insert(conversation_id, at, "bot", reply, json.dumps(actions) if actions else None)
        cutoff = (self._now() - KEEP).isoformat(timespec="seconds")
        self._db.execute("DELETE FROM chat_messages WHERE at < ?", (cutoff,))

    def conversations(self, limit: int = 60) -> list[dict[str, Any]]:
        rows = self._db.query(
            "SELECT conversation_id, MIN(id) AS first_id, MIN(at) AS started, MAX(at) AS last, COUNT(*) AS total "
            "FROM chat_messages WHERE deleted = 0 GROUP BY conversation_id ORDER BY MAX(id) DESC LIMIT ?",
            (limit,),
        )
        result = []
        for row in rows:
            first = self._db.query("SELECT text FROM chat_messages WHERE id = ?", (row["first_id"],))
            result.append(
                {
                    "id": row["conversation_id"],
                    "title": _title(first[0]["text"]) if first else "Conversazione",
                    "startedAt": row["started"],
                    "lastAt": row["last"],
                    "messages": row["total"],
                    "source": source_of(row["conversation_id"]),
                }
            )
        return result

    def messages(self, conversation_id: str) -> list[dict[str, Any]]:
        rows = self._db.query(
            "SELECT at, role, text, actions FROM chat_messages WHERE conversation_id = ? AND deleted = 0 ORDER BY id",
            (conversation_id,),
        )
        return [
            {
                "at": row["at"],
                "role": row["role"],
                "text": row["text"],
                "actions": json.loads(row["actions"]) if row["actions"] else [],
            }
            for row in rows
        ]

    def delete(self, conversation_id: str) -> bool:
        alive = self._db.query(
            "SELECT COUNT(*) AS n FROM chat_messages WHERE conversation_id = ? AND deleted = 0",
            (conversation_id,),
        )
        if alive[0]["n"] == 0:
            return False
        self.tombstone(conversation_id, self._now())
        return True

    def tombstone(self, conversation_id: str, at: datetime) -> int:
        """Hide a conversation on this PC and stamp it so the other side drops it too."""
        rev = self._db.bump()
        stamp = at.isoformat(timespec="seconds")
        self._db.execute(
            "UPDATE chat_messages SET deleted = 1, updated_at = ?, rev = ? WHERE conversation_id = ? AND deleted = 0",
            (stamp, rev, conversation_id),
        )
        self._db.execute(
            "INSERT INTO conversation_deletions (conversation_id, updated_at, rev) VALUES (?, ?, ?) "
            "ON CONFLICT(conversation_id) DO UPDATE SET updated_at = excluded.updated_at, rev = excluded.rev",
            (conversation_id, stamp, rev),
        )
        return rev

    def _insert(self, conversation_id: str, at: str, role: str, text: str, actions: str | None) -> None:
        self._db.execute(
            "INSERT INTO chat_messages (conversation_id, at, role, text, actions, uid, updated_at, rev) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (conversation_id, at, role, text, actions, str(uuid.uuid4()), at, self._db.bump()),
        )

    def context(self, conversation_id: str, turns: int) -> list[list[dict[str, Any]]]:
        """The last [turns] exchanges as model turns, so an old conversation can be continued."""
        stored = self.messages(conversation_id)
        pairs: list[list[dict[str, Any]]] = []
        pending: dict[str, Any] | None = None
        for message in stored:
            if message["role"] == "user":
                pending = {"role": "user", "parts": [{"text": message["text"]}]}
            elif pending is not None:
                pairs.append([pending, {"role": "model", "parts": [{"text": message["text"]}]}])
                pending = None
        return pairs[-turns:]
