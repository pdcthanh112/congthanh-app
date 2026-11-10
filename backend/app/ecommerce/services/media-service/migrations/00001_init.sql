-- +goose Up
-- Metadata file người dùng tải lên. Nội dung nằm ở object storage (S3/R2/MinIO), service chỉ cấp URL ký sẵn.
CREATE TABLE media (
    id            TEXT PRIMARY KEY,
    owner_id      TEXT        NOT NULL,
    purpose       TEXT        NOT NULL,
    object_key    TEXT        NOT NULL UNIQUE,
    file_name     TEXT        NOT NULL,
    content_type  TEXT        NOT NULL,
    size_bytes    BIGINT      NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'PENDING', -- PENDING → READY
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ
);
CREATE INDEX media_owner ON media (owner_id, created_at DESC);
CREATE INDEX media_pending ON media (created_at) WHERE status = 'PENDING';

-- +goose Down
DROP TABLE media;
