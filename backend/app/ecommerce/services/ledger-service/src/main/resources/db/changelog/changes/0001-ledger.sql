--liquibase formatted sql

--changeset ledger-service:0001-ledger splitStatements:false
-- Sổ cái kép (schema ledger_service). journal_entries và postings CHỈ ghi thêm: trigger chặn UPDATE/DELETE,
-- sai thì ghi bút toán đảo. Số tiền NUMERIC(19,4), không bao giờ float. Giữ tối thiểu 7 năm (chứng từ thuế).
CREATE TABLE accounts (
    code       VARCHAR(128) PRIMARY KEY,
    type       VARCHAR(16)  NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'CONTRA_REVENUE', 'EXPENSE')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE journal_entries (
    id              BIGINT       PRIMARY KEY,
    source_type     VARCHAR(32)  NOT NULL,
    source_id       VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(64)  UNIQUE,
    description     TEXT,
    booking_date    DATE         NOT NULL,
    created_by      VARCHAR(160) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX journal_entries_source ON journal_entries (source_type, source_id);
CREATE INDEX journal_entries_booking_date ON journal_entries (booking_date);

CREATE TABLE postings (
    entry_id     BIGINT         NOT NULL REFERENCES journal_entries (id),
    line_no      INT            NOT NULL,
    account_code VARCHAR(128)   NOT NULL REFERENCES accounts (code),
    side         VARCHAR(6)     NOT NULL CHECK (side IN ('DEBIT', 'CREDIT')),
    amount       NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency     VARCHAR(3)     NOT NULL,
    PRIMARY KEY (entry_id, line_no)
);
CREATE INDEX postings_account ON postings (account_code);

CREATE TABLE account_balances (
    account_code VARCHAR(128)   NOT NULL REFERENCES accounts (code),
    currency     VARCHAR(3)     NOT NULL,
    debits       NUMERIC(23, 4) NOT NULL DEFAULT 0,
    credits      NUMERIC(23, 4) NOT NULL DEFAULT 0,
    PRIMARY KEY (account_code, currency)
);

-- Event không hạch toán được (thiếu luật/thiếu trường): chờ người xử lý, không đoán.
CREATE TABLE unposted_events (
    event_id    VARCHAR(64) PRIMARY KEY,
    event_type  VARCHAR(160),
    reason      TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

-- Bút toán tay chờ duyệt (maker/checker).
CREATE TABLE manual_entries (
    id                BIGINT       PRIMARY KEY,
    description       TEXT         NOT NULL,
    booking_date      DATE         NOT NULL,
    postings          JSONB        NOT NULL,
    maker             VARCHAR(160) NOT NULL,
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reverses_entry_id BIGINT REFERENCES journal_entries (id),
    checker           VARCHAR(160),
    posted_entry_id   BIGINT REFERENCES journal_entries (id),
    note              TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_at        TIMESTAMPTZ,
    CHECK (checker IS NULL OR checker <> maker)
);

CREATE FUNCTION ledger_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ledger table % is append-only (% not allowed); post a reversal instead', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER journal_entries_append_only BEFORE UPDATE OR DELETE OR TRUNCATE ON journal_entries
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_append_only();
CREATE TRIGGER postings_append_only BEFORE UPDATE OR DELETE OR TRUNCATE ON postings
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_append_only();
--rollback DROP TABLE manual_entries, unposted_events, account_balances, postings, journal_entries, accounts; DROP FUNCTION ledger_append_only();
