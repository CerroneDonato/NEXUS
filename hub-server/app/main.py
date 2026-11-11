from __future__ import annotations

import hmac
import logging
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any, Callable, Protocol

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from .assistant import Assistant, AssistantError, ModelClient
from .assistant_tools import Toolbox
from .cache import TtlCache
from .calendar_client import CalendarNotConfigured
from .config import Settings
from .db import Database
from .tasks import Task, TaskCreate, TaskRepository, TaskUpdate
from .wake import WakeScheduler

log = logging.getLogger(__name__)

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"
ALARM_KEY = "next_alarm"
WAKE_KEY = "wake_at"


class WeatherProvider(Protocol):
    def fetch(self) -> dict[str, Any]: ...


class CalendarProvider(Protocol):
    def events_for(self, day: date) -> list[dict[str, Any]]: ...


class AlarmUpdate(BaseModel):
    nextAlarm: datetime | None = None


class AlarmState(BaseModel):
    nextAlarm: datetime | None
    wakeAt: datetime | None
    scheduled: bool


class ChatRequest(BaseModel):
    conversationId: str = Field(min_length=1, max_length=64)
    message: str = Field(min_length=1, max_length=2000)


class ChatResponse(BaseModel):
    reply: str
    actions: list[str]


class AssistantInfo(BaseModel):
    name: str
    enabled: bool


@dataclass
class HubServices:
    settings: Settings
    db: Database
    weather: WeatherProvider
    calendar: CalendarProvider
    wake: WakeScheduler
    now: Callable[[], datetime]
    assistant_model: ModelClient | None = None


