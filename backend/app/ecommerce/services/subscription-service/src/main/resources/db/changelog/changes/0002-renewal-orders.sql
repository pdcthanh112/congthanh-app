--liquibase formatted sql

--changeset subscription-service:0002-renewal-orders
-- Đơn của từng kỳ gia hạn (ADR 0027): khi order-service huỷ đơn (thẻ bị từ chối khi charge off-session) gói quay
-- lại đúng kỳ đó và vào dunning.
CREATE TABLE renewal_orders (
    order_id        VARCHAR(36) PRIMARY KEY,
    subscription_id UUID        NOT NULL REFERENCES subscriptions (id),
    cycle           INT         NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE renewal_orders;
