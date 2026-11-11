from fastapi.testclient import TestClient

from app import main
from app.service import ChatService
from tests.fakes import SHIRT, FakeLLM, FakeSearch, MemHistory


def test_sse_chat():
    with TestClient(main.app) as c:
        main.app.state.service = ChatService(
            FakeLLM(), FakeSearch([SHIRT]), MemHistory(), store="S", context_size=3, turns=2, ttl=60, max_chars=100
        )
        r = c.post("/api/ecommerce/chatbot/chat", json={"message": "shirt", "sessionId": "session-0001"})
        assert r.status_code == 200 and r.headers["content-type"].startswith("text/event-stream")
        events = [b.split("\n")[0].removeprefix("event: ") for b in r.text.strip().split("\n\n")]
        assert events == ["products", "delta", "delta", "done"]
        assert c.post("/api/ecommerce/chatbot/chat", json={"message": " "}).status_code == 400
        assert c.delete("/api/ecommerce/chatbot/sessions/session-0001").json()["status"] == "Success"
