-- +goose Up
-- Sản phẩm của đơn vừa đặt (OrderPlaced), chờ OrderConfirmed mới bỏ khỏi giỏ.
-- Đơn bị huỷ (thanh toán lỗi, hết hàng...) thì giỏ của khách còn nguyên để đặt lại.
CREATE TABLE pending_order_clears (
    order_id    TEXT PRIMARY KEY,
    customer_id TEXT        NOT NULL,
    product_ids TEXT[]      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX pending_order_clears_created_at ON pending_order_clears (created_at);

-- +goose Down
DROP TABLE pending_order_clears;
