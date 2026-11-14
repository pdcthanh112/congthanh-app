"""Báo cáo giao dịch Stripe qua Balance Transactions API (chỉ đọc, khoá restricted chỉ cần quyền đọc balance)."""

from __future__ import annotations

from datetime import UTC, date, datetime, time, timedelta

import httpx

from ..domain.matching import Kind, ProviderTxn


class StripeReports:
    def __init__(self, api_key: str, base_url: str = "https://api.stripe.com", client: httpx.AsyncClient | None = None):
        self.api_key = api_key
        self.base_url = base_url
        self.client = client

    async def transactions(self, day: date) -> list[ProviderTxn]:
        start = datetime.combine(day, time.min, tzinfo=UTC)
        end = start + timedelta(days=1)
        params: dict[str, str | int] = {
            "created[gte]": int(start.timestamp()),
            "created[lt]": int(end.timestamp()),
            "limit": 100,
            "expand[]": "data.source",
        }
        out: list[ProviderTxn] = []
        client = self.client or httpx.AsyncClient(base_url=self.base_url, timeout=30)
        try:
            while True:
                r = await client.get("/v1/balance_transactions", params=params, auth=(self.api_key, ""))
                r.raise_for_status()
                body = r.json()
                for bt in body["data"]:
                    t = parse_balance_transaction(bt)
                    if t is not None:
                        out.append(t)
                if not body.get("has_more"):
                    break
                params["starting_after"] = body["data"][-1]["id"]
        finally:
            if self.client is None:
                await client.aclose()
        return out


def parse_balance_transaction(bt: dict) -> ProviderTxn | None:
    """charge/payment → CHARGE theo PaymentIntent; refund → REFUND theo PaymentIntent của charge gốc.
    Loại khác (payout, adjustment, dispute...) không phải giao dịch của khách nên bỏ qua."""
    typ = bt.get("type")
    source = bt.get("source") or {}
    if typ in ("charge", "payment"):
        kind = Kind.CHARGE
        ref = source.get("payment_intent") if isinstance(source, dict) else None
    elif typ in ("refund", "payment_refund"):
        kind = Kind.REFUND
        ref = source.get("payment_intent") if isinstance(source, dict) else None
    else:
        return None
    if not ref:
        ref = source.get("id") if isinstance(source, dict) else str(source)
    return ProviderTxn(
        provider="stripe",
        ref=ref,
        kind=kind,
        amount_minor=abs(int(bt["amount"])),
        fee_minor=abs(int(bt.get("fee", 0))),
        currency=str(bt["currency"]).upper(),
        created_at=datetime.fromtimestamp(int(bt["created"]), tz=UTC),
        payout_id=str(bt.get("payout") or ""),
    )
