CREATE TABLE checkout_sessions (
    id          VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    order_id    VARCHAR(36) NOT NULL UNIQUE,
    order_code  VARCHAR(32),
    data        JSONB       NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT      NOT NULL
);
CREATE INDEX checkout_sessions_customer ON checkout_sessions (customer_id, created_at DESC);
CREATE INDEX checkout_sessions_pending_review ON checkout_sessions (status) WHERE status = 'PENDING_REVIEW';
