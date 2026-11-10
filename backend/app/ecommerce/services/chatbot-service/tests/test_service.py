import pytest

from app.domain.chat import InvalidRequest, Message, system_prompt
from app.service import ChatService
from tests.fakes import SHIRT, FakeLLM, FakeSearch, MemHistory


def svc(llm=None, search=None, history=None, turns=2):
    return ChatService(
        llm or FakeLLM(),
        search or FakeSearch([SHIRT]),
        history or MemHistory(),
        store="Shop",
        context_size=3,
        turns=turns,
        ttl=60,
        max_chars=100,
    )


async def collect(it):
    return [(e.type, e.data) async for e in it]


async def test_stream_and_history():
    llm, hist = FakeLLM(), MemHistory()
    s = svc(llm, history=hist)
    ev = await collect(s.chat("áo thun", "session-0001", "u1"))
    assert ev[0] == ("products", [SHIRT.__dict__])
    assert [d for t, d in ev if t == "delta"] == ["Xin ", "chào"]
    assert ev[-1] == ("done", {"sessionId": "session-0001"})
    assert "/products/ao-thun-cotton" in llm.calls[0][0]
    # Lượt hai gửi kèm lịch sử lượt một.
    await collect(s.chat("còn size L?", "session-0001", "u1"))
    assert llm.calls[1][1] == [
        Message("user", "áo thun"),
        Message("assistant", "Xin chào"),
        Message("user", "còn size L?"),
    ]
    # Người khác cùng sessionId không thấy lịch sử.
    await collect(s.chat("hi", "session-0001", "u2"))
    assert llm.calls[2][1] == [Message("user", "hi")]


async def test_history_trimmed():
    hist = MemHistory()
    s = svc(history=hist, turns=1)
    for q in ("a", "b", "c"):
        await collect(s.chat(q, "session-0001", None))
    assert [m.content for m in hist.d["chatbot:history:anon:session-0001"]] == ["c", "Xin chào"]


async def test_llm_error_and_search_down():
    hist = MemHistory()
    ev = await collect(svc(FakeLLM(fail=True), FakeSearch(fail=True), hist).chat("x", "session-0001", None))
    assert ev[0] == ("products", []) and ev[-1] == ("error", "busy")
    assert hist.d == {}  # câu trả lời dở dang không lưu


@pytest.mark.parametrize(("text", "sid"), [("  ", None), ("x" * 101, None), ("ok", "bad id!"), ("ok", "short")])
def test_validation(text, sid):
    with pytest.raises(InvalidRequest):
        svc().prepare(text, sid)


def test_prompt_without_products():
    assert "no matching products" in system_prompt("Shop", [])
