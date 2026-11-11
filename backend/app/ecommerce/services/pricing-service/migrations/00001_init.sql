-- +goose Up
-- Bảng giá: "default" là giá công khai; bảng giá theo nhóm khách (customer_group) chỉ áp cho khách có nhóm đó.
-- Khi nhiều bảng giá cùng áp dụng, khách nhận giá thấp nhất (hoà thì ưu tiên priority cao hơn).
CREATE TABLE price_lists (
    id             BIGSERIAL PRIMARY KEY,
    code           TEXT        NOT NULL UNIQUE CHECK (code ~ '^[a-z0-9][a-z0-9_-]{0,62}$'),
    name           TEXT        NOT NULL,
    currency       CHAR(3)     NOT NULL,
    customer_group TEXT        NOT NULL DEFAULT '',
    priority       INT         NOT NULL DEFAULT 0,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    starts_at      TIMESTAMPTZ,
    ends_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

-- Giá theo đơn vị nhỏ nhất của tiền tệ (VND: đồng).
CREATE TABLE prices (
    price_list_id    BIGINT      NOT NULL REFERENCES price_lists (id) ON DELETE CASCADE,
    product_id       TEXT        NOT NULL,
    amount_minor     BIGINT      NOT NULL CHECK (amount_minor >= 0),
    compare_at_minor BIGINT      CHECK (compare_at_minor IS NULL OR compare_at_minor >= amount_minor),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       TEXT        NOT NULL DEFAULT '',
    PRIMARY KEY (price_list_id, product_id)
);
CREATE INDEX prices_product ON prices (product_id);

-- Đợt giảm giá hẹn giờ (SALE) và flash sale (FLASH). Giá đúng theo khung giờ ngay cả khi scheduler chưa chạy;
-- status chỉ để phát event đúng một lần khi đợt bắt đầu/kết thúc.
CREATE TABLE price_schedules (
    id            UUID PRIMARY KEY,
    price_list_id BIGINT      NOT NULL REFERENCES price_lists (id) ON DELETE CASCADE,
    product_id    TEXT        NOT NULL,
    kind          TEXT        NOT NULL CHECK (kind IN ('SALE', 'FLASH')),
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor >= 0),
    starts_at     TIMESTAMPTZ NOT NULL,
    ends_at       TIMESTAMPTZ,
    status        TEXT        NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'ACTIVE', 'ENDED', 'CANCELLED')),
    reason        TEXT        NOT NULL DEFAULT '',
    created_by    TEXT        NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX price_schedules_product ON price_schedules (product_id, starts_at) WHERE status <> 'CANCELLED';
CREATE INDEX price_schedules_due ON price_schedules (status, starts_at, ends_at) WHERE status IN ('SCHEDULED', 'ACTIVE');

-- Lịch sử giá hiệu lực, dùng cho "giá thấp nhất 30 ngày" và tra soát.
CREATE TABLE price_history (
    id            BIGSERIAL PRIMARY KEY,
    price_list_id BIGINT      NOT NULL REFERENCES price_lists (id) ON DELETE CASCADE,
    product_id    TEXT        NOT NULL,
    old_minor     BIGINT,
    new_minor     BIGINT      NOT NULL,
    reason        TEXT        NOT NULL,
    changed_by    TEXT        NOT NULL DEFAULT '',
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX price_history_product ON price_history (product_id, changed_at DESC);

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

INSERT INTO price_lists (code, name, currency, priority) VALUES ('default', 'Giá bán lẻ', 'VND', 0);

-- +goose Down
DROP TABLE processed_events;
DROP TABLE outbox;
DROP TABLE price_history;
DROP TABLE price_schedules;
DROP TABLE prices;
DROP TABLE price_lists;
