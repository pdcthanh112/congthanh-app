import pytest

from app.domain.translation import Format, InvalidRequest, TranslateRequest, cache_key
from app.service import TranslateService


class FakeTranslator:
    model = "m"

    def __init__(self):
        self.calls: list[list[str]] = []

    async def translate(self, req, texts):
        self.calls.append(texts)
        return [f"[{req.target}]{t}" for t in texts]


class MemCache:
    def __init__(self, broken=False):
        self.d: dict[str, str] = {}
        self.broken = broken

    async def get_many(self, keys):
        if self.broken:
            raise ConnectionError("down")
        return [self.d.get(k) for k in keys]

    async def set_many(self, items, ttl):
        if self.broken:
            raise ConnectionError("down")
        self.d.update(items)


async def test_cache_and_dedupe():
    tr, cache = FakeTranslator(), MemCache()
    svc = TranslateService(tr, cache, 60, 10, 1000)
    req = TranslateRequest(texts=("Camiseta", "Vaqueros", "Camiseta", "  "), target="en")
    assert await svc.translate(req) == ["[en]Camiseta", "[en]Vaqueros", "[en]Camiseta", "  "]
    assert tr.calls == [["Camiseta", "Vaqueros"]]
    # Lần hai lấy hết từ cache.
    assert await svc.translate(TranslateRequest(texts=("Vaqueros",), target="en")) == ["[en]Vaqueros"]
    assert len(tr.calls) == 1


async def test_cache_down_still_translates():
    tr = FakeTranslator()
    svc = TranslateService(tr, MemCache(broken=True), 60, 10, 1000)
    assert await svc.translate(TranslateRequest(texts=("x",), target="ja")) == ["[ja]x"]


@pytest.mark.parametrize(
    "req",
    [
        TranslateRequest(texts=("a",), target="xx"),
        TranslateRequest(texts=(), target="en"),
        TranslateRequest(texts=("a",) * 11, target="en"),
        TranslateRequest(texts=("a" * 2000,), target="en"),
    ],
)
async def test_validation(req):
    with pytest.raises(InvalidRequest):
        await TranslateService(FakeTranslator(), MemCache(), 60, 10, 1000).translate(req)


def test_cache_key_varies():
    a = cache_key("m", TranslateRequest(texts=(), target="en"), "x")
    assert a != cache_key("m", TranslateRequest(texts=(), target="es"), "x")
    assert a != cache_key("m", TranslateRequest(texts=(), target="en", format=Format.HTML), "x")
    assert a != cache_key("m2", TranslateRequest(texts=(), target="en"), "x")
