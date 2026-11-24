"""Everyday questions answered straight from the hub's data, without a round trip to the model."""

from __future__ import annotations

import math
import re
from datetime import date, datetime, timedelta
from typing import Any, Protocol

from .assistant_tools import ToolResult

WEEKDAYS = ["lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica"]

MAX_WORDS = 10

# Anything that asks Lilith to change something goes to the model.
ACTION_WORDS = {
    "aggiungi", "aggiungere", "crea", "creare", "segna", "completa", "completato", "finito", "fatto",
    "sposta", "ricorda", "ricordami", "cancella", "elimina", "modifica", "rinomina", "togli", "metti",
    "imposta", "scrivi", "manda", "chiama", "rimanda", "annulla",
}
# A day other than today or tomorrow: the model resolves it.
OTHER_DAY_WORDS = {
    "lunedì", "lunedi", "martedì", "martedi", "mercoledì", "mercoledi", "giovedì", "giovedi",
    "venerdì", "venerdi", "sabato", "domenica", "settimana", "weekend", "dopodomani", "ieri",
    "prossimo", "prossima", "prossimi", "prossime", "mese",
}
WEATHER_WORDS = {"meteo", "piove", "pioverà", "pioggia", "temperatura", "gradi", "nevica", "neve"}
AGENDA_WORDS = {"impegni", "impegno", "agenda", "appuntamenti", "appuntamento", "eventi", "riunioni", "calendario"}
TASK_WORDS = {"task", "todo", "scadenza", "scadenze"}
# "Cosa ho da fare oggi?", "Cosa ho in programma?": the whole day, events and tasks together.
DAY_WORDS = {"programma", "fare"}

_LEADING_NOTE = re.compile(r"^\s*\([^)]*\)\s*")
_WORD = re.compile(r"[a-zàèéìòù0-9]+")


class Tools(Protocol):
    def call(self, name: str, args: dict[str, Any]) -> ToolResult: ...


def quick_answer(message: str, tools: Tools, now: datetime) -> str | None:
    """Return a ready reply for a simple question, or None when the model should handle it."""
    text = _LEADING_NOTE.sub("", message).lower()
    words = _WORD.findall(text)
    if not words or len(words) > MAX_WORDS:
        return None
    vocabulary = set(words)
    if vocabulary & ACTION_WORDS or vocabulary & OTHER_DAY_WORDS or any(w.isdigit() for w in words):
        return None

    today = now.date()
    tomorrow = "domani" in vocabulary
    day = today + timedelta(days=1) if tomorrow else today

    if "ombrello" in vocabulary:
        return _umbrella(tools, day, tomorrow)
    if vocabulary & WEATHER_WORDS or ("tempo" in vocabulary and vocabulary & {"che", "fa", "farà"}):
        return _weather(tools, day, tomorrow)
    if vocabulary & TASK_WORDS:
        return _tasks(tools, today)
    if vocabulary & AGENDA_WORDS:
        return _agenda(tools, day, tomorrow)
    if vocabulary & DAY_WORDS or _has_pair(words, "cosa", "ho"):
        return _day(tools, day, tomorrow, today)
    if "sveglia" in vocabulary:
        return _alarm(tools, today)
    return None


def _has_pair(words: list[str], first: str, second: str) -> bool:
    return any(a == first and b == second for a, b in zip(words, words[1:]))


def _deg(value: Any) -> int:
    return int(math.floor(float(value) + 0.5))


def _forecast_for(weather: dict[str, Any], day: date, tomorrow: bool) -> dict[str, Any] | None:
    if not tomorrow:
        return weather.get("today")
    return next((d for d in weather.get("daily") or [] if d.get("date") == day.isoformat()), None)


