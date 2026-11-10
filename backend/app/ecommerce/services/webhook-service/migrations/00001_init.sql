-- +goose Up
CREATE TABLE webhooks (
    id          TEXT PRIMARY KEY,
    name        TEXT        NOT NULL,
    url         TEXT        NOT NULL,
    -- Khoá ký HMAC. Chỉ trả về một lần lúc tạo; nên bật mã hoá ở tầng lưu trữ (TDE/pgcrypto) ở prod.
    secret      TEXT        NOT NULL,
    events      TEXT[]      NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX webhooks_events ON webhooks USING GIN (events) WHERE active;

-- Mỗi (webhook, event) là một lần giao; worker gửi lại theo backoff tới khi thành công hoặc DEAD.
CREATE TABLE deliveries (
    id               TEXT PRIMARY KEY,
    webhook_id       TEXT        NOT NULL REFERENCES webhooks (id) ON DELETE CASCADE,
    event_id         TEXT        NOT NULL,
    event_type       TEXT        NOT NULL,
    payload          JSONB       NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCEEDED, DEAD
    attempts         INT         NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_status_code INT,
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at     TIMESTAMPTZ,
    UNIQUE (webhook_id, event_id)
);
CREATE INDEX deliveries_due ON deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX deliveries_webhook ON deliveries (webhook_id, created_at DESC);

CREATE TABLE processed_events (
    event_id     TEXT PRIMARY KEY,
    consumer     TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- +goose Down
DROP TABLE processed_events;
DROP TABLE deliveries;
DROP TABLE webhooks;
