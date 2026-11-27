--liquibase formatted sql

--changeset product-service:0006-product-specifications
-- ADR 0033: thông số tự do chỉ để hiển thị (bảng thông số dài dán từ nhà sản xuất), ngoài mẫu thuộc tính của danh mục.
-- Không lọc, không so sánh, không dịch theo thuộc tính: thứ cần những việc đó phải là product_attribute (EAV).
-- Dạng: [{"name": "Màn hình" | null, "items": [{"name": "Kích thước", "value": "6.7 inch"}]}]; domain kiểm chi tiết.
ALTER TABLE products ADD COLUMN specifications jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE products ADD CONSTRAINT products_specifications_array
    CHECK (jsonb_typeof(specifications) = 'array');
-- Trần kích thước để một sản phẩm không làm phình read model và payload trang chi tiết.
ALTER TABLE products ADD CONSTRAINT products_specifications_size
    CHECK (octet_length(specifications::text) <= 65536);
-- Thông số thuộc về sản phẩm gốc; biến thể dùng chung của cha.
ALTER TABLE products ADD CONSTRAINT products_specifications_root_only
    CHECK (parent_id IS NULL OR specifications = '[]'::jsonb);
--rollback ALTER TABLE products DROP COLUMN specifications;
