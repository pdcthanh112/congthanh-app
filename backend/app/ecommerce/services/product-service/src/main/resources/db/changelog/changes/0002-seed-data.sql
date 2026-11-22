INSERT INTO product_attribute (id, name, created_at, created_by, updated_at, updated_by) VALUES
    (16556939800219641, 'Manufacture', CURRENT_TIMESTAMP, 'thanh', CURRENT_TIMESTAMP, 'thanh'),
    (16556939800219642, 'Made in', CURRENT_TIMESTAMP, 'thanh', CURRENT_TIMESTAMP, 'thanh'),
    (16556939800219643, 'Material', CURRENT_TIMESTAMP, 'thanh', CURRENT_TIMESTAMP, 'thanh'),
    (16556939800219644, 'Author', CURRENT_TIMESTAMP, 'thanh', CURRENT_TIMESTAMP, 'thanh'),
    (16556939800219645, 'Pages', CURRENT_TIMESTAMP, 'thanh', CURRENT_TIMESTAMP, 'thanh'),
    (16556939800219646, 'ISBN', CURRENT_TIMESTAMP, 'thanh', CURRENT_TIMESTAMP, 'thanh');

INSERT INTO products (id, name, slug, has_variant, sku, gtin, price, parent_id, description, status) VALUES
    (279094089499572, 'Nhà Giả Kim', 'nha-gia-kim', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (369334726608713, 'Iphone 16 Pro Max', 'iphone-16-pro-max', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (2990462819940328, 'Iphone 15 Pro Max', 'iphone-15-pro-max', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (1969035744565312, 'Iphone 14 Pro Max', 'iphone-14-pro-max', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (1523785008147580, 'Iphone 16 Pro', 'iphone-16-pro', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (3375540433374088, 'Iphone 15 Pro', 'iphone-15-pro', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (2362902282504487, 'Iphone 14 Pro', 'iphone-14-pro', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (312214921293860, 'Những Người Khốn Khổ', 'nhung-nguoi-khon-kho', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (3836321763247313, 'Sapiens Lược Sử Loài Người', 'sapiens-luoc-su-loai-nguoi', false, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (2201591933659885, 'Guns, Germs, and Steel: The Fates of Human Societies', 'guns-germs-and-steel', false, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (263230787848070, 'Samsung Galaxy Fold 6', 'samsung-galaxy-fold-6', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (1354711447400425, 'Samsung Galaxy Zflip 6', 'samsung-galaxy-zflip-6', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (3219423096702472, 'Samsung Galaxy S25 Ultra', 'samsung-galaxy-s25-ultra', true, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (124839411726669, 'Patek Philippe Nautilus 5723', 'patek-philippe-nautilus-5723', false, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (1136423099780520, 'Rolex GMT Master II ', 'rolex-gmt-master-ii', false, NULL, NULL, NULL, NULL, 'description', 'ACTIVE'),
    (1233877787362295, 'Richard Mille RM 70-01 Tourbillon', 'richard-mille-rm-70-01', false, NULL, NULL, NULL, NULL, 'description', 'ACTIVE');

INSERT INTO products (id, name, has_variant, parent_id, sku, gtin, price, currency, description, status) VALUES
    (772266622382519, 'Iphone 16 Pro Max Đen 128GB', false, 369334726608713, 'IPHON-COLOR-CAPAC-1432', '1845678901901', 25000000, 'VND', 'description', 'ACTIVE'),
    (4375511598425480, 'Iphone 16 Pro Max Đen 256GB', false, 369334726608713, 'IPHON-COLOR-CAPAC-5432', '1845678901001', 35000000, 'VND', 'description', 'ACTIVE'),
    (3290008034609610, 'Iphone 16 Pro Max Đen 512GB', false, 369334726608713, 'IPHON-COLOR-CAPAC-7652', '1845678901201', 45000000, 'VND', 'description', 'ACTIVE'),
    (4074358702518785, 'Iphone 16 Pro Max Đen 1TB', false, 369334726608713, 'IPHON-COLOR-CAPAC-1245', '1845678901301', 55000000, 'VND', 'description', 'ACTIVE'),
    (3462609080730822, 'Iphone 16 Pro Max Trắng 128GB', false, 369334726608713, 'IPHON-COLOR-CAPAC-6565', '1845678901021', 26000000, 'VND', 'description', 'ACTIVE'),
    (1626969164074855, 'Iphone 16 Pro Max Trắng 256GB', false, 369334726608713, 'IPHON-COLOR-CAPAC-8623', '1845678901401', 36000000, 'VND', 'description', 'ACTIVE'),
    (1785377336959597, 'Iphone 16 Pro Max Trắng 512GB', false, 369334726608713, 'IPHON-COLOR-CAPAC-4184', '1845678901501', 46000000, 'VND', 'description', 'ACTIVE'),
    (3310162956519876, 'Iphone 16 Pro Max Trắng 1TB', false, 369334726608713, 'IPHON-COLOR-CAPAC-1457', '1845678901601', 56000000, 'VND', 'description', 'ACTIVE'),
    (1731618100637396, 'Iphone 16 Pro Max Titan 128GB', false, 369334726608713, 'IPHON-COLOR-CAPAC-6894', '1845678901071', 29000000, 'VND', 'description', 'ACTIVE'),
    (87950754801030, 'Iphone 16 Pro Max Titan 256GB', false, 369334726608713, 'IPHON-COLOR-CAPAC-9867', '1845678901611', 39000000, 'VND', 'description', 'ACTIVE'),
    (2183680520018239, 'Iphone 16 Pro Max Titan 512B', false, 369334726608713, 'IPHON-COLOR-CAPAC-6474', '1845678901614', 49000000, 'VND', 'description', 'ACTIVE'),
    (625333548123667, 'Iphone 16 Pro Max Titan 1TB', false, 369334726608713, 'IPHON-COLOR-CAPAC-8735', '1845678901612', 59000000, 'VND', 'description', 'ACTIVE');

INSERT INTO product_images (id, product_id, image_path, is_default, display_order) VALUES
    (2026111500000001, 279094089499572, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000002, 369334726608713, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000003, 2990462819940328, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000004, 1969035744565312, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000005, 1523785008147580, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000006, 3375540433374088, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000007, 2362902282504487, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000008, 312214921293860, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000009, 3836321763247313, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000010, 2201591933659885, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000011, 263230787848070, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000012, 1354711447400425, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000013, 3219423096702472, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000014, 124839411726669, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000015, 1136423099780520, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000016, 1233877787362295, 'https://www.mrlinhadventure.com/UserFiles/image/Travel-blog/Image-content/Top-Landscapes-in-Vietnam-1.jpg', true, 0),
    (2026111500000017, 772266622382519, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000018, 4375511598425480, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000019, 3290008034609610, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000020, 4074358702518785, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000021, 3462609080730822, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000022, 1626969164074855, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000023, 1785377336959597, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000024, 3310162956519876, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000025, 1731618100637396, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000026, 87950754801030, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000027, 2183680520018239, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0),
    (2026111500000028, 625333548123667, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFTtvUMmUEQhDJHf4wYNhUz3JaUhUjBnznkzsYoc-ik444TcrTrd_dN8ehrlmJwO8e0y8&usqp=CAU', true, 0);

INSERT INTO product_attribute_value (id, product_id, product_attribute_id, value) VALUES
    (486580923245727745, 279094089499572, 16556939800219644, 'Paulo Coelho'),
    (486580923245727746, 279094089499572, 16556939800219645, '225'),
    (486580923245727747, 279094089499572, 16556939800219646, '978-604-53-7204-3'),
    (486580923245727748, 279094089499572, 16556939800219642, 'Potugal');

INSERT INTO variant_option (id, name) VALUES
    (1865809232457270, 'Size'),
    (1865809232457271, 'Color'),
    (1865809232457272, 'Capacity');

INSERT INTO variant_option_value (id, variant_option_id, product_id, value, display_order) VALUES
    (1965809232457270, 1865809232457271, 369334726608713, 'Black', 1),
    (1965809232457271, 1865809232457271, 369334726608713, 'White', 2),
    (1965809232457272, 1865809232457271, 369334726608713, 'Titan', 3),
    (1965809232457273, 1865809232457272, 369334726608713, '128 GB', 1),
    (1965809232457274, 1865809232457272, 369334726608713, '256 GB', 2),
    (1965809232457275, 1865809232457272, 369334726608713, '512 GB', 3),
    (1965809232457276, 1865809232457272, 369334726608713, '1 TB', 4);

-- Tổ hợp variant: dữ liệu giữ nguyên dạng (variant, trục, giá trị); câu SELECT đổi giá trị chữ sang id
-- variant_option_value của sản phẩm gốc (ADR 0030).
INSERT INTO variant_option_combination (id, variant_id, parent_id, variant_option_id, variant_option_value_id, display_order)
SELECT c.id, c.product_id, p.parent_id, c.variant_option_id, v.id, c.display_order
FROM (VALUES
    (556939800219647, 772266622382519, 1865809232457271, 'Black', 1),
    (556939800219648, 772266622382519, 1865809232457272, '128 GB', 2),
    (556939800219649, 4375511598425480, 1865809232457271, 'Black', 1),
    (556939800219650, 4375511598425480, 1865809232457272, '256 GB', 2),
    (556939800219651, 3290008034609610, 1865809232457271, 'Black', 1),
    (556939800219652, 3290008034609610, 1865809232457272, '512 GB', 2),
    (556939800219653, 4074358702518785, 1865809232457271, 'Black', 1),
    (556939800219654, 4074358702518785, 1865809232457272, '1 TB', 2),
    (556939800219655, 3462609080730822, 1865809232457271, 'White', 1),
    (556939800219656, 3462609080730822, 1865809232457272, '128 GB', 2),
    (556939800219657, 1626969164074855, 1865809232457271, 'White', 1),
    (556939800219658, 1626969164074855, 1865809232457272, '256 GB', 2),
    (556939800219659, 1785377336959597, 1865809232457271, 'White', 1),
    (556939800219660, 1785377336959597, 1865809232457272, '512 GB', 2),
    (556939800219661, 3310162956519876, 1865809232457271, 'White', 1),
    (556939800219662, 3310162956519876, 1865809232457272, '1 TB', 2),
    (556939800219663, 1731618100637396, 1865809232457271, 'Titan', 1),
    (556939800219664, 1731618100637396, 1865809232457272, '128 GB', 2),
    (556939800219665, 87950754801030, 1865809232457271, 'Titan', 1),
    (556939800219666, 87950754801030, 1865809232457272, '256 GB', 2),
    (556939800219667, 2183680520018239, 1865809232457271, 'Titan', 1),
    (556939800219668, 2183680520018239, 1865809232457272, '512 GB', 2),
    (556939800219669, 625333548123667, 1865809232457271, 'Titan', 1),
    (556939800219670, 625333548123667, 1865809232457272, '1 TB', 2)
) AS c (id, product_id, variant_option_id, value, display_order)
JOIN products p ON p.id = c.product_id
JOIN variant_option_value v
  ON v.product_id = p.parent_id AND v.variant_option_id = c.variant_option_id AND v.value = c.value;

-- variant_key = id các giá trị đã chọn, sắp tăng dần, nối '-' (giống ProductVariantService).
UPDATE products p
SET variant_key = k.variant_key
FROM (SELECT variant_id, string_agg(variant_option_value_id::text, '-' ORDER BY variant_option_value_id) AS variant_key
      FROM variant_option_combination GROUP BY variant_id) k
WHERE p.id = k.variant_id;

-- Định nghĩa thuộc tính: code, kiểu dữ liệu và cờ lọc (ADR 0030).
UPDATE product_attribute SET code = 'manufacture', is_filterable = true WHERE id = 16556939800219641;
UPDATE product_attribute SET code = 'made_in', is_filterable = true WHERE id = 16556939800219642;
UPDATE product_attribute SET code = 'material', is_filterable = true WHERE id = 16556939800219643;
UPDATE product_attribute SET code = 'author', is_filterable = true, is_searchable = true WHERE id = 16556939800219644;
UPDATE product_attribute SET code = 'pages', data_type = 'NUMBER' WHERE id = 16556939800219645;
UPDATE product_attribute SET code = 'isbn', is_searchable = true WHERE id = 16556939800219646;
UPDATE product_attribute_value SET value_number = 225 WHERE id = 486580923245727746;

INSERT INTO product_categories (id, product_id, category_id, display_order) VALUES
    (1, 369334726608713, '3247917155611621', 1),
    (2, 369334726608713, '2847328361288900', 2),
    (3, 369334726608713, '58198209806551', 3);
