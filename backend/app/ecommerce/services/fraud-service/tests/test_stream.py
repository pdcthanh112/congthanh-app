"""Feature stream (Flink → Kafka → Redis → chấm điểm)."""

from datetime import UTC, datetime, timedelta

from ecommerce.common.v1 import event_envelope_pb2
from ecommerce.stream.v1 import stream_events_pb2
from google.protobuf.timestamp_pb2 import Timestamp

from app.adapters.kafka import stream_features
from app.domain.rules import Decision, History, PaymentMethod, StreamFeatures, Transaction, Velocity
from app.service import FraudService

NOW = datetime(2026, 11, 10, 12, tzinfo=UTC)


def test_stream_envelope_is_decoded():
    ts = Timestamp()
    ts.FromDatetime(NOW)
    ev = stream_events_pb2.CustomerRiskFeatures(customer_id="u1", orders_1h=2, payment_failures_1h=3, computed_at=ts)
    env = event_envelope_pb2.EventEnvelope(type="ecommerce.stream.v1.CustomerRiskFeatures")
    env.payload.Pack(ev)
    cid, f = stream_features(env)
    assert cid == "u1" and f.orders_1h == 2 and f.payment_failures_1h == 3 and f.computed_at == NOW
    assert stream_features(event_envelope_pb2.EventEnvelope(type="ecommerce.order.v1.OrderPlaced")) is None


class Store:
    def __init__(self):
        self.signals = None

    async def blocked(self, values):
        return []

    async def history(self, customer_id, fp, now):
        return History(paid_orders=2, avg_paid_amount=400_000, known_address=True)

    async def save_assessment(self, aid, tx, a, signals):
        self.signals = signals


class Vel:
    async def record_and_count(self, tx, now):
        return Velocity()


class Stream:
    def __init__(self, f=None, fail=False):
        self.f, self.fail = f, fail

    async def get(self, customer_id):
        if self.fail:
            raise ConnectionError("redis down")
        return self.f


TX = Transaction(checkout_id="c1", customer_id="u1", amount=12_500, currency="USD", payment_method=PaymentMethod.CARD)


async def assess(stream):
    store = Store()
    svc = FraudService(store, Vel(), clock=lambda: NOW, stream=stream)
    _, a = await svc.assess(TX)
    return a, store.signals


async def test_fresh_stream_features_are_used_and_recorded():
    f = StreamFeatures(payment_failures_1h=5, orders_1h=6, computed_at=NOW - timedelta(minutes=5))
    a, signals = await assess(Stream(f))
    assert "STREAM_PAYMENT_FAILURES" in a.reasons and "STREAM_ORDER_BURST" in a.reasons
    assert signals["stream"]["orders_1h"] == 6


async def test_stale_missing_or_failing_stream_is_ignored():
    stale = StreamFeatures(payment_failures_1h=5, computed_at=NOW - timedelta(hours=2))
    for s in (Stream(stale), Stream(None), Stream(fail=True), None):
        a, signals = await assess(s)
        assert a.decision == Decision.APPROVE and signals["stream"] is None
