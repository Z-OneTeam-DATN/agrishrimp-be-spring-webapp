-- =============================================================================
-- SEED: Sản phẩm phục vụ Knowledge Base AI chẩn đoán bệnh tôm
-- Mapping với protocol_map.py — drug_categories_indicated
-- Chạy lần lượt từ trên xuống (categories → brands → products → variants)
-- =============================================================================

-- Tắt kiểm tra FK tạm thời để insert an toàn
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- BƯỚC 1: CATEGORY
-- Tên category PHẢI chứa keyword khớp với MiniAppDiseaseProductMapping.java:
--   kháng sinh | vi sinh | men vi sinh | gan | đường ruột
--   vitamin    | khoáng  | miễn dịch   | hóa chất | diệt khuẩn | tăng trưởng
-- =============================================================================

-- Parent category
INSERT INTO category (name, image_url, status, parent_id) VALUES
('Thuốc & Chế phẩm thủy sản', NULL, 'ACTIVE', NULL);

-- Lưu parent_id vào biến (thay số nếu DB đã có dữ liệu trước)
SET @parent_id = LAST_INSERT_ID();

-- Sub-categories (tên lowercase chứa keyword khớp mapping)
INSERT INTO category (name, image_url, status, parent_id) VALUES
('Kháng sinh thủy sản',                  NULL, 'ACTIVE', @parent_id),  -- keyword: kháng sinh
('Men vi sinh / Probiotic',              NULL, 'ACTIVE', @parent_id),  -- keyword: vi sinh, men vi sinh
('Hỗ trợ gan & Đường ruột',             NULL, 'ACTIVE', @parent_id),  -- keyword: gan, đường ruột
('Vitamin & Khoáng chất thủy sản',       NULL, 'ACTIVE', @parent_id),  -- keyword: vitamin, khoáng
('Tăng cường miễn dịch tôm',            NULL, 'ACTIVE', @parent_id),  -- keyword: miễn dịch
('Hóa chất & Diệt khuẩn ao nuôi',       NULL, 'ACTIVE', @parent_id),  -- keyword: hóa chất, diệt khuẩn
('Vôi & Khoáng xử lý ao',              NULL, 'ACTIVE', @parent_id),  -- keyword: khoáng, diệt khuẩn
('Sản phẩm hỗ trợ tăng trưởng',         NULL, 'ACTIVE', @parent_id);  -- keyword: tăng trưởng

-- Lưu category_id từng nhóm (SELECT thay vì dùng biến để tránh thứ tự không chắc)
-- Dùng trong phần INSERT products bên dưới

-- =============================================================================
-- BƯỚC 2: BRAND
-- =============================================================================

INSERT INTO brands (name, logo_url, status) VALUES
('VINA AQUA',        NULL, 'ACTIVE'),
('BIO PHARMA',       NULL, 'ACTIVE'),
('AQUA PRO',         NULL, 'ACTIVE'),
('CLEAN POND',       NULL, 'ACTIVE'),
('VIETNAM AQUATIC',  NULL, 'ACTIVE');

-- =============================================================================
-- BƯỚC 3: PRODUCTS + VARIANTS
-- category_id lấy theo tên để không phụ thuộc vào ID sinh ra
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- NHÓM A: KHÁNG SINH
-- Dùng cho: BACTERIAL_GROUP (Florfenicol, Oxytetracycline), BG (Oxytetracycline, Doxycycline)
-- ─────────────────────────────────────────────────────────────────────────────

