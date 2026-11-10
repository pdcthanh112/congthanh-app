--liquibase formatted sql

--changeset platform:002-processed-events
-- Kafka consumer chống xử lý trùng: ghi event_id trong cùng transaction với tác dụng phụ.
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     TEXT        NOT NULL,
    consumer     TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);
