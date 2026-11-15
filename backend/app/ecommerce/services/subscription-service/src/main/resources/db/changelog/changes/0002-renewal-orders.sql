CREATE TABLE renewal_orders (
    order_id        VARCHAR(36) PRIMARY KEY,
    subscription_id BIGINT      NOT NULL REFERENCES subscriptions (id),
    cycle           INT         NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
