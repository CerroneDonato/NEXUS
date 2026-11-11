"""Functions the assistant model may call, executed against the hub's own data."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import date
from typing import Any, Callable

from pydantic import ValidationError

from .calendar_client import CalendarNotConfigured
from .tasks import TaskCreate, TaskRepository, TaskUpdate

log = logging.getLogger(__name__)

DECLARATIONS: list[dict[str, Any]] = [
    {
        "name": "get_weather",
        "description": "Meteo attuale, prossime 12 ore e previsione giornaliera fino a 7 giorni per la città dell'utente.",
        "parameters": {"type": "object", "properties": {}},
    },
    {
        "name": "list_events",
        "description": "Eventi del Google Calendar dell'utente in un giorno.",
        "parameters": {
            "type": "object",
            "properties": {"date": {"type": "string", "description": "Giorno in formato YYYY-MM-DD"}},
            "required": ["date"],
        },
    },
    {
        "name": "list_tasks",
        "description": "Task personali dell'utente, con id, titolo, scadenza e stato.",
        "parameters": {
            "type": "object",
            "properties": {
                "include_done": {"type": "boolean", "description": "Includi anche i task completati"},
            },
        },
    },
    {
        "name": "create_task",
        "description": "Crea un nuovo task personale.",
        "parameters": {
            "type": "object",
            "properties": {
                "title": {"type": "string", "description": "Titolo breve del task"},
                "due": {"type": "string", "description": "Scadenza opzionale in formato YYYY-MM-DD"},
            },
            "required": ["title"],
        },
    },
    {
        "name": "complete_task",
        "description": "Segna un task come completato. Usa prima list_tasks per trovare l'id.",
        "parameters": {
            "type": "object",
            "properties": {"task_id": {"type": "integer"}},
            "required": ["task_id"],
        },
    },
    {
        "name": "update_task",
        "description": "Modifica titolo o scadenza di un task. due vuoto rimuove la scadenza.",
        "parameters": {
            "type": "object",
            "properties": {
                "task_id": {"type": "integer"},
                "title": {"type": "string"},
                "due": {"type": "string", "description": "YYYY-MM-DD, oppure stringa vuota per togliere la scadenza"},
            },
            "required": ["task_id"],
        },
    },
    {
        "name": "get_alarm",
        "description": "Prossima sveglia impostata sul telefono dell'utente.",
        "parameters": {"type": "object", "properties": {}},
    },
]


@dataclass
class ToolResult:
    data: dict[str, Any]
    action: str | None = None


@dataclass
class Toolbox:
    tasks: TaskRepository
    weather: Callable[[], dict[str, Any]]
    events_on: Callable[[date], list[dict[str, Any]]]
    alarm: Callable[[], dict[str, Any]]
    handlers: dict[str, Callable[[dict[str, Any]], ToolResult]] = field(init=False)

    def __post_init__(self) -> None:
        self.handlers = {
            "get_weather": self._get_weather,
            "list_events": self._list_events,
            "list_tasks": self._list_tasks,
            "create_task": self._create_task,
            "complete_task": self._complete_task,
            "update_task": self._update_task,
            "get_alarm": lambda _args: ToolResult(self.alarm()),
        }

    def call(self, name: str, args: dict[str, Any]) -> ToolResult:
        handler = self.handlers.get(name)
        if handler is None:
            return ToolResult({"error": f"Strumento sconosciuto: {name}"})
        try:
            return handler(args or {})
        except (ValueError, ValidationError) as exc:
            return ToolResult({"error": f"Parametri non validi: {exc}"})
        except Exception as exc:
            log.warning("Tool %s failed: %s", name, exc)
            return ToolResult({"error": "Servizio non disponibile in questo momento"})

    def _get_weather(self, _args: dict[str, Any]) -> ToolResult:
        weather = self.weather()
        return ToolResult(
            {
                "city": weather.get("city"),
                "current": weather.get("current"),
                "today": weather.get("today"),
                "nextHours": [
                    {k: h.get(k) for k in ("time", "temperature", "precipitationProbability", "description")}
                    for h in weather.get("hourly", [])
                ],
                "daily": weather.get("daily", []),
            }
        )

    def _list_events(self, args: dict[str, Any]) -> ToolResult:
        day = date.fromisoformat(str(args.get("date", "")))
        try:
            events = self.events_on(day)
        except CalendarNotConfigured:
            return ToolResult({"error": "Google Calendar non è collegato all'hub"})
        return ToolResult(
            {
                "date": day.isoformat(),
                "events": [
                    {k: e.get(k) for k in ("title", "start", "end", "allDay", "location", "calendar")} for e in events
                ],
            }
        )

    def _list_tasks(self, args: dict[str, Any]) -> ToolResult:
        items = self.tasks.list_all(include_done=bool(args.get("include_done")))
        return ToolResult(
            {
                "tasks": [
                    {
                        "id": t.id,
                        "title": t.title,
                        "due": t.due.isoformat() if t.due else None,
                        "done": t.done,
                        "overdue": t.overdue,
                    }
                    for t in items
                ]
            }
        )

    def _create_task(self, args: dict[str, Any]) -> ToolResult:
        due = _parse_due(args.get("due"))
        task = self.tasks.create(TaskCreate(title=str(args.get("title", "")), due=due))
        due_iso = task.due.isoformat() if task.due else None
        suffix = f" (entro {due_iso})" if due_iso else ""
        return ToolResult(
            {"created": {"id": task.id, "title": task.title, "due": due_iso}},
            action=f"Task creato: {task.title}{suffix}",
        )

    def _complete_task(self, args: dict[str, Any]) -> ToolResult:
        task = self.tasks.update(int(args["task_id"]), TaskUpdate(done=True))
        if task is None:
            return ToolResult({"error": "Task non trovato"})
        return ToolResult({"completed": {"id": task.id, "title": task.title}}, action=f"Task completato: {task.title}")

    def _update_task(self, args: dict[str, Any]) -> ToolResult:
        changes: dict[str, Any] = {}
        if args.get("title"):
            changes["title"] = str(args["title"])
        if "due" in args:
            changes["due"] = _parse_due(args["due"])
        if not changes:
            return ToolResult({"error": "Nessuna modifica indicata"})
        task = self.tasks.update(int(args["task_id"]), TaskUpdate(**changes))
        if task is None:
            return ToolResult({"error": "Task non trovato"})
        return ToolResult(
            {"updated": {"id": task.id, "title": task.title, "due": task.due.isoformat() if task.due else None}},
            action=f"Task modificato: {task.title}",
        )


def _parse_due(value: Any) -> date | None:
    if value is None or str(value).strip() == "":
        return None
    return date.fromisoformat(str(value).strip()[:10])
