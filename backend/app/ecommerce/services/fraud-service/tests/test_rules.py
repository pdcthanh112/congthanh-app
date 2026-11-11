from datetime import UTC, datetime, timedelta

from app.domain.rules import (
    Address,
    Decision,
    History,
    Line,
    PaymentMethod,
    StreamFeatures,
    Transaction,
    Velocity,
    evaluate,
)

NOW = datetime(2026, 11, 10, 12, tzinfo=UTC)


def tx(**kw) -> Transaction:
    base = dict(checkout_id="c1", customer_id="u1", amount=12_500, currency="USD", payment_method=PaymentMethod.CARD)
    base.update(kw)
    return Transaction(**base)


def test_clean_transaction_is_approved():
    a = evaluate(tx(), History(paid_orders=2, avg_paid_amount=10_000, known_address=True), Velocity(), [], NOW)
    assert a.decision == Decision.APPROVE and a.score == 0 and a.hits == ()


def test_blocklist_always_rejects():
    a = evaluate(tx(), History(paid_orders=50), Velocity(), ["EMAIL"], NOW)
    assert a.decision == Decision.REJECT and a.score == 100 and a.reasons == ["BLOCKLIST"]


def test_card_testing_pattern_goes_to_review_or_reject():
    a = evaluate(
        tx(), History(payment_failures_24h=4), Velocity(customer_cards_24h=4, customer_checkouts_1h=6), [], NOW
    )
    assert set(a.reasons) == {"CARD_TESTING", "PAYMENT_FAILURES", "VELOCITY_CUSTOMER"}
    assert a.score == 85 and a.decision == Decision.REJECT


def test_new_account_first_big_order_to_new_address_needs_review():
    t = tx(
        amount=95_000,
        account_created_at=NOW - timedelta(hours=2),
        shipping=Address("US", "CA", "1 Market St", "+14155550100"),
    )
    a = evaluate(t, History(), Velocity(), [], NOW)
    assert set(a.reasons) == {"NEW_ACCOUNT_HIGH_VALUE", "FIRST_ORDER_HIGH_VALUE", "NEW_ADDRESS_HIGH_VALUE"}
    assert a.decision == Decision.REVIEW


def test_returned_shipments_add_risk():
    a = evaluate(tx(), History(paid_orders=1, orders=4, shipments_returned=2, known_address=True), Velocity(), [], NOW)
    assert a.reasons == ["RETURNED_SHIPMENTS"] and a.score == 20


def test_wallet_payment_lowers_score_but_not_below_zero():
    a = evaluate(tx(payment_method=PaymentMethod.WALLET), History(payment_failures_24h=3), Velocity(), [], NOW)
    assert a.reasons == ["PAYMENT_FAILURES", "WALLET_AUTHENTICATED"] and a.score == 20
    # Trả góp qua đối tác (ADR 0016): không được trừ điểm như ví, các luật khác vẫn chạy.
    a = evaluate(tx(payment_method=PaymentMethod.BNPL), History(payment_failures_24h=3), Velocity(), [], NOW)
    assert a.reasons == ["PAYMENT_FAILURES"] and a.score == 25


def test_trusted_customer_offsets_and_score_never_negative():
    a = evaluate(
        tx(lines=(Line("p1", 25, 1000),)), History(paid_orders=10, avg_paid_amount=12_500), Velocity(), [], NOW
    )
    assert a.reasons == ["HIGH_QUANTITY", "TRUSTED_CUSTOMER"] and a.score == 0


def test_country_mismatch_and_unknown_currency_skips_value_rules():
    t = tx(currency="THB", amount=10**12, card_country="gb", shipping=Address("US"))
    a = evaluate(t, History(), Velocity(), [], NOW)
    assert a.reasons == ["COUNTRY_MISMATCH"]


def test_velocity_unavailable_is_recorded_without_points():
    a = evaluate(tx(), History(), None, [], NOW)
    assert a.reasons == ["VELOCITY_UNAVAILABLE"] and a.decision == Decision.APPROVE


def test_stream_features_add_short_window_signals():
    clean = History(paid_orders=2, avg_paid_amount=10_000, known_address=True)
    s = StreamFeatures(
        payment_failures_1h=4, orders_1h=6, cancellations_24h=3, distinct_addresses_24h=3, computed_at=NOW
    )
    a = evaluate(tx(), clean, Velocity(), [], NOW, stream=s)
    assert a.reasons == [
        "STREAM_PAYMENT_FAILURES",
        "STREAM_ORDER_BURST",
        "STREAM_CANCELLATIONS",
        "STREAM_ADDRESS_HOPPING",
    ]
    assert a.score == 65 and a.decision == Decision.REVIEW
    # không có feature stream: giữ nguyên kết quả cũ
    assert evaluate(tx(), clean, Velocity(), [], NOW).score == 0
