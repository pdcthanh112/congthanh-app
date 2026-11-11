"""Postgres thật (TEST_DATABASE_URL). Phần pgvector chỉ chạy khi máy có extension vector."""

import os
import uuid
from datetime import UTC, datetime, timedelta

import pytest
from ecommerce.common.v1 import event_envelope_pb2
from ecommerce.order.v1 import order_events_pb2
from ecommerce.product.v1 import product_events_pb2
from ecommerce.stream.v1 import stream_events_pb2
from google.protobuf.timestamp_pb2 import Timestamp

from app.adapters import kafka, postgres
from app.domain.embedding import HashingEmbedder
from app.domain.ranking import Kind, Reason
from app.service import RecommendationService

DB = os.getenv("TEST_DATABASE_URL")
pytestmark = pytest.mark.skipif(not DB, reason="TEST_DATABASE_URL not set")


def env(msg) -> bytes:
    e = event_envelope_pb2.EventEnvelope(id=str(uuid.uuid4()), type=msg.DESCRIPTOR.full_name)
    e.payload.Pack(msg)
    return e.SerializeToString()


async def _has_vector(pool) -> bool:
    return bool(await pool.fetchval("SELECT 1 FROM pg_available_extensions WHERE name = 'vector'"))


async def _setup(schema):
    pool = await postgres.connect(DB, schema)
    store = postgres.PostgresStore(pool)
    return pool, store


async def test_signals_from_events_without_vector():
    schema = "reco_test_" + uuid.uuid4().hex[:8]
    pool, store = await _setup(schema)
    svc = RecommendationService(store, postgres.NoVectorIndex(), HashingEmbedder(64))
    try:
        for pid, name, cat, status in [
            ("tee", "Cotton Tee", "shirts", "ACTIVE"),
            ("jean", "Slim Jeans", "pants", "ACTIVE"),
            ("belt", "Leather Belt", "acc", "ACTIVE"),
            ("old", "Discontinued Item", "acc", "INACTIVE"),
        ]:
            await kafka.process(
                svc,
                store,
                env(product_events_pb2.ProductUpserted(product_id=pid, name=name, category_id=cat, status=status)),
            )
        orders = [["tee", "jean"], ["tee", "jean", "belt"], ["tee", "old"], ["jean", "belt"]]
        for i, items in enumerate(orders):
            o = order_events_pb2.OrderPlaced(order_id=f"o{i}", customer_id=f"c{i}")
            for p in items:
                o.lines.add(product_id=p, quantity=1)
            raw = env(o)
            await kafka.process(svc, store, raw)
            await kafka.process(svc, store, raw)  # giao lại: không đếm hai lần

        bt = await svc.bought_together("tee", 10)
        assert [s.product_id for s in bt] == ["jean", "belt"]  # "old" ngừng bán bị loại
        assert bt[0].reason == Reason.BOUGHT_TOGETHER and abs(bt[0].score - 2 / (3 * 3) ** 0.5) < 1e-6

        await svc.record_event("s:sess_12345678", "belt", Kind.VIEW)
        trend = await svc.trending(10)
        assert {s.product_id for s in trend} == {"tee", "jean", "belt"}
        assert (await svc.trending(10, "pants"))[0].product_id == "jean"

        fy = await svc.for_you("c0", 5)  # đã mua tee + jean → gợi ý belt
        assert [s.product_id for s in fy] == ["belt"]
        sim = await svc.similar("tee", 5)  # không có vector: rơi về mua kèm
        assert [s.product_id for s in sim][:2] == ["jean", "belt"]

        # Trending realtime (Flink) đứng trước; cửa sổ cũ hoặc đến trễ không ghi đè/không được dùng.
        now = datetime.now(UTC)

        def trending_env(pid, score, end):
            ts = Timestamp()
            ts.FromDatetime(end)
            e = event_envelope_pb2.EventEnvelope(
                id=str(uuid.uuid4()), type="ecommerce.stream.v1.ProductTrending", source="stream-processor"
            )
            e.payload.Pack(stream_events_pb2.ProductTrending(product_id=pid, score=score, window_end=ts))
            return e.SerializeToString()

        await kafka.process(svc, store, trending_env("belt", 50, now))
        await kafka.process(svc, store, trending_env("belt", 1, now - timedelta(minutes=5)))  # đến trễ: bỏ
        await kafka.process(svc, store, trending_env("jean", 99, now - timedelta(hours=2)))  # quá cũ: không dùng
        trend = await svc.trending(10)
        assert trend[0].product_id == "belt" and trend[0].score == 50
        assert {s.product_id for s in trend} == {"tee", "jean", "belt"}

        await kafka.process(svc, store, env(product_events_pb2.ProductDeleted(product_id="belt")))
        assert [s.product_id for s in await svc.bought_together("tee", 10)] == ["jean"]
        assert await store.prune(datetime(2100, 1, 1, tzinfo=UTC)) >= 1
    finally:
        await pool.execute(f'DROP SCHEMA "{schema}" CASCADE')
        await pool.close()


async def test_pgvector_similar_and_for_you():
    schema = "reco_test_" + uuid.uuid4().hex[:8]
    pool, store = await _setup(schema)
    try:
        if not await _has_vector(pool):
            pytest.skip("pgvector extension not installed on this Postgres")
        index = postgres.PgVectorIndex(pool, 64)
        assert await index.ensure() is True
        assert await index.ensure() is False
        svc = RecommendationService(store, index, HashingEmbedder(64))
        for pid, name, cat in [
            ("a", "Black Cotton Tee", "shirts"),
            ("b", "White Cotton Tee", "shirts"),
            ("k", "Electric Rice Cooker", "kitchen"),
        ]:
            await svc.on_product_upserted(pid, name, cat, True)
        assert [s.product_id for s in await svc.similar("a", 2)] == ["b", "k"]
        await svc.record_event("u1", "a", Kind.VIEW)
        assert (await svc.for_you("u1", 2))[0].product_id == "b"
    finally:
        await pool.execute(f'DROP SCHEMA "{schema}" CASCADE')
        await pool.close()
