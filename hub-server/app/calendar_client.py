from __future__ import annotations

import logging
from datetime import date, datetime, time, timedelta
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

log = logging.getLogger(__name__)

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


def visible_calendars(items: list[dict[str, Any]]) -> list[tuple[str, str]]:
    """Calendars the user has checked in Google Calendar."""
    found: list[tuple[str, str]] = []
    for item in items:
        if not item.get("selected"):
            continue
        calendar_id = item.get("id")
        if not calendar_id:
            continue
        found.append((calendar_id, item.get("summary") or calendar_id))
    return found


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
        end = datetime.combine(day + timedelta(days=1), time.min, tzinfo=self._tz)

        events: list[dict[str, Any]] = []
        seen: set[str] = set()
        failures: list[Exception] = []
        targets = self._resolve_calendars(service)
        for calendar_id, calendar_name in targets:
            try:
                items = self._list_events(service, calendar_id, start, end)
            except Exception as exc:
                log.warning("Calendar %s failed: %s", calendar_name, exc)
                failures.append(exc)
                continue
            added = 0
            for item in items:
                if item.get("status") == "cancelled":
                    continue
                key = item.get("iCalUID") or f"{calendar_id}:{item.get('id')}"
                if key in seen:
                    continue
                seen.add(key)
                events.append(normalize_event(item, calendar_name))
                added += 1
            log.info("Calendar %s: %d events on %s", calendar_name, added, day.isoformat())
        if not events and failures and len(failures) == len(targets):
            raise failures[0]
        return sort_events(events)

    def _resolve_calendars(self, service) -> list[tuple[str, str]]:
        # "primary" alone means every calendar checked in the Google Calendar UI.
        # An explicit list is used as-is.
        if self._calendar_ids != ("primary",):
            return [(calendar_id, calendar_id) for calendar_id in self._calendar_ids]

        items: list[dict[str, Any]] = []
        page_token = None
        while True:
            response = service.calendarList().list(pageToken=page_token, maxResults=250).execute()
            items.extend(response.get("items", []))
            page_token = response.get("nextPageToken")
            if not page_token:
                break
        return visible_calendars(items) or [("primary", "primary")]

    def _list_events(self, service, calendar_id: str, start: datetime, end: datetime) -> list[dict[str, Any]]:
        items: list[dict[str, Any]] = []
        page_token = None
        while True:
            response = (
                service.events()
                .list(
                    calendarId=calendar_id,
                    timeMin=start.isoformat(),
                    timeMax=end.isoformat(),
                    timeZone=self._tz.key,
                    singleEvents=True,
                    orderBy="startTime",
                    maxResults=250,
                    pageToken=page_token,
                )
                .execute()
            )
            items.extend(response.get("items", []))
            page_token = response.get("nextPageToken")
            if not page_token:
                return items
