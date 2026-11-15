from datetime import UTC, date, datetime

import pytest

from app.adapters.kafka import internal_txn
from app.domain.matching import InternalTxn, Kind
from app.service import Invalid, NotFound, ReconciliationService


class FakeStore:
    def __init__(self, internal):
        self.internal = internal
        self.saved = []

    async def internal_for_day(self, provider, day):
        return [t for t in self.internal if t.provider == provider and t.at.date() == day]

    async def save_run(self, run_id, provider, day, source, txns, result):
        self.saved.append((run_id, provider, day, source, txns, result))

    async def resolve(self, mismatch_id, by, note):
        return None


DAY = date(2026, 11, 13)


async def test_csv_run_only_counts_transactions_of_that_day():
    store = FakeStore(
        [InternalTxn("paypal", "PAY-1", Kind.CHARGE, 10800, "USD", "o-1", datetime(2026, 11, 13, 9, tzinfo=UTC))]
    )
    svc = ReconciliationService(store, {})
    csv = (
        "provider_ref,type,amount,fee,currency,created\n"
        "PAY-1,payment,10800,350,USD,2026-11-13T10:00:00Z\n"
        "PAY-2,payment,500,20,USD,2026-11-14T10:00:00Z\n"
    )
    r = await svc.run_from_csv("paypal", DAY, csv)
    assert (r["matched"], r["mismatched"], r["providerTransactions"]) == (1, 0, 1)
    assert store.saved[0][3] == "csv"


async def test_provider_without_api_and_bad_input_are_rejected():
    svc = ReconciliationService(FakeStore([]), {})
    with pytest.raises(Invalid):
        await svc.run_from_provider("stripe", DAY)
    with pytest.raises(Invalid):
        await svc.run_from_csv("paypal", DAY, "nope\n")
    with pytest.raises(Invalid):
        await svc.resolve(1, "u", "  ")
    with pytest.raises(NotFound):
        await svc.resolve(1, "u", "fixed in ledger")


def test_run_ids_are_increasing_snowflakes():
    from ecommerce_platform.ids import snowflake

    a, b = snowflake(), snowflake()
    assert a.isdigit() and int(a) < int(b) < 2**63


def test_payment_events_become_internal_transactions():
    from ecommerce.common.v1 import event_envelope_pb2, money_pb2
    from ecommerce.payment.v1 import payment_events_pb2

    env = event_envelope_pb2.EventEnvelope(id="ev-1", type="ecommerce.payment.v1.PaymentCaptured")
    env.payload.Pack(
        payment_events_pb2.PaymentCaptured(
            payment_id="pay-1",
            order_id="o-1",
            provider="Stripe",
            provider_ref="pi_1",
            amount=money_pb2.Money(currency_code="USD", units=108, nanos=500_000_000),
        )
    )
    t = internal_txn(env)
    assert (t.provider, t.ref, t.kind, t.amount_minor, t.order_id) == ("stripe", "pi_1", Kind.CHARGE, 10850, "o-1")

    env2 = event_envelope_pb2.EventEnvelope(id="ev-2", type="ecommerce.payment.v1.PaymentCaptured")
    env2.payload.Pack(
        payment_events_pb2.PaymentCaptured(
            payment_id="p", order_id="o", provider="stripe", amount=money_pb2.Money(currency_code="USD", units=1)
        )
    )
    assert internal_txn(env2) is None  # thiếu provider_ref thì không đối soát được
