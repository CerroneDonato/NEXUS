"""The last sync attempts that reached this PC, so the settings pages can show what happened."""

from __future__ import annotations

import json
import threading
from datetime import datetime
from typing import Any

from .db import Database

KEY = "sync_log"
LIMIT = 10


class SyncLog:
    _lock = threading.Lock()

    def __init__(self, db: Database):
        self._db = db

    def record(
        self,
        at: datetime,
        client: str | None,
        ok: bool,
        status: int,
        detail: str,
        received: dict[str, int] | None = None,
        sent: dict[str, int] | None = None,
        duration_ms: int = 0,
    ) -> None:
        entry = {
            "at": at.isoformat(timespec="seconds"),
            "client": client or "",
            "ok": ok,
            "status": status,
            "detail": detail,
            "received": received or {},
            "sent": sent or {},
            "durationMs": duration_ms,
        }
        with self._lock:
            items = self.recent()
            items.insert(0, entry)
            self._db.set_value(KEY, json.dumps(items[:LIMIT]))

    def recent(self) -> list[dict[str, Any]]:
        raw = self._db.get_value(KEY)
        if not raw:
            return []
        try:
            items = json.loads(raw)
        except ValueError:
            return []
        return items if isinstance(items, list) else []
