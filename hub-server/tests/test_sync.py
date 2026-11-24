from __future__ import annotations

from datetime import datetime, timedelta

from app.history import ChatHistory


def _task(created: dict, **overrides) -> dict:
    body = {
        "uid": created["uid"],
        "title": created["title"],
        "due": created["due"],
        "done": created["done"],
        "createdAt": created["createdAt"],
        "completedAt": created["completedAt"],
        "updatedAt": created["updatedAt"],
        "deleted": False,
    }
    body.update(overrides)
    return body


def test_existing_tasks_reach_the_phone_on_the_first_sync(client):
    created = client.post("/api/tasks", json={"title": "Latte", "due": "2026-11-12"}).json()

    first = client.post("/api/sync", json={"since": 0}).json()

    assert first["revision"] >= 1
    assert first["tasks"][0]["uid"] == created["uid"]
    assert first["tasks"][0]["title"] == "Latte"
    assert first["settings"]
    assert any(item["key"] == "city" and item["value"] == "Milano" for item in first["settings"])
    assert first["geminiKey"] is None


def test_repeating_a_sync_does_not_duplicate_or_change_anything(client):
    client.post("/api/tasks", json={"title": "Latte"})
    first = client.post("/api/sync", json={"since": 0}).json()

    second = client.post("/api/sync", json={"since": first["revision"], "tasks": first["tasks"]}).json()

    assert second["revision"] == first["revision"]
    listed = client.get("/api/tasks").json()
    assert [item["title"] for item in listed] == ["Latte"]
    assert [item["uid"] for item in second["tasks"]] == [listed[0]["uid"]]


def test_newer_phone_edit_wins_and_a_tie_keeps_the_pc(client):
    created = client.post("/api/tasks", json={"title": "Dal PC"}).json()

    older = _task(created, title="Dal telefono vecchio", updatedAt="2020-01-01T00:00:00+01:00")
    client.post("/api/sync", json={"since": 0, "tasks": [older]})
    assert client.get("/api/tasks").json()[0]["title"] == "Dal PC"

    newer = _task(created, title="Dal telefono", updatedAt="2026-12-01T10:00:00+01:00")
    client.post("/api/sync", json={"since": 0, "tasks": [newer]})
    assert client.get("/api/tasks").json()[0]["title"] == "Dal telefono"


def test_deleted_task_does_not_come_back(client):
    created = client.post("/api/tasks", json={"title": "Via"}).json()
    deleted = _task(created, deleted=True, updatedAt="2026-12-01T10:00:00+01:00")

    client.post("/api/sync", json={"since": 0, "tasks": [deleted]})
    assert client.get("/api/tasks").json() == []

    revived = _task(created, title="Di nuovo", updatedAt="2020-01-01T00:00:00+01:00")
    client.post("/api/sync", json={"since": 0, "tasks": [revived]})
    assert client.get("/api/tasks").json() == []


def test_chat_messages_append_once_and_a_deleted_conversation_stays_deleted(client, services):
    history = ChatHistory(services.db, services.now)
    history.record("android-1", "ciao", "ecco", [])
    first = client.post("/api/sync", json={"since": 0}).json()
    assert len(first["messages"]) == 2

    again = client.post("/api/sync", json={"since": 0, "messages": first["messages"]}).json()
    assert len(history.messages("android-1")) == 2
    assert len([m for m in again["messages"] if m["conversationId"] == "android-1"]) == 2

    client.delete("/api/assistant/history/android-1")
    gone = client.post("/api/sync", json={"since": first["revision"]}).json()
    assert any(item["id"] == "android-1" for item in gone["deletedConversations"])

    client.post("/api/sync", json={"since": 0, "messages": first["messages"]})
    assert history.messages("android-1") == []
    assert client.get("/api/assistant/history/android-1").status_code == 404


def test_phone_alarm_schedules_the_pc_wake(client, services):
    alarm = (services.now() + timedelta(hours=8)).isoformat()

    state = client.post("/api/sync", json={"since": 0, "alarm": alarm}).json()

    assert services.wake.scheduled
    assert client.get("/api/alarm").json()["scheduled"] is True
    assert state["revision"] >= 0
    assert client.get("/api/today").json()["lastSync"]


def test_sync_requires_the_token(client):
    client.headers.pop("Authorization")

    assert client.post("/api/sync", json={"since": 0}).status_code == 401


def test_prefer_incoming_breaks_ties_toward_the_pc():
    from app.sync import SyncTask, merge_task, prefer_incoming

    moment = datetime.fromisoformat("2026-11-19T10:00:00+01:00")
    assert prefer_incoming(moment, moment, incoming_from_pc=False) is False
    assert prefer_incoming(moment, moment, incoming_from_pc=True) is True
    assert prefer_incoming(moment, moment + timedelta(seconds=1), False) is True
    local = SyncTask(uid="u", title="pc", createdAt=moment, updatedAt=moment)
    incoming = local.model_copy(update={"title": "phone"})
    assert merge_task(local, incoming, incoming_from_pc=False).title == "pc"
    assert merge_task(None, incoming, incoming_from_pc=False).title == "phone"
