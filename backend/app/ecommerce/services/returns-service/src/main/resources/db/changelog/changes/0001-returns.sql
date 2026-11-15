CREATE TABLE order_snapshots (
    order_id     VARCHAR(36)    PRIMARY KEY,
    customer_id  VARCHAR(64),
    status       VARCHAR(16),
    payment_id   VARCHAR(36),
    currency     VARCHAR(3),
    total        NUMERIC(19, 4),
    lines        JSONB,
    delivered_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE return_requests (
    id             BIGINT         PRIMARY KEY,
    order_id       VARCHAR(36)    NOT NULL,
    customer_id    VARCHAR(64)    NOT NULL,
    rma_code       VARCHAR(16)    NOT NULL UNIQUE,
    currency       VARCHAR(3),
    lines          JSONB          NOT NULL,
    status         VARCHAR(16)    NOT NULL CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'RECEIVED',
                                                   'REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED', 'CANCELLED')),
    refund_amount  NUMERIC(19, 4) CHECK (refund_amount IS NULL OR refund_amount >= 0),
    restocking_fee NUMERIC(19, 4),
    payment_id     VARCHAR(36),
    note           TEXT,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL
);
CREATE INDEX return_requests_order ON return_requests (order_id);
CREATE INDEX return_requests_customer ON return_requests (customer_id, created_at DESC);
CREATE INDEX return_requests_status ON return_requests (status, updated_at);
