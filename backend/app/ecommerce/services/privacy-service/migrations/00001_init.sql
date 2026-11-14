-- +goose Up
-- privacy-service (schema privacy). Giữ ít dữ liệu cá nhân nhất có thể: subject id (Keycloak sub) và email đã băm.

-- Lựa chọn đồng ý, chỉ ghi thêm: lịch sử là bằng chứng cho cơ quan quản lý.
CREATE TABLE consents (
    id             BIGSERIAL PRIMARY KEY,
    subject_id     TEXT        NOT NULL,
    purpose        TEXT        NOT NULL,
    granted        BOOLEAN     NOT NULL,
    source         TEXT        NOT NULL,
    policy_version TEXT        NOT NULL DEFAULT '',
    recorded_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX consents_subject_purpose ON consents (subject_id, purpose, recorded_at DESC);

CREATE TABLE dsr_requests (
    id               UUID PRIMARY KEY,
    subject_id       TEXT        NOT NULL,
    type             TEXT        NOT NULL CHECK (type IN ('ACCESS', 'DELETE', 'OPT_OUT')),
    status           TEXT        NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'PARTIALLY_COMPLETED', 'REJECTED')),
    email_sha256     TEXT        NOT NULL DEFAULT '',
    due_at           TIMESTAMPTZ NOT NULL,
    extended         BOOLEAN     NOT NULL DEFAULT false,
    extension_reason TEXT        NOT NULL DEFAULT '',
    created_at       TIMESTAMPTZ NOT NULL,
    completed_at     TIMESTAMPTZ,
    last_reminded_at TIMESTAMPTZ,
    version          BIGINT      NOT NULL DEFAULT 1
);
-- Mỗi khách chỉ có một yêu cầu đang mở cho mỗi loại.
CREATE UNIQUE INDEX dsr_requests_one_open ON dsr_requests (subject_id, type) WHERE status = 'IN_PROGRESS';
CREATE INDEX dsr_requests_status ON dsr_requests (status, created_at);

CREATE TABLE dsr_tasks (
    request_id       UUID        NOT NULL REFERENCES dsr_requests (id),
    processor        TEXT        NOT NULL,
    status           TEXT        NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'RETAINED')),
    retention_reason TEXT        NOT NULL DEFAULT '',
    object_key       TEXT        NOT NULL DEFAULT '',
    completed_at     TIMESTAMPTZ,
    PRIMARY KEY (request_id, processor)
);

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
DROP TABLE processed_events, outbox, dsr_tasks, dsr_requests, consents;
