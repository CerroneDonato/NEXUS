from __future__ import annotations

import hashlib
import hmac
import logging
import threading
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any, Callable, Literal, Protocol

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, ConfigDict, Field

from .announce import lan_ip
from .assistant import Assistant, AssistantError, ModelClient
from .assistant_tools import Toolbox
from .cache import TtlCache
from .calendar_client import CalendarNotConfigured
from .config import Settings, read_version
from .db import Database
from .history import ChatHistory
from .sync import SyncEngine, SyncRequest, SyncResponse
from .synclog import SyncLog
from .tasks import Task, TaskCreate, TaskRepository, TaskUpdate
from .wake import WakeScheduler

log = logging.getLogger(__name__)

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"
ALARM_KEY = "next_alarm"
WAKE_KEY = "wake_at"
WAKE_LEAD_KEY = "wake_lead_minutes"


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
    language: Literal["it", "en"] = "it"


class ChatResponse(BaseModel):
    reply: str
    actions: list[str]


class AssistantInfo(BaseModel):
    name: str
    enabled: bool


class SettingsUpdate(BaseModel):
    """What the dashboard settings page can change. Values go to the database, never to .env."""

    model_config = ConfigDict(str_strip_whitespace=True)

    city: str | None = Field(default=None, min_length=1, max_length=60)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    assistantName: str | None = Field(default=None, min_length=1, max_length=30)
    wakeLeadMinutes: int | None = Field(default=None, ge=0, le=120)
    geminiModel: str | None = Field(default=None, min_length=1, max_length=60, pattern=r"^[A-Za-z0-9._\-]+$")


class ConversationSummary(BaseModel):
    id: str
    title: str
    startedAt: str
    lastAt: str
    messages: int
    source: str


class StoredMessage(BaseModel):
    at: str
    role: str
    text: str
    actions: list[str]


class ConversationDetail(BaseModel):
    id: str
    messages: list[StoredMessage]


@dataclass
class Heartbeat:
    """Last moment the open dashboard said it is still there. None until the first one."""

    def __init__(self) -> None:
        self.at: float | None = None

    def beat(self) -> None:
        import time

        self.at = time.monotonic()


@dataclass
class HubServices:
    settings: Settings
    db: Database
    weather: WeatherProvider
    calendar: CalendarProvider
    wake: WakeScheduler
    now: Callable[[], datetime]
    assistant_model: ModelClient | None = None
    heartbeat: Heartbeat | None = None


