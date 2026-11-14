--liquibase formatted sql

--changeset order-service:0003-tenders
-- Phần trả bằng store credit và điểm thưởng (ADR 0027); saga chỉ charge cổng phần còn lại.
ALTER TABLE orders ADD COLUMN store_credit NUMERIC(19, 4) CHECK (store_credit IS NULL OR store_credit >= 0);
ALTER TABLE orders ADD COLUMN loyalty_points BIGINT NOT NULL DEFAULT 0 CHECK (loyalty_points >= 0);
--rollback ALTER TABLE orders DROP COLUMN loyalty_points; ALTER TABLE orders DROP COLUMN store_credit;
