-- ADR 0015: bỏ COD. Kiện bị hoàn về vẫn là tín hiệu rủi ro (địa chỉ giả, reshipping) với mọi phương thức.
ALTER TABLE customer_orders RENAME COLUMN cod_refused TO shipment_returned;
UPDATE customer_orders SET payment_method = 'CARD' WHERE payment_method IN ('COD', 'VNPAY');
UPDATE assessments SET payment_method = 'CARD' WHERE payment_method IN ('COD', 'VNPAY');
