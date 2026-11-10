-- +goose Up
-- Bảng giá: dòng có region_code ghi đè dòng chỉ có quốc gia (cùng carrier + service_code).
CREATE TABLE shipping_rates (
    id               BIGSERIAL PRIMARY KEY,
    carrier          TEXT    NOT NULL,
    service_code     TEXT    NOT NULL,
    service_name     TEXT    NOT NULL,
    country_code     CHAR(2) NOT NULL,
    region_code      TEXT    NOT NULL DEFAULT '',
    currency         CHAR(3) NOT NULL,
    base_fee_minor   BIGINT  NOT NULL CHECK (base_fee_minor >= 0),      -- gồm kg đầu tiên
    per_kg_fee_minor BIGINT  NOT NULL CHECK (per_kg_fee_minor >= 0),    -- mỗi kg tiếp theo (làm tròn lên)
    free_over_minor  BIGINT,                                            -- miễn phí khi giá trị đơn >= mức này
    days_min         INT     NOT NULL,
    days_max         INT     NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (carrier, service_code, country_code, region_code)
);

CREATE TABLE shipments (
    id              TEXT PRIMARY KEY,
    order_id        TEXT        NOT NULL UNIQUE,
    customer_id     TEXT        NOT NULL,
    carrier         TEXT        NOT NULL DEFAULT '',
    service_code    TEXT        NOT NULL DEFAULT '',
    tracking_number TEXT UNIQUE,
    status          TEXT        NOT NULL,
    address         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX shipments_customer ON shipments (customer_id, created_at DESC);

-- Lịch sử hành trình (hiển thị cho khách khi tra cứu vận đơn).
CREATE TABLE shipment_events (
    id          BIGSERIAL PRIMARY KEY,
    shipment_id TEXT        NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
    status      TEXT        NOT NULL,
    note        TEXT        NOT NULL DEFAULT '',
    location    TEXT        NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX shipment_events_shipment ON shipment_events (shipment_id, occurred_at);

CREATE TABLE outbox (
    id            UUID PRIMARY KEY,
    aggregatetype TEXT        NOT NULL,
    aggregateid   TEXT        NOT NULL,
    type          TEXT        NOT NULL,
    payload       BYTEA       NOT NULL,
    traceparent   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    event_id     TEXT PRIMARY KEY,
    consumer     TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Giá mặc định nội địa (VND): giao tiêu chuẩn và nhanh, miễn phí tiêu chuẩn cho đơn từ 500.000đ.
INSERT INTO shipping_rates (carrier, service_code, service_name, country_code, region_code, currency, base_fee_minor, per_kg_fee_minor, free_over_minor, days_min, days_max) VALUES
  ('internal', 'standard', 'Giao tiêu chuẩn', 'VN', '',    'VND', 30000, 5000, 500000, 3, 5),
  ('internal', 'standard', 'Giao tiêu chuẩn', 'VN', 'HCM', 'VND', 20000, 4000, 500000, 1, 2),
  ('internal', 'standard', 'Giao tiêu chuẩn', 'VN', 'HN',  'VND', 20000, 4000, 500000, 1, 2),
  ('internal', 'express',  'Giao nhanh',      'VN', '',    'VND', 50000, 8000, NULL,   1, 2);

-- +goose Down
DROP TABLE processed_events;
DROP TABLE outbox;
DROP TABLE shipment_events;
DROP TABLE shipments;
DROP TABLE shipping_rates;
