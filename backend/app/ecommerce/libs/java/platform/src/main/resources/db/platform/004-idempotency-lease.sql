--liquibase formatted sql

--changeset platform:004-idempotency-lease
-- Lease ngắn cho IN_PROGRESS: pod chết giữa request thì key không bị khoá tới hết TTL 24h.
-- lock_token: chỉ người đang giữ lease mới được complete/release (người cũ hết lease không ghi đè được).
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS lock_token   TEXT;
