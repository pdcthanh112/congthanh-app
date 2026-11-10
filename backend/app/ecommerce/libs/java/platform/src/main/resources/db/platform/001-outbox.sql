--liquibase formatted sql

--changeset platform:001-outbox
-- Transactional outbox, cùng cấu trúc với service Go (libs/go/platform/outbox).
-- Debezium Outbox Event Router đọc bảng này qua WAL và đẩy lên topic ecom.<aggregatetype>.events.
CREATE TABLE IF NOT EXISTS outbox (
    id            UUID PRIMARY KEY,
    aggregatetype TEXT        NOT NULL,
    aggregateid   TEXT        NOT NULL,
    type          TEXT        NOT NULL,
    payload       BYTEA       NOT NULL,
    traceparent   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
