-- Write model của catalog (ADR 0030). Postgres là nguồn sự thật, giữ dữ liệu chuẩn hoá và mọi ràng buộc toàn vẹn;
-- trang chi tiết đọc read model Mongo, tìm kiếm/lọc/facet đọc Elasticsearch của search-service.
--
--   products                  sản phẩm gốc (parent_id NULL) và variant (parent_id = sản phẩm gốc, đúng một cấp)
--   product_attribute         định nghĩa thuộc tính mô tả (EAV có kiểu): code, kiểu dữ liệu, đơn vị, cờ lọc/tìm
--   product_attribute_value   giá trị thuộc tính của từng sản phẩm, một giá trị cho mỗi (sản phẩm, thuộc tính)
--   variant_option            trục variant dùng chung (Color, Size, Capacity)
--   variant_option_value      giá trị được phép của một trục trên một sản phẩm gốc (Black, 128 GB)
--   variant_option_combination  variant chọn đúng một giá trị trên mỗi trục; FK ba cột bảo đảm giá trị thuộc
--                             cùng trục và cùng sản phẩm gốc với variant
--
-- Giá: products.price là giá niêm yết gốc do catalog nhập (kèm currency). Giá bán theo bảng giá, lịch sử giá,
-- khuyến mãi thuộc pricing-service (seed từ ProductUpserted, phát PriceChanged vào read model).

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

CREATE TABLE products (
    id          bigint       NOT NULL, -- Snowflake
    parent_id   bigint,
    name        varchar(255) NOT NULL,
    slug        varchar(255),
    sku         varchar(64),
    gtin        varchar(14),
    description text,
    has_variant boolean      NOT NULL DEFAULT false,
    is_featured boolean      NOT NULL DEFAULT false,
    price       numeric(19,4), -- đủ cho đơn vị tiền 3 chữ số lẻ (BHD, KWD)
    currency    character(3),  -- ISO 4217 của price; bắt buộc khi có giá
    brand       bigint,        -- id thương hiệu ở catalog-service
    thumbnail   bigint,        -- id ảnh (media-service) trong product_images
    supplier    varchar(255),
    status      varchar(16)  NOT NULL DEFAULT 'INACTIVE',
    -- Variant: id các variant_option_value đã chọn, sắp tăng dần, nối bằng '-'. Unique trong cùng sản phẩm gốc
    -- nên hai variant không thể trùng tổ hợp (Black/128 GB).
    variant_key varchar(255),
    version     bigint       NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    -- Cột phụ cho FK "variant chỉ treo dưới sản phẩm gốc" (không map vào JPA).
    root_marker   boolean GENERATED ALWAYS AS (CASE WHEN parent_id IS NULL THEN true END) STORED,
    parent_marker boolean GENERATED ALWAYS AS (CASE WHEN parent_id IS NOT NULL THEN true END) STORED,
    CONSTRAINT products_pkey PRIMARY KEY (id),
    CONSTRAINT products_id_parent_key UNIQUE (id, parent_id),
    CONSTRAINT products_id_root_key UNIQUE (id, root_marker),
    -- parent_id phải trỏ tới một sản phẩm gốc: variant lồng variant bị chặn ngay ở DB.
    CONSTRAINT fk_products_parent FOREIGN KEY (parent_id, parent_marker) REFERENCES products (id, root_marker),
    CONSTRAINT products_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT products_variant_is_leaf CHECK (parent_id IS NULL OR has_variant = false),
    CONSTRAINT products_variant_key_only_on_variant CHECK (variant_key IS NULL OR parent_id IS NOT NULL),
    CONSTRAINT products_price_currency_check CHECK (price IS NULL OR currency IS NOT NULL),
    CONSTRAINT products_price_non_negative CHECK (price IS NULL OR price >= 0),
    CONSTRAINT products_currency_format CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT products_gtin_format CHECK (gtin IS NULL OR gtin ~ '^[0-9]{8,14}$'),
    CONSTRAINT products_status_check CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);