def _weather(tools: Tools, day: date, tomorrow: bool) -> str | None:
    weather = tools.call("get_weather", {}).data
    if "error" in weather:
        return None
    forecast = _forecast_for(weather, day, tomorrow)
    if tomorrow:
        if not forecast or forecast.get("min") is None or forecast.get("max") is None:
            return None
        reply = f"Domani {str(forecast.get('description') or '').lower()}, tra {_deg(forecast['min'])}° e {_deg(forecast['max'])}°"
        rain = forecast.get("precipitationProbability")
        return reply + (f", pioggia al {rain}%." if rain is not None else ".")

    current = weather.get("current") or {}
    if current.get("temperature") is None:
        return None
    reply = f"Adesso {_deg(current['temperature'])}°, {str(current.get('description') or '').lower()}."
    if forecast and forecast.get("min") is not None and forecast.get("max") is not None:
        reply += f" Oggi tra {_deg(forecast['min'])}° e {_deg(forecast['max'])}°"
        rain = forecast.get("precipitationProbability")
        reply += f", pioggia al {rain}%." if rain is not None else "."
    return reply


def _umbrella(tools: Tools, day: date, tomorrow: bool) -> str | None:
    weather = tools.call("get_weather", {}).data
    if "error" in weather:
        return None
    forecast = _forecast_for(weather, day, tomorrow) or {}
    rain = forecast.get("precipitationProbability")
    if rain is None:
        return None
    when = "domani" if tomorrow else "oggi"
    if rain >= 50:
        return f"Sì, meglio prenderlo: pioggia al {rain}% {when}."
    if rain >= 20:
        return f"Forse: pioggia al {rain}% {when}. Uno piccolo in borsa basta."
    return f"No, pioggia al {rain}% {when}."


def _agenda(tools: Tools, day: date, tomorrow: bool) -> str:
    result = tools.call("list_events", {"date": day.isoformat()}).data
    if "error" in result:
        return f"{result['error']}."
    events = result.get("events") or []
    label = "Domani" if tomorrow else "Oggi"
    if not events:
        return f"{label} non hai impegni."
    noun = "impegno" if len(events) == 1 else "impegni"
    lines = [f"{label} hai {len(events)} {noun}:"]
    for event in events:
        start = event.get("start") or ""
        when = "Tutto il giorno" if event.get("allDay") or len(start) < 16 else start[11:16]
        lines.append(f"- {when} {event.get('title') or 'Senza titolo'}")
    return "\n".join(lines)


def _day(tools: Tools, day: date, tomorrow: bool, today: date) -> str:
    """Events of the day followed by the tasks: today every open task, tomorrow those due by then."""
    agenda = _agenda(tools, day, tomorrow)
    tasks = tools.call("list_tasks", {}).data.get("tasks") or []
    if tomorrow:
        tasks = [t for t in tasks if t.get("due") and t["due"] <= day.isoformat()]
    if not tasks:
        ending = " entro domani." if tomorrow else "."
        return f"{agenda}\nNessun task da chiudere{ending}"
    if tomorrow:
        header = f"Task da chiudere entro domani ({len(tasks)}):"
    else:
        header = f"E {len(tasks)} task {'aperto' if len(tasks) == 1 else 'aperti'}:"
    return f"{agenda}\n{_task_lines(tasks, today, header)}"


def _tasks(tools: Tools, today: date) -> str:
    tasks = tools.call("list_tasks", {}).data.get("tasks") or []
    if not tasks:
        return "Non hai task aperti."
    noun = "task aperto" if len(tasks) == 1 else "task aperti"
    return _task_lines(tasks, today, f"Hai {len(tasks)} {noun}:")


def _task_lines(tasks: list[dict[str, Any]], today: date, header: str) -> str:
    lines = [header]
    for task in tasks:
        due = task.get("due")
        if task.get("overdue"):
            note = " (scaduto)"
        elif due == today.isoformat():
            note = " (oggi)"
        elif due:
            d = date.fromisoformat(due)
            note = f" (entro il {d.day}/{d.month})"
        else:
            note = ""
        lines.append(f"- {task.get('title')}{note}")
    return "\n".join(lines)


def _alarm(tools: Tools, today: date) -> str:
    iso = tools.call("get_alarm", {}).data.get("nextAlarm")
    if not iso:
        return "Non c'è nessuna sveglia impostata sul telefono."
    at = datetime.fromisoformat(iso)
    if at.date() == today:
        when = "oggi"
    elif at.date() == today + timedelta(days=1):
        when = "domani"
    else:
        when = f"{WEEKDAYS[at.weekday()]} {at.day}"
    return f"La prossima sveglia è {when} alle {at:%H:%M}."
