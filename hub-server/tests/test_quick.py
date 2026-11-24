from __future__ import annotations

from datetime import datetime
from zoneinfo import ZoneInfo

import pytest

from app.assistant_tools import ToolResult
from app.quick import quick_answer

NOW = datetime(2026, 11, 10, 8, 30, tzinfo=ZoneInfo("Europe/Rome"))

WEATHER = {
    "city": "Milano",
    "current": {"temperature": 9.5, "description": "Coperto"},
    "today": {"min": 6.2, "max": 13.4, "precipitationProbability": 70},
    "daily": [
        {"date": "2026-11-10", "min": 6.2, "max": 13.4, "precipitationProbability": 70, "description": "Pioggia"},
        {"date": "2026-11-11", "min": 5.0, "max": 15.6, "precipitationProbability": 10, "description": "Sereno"},
    ],
}


class FakeTools:
    def __init__(self, events=None, tasks=None, alarm=None, calendar_error=None):
        self.events = events or []
        self.tasks = tasks or []
        self.alarm = alarm
        self.calendar_error = calendar_error
        self.calls: list[tuple[str, dict]] = []

    def call(self, name, args):
        self.calls.append((name, args))
        if name == "get_weather":
            return ToolResult(WEATHER)
        if name == "list_events":
            if self.calendar_error:
                return ToolResult({"error": self.calendar_error})
            return ToolResult({"date": args["date"], "events": self.events})
        if name == "list_tasks":
            return ToolResult({"tasks": self.tasks})
        if name == "get_alarm":
            return ToolResult({"nextAlarm": self.alarm})
        raise AssertionError(name)


def test_weather_now_and_today():
    assert quick_answer("Che tempo fa?", FakeTools(), NOW) == "Adesso 10°, coperto. Oggi tra 6° e 13°, pioggia al 70%."


def test_weather_tomorrow_uses_daily_forecast():
    assert quick_answer("Che tempo farà domani?", FakeTools(), NOW) == "Domani sereno, tra 5° e 16°, pioggia al 10%."


@pytest.mark.parametrize(
    ("message", "expected"),
    [
        ("Mi serve l'ombrello?", "Sì, meglio prenderlo: pioggia al 70% oggi."),
        ("Mi serve l'ombrello domani?", "No, pioggia al 10% domani."),
    ],
)
def test_umbrella(message, expected):
    assert quick_answer(message, FakeTools(), NOW) == expected


def test_agenda_today_lists_events():
    tools = FakeTools(events=[
        {"title": "Riunione", "start": "2026-11-10T10:00:00+01:00", "allDay": False},
        {"title": "Compleanno", "start": "2026-11-10", "allDay": True},
    ])
    reply = quick_answer("Che impegni ho oggi?", tools, NOW)
    assert reply == "Oggi hai 2 impegni:\n- 10:00 Riunione\n- Tutto il giorno Compleanno"
    assert tools.calls == [("list_events", {"date": "2026-11-10"})]


@pytest.mark.parametrize("message", ["Cosa ho da fare oggi?", "Cosa ho oggi?", "Cosa ho in programma oggi?", "Cose da fare?"])
def test_day_question_lists_events_and_tasks(message):
    tools = FakeTools(
        events=[{"title": "Riunione", "start": "2026-11-10T10:00:00+01:00", "allDay": False}],
        tasks=[
            {"id": 1, "title": "Assicurazione", "due": "2026-11-09", "overdue": True},
            {"id": 2, "title": "Libro", "due": None, "overdue": False},
        ],
    )
    assert quick_answer(message, tools, NOW) == (
        "Oggi hai 1 impegno:\n- 10:00 Riunione\nE 2 task aperti:\n- Assicurazione (scaduto)\n- Libro"
    )


def test_day_question_with_nothing_to_do():
    assert quick_answer("Cosa ho da fare oggi?", FakeTools(), NOW) == "Oggi non hai impegni.\nNessun task da chiudere."


def test_day_question_tomorrow_keeps_only_tasks_due_by_then():
    tools = FakeTools(tasks=[
        {"id": 1, "title": "Latte", "due": "2026-11-11", "overdue": False},
        {"id": 2, "title": "Dentista", "due": "2026-11-20", "overdue": False},
        {"id": 3, "title": "Libro", "due": None, "overdue": False},
    ])
    assert quick_answer("Cosa ho da fare domani?", tools, NOW) == (
        "Domani non hai impegni.\nTask da chiudere entro domani (1):\n- Latte (entro il 11/11)"
    )


def test_day_question_still_lists_tasks_when_calendar_fails():
    tools = FakeTools(calendar_error="Google Calendar non è collegato all'hub", tasks=[{"id": 1, "title": "Libro", "due": None}])
    assert quick_answer("Cosa ho da fare oggi?", tools, NOW) == (
        "Google Calendar non è collegato all'hub.\nE 1 task aperto:\n- Libro"
    )


def test_agenda_tomorrow_and_empty_day():
    tools = FakeTools()
    assert quick_answer("Che impegni ho domani?", tools, NOW) == "Domani non hai impegni."
    assert tools.calls == [("list_events", {"date": "2026-11-11"})]


def test_agenda_reports_calendar_error():
    tools = FakeTools(calendar_error="Google Calendar non è collegato all'hub")
    assert quick_answer("Che impegni ho oggi?", tools, NOW) == "Google Calendar non è collegato all'hub."


def test_tasks_with_due_notes():
    tools = FakeTools(tasks=[
        {"id": 1, "title": "Assicurazione", "due": "2026-11-09", "overdue": True},
        {"id": 2, "title": "Latte", "due": "2026-11-10", "overdue": False},
        {"id": 3, "title": "Dentista", "due": "2026-11-20", "overdue": False},
        {"id": 4, "title": "Libro", "due": None, "overdue": False},
    ])
    assert quick_answer("Che task ho in scadenza?", tools, NOW) == (
        "Hai 4 task aperti:\n- Assicurazione (scaduto)\n- Latte (oggi)\n- Dentista (entro il 20/11)\n- Libro"
    )


def test_alarm():
    assert quick_answer("A che ora è la sveglia?", FakeTools(alarm="2026-11-11T07:00:00+01:00"), NOW) == (
        "La prossima sveglia è domani alle 07:00."
    )
    assert quick_answer("Sveglia?", FakeTools(), NOW) == "Non c'è nessuna sveglia impostata sul telefono."


def test_sport_hint_in_parentheses_is_ignored():
    assert quick_answer("(Mi sto allenando: rispondi breve.) che tempo fa", FakeTools(), NOW).startswith("Adesso 10°")


@pytest.mark.parametrize(
    "message",
    [
        "Aggiungi un task per domani: comprare il latte",
        "Segna come fatto il task del latte",
        "Cosa ho venerdì?",
        "Che tempo fa il 15?",
        "Ciao",
        "Raccontami una storia lunga su come organizzare al meglio la mia giornata di domani",
    ],
)
def test_everything_else_goes_to_the_model(message):
    assert quick_answer(message, FakeTools(), NOW) is None
