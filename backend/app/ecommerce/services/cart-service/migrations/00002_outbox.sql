-- +goose Up
-- Transactional outbox (Debezium Outbox Event Router đọc bảng này) và bảng chống
-- xử lý trùng cho Kafka consumer.
CREATE TABLE outbox (
    id            UUID PRIMARY KEY,
    aggregatetype TEXT        NOT NULL,
    aggregateid   TEXT        NOT NULL,
    type          TEXT        NOT NULL,
    payload       BYTEA       NOT NULL,
    traceparent   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    event_id     TEXT PRIMARY KEY,
    consumer     TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- +goose Down
DROP TABLE processed_events;
DROP TABLE outbox;
