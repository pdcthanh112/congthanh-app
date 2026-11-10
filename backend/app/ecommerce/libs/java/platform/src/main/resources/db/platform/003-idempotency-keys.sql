--liquibase formatted sql

--changeset platform:003-idempotency-keys
-- Idempotency-Key cho HTTP (draft-ietf-httpapi-idempotency-key-header).
-- Một key thuộc về một principal; request_hash chặn việc dùng lại key cho payload khác.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    principal     TEXT        NOT NULL,
    idem_key      TEXT        NOT NULL,
    request_hash  TEXT        NOT NULL,
    status        TEXT        NOT NULL,          -- IN_PROGRESS | COMPLETED
    response_code INT,
    response_type TEXT,
    response_body BYTEA,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (principal, idem_key)
);
CREATE INDEX IF NOT EXISTS idempotency_keys_created_at ON idempotency_keys (created_at);
