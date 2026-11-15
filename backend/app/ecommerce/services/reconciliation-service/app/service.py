"""Use case đối soát."""

from __future__ import annotations

from datetime import date
from typing import Any

from ecommerce_platform.ids import snowflake

from .domain.matching import match, parse_csv
from .ports import ProviderReports, Store


class Invalid(Exception):
    pass


class NotFound(Exception):
    pass


class ReconciliationService:
    def __init__(self, store: Store, reports: dict[str, ProviderReports], tolerance_minor: int = 0):
        self.store = store
        self.reports = reports
        self.tolerance = tolerance_minor

    async def run_from_provider(self, provider: str, day: date) -> dict[str, Any]:
        """Tải giao dịch của ngày từ API cổng rồi đối soát."""
        reports = self.reports.get(provider)
        if reports is None:
            raise Invalid(f"no report API configured for provider {provider!r}; upload a CSV instead")
        txns = await reports.transactions(day)
        return await self._run(provider, day, "api", txns)

    async def run_from_csv(self, provider: str, day: date, text: str) -> dict[str, Any]:
        try:
            txns = parse_csv(provider, text)
        except ValueError as e:
            raise Invalid(str(e)) from e
        txns = [t for t in txns if t.created_at.date() == day]
        return await self._run(provider, day, "csv", txns)

    async def _run(self, provider: str, day: date, source: str, txns) -> dict[str, Any]:
        internal = await self.store.internal_for_day(provider, day)
        result = match(txns, internal, self.tolerance)
        run_id = snowflake()
        await self.store.save_run(run_id, provider, day, source, txns, result)
        return {
            "runId": run_id,
            "provider": provider,
            "businessDate": day.isoformat(),
            "providerTransactions": len(txns),
            "internalTransactions": len(internal),
            "matched": result.matched,
            "mismatched": len(result.mismatches),
            "providerFeesMinor": result.provider_fees_minor,
        }

    async def runs(self, limit: int) -> list[dict[str, Any]]:
        return await self.store.runs(min(max(limit, 1), 200))

    async def run(self, run_id: str) -> dict[str, Any]:
        r = await self.store.run(run_id)
        if r is None:
            raise NotFound(f"run {run_id} not found")
        return r

    async def mismatches(self, status: str, limit: int) -> list[dict[str, Any]]:
        if status not in ("OPEN", "RESOLVED"):
            raise Invalid("status must be OPEN or RESOLVED")
        return await self.store.mismatches(status, min(max(limit, 1), 500))

    async def resolve(self, mismatch_id: int, by: str, note: str) -> dict[str, Any]:
        if not note.strip():
            raise Invalid("a resolution needs a note (cause and the ledger adjustment, if any)")
        r = await self.store.resolve(mismatch_id, by, note)
        if r is None:
            raise NotFound(f"open mismatch {mismatch_id} not found")
        return r
