from __future__ import annotations

from datetime import datetime, timedelta

from app.calendar_client import CalendarNotConfigured


def test_requests_without_token_are_rejected(client):
    # Arrange
    client.headers.pop("Authorization")

    # Act
    response = client.get("/api/today")

    # Assert
    assert response.status_code == 401


def test_requests_with_wrong_token_are_rejected(client):
    response = client.get("/api/tasks", headers={"Authorization": "Bearer wrong"})

    assert response.status_code == 401


def test_health_is_public(client):
    client.headers.pop("Authorization")

    assert client.get("/api/health").status_code == 200


def test_dashboard_is_served_without_token(client):
    client.headers.pop("Authorization")

    response = client.get("/")

    assert response.status_code == 200
    assert "Daily Hub" in response.text
    assert response.headers["Cache-Control"] == "no-cache"
    assert client.get("/static/app.js").status_code == 200


def test_task_lifecycle(client):
    # Arrange / Act: create
    created = client.post("/api/tasks", json={"title": " Comprare il latte ", "due": "2026-11-12"})

    # Assert
    assert created.status_code == 201
    task = created.json()
    assert task["title"] == "Comprare il latte"
    assert task["due"] == "2026-11-12"
    assert task["done"] is False

    # Act: complete
    done = client.patch(f"/api/tasks/{task['id']}", json={"done": True}).json()
    assert done["done"] is True
    assert done["completedAt"] is not None
    assert client.get("/api/tasks").json() == []
    assert len(client.get("/api/tasks", params={"includeDone": True}).json()) == 1

    # Act: delete
    assert client.delete(f"/api/tasks/{task['id']}").status_code == 204
    assert client.delete(f"/api/tasks/{task['id']}").status_code == 404


def test_patch_can_clear_due_date(client):
    task = client.post("/api/tasks", json={"title": "A", "due": "2026-11-12"}).json()

    updated = client.patch(f"/api/tasks/{task['id']}", json={"due": None}).json()

    assert updated["due"] is None
    assert updated["title"] == "A"


def test_empty_title_is_rejected(client):
    assert client.post("/api/tasks", json={"title": ""}).status_code == 422


def test_tasks_sorted_by_due_date_and_overdue_flagged(client):
    client.post("/api/tasks", json={"title": "senza scadenza"})
    client.post("/api/tasks", json={"title": "dopodomani", "due": "2026-11-12"})
    client.post("/api/tasks", json={"title": "ieri", "due": "2026-11-09"})

    tasks = client.get("/api/tasks").json()

    assert [t["title"] for t in tasks] == ["ieri", "dopodomani", "senza scadenza"]
    assert [t["overdue"] for t in tasks] == [True, False, False]


def test_today_aggregates_weather_events_and_tasks(client):
    client.post("/api/tasks", json={"title": "Palestra"})

    body = client.get("/api/today").json()

    assert body["date"] == "2026-11-10"
    assert body["weather"]["city"] == "Milano"
    assert body["events"][0]["title"] == "Riunione"
    assert body["tasks"][0]["title"] == "Palestra"
    assert body["weatherError"] is None and body["calendarError"] is None


def test_today_weather_is_cached(client, services):
    client.get("/api/today")
    client.get("/api/today")

    assert services.weather.calls == 1


def test_today_survives_provider_failures(client, services):
    services.weather.fail = True
    services.calendar.error = CalendarNotConfigured("Google Calendar non configurato")

    body = client.get("/api/today").json()

    assert body["weather"] is None
    assert body["weatherError"] == "Meteo non disponibile"
    assert body["calendarError"] == "Google Calendar non configurato"


def test_future_alarm_schedules_wake_with_lead_time(client, services):
    alarm = "2026-11-11T07:00:00+01:00"

    state = client.put("/api/alarm", json={"nextAlarm": alarm}).json()

    expected_wake = datetime.fromisoformat(alarm) - timedelta(minutes=1)
    assert services.wake.scheduled == [expected_wake]
    assert state["scheduled"] is True
    assert datetime.fromisoformat(state["wakeAt"]) == expected_wake
    assert client.get("/api/today").json()["alarm"]["nextAlarm"].startswith("2026-11-11T07:00")


def test_clearing_alarm_cancels_wake(client, services):
    client.put("/api/alarm", json={"nextAlarm": "2026-11-11T07:00:00+01:00"})

    state = client.put("/api/alarm", json={"nextAlarm": None}).json()

    assert services.wake.cancelled == 1
    assert state == {"nextAlarm": None, "wakeAt": None, "scheduled": False}


def test_past_alarm_is_not_scheduled(client, services):
    state = client.put("/api/alarm", json={"nextAlarm": "2026-11-10T07:00:00+01:00"}).json()

    assert services.wake.scheduled == []
    assert state["scheduled"] is False
