import json
from types import SimpleNamespace

import httpx
import pytest

from app.adapters.claude import ClaudeLLM
from app.adapters.ollama import OllamaLLM
from app.adapters.search import SearchServiceClient
from app.domain.chat import Message
from app.ports import LLMError


async def test_search_client_maps_hits():
    def handler(req: httpx.Request):
        assert req.url.path == "/api/ecommerce/search/products" and req.url.params["size"] == "2"
        hits = [
            {"id": "1", "name": "A", "slug": "a", "price": 10.5, "currency": "VND", "status": "ACTIVE"},
            {"id": "2", "name": "B", "slug": "b", "price": 1, "currency": "VND", "status": "INACTIVE"},
        ]
        return httpx.Response(200, json={"data": {"hits": hits, "total": 2}})

    c = SearchServiceClient(httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://s"))
    out = await c.search("áo", 2)
    assert [p.id for p in out] == ["1"] and out[0].price == 10.5


async def test_ollama_stream():
    lines = [{"message": {"content": "Xin "}}, {"message": {"content": "chào"}}, {"done": True}]

    def handler(req: httpx.Request):
        body = json.loads(req.content)
        assert body["messages"][0] == {"role": "system", "content": "sys"} and body["stream"] is True
        return httpx.Response(200, content="\n".join(json.dumps(x) for x in lines).encode())

    llm = OllamaLLM(httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://o"), "mistral")
    assert [t async for t in llm.stream("sys", [Message("user", "hi")])] == ["Xin ", "chào"]


async def test_ollama_error():
    llm = OllamaLLM(
        httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(500)), base_url="http://o"), "m"
    )
    with pytest.raises(LLMError):
        [t async for t in llm.stream("s", [Message("user", "hi")])]


class FakeStream:
    def __init__(self, chunks, stop):
        self.chunks, self.stop = chunks, stop

    async def __aenter__(self):
        async def gen():
            for c in self.chunks:
                yield c

        self.text_stream = gen()
        return self

    async def __aexit__(self, *a):
        return False

    async def get_final_message(self):
        return SimpleNamespace(stop_reason=self.stop)


def fake_client(chunks, stop, seen):
    def stream(**kw):
        seen.update(kw)
        return FakeStream(chunks, stop)

    return SimpleNamespace(beta=SimpleNamespace(messages=SimpleNamespace(stream=stream)))


async def test_claude_stream_request():
    seen = {}
    llm = ClaudeLLM(fake_client(["a", "b"], "end_turn", seen), "claude-opus-5-5", "low", 100)
    assert [t async for t in llm.stream("sys", [Message("user", "hi")])] == ["a", "b"]
    assert seen["fallbacks"] == "default" and seen["system"] == "sys" and seen["output_config"] == {"effort": "low"}
    assert seen["messages"] == [{"role": "user", "content": "hi"}]


async def test_claude_refusal():
    llm = ClaudeLLM(fake_client([], "refusal", {}), "m", "low", 100)
    with pytest.raises(LLMError):
        [t async for t in llm.stream("s", [Message("user", "x")])]
