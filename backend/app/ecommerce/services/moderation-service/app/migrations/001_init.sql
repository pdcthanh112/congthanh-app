-- moderation-service (schema moderation), ADR 0026.
-- Nội dung được giữ để người duyệt xem; là dữ liệu cá nhân nên tham gia luồng xoá của privacy-service.
CREATE TABLE items (
    id              UUID PRIMARY KEY,
    content_type    TEXT        NOT NULL,
    content_id      TEXT        NOT NULL,
    author_id       TEXT        NOT NULL,
    text            TEXT        NOT NULL,
    content_hash    TEXT        NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('DECIDED', 'ESCALATED', 'APPEALED')),
    decision        TEXT        NOT NULL CHECK (decision IN ('APPROVE', 'REJECT', 'REDACT', 'ESCALATE')),
    categories      JSONB       NOT NULL DEFAULT '[]',
    redacted_text   TEXT        NOT NULL DEFAULT '',
    rationale       TEXT        NOT NULL DEFAULT '',
    decided_by      TEXT        NOT NULL,
    policy_version  TEXT        NOT NULL,
    appeal_reason   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (content_type, content_id, content_hash)
);
CREATE INDEX items_queue ON items (created_at) WHERE status IN ('ESCALATED', 'APPEALED');
CREATE INDEX items_hash ON items (content_hash, decided_at DESC) WHERE status = 'DECIDED';
CREATE INDEX items_author_rejects ON items (author_id, decided_at) WHERE decision = 'REJECT';

-- Lịch sử quyết định (cả tự động và người duyệt) làm bằng chứng và dữ liệu đánh giá độ chính xác.
CREATE TABLE decisions (
    id         BIGSERIAL PRIMARY KEY,
    item_id    UUID        NOT NULL REFERENCES items (id),
    decision   TEXT        NOT NULL,
    decided_by TEXT        NOT NULL,
    note       TEXT        NOT NULL DEFAULT '',
    at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE strikes (
    author_id    TEXT PRIMARY KEY,
    strikes      INT         NOT NULL,
    last_item_id UUID        NOT NULL,
    issued_at    TIMESTAMPTZ NOT NULL DEFAULT now()
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
