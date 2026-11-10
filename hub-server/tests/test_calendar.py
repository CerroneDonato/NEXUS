from __future__ import annotations

from app.calendar_client import normalize_event, sort_events


def test_normalize_timed_and_all_day_events():
    timed = normalize_event(
        {"id": "a", "summary": "Dentista", "start": {"dateTime": "2026-11-10T15:00:00+01:00"},
         "end": {"dateTime": "2026-11-10T16:00:00+01:00"}, "location": "Via Roma"},
        "Personale",
    )
    all_day = normalize_event({"id": "b", "start": {"date": "2026-11-10"}, "end": {"date": "2026-11-11"}}, "Lavoro")

    assert timed["allDay"] is False and timed["location"] == "Via Roma"
    assert all_day["allDay"] is True
    assert all_day["title"] == "(senza titolo)"


def test_all_day_events_come_first():
    events = [
        {"allDay": False, "start": "2026-11-10T09:00:00+01:00"},
        {"allDay": True, "start": "2026-11-10"},
        {"allDay": False, "start": "2026-11-10T08:00:00+01:00"},
    ]

    ordered = sort_events(events)

    assert [e["start"] for e in ordered] == ["2026-11-10", "2026-11-10T08:00:00+01:00", "2026-11-10T09:00:00+01:00"]
