CREATE TABLE subscriptions (
    id                 BIGINT       PRIMARY KEY,
    customer_id        VARCHAR(64)  NOT NULL,
    lines              JSONB        NOT NULL,
    interval_days      INT          NOT NULL CHECK (interval_days BETWEEN 7 AND 90),
    next_delivery_date DATE         NOT NULL,
    payment_method_ref VARCHAR(128) NOT NULL,
    address_id         VARCHAR(64),
    status             VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'PAST_DUE', 'CANCELLED')),
    cycle              INT          NOT NULL,
    attempts           INT          NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ,
    reminded_cycle     INT          NOT NULL DEFAULT 0,
    paused_until       DATE,
    cancel_reason      TEXT,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL
);
CREATE INDEX subscriptions_customer ON subscriptions (customer_id, created_at DESC);
CREATE INDEX subscriptions_due ON subscriptions (next_delivery_date) WHERE status = 'ACTIVE';
CREATE INDEX subscriptions_retry ON subscriptions (next_attempt_at) WHERE status = 'PAST_DUE';
