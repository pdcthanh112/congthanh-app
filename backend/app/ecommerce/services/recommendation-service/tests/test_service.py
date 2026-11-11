from datetime import UTC, datetime

from app.domain.embedding import HashingEmbedder, cosine
from app.domain.ranking import Kind, Reason, Scored
from app.service import RecommendationService

NOW = datetime(2026, 11, 10, tzinfo=UTC)


class FakeStore:
    def __init__(self):
        self.products = {}
        self.events = []
        self.orders = []
        self.embedded = set()

    async def upsert_product(self, pid, name, cat, active):
        self.products[pid] = (name, cat, active)

    async def delete_product(self, pid):
        self.products.pop(pid, None)

    async def product(self, pid):
        return self.products.get(pid)

    async def record(self, subject, pid, kind, weight, at):
        self.events.append((subject, pid, kind, weight))

    async def record_order(self, customer, pids, at):
        self.orders.append((customer, pids))

    async def bought_together(self, pid, limit, min_support):
        return [Scored("b1", 0.9, Reason.BOUGHT_TOGETHER, "c")][:limit] if pid == "p1" else []

    async def bought_with_any(self, pids, limit, min_support):
        return [Scored("b1", 0.9, Reason.BOUGHT_TOGETHER, "c"), Scored("p1", 0.5, Reason.BOUGHT_TOGETHER, "c")]

    async def upsert_stream_trending(self, pid, score, window_end):
        self.stream = (pid, score, window_end)

    async def trending(self, limit, cat, now):
        return [Scored("t1", 3.0, Reason.TRENDING, "c"), Scored("t2", 1.0, Reason.TRENDING, "d")][:limit]

    async def recent_interactions(self, subject, since, limit):
        return [("p1", Kind.PURCHASE, 5.0), ("p2", Kind.VIEW, 1.0)] if subject == "u1" else []

    async def products_to_embed(self, limit):
        todo = [(pid, n, c) for pid, (n, c, a) in self.products.items() if a and pid not in self.embedded]
        return todo[:limit]

    async def mark_embedded(self, pids, at):
        self.embedded |= set(pids)

    async def prune(self, before):
        return 0


class FakeIndex:
    enabled = True

    def __init__(self):
        self.vecs = {}

    async def upsert(self, items):
        for pid, cat, v in items:
            self.vecs[pid] = (cat, v)

    async def delete(self, pid):
        self.vecs.pop(pid, None)

    async def similar(self, pid, limit):
        if pid not in self.vecs:
            return []
        q = self.vecs[pid][1]
        ranked = sorted(((cosine(q, v), p, c) for p, (c, v) in self.vecs.items() if p != pid), reverse=True)
        return [Scored(p, s, Reason.SIMILAR, c) for s, p, c in ranked[:limit]]

    async def vectors(self, pids):
        return {p: self.vecs[p][1] for p in pids if p in self.vecs}

    async def nearest(self, vector, limit, exclude):
        ranked = sorted(
            ((cosine(vector, v), p, c) for p, (c, v) in self.vecs.items() if p not in exclude), reverse=True
        )
        return [Scored(p, s, Reason.FOR_YOU, c) for s, p, c in ranked[:limit]]


def make():
    store, index = FakeStore(), FakeIndex()
    return RecommendationService(store, index, HashingEmbedder(128), clock=lambda: NOW), store, index


async def test_product_events_maintain_vectors_and_similar():
    svc, store, index = make()
    await svc.on_product_upserted("p1", "Black Cotton Tee", "shirts", True)
    await svc.on_product_upserted("p2", "White Cotton Tee", "shirts", True)
    await svc.on_product_upserted("p3", "Electric Rice Cooker", "kitchen", True)
    sim = await svc.similar("p1", 2)
    assert [s.product_id for s in sim] == ["p2", "p3"] and sim[0].reason == Reason.SIMILAR
    await svc.on_product_upserted("p2", "White Cotton Tee", "shirts", False)
    assert "p2" not in index.vecs
    await svc.on_product_deleted("p3")
    assert "p3" not in index.vecs and "p3" not in store.products


async def test_similar_falls_back_without_vector():
    svc, store, _ = make()
    store.products["p1"] = ("x", "c", True)
    out = await svc.similar("p1", 3)
    assert [s.product_id for s in out] == ["b1", "t1", "t2"]


async def test_for_you_blends_and_excludes_seen():
    svc, _store, _index = make()
    for pid, name, cat in [("p1", "Cotton Tee", "c"), ("p2", "Slim Jeans", "d"), ("n1", "Crew Neck Cotton Tee", "c")]:
        await svc.on_product_upserted(pid, name, cat, True)
    out = await svc.for_you("u1", 5)
    ids = [s.product_id for s in out]
    assert "p1" not in ids and "p2" not in ids
    assert ids[0] == "b1" and "n1" in ids and "t1" in ids
    anon = await svc.for_you(None, 1)
    assert [s.product_id for s in anon] == ["t1"]


async def test_record_event_and_rebuild():
    svc, store, index = make()
    await svc.record_event("s:abc12345", "p9", Kind.VIEW)
    assert store.events == [("s:abc12345", "p9", Kind.VIEW, 1.0)]
    store.products["p7"] = ("Running Shoes", "shoes", True)
    assert await svc.rebuild(batch=10) == 1 and "p7" in index.vecs
    await svc.on_order_placed("u1", ["p2", "p1", "p2"])
    assert store.orders == [("u1", ["p1", "p2"])]


async def test_stream_trending_is_stored_and_clamped():
    svc, store, _ = make()
    await svc.on_stream_trending("p9", -1.0, NOW)
    assert store.stream == ("p9", 0.0, NOW)
    await svc.on_stream_trending("", 5.0, NOW)
    assert store.stream == ("p9", 0.0, NOW)
