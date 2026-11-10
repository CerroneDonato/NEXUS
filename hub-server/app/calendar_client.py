from __future__ import annotations

from datetime import date, datetime, time, timedelta
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

SCOPES = ["https://www.googleapis.com/auth/calendar.readonly"]


class CalendarNotConfigured(RuntimeError):
    pass


def load_credentials(token_file: Path):
    from google.auth.transport.requests import Request
    from google.oauth2.credentials import Credentials

    if not token_file.exists():
        raise CalendarNotConfigured(
            "Google Calendar non configurato: esegui 'python -m app.google_auth' sul PC dell'hub"
        )
    creds = Credentials.from_authorized_user_file(str(token_file), SCOPES)
    if not creds.valid:
        if creds.expired and creds.refresh_token:
            creds.refresh(Request())
            token_file.write_text(creds.to_json(), encoding="utf-8")
        else:
            raise CalendarNotConfigured("Token Google non valido: ripeti 'python -m app.google_auth'")
    return creds


def normalize_event(raw: dict[str, Any], calendar_name: str) -> dict[str, Any]:
    start = raw.get("start", {})
    end = raw.get("end", {})
    all_day = "date" in start and "dateTime" not in start
    return {
        "id": raw.get("id"),
        "title": raw.get("summary") or "(senza titolo)",
        "start": start.get("dateTime") or start.get("date"),
        "end": end.get("dateTime") or end.get("date"),
        "allDay": all_day,
        "location": raw.get("location"),
        "calendar": calendar_name,
    }


def sort_events(events: list[dict[str, Any]]) -> list[dict[str, Any]]:
    # All-day events first, then by start time.
    return sorted(events, key=lambda e: (not e["allDay"], e["start"] or ""))


class GoogleCalendarClient:
    def __init__(self, token_file: Path, calendar_ids: tuple[str, ...], tz: ZoneInfo):
        self._token_file = token_file
        self._calendar_ids = calendar_ids
        self._tz = tz

    def events_for(self, day: date) -> list[dict[str, Any]]:
        from googleapiclient.discovery import build

        creds = load_credentials(self._token_file)
        service = build("calendar", "v3", credentials=creds, cache_discovery=False)
        start = datetime.combine(day, time.min, tzinfo=self._tz)
        end = start + timedelta(days=1)

        events: list[dict[str, Any]] = []
        for calendar_id in self._calendar_ids:
            response = (
                service.events()
                .list(
                    calendarId=calendar_id,
                    timeMin=start.isoformat(),
                    timeMax=end.isoformat(),
                    singleEvents=True,
                    orderBy="startTime",
                    maxResults=50,
                )
                .execute()
            )
            name = response.get("summary", calendar_id)
            events.extend(
                normalize_event(item, name) for item in response.get("items", []) if item.get("status") != "cancelled"
            )
        return sort_events(events)
