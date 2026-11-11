-- +goose Up
-- ADR 0015: thị trường chính là Mỹ. Giá tính bằng cent; kho chính ở New Jersey nên bờ Đông nhanh hơn.
-- Standard miễn phí cho đơn từ $50. Bảng giá VN cũ bỏ đi (shop không giao nội địa Việt Nam nữa).
DELETE FROM shipping_rates WHERE country_code = 'VN' AND carrier = 'internal';
INSERT INTO shipping_rates (carrier, service_code, service_name, country_code, region_code, currency, base_fee_minor, per_kg_fee_minor, free_over_minor, days_min, days_max) VALUES
  ('internal', 'standard', 'Standard Shipping', 'US', '',   'USD', 599,  150, 5000, 3, 5),
  ('internal', 'standard', 'Standard Shipping', 'US', 'NJ', 'USD', 499,  100, 5000, 1, 3),
  ('internal', 'standard', 'Standard Shipping', 'US', 'NY', 'USD', 499,  100, 5000, 1, 3),
  ('internal', 'standard', 'Standard Shipping', 'US', 'AK', 'USD', 1499, 400, NULL, 5, 9),
  ('internal', 'standard', 'Standard Shipping', 'US', 'HI', 'USD', 1499, 400, NULL, 5, 9),
  ('internal', 'express',  'Express Shipping',  'US', '',   'USD', 1499, 250, NULL, 1, 2);

-- +goose Down
DELETE FROM shipping_rates WHERE country_code = 'US' AND carrier = 'internal';
INSERT INTO shipping_rates (carrier, service_code, service_name, country_code, region_code, currency, base_fee_minor, per_kg_fee_minor, free_over_minor, days_min, days_max) VALUES
  ('internal', 'standard', 'Giao tiêu chuẩn', 'VN', '',    'VND', 30000, 5000, 500000, 3, 5),
  ('internal', 'standard', 'Giao tiêu chuẩn', 'VN', 'HCM', 'VND', 20000, 4000, 500000, 1, 2),
  ('internal', 'standard', 'Giao tiêu chuẩn', 'VN', 'HN',  'VND', 20000, 4000, 500000, 1, 2),
  ('internal', 'express',  'Giao nhanh',      'VN', '',    'VND', 50000, 8000, NULL,   1, 2);
