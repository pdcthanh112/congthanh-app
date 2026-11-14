--liquibase formatted sql

--changeset returns-service:0002-refund-method
-- Khách chọn nhận hoàn tiền về phương thức gốc hay thành store credit (ADR 0025).
ALTER TABLE return_requests ADD COLUMN refund_method VARCHAR(16) NOT NULL DEFAULT 'ORIGINAL_PAYMENT'
    CHECK (refund_method IN ('ORIGINAL_PAYMENT', 'STORE_CREDIT'));
--rollback ALTER TABLE return_requests DROP COLUMN refund_method;
