from __future__ import annotations

import httpx
import pytest
from fastapi.testclient import TestClient

from app.assistant import GeminiClient
from app.main import create_app
from conftest import TOKEN


def call(name, **args):
    return {"role": "model", "parts": [{"functionCall": {"name": name, "args": args}}]}


def say(text):
    return {"role": "model", "parts": [{"text": text}]}


class ScriptedModel:
    """Fake Gemini: returns the scripted replies in order and records every request."""

    def __init__(self, *replies):
        self.replies = list(replies)
        self.requests: list[list[dict]] = []
        self.systems: list[str] = []

    def generate(self, system, contents, tools):
        self.systems.append(system)
        self.requests.append([dict(c) for c in contents])
        return self.replies.pop(0)


@pytest.fixture
def make_client(services):
    clients = []

    def factory(model):
        services.assistant_model = model
        client = TestClient(create_app(services), headers={"Authorization": f"Bearer {TOKEN}"})
        clients.append(client)
        return client

    yield factory
    for client in clients:
        client.close()


def chat(client, message, conversation="c1"):
    return client.post("/api/assistant/chat", json={"conversationId": conversation, "message": message})


def function_responses(contents):
    return [p["functionResponse"] for c in contents for p in c["parts"] if "functionResponse" in p]


def test_create_task_tool_writes_to_database(make_client):
    model = ScriptedModel(call("create_task", title="Comprare il latte", due="2026-11-11"), say("Fatto, ci penso io."))
    client = make_client(model)

    response = chat(client, "Ricordami di comprare il latte domani")

    assert response.status_code == 200
    assert response.json() == {"reply": "Fatto, ci penso io.", "actions": ["Task creato: Comprare il latte (entro 2026-11-11)"]}
    tasks = client.get("/api/tasks").json()
    assert [(t["title"], t["due"]) for t in tasks] == [("Comprare il latte", "2026-11-11")]
    created = function_responses(model.requests[1])[0]
    assert created["name"] == "create_task"
    assert created["response"]["created"]["title"] == "Comprare il latte"


def test_list_events_uses_requested_date(make_client, services):
    model = ScriptedModel(call("list_events", date="2026-11-11"), say("Domani hai una riunione alle 10."))
    client = make_client(model)

    response = chat(client, "Cosa ho domani?")

    assert response.status_code == 200
    result = function_responses(model.requests[1])[0]["response"]
    assert result["date"] == "2026-11-11"
    assert result["events"][0]["start"].startswith("2026-11-11T10:00")


def test_system_prompt_knows_date_city_and_name(make_client):
    model = ScriptedModel(say("Ciao"))
    client = make_client(model)

    chat(client, "Ciao")

    assert "martedì 10 novembre 2026" in model.systems[0]
    assert "2026-11-10" in model.systems[0]
    assert "Milano" in model.systems[0]
    assert "Sei Lilith" in model.systems[0]


def test_conversation_keeps_history_and_can_be_reset(make_client):
    model = ScriptedModel(say("Piacere"), say("Ti chiami Donato"), say("Non lo so"))
    client = make_client(model)

    chat(client, "Mi chiamo Donato")
    chat(client, "Come mi chiamo?")
    assert [c["role"] for c in model.requests[1]] == ["user", "model", "user"]

    assert client.delete("/api/assistant/chat/c1").status_code == 204
    chat(client, "Come mi chiamo?")
    assert len(model.requests[2]) == 1


def test_tool_errors_are_reported_to_the_model(make_client):
    model = ScriptedModel(call("complete_task", task_id=999), say("Non trovo quel task."))
    client = make_client(model)

    response = chat(client, "Ho finito il task 999")

    assert response.status_code == 200
    assert response.json()["actions"] == []
    assert function_responses(model.requests[1])[0]["response"] == {"error": "Task non trovato"}


def test_stops_after_too_many_tool_rounds(make_client):
    model = ScriptedModel(*[call("list_tasks") for _ in range(10)])
    client = make_client(model)

    response = chat(client, "Loop")

    assert response.status_code == 502
    assert "troppi passaggi" in response.json()["detail"]
    assert len(model.requests) == 6


def test_assistant_disabled_without_key(client):
    assert client.get("/api/assistant").json() == {"name": "Lilith", "enabled": False}
    response = chat(client, "Ciao")
    assert response.status_code == 503
    assert "GEMINI_API_KEY" in response.json()["detail"]


def test_assistant_requires_token(make_client):
    client = make_client(ScriptedModel(say("Ciao")))
    client.headers.pop("Authorization")

    assert chat(client, "Ciao").status_code == 401


def test_message_length_is_limited(make_client):
    client = make_client(ScriptedModel(say("Ciao")))

    assert chat(client, "x" * 2001).status_code == 422


def test_gemini_client_sends_tools_and_maps_quota_error():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["key"] = request.headers["x-goog-api-key"]
        seen["body"] = request.read()
        if b"quota" in seen["body"]:
            return httpx.Response(429, json={"error": {"message": "quota"}})
        return httpx.Response(200, json={"candidates": [{"content": {"role": "model", "parts": [{"text": "ok"}]}}]})

    gemini = GeminiClient("secret", "gemini-test", http=httpx.Client(transport=httpx.MockTransport(handler)))

    content = gemini.generate("sys", [{"role": "user", "parts": [{"text": "hi"}]}], [{"name": "get_weather"}])

    assert content["parts"][0]["text"] == "ok"
    assert seen["key"] == "secret"
    assert b"functionDeclarations" in seen["body"]
    with pytest.raises(Exception, match="limite gratuito"):
        gemini.generate("sys", [{"role": "user", "parts": [{"text": "quota"}]}], [])
