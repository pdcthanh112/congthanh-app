"""Rule engine chấm điểm rủi ro giao dịch: thuần (không I/O), mỗi luật cộng/trừ điểm kèm mã lý do.

Điểm 0-100: >= reject_at → REJECT, >= review_at → REVIEW (duyệt tay), còn lại APPROVE.
Luật danh sách chặn (blocklist) luôn REJECT. Ngưỡng tiền tính theo đơn vị nhỏ nhất của từng tiền tệ.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import StrEnum


class Decision(StrEnum):
    APPROVE = "APPROVE"
    REVIEW = "REVIEW"
    REJECT = "REJECT"


class PaymentMethod(StrEnum):
    CARD = "CARD"
    WALLET = "WALLET"  # Apple Pay / Google Pay: thẻ đã token hoá, có xác thực thiết bị
    PAYPAL = "PAYPAL"
    BNPL = "BNPL"  # trả góp qua Affirm/Klarna/Afterpay: đối tác chịu rủi ro tín dụng, luật giao hàng vẫn áp dụng
    BANK_TRANSFER = "BANK_TRANSFER"


@dataclass(frozen=True)
class Address:
    country_code: str
    region_code: str = ""
    line1: str = ""
    phone: str = ""

    def fingerprint(self) -> str:
        return "|".join(p.strip().lower() for p in (self.country_code, self.region_code, self.line1, self.phone))


@dataclass(frozen=True)
class Line:
    product_id: str
    quantity: int
    unit_price: int


@dataclass(frozen=True)
class Transaction:
    """Thông tin checkout gửi sang để chấm điểm."""

    checkout_id: str
    customer_id: str
    amount: int
    currency: str
    payment_method: PaymentMethod
    email: str = ""
    phone: str = ""
    ip: str = ""
    device_id: str = ""
    card_fingerprint: str = ""
    card_country: str = ""
    shipping: Address | None = None
    lines: tuple[Line, ...] = ()
    account_created_at: datetime | None = None


@dataclass(frozen=True)
class History:
    """Lịch sử của khách, dựng từ ecommerce.order.events / ecommerce.payment.events."""

    paid_orders: int = 0
    avg_paid_amount: int = 0
    shipments_returned: int = 0  # kiện bị hoàn về (không giao được / khách từ chối nhận)
    orders: int = 0
    refunds_90d: int = 0
    payment_failures_24h: int = 0
    known_address: bool = False


@dataclass(frozen=True)
class Velocity:
    """Đếm theo cửa sổ thời gian (Redis). None = không đo được (Redis lỗi)."""

    customer_checkouts_1h: int = 0
    ip_checkouts_1h: int = 0
    device_customers_24h: int = 0
    customer_cards_24h: int = 0
    customer_addresses_7d: int = 0


@dataclass(frozen=True)
class StreamFeatures:
    """Feature theo thời gian thực do stream-processor (Flink) tính từ order/payment events
    (topic ecommerce.stream.customer-risk). None = chưa có hoặc đã cũ (service tự bỏ)."""

    orders_1h: int = 0
    order_value_1h: int = 0
    currency: str = ""
    payment_failures_1h: int = 0
    cancellations_24h: int = 0
    distinct_addresses_24h: int = 0
    computed_at: datetime | None = None


@dataclass(frozen=True)
class Thresholds:
    review_at: int = 50
    reject_at: int = 80
    # Giá trị "cao" theo tiền tệ (đơn vị nhỏ nhất). Tiền tệ không có trong bảng thì bỏ qua luật theo giá trị.
    high_value: dict[str, int] = field(
        default_factory=lambda: {"USD": 80_000, "CAD": 110_000, "EUR": 75_000, "GBP": 65_000}
    )
    max_line_quantity: int = 20
    new_account_age: timedelta = timedelta(days=1)


@dataclass(frozen=True)
class Hit:
    code: str
    points: int
    detail: str


@dataclass(frozen=True)
class Assessment:
    score: int
    decision: Decision
    hits: tuple[Hit, ...]

    @property
    def reasons(self) -> list[str]:
        return [h.code for h in self.hits]


def evaluate(
    tx: Transaction,
    history: History,
    velocity: Velocity | None,
    blocked: list[str],
    now: datetime,
    t: Thresholds | None = None,
    stream: StreamFeatures | None = None,
) -> Assessment:
    t = t or Thresholds()
    hits: list[Hit] = []

    def hit(code: str, points: int, detail: str) -> None:
        hits.append(Hit(code, points, detail))

    if blocked:
        for kind in blocked:
            hit("BLOCKLIST", 100, f"{kind} is on the blocklist")
        return Assessment(100, Decision.REJECT, tuple(hits))

    high = t.high_value.get(tx.currency)

    if velocity is None:
        hit("VELOCITY_UNAVAILABLE", 0, "velocity counters could not be read")
    else:
        if velocity.customer_checkouts_1h > 10:
            hit("VELOCITY_CUSTOMER", 45, f"{velocity.customer_checkouts_1h} checkouts in 1h")
        elif velocity.customer_checkouts_1h > 5:
            hit("VELOCITY_CUSTOMER", 25, f"{velocity.customer_checkouts_1h} checkouts in 1h")
        if velocity.ip_checkouts_1h > 20:
            hit("VELOCITY_IP", 20, f"{velocity.ip_checkouts_1h} checkouts from this IP in 1h")
        if velocity.device_customers_24h >= 3:
            hit("DEVICE_MANY_ACCOUNTS", 30, f"{velocity.device_customers_24h} accounts on this device in 24h")
        if velocity.customer_cards_24h >= 3:
            hit("CARD_TESTING", 35, f"{velocity.customer_cards_24h} different cards in 24h")
        if velocity.customer_addresses_7d >= 4:
            hit("MANY_ADDRESSES", 15, f"{velocity.customer_addresses_7d} shipping addresses in 7 days")

    if history.payment_failures_24h >= 3:
        hit("PAYMENT_FAILURES", 25, f"{history.payment_failures_24h} failed payments in 24h")

    if stream is not None:
        # Tín hiệu dồn dập trong thời gian ngắn mà lịch sử theo ngày (Postgres) chưa phản ánh kịp.
        if stream.payment_failures_1h >= 3:
            hit("STREAM_PAYMENT_FAILURES", 20, f"{stream.payment_failures_1h} failed payments in the last hour")
        if stream.orders_1h >= 5:
            hit("STREAM_ORDER_BURST", 15, f"{stream.orders_1h} orders in the last hour")
        if stream.cancellations_24h >= 3:
            hit("STREAM_CANCELLATIONS", 15, f"{stream.cancellations_24h} cancelled orders in 24h")
        if stream.distinct_addresses_24h >= 3:
            hit("STREAM_ADDRESS_HOPPING", 15, f"{stream.distinct_addresses_24h} shipping addresses in 24h")

    if high is not None:
        is_new = tx.account_created_at is not None and now - tx.account_created_at < t.new_account_age
        if is_new and tx.amount >= high // 2:
            hit("NEW_ACCOUNT_HIGH_VALUE", 20, "account younger than 1 day placing a large order")
        if history.paid_orders >= 3 and history.avg_paid_amount > 0 and tx.amount > 5 * history.avg_paid_amount:
            hit("AMOUNT_ANOMALY", 20, "amount is more than 5x the customer's average order")
        elif history.paid_orders == 0 and tx.amount >= high:
            hit("FIRST_ORDER_HIGH_VALUE", 25, "first order above the high-value threshold")
        if not history.known_address and tx.shipping is not None and tx.amount >= high // 2:
            hit("NEW_ADDRESS_HIGH_VALUE", 10, "large order to an address never used before")

    if tx.lines and max(line.quantity for line in tx.lines) >= t.max_line_quantity:
        hit("HIGH_QUANTITY", 10, f"a line has quantity >= {t.max_line_quantity}")

    if history.shipments_returned >= 2 and history.shipments_returned * 10 >= history.orders * 3:
        hit(
            "RETURNED_SHIPMENTS",
            20,
            f"{history.shipments_returned} of {history.orders} shipments returned to sender",
        )

    if tx.payment_method == PaymentMethod.WALLET and not blocked:
        # Apple Pay / Google Pay đã qua xác thực sinh trắc/thiết bị và thường được dời trách nhiệm chargeback.
        hit("WALLET_AUTHENTICATED", -5, "device-authenticated wallet payment")

    if (
        tx.card_country
        and tx.shipping is not None
        and tx.shipping.country_code
        and tx.card_country.upper() != tx.shipping.country_code.upper()
    ):
        hit(
            "COUNTRY_MISMATCH",
            15,
            f"card from {tx.card_country.upper()}, shipping to {tx.shipping.country_code.upper()}",
        )

    if history.refunds_90d >= 3:
        hit("FREQUENT_REFUNDS", 15, f"{history.refunds_90d} refunds in 90 days")

    if history.paid_orders >= 5 and history.shipments_returned == 0 and history.refunds_90d == 0:
        hit("TRUSTED_CUSTOMER", -15, f"{history.paid_orders} paid orders without problems")

    score = max(0, min(100, sum(h.points for h in hits)))
    if score >= t.reject_at:
        decision = Decision.REJECT
    elif score >= t.review_at:
        decision = Decision.REVIEW
    else:
        decision = Decision.APPROVE
    return Assessment(score, decision, tuple(hits))
