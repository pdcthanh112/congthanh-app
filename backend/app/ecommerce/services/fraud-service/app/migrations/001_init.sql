-- Kết quả chấm điểm: lưu cả APPROVE để làm nhãn huấn luyện mô hình về sau.
CREATE TABLE assessments (
    id             UUID PRIMARY KEY,
    checkout_id    TEXT        NOT NULL,
    customer_id    TEXT        NOT NULL,
    order_id       TEXT,
    amount_minor   BIGINT      NOT NULL,
    currency       CHAR(3)     NOT NULL,
    payment_method TEXT        NOT NULL,
    score          INT         NOT NULL,
    decision       TEXT        NOT NULL CHECK (decision IN ('APPROVE', 'REVIEW', 'REJECT')),
    reasons        JSONB       NOT NULL,
    signals        JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX assessments_checkout ON assessments (checkout_id, created_at DESC);
CREATE INDEX assessments_customer ON assessments (customer_id, created_at DESC);
CREATE INDEX assessments_order ON assessments (order_id) WHERE order_id IS NOT NULL;

-- Hàng chờ duyệt thủ công cho quyết định REVIEW.
CREATE TABLE reviews (
    assessment_id UUID PRIMARY KEY REFERENCES assessments (id) ON DELETE CASCADE,
    status        TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    decided_by    TEXT,
    note          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at    TIMESTAMPTZ
);
CREATE INDEX reviews_status ON reviews (status, created_at);

-- Lịch sử đơn của khách (dựng từ event), dùng cho các luật theo lịch sử.
CREATE TABLE customer_orders (
    order_id       TEXT PRIMARY KEY,
    customer_id    TEXT        NOT NULL,
    amount_minor   BIGINT      NOT NULL,
    currency       CHAR(3)     NOT NULL,
    payment_method TEXT,
    address_fp     TEXT,
    status         TEXT        NOT NULL DEFAULT 'PLACED' CHECK (status IN ('PLACED', 'PAID', 'CANCELLED', 'REFUNDED')),
    cod_refused    BOOLEAN     NOT NULL DEFAULT FALSE,
    placed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX customer_orders_customer ON customer_orders (customer_id, placed_at DESC);

CREATE TABLE payment_failures (
    id          BIGSERIAL PRIMARY KEY,
    customer_id TEXT        NOT NULL,
    order_id    TEXT        NOT NULL,
    reason      TEXT        NOT NULL DEFAULT '',
    failed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX payment_failures_customer ON payment_failures (customer_id, failed_at DESC);

CREATE TABLE refunds (
    order_id    TEXT PRIMARY KEY,
    customer_id TEXT        NOT NULL,
    refunded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refunds_customer ON refunds (customer_id, refunded_at DESC);

-- Danh sách chặn do backoffice quản lý.
CREATE TABLE blocklist (
    kind       TEXT        NOT NULL CHECK (kind IN ('EMAIL', 'PHONE', 'IP', 'DEVICE', 'CARD', 'CUSTOMER')),
    value      TEXT        NOT NULL,
    reason     TEXT        NOT NULL DEFAULT '',
    created_by TEXT        NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (kind, value)
);

-- Debezium Outbox Router → topic ecommerce.fraud.events (cùng định dạng với service Go/Java).
CREATE TABLE outbox (
    id            UUID PRIMARY KEY,
    aggregatetype TEXT        NOT NULL,
    aggregateid   TEXT        NOT NULL,
    type          TEXT        NOT NULL,
    payload       BYTEA       NOT NULL,
    traceparent   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    event_id     TEXT PRIMARY KEY,
    consumer     TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