def create_app(services: HubServices) -> FastAPI:
    settings = services.settings
    tasks = TaskRepository(services.db, services.now)
    weather_cache = TtlCache(lambda: _fetch_weather(services), ttl_seconds=30 * 60)
    calendar_cache: dict[date, TtlCache] = {}

    def events_on(day: date) -> list[dict[str, Any]]:
        if day not in calendar_cache:
            if len(calendar_cache) >= 16:
                calendar_cache.clear()
            calendar_cache[day] = TtlCache(lambda: services.calendar.events_for(day), ttl_seconds=5 * 60)
        return calendar_cache[day].get()

    version = read_version()
    started_at = services.now()
    sync_log = SyncLog(services.db)
    sync_lock = threading.Lock()

    def require_token(request: Request) -> None:
        header = request.headers.get("Authorization", "")
        supplied = header[7:] if header.startswith("Bearer ") else ""
        if not supplied or not hmac.compare_digest(supplied.encode(), settings.api_token.encode()):
            if request.url.path == "/api/sync":
                sync_log.record(services.now(), _client(request), False, 401, "Token non valido")
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token non valido")

    app = FastAPI(title="Daily Hub", version=version)
    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["Authorization", "Content-Type"])
    auth = [Depends(require_token)]

    @app.exception_handler(RequestValidationError)
    async def invalid_request(request: Request, exc: RequestValidationError):
        if request.url.path == "/api/sync":
            sync_log.record(services.now(), _client(request), False, 422, "Dati della sync non validi")
        return await request_validation_exception_handler(request, exc)

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
            "week": None,
            "calendarError": None,
            "tasks": [t.model_dump(mode="json") for t in tasks.list_all()],
            "alarm": _alarm_state().model_dump(mode="json"),
            "lastSync": services.db.get_value("last_sync_at"),
            "phoneAddress": f"http://{lan_ip()}:{services.settings.port}",
        }
        try:
            result["weather"] = weather_cache.get()
        except Exception as exc:  # network errors must not break the dashboard
            log.warning("Weather fetch failed: %s", exc)
            result["weatherError"] = "Meteo non disponibile"
        today = now.date()
        try:
            result["events"] = events_on(today)
        except CalendarNotConfigured as exc:
            result["calendarError"] = str(exc)
        except Exception as exc:
            log.warning("Calendar fetch failed: %s", exc)
            result["calendarError"] = "Calendario non disponibile"
        else:
            week: list[dict[str, Any]] = [{"date": today.isoformat(), "events": result["events"]}]
            try:
                for offset in range(1, 7):
                    day = today + timedelta(days=offset)
                    week.append({"date": day.isoformat(), "events": events_on(day)})
            except Exception as exc:
                log.warning("Week calendar fetch failed: %s", exc)
            else:
                result["week"] = week
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
        stored = services.db.get_value(ALARM_KEY)
        alarm = datetime.fromisoformat(stored) if stored else None
        if alarm is not None and alarm.tzinfo is None:
            alarm = alarm.replace(tzinfo=settings.tz)
        if alarm is not None and alarm <= services.now():
            # A rung alarm is not "next": hide it until the phone sends a new one.
            return AlarmState(nextAlarm=None, wakeAt=None, scheduled=False)
        wake_at = services.db.get_value(WAKE_KEY)
        return AlarmState(
            nextAlarm=alarm,
            wakeAt=datetime.fromisoformat(wake_at) if wake_at else None,
            scheduled=bool(wake_at),
        )

    @app.get("/api/alarm", dependencies=auth, response_model=AlarmState)
    def get_alarm() -> AlarmState:
        return _alarm_state()

    @app.put("/api/alarm", dependencies=auth, response_model=AlarmState)
    def put_alarm(data: AlarmUpdate) -> AlarmState:
        apply_alarm(services, data.nextAlarm)
        return _alarm_state()

    @app.post("/api/sync", dependencies=auth, response_model=SyncResponse)
    def sync(data: SyncRequest, request: Request) -> SyncResponse:
        started = time.monotonic()
        received = {"tasks": len(data.tasks), "messages": len(data.messages), "deletions": len(data.deletedConversations)}
        # Phone and dashboard can sync at the same time: one exchange at a time keeps the merge consistent.
        with sync_lock:
            place_before = (_place(services), _shared(services, "city", settings.city))
            engine = SyncEngine(
                services.db, services.now, settings.tz, settings.city, settings.latitude, settings.longitude,
                settings.assistant_name, settings.gemini_api_key, settings.gemini_model,
            )
            try:
                response, alarm, alarm_changed = engine.apply(data)
                if alarm_changed:
                    apply_alarm(services, datetime.fromisoformat(alarm) if alarm else None)
            except Exception as exc:
                sync_log.record(services.now(), _client(request), False, 500, f"Errore del server: {exc}",
                                received, None, _elapsed_ms(started))
                raise
            refresh_identity()
            if (_place(services), _shared(services, "city", settings.city)) != place_before:
                weather_cache.invalidate()
        sent = {"tasks": len(response.tasks), "messages": len(response.messages),
                "deletions": len(response.deletedConversations)}
        idle = not any(received.values()) and not any(sent.values()) and not alarm_changed
        recent = sync_log.recent()
        recovered = bool(recent) and not recent[0].get("ok", True)  # first success after a failure is worth showing
        if not idle or recovered:  # the 15 s polls of an open app would otherwise flood the log
            sync_log.record(services.now(), _client(request), True, 200, "Sincronizzato", received, sent,
                            _elapsed_ms(started))
        return response.model_copy(update={"version": version})

    @app.get("/api/revision", dependencies=auth)
    def revision() -> dict[str, Any]:
        """Cheap change marker: the dashboard polls it and reloads only when something moved."""
        # Tasks and chat move `revision`; settings and the alarm live in kv rows, so they are folded in too.
        rows = services.db.query(
            "SELECT key, value FROM kv WHERE key LIKE 'setting:%' OR key IN (?, ?) ORDER BY key", (ALARM_KEY, WAKE_LEAD_KEY)
        )
        marker = hashlib.sha1("|".join(f"{r['key']}={r['value']}" for r in rows).encode()).hexdigest()[:12]
        return {"revision": services.db.revision(), "marker": marker, "lastSync": services.db.get_value("last_sync_at")}

    @app.get("/api/info", dependencies=auth)
    def info() -> dict[str, Any]:
        calendar_error = None
        try:
            events_on(services.now().date())
        except CalendarNotConfigured as exc:
            calendar_error = str(exc)
        except Exception as exc:
            log.warning("Calendar check failed: %s", exc)
            calendar_error = "Calendario non disponibile"
        token = settings.api_token
        latitude, longitude = _place(services)
        return {
            "version": version,
            "startedAt": started_at.isoformat(timespec="seconds"),
            "phoneAddress": f"http://{lan_ip()}:{settings.port}",
            "port": settings.port,
            "tokenHint": f"…{token[-4:]}" if len(token) > 4 else "…",
            "lastSync": services.db.get_value("last_sync_at"),
            "syncLog": sync_log.recent(),
            "assistant": {"name": current_name(), "enabled": assistant is not None, "model": current_model()},
            "place": {"city": _shared(services, "city", settings.city), "latitude": latitude, "longitude": longitude},
            "calendar": {"connected": calendar_error is None, "error": calendar_error},
            "wake": {"enabled": settings.wake_enabled, "leadMinutes": wake_lead_minutes(services)},
            "alarm": _alarm_state().model_dump(mode="json"),
        }

    @app.put("/api/settings", dependencies=auth)
    def put_settings(data: SettingsUpdate) -> dict[str, Any]:
        stamp = services.now().isoformat(timespec="seconds")
        shared = {"city": data.city, "latitude": data.latitude, "longitude": data.longitude,
                  "assistant_name": data.assistantName, "gemini_model": data.geminiModel}
        for key, value in shared.items():
            if value is None:
                continue
            services.db.set_value(f"setting:{key}", str(value))
            services.db.set_value(f"setting:{key}:at", stamp)
        if data.city is not None or data.latitude is not None or data.longitude is not None:
            weather_cache.invalidate()
        refresh_identity()
        if data.wakeLeadMinutes is not None:
            services.db.set_value(WAKE_LEAD_KEY, str(data.wakeLeadMinutes))
            alarm = _alarm_state().nextAlarm
            if alarm is not None:
                apply_alarm(services, alarm)
        return info()

    @app.post("/api/dashboard/heartbeat", dependencies=auth, status_code=status.HTTP_204_NO_CONTENT)
    def dashboard_heartbeat() -> None:
        if services.heartbeat is not None:
            services.heartbeat.beat()

    history = ChatHistory(services.db, services.now)

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
            name=_shared(services, "assistant_name", settings.assistant_name),
            city=_shared(services, "city", settings.city),
            timezone=settings.timezone,
            now=services.now,
            seed=history.context,
        )

    def current_name() -> str:
        return _shared(services, "assistant_name", settings.assistant_name)

    def current_model() -> str:
        return _shared(services, "gemini_model", settings.gemini_model)

    def refresh_identity() -> None:
        if assistant is not None:
            assistant.set_identity(current_name(), _shared(services, "city", settings.city))
        switch = getattr(services.assistant_model, "set_model", None)
        if switch is not None and getattr(services.assistant_model, "model", None) != current_model():
            switch(current_model())

    refresh_identity()

    def require_assistant(language: str = "it") -> Assistant:
        if assistant is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=("Assistant not configured: set GEMINI_API_KEY in the hub's .env file" if language == "en"
                        else "Assistente non configurato: imposta GEMINI_API_KEY nel file .env dell'hub"),
            )
        return assistant

    @app.get("/api/assistant", dependencies=auth, response_model=AssistantInfo)
    def assistant_info() -> AssistantInfo:
        return AssistantInfo(name=current_name(), enabled=assistant is not None)

    @app.post("/api/assistant/chat", dependencies=auth, response_model=ChatResponse)
    def assistant_chat(data: ChatRequest) -> ChatResponse:
        bot = require_assistant(data.language)
        try:
            result = bot.chat(data.conversationId, data.message.strip(), data.language)
        except AssistantError as exc:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=exc.text(data.language)) from exc
        try:
            history.record(data.conversationId, data.message.strip(), result.reply, result.actions)
        except Exception:  # the answer matters more than the log
            log.exception("Could not store the chat message")
        return ChatResponse(reply=result.reply, actions=result.actions)

    @app.delete("/api/assistant/chat/{conversation_id}", dependencies=auth, status_code=status.HTTP_204_NO_CONTENT)
    def assistant_reset(conversation_id: str) -> None:
        # Starts a fresh context only: the stored history is kept.
        require_assistant().reset(conversation_id)

    @app.get("/api/assistant/history", dependencies=auth, response_model=list[ConversationSummary])
    def assistant_history() -> list[dict[str, Any]]:
        return history.conversations()

    @app.get("/api/assistant/history/{conversation_id}", dependencies=auth, response_model=ConversationDetail)
    def assistant_history_detail(conversation_id: str) -> ConversationDetail:
        messages = history.messages(conversation_id)
        if not messages:
            raise HTTPException(status_code=404, detail="Conversazione non trovata")
        return ConversationDetail(id=conversation_id, messages=messages)

    @app.delete("/api/assistant/history/{conversation_id}", dependencies=auth, status_code=status.HTTP_204_NO_CONTENT)
    def assistant_history_delete(conversation_id: str) -> None:
        if not history.delete(conversation_id):
            raise HTTPException(status_code=404, detail="Conversazione non trovata")
        if assistant is not None:
            assistant.reset(conversation_id)

    if STATIC_DIR.exists():
        app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

        @app.get("/", include_in_schema=False)
        def dashboard() -> FileResponse:
            return FileResponse(STATIC_DIR / "index.html")

    return app


