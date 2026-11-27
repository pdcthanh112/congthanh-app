from fastapi.testclient import TestClient

from app import main
from app.service import ChatService
from app.tools import ToolBox
from tests.fakes import FakeShop, MemHistory, ScriptedLLM, text_turn, tool_turn


def test_sse_chat():
    with TestClient(main.app) as c:
        llm = ScriptedLLM(tool_turn(("t1", "search_products", {"query": "shirt"})), text_turn("Có ", "áo"))
        main.app.state.service = ChatService(
            llm, ToolBox(FakeShop()), MemHistory(), store="S", turns=2, ttl=60, max_chars=100
        )
        r = c.post("/api/ecommerce/chatbot/chat", json={"message": "shirt", "sessionId": "session-0001"})
        assert r.status_code == 200 and r.headers["content-type"].startswith("text/event-stream")
        events = [b.split("\n")[0].removeprefix("event: ") for b in r.text.strip().split("\n\n")]
        assert events == ["tool_started", "products", "tool_finished", "delta", "delta", "done"]
        assert c.post("/api/ecommerce/chatbot/chat", json={"message": " "}).status_code == 400
        assert c.delete("/api/ecommerce/chatbot/sessions/session-0001").json()["status"] == "Success"
