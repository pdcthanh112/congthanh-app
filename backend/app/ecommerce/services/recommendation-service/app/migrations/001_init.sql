CREATE TABLE products (
    product_id  TEXT PRIMARY KEY,
    name        TEXT        NOT NULL DEFAULT '',
    category_id TEXT        NOT NULL DEFAULT '',
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    embedded_at TIMESTAMPTZ
);
CREATE INDEX products_to_embed ON products (updated_at) WHERE active;

CREATE TABLE interactions (
    id         BIGSERIAL PRIMARY KEY,
    subject    TEXT        NOT NULL,
    product_id TEXT        NOT NULL,
    kind       TEXT        NOT NULL CHECK (kind IN ('VIEW', 'CART', 'WISHLIST', 'PURCHASE')),
    weight     REAL        NOT NULL,
    at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX interactions_subject ON interactions (subject, at DESC);
CREATE INDEX interactions_recent ON interactions (at DESC, product_id);

CREATE TABLE product_pairs (
    product_a TEXT        NOT NULL,
    product_b TEXT        NOT NULL,
    count     INT         NOT NULL,
    last_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (product_a, product_b)
);
CREATE TABLE product_orders (
    product_id TEXT PRIMARY KEY,
    orders     INT NOT NULL
);

CREATE TABLE processed_events (
    event_id     TEXT PRIMARY KEY,
    consumer     TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
