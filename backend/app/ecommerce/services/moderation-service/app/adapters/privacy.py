"""Driving adapter: yêu cầu xoá dữ liệu của privacy-service (ecommerce.privacy.events, CCPA/CPRA, ADR 0027).

Xoá trong một transaction cùng với việc đánh dấu processed_events và ghi ErasureCompleted vào outbox
(aggregatetype "privacy"), nên không bao giờ báo xong khi chưa xoá.
"""

from __future__ import annotations

import asyncio
import hashlib
import logging
import os
import time
import uuid

import asyncpg
from aiokafka import AIOKafkaConsumer
from ecommerce.common.v1 import event_envelope_pb2
from ecommerce.privacy.v1 import privacy_events_pb2
from google.protobuf import timestamp_pb2

log = logging.getLogger(__name__)

TOPIC = "ecommerce.privacy.events"
PROCESSOR = "moderation-service"
CONSUMER = PROCESSOR + "-privacy"


def erased_id(subject_id: str) -> str:
    """Định danh thay thế ổn định, không đảo ngược (cùng công thức với Java/Go/TS)."""
    return "erased-" + hashlib.sha256(subject_id.encode()).hexdigest()[:16]


def _uuid7() -> str:
    ms = int(time.time() * 1000)
    rand = int.from_bytes(os.urandom(10), "big")
    value = (ms << 80) | (0x7 << 76) | (((rand >> 68) & 0xFFF) << 64) | (0b10 << 62) | (rand & ((1 << 62) - 1))
    return str(uuid.UUID(int=value))


async def erase(c: asyncpg.Connection, subject_id: str) -> str:
    """Xoá/ẩn danh dữ liệu của khách. Trả lý do pháp lý cho phần phải giữ lại, chuỗi rỗng nếu đã xoá hết."""
    # Nội dung người dùng viết: xoá văn bản, ẩn danh tác giả; quyết định kiểm duyệt giữ làm bằng chứng (đã ẩn danh).
    anon = erased_id(subject_id)
    await c.execute(
        "UPDATE items SET text = '[erased]', redacted_text = '', author_id = $2 WHERE author_id = $1", subject_id, anon
    )
    await c.execute("DELETE FROM strikes WHERE author_id = $1", subject_id)
    return ""


async def process(pool: asyncpg.Pool, raw: bytes) -> None:
    env = event_envelope_pb2.EventEnvelope()
    try:
        env.ParseFromString(raw)
    except Exception as e:
        log.warning("bad envelope: %s", e)
        return
    if env.type != privacy_events_pb2.ErasureRequested.DESCRIPTOR.full_name:
        return
    ev = privacy_events_pb2.ErasureRequested()
    env.payload.Unpack(ev)
    if not ev.subject_id:
        return
    async with pool.acquire() as c, c.transaction():
        r = await c.execute(
            "INSERT INTO processed_events (event_id, consumer) VALUES ($1,$2) ON CONFLICT DO NOTHING", env.id, CONSUMER
        )
        if r == "INSERT 0 0":
            return
        retained = await erase(c, ev.subject_id)
        reply = privacy_events_pb2.ErasureCompleted(
            request_id=ev.request_id,
            subject_id=ev.subject_id,
            processor=PROCESSOR,
            partially_retained=bool(retained),
            retention_reason=retained,
        )
        out = event_envelope_pb2.EventEnvelope(
            id=_uuid7(),
            type=reply.DESCRIPTOR.full_name,
            source=PROCESSOR,
            aggregate_type="privacy",
            aggregate_id=ev.subject_id,
        )
        ts = timestamp_pb2.Timestamp()
        ts.GetCurrentTime()
        out.occurred_at.CopyFrom(ts)
        out.payload.Pack(reply)
        await c.execute(
            "INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload) VALUES ($1,$2,$3,$4,$5)",
            out.id,
            "privacy",
            ev.subject_id,
            out.type,
            out.SerializeToString(),
        )


async def run(pool: asyncpg.Pool, brokers: list[str], group: str) -> None:
    consumer = AIOKafkaConsumer(
        TOPIC,
        bootstrap_servers=",".join(brokers),
        group_id=group + "-privacy",
        enable_auto_commit=False,
        auto_offset_reset="earliest",
    )
    await consumer.start()
    try:
        async for msg in consumer:
            while True:
                try:
                    await process(pool, msg.value)
                    break
                except Exception:
                    log.exception("privacy erasure failed, retrying", extra={"x_offset": msg.offset})
                    await asyncio.sleep(2)
            await consumer.commit()
    finally:
        await consumer.stop()
