CREATE TABLE accounts (
    id          BIGINT         PRIMARY KEY,
    customer_id VARCHAR(64)    NOT NULL,
    kind        VARCHAR(16)    NOT NULL CHECK (kind IN ('STORE_CREDIT', 'GIFT_CARD')),
    currency    VARCHAR(3)     NOT NULL,
    balance     NUMERIC(19, 4) NOT NULL,
    held        NUMERIC(19, 4) NOT NULL,
    status      VARCHAR(8)     NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN')),
    created_at  TIMESTAMPTZ    NOT NULL,
    version     BIGINT         NOT NULL,
    CHECK (held >= 0 AND held <= balance)
);
CREATE UNIQUE INDEX accounts_customer_kind_currency ON accounts (customer_id, kind, currency) WHERE kind = 'STORE_CREDIT';

CREATE TABLE movements (
    id            BIGSERIAL      PRIMARY KEY,
    account_id    BIGINT         NOT NULL REFERENCES accounts (id),
    type          VARCHAR(8)     NOT NULL CHECK (type IN ('CREDIT', 'HOLD', 'DEBIT', 'RELEASE')),
    amount        NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency      VARCHAR(3)     NOT NULL,
    reason        VARCHAR(64),
    reference     VARCHAR(128)   NOT NULL,
    order_id      VARCHAR(36),
    balance_after NUMERIC(19, 4) NOT NULL,
    at            TIMESTAMPTZ    NOT NULL,
    UNIQUE (type, reference)
);
CREATE INDEX movements_account ON movements (account_id, id DESC);

CREATE TABLE holds (
    id         VARCHAR(128)   PRIMARY KEY,
    account_id BIGINT         NOT NULL REFERENCES accounts (id),
    order_id   VARCHAR(36),
    amount     NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    status     VARCHAR(8)     NOT NULL CHECK (status IN ('HELD', 'CAPTURED', 'RELEASED')),
    expires_at TIMESTAMPTZ    NOT NULL
);
CREATE INDEX holds_expiring ON holds (expires_at) WHERE status = 'HELD';
