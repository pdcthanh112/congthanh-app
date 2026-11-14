from __future__ import annotations

from typing import Any, Protocol

from .domain.policy import Item, Verdict


class Classifier(Protocol):
    async def classify(self, item: Item) -> Verdict | None: ...


class Store(Protocol):
    async def ping(self) -> None: ...

    async def cached_verdict(self, content_hash: str) -> dict[str, Any] | None: ...

    async def save_item(self, item: Item, content_hash: str, verdict: Verdict, decided_by: str) -> dict[str, Any]:
        """Lưu nội dung + quyết định và ghi ModerationDecided vào outbox (cùng transaction). Idempotent theo nội dung."""

    async def get(self, item_id: str) -> dict[str, Any] | None: ...

    async def queue(self, limit: int) -> list[dict[str, Any]]: ...

    async def decide(self, item_id: str, verdict: Verdict, by: str, note: str) -> dict[str, Any] | None:
        """Người duyệt ra quyết định cho mục đang ESCALATE/APPEALED; ghi event; None nếu không còn chờ."""

    async def appeal(self, item_id: str, author_id: str, reason: str) -> dict[str, Any] | None: ...

    async def rejections_since(self, author_id: str, days: int) -> int: ...

    async def issue_strike(self, author_id: str, strikes: int, item_id: str) -> None: ...
