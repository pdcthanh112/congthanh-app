-- reconciliation-service (schema reconciliation), ADR 0025.

-- Sổ nội bộ dựng từ event payment-service (PaymentCaptured/PaymentRefunded).
CREATE TABLE internal_txns (
    event_id     TEXT PRIMARY KEY,
    provider     TEXT        NOT NULL,
    provider_ref TEXT        NOT NULL,
    kind         TEXT        NOT NULL CHECK (kind IN ('CHARGE', 'REFUND')),
    amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency     CHAR(3)     NOT NULL,
    order_id     TEXT        NOT NULL,
    at           TIMESTAMPTZ NOT NULL
);
CREATE INDEX internal_txns_day ON internal_txns (provider, at);

CREATE TABLE runs (
    id                     UUID PRIMARY KEY,
    provider               TEXT        NOT NULL,
    business_date          DATE        NOT NULL,
    source                 TEXT        NOT NULL CHECK (source IN ('api', 'csv')),
    provider_transactions  INT         NOT NULL,
    matched                INT         NOT NULL,
    mismatched             INT         NOT NULL,
    provider_fees_minor    BIGINT      NOT NULL,
    currency               CHAR(3)     NOT NULL,
    superseded             BOOLEAN     NOT NULL DEFAULT false,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Chạy lại cùng ngày: lần mới thay lần cũ (cổng bổ sung giao dịch trễ), lần cũ giữ lại làm lịch sử.
CREATE UNIQUE INDEX runs_current ON runs (provider, business_date) WHERE NOT superseded;

-- Giao dịch phía cổng của từng lần chạy (giữ nguyên để làm bằng chứng kiểm toán).
CREATE TABLE provider_txns (
    run_id       UUID        NOT NULL REFERENCES runs (id),
    provider_ref TEXT        NOT NULL,
    kind         TEXT        NOT NULL,
    amount_minor BIGINT      NOT NULL,
    fee_minor    BIGINT      NOT NULL,
    currency     CHAR(3)     NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    payout_id    TEXT        NOT NULL DEFAULT ''
);
CREATE INDEX provider_txns_run ON provider_txns (run_id);

CREATE TABLE mismatches (
    id                    BIGSERIAL PRIMARY KEY,
    run_id                UUID        NOT NULL REFERENCES runs (id),
    provider              TEXT        NOT NULL,
    kind                  TEXT        NOT NULL,
    txn_kind              TEXT        NOT NULL,
    provider_ref          TEXT        NOT NULL,
    order_id              TEXT        NOT NULL DEFAULT '',
    provider_amount_minor BIGINT,
    internal_amount_minor BIGINT,
    currency              TEXT        NOT NULL,
    status                TEXT        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'SUPERSEDED')),
    resolved_by           TEXT,
    resolution_note       TEXT,
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX mismatches_status ON mismatches (status, created_at);

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