-- SKU không phân biệt hoa thường và không dùng lại kể cả khi đã xoá mềm (đơn hàng cũ còn tham chiếu).
CREATE UNIQUE INDEX products_sku_key ON products (lower(sku)) WHERE sku IS NOT NULL;
-- Slug và GTIN chỉ unique giữa các sản phẩm chưa xoá: xoá mềm xong thì dùng lại được.
CREATE UNIQUE INDEX products_slug_key ON products (slug) WHERE slug IS NOT NULL AND status <> 'DELETED';
CREATE UNIQUE INDEX products_gtin_key ON products (gtin) WHERE gtin IS NOT NULL AND status <> 'DELETED';
CREATE UNIQUE INDEX products_variant_key ON products (parent_id, variant_key)
    WHERE variant_key IS NOT NULL AND status <> 'DELETED';
CREATE INDEX idx_products_parent_id ON products (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_products_brand ON products (brand) WHERE brand IS NOT NULL;
-- Tra cứu ở back-office (tên chứa từ khoá). Storefront tìm kiếm qua search-service.
CREATE INDEX idx_products_name_trgm ON products USING gin (lower(name) public.gin_trgm_ops);

CREATE TABLE product_categories (
    id            bigint      NOT NULL,
    product_id    bigint      NOT NULL,
    category_id   varchar(32) NOT NULL, -- id danh mục ở catalog-service
    display_order integer     NOT NULL DEFAULT 0,
    CONSTRAINT product_categories_pkey PRIMARY KEY (id),
    CONSTRAINT product_categories_product_category_key UNIQUE (product_id, category_id),
    CONSTRAINT fk_product_categories_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);
CREATE INDEX idx_product_categories_category ON product_categories (category_id, product_id);

CREATE TABLE product_images (
    id            bigint        NOT NULL, -- id media do media-service cấp
    product_id    bigint        NOT NULL,
    image_path    varchar(1024) NOT NULL,
    display_order integer       NOT NULL DEFAULT 0,
    is_default    boolean       NOT NULL DEFAULT false,
    CONSTRAINT product_images_pkey PRIMARY KEY (id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);
CREATE INDEX idx_product_images_product_id ON product_images (product_id, display_order);
CREATE UNIQUE INDEX product_images_one_default ON product_images (product_id) WHERE is_default;

CREATE TABLE product_attribute (
    id            bigint       NOT NULL,
    code          varchar(64),  -- khoá ổn định dùng trong URL lọc và chỉ mục tìm kiếm ("ram", "made_in")
    name          varchar(255) NOT NULL,
    data_type     varchar(16)  NOT NULL DEFAULT 'TEXT',
    unit          varchar(16),  -- đơn vị cho NUMBER (GB, g, cm)
    is_filterable boolean      NOT NULL DEFAULT false, -- thành facet/bộ lọc ở storefront
    is_searchable boolean      NOT NULL DEFAULT false, -- đưa giá trị vào văn bản tìm kiếm
    display_order integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL,
    created_by    varchar(255) NOT NULL,
    updated_at    timestamptz  NOT NULL,
    updated_by    varchar(255) NOT NULL,
    CONSTRAINT product_attribute_pkey PRIMARY KEY (id),
    CONSTRAINT product_attribute_data_type_check CHECK (data_type IN ('TEXT', 'NUMBER', 'BOOLEAN')),
    CONSTRAINT product_attribute_code_format CHECK (code IS NULL OR code ~ '^[a-z][a-z0-9_]{0,63}$'),
    -- Thuộc tính lọc được phải có code (đó là khoá facet trong search-service).
    CONSTRAINT product_attribute_filterable_needs_code CHECK (is_filterable = false OR code IS NOT NULL)
);
CREATE UNIQUE INDEX product_attribute_code_key ON product_attribute (code) WHERE code IS NOT NULL;
CREATE UNIQUE INDEX product_attribute_name_key ON product_attribute (lower(name));

CREATE TABLE product_attribute_value (
    id                   bigint        NOT NULL,
    product_id           bigint        NOT NULL,
    product_attribute_id bigint        NOT NULL,
    value                varchar(1024) NOT NULL, -- dạng chuẩn để hiển thị/so khớp
    value_number         numeric,                -- có khi thuộc tính kiểu NUMBER: lọc theo khoảng
    CONSTRAINT product_attribute_value_pkey PRIMARY KEY (id),
    CONSTRAINT product_attribute_value_product_attribute_key UNIQUE (product_id, product_attribute_id),
    CONSTRAINT fk_product_attribute_value_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_attribute_value_product_attribute FOREIGN KEY (product_attribute_id)
        REFERENCES product_attribute (id)
);
CREATE INDEX idx_product_attribute_value_attr_value ON product_attribute_value (product_attribute_id, value);
CREATE INDEX idx_product_attribute_value_attr_number ON product_attribute_value (product_attribute_id, value_number)
    WHERE value_number IS NOT NULL;

CREATE TABLE variant_option (
    id   bigint       NOT NULL,
    name varchar(255) NOT NULL,
    CONSTRAINT variant_option_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX variant_option_name_key ON variant_option (lower(name));

CREATE TABLE variant_option_value (
    id                bigint       NOT NULL,
    product_id        bigint       NOT NULL, -- sản phẩm gốc
    variant_option_id bigint       NOT NULL,
    value             varchar(255) NOT NULL,
    display_order     integer      NOT NULL DEFAULT 0,
    CONSTRAINT variant_option_value_pkey PRIMARY KEY (id),
    -- Đích của FK ba cột từ variant_option_combination.
    CONSTRAINT variant_option_value_ref_key UNIQUE (id, variant_option_id, product_id),
    CONSTRAINT fk_variant_option_value_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_variant_option_value_variant_option FOREIGN KEY (variant_option_id) REFERENCES variant_option (id)
);
CREATE UNIQUE INDEX variant_option_value_key ON variant_option_value (product_id, variant_option_id, lower(value));
CREATE INDEX idx_variant_option_value_variant_option_id ON variant_option_value (variant_option_id);

CREATE TABLE variant_option_combination (
    id                      bigint  NOT NULL,
    variant_id              bigint  NOT NULL,
    parent_id               bigint  NOT NULL,
    variant_option_id       bigint  NOT NULL,
    variant_option_value_id bigint  NOT NULL,
    display_order           integer NOT NULL DEFAULT 0,
    CONSTRAINT variant_option_combination_pkey PRIMARY KEY (id),
    -- Mỗi variant chọn đúng một giá trị trên mỗi trục.
    CONSTRAINT variant_option_combination_axis_key UNIQUE (variant_id, variant_option_id),
    -- variant_id phải là variant của parent_id.
    CONSTRAINT fk_variant_option_combination_variant FOREIGN KEY (variant_id, parent_id)
        REFERENCES products (id, parent_id) ON DELETE CASCADE,
    -- Giá trị phải thuộc đúng trục và đúng sản phẩm gốc của variant.
    CONSTRAINT fk_variant_option_combination_value FOREIGN KEY (variant_option_value_id, variant_option_id, parent_id)
        REFERENCES variant_option_value (id, variant_option_id, product_id)
);
CREATE INDEX idx_variant_option_combination_value ON variant_option_combination (variant_option_value_id);
CREATE INDEX idx_variant_option_combination_parent ON variant_option_combination (parent_id);

CREATE TABLE product_view (
    id         bigint GENERATED BY DEFAULT AS IDENTITY,
    product_id bigint       NOT NULL,
    view_by    varchar(255) NOT NULL,
    viewed_at  timestamptz  NOT NULL,
    CONSTRAINT product_view_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_product_view_product ON product_view (product_id, viewed_at);
