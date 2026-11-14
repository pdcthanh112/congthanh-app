"""Ghép giao dịch phía cổng với ghi nhận nội bộ. Thuần (không I/O) để kiểm thử mọi tình huống.

- Tiền thu (CHARGE) ghép 1-1 theo mã giao dịch phía cổng (Stripe PaymentIntent, PayPal order).
- Hoàn tiền (REFUND) ghép theo tổng trên mỗi mã giao dịch, vì một payment có thể hoàn nhiều lần và cổng
  không mang refund id nội bộ của mình.
- Phí cổng không có phía nội bộ (payment-service không ghi phí): chỉ cộng tổng để báo cáo và hạch toán.
"""

from __future__ import annotations

import csv
import io
from dataclasses import dataclass, field
from datetime import UTC, datetime
from enum import StrEnum


class Kind(StrEnum):
    CHARGE = "CHARGE"
    REFUND = "REFUND"


class MismatchKind(StrEnum):
    MISSING_INTERNAL = "MISSING_INTERNAL"  # cổng có, mình không (webhook mất, tạo đơn lỗi sau khi đã thu)
    MISSING_PROVIDER = "MISSING_PROVIDER"  # mình ghi đã thu/hoàn, cổng không có
    AMOUNT_MISMATCH = "AMOUNT_MISMATCH"
    CURRENCY_MISMATCH = "CURRENCY_MISMATCH"


@dataclass(frozen=True)
class ProviderTxn:
    provider: str
    ref: str
    kind: Kind
    amount_minor: int  # luôn dương
    fee_minor: int
    currency: str
    created_at: datetime
    payout_id: str = ""


@dataclass(frozen=True)
class InternalTxn:
    provider: str
    ref: str
    kind: Kind
    amount_minor: int
    currency: str
    order_id: str
    at: datetime


@dataclass(frozen=True)
class Mismatch:
    kind: MismatchKind
    txn_kind: Kind
    ref: str
    order_id: str
    provider_amount_minor: int | None
    internal_amount_minor: int | None
    currency: str


@dataclass
class MatchResult:
    matched: int = 0
    mismatches: list[Mismatch] = field(default_factory=list)
    provider_fees_minor: int = 0
    currency: str = "USD"


def _sum_by_ref(items):
    out: dict[str, tuple[int, str, str]] = {}
    for t in items:
        amount, currency, order_id = out.get(t.ref, (0, t.currency, getattr(t, "order_id", "")))
        out[t.ref] = (amount + t.amount_minor, currency if currency == t.currency else "MIXED", order_id)
    return out


def match(provider: list[ProviderTxn], internal: list[InternalTxn], tolerance_minor: int = 0) -> MatchResult:
    """So khớp một ngày giao dịch. Mỗi mã giao dịch được xét riêng cho CHARGE và REFUND."""
    result = MatchResult()
    result.provider_fees_minor = sum(t.fee_minor for t in provider)
    if provider:
        result.currency = provider[0].currency
    for kind in Kind:
        p = _sum_by_ref([t for t in provider if t.kind == kind])
        i = _sum_by_ref([t for t in internal if t.kind == kind])
        for ref in sorted(p.keys() | i.keys()):
            pa, pc, _ = p.get(ref, (None, None, ""))
            ia, ic, order_id = i.get(ref, (None, None, ""))
            if pa is None:
                result.mismatches.append(Mismatch(MismatchKind.MISSING_PROVIDER, kind, ref, order_id, None, ia, ic))
            elif ia is None:
                result.mismatches.append(Mismatch(MismatchKind.MISSING_INTERNAL, kind, ref, "", pa, None, pc))
            elif pc != ic:
                result.mismatches.append(Mismatch(MismatchKind.CURRENCY_MISMATCH, kind, ref, order_id, pa, ia, pc))
            elif abs(pa - ia) > tolerance_minor:
                result.mismatches.append(Mismatch(MismatchKind.AMOUNT_MISMATCH, kind, ref, order_id, pa, ia, pc))
            else:
                result.matched += 1
    return result


CSV_COLUMNS = ("provider_ref", "type", "amount", "fee", "currency", "created", "payout_id")


def parse_csv(provider: str, text: str) -> list[ProviderTxn]:
    """Định dạng chung cho báo cáo cổng chưa có API (PayPal settlement, BNPL...): mỗi dòng một giao dịch.

    Cột: provider_ref, type (charge|refund), amount và fee theo đơn vị nhỏ nhất (refund có thể âm), currency,
    created (ISO 8601 hoặc epoch giây), payout_id (tuỳ chọn). Dòng sai định dạng làm hỏng cả file (ValueError)
    để không đối soát trên dữ liệu thiếu.
    """
    reader = csv.DictReader(io.StringIO(text))
    missing = {"provider_ref", "type", "amount", "currency", "created"} - set(reader.fieldnames or [])
    if missing:
        raise ValueError(f"CSV is missing columns: {', '.join(sorted(missing))}")
    out: list[ProviderTxn] = []
    for n, row in enumerate(reader, start=2):
        try:
            typ = row["type"].strip().lower()
            if typ not in ("charge", "payment", "refund"):
                continue  # payout, adjustment...: không phải giao dịch của khách
            created = row["created"].strip()
            at = (
                datetime.fromtimestamp(int(created), tz=UTC)
                if created.isdigit()
                else datetime.fromisoformat(created.replace("Z", "+00:00"))
            )
            out.append(
                ProviderTxn(
                    provider=provider,
                    ref=row["provider_ref"].strip(),
                    kind=Kind.REFUND if typ == "refund" else Kind.CHARGE,
                    amount_minor=abs(int(row["amount"])),
                    fee_minor=abs(int(row.get("fee") or 0)),
                    currency=row["currency"].strip().upper(),
                    created_at=at if at.tzinfo else at.replace(tzinfo=UTC),
                    payout_id=(row.get("payout_id") or "").strip(),
                )
            )
        except (KeyError, ValueError) as e:
            raise ValueError(f"line {n}: {e}") from e
    return out
