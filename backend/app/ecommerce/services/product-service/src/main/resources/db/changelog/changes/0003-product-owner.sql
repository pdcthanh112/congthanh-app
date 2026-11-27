--liquibase formatted sql

--changeset product-service:0003-product-owner
-- Người bán sở hữu sản phẩm: subject (sub) trong JWT Keycloak của SELLER đã tạo sản phẩm. Variant mang cùng owner với
-- sản phẩm gốc. NULL nghĩa là sản phẩm của sàn (seed, hoặc do ADMIN tạo): chỉ ADMIN được sửa.
ALTER TABLE products ADD COLUMN owner_id VARCHAR(64);
--rollback ALTER TABLE products DROP COLUMN owner_id;
