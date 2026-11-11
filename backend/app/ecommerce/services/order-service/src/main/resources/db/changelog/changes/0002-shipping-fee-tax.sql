--liquibase formatted sql

--changeset order-service:0002-shipping-fee-tax
-- Phí giao hàng và thuế do checkout-service báo giá; total = subtotal - discount + shipping_fee + tax.
ALTER TABLE orders ADD COLUMN shipping_fee NUMERIC(19, 4) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN tax NUMERIC(19, 4) NOT NULL DEFAULT 0;
--rollback ALTER TABLE orders DROP COLUMN tax; ALTER TABLE orders DROP COLUMN shipping_fee;
