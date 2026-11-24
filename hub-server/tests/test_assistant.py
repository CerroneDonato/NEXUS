from __future__ import annotations

import httpx
import pytest
from fastapi.testclient import TestClient

from app.assistant import GeminiClient, thinking_config
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

    response = chat(client, "Cosa ho mercoledì?")

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


def test_simple_questions_skip_the_model_and_stay_in_history(make_client):
    model = ScriptedModel(say("Prego"))
    client = make_client(model)

    response = chat(client, "Che tempo fa?")

    assert response.status_code == 200
    assert response.json() == {"reply": "Adesso 13°, sereno.", "actions": []}
    assert model.requests == []
    chat(client, "Grazie")
    assert [c["role"] for c in model.requests[0]] == ["user", "model", "user"]


def test_chats_are_stored_and_listed_newest_first(make_client):
    model = ScriptedModel(say("Piacere"), say("Ti chiami Donato"), say("Ciao"))
    client = make_client(model)

    chat(client, "Mi chiamo Donato", conversation="web1")
    chat(client, "Come mi chiamo?", conversation="web1")
    chat(client, "(Mi sto allenando: rispondi breve.) Ciao", conversation="sport-9")

    listing = client.get("/api/assistant/history").json()
    assert [(c["id"], c["source"], c["messages"]) for c in listing] == [("sport-9", "sport", 2), ("web1", "pc", 4)]
    assert listing[0]["title"] == "Ciao"
    assert listing[1]["title"] == "Mi chiamo Donato"
    detail = client.get("/api/assistant/history/web1").json()
    assert [(m["role"], m["text"]) for m in detail["messages"]] == [
        ("user", "Mi chiamo Donato"), ("bot", "Piacere"), ("user", "Come mi chiamo?"), ("bot", "Ti chiami Donato"),
    ]


def test_actions_are_kept_in_the_history(make_client):
    model = ScriptedModel(call("create_task", title="Latte"), say("Fatto."))
    client = make_client(model)

    chat(client, "Aggiungi latte")

    messages = client.get("/api/assistant/history/c1").json()["messages"]
    assert messages[1]["actions"] == ["Task creato: Latte"]


def test_reset_keeps_the_history_but_forgets_the_context(make_client):
    model = ScriptedModel(say("Piacere"), say("Non lo so"))
    client = make_client(model)
    chat(client, "Mi chiamo Donato")

    client.delete("/api/assistant/chat/c1")
    chat(client, "Come mi chiamo?")

    assert len(model.requests[1]) == 1
    assert len(client.get("/api/assistant/history/c1").json()["messages"]) == 4


def test_old_chat_can_be_continued_after_the_hub_restarts(make_client, services):
    first = make_client(ScriptedModel(say("Piacere")))
    chat(first, "Mi chiamo Donato", conversation="web7")

    model = ScriptedModel(say("Ti chiami Donato"))
    restarted = make_client(model)
    chat(restarted, "Come mi chiamo?", conversation="web7")

    assert [c["role"] for c in model.requests[0]] == ["user", "model", "user"]
    assert model.requests[0][0]["parts"][0]["text"] == "Mi chiamo Donato"


def test_history_can_be_deleted_and_requires_token(make_client):
    client = make_client(ScriptedModel(say("Ciao")))
    chat(client, "Ciao", conversation="x1")

    assert client.delete("/api/assistant/history/x1").status_code == 204
    assert client.get("/api/assistant/history").json() == []
    assert client.get("/api/assistant/history/x1").status_code == 404
    assert client.delete("/api/assistant/history/x1").status_code == 404
    assert TestClient(client.app).get("/api/assistant/history").status_code == 401


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


def test_thinking_stays_shallow():
    assert thinking_config("gemini-3.5-flash-lite") == {"thinkingLevel": "MINIMAL"}
    assert thinking_config("gemini-2.5-flash") == {"thinkingBudget": 0}
    assert thinking_config("gemini-2.5-flash", "low") == {}
    assert thinking_config("gemini-2.0-flash") == {}


def test_thought_signature_is_sent_back_unchanged(make_client):
    signed = {
        "role": "model",
        "parts": [{"functionCall": {"name": "list_tasks", "args": {}}, "thoughtSignature": "sig-1"}],
    }
    model = ScriptedModel(signed, say("Nessun task aperto."))
    client = make_client(model)

    response = chat(client, "Ho qualcosa di urgente?")

    assert response.status_code == 200
    echoed = next(item for item in model.requests[1] if item["role"] == "model")
    assert echoed["parts"][0]["thoughtSignature"] == "sig-1"


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
    assert b'"thinkingLevel":"MINIMAL"' in seen["body"]
    with pytest.raises(Exception, match="limite gratuito"):
        gemini.generate("sys", [{"role": "user", "parts": [{"text": "quota"}]}], [])


def test_english_chat_gets_english_prompt_skips_quick_answers_and_translates_actions(make_client):
    model = ScriptedModel(say("It's 10 past nine."), call("create_task", title="Milk", due="2026-11-11"), say("Done."))
    client = make_client(model)

    quick = client.post("/api/assistant/chat", json={"conversationId": "e1", "message": "che ore sono?", "language": "en"})
    assert quick.json()["reply"] == "It's 10 past nine."  # went to the model, not the local Italian answer
    assert "Always answer in English" in model.systems[0]
    assert "Tuesday 10 November 2026" in model.systems[0]

    reply = client.post("/api/assistant/chat", json={"conversationId": "e2", "message": "add milk tomorrow", "language": "en"})
    assert reply.json()["actions"] == ["Task created: Milk (due 2026-11-11)"]


def test_language_defaults_to_italian_and_rejects_unknown_values(make_client):
    model = ScriptedModel(say("Ciao"))
    client = make_client(model)
    assert chat(client, "Ciao").status_code == 200
    assert "martedì 10 novembre 2026" in model.systems[0]
    bad = client.post("/api/assistant/chat", json={"conversationId": "x", "message": "hi", "language": "fr"})
    assert bad.status_code == 422


def test_model_can_be_switched_at_runtime():
    def handler(request: httpx.Request) -> httpx.Response:
        handler.urls.append(str(request.url))
        return httpx.Response(200, json={"candidates": [{"content": {"role": "model", "parts": [{"text": "ok"}]}}]})

    handler.urls = []
    gemini = GeminiClient("key", "gemini-2.5-flash", http=httpx.Client(transport=httpx.MockTransport(handler)))
    gemini.set_model("gemini-3.5-flash-lite")
    gemini.generate("sys", [{"role": "user", "parts": [{"text": "hi"}]}], [])
    assert "models/gemini-3.5-flash-lite:" in handler.urls[0]
