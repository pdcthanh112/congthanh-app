-- +goose Up
CREATE TABLE carts (
    id          BIGINT PRIMARY KEY,
    name        TEXT        NOT NULL,
    customer_id TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | INACTIVE
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX carts_customer_idx ON carts (customer_id);
-- Mỗi khách chỉ có tối đa một giỏ mặc định đang hoạt động.
CREATE UNIQUE INDEX carts_one_default_per_customer ON carts (customer_id) WHERE is_default AND status = 'ACTIVE';

CREATE TABLE cart_items (
    id         BIGINT PRIMARY KEY,
    cart_id    BIGINT      NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id TEXT        NOT NULL,
    quantity   INTEGER     NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cart_items_cart_product_uq UNIQUE (cart_id, product_id)
);

-- +goose Down
DROP TABLE cart_items;
DROP TABLE carts;
