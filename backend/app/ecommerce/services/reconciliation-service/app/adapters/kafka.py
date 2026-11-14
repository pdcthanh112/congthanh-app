"""Driving adapter: dựng sổ nội bộ từ ecommerce.payment.events (PaymentCaptured/PaymentRefunded).
processed_events chống xử lý trùng; commit offset sau khi ghi DB."""

from __future__ import annotations

import asyncio
import logging
from datetime import UTC, datetime

from aiokafka import AIOKafkaConsumer
from ecommerce.common.v1 import event_envelope_pb2
from ecommerce.payment.v1 import payment_events_pb2

from ..domain.matching import InternalTxn, Kind
from .postgres import PostgresStore, to_minor

log = logging.getLogger(__name__)

TOPICS = ("ecommerce.payment.events",)


def internal_txn(env: event_envelope_pb2.EventEnvelope) -> InternalTxn | None:
    at = env.occurred_at.ToDatetime(tzinfo=UTC) if env.HasField("occurred_at") else datetime.now(UTC)
    if env.type == payment_events_pb2.PaymentCaptured.DESCRIPTOR.full_name:
        ev = payment_events_pb2.PaymentCaptured()
        env.payload.Unpack(ev)
        kind = Kind.CHARGE
    elif env.type == payment_events_pb2.PaymentRefunded.DESCRIPTOR.full_name:
        ev = payment_events_pb2.PaymentRefunded()
        env.payload.Unpack(ev)
        kind = Kind.REFUND
    else:
        return None
    if not ev.provider_ref or not ev.provider:
        log.warning("payment event %s has no provider ref; cannot reconcile it", env.id)
        return None
    amount = to_minor(ev.amount)
    if amount <= 0:
        return None
    return InternalTxn(
        ev.provider.lower(), ev.provider_ref, kind, amount, ev.amount.currency_code or "USD", ev.order_id, at
    )


async def process(store: PostgresStore, raw: bytes) -> None:
    env = event_envelope_pb2.EventEnvelope()
    try:
        env.ParseFromString(raw)
    except Exception as e:  # message hỏng: bỏ qua thay vì kẹt partition
        log.warning("bad envelope: %s", e)
        return
    t = internal_txn(env)
    if t is None:
        return
    async with store.pool.acquire() as c, c.transaction():
        if env.id and not await store.once(c, env.id):
            return
        await store.record_internal(c, env.id, t)


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
