-- Outbox để trả lời privacy-service (ErasureCompleted), ADR 0027. Debezium connector recommendation-outbox.
CREATE TABLE outbox (
    id            UUID PRIMARY KEY,
    aggregatetype TEXT        NOT NULL,
    aggregateid   TEXT        NOT NULL,
    type          TEXT        NOT NULL,
    payload       BYTEA       NOT NULL,
    traceparent   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
