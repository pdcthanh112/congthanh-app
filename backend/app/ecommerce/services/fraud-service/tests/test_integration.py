"""Chạy trên Postgres + Redis thật: đặt TEST_DATABASE_URL (asyncpg URL) và TEST_REDIS_URL, không có thì bỏ qua."""

import os
import uuid
from datetime import UTC, datetime

import asyncpg
import pytest
from ecommerce.common.v1 import event_envelope_pb2, money_pb2
from ecommerce.fraud.v1 import fraud_events_pb2
from ecommerce.order.v1 import order_events_pb2
from ecommerce.payment.v1 import payment_events_pb2

from app.adapters import kafka, postgres
from app.adapters.redis_store import RedisVelocity
from app.domain.rules import Address, Decision, PaymentMethod, Transaction
from app.service import Conflict, FraudService

DB = os.getenv("TEST_DATABASE_URL")
VK = os.getenv("TEST_REDIS_URL")
pytestmark = pytest.mark.skipif(not (DB and VK), reason="TEST_DATABASE_URL / TEST_REDIS_URL not set")


def envelope(msg) -> bytes:
    env = event_envelope_pb2.EventEnvelope(id=str(uuid.uuid4()), type=msg.DESCRIPTOR.full_name)
    env.payload.Pack(msg)
    return env.SerializeToString()


async def test_end_to_end():
    schema = "fraud_test_" + uuid.uuid4().hex[:8]
    pool = await postgres.connect(DB, schema)
    store = postgres.PostgresStore(pool)
    velocity = RedisVelocity(VK, prefix="fraudtest:" + schema)
    svc = FraudService(store, velocity)
    try:
        addr = Address("US", "NY", "350 5th Ave", "+12125550100")
        aid, a = await svc.assess(
            Transaction("chk-1", "u1", 24_900, "USD", PaymentMethod.PAYPAL, ip="1.2.3.4", device_id="d1", shipping=addr)
        )
        assert a.decision == Decision.APPROVE

        # Đơn đặt từ checkout chk-1 rồi bị thanh toán thất bại 3 lần (3 đơn), event trùng bị bỏ qua.
        placed = order_events_pb2.OrderPlaced(
            order_id="o1",
            customer_id="u1",
            total=money_pb2.Money(currency_code="USD", units=249),
            checkout_workflow_id="chk-1",
        )
        placed.shipping_address.country_code = "US"
        placed.shipping_address.region_code = "NY"
        placed.shipping_address.line1 = "350 5th Ave"
        placed.shipping_address.phone = "+12125550100"
        raw = envelope(placed)
        await kafka.process(store, raw)
        await kafka.process(store, raw)
        for _ in range(3):
            await kafka.process(store, envelope(payment_events_pb2.PaymentFailed(order_id="o1", reason="declined")))
        stored = await svc.get("o1")
        assert stored.id == aid and stored.order_id == "o1"
        hist = await store.history("u1", addr.fingerprint(), datetime.now(UTC))
        assert hist.payment_failures_24h == 3 and hist.known_address and hist.orders == 1

        # Nhiều thẻ trong ngày + thất bại liên tiếp → REVIEW, ghi outbox FraudReviewRequested.
        for i in range(3):
            aid2, a2 = await svc.assess(
                Transaction(f"chk-{i + 2}", "u1", 24_900, "USD", PaymentMethod.CARD, card_fingerprint=f"card{i}")
            )
        assert a2.decision in (Decision.REVIEW, Decision.REJECT), a2
        assert {"CARD_TESTING", "PAYMENT_FAILURES"} <= set(a2.reasons)

        if a2.decision == Decision.REVIEW:
            pending = await svc.reviews("PENDING", 10, 0)
            assert [p.id for p in pending] == [aid2]
            done = await svc.decide(aid2, "approve", "ana", "called the customer")
            assert done.review_status == "APPROVED"
            with pytest.raises(Conflict):
                await svc.decide(aid2, "REJECT", "ana", "")
            types = [r["type"] for r in await pool.fetch("SELECT type FROM outbox ORDER BY created_at")]
            assert types == [
                fraud_events_pb2.FraudReviewRequested.DESCRIPTOR.full_name,
                fraud_events_pb2.FraudReviewDecided.DESCRIPTOR.full_name,
            ]

        # Danh sách chặn.
        await svc.block("email", "Bad@Example.com", "chargeback", "ana")
        _, a3 = await svc.assess(Transaction("chk-9", "u2", 1, "USD", PaymentMethod.CARD, email="bad@example.COM"))
        assert a3.decision == Decision.REJECT and a3.reasons == ["BLOCKLIST"]
        await svc.unblock("EMAIL", "bad@example.com")
        assert await svc.blocks(10) == []
    finally:
        await velocity.r.flushdb()
        await velocity.close()
        async with pool.acquire() as c:
            await c.execute(f'DROP SCHEMA "{schema}" CASCADE')
        await pool.close()


async def test_migrate_is_idempotent():
    schema = "fraud_test_" + uuid.uuid4().hex[:8]
    pool = await postgres.connect(DB, schema)
    try:
        await postgres.migrate(pool, schema)
        assert await pool.fetchval("SELECT count(*) FROM schema_migrations") == 2
    finally:
        c: asyncpg.Connection
        async with pool.acquire() as c:
            await c.execute(f'DROP SCHEMA "{schema}" CASCADE')
        await pool.close()
