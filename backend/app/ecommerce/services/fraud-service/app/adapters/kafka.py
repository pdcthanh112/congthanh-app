"""Driving adapter: tiêu thụ ecommerce.order.events / ecommerce.payment.events / ecommerce.shipping.events (EventEnvelope protobuf
từ Debezium Outbox Router) để dựng lịch sử khách. processed_events chống xử lý trùng; commit offset sau khi ghi DB."""

from __future__ import annotations

import asyncio
import logging
from datetime import UTC

import asyncpg
from aiokafka import AIOKafkaConsumer
from ecommerce.common.v1 import event_envelope_pb2, money_pb2
from ecommerce.order.v1 import order_events_pb2
from ecommerce.payment.v1 import payment_events_pb2
from ecommerce.shipping.v1 import shipping_events_pb2
from ecommerce.stream.v1 import stream_events_pb2

from ..domain.rules import Address, StreamFeatures
from ..ports import StreamFeatureStore
from .postgres import PostgresStore

log = logging.getLogger(__name__)

TOPICS = ("ecommerce.order.events", "ecommerce.payment.events", "ecommerce.shipping.events")
STREAM_TOPIC = "ecommerce.stream.customer-risk"


def to_minor(m: money_pb2.Money) -> int:
    digits = 0 if m.currency_code in ("VND", "JPY", "KRW") else 2
    return m.units * 10**digits + round(m.nanos / 10 ** (9 - digits))


def _name(cls) -> str:
    return cls.DESCRIPTOR.full_name


async def handle(store: PostgresStore, c: asyncpg.Connection, env: event_envelope_pb2.EventEnvelope) -> None:
    t = env.type
    if t == _name(order_events_pb2.OrderPlaced):
        ev = order_events_pb2.OrderPlaced()
        env.payload.Unpack(ev)
        a = ev.shipping_address
        fp = (
            Address(a.country_code, a.region_code, a.line1, a.phone).fingerprint()
            if ev.HasField("shipping_address") and a.country_code
            else None
        )
        await store.order_placed(
            c,
            ev.order_id,
            ev.customer_id,
            to_minor(ev.total),
            ev.total.currency_code or "USD",
            ev.checkout_workflow_id,
            fp,
        )
    elif t == _name(order_events_pb2.OrderConfirmed):
        ev = order_events_pb2.OrderConfirmed()
        env.payload.Unpack(ev)
        await store.set_status(c, ev.order_id, "PAID")
    elif t == _name(order_events_pb2.OrderCancelled):
        ev = order_events_pb2.OrderCancelled()
        env.payload.Unpack(ev)
        await store.set_status(c, ev.order_id, "CANCELLED")
    elif t == _name(payment_events_pb2.PaymentFailed):
        ev = payment_events_pb2.PaymentFailed()
        env.payload.Unpack(ev)
        await store.payment_failed(c, ev.order_id, ev.reason)
    elif t == _name(payment_events_pb2.PaymentRefunded):
        ev = payment_events_pb2.PaymentRefunded()
        env.payload.Unpack(ev)
        await store.refunded(c, ev.order_id)
    elif t == _name(shipping_events_pb2.ShipmentStatusChanged):
        ev = shipping_events_pb2.ShipmentStatusChanged()
        env.payload.Unpack(ev)
        if ev.to_status == "RETURNED":
            await store.shipment_returned(c, ev.order_id)


async def process(store: PostgresStore, raw: bytes) -> None:
    env = event_envelope_pb2.EventEnvelope()
    try:
        env.ParseFromString(raw)
    except Exception as e:  # message hỏng: bỏ qua thay vì kẹt partition
        log.warning("bad envelope: %s", e)
        return
    async with store.pool.acquire() as c, c.transaction():
        if env.id and not await store.once(c, env.id):
            return
        await handle(store, c, env)


async def run(store: PostgresStore, brokers: list[str], group: str) -> None:
    consumer = AIOKafkaConsumer(
        *TOPICS,
        bootstrap_servers=",".join(brokers),
        group_id=group,
        enable_auto_commit=False,
        auto_offset_reset="earliest",
    )
    await consumer.start()
    try:
        async for msg in consumer:
            while True:
                try:
                    await process(store, msg.value)
                    break
                except Exception:
                    log.exception(
                        "event handling failed, retrying", extra={"x_topic": msg.topic, "x_offset": msg.offset}
                    )
                    await asyncio.sleep(2)
            await consumer.commit()
    finally:
        await consumer.stop()


def stream_features(env: event_envelope_pb2.EventEnvelope) -> tuple[str, StreamFeatures] | None:
    if env.type != _name(stream_events_pb2.CustomerRiskFeatures):
        return None
    ev = stream_events_pb2.CustomerRiskFeatures()
    env.payload.Unpack(ev)
    if not ev.customer_id:
        return None
    at = ev.computed_at.ToDatetime(tzinfo=UTC) if ev.HasField("computed_at") else None
    return ev.customer_id, StreamFeatures(
        orders_1h=ev.orders_1h,
        order_value_1h=ev.order_value_1h,
        currency=ev.currency,
        payment_failures_1h=ev.payment_failures_1h,
        cancellations_24h=ev.cancellations_24h,
        distinct_addresses_24h=ev.distinct_addresses_24h,
        computed_at=at,
    )


async def run_stream_features(target: StreamFeatureStore, brokers: list[str], group: str) -> None:
    """Chép feature từ topic compacted sang Redis. Ghi đè theo khách nên xử lý lặp không sao (không cần processed_events)."""
    consumer = AIOKafkaConsumer(
        STREAM_TOPIC,
        bootstrap_servers=",".join(brokers),
        group_id=group,
        enable_auto_commit=False,
        auto_offset_reset="earliest",
    )
    await consumer.start()
    try:
        async for msg in consumer:
            env = event_envelope_pb2.EventEnvelope()
            try:
                env.ParseFromString(msg.value)
                parsed = stream_features(env)
            except Exception as e:
                log.warning("bad stream envelope: %s", e)
                parsed = None
            while parsed is not None:
                try:
                    await target.put(*parsed)
                    break
                except Exception:
                    log.exception("storing stream features failed, retrying")
                    await asyncio.sleep(2)
            await consumer.commit()
    finally:
        await consumer.stop()
