"""Port (driven) của fraud-service: kho lưu trữ (Postgres), bộ đếm velocity và feature stream (Redis)."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any, Protocol

from .domain.rules import Assessment, History, StreamFeatures, Transaction, Velocity


@dataclass(frozen=True)
class StoredAssessment:
    id: str
    checkout_id: str
    customer_id: str
    order_id: str | None
    amount: int
    currency: str
    score: int
    decision: str
    reasons: list[dict[str, Any]]
    review_status: str | None
    reviewed_by: str | None
    review_note: str | None
    created_at: datetime

    def to_json(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "checkoutId": self.checkout_id,
            "customerId": self.customer_id,
            "orderId": self.order_id,
            "amount": self.amount,
            "currency": self.currency,
            "score": self.score,
            "decision": self.decision,
            "reasons": self.reasons,
            "review": None
            if self.review_status is None
            else {"status": self.review_status, "decidedBy": self.reviewed_by, "note": self.review_note},
            "createdAt": self.created_at.isoformat(),
        }


class Store(Protocol):
    async def history(self, customer_id: str, address_fp: str | None, now: datetime) -> History: ...

    async def blocked(self, values: dict[str, str]) -> list[str]: ...

    async def save_assessment(self, id: str, tx: Transaction, a: Assessment, signals: dict[str, Any]) -> None: ...

    async def get_assessment(self, id_or_checkout: str) -> StoredAssessment | None: ...

    async def list_reviews(self, status: str, limit: int, offset: int) -> list[StoredAssessment]: ...

    async def decide_review(self, assessment_id: str, decision: str, by: str, note: str) -> StoredAssessment: ...

    async def block(self, kind: str, value: str, reason: str, by: str) -> None: ...

    async def unblock(self, kind: str, value: str) -> bool: ...

    async def list_blocks(self, limit: int) -> list[dict[str, Any]]: ...

    async def ping(self) -> None: ...


class VelocityCounter(Protocol):
    async def record_and_count(self, tx: Transaction, now: datetime) -> Velocity: ...

    async def ping(self) -> None: ...


class StreamFeatureStore(Protocol):
    """Feature mới nhất của khách do stream-processor ghi (consumer topic ecommerce.stream.customer-risk)."""

    async def get(self, customer_id: str) -> StreamFeatures | None: ...

    async def put(self, customer_id: str, features: StreamFeatures) -> None: ...
