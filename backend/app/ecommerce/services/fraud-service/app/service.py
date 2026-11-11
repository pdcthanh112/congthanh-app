"""Use case của fraud-service: chấm điểm giao dịch, hàng chờ duyệt, danh sách chặn."""

from __future__ import annotations

import logging
import os
import time
import uuid
from datetime import UTC, datetime, timedelta

from .domain.rules import Assessment, Decision, StreamFeatures, Thresholds, Transaction, Velocity, evaluate
from .ports import Store, StoredAssessment, StreamFeatureStore, VelocityCounter

log = logging.getLogger(__name__)

BLOCK_KINDS = ("EMAIL", "PHONE", "IP", "DEVICE", "CARD", "CUSTOMER")
# Feature stream cũ hơn mức này coi như không có (job dừng lâu hoặc khách không hoạt động).
STREAM_MAX_AGE = timedelta(hours=1)


def uuid7() -> str:
    """UUIDv7 (RFC 9562): 48 bit mili giây + ngẫu nhiên, sắp theo thời gian (uuid.uuid7 chỉ có từ Python 3.14)."""
    ms = time.time_ns() // 1_000_000
    b = bytearray(ms.to_bytes(6, "big") + os.urandom(10))
    b[6] = (b[6] & 0x0F) | 0x70
    b[8] = (b[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(b)))


class NotFound(Exception):
    pass


class Invalid(Exception):
    pass


class Conflict(Exception):
    pass


def normalize_block_value(kind: str, value: str) -> str:
    v = value.strip()
    return v.lower() if kind in ("EMAIL", "CARD", "DEVICE") else v


class FraudService:
    def __init__(
        self,
        store: Store,
        velocity: VelocityCounter,
        thresholds: Thresholds | None = None,
        clock=None,
        stream: StreamFeatureStore | None = None,
    ):
        self.store = store
        self.velocity = velocity
        self.stream = stream
        self.t = thresholds or Thresholds()
        self.now = clock or (lambda: datetime.now(UTC))

    async def assess(self, tx: Transaction) -> tuple[str, Assessment]:
        """Chấm điểm; luôn lưu lại kết quả (kể cả APPROVE) để làm nhãn huấn luyện về sau."""
        now = self.now()
        try:
            vel: Velocity | None = await self.velocity.record_and_count(tx, now)
        except Exception as e:  # Redis lỗi: vẫn chấm bằng luật còn lại thay vì chặn checkout.
            log.warning("velocity unavailable: %s", e)
            vel = None
        candidates = {
            "CUSTOMER": tx.customer_id,
            "EMAIL": tx.email,
            "PHONE": tx.phone or (tx.shipping.phone if tx.shipping else ""),
            "IP": tx.ip,
            "DEVICE": tx.device_id,
            "CARD": tx.card_fingerprint,
        }
        values = {k: normalize_block_value(k, v) for k, v in candidates.items() if v}
        blocked = await self.store.blocked(values)
        history = await self.store.history(tx.customer_id, tx.shipping.fingerprint() if tx.shipping else None, now)
        stream = await self._stream_features(tx.customer_id, now)
        a = evaluate(tx, history, vel, blocked, now, self.t, stream)
        aid = uuid7()
        signals = {
            "history": history.__dict__,
            "velocity": None if vel is None else vel.__dict__,
            "stream": None if stream is None else {**stream.__dict__, "computed_at": str(stream.computed_at)},
        }
        await self.store.save_assessment(aid, tx, a, signals)
        if a.decision != Decision.APPROVE:
            log.info(
                "transaction flagged",
                extra={
                    "x_checkout": tx.checkout_id,
                    "x_decision": a.decision,
                    "x_score": a.score,
                    "x_reasons": a.reasons,
                },
            )
        return aid, a

    async def _stream_features(self, customer_id: str, now: datetime) -> StreamFeatures | None:
        if self.stream is None:
            return None
        try:
            f = await self.stream.get(customer_id)
        except Exception as e:  # thiếu feature stream không được chặn checkout
            log.warning("stream features unavailable: %s", e)
            return None
        if f is None or f.computed_at is None or now - f.computed_at > STREAM_MAX_AGE:
            return None
        return f

    async def get(self, id_or_checkout: str) -> StoredAssessment:
        a = await self.store.get_assessment(id_or_checkout)
        if a is None:
            raise NotFound(f"assessment {id_or_checkout} not found")
        return a

    async def reviews(self, status: str, limit: int, offset: int) -> list[StoredAssessment]:
        status = status.upper()
        if status not in ("PENDING", "APPROVED", "REJECTED"):
            raise Invalid("status must be PENDING, APPROVED or REJECTED")
        return await self.store.list_reviews(status, max(1, min(limit, 200)), max(0, offset))

    async def decide(self, assessment_id: str, decision: str, by: str, note: str) -> StoredAssessment:
        decision = decision.upper()
        if decision not in (Decision.APPROVE, Decision.REJECT):
            raise Invalid("decision must be APPROVE or REJECT")
        return await self.store.decide_review(assessment_id, decision, by, note.strip()[:1000])

    async def block(self, kind: str, value: str, reason: str, by: str) -> None:
        kind = kind.upper()
        if kind not in BLOCK_KINDS:
            raise Invalid("kind must be one of " + ", ".join(BLOCK_KINDS))
        if not value.strip():
            raise Invalid("value is required")
        await self.store.block(kind, normalize_block_value(kind, value), reason.strip()[:500], by)

    async def unblock(self, kind: str, value: str) -> None:
        kind = kind.upper()
        if not await self.store.unblock(kind, normalize_block_value(kind, value)):
            raise NotFound("entry not found")

    async def blocks(self, limit: int) -> list[dict]:
        return await self.store.list_blocks(max(1, min(limit, 500)))
