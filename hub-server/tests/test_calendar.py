from __future__ import annotations

import pytest
from google.auth.exceptions import RefreshError

from app.calendar_client import CalendarNotConfigured, load_credentials, normalize_event, sort_events, visible_calendars


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


def test_expired_google_grant_asks_for_a_new_login(tmp_path, monkeypatch):
    token = tmp_path / "google_token.json"
    token.write_text("{}", encoding="utf-8")

    class Expired:
        valid = False
        expired = True
        refresh_token = "present"

        def refresh(self, _request):
            raise RefreshError("Token has been expired or revoked.")

    monkeypatch.setattr("google.oauth2.credentials.Credentials.from_authorized_user_file", lambda *_a, **_k: Expired())

    with pytest.raises(CalendarNotConfigured, match="scaduto o è stato rimosso"):
        load_credentials(token)


def test_visible_calendars_keeps_only_checked_ones():
    items = [
        {"id": "primary-id", "summary": "Donato", "selected": True, "primary": True},
        {"id": "work@group.calendar.google.com", "summary": "Lavoro", "selected": True},
        {"id": "hidden@group.calendar.google.com", "summary": "Vecchio", "selected": False},
        {"id": "it.italian#holiday@group.calendar.google.com", "summary": "Festività", "selected": True},
    ]

    assert visible_calendars(items) == [
        ("primary-id", "Donato"),
        ("work@group.calendar.google.com", "Lavoro"),
        ("it.italian#holiday@group.calendar.google.com", "Festività"),
    ]
