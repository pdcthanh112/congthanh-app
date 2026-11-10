-- +goose Up
-- Gộp warehouse-service vào inventory-service: kho và tồn kho nằm chung một chỗ,
-- nên giữ chỗ/trừ kho chạy được trong một transaction.
CREATE TABLE warehouses (
    id          TEXT PRIMARY KEY,
    name        TEXT        NOT NULL,
    location    TEXT        NOT NULL DEFAULT '',
    address     TEXT        NOT NULL DEFAULT '',
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO warehouses (id, name) VALUES ('WH-DEFAULT', 'Kho mặc định');

-- Tồn kho theo (sản phẩm, kho). available = quantity - reserved.
CREATE TABLE stocks (
    id               BIGINT PRIMARY KEY,
    product_id       TEXT        NOT NULL,
    sku              TEXT        NOT NULL DEFAULT '',
    warehouse_id     TEXT        NOT NULL REFERENCES warehouses (id),
    quantity         INTEGER     NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reserved         INTEGER     NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    in_transit       INTEGER     NOT NULL DEFAULT 0 CHECK (in_transit >= 0),
    reorder_point    INTEGER     NOT NULL DEFAULT 0,
    reorder_quantity INTEGER     NOT NULL DEFAULT 0,
    discontinued     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT stocks_reserved_le_quantity CHECK (reserved <= quantity),
    CONSTRAINT stocks_product_warehouse_uq UNIQUE (product_id, warehouse_id)
);
CREATE INDEX stocks_sku_idx ON stocks (sku);

CREATE TABLE reservations (
    id          TEXT PRIMARY KEY,           -- saga id / order id do bên gọi cấp
    customer_id TEXT        NOT NULL DEFAULT '',
    order_id    TEXT        NOT NULL DEFAULT '',
    type        TEXT        NOT NULL,       -- ORDER | CART | QUOTE
    status      TEXT        NOT NULL,       -- RESERVED | RELEASED | EXPIRED | COMMITTED
    reason      TEXT        NOT NULL DEFAULT '',
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX reservations_expiry_idx ON reservations (expires_at) WHERE status = 'RESERVED';

CREATE TABLE reservation_items (
    reservation_id TEXT    NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    stock_id       BIGINT  NOT NULL REFERENCES stocks (id),
    product_id     TEXT    NOT NULL,
    warehouse_id   TEXT    NOT NULL,
    quantity       INTEGER NOT NULL CHECK (quantity > 0),
    status         TEXT    NOT NULL DEFAULT 'RESERVED',
    PRIMARY KEY (reservation_id, stock_id)
);

-- Lịch sử thay đổi tồn kho (thay cho InventoryHistory ở bản Java).
CREATE TABLE movements (
    id           BIGSERIAL PRIMARY KEY,
    stock_id     BIGINT      NOT NULL REFERENCES stocks (id) ON DELETE CASCADE,
    product_id   TEXT        NOT NULL,
    warehouse_id TEXT        NOT NULL,
    kind         TEXT        NOT NULL,   -- RESERVE | RELEASE | COMMIT | ADJUST:<type> | SET
    change       INTEGER     NOT NULL,
    reference_id TEXT        NOT NULL DEFAULT '',
    reason       TEXT        NOT NULL DEFAULT '',
    actor        TEXT        NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX movements_product_idx ON movements (product_id, created_at DESC);

-- +goose Down
DROP TABLE movements;
DROP TABLE reservation_items;
DROP TABLE reservations;
DROP TABLE stocks;
DROP TABLE warehouses;
