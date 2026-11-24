from __future__ import annotations

import sqlite3
import threading
import uuid
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    due TEXT,
    done INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    completed_at TEXT,
    uid TEXT,
    updated_at TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    rev INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS kv (
    key TEXT PRIMARY KEY,
    value TEXT
);
CREATE TABLE IF NOT EXISTS chat_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    at TEXT NOT NULL,
    role TEXT NOT NULL,
    text TEXT NOT NULL,
    actions TEXT,
    uid TEXT,
    updated_at TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    rev INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS chat_messages_conversation ON chat_messages (conversation_id, id);
CREATE TABLE IF NOT EXISTS conversation_deletions (
    conversation_id TEXT PRIMARY KEY,
    updated_at TEXT NOT NULL,
    rev INTEGER NOT NULL DEFAULT 0
);
"""


class Database:
    """Thin thread-safe wrapper around a single SQLite connection."""

    def __init__(self, path: Path | str):
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(str(path), check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._lock = threading.Lock()
        with self._lock:
            if path != ":memory:":
                # Readers (dashboard polling) and the sync writer should not block each other.
                self._conn.execute("PRAGMA journal_mode=WAL")
                self._conn.execute("PRAGMA synchronous=NORMAL")
            self._conn.execute("PRAGMA busy_timeout=5000")
            self._conn.executescript(SCHEMA)
            self._migrate()
            self._conn.commit()

    def _columns(self, table: str) -> set[str]:
        return {row[1] for row in self._conn.execute(f"PRAGMA table_info({table})")}

    def _add_column(self, table: str, column: str, ddl: str) -> None:
        if column not in self._columns(table):
            self._conn.execute(f"ALTER TABLE {table} ADD COLUMN {ddl}")

    def _migrate(self) -> None:
        """Add sync columns to databases created before UUIDs existed, then backfill them."""
        self._add_column("tasks", "uid", "uid TEXT")
        self._add_column("tasks", "updated_at", "updated_at TEXT")
        self._add_column("tasks", "deleted", "deleted INTEGER NOT NULL DEFAULT 0")
        self._add_column("tasks", "rev", "rev INTEGER NOT NULL DEFAULT 0")
        self._add_column("chat_messages", "uid", "uid TEXT")
        self._add_column("chat_messages", "updated_at", "updated_at TEXT")
        self._add_column("chat_messages", "deleted", "deleted INTEGER NOT NULL DEFAULT 0")
        self._add_column("chat_messages", "rev", "rev INTEGER NOT NULL DEFAULT 0")
        self._conn.execute(
            "CREATE TABLE IF NOT EXISTS conversation_deletions ("
            "conversation_id TEXT PRIMARY KEY, updated_at TEXT NOT NULL, rev INTEGER NOT NULL DEFAULT 0)"
        )
        for table, stamp in (("tasks", "created_at"), ("chat_messages", "at")):
            rows = self._conn.execute(
                f"SELECT id, {stamp} AS stamp FROM {table} WHERE uid IS NULL OR uid = ''"
            ).fetchall()
            for row in rows:
                self._conn.execute(
                    f"UPDATE {table} SET uid = ?, updated_at = COALESCE(updated_at, ?), "
                    "rev = CASE WHEN rev IS NULL OR rev = 0 THEN 1 ELSE rev END WHERE id = ?",
                    (str(uuid.uuid4()), row["stamp"], row["id"]),
                )
        self._conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS tasks_uid ON tasks(uid)")
        self._conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS chat_messages_uid ON chat_messages(uid)")
        highest = 0
        for table in ("tasks", "chat_messages", "conversation_deletions"):
            row = self._conn.execute(f"SELECT MAX(rev) AS m FROM {table}").fetchone()
            highest = max(highest, int(row["m"] or 0))
        current = self._conn.execute("SELECT value FROM kv WHERE key = 'sync_rev'").fetchone()
        if highest and (current is None or int(current["value"] or 0) < highest):
            self._conn.execute(
                "INSERT INTO kv (key, value) VALUES ('sync_rev', ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (str(highest),),
            )

    def execute(self, sql: str, params: tuple = ()) -> sqlite3.Cursor:
        with self._lock:
            cursor = self._conn.execute(sql, params)
            self._conn.commit()
            return cursor

    def query(self, sql: str, params: tuple = ()) -> list[sqlite3.Row]:
        with self._lock:
            return self._conn.execute(sql, params).fetchall()

    def get_value(self, key: str) -> str | None:
        rows = self.query("SELECT value FROM kv WHERE key = ?", (key,))
        return rows[0]["value"] if rows else None

    def set_value(self, key: str, value: str | None) -> None:
        self.execute(
            "INSERT INTO kv (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (key, value),
        )

    def revision(self) -> int:
        return int(self.get_value("sync_rev") or "0")

    def bump(self) -> int:
        """Next change counter. Rows stamped with it are what a later sync will send."""
        with self._lock:
            row = self._conn.execute("SELECT value FROM kv WHERE key = 'sync_rev'").fetchone()
            nxt = int(row["value"] if row else 0) + 1
            self._conn.execute(
                "INSERT INTO kv (key, value) VALUES ('sync_rev', ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (str(nxt),),
            )
            self._conn.commit()
            return nxt

    def close(self) -> None:
        with self._lock:
            self._conn.close()
