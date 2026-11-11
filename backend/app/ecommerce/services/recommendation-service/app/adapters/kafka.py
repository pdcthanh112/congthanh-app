"""Driving adapter: dựng tín hiệu gợi ý từ ecommerce.product/order/cart/customer.events và điểm trending realtime
(ecommerce.stream.product-trending, do stream-processor tính).

Tín hiệu gợi ý là xấp xỉ: đánh dấu processed_events sau khi xử lý xong, nên nếu pod chết đúng giữa hai bước
thì một event có thể được tính hai lần — chấp nhận được, đổi lại không cần transaction xuyên use case.
"""

from __future__ import annotations

import asyncio
import logging
from datetime import UTC

from aiokafka import AIOKafkaConsumer
from ecommerce.cart.v1 import cart_events_pb2
from ecommerce.common.v1 import event_envelope_pb2
from ecommerce.customer.v1 import customer_events_pb2
from ecommerce.order.v1 import order_events_pb2
from ecommerce.product.v1 import product_events_pb2
from ecommerce.stream.v1 import stream_events_pb2

from ..domain.ranking import Kind
from ..service import RecommendationService
from .postgres import CONSUMER, PostgresStore

log = logging.getLogger(__name__)

TOPICS = (
    "ecommerce.product.events",
    "ecommerce.order.events",
    "ecommerce.cart.events",
    "ecommerce.customer.events",
    "ecommerce.stream.product-trending",
)
INACTIVE = {"INACTIVE", "DELETED", "DRAFT", "ARCHIVED", "HIDDEN"}


def _name(cls) -> str:
    return cls.DESCRIPTOR.full_name


async def handle(svc: RecommendationService, env: event_envelope_pb2.EventEnvelope) -> None:
    t = env.type
    if t == _name(product_events_pb2.ProductUpserted):
        ev = product_events_pb2.ProductUpserted()
        env.payload.Unpack(ev)
        await svc.on_product_upserted(ev.product_id, ev.name, ev.category_id, ev.status.upper() not in INACTIVE)
    elif t == _name(product_events_pb2.ProductDeleted):
        ev = product_events_pb2.ProductDeleted()
        env.payload.Unpack(ev)
        await svc.on_product_deleted(ev.product_id)
    elif t == _name(order_events_pb2.OrderPlaced):
        ev = order_events_pb2.OrderPlaced()
        env.payload.Unpack(ev)
        await svc.on_order_placed(ev.customer_id, [line.product_id for line in ev.lines])
    elif t == _name(cart_events_pb2.CartItemAdded):
        ev = cart_events_pb2.CartItemAdded()
        env.payload.Unpack(ev)
        if ev.customer_id:
            await svc.record_event(ev.customer_id, ev.product_id, Kind.CART)
    elif t == _name(stream_events_pb2.ProductTrending):
        ev = stream_events_pb2.ProductTrending()
        env.payload.Unpack(ev)
        await svc.on_stream_trending(ev.product_id, ev.score, ev.window_end.ToDatetime(tzinfo=UTC))
    elif t == _name(customer_events_pb2.WishlistItemAdded):
        ev = customer_events_pb2.WishlistItemAdded()
        env.payload.Unpack(ev)
        await svc.record_event(ev.customer_id, ev.product_id, Kind.WISHLIST)


async def process(svc: RecommendationService, store: PostgresStore, raw: bytes) -> None:
    env = event_envelope_pb2.EventEnvelope()
    try:
        env.ParseFromString(raw)
    except Exception as e:
        log.warning("bad envelope: %s", e)
        return
    if env.source == "stream-processor":
        # Bản ghi "giá trị hiện tại" trên topic compacted, ghi đè có điều kiện: không cần sổ processed_events.
        await handle(svc, env)
        return
    if env.id and await store.pool.fetchval("SELECT 1 FROM processed_events WHERE event_id = $1", env.id):
        return
    await handle(svc, env)
    if env.id:
        await store.pool.execute(
            "INSERT INTO processed_events (event_id, consumer) VALUES ($1,$2) ON CONFLICT DO NOTHING", env.id, CONSUMER
        )


async def run(svc: RecommendationService, store: PostgresStore, brokers: list[str], group: str) -> None:
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
                    await process(svc, store, msg.value)
                    break
                except Exception:
                    log.exception(
                        "event handling failed, retrying", extra={"x_topic": msg.topic, "x_offset": msg.offset}
                    )
                    await asyncio.sleep(2)
            await consumer.commit()
    finally:
        await consumer.stop()
