from __future__ import annotations

from datetime import date
from typing import Any, Protocol

from .domain.matching import InternalTxn, MatchResult, ProviderTxn


class Store(Protocol):
    async def ping(self) -> None: ...

    async def internal_for_day(self, provider: str, day: date) -> list[InternalTxn]: ...

    async def save_run(
        self, run_id: str, provider: str, day: date, source: str, txns: list[ProviderTxn], result: MatchResult
    ) -> None:
        """Lưu giao dịch cổng, kết quả và event outbox trong một transaction; chạy lại cùng ngày thay kết quả cũ."""

    async def runs(self, limit: int) -> list[dict[str, Any]]: ...

    async def run(self, run_id: str) -> dict[str, Any] | None: ...

    async def mismatches(self, status: str, limit: int) -> list[dict[str, Any]]: ...

    async def resolve(self, mismatch_id: int, by: str, note: str) -> dict[str, Any] | None: ...


class ProviderReports(Protocol):
    async def transactions(self, day: date) -> list[ProviderTxn]: ...
