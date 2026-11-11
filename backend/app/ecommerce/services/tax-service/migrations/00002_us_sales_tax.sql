-- +goose Up
-- ADR 0015: thị trường chính là Mỹ. Bỏ VAT Việt Nam, thay bằng thuế bán hàng cấp bang (state base rate,
-- số liệu công bố 2025). CHƯA gồm thuế quận/thành phố (tổng thực tế có thể tới ~10%): production nên
-- dùng Stripe Tax / Avalara / TaxJar, hoặc nạp thêm dòng theo ZIP qua API quản trị (postal_code ưu tiên nhất).
-- Lớp Reduced (thực phẩm, thuốc) và Exempt mặc định 0% trên cả nước; bang nào đánh thuế thực phẩm thì thêm dòng riêng.
DELETE FROM tax_rates WHERE country_code = 'VN';

INSERT INTO tax_rates (tax_class_id, country_code, rate_percent)
SELECT id, 'US', 0 FROM tax_classes WHERE name IN ('Standard', 'Reduced', 'Exempt');

INSERT INTO tax_rates (tax_class_id, country_code, region_code, rate_percent)
SELECT c.id, 'US', s.code, s.rate
FROM tax_classes c
CROSS JOIN (VALUES
  ('AL', 4.000), ('AK', 0.000), ('AZ', 5.600), ('AR', 6.500), ('CA', 7.250), ('CO', 2.900), ('CT', 6.350),
  ('DE', 0.000), ('DC', 6.000), ('FL', 6.000), ('GA', 4.000), ('HI', 4.000), ('ID', 6.000), ('IL', 6.250),
  ('IN', 7.000), ('IA', 6.000), ('KS', 6.500), ('KY', 6.000), ('LA', 5.000), ('ME', 5.500), ('MD', 6.000),
  ('MA', 6.250), ('MI', 6.000), ('MN', 6.875), ('MS', 7.000), ('MO', 4.225), ('MT', 0.000), ('NE', 5.500),
  ('NV', 6.850), ('NH', 0.000), ('NJ', 6.625), ('NM', 4.875), ('NY', 4.000), ('NC', 4.750), ('ND', 5.000),
  ('OH', 5.750), ('OK', 4.500), ('OR', 0.000), ('PA', 6.000), ('RI', 7.000), ('SC', 6.000), ('SD', 4.200),
  ('TN', 7.000), ('TX', 6.250), ('UT', 6.100), ('VT', 6.000), ('VA', 5.300), ('WA', 6.500), ('WV', 6.000),
  ('WI', 5.000), ('WY', 4.000)
) AS s (code, rate)
WHERE c.name = 'Standard';

-- +goose Down
DELETE FROM tax_rates WHERE country_code = 'US';
INSERT INTO tax_rates (tax_class_id, country_code, rate_percent)
SELECT id, 'VN', CASE name WHEN 'Standard' THEN 10 WHEN 'Reduced' THEN 5 ELSE 0 END FROM tax_classes;
