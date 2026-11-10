from app.domain.chat import Message, Product
from app.ports import LLMError


class FakeLLM:
    def __init__(self, chunks=("Xin ", "chào"), fail=False):
        self.chunks, self.fail, self.calls = chunks, fail, []

    async def stream(self, system, messages):
        self.calls.append((system, list(messages)))
        for c in self.chunks:
            yield c
        if self.fail:
            raise LLMError("busy")


class FakeSearch:
    def __init__(self, products=None, fail=False):
        self.products, self.fail = products or [], fail

    async def search(self, query, limit):
        if self.fail:
            raise RuntimeError("down")
        return self.products[:limit]


class MemHistory:
    def __init__(self):
        self.d: dict[str, list[Message]] = {}

    async def load(self, key, turns):
        return self.d.get(key, [])[-2 * turns :]

    async def append(self, key, messages, keep_turns, ttl):
        self.d[key] = (self.d.get(key, []) + messages)[-2 * keep_turns :]

    async def clear(self, key):
        self.d.pop(key, None)


SHIRT = Product(id="p1", name="Áo thun cotton", slug="ao-thun-cotton", price=199000, currency="VND")