def _client(request: Request) -> str | None:
    return request.client.host if request.client else None


def _elapsed_ms(started: float) -> int:
    return round((time.monotonic() - started) * 1000)


def _shared(services: HubServices, key: str, fallback: str) -> str:
    """A setting agreed with the phone (or changed on the settings page), else the .env value."""
    return services.db.get_value(f"setting:{key}") or fallback


def _place(services: HubServices) -> tuple[float, float]:
    try:
        latitude = float(services.db.get_value("setting:latitude") or services.settings.latitude)
        longitude = float(services.db.get_value("setting:longitude") or services.settings.longitude)
    except ValueError:
        return services.settings.latitude, services.settings.longitude
    return latitude, longitude


def wake_lead_minutes(services: HubServices) -> int:
    stored = services.db.get_value(WAKE_LEAD_KEY)
    try:
        return int(stored) if stored else services.settings.wake_lead_minutes
    except ValueError:
        return services.settings.wake_lead_minutes


def _fetch_weather(services: HubServices) -> dict[str, Any]:
    """Use the location agreed by sync when the phone and the PC have one."""
    use = getattr(services.weather, "use_location", None)
    if use is not None:
        latitude, longitude = _place(services)
        use(latitude, longitude, _shared(services, "city", services.settings.city))
    return services.weather.fetch()


def apply_alarm(services: HubServices, alarm: datetime | None) -> None:
    """Remember the phone's next alarm and ask Windows to wake in time for the dashboard."""
    settings = services.settings
    if alarm is not None and alarm.tzinfo is None:
        alarm = alarm.replace(tzinfo=settings.tz)
    services.db.set_value(ALARM_KEY, alarm.isoformat() if alarm else None)
    wake_at = alarm - timedelta(minutes=wake_lead_minutes(services)) if alarm else None
    if wake_at is not None and wake_at > services.now():
        scheduled = services.wake.schedule(wake_at)
    else:
        services.wake.cancel()
        scheduled = False
    services.db.set_value(WAKE_KEY, wake_at.isoformat() if scheduled and wake_at else None)


def restore_wake(services: HubServices) -> None:
    """On startup the saved alarm is scheduled again, because the process now lives with the dashboard."""
    stored = services.db.get_value(ALARM_KEY)
    if not stored:
        return
    alarm = datetime.fromisoformat(stored)
    if alarm.tzinfo is None:
        alarm = alarm.replace(tzinfo=services.settings.tz)
    if alarm > services.now():
        apply_alarm(services, alarm)
