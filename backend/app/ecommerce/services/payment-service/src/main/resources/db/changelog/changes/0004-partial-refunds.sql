ALTER TABLE payments ADD COLUMN refunded_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;
ALTER TABLE payments ADD CONSTRAINT payments_refunded_amount_chk CHECK (refunded_amount >= 0 AND refunded_amount <= amount);
UPDATE payments SET refunded_amount = amount WHERE status = 'REFUNDED';

CREATE TABLE payment_refunds (
    refund_id  VARCHAR(64)    PRIMARY KEY,
    payment_id BIGINT         NOT NULL REFERENCES payments (id),
    order_id   VARCHAR(36)    NOT NULL,
    amount     NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency   VARCHAR(3)     NOT NULL,
    reason     TEXT,
    created_at TIMESTAMPTZ    NOT NULL
);
CREATE INDEX payment_refunds_order ON payment_refunds (order_id);