-- A1. Florfenicol 10%
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Florfenicol 10% Bột trộn thức ăn',
    'florfenicol-10-bot-tron-thuc-an',
    'Kháng sinh phổ rộng điều trị Vibriosis, AHPND ở tôm. Ưu tiên hơn Oxytetracycline do nhiều chủng Vibrio đã kháng.',
    'Hoạt chất: Florfenicol 100g/kg. Liều dùng: 10–15 mg/kg tôm/ngày, trộn thức ăn, liệu trình 5–7 ngày. Chỉ định: Vibriosis, AHPND, Phân trắng do vi khuẩn. Lưu ý: Thực hiện kháng sinh đồ trước khi dùng. Ngừng thuốc đúng liệu trình, không kéo dài quá 7 ngày.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'FLO-10',
    (SELECT id FROM brands WHERE name = 'BIO PHARMA' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Kháng sinh thủy sản' LIMIT 1)
);
SET @p_flo = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('FLO-10-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_flo),
       ('FLO-10-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_flo),
       ('FLO-10-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_flo);

-- A2. Oxytetracycline HCl 500mg
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Oxytetracycline HCl Bột trộn thức ăn',
    'oxytetracycline-hcl-bot-tron-thuc-an',
    'Kháng sinh nhóm Tetracycline điều trị bệnh mang (BG) do vi khuẩn. Lưu ý: nhiều chủng Vibrio đã kháng, ưu tiên dùng kháng sinh đồ.',
    'Hoạt chất: Oxytetracycline HCl. Liều dùng: 50–75 mg/kg tôm/ngày, trộn thức ăn, 5–7 ngày liên tục. Chỉ định: Bệnh mang vi khuẩn (BG), Vibriosis khi có chỉ định. Nhóm Tetracycline thấm qua mô mang, hiệu quả với Flexibacter và Vibrio spp.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'OXY-TC',
    (SELECT id FROM brands WHERE name = 'BIO PHARMA' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Kháng sinh thủy sản' LIMIT 1)
);
SET @p_oxy = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('OXY-TC-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_oxy),
       ('OXY-TC-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_oxy),
       ('OXY-TC-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_oxy);

-- A3. Doxycycline 100mg
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Doxycycline Hyclate Bột trộn thức ăn',
    'doxycycline-hyclate-bot-tron-thuc-an',
    'Kháng sinh Tetracycline thế hệ 2 điều trị bệnh mang (BG). Dùng khi có kháng sinh đồ chỉ định.',
    'Hoạt chất: Doxycycline hyclate. Liều dùng: 20–30 mg/kg tôm/ngày, theo kháng sinh đồ. Chỉ định: Bệnh mang vi khuẩn (BG) khi Oxytetracycline kém hiệu quả. Không tự ý tăng liều.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'DOXY-100',
    (SELECT id FROM brands WHERE name = 'BIO PHARMA' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Kháng sinh thủy sản' LIMIT 1)
);
SET @p_doxy = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('DOXY-100-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_doxy),
       ('DOXY-100-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_doxy);

-- ─────────────────────────────────────────────────────────────────────────────
-- NHÓM B: MEN VI SINH / PROBIOTIC
-- Dùng cho: WSSV, BACTERIAL_GROUP, BG, WSSV_BG
-- ─────────────────────────────────────────────────────────────────────────────

-- B1. Probiotic Bacillus spp. đa chủng
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Probiotic Bacillus spp. Đa chủng',
    'probiotic-bacillus-spp-da-chung',
    'Men vi sinh Bacillus spp. dùng xuống ao duy trì vi sinh có lợi, ức chế Vibrio cơ hội. Phù hợp mọi loại bệnh.',
    'Thành phần: Bacillus spp. ≥ 1×10⁹ CFU/g. Liều dùng: 1–2 ppm/ngày xuống ao. Phòng ngừa duy trì: 1 ppm, 3 lần/tuần. Công dụng: ức chế cạnh tranh Vibrio, phân hủy hữu cơ đáy ao. Dùng được cho tất cả loại bệnh vi khuẩn và bệnh virus (hỗ trợ).',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'PRO-BAC-SPP',
    (SELECT id FROM brands WHERE name = 'VINA AQUA' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Men vi sinh / Probiotic' LIMIT 1)
);
SET @p_bac = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('PRO-BAC-SPP-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_bac),
       ('PRO-BAC-SPP-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_bac),
       ('PRO-BAC-SPP-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_bac);

-- B2. Probiotic Bacillus subtilis + B. licheniformis (chuyên Vibriosis/AHPND)
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Probiotic Bacillus subtilis + B. licheniformis',
    'probiotic-bacillus-subtilis-licheniformis',
    'Hỗn hợp 2 chủng Bacillus chuyên phân hủy hữu cơ đáy ao và ức chế Vibrio gây AHPND. Dùng giai đoạn đầu xử lý môi trường.',
    'Thành phần: Bacillus subtilis + B. licheniformis ≥ 2×10⁹ CFU/g. Liều dùng: 1–2 ppm xuống ao ở giai đoạn xử lý môi trường (ngày 1–3). Tiết bacteriocin ức chế Vibrio parahaemolyticus gây AHPND/EMS.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'PRO-BS-BL',
    (SELECT id FROM brands WHERE name = 'AQUA PRO' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Men vi sinh / Probiotic' LIMIT 1)
);
SET @p_bsbl = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('PRO-BS-BL-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_bsbl),
       ('PRO-BS-BL-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_bsbl),
       ('PRO-BS-BL-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_bsbl);

-- B3. Probiotic Bacillus subtilis + Lactobacillus spp. (chuyên WFS/EHP)
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Probiotic Bacillus subtilis + Lactobacillus',
    'probiotic-bacillus-subtilis-lactobacillus',
    'Men vi sinh đường ruột kết hợp 2 chủng, cạnh tranh loại bỏ Vibrio thứ phát trong WFS/EHP. Ổn định hệ vi sinh đường ruột tôm.',
    'Thành phần: Bacillus subtilis + Lactobacillus spp. ≥ 2×10⁹ CFU/g. Liều dùng: 2 ppm/ngày xuống ao (giai đoạn hỗ trợ gan tụy). Duy trì: 1 ppm, 3 lần/tuần. Chỉ định: Hội chứng phân trắng (WFS), EHP bội nhiễm Vibrio.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'PRO-BS-LC',
    (SELECT id FROM brands WHERE name = 'AQUA PRO' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Men vi sinh / Probiotic' LIMIT 1)
);
SET @p_bslc = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('PRO-BS-LC-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_bslc),
       ('PRO-BS-LC-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_bslc),
       ('PRO-BS-LC-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_bslc);

-- ─────────────────────────────────────────────────────────────────────────────
-- NHÓM C: HỖ TRỢ GAN & ĐƯỜNG RUỘT
-- Dùng cho: BACTERIAL_GROUP, WSSV_BG
-- ─────────────────────────────────────────────────────────────────────────────

-- C1. Sorbitol + Methionine hỗ trợ gan (BACTERIAL_GROUP)
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Sorbitol + Methionine Hỗ trợ gan tụy',
    'sorbitol-methionine-ho-tro-gan-tuy',
    'Bổ sung Sorbitol và Methionine hỗ trợ chức năng gan tụy tôm trong liệu trình kháng sinh điều trị Vibriosis/AHPND.',
    'Thành phần: Sorbitol + L-Methionine. Liều dùng: theo khuyến cáo nhà sản xuất, dùng liên tục trong suốt liệu trình kháng sinh. Công dụng: bảo vệ tế bào gan tụy, giảm tích lũy độc chất, hỗ trợ chức năng giải độc.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'SORB-MET',
    (SELECT id FROM brands WHERE name = 'VIETNAM AQUATIC' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Hỗ trợ gan & Đường ruột' LIMIT 1)
);
SET @p_sm = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('SORB-MET-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_sm),
       ('SORB-MET-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_sm),
       ('SORB-MET-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_sm);

-- C2. Sorbitol + Choline + Methionine (WSSV_BG/WFS)
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Sorbitol + Choline + Methionine Phục hồi gan',
    'sorbitol-choline-methionine-phuc-hoi-gan',
    'Phối hợp 3 hoạt chất bảo vệ và phục hồi gan tụy bị tổn thương do EHP, WFS. Dùng liên tục 10–14 ngày.',
    'Thành phần: Sorbitol + Choline Chloride + L-Methionine. Liều dùng: theo khuyến cáo nhà sản xuất, dùng 10–14 ngày liên tục. Chỉ định: WFS/EHP, tôm gan tụy teo nhỏ, nhợt màu.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'SCM-GAL',
    (SELECT id FROM brands WHERE name = 'VIETNAM AQUATIC' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Hỗ trợ gan & Đường ruột' LIMIT 1)
);
SET @p_scm = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('SCM-GAL-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_scm),
       ('SCM-GAL-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_scm),
       ('SCM-GAL-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_scm);

-- C3. Men tiêu hóa Enzyme tổng hợp (Protease + Amylase) — BACTERIAL_GROUP
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Men tiêu hóa Enzyme (Protease + Amylase)',
    'men-tieu-hoa-enzyme-protease-amylase',
    'Enzyme tiêu hóa hỗ trợ hấp thu và giảm tải chức năng gan trong điều trị Vibriosis/AHPND.',
    'Thành phần: Protease ≥ 50.000 U/g, Amylase ≥ 30.000 U/g. Liều dùng: theo khuyến cáo nhà sản xuất, trộn thức ăn. Công dụng: cải thiện hấp thu dinh dưỡng, giảm tải gan tụy.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'ENZ-PA',
    (SELECT id FROM brands WHERE name = 'AQUA PRO' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Hỗ trợ gan & Đường ruột' LIMIT 1)
);
SET @p_enzpa = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('ENZ-PA-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_enzpa),
       ('ENZ-PA-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_enzpa),
       ('ENZ-PA-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_enzpa);

-- C4. Men tiêu hóa Enzyme tổng hợp (Protease + Lipase + Amylase) — WSSV_BG
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Men tiêu hóa Enzyme (Protease + Lipase + Amylase)',
    'men-tieu-hoa-enzyme-protease-lipase-amylase',
    'Enzyme 3 thành phần hỗ trợ hấp thu toàn diện cho tôm bị WFS/EHP. Dùng 0,3–0,5 g/kg thức ăn.',
    'Thành phần: Protease + Lipase + Amylase. Liều dùng: 0,3–0,5 g/kg thức ăn, trộn đều. Chỉ định: WFS/EHP — EHP phá hủy tế bào biểu mô gan tụy làm giảm hấp thu; enzyme bù đắp chức năng bị suy giảm.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'ENZ-PLA',
    (SELECT id FROM brands WHERE name = 'AQUA PRO' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Hỗ trợ gan & Đường ruột' LIMIT 1)
);
SET @p_enzpla = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('ENZ-PLA-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_enzpla),
       ('ENZ-PLA-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_enzpla),
       ('ENZ-PLA-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_enzpla);

-- ─────────────────────────────────────────────────────────────────────────────
-- NHÓM D: VITAMIN
-- Dùng cho: WSSV, BACTERIAL_GROUP, BG, Yellowhead, WSSV_BG
-- ─────────────────────────────────────────────────────────────────────────────

-- D1. Vitamin C (Ascorbyl phosphate) dạng bền
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Vitamin C Ascorbyl Phosphate Dạng bền',
    'vitamin-c-ascorbyl-phosphate-dang-ben',
    'Vitamin C dạng bền nhiệt (Ascorbyl phosphate) hỗ trợ miễn dịch tôm. Dùng được cho WSSV, BG, Yellowhead.',
    'Hoạt chất: Ascorbyl-2-Polyphosphate (Stay-C). Liều dùng: 5–10 g/kg thức ăn (WSSV/BG); 10 g/kg (Yellowhead hỗ trợ ngắn hạn). Ổn định trong thức ăn viên, không bị oxy hóa khi tiếp xúc nước.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'VIT-C-AP',
    (SELECT id FROM brands WHERE name = 'VINA AQUA' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Vitamin & Khoáng chất thủy sản' LIMIT 1)
);
SET @p_vitc = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('VIT-C-AP-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_vitc),
       ('VIT-C-AP-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_vitc),
       ('VIT-C-AP-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_vitc);

-- D2. Vitamin B Complex + Vitamin E
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Vitamin B Complex + Vitamin E Thủy sản',
    'vitamin-b-complex-vitamin-e-thuy-san',
    'Hỗn hợp Vitamin B complex và Vitamin E hỗ trợ phục hồi tế bào gan tụy trong điều trị WFS/EHP.',
    'Thành phần: Vitamin B1, B2, B3, B5, B6, B12 + Vitamin E. Liều dùng: theo khuyến cáo nhà sản xuất. Chỉ định: WFS/EHP — hỗ trợ phục hồi tế bào biểu mô gan tụy sau tổn thương.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'VIT-BE',
    (SELECT id FROM brands WHERE name = 'VINA AQUA' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Vitamin & Khoáng chất thủy sản' LIMIT 1)
);
SET @p_vitbe = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('VIT-BE-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_vitbe),
       ('VIT-BE-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_vitbe),
       ('VIT-BE-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_vitbe);

-- D3. Khoáng tổng hợp Ca-Mg-K
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Khoáng tổng hợp Ca-Mg-K Hỗ trợ lột xác',
    'khoang-tong-hop-ca-mg-k-ho-tro-lot-xac',
    'Khoáng tổng hợp Canxi, Magiê, Kali hỗ trợ lột xác và tái tạo biểu mô mang sau điều trị kháng sinh.',
    'Thành phần: Ca²⁺, Mg²⁺, K⁺ dạng hòa tan cao. Liều dùng: theo khuyến cáo nhà sản xuất. Chỉ định: BACTERIAL_GROUP (giai đoạn phục hồi), BG (tái tạo biểu mô mang). Bổ sung sau khi ngừng kháng sinh.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'MIN-CMK',
    (SELECT id FROM brands WHERE name = 'VINA AQUA' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Vitamin & Khoáng chất thủy sản' LIMIT 1)
);
SET @p_mincmk = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('MIN-CMK-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_mincmk),
       ('MIN-CMK-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_mincmk),
       ('MIN-CMK-5KG',  NULL, NULL, NULL, '{"khối lượng": "5kg"}',  'ACTIVE', @p_mincmk);

-- ─────────────────────────────────────────────────────────────────────────────
-- NHÓM E: TĂNG CƯỜNG MIỄN DỊCH
-- Dùng cho: WSSV, WSSV_BG
-- ─────────────────────────────────────────────────────────────────────────────

-- E1. Beta-glucan β-1,3/1,6-glucan
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Beta-Glucan β-1,3/1,6 Kích thích miễn dịch tôm',
    'beta-glucan-kich-thich-mien-dich-tom',
    'Beta-glucan β-1,3/1,6 kích thích haemocyte (tế bào diệt tự nhiên) của tôm. Dùng hỗ trợ tôm nhiễm WSSV còn ăn.',
    'Hoạt chất: β-1,3/1,6-glucan từ nấm men Saccharomyces cerevisiae. Liều dùng: 0,1–0,2 g/kg thức ăn, dùng 7–10 ngày liên tục. Cơ chế: kích thích đáp ứng miễn dịch không đặc hiệu — tăng cường hoạt tính haemocyte chống virus. Không điều trị trực tiếp WSSV nhưng giúp tôm tự chống chịu tốt hơn.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'BETA-GLU',
    (SELECT id FROM brands WHERE name = 'VIETNAM AQUATIC' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Tăng cường miễn dịch tôm' LIMIT 1)
);
SET @p_beta = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('BETA-GLU-100G', NULL, NULL, NULL, '{"khối lượng": "100g"}', 'ACTIVE', @p_beta),
       ('BETA-GLU-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_beta),
       ('BETA-GLU-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_beta);

-- ─────────────────────────────────────────────────────────────────────────────
-- NHÓM F: HÓA CHẤT & DIỆT KHUẨN AO NUÔI
-- Dùng cho: BG, WSSV_BG
-- ─────────────────────────────────────────────────────────────────────────────

-- F1. BKC (Benzalkonium Chloride) 50%
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'BKC Benzalkonium Chloride 50% Diệt khuẩn ao',
    'bkc-benzalkonium-chloride-50-diet-khuan-ao',
    'BKC 50% diệt vi khuẩn sợi bám mang (BG). Liều 0,3–0,5 ppm, dùng lúc 8–10 giờ sáng. KHÔNG dùng quá 1 ppm.',
    'Hoạt chất: Benzalkonium Chloride 50%. Liều dùng: pha loãng đạt 0,3–0,5 ppm trong ao, dùng lúc 8–10 giờ sáng, không dùng khi trời nắng gắt. Cơ chế: phá vỡ màng tế bào vi khuẩn Filamentous bacteria và Vibrio spp. Chống chỉ định: KHÔNG dùng liều > 1 ppm — gây tổn thương mang tôm.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'BKC-50',
    (SELECT id FROM brands WHERE name = 'CLEAN POND' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Hóa chất & Diệt khuẩn ao nuôi' LIMIT 1)
);
SET @p_bkc = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('BKC-50-500ML', NULL, NULL, NULL, '{"thể tích": "500ml"}', 'ACTIVE', @p_bkc),
       ('BKC-50-1L',    NULL, NULL, NULL, '{"thể tích": "1L"}',    'ACTIVE', @p_bkc),
       ('BKC-50-5L',    NULL, NULL, NULL, '{"thể tích": "5L"}',    'ACTIVE', @p_bkc);

-- F2. EDTA Disodium dạng bột
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'EDTA Disodium Chelate kim loại nặng ao nuôi',
    'edta-disodium-chelate-kim-loai-nang',
    'EDTA 2–3 ppm chelate kim loại nặng và giảm độc tố Dinoflagellate trong WFS. Tắm tôm hoặc xuống ao.',
    'Hoạt chất: EDTA Disodium (Ethylenediaminetetraacetic acid disodium salt). Liều dùng: 2–3 ppm, tắm tôm 1 giờ hoặc xuống ao trực tiếp. Công dụng: chelate kim loại nặng tích lũy trong ruột, giảm độc tính hợp chất từ tảo Dinoflagellate. Chỉ định chính: WSSV_BG (WFS).',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'EDTA-DS',
    (SELECT id FROM brands WHERE name = 'CLEAN POND' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Hóa chất & Diệt khuẩn ao nuôi' LIMIT 1)
);
SET @p_edta = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('EDTA-DS-500G', NULL, NULL, NULL, '{"khối lượng": "500g"}', 'ACTIVE', @p_edta),
       ('EDTA-DS-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_edta),
       ('EDTA-DS-5KG',  NULL, NULL, NULL, '{"khối lượng": "5kg"}',  'ACTIVE', @p_edta);

-- F3. Zeolite hấp thụ NH₃
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Zeolite Hấp thụ NH₃ Cải thiện chất lượng nước',
    'zeolite-hap-thu-nh3-cai-thien-chat-luong-nuoc',
    'Zeolite tự nhiên hấp thụ NH₃ và khí độc đáy ao, giảm stress tôm trong điều trị Vibriosis/AHPND.',
    'Thành phần: Zeolite tự nhiên (Clinoptilolite) ≥ 80%. Liều dùng: 10–15 kg/1.000 m³ khi NH₃ > 0,1 mg/L. Công dụng: hấp thụ NH₃, NO₂, H₂S, cải thiện chất lượng nước nền trong giai đoạn điều trị kháng sinh. Chỉ định: BACTERIAL_GROUP giai đoạn 1.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'ZEOL-NAT',
    (SELECT id FROM brands WHERE name = 'CLEAN POND' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Hóa chất & Diệt khuẩn ao nuôi' LIMIT 1)
);
SET @p_zeol = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('ZEOL-NAT-5KG',  NULL, NULL, NULL, '{"khối lượng": "5kg"}',  'ACTIVE', @p_zeol),
       ('ZEOL-NAT-10KG', NULL, NULL, NULL, '{"khối lượng": "10kg"}', 'ACTIVE', @p_zeol),
       ('ZEOL-NAT-25KG', NULL, NULL, NULL, '{"khối lượng": "25kg"}', 'ACTIVE', @p_zeol);

-- ─────────────────────────────────────────────────────────────────────────────
-- NHÓM G: VÔI & KHOÁNG XỬ LÝ AO
-- Dùng cho: WSSV, Yellowhead (khử trùng môi trường)
-- ─────────────────────────────────────────────────────────────────────────────

-- G1. Vôi CaO (Calcium Oxide) xử lý tôm chết
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Vôi CaO Calcium Oxide Xử lý ao nuôi',
    'voi-cao-calcium-oxide-xu-ly-ao-nuoi',
    'Vôi CaO nâng pH > 11 bất hoạt virus WSSV/YHV ngoài môi trường. Xử lý tôm chết tại chỗ. Liều 20 kg/1.000 m³.',
    'Thành phần: Calcium Oxide (CaO) ≥ 90%. Liều dùng: 20 kg/1.000 m³ xử lý tôm chết tại chỗ. Cơ chế: nâng pH > 11 bất hoạt hoàn toàn WSSV, YHV ngoài môi trường. Lưu ý: KHÔNG rải trực tiếp khi tôm còn sống trong ao.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'LIME-CAO',
    (SELECT id FROM brands WHERE name = 'CLEAN POND' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Vôi & Khoáng xử lý ao' LIMIT 1)
);
SET @p_cao = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('LIME-CAO-5KG',  NULL, NULL, NULL, '{"khối lượng": "5kg"}',  'ACTIVE', @p_cao),
       ('LIME-CAO-10KG', NULL, NULL, NULL, '{"khối lượng": "10kg"}', 'ACTIVE', @p_cao),
       ('LIME-CAO-25KG', NULL, NULL, NULL, '{"khối lượng": "25kg"}', 'ACTIVE', @p_cao);

-- G2. Vôi nông nghiệp CaCO₃ (Calcium Carbonate) phơi ao
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Vôi Nông nghiệp CaCO₃ Cải tạo đáy ao',
    'voi-nong-nghiep-caco3-cai-tao-day-ao',
    'Vôi nông nghiệp CaCO₃ 1.000 kg/ha bón sau phơi ao diệt virus tồn lưu trong bùn. Dùng cho WSSV và Yellowhead.',
    'Thành phần: Calcium Carbonate (CaCO₃) ≥ 85%. Liều dùng: 1.000–2.000 kg/ha bón đều sau phơi ao 15–30 ngày. Công dụng: nâng pH đáy, tiêu diệt virus và mầm bệnh tồn lưu trong bùn, cải thiện cấu trúc đáy ao cho vụ sau.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'LIME-CC',
    (SELECT id FROM brands WHERE name = 'CLEAN POND' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Vôi & Khoáng xử lý ao' LIMIT 1)
);
SET @p_cc = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('LIME-CC-10KG', NULL, NULL, NULL, '{"khối lượng": "10kg"}', 'ACTIVE', @p_cc),
       ('LIME-CC-25KG', NULL, NULL, NULL, '{"khối lượng": "25kg"}', 'ACTIVE', @p_cc),
       ('LIME-CC-50KG', NULL, NULL, NULL, '{"khối lượng": "50kg"}', 'ACTIVE', @p_cc);

-- G3. Chlorine bột 70% (Ca(OCl)₂) xử lý nước thải / khử trùng
INSERT INTO products (name, slug, short_desc, description, status, created_at, rating_average, review_count, origin, base_sku, brand_id, category_id)
VALUES (
    'Chlorine bột 70% Khử trùng nước thải ao',
    'chlorine-bot-70-khu-trung-nuoc-thai-ao',
    'Chlorine Ca(OCl)₂ 70% xử lý nước thải ao bệnh trước khi xả (30 ppm, 48 giờ). KHÔNG dùng khi tôm còn sống.',
    'Hoạt chất: Calcium Hypochlorite Ca(OCl)₂ 70%. Liều dùng: 30 ppm giữ 48 giờ (nước thải); 100 ppm sát trùng dụng cụ. Cơ chế: oxy hóa mạnh tiêu diệt toàn bộ virus và vi khuẩn trong nước thải. Bắt buộc theo QCVN 02-19:2014 Điều 2.4 trước khi xả nước ra môi trường.',
    'ACTIVE',
    NOW(),
    NULL, 0,
    'Việt Nam',
    'CLO-70',
    (SELECT id FROM brands WHERE name = 'CLEAN POND' LIMIT 1),
    (SELECT id FROM category WHERE name = 'Vôi & Khoáng xử lý ao' LIMIT 1)
);
SET @p_clo = LAST_INSERT_ID();
INSERT INTO product_variants (sku, barcode, image_url, image_public_id, custom_specs, status, product_id)
VALUES ('CLO-70-1KG',  NULL, NULL, NULL, '{"khối lượng": "1kg"}',  'ACTIVE', @p_clo),
       ('CLO-70-5KG',  NULL, NULL, NULL, '{"khối lượng": "5kg"}',  'ACTIVE', @p_clo),
       ('CLO-70-25KG', NULL, NULL, NULL, '{"khối lượng": "25kg"}', 'ACTIVE', @p_clo);

-- =============================================================================
-- Bật lại FK check
-- =============================================================================
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- KIỂM TRA NHANH SAU KHI CHẠY
-- =============================================================================
-- SELECT p.id, p.name, p.slug, c.name AS category, b.name AS brand, p.status
-- FROM products p
-- JOIN category c ON p.category_id = c.id
-- JOIN brands b ON p.brand_id = b.id
-- WHERE p.base_sku IN ('FLO-10','OXY-TC','DOXY-100','PRO-BAC-SPP','PRO-BS-BL','PRO-BS-LC',
--                      'SORB-MET','SCM-GAL','ENZ-PA','ENZ-PLA','VIT-C-AP','VIT-BE',
--                      'MIN-CMK','BETA-GLU','BKC-50','EDTA-DS','ZEOL-NAT',
--                      'LIME-CAO','LIME-CC','CLO-70')
-- ORDER BY c.name, p.name;