def create_app(services: HubServices) -> FastAPI:
    settings = services.settings
    tasks = TaskRepository(services.db, services.now)
    weather_cache = TtlCache(services.weather.fetch, ttl_seconds=30 * 60)
    calendar_cache: dict[date, TtlCache] = {}

    def events_on(day: date) -> list[dict[str, Any]]:
        if day not in calendar_cache:
            if len(calendar_cache) >= 16:
                calendar_cache.clear()
            calendar_cache[day] = TtlCache(lambda: services.calendar.events_for(day), ttl_seconds=5 * 60)
        return calendar_cache[day].get()

    def require_token(request: Request) -> None:
        header = request.headers.get("Authorization", "")
        supplied = header[7:] if header.startswith("Bearer ") else ""
        if not supplied or not hmac.compare_digest(supplied.encode(), settings.api_token.encode()):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token non valido")

    app = FastAPI(title="Daily Hub", version="1.0.0")
    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["Authorization", "Content-Type"])
    auth = [Depends(require_token)]

    @app.middleware("http")
    async def revalidate_static(request: Request, call_next):
        response = await call_next(request)
        if request.url.path == "/" or request.url.path.startswith("/static/"):
            response.headers["Cache-Control"] = "no-cache"
        return response

    @app.get("/api/health")
    def health() -> dict[str, str]:
        return {"status": "ok", "time": services.now().isoformat(timespec="seconds")}

    @app.get("/api/today", dependencies=auth)
    def today() -> dict[str, Any]:
        now = services.now()
        result: dict[str, Any] = {
            "date": now.date().isoformat(),
            "generatedAt": now.isoformat(timespec="seconds"),
            "weather": None,
            "weatherError": None,
            "events": [],
            "calendarError": None,
            "tasks": [t.model_dump(mode="json") for t in tasks.list_all()],
            "alarm": _alarm_state().model_dump(mode="json"),
        }
        try:
            result["weather"] = weather_cache.get()
        except Exception as exc:  # network errors must not break the dashboard
            log.warning("Weather fetch failed: %s", exc)
            result["weatherError"] = "Meteo non disponibile"
        try:
            result["events"] = events_on(now.date())
        except CalendarNotConfigured as exc:
            result["calendarError"] = str(exc)
        except Exception as exc:
            log.warning("Calendar fetch failed: %s", exc)
            result["calendarError"] = "Calendario non disponibile"
        return result

    @app.get("/api/tasks", dependencies=auth, response_model=list[Task])
    def list_tasks(includeDone: bool = False) -> list[Task]:
        return tasks.list_all(include_done=includeDone)

    @app.post("/api/tasks", dependencies=auth, response_model=Task, status_code=status.HTTP_201_CREATED)
    def create_task(data: TaskCreate) -> Task:
        return tasks.create(data)

    @app.patch("/api/tasks/{task_id}", dependencies=auth, response_model=Task)
    def update_task(task_id: int, data: TaskUpdate) -> Task:
        updated = tasks.update(task_id, data)
        if updated is None:
            raise HTTPException(status_code=404, detail="Task non trovato")
        return updated

    @app.delete("/api/tasks/{task_id}", dependencies=auth, status_code=status.HTTP_204_NO_CONTENT)
    def delete_task(task_id: int) -> None:
        if not tasks.delete(task_id):
            raise HTTPException(status_code=404, detail="Task non trovato")

    def _alarm_state() -> AlarmState:
        alarm = services.db.get_value(ALARM_KEY)
        wake_at = services.db.get_value(WAKE_KEY)
        return AlarmState(
            nextAlarm=datetime.fromisoformat(alarm) if alarm else None,
            wakeAt=datetime.fromisoformat(wake_at) if wake_at else None,
            scheduled=bool(wake_at),
        )

    @app.get("/api/alarm", dependencies=auth, response_model=AlarmState)
    def get_alarm() -> AlarmState:
        return _alarm_state()

    @app.put("/api/alarm", dependencies=auth, response_model=AlarmState)
    def put_alarm(data: AlarmUpdate) -> AlarmState:
        alarm = data.nextAlarm
        if alarm is not None and alarm.tzinfo is None:
            alarm = alarm.replace(tzinfo=settings.tz)
        services.db.set_value(ALARM_KEY, alarm.isoformat() if alarm else None)

        wake_at = alarm - timedelta(minutes=settings.wake_lead_minutes) if alarm else None
        if wake_at is not None and wake_at > services.now():
            scheduled = services.wake.schedule(wake_at)
        else:
            services.wake.cancel()
            scheduled = False
        services.db.set_value(WAKE_KEY, wake_at.isoformat() if scheduled and wake_at else None)
        return _alarm_state()

    assistant: Assistant | None = None
    if services.assistant_model is not None:
        toolbox = Toolbox(
            tasks=tasks,
            weather=weather_cache.get,
            events_on=events_on,
            alarm=lambda: {"nextAlarm": _alarm_state().model_dump(mode="json")["nextAlarm"]},
        )
        assistant = Assistant(
            services.assistant_model,
            toolbox,
            name=settings.assistant_name,
            city=settings.city,
            timezone=settings.timezone,
            now=services.now,
        )

    def require_assistant() -> Assistant:
        if assistant is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Assistente non configurato: imposta GEMINI_API_KEY nel file .env dell'hub",
            )
        return assistant

    @app.get("/api/assistant", dependencies=auth, response_model=AssistantInfo)
    def assistant_info() -> AssistantInfo:
        return AssistantInfo(name=settings.assistant_name, enabled=assistant is not None)

    @app.post("/api/assistant/chat", dependencies=auth, response_model=ChatResponse)
    def assistant_chat(data: ChatRequest) -> ChatResponse:
        bot = require_assistant()
        try:
            result = bot.chat(data.conversationId, data.message.strip())
        except AssistantError as exc:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)) from exc
        return ChatResponse(reply=result.reply, actions=result.actions)

    @app.delete("/api/assistant/chat/{conversation_id}", dependencies=auth, status_code=status.HTTP_204_NO_CONTENT)
    def assistant_reset(conversation_id: str) -> None:
        require_assistant().reset(conversation_id)

    if STATIC_DIR.exists():
        app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

        @app.get("/", include_in_schema=False)
        def dashboard() -> FileResponse:
            return FileResponse(STATIC_DIR / "index.html")

    return app
