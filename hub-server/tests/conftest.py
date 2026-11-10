from __future__ import annotations

from datetime import date, datetime
from zoneinfo import ZoneInfo

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.db import Database
from app.main import HubServices, create_app

TOKEN = "test-token"
TZ = ZoneInfo("Europe/Rome")


class FakeWeather:
    def __init__(self):
        self.calls = 0
        self.fail = False

    def fetch(self):
        self.calls += 1
        if self.fail:
            raise RuntimeError("offline")
        return {"city": "Milano", "current": {"temperature": 12.5, "description": "Sereno", "icon": "clear"}}


class FakeCalendar:
    def __init__(self):
        self.error: Exception | None = None

    def events_for(self, day: date):
        if self.error:
            raise self.error
        return [{"id": "1", "title": "Riunione", "start": f"{day}T10:00:00+01:00", "allDay": False}]


class FakeWake:
    def __init__(self):
        self.scheduled: list[datetime] = []
        self.cancelled = 0

    def schedule(self, at: datetime) -> bool:
        self.scheduled.append(at)
        return True

    def cancel(self) -> bool:
        self.cancelled += 1
        return True


class Clock:
    def __init__(self):
        self.value = datetime(2026, 11, 10, 21, 0, tzinfo=TZ)

    def __call__(self) -> datetime:
        return self.value


@pytest.fixture
def services(tmp_path):
    return HubServices(
        settings=Settings(api_token=TOKEN, data_dir=tmp_path, wake_lead_minutes=1),
        db=Database(":memory:"),
        weather=FakeWeather(),
        calendar=FakeCalendar(),
        wake=FakeWake(),
        now=Clock(),
    )


@pytest.fixture
def client(services):
    with TestClient(create_app(services), headers={"Authorization": f"Bearer {TOKEN}"}) as test_client:
        yield test_client
