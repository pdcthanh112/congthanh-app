--liquibase formatted sql

--changeset order-service:0001-orders
-- order-service (schema order_service). Bảng outbox/processed_events/idempotency_keys đến từ db/platform/changelog.yaml.
CREATE TABLE orders (
    id               VARCHAR(36)    PRIMARY KEY,
    order_code       VARCHAR(32)    NOT NULL UNIQUE,
    customer_id      VARCHAR(64)    NOT NULL,
    status           VARCHAR(16)    NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    subtotal         NUMERIC(19, 4) NOT NULL,
    discount         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total            NUMERIC(19, 4) NOT NULL,
    promotion_code   VARCHAR(64),
    payment_method   VARCHAR(32)    NOT NULL,
    payment_token    VARCHAR(255),
    return_url       VARCHAR(2048),
    client_ip        VARCHAR(64),
    reservation_id   VARCHAR(64),
    payment_id       VARCHAR(64),
    cancel_reason    TEXT,
    note             TEXT,
    ship_full_name   VARCHAR(255),
    ship_phone       VARCHAR(32),
    ship_line1       VARCHAR(255),
    ship_line2       VARCHAR(255),
    ship_ward        VARCHAR(128),
    ship_district    VARCHAR(128),
    ship_city        VARCHAR(128),
    ship_country     VARCHAR(64),
    ship_postal_code VARCHAR(32),
    bill_full_name   VARCHAR(255),
    bill_phone       VARCHAR(32),
    bill_line1       VARCHAR(255),
    bill_line2       VARCHAR(255),
    bill_ward        VARCHAR(128),
    bill_district    VARCHAR(128),
    bill_city        VARCHAR(128),
    bill_country     VARCHAR(64),
    bill_postal_code VARCHAR(32),
    placed_at        TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT orders_status_chk CHECK (status IN ('PENDING', 'RESERVED', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT orders_amounts_chk CHECK (subtotal >= 0 AND discount >= 0 AND total = subtotal - discount)
);

CREATE INDEX orders_customer_placed ON orders (customer_id, placed_at DESC);
CREATE INDEX orders_pending ON orders (placed_at) WHERE status = 'PENDING';

CREATE TABLE order_lines (
    id         BIGSERIAL      PRIMARY KEY,
    order_id   VARCHAR(36)    NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id VARCHAR(64)    NOT NULL,
    sku        VARCHAR(128),
    name       VARCHAR(512),
    quantity   INT            NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 4) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX order_lines_order ON order_lines (order_id);
