CREATE TABLE stream_trending (
    product_id TEXT PRIMARY KEY,
    score      REAL        NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX stream_trending_recent ON stream_trending (window_end DESC, score DESC);
