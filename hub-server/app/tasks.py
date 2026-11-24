from __future__ import annotations

import sqlite3
import uuid
from datetime import date, datetime
from typing import Callable

from pydantic import BaseModel, Field

from .db import Database


class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    due: date | None = None


class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    due: date | None = None
    done: bool | None = None


class Task(BaseModel):
    id: int
    uid: str
    title: str
    due: date | None
    done: bool
    createdAt: datetime
    completedAt: datetime | None
    updatedAt: datetime
    overdue: bool = False


class TaskRepository:
    def __init__(self, db: Database, now: Callable[[], datetime]):
        self._db = db
        self._now = now

    def _to_task(self, row: sqlite3.Row) -> Task:
        due = date.fromisoformat(row["due"]) if row["due"] else None
        done = bool(row["done"])
        updated = row["updated_at"] or row["created_at"]
        return Task(
            id=row["id"],
            uid=row["uid"],
            title=row["title"],
            due=due,
            done=done,
            createdAt=datetime.fromisoformat(row["created_at"]),
            completedAt=datetime.fromisoformat(row["completed_at"]) if row["completed_at"] else None,
            updatedAt=datetime.fromisoformat(updated),
            overdue=bool(due and not done and due < self._now().date()),
        )

    def list_all(self, include_done: bool = False) -> list[Task]:
        where = "WHERE deleted = 0" if include_done else "WHERE deleted = 0 AND done = 0"
        rows = self._db.query(
            f"SELECT * FROM tasks {where} ORDER BY done, due IS NULL, due, created_at"
        )
        return [self._to_task(row) for row in rows]

    def get(self, task_id: int) -> Task | None:
        rows = self._db.query("SELECT * FROM tasks WHERE id = ? AND deleted = 0", (task_id,))
        return self._to_task(rows[0]) if rows else None

    def get_by_uid(self, uid: str) -> Task | None:
        rows = self._db.query("SELECT * FROM tasks WHERE uid = ? AND deleted = 0", (uid,))
        return self._to_task(rows[0]) if rows else None

    def create(self, data: TaskCreate) -> Task:
        now = self._now().isoformat()
        cursor = self._db.execute(
            "INSERT INTO tasks (title, due, created_at, uid, updated_at, rev) VALUES (?, ?, ?, ?, ?, ?)",
            (data.title.strip(), data.due.isoformat() if data.due else None, now, str(uuid.uuid4()), now, self._db.bump()),
        )
        return self.get(cursor.lastrowid)

    def update(self, task_id: int, data: TaskUpdate) -> Task | None:
        current = self.get(task_id)
        if current is None:
            return None
        fields = data.model_fields_set
        title = data.title.strip() if "title" in fields and data.title else current.title
        due = data.due if "due" in fields else current.due
        done = data.done if "done" in fields and data.done is not None else current.done
        completed_at = current.completedAt
        if done and not current.done:
            completed_at = self._now()
        elif not done:
            completed_at = None
        self._db.execute(
            "UPDATE tasks SET title = ?, due = ?, done = ?, completed_at = ?, updated_at = ?, rev = ? WHERE id = ?",
            (
                title,
                due.isoformat() if due else None,
                int(done),
                completed_at.isoformat() if completed_at else None,
                self._now().isoformat(),
                self._db.bump(),
                task_id,
            ),
        )
        return self.get(task_id)

    def delete(self, task_id: int) -> bool:
        if self.get(task_id) is None:
            return False
        self._db.execute(
            "UPDATE tasks SET deleted = 1, updated_at = ?, rev = ? WHERE id = ?",
            (self._now().isoformat(), self._db.bump(), task_id),
        )
        return True
