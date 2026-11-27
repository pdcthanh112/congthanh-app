--liquibase formatted sql

--changeset product-service:0005-attribute-groups-options-templates splitStatements:false
-- ADR 0033: thông số sản phẩm (thuộc tính mô tả) có nhóm hiển thị, kiểu chọn nhiều giá trị, danh sách giá trị cho
-- phép và mẫu thuộc tính theo danh mục. Trục biến thể (variant_option) không đổi.

-- Nhóm hiển thị của thông số ("Màn hình", "Pin", "Chất liệu & kiểu dáng").
CREATE TABLE attribute_group (
    id            bigint       NOT NULL,
    name          varchar(255) NOT NULL,
    display_order integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL,
    created_by    varchar(255) NOT NULL,
    updated_at    timestamptz  NOT NULL,
    updated_by    varchar(255) NOT NULL,
    CONSTRAINT attribute_group_pkey PRIMARY KEY (id),
    CONSTRAINT attribute_group_name_not_blank CHECK (btrim(name) <> '')
);
CREATE UNIQUE INDEX attribute_group_name_key ON attribute_group (lower(name));

-- Xoá nhóm thì thuộc tính trong nhóm thành "không nhóm", không mất thuộc tính.
ALTER TABLE product_attribute ADD COLUMN group_id bigint;
ALTER TABLE product_attribute ADD CONSTRAINT fk_product_attribute_group
    FOREIGN KEY (group_id) REFERENCES attribute_group (id) ON DELETE SET NULL;
CREATE INDEX idx_product_attribute_group ON product_attribute (group_id);

ALTER TABLE product_attribute DROP CONSTRAINT product_attribute_data_type_check;
ALTER TABLE product_attribute ADD CONSTRAINT product_attribute_data_type_check
    CHECK (data_type IN ('TEXT', 'NUMBER', 'BOOLEAN', 'MULTI_SELECT'));

-- Giá trị cho phép của thuộc tính TEXT/MULTI_SELECT. Thuộc tính có danh sách này thì chỉ nhận giá trị trong danh sách.
CREATE TABLE product_attribute_option (
    id                   bigint       NOT NULL,
    product_attribute_id bigint       NOT NULL,
    value                varchar(255) NOT NULL,
    display_order        integer      NOT NULL DEFAULT 0,
    CONSTRAINT product_attribute_option_pkey PRIMARY KEY (id),
    CONSTRAINT product_attribute_option_value_not_blank CHECK (btrim(value) <> ''),
    CONSTRAINT fk_product_attribute_option_attribute FOREIGN KEY (product_attribute_id)
        REFERENCES product_attribute (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX product_attribute_option_value_key ON product_attribute_option (product_attribute_id, lower(value));

-- Một sản phẩm có thể mang nhiều giá trị của cùng thuộc tính MULTI_SELECT, nhưng không trùng giá trị.
ALTER TABLE product_attribute_value DROP CONSTRAINT product_attribute_value_product_attribute_key;
CREATE UNIQUE INDEX product_attribute_value_product_attribute_value_key
    ON product_attribute_value (product_id, product_attribute_id, lower(value));

-- Ràng buộc qua nhiều dòng mà CHECK không diễn đạt được: thuộc tính không phải MULTI_SELECT chỉ có một giá trị trên
-- mỗi sản phẩm, và thuộc tính có danh sách giá trị cho phép chỉ nhận giá trị trong danh sách. Kiểm lúc commit
-- (DEFERRABLE) để service thay giá trị cũ bằng giá trị mới trong cùng transaction.
CREATE FUNCTION product_attribute_value_check() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    attr_type   varchar(16);
    value_count integer;
BEGIN
    SELECT data_type INTO attr_type FROM product_attribute WHERE id = NEW.product_attribute_id;
    IF attr_type <> 'MULTI_SELECT' THEN
        SELECT count(*) INTO value_count FROM product_attribute_value
        WHERE product_id = NEW.product_id AND product_attribute_id = NEW.product_attribute_id;
        IF value_count > 1 THEN
            RAISE EXCEPTION 'attribute % accepts a single value per product', NEW.product_attribute_id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM product_attribute_option WHERE product_attribute_id = NEW.product_attribute_id)
       AND NOT EXISTS (SELECT 1 FROM product_attribute_option
                       WHERE product_attribute_id = NEW.product_attribute_id AND lower(value) = lower(NEW.value)) THEN
        RAISE EXCEPTION 'value % is not an allowed option of attribute %', NEW.value, NEW.product_attribute_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER product_attribute_value_check
    AFTER INSERT OR UPDATE ON product_attribute_value
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION product_attribute_value_check();

-- Đổi một thuộc tính khỏi MULTI_SELECT khi đã có sản phẩm mang nhiều giá trị sẽ làm dữ liệu sai kiểu: chặn.
CREATE FUNCTION product_attribute_type_check() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.data_type <> 'MULTI_SELECT' AND EXISTS (
        SELECT 1 FROM product_attribute_value WHERE product_attribute_id = NEW.id
        GROUP BY product_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'attribute % has products with several values', NEW.id USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER product_attribute_type_check
    AFTER UPDATE OF data_type ON product_attribute
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION product_attribute_type_check();

-- Mẫu thông số của danh mục: thuộc tính nào áp dụng cho sản phẩm trong danh mục, có bắt buộc không, thứ tự trong form.
-- category_id là id ở catalog-service (database per service, không có FK); service kiểm danh mục tồn tại khi ghi.
CREATE TABLE category_attribute (
    category_id          varchar(32) NOT NULL,
    product_attribute_id bigint      NOT NULL,
    is_required          boolean     NOT NULL DEFAULT false,
    display_order        integer     NOT NULL DEFAULT 0,
    CONSTRAINT category_attribute_pkey PRIMARY KEY (category_id, product_attribute_id),
    CONSTRAINT fk_category_attribute_attribute FOREIGN KEY (product_attribute_id)
        REFERENCES product_attribute (id) ON DELETE CASCADE
);
CREATE INDEX idx_category_attribute_attribute ON category_attribute (product_attribute_id);

--rollback DROP TABLE category_attribute;
--rollback DROP TRIGGER product_attribute_type_check ON product_attribute;
--rollback DROP FUNCTION product_attribute_type_check();
--rollback DROP TRIGGER product_attribute_value_check ON product_attribute_value;
--rollback DROP FUNCTION product_attribute_value_check();
--rollback DROP INDEX product_attribute_value_product_attribute_value_key;
--rollback ALTER TABLE product_attribute_value ADD CONSTRAINT product_attribute_value_product_attribute_key UNIQUE (product_id, product_attribute_id);
--rollback DROP TABLE product_attribute_option;
--rollback ALTER TABLE product_attribute DROP CONSTRAINT product_attribute_data_type_check;
--rollback ALTER TABLE product_attribute ADD CONSTRAINT product_attribute_data_type_check CHECK (data_type IN ('TEXT', 'NUMBER', 'BOOLEAN'));
--rollback ALTER TABLE product_attribute DROP COLUMN group_id;
--rollback DROP TABLE attribute_group;
