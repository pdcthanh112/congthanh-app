CREATE TABLE payments (
    id             BIGINT         PRIMARY KEY, -- Snowflake
    order_id       VARCHAR(36)    NOT NULL UNIQUE,
    customer_id    VARCHAR(64),
    amount         NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL,
    method         VARCHAR(32)    NOT NULL,
    provider       VARCHAR(32)    NOT NULL,
    provider_ref   VARCHAR(255),
    status         VARCHAR(16)    NOT NULL,
    redirect_url   VARCHAR(4096),
    failure_reason TEXT,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT payments_status_chk CHECK (status IN
        ('INITIATED', 'PENDING_ACTION', 'AUTHORIZED', 'CAPTURED', 'DECLINED', 'VOIDED', 'REFUNDED'))
);

CREATE INDEX payments_provider_ref ON payments (provider, provider_ref) WHERE provider_ref IS NOT NULL;
