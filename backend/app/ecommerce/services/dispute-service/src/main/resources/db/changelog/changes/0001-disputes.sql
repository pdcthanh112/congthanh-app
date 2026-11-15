--liquibase formatted sql

--changeset dispute-service:0001-disputes
-- dispute-service (schema dispute_service), ADR 0025. Bằng chứng có dữ liệu cá nhân: chỉ nhóm FINANCE/SUPPORT đọc.
CREATE TABLE disputes (
    id                  BIGINT         PRIMARY KEY,
    provider            VARCHAR(16)    NOT NULL,
    provider_dispute_id VARCHAR(64)    NOT NULL,
    payment_id          VARCHAR(36),
    order_id            VARCHAR(36),
    customer_id         VARCHAR(64),
    amount              NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    fee                 NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency            VARCHAR(3)     NOT NULL,
    reason              VARCHAR(32)    NOT NULL,
    evidence_due_by     TIMESTAMPTZ,
    status              VARCHAR(16)    NOT NULL CHECK (status IN ('NEEDS_RESPONSE', 'SUBMITTED', 'ACCEPTED', 'WON', 'LOST')),
    evidence            JSONB,
    note                TEXT,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    version             BIGINT         NOT NULL,
    UNIQUE (provider, provider_dispute_id)
);
CREATE INDEX disputes_open_due ON disputes (evidence_due_by) WHERE status = 'NEEDS_RESPONSE';
CREATE INDEX disputes_order ON disputes (order_id);

-- Dữ kiện dựng từ event để gom bằng chứng.
CREATE TABLE order_facts (
    order_id        VARCHAR(36)    PRIMARY KEY,
    customer_id     VARCHAR(64),
    recipient_name  VARCHAR(200),
    region          VARCHAR(64),
    postal_code     VARCHAR(16),
    product_ids     JSONB,
    total           NUMERIC(19, 4),
    carrier         VARCHAR(64),
    tracking_number VARCHAR(128),
    shipped_at      TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ
);

CREATE TABLE payment_refs (
    provider     VARCHAR(16)  NOT NULL,
    provider_ref VARCHAR(128) NOT NULL,
    payment_id   VARCHAR(36)  NOT NULL,
    order_id     VARCHAR(36)  NOT NULL,
    customer_id  VARCHAR(64),
    PRIMARY KEY (provider, provider_ref)
);

CREATE TABLE order_refunds (
    refund_key VARCHAR(128)   PRIMARY KEY,
    order_id   VARCHAR(36)    NOT NULL,
    amount     NUMERIC(19, 4) NOT NULL,
    at         TIMESTAMPTZ    NOT NULL
);
CREATE INDEX order_refunds_order ON order_refunds (order_id);
--rollback DROP TABLE order_refunds, payment_refs, order_facts, disputes;
