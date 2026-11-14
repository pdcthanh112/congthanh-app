from datetime import UTC, datetime

import pytest

from app.adapters.stripe import parse_balance_transaction
from app.domain.matching import InternalTxn, Kind, MismatchKind, ProviderTxn, match, parse_csv

AT = datetime(2026, 11, 13, 15, tzinfo=UTC)


def p(ref, kind=Kind.CHARGE, amount=10800, fee=343, currency="USD"):
    return ProviderTxn("stripe", ref, kind, amount, fee, currency, AT)


def i(ref, kind=Kind.CHARGE, amount=10800, currency="USD", order="o-1"):
    return InternalTxn("stripe", ref, kind, amount, currency, order, AT)


def test_exact_matches_and_fees_are_totalled():
    r = match([p("pi_1"), p("pi_2", amount=5000, fee=175)], [i("pi_1"), i("pi_2", amount=5000, order="o-2")])
    assert r.matched == 2
    assert r.mismatches == []
    assert r.provider_fees_minor == 518


def test_every_kind_of_mismatch_is_reported():
    r = match(
        [p("pi_only_provider"), p("pi_amount", amount=9999), p("pi_cur", currency="EUR")],
        [i("pi_only_internal", order="o-9"), i("pi_amount"), i("pi_cur")],
    )
    kinds = {(m.ref, m.kind) for m in r.mismatches}
    assert kinds == {
        ("pi_only_provider", MismatchKind.MISSING_INTERNAL),
        ("pi_only_internal", MismatchKind.MISSING_PROVIDER),
        ("pi_amount", MismatchKind.AMOUNT_MISMATCH),
        ("pi_cur", MismatchKind.CURRENCY_MISMATCH),
    }
    missing = next(m for m in r.mismatches if m.ref == "pi_only_internal")
    assert missing.order_id == "o-9"


def test_refunds_are_compared_as_totals_per_payment():
    provider = [p("pi_1", Kind.REFUND, 3000, 0), p("pi_1", Kind.REFUND, 2400, 0)]
    internal = [i("pi_1", Kind.REFUND, 5400)]
    assert match(provider, internal).matched == 1


def test_tolerance_absorbs_rounding():
    r = match([p("pi_1", amount=10801)], [i("pi_1")], tolerance_minor=1)
    assert r.matched == 1


def test_csv_parsing_skips_non_customer_rows_and_rejects_bad_lines():
    text = (
        "provider_ref,type,amount,fee,currency,created,payout_id\n"
        "PAY-1,payment,10800,350,usd,2026-11-13T10:00:00Z,po_1\n"
        "PAY-1,refund,-2000,0,usd,1763028000,po_1\n"
        "PO-1,payout,-8800,0,usd,2026-11-13T23:00:00Z,po_1\n"
    )
    txns = parse_csv("paypal", text)
    assert [(t.kind, t.amount_minor, t.currency) for t in txns] == [
        (Kind.CHARGE, 10800, "USD"),
        (Kind.REFUND, 2000, "USD"),
    ]
    with pytest.raises(ValueError, match="line 2"):
        parse_csv("paypal", "provider_ref,type,amount,currency,created\nX,charge,abc,USD,2026-11-13\n")
    with pytest.raises(ValueError, match="missing columns"):
        parse_csv("paypal", "ref,amount\n")


def test_stripe_balance_transactions_map_to_payment_intents():
    charge = {
        "id": "txn_1",
        "type": "charge",
        "amount": 10800,
        "fee": 343,
        "currency": "usd",
        "created": 1763046000,
        "payout": "po_1",
        "source": {"id": "ch_1", "payment_intent": "pi_1"},
    }
    refund = {
        "id": "txn_2",
        "type": "refund",
        "amount": -5400,
        "fee": 0,
        "currency": "usd",
        "created": 1763046000,
        "source": {"id": "re_1", "payment_intent": "pi_1"},
    }
    payout = {"id": "txn_3", "type": "payout", "amount": -100, "fee": 0, "currency": "usd", "created": 1763046000}
    c, r = parse_balance_transaction(charge), parse_balance_transaction(refund)
    assert (c.ref, c.kind, c.amount_minor, c.fee_minor) == ("pi_1", Kind.CHARGE, 10800, 343)
    assert (r.ref, r.kind, r.amount_minor) == ("pi_1", Kind.REFUND, 5400)
    assert parse_balance_transaction(payout) is None
