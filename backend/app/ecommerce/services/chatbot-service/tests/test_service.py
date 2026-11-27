import pytest

from app.domain.chat import InvalidRequest, Message, Turn, window
from app.ports import TurnEnd
from app.service import MAX_TOOL_ROUNDS, MAX_WRITES_PER_TURN, ChatService, Customer
from app.tools import ToolBox
from tests.fakes import SHIRT, FakeShop, MemHistory, ScriptedLLM, text_turn, tool_turn

ME = Customer("u1", "token-u1")


def svc(llm, shop=None, history=None, turns=3):
    return ChatService(
        llm, ToolBox(shop or FakeShop()), history or MemHistory(), store="Shop", turns=turns, ttl=60, max_chars=100
    )


async def collect(it):
    return [(e.type, e.data) async for e in it]


async def test_answer_without_tools_is_streamed_and_saved():
    llm, hist = ScriptedLLM(text_turn("Xin ", "chào")), MemHistory()
    ev = await collect(svc(llm, history=hist).chat("hi", "session-0001", ME))
    assert [d for t, d in ev if t == "delta"] == ["Xin ", "chào"]
    assert ev[-1] == ("done", {"sessionId": "session-0001"})
    saved = hist.d["chatbot:history:u1:session-0001"][0].messages
    assert [m.role for m in saved] == ["user", "assistant"]


async def test_tool_loop_runs_search_and_sends_results_back():
    llm = ScriptedLLM(tool_turn(("t1", "search_products", {"query": "shirt", "max_price": 30})), text_turn("Có áo"))
    shop = FakeShop()
    ev = await collect(svc(llm, shop).chat("áo shirt dưới 30$", "session-0001", ME))

    types = [t for t, _ in ev]
    assert types == ["tool_started", "products", "tool_finished", "delta", "done"]
    assert dict(ev)["products"] == [SHIRT]
    assert shop.calls[0] == ("search", ("shirt", None, 30.0, None, 6))
    # Lần gọi thứ hai nhận lại tool_use của model và tool_result tương ứng.
    second = llm.calls[1][1]
    assert second[1].content[0]["type"] == "tool_use"
    assert second[2].role == "user" and second[2].content[0]["tool_use_id"] == "t1"
    assert '"name":"Cotton T-Shirt"' in second[2].content[0]["content"]


async def test_guest_gets_no_account_tools_and_is_blocked_if_model_calls_one():
    llm = ScriptedLLM(tool_turn(("t1", "view_cart", {})), text_turn("Hãy đăng nhập"))
    ev = await collect(svc(llm).chat("giỏ của tôi?", "session-0001", Customer()))
    names = {t["name"] for t in llm.calls[0][2]}
    assert "view_cart" not in names and "search_products" in names
    assert ("tool_finished", {"id": "t1", "name": "view_cart", "ok": False}) in ev
    result = llm.calls[1][1][-1].content[0]
    assert result["is_error"] is True and "not signed in" in result["content"]


async def test_add_to_cart_uses_customer_token_and_emits_cart():
    shop = FakeShop()
    llm = ScriptedLLM(tool_turn(("t1", "add_to_cart", {"product_id": "p1", "quantity": 2})), text_turn("Đã thêm"))
    ev = await collect(svc(llm, shop).chat("thêm 2 áo", "session-0001", ME))
    assert shop.calls[0] == ("add_to_cart", ("token-u1", "p1", 2))
    cart = dict(ev)["cart_updated"]
    assert cart["itemCount"] == 2


async def test_cart_writes_are_capped_per_turn():
    calls = [(f"t{i}", "add_to_cart", {"product_id": "p1", "quantity": 1}) for i in range(MAX_WRITES_PER_TURN + 1)]
    shop = FakeShop()
    llm = ScriptedLLM(tool_turn(*calls), text_turn("xong"))
    await collect(svc(llm, shop).chat("thêm nhiều", "session-0001", ME))
    assert len([c for c in shop.calls if c[0] == "add_to_cart"]) == MAX_WRITES_PER_TURN
    results = llm.calls[1][1][-1].content
    assert results[-1]["is_error"] is True and len(results) == MAX_WRITES_PER_TURN + 1


async def test_tool_rounds_are_bounded():
    endless = [tool_turn((f"t{i}", "search_products", {"query": "shirt"})) for i in range(MAX_TOOL_ROUNDS + 2)]
    llm = ScriptedLLM(*endless)
    ev = await collect(svc(llm).chat("shirt", "session-0001", ME))
    assert len(llm.calls) == MAX_TOOL_ROUNDS + 1
    assert llm.calls[-1][1][-1].role == "system"  # lần cuối được dặn trả lời ngay
    assert ev[-1][0] == "done"


async def test_llm_error_is_reported_and_turn_not_saved():
    hist = MemHistory()
    ev = await collect(svc(ScriptedLLM(fail=True), history=hist).chat("x", "session-0001", ME))
    assert ev[-1] == ("error", "busy")
    assert hist.d == {}


async def test_refusal_stops_turn():
    llm = ScriptedLLM([TurnEnd([], "refusal")])
    ev = await collect(svc(llm).chat("x", "session-0001", ME))
    assert ev[-1] == ("error", "the assistant can't help with that request")


async def test_shop_down_becomes_tool_error():
    llm = ScriptedLLM(tool_turn(("t1", "search_products", {"query": "shirt"})), text_turn("Thử lại sau"))
    ev = await collect(svc(llm, FakeShop(down=True)).chat("shirt", "session-0001", ME))
    assert ("tool_finished", {"id": "t1", "name": "search_products", "ok": False}) in ev
    assert "temporarily unavailable" in llm.calls[1][1][-1].content[0]["content"]


async def test_history_is_private_per_customer():
    hist, llm = MemHistory(), ScriptedLLM(text_turn("a"), text_turn("b"), text_turn("c"))
    s = svc(llm, history=hist)
    await collect(s.chat("một", "session-0001", ME))
    await collect(s.chat("hai", "session-0001", ME))
    assert llm.calls[1][1][0].content == "một"
    await collect(s.chat("ba", "session-0001", Customer("u2", "t2")))
    assert llm.calls[2][1] == [Message("user", "ba")]


async def test_model_without_tools_gets_prefetched_catalogue():
    llm = ScriptedLLM(text_turn("Có áo"), supports_tools=False)
    ev = await collect(svc(llm).chat("cotton shirt", "session-0001", ME))
    assert ev[0] == ("products", [SHIRT])
    system, _, tools = llm.calls[0]
    assert tools == [] and "Cotton T-Shirt" in system and "<catalogue>" in system


def test_window_keeps_history_verbatim_until_limit_then_drops_thinking():
    thinking = {"type": "thinking", "thinking": "", "signature": "s"}
    turns = [Turn([Message("user", q), Message("assistant", [thinking, {"type": "text", "text": "a"}])]) for q in "abc"]
    assert window(turns, 3)[1].content[0] == thinking
    trimmed = window(turns, 2)
    assert [m.content for m in trimmed if m.role == "user"] == ["b", "c"]
    assert all(b["type"] != "thinking" for m in trimmed if m.role == "assistant" for b in m.content)


@pytest.mark.parametrize(("text", "sid"), [("  ", None), ("x" * 101, None), ("ok", "bad id!"), ("ok", "short")])
def test_validation(text, sid):
    with pytest.raises(InvalidRequest):
        svc(ScriptedLLM()).prepare(text, sid)
