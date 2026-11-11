-- +goose Up
-- ADR 0015: thị trường chính là Mỹ, bảng giá mặc định tính bằng USD (đơn vị nhỏ nhất: cent).
UPDATE price_lists SET currency = 'USD', name = 'Retail' WHERE code = 'default';

-- +goose Down
UPDATE price_lists SET currency = 'VND', name = 'Giá bán lẻ' WHERE code = 'default';
