--liquibase formatted sql

--changeset loyalty-service:0001-loyalty
-- loyalty-service (schema loyalty_service), ADR 0026. Điểm khả dụng là nợ phải trả: ledger ghi từ event.
CREATE TABLE members (
    customer_id VARCHAR(64) PRIMARY KEY,
    tier        VARCHAR(8)  NOT NULL CHECK (tier IN ('MEMBER', 'SILVER', 'GOLD')),
    housed_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lots (
    id          BIGINT         PRIMARY KEY,
    customer_id VARCHAR(64)    NOT NULL REFERENCES members (customer_id),
    order_id    VARCHAR(36)    NOT NULL UNIQUE,
    points      BIGINT         NOT NULL CHECK (points > 0),
    remaining   BIGINT         NOT NULL CHECK (remaining >= 0 AND remaining <= points),
    status      VARCHAR(10)    NOT NULL CHECK (status IN ('PENDING', 'AVAILABLE', 'EXPIRED', 'REVERSED')),
    order_total NUMERIC(19, 4) NOT NULL,
    earned_at   TIMESTAMPTZ    NOT NULL,
    confirm_at  TIMESTAMPTZ,
    expires_at  TIMESTAMPTZ
);
CREATE INDEX lots_customer ON lots (customer_id, earned_at);
CREATE INDEX lots_pending_due ON lots (confirm_at) WHERE status = 'PENDING';
CREATE INDEX lots_expiring ON lots (expires_at) WHERE status = 'AVAILABLE';

CREATE TABLE redemptions (
    id          VARCHAR(128) PRIMARY KEY,
    customer_id VARCHAR(64)  NOT NULL,
    order_id    VARCHAR(36),
    points      BIGINT       NOT NULL CHECK (points > 0),
    status      VARCHAR(10)  NOT NULL CHECK (status IN ('RESERVED', 'REDEEMED', 'RELEASED')),
    allocations JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
--rollback DROP TABLE redemptions, lots, members;
