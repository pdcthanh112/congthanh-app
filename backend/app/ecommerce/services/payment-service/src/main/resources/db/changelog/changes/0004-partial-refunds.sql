--liquibase formatted sql

--changeset payment-service:0004-partial-refunds
-- Hoàn một phần cho trả hàng (returns-service). Mỗi lần hoàn một dòng; refund_id là khoá idempotency
-- (return id), nên activity Temporal gọi lại không tạo lần hoàn thứ hai. refunded_amount là tổng đã hoàn,
-- không bao giờ vượt amount.
ALTER TABLE payments ADD COLUMN refunded_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;
ALTER TABLE payments ADD CONSTRAINT payments_refunded_amount_chk CHECK (refunded_amount >= 0 AND refunded_amount <= amount);
UPDATE payments SET refunded_amount = amount WHERE status = 'REFUNDED';

CREATE TABLE payment_refunds (
    refund_id  VARCHAR(64)    PRIMARY KEY,
    payment_id VARCHAR(36)    NOT NULL REFERENCES payments (id),
    order_id   VARCHAR(36)    NOT NULL,
    amount     NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency   VARCHAR(3)     NOT NULL,
    reason     TEXT,
    created_at TIMESTAMPTZ    NOT NULL
);
CREATE INDEX payment_refunds_order ON payment_refunds (order_id);
--rollback DROP TABLE payment_refunds; ALTER TABLE payments DROP CONSTRAINT payments_refunded_amount_chk; ALTER TABLE payments DROP COLUMN refunded_amount;
