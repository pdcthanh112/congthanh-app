import json
from types import SimpleNamespace

import httpx
import pytest

from app.adapters.claude import ClaudeLLM
from app.adapters.graphql_shop import GraphqlShop
from app.adapters.ollama import OllamaLLM
from app.adapters.vllm import VllmLLM
from app.domain.chat import Message
from app.ports import LLMError, ShopError, ShopUnavailable, TextDelta, TurnEnd


async def texts(llm, messages):
    out = [e async for e in llm.turn("sys", messages, [])]
    assert isinstance(out[-1], TurnEnd)
    return [e.text for e in out if isinstance(e, TextDelta)], out[-1]


async def test_ollama_stream():
    lines = [{"message": {"content": "Xin "}}, {"message": {"content": "chào"}}, {"done": True}]

    def handler(req: httpx.Request):
        body = json.loads(req.content)
        assert body["messages"][0] == {"role": "system", "content": "sys"} and body["stream"] is True
        return httpx.Response(200, content="\n".join(json.dumps(x) for x in lines).encode())

    llm = OllamaLLM(httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://o"), "mistral")
    deltas, end = await texts(llm, [Message("user", "hi")])
    assert deltas == ["Xin ", "chào"] and end.content == [{"type": "text", "text": "Xin chào"}]


async def test_ollama_error():
    llm = OllamaLLM(
        httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(500)), base_url="http://o"), "m"
    )
    with pytest.raises(LLMError):
        await texts(llm, [Message("user", "hi")])


class FakeStream:
    def __init__(self, chunks, final):
        self.chunks, self.final = chunks, final

    async def __aenter__(self):
        async def gen():
            for c in self.chunks:
                yield c

        self.text_stream = gen()
        return self

    async def __aexit__(self, *a):
        return False

    async def get_final_message(self):
        return self.final


class Block(SimpleNamespace):
    def model_dump(self, **_):
        return {k: v for k, v in self.__dict__.items() if v is not None}


def final(stop, *blocks):
    usage = SimpleNamespace(input_tokens=10, output_tokens=5, cache_read_input_tokens=0)
    return SimpleNamespace(stop_reason=stop, content=list(blocks), usage=usage)


def fake_client(chunks, message, seen):
    def stream(**kw):
        seen.update(kw)
        return FakeStream(chunks, message)

    return SimpleNamespace(beta=SimpleNamespace(messages=SimpleNamespace(stream=stream)))


async def test_claude_turn_request_and_tool_calls():
    seen = {}
    tool_use = Block(type="tool_use", id="t1", name="search_products", input={"query": "shirt"})
    message = final("tool_use", Block(type="text", text="ab"), tool_use)
    llm = ClaudeLLM(fake_client(["a", "b"], message, seen), "claude-opus-5-5", "low", 100)
    tools = [{"name": "search_products"}]
    events = [e async for e in llm.turn("sys", [Message("user", "hi")], tools)]
    assert [e.text for e in events if isinstance(e, TextDelta)] == ["a", "b"]
    end = events[-1]
    assert end.stop_reason == "tool_use" and end.tool_calls[0].name == "search_products"
    assert end.content[1] == {"type": "tool_use", "id": "t1", "name": "search_products", "input": {"query": "shirt"}}
    assert seen["fallbacks"] == "default" and seen["output_config"] == {"effort": "low"} and seen["tools"] == tools
    assert seen["system"] == [{"type": "text", "text": "sys", "cache_control": {"type": "ephemeral"}}]
    assert seen["messages"] == [{"role": "user", "content": "hi"}]


async def test_claude_omits_tools_when_none():
    seen = {}
    llm = ClaudeLLM(fake_client([], final("end_turn", Block(type="text", text="")), seen), "m", "low", 100)
    [e async for e in llm.turn("s", [Message("user", "x")], [])]
    assert "tools" not in seen


async def test_graphql_shop_forwards_token_and_maps_errors():
    seen = []

    def handler(req: httpx.Request):
        seen.append(req)
        body = json.loads(req.content)
        if "addToCart" in body["query"]:
            return httpx.Response(
                200, json={"errors": [{"message": "product is out of stock", "extensions": {"code": "CONFLICT"}}]}
            )
        if "myOrders" in body["query"]:
            return httpx.Response(
                200, json={"errors": [{"message": "boom", "extensions": {"code": "INTERNAL_SERVER_ERROR"}}]}
            )
        return httpx.Response(200, json={"data": {"myCart": {"id": "1", "lines": []}}})

    shop = GraphqlShop(httpx.AsyncClient(transport=httpx.MockTransport(handler)), "http://router/graphql")
    assert (await shop.cart("tok"))["id"] == "1"
    assert seen[0].headers["authorization"] == "Bearer tok" and str(seen[0].url) == "http://router/graphql"
    with pytest.raises(ShopError, match="out of stock"):
        await shop.add_to_cart("tok", "p1", 1)
    with pytest.raises(ShopUnavailable):
        await shop.orders("tok", 5)


async def test_graphql_shop_network_error_is_unavailable():
    def handler(req):
        raise httpx.ConnectError("down")

    shop = GraphqlShop(httpx.AsyncClient(transport=httpx.MockTransport(handler)), "http://router/graphql")
    with pytest.raises(ShopUnavailable):
        await shop.search("x", min_price=None, max_price=None, sort=None, limit=5)


async def test_vllm_stream():
    events = [
        {"choices": [{"delta": {"role": "assistant"}}]},
        {"choices": [{"delta": {"content": "Xin "}}]},
        {"choices": [{"delta": {"content": "chào"}, "finish_reason": "stop"}]},
    ]

    def handler(req: httpx.Request):
        body = json.loads(req.content)
        assert req.url.path == "/openai/v1/chat/completions"
        assert body["model"] == "shop-assistant" and body["max_tokens"] == 100 and body["stream"] is True
        assert body["messages"][0] == {"role": "system", "content": "sys"}
        sse = "".join(f"data: {json.dumps(e)}\n\n" for e in events) + "data: [DONE]\n\n"
        return httpx.Response(200, content=sse.encode())

    http = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://v")
    llm = VllmLLM(http, "shop-assistant", "/openai/v1/chat/completions", 100)
    deltas, _ = await texts(llm, [Message("user", "hi")])
    assert deltas == ["Xin ", "chào"]


async def test_vllm_error():
    http = httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(503)), base_url="http://v")
    with pytest.raises(LLMError):
        await texts(VllmLLM(http, "m", "/v1/chat/completions", 10), [Message("user", "hi")])
