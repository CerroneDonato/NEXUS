from __future__ import annotations

import sqlite3
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
    title: str
    due: date | None
    done: bool
    createdAt: datetime
    completedAt: datetime | None
    overdue: bool = False


class TaskRepository:
    def __init__(self, db: Database, now: Callable[[], datetime]):
        self._db = db
        self._now = now

    def _to_task(self, row: sqlite3.Row) -> Task:
        due = date.fromisoformat(row["due"]) if row["due"] else None
        done = bool(row["done"])
        return Task(
            id=row["id"],
            title=row["title"],
            due=due,
            done=done,
            createdAt=datetime.fromisoformat(row["created_at"]),
            completedAt=datetime.fromisoformat(row["completed_at"]) if row["completed_at"] else None,
            overdue=bool(due and not done and due < self._now().date()),
        )

    def list_all(self, include_done: bool = False) -> list[Task]:
        where = "" if include_done else "WHERE done = 0"
        rows = self._db.query(
            f"SELECT * FROM tasks {where} ORDER BY done, due IS NULL, due, created_at"
        )
        return [self._to_task(row) for row in rows]

    def get(self, task_id: int) -> Task | None:
        rows = self._db.query("SELECT * FROM tasks WHERE id = ?", (task_id,))
        return self._to_task(rows[0]) if rows else None

    def create(self, data: TaskCreate) -> Task:
        cursor = self._db.execute(
            "INSERT INTO tasks (title, due, created_at) VALUES (?, ?, ?)",
            (data.title.strip(), data.due.isoformat() if data.due else None, self._now().isoformat()),
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
            "UPDATE tasks SET title = ?, due = ?, done = ?, completed_at = ? WHERE id = ?",
            (
                title,
                due.isoformat() if due else None,
                int(done),
                completed_at.isoformat() if completed_at else None,
                task_id,
            ),
        )
        return self.get(task_id)

    def delete(self, task_id: int) -> bool:
        return self._db.execute("DELETE FROM tasks WHERE id = ?", (task_id,)).rowcount > 0
