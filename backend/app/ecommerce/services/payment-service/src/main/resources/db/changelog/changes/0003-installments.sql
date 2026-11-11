--liquibase formatted sql

--changeset payment-service:0003-installments
-- ADR 0016: trả góp qua đối tác (AFFIRM, KLARNA, AFTERPAY). Cột method là VARCHAR(32) không có CHECK nên
-- không phải đổi. authorization của đối tác hết hạn sớm hơn thẻ: lưu hạn chót capture để theo dõi đơn treo.
ALTER TABLE payments ADD COLUMN authorization_expires_at TIMESTAMPTZ;
CREATE INDEX payments_authorization_expires_at ON payments (authorization_expires_at)
    WHERE status = 'AUTHORIZED' AND authorization_expires_at IS NOT NULL;
--rollback DROP INDEX payments_authorization_expires_at; ALTER TABLE payments DROP COLUMN authorization_expires_at;
