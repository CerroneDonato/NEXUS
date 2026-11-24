from __future__ import annotations

from datetime import datetime, timedelta

from app.config import read_version


def _sync(client, **extra):
    body = {"since": 0, "tasks": [], "messages": [], "deletedConversations": [], **extra}
    return client.post("/api/sync", json=body)


def test_info_reports_version_address_and_masked_token(client):
    info = client.get("/api/info").json()
    assert info["version"] == read_version()
    assert info["phoneAddress"].startswith("http://") and info["phoneAddress"].endswith(":8765")
    assert info["tokenHint"] == "…oken"
    assert "test-token" not in str(info)
    assert info["syncLog"] == []
    assert info["calendar"]["connected"] is True
    assert info["wake"]["leadMinutes"] == 1


def test_info_requires_the_token(client):
    assert client.get("/api/info", headers={"Authorization": "Bearer nope"}).status_code == 401


def test_successful_sync_is_logged_with_counts_and_returns_the_version(client):
    client.post("/api/tasks", json={"title": "Dal PC"})
    response = _sync(client, tasks=[{"uid": "p1", "title": "Dal telefono", "done": False, "deleted": False,
                                      "createdAt": "2026-11-10T20:00:00+01:00", "updatedAt": "2026-11-10T20:00:00+01:00"}])
    assert response.status_code == 200
    assert response.json()["version"] == read_version()
    entry = client.get("/api/info").json()["syncLog"][0]
    assert entry["ok"] is True and entry["status"] == 200
    assert entry["received"]["tasks"] == 1
    assert entry["sent"]["tasks"] >= 1
    assert entry["client"]


def test_wrong_token_and_bad_body_are_logged_too(client):
    assert client.post("/api/sync", json={}, headers={"Authorization": "Bearer nope"}).status_code == 401
    assert client.post("/api/sync", json={"tasks": "x"}).status_code == 422
    log = client.get("/api/info").json()["syncLog"]
    assert [e["status"] for e in log] == [422, 401]
    assert log[1]["detail"] == "Token non valido"
    assert all(e["ok"] is False for e in log)


def test_log_keeps_the_last_ten(client):
    for index in range(15):
        client.post("/api/tasks", json={"title": f"Task {index}"})
        _sync(client)
    assert len(client.get("/api/info").json()["syncLog"]) == 10


def test_idle_syncs_are_not_logged_but_still_counted_as_contact(client):
    _sync(client)
    _sync(client)
    info = client.get("/api/info").json()
    assert info["syncLog"] == []
    assert info["lastSync"] is not None


def test_first_idle_sync_after_a_failure_is_logged_once(client):
    assert client.post("/api/sync", json={"tasks": "x"}).status_code == 422
    _sync(client)
    _sync(client)
    log = client.get("/api/info").json()["syncLog"]
    assert [(e["ok"], e["status"]) for e in log] == [(True, 200), (False, 422)]


def test_revision_moves_with_tasks_and_settings(client):
    first = client.get("/api/revision").json()
    client.post("/api/tasks", json={"title": "Nuovo"})
    second = client.get("/api/revision").json()
    assert second["revision"] > first["revision"]
    client.put("/api/settings", json={"city": "Torino"})
    third = client.get("/api/revision").json()
    assert third["revision"] == second["revision"] and third["marker"] != second["marker"]
    assert client.get("/api/revision", headers={"Authorization": "Bearer nope"}).status_code == 401


def test_model_name_is_shared_validated_and_shown(client):
    info = client.put("/api/settings", json={"geminiModel": "gemini-4-flash"}).json()
    assert info["assistant"]["model"] == "gemini-4-flash"
    settings = {s["key"]: s["value"] for s in _sync(client).json()["settings"]}
    assert settings["gemini_model"] == "gemini-4-flash"
    assert client.put("/api/settings", json={"geminiModel": "bad model!"}).status_code == 422
    assert client.put("/api/settings", json={"geminiModel": ""}).status_code == 422


def test_phone_can_change_the_model_through_sync(client):
    stamp = "2099-01-01T00:00:00+01:00"
    _sync(client, settings=[{"key": "gemini_model", "value": "gemini-9", "updatedAt": stamp}])
    assert client.get("/api/info").json()["assistant"]["model"] == "gemini-9"


def test_settings_page_changes_reach_the_phone_and_the_assistant(client):
    info = client.put("/api/settings", json={"city": " Torino ", "latitude": 45.07, "longitude": 7.69,
                                             "assistantName": "Eva"}).json()
    assert info["place"] == {"city": "Torino", "latitude": 45.07, "longitude": 7.69}
    assert info["assistant"]["name"] == "Eva"
    assert client.get("/api/assistant").json()["name"] == "Eva"
    settings = {s["key"]: s["value"] for s in _sync(client).json()["settings"]}
    assert settings["city"] == "Torino"
    assert settings["assistant_name"] == "Eva"


def test_settings_are_validated(client):
    assert client.put("/api/settings", json={"latitude": 120}).status_code == 422
    assert client.put("/api/settings", json={"city": "   "}).status_code == 422
    assert client.put("/api/settings", json={"wakeLeadMinutes": -1}).status_code == 422


def test_new_wake_lead_moves_the_scheduled_wake(client, services):
    alarm = services.now() + timedelta(hours=9)
    client.put("/api/alarm", json={"nextAlarm": alarm.isoformat()})
    info = client.put("/api/settings", json={"wakeLeadMinutes": 10}).json()
    assert info["wake"]["leadMinutes"] == 10
    assert services.wake.scheduled[-1] == alarm - timedelta(minutes=10)
    assert datetime.fromisoformat(info["alarm"]["wakeAt"]) == alarm - timedelta(minutes=10)
