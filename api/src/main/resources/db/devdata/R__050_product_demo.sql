-- Development only. Add missing 3C demo catalog rows; never reset operator changes.
-- Guards run before any business writes. Error column names identify the missing dependency/conflict.
DROP TEMPORARY TABLE IF EXISTS dev_seed_product_guard;
CREATE TEMPORARY TABLE dev_seed_product_guard (
  dev_seed_missing_M_3C_DEMO INT NOT NULL,
  dev_seed_missing_M_ISOLATION_TEST INT NOT NULL,
  dev_seed_category_name_conflict INT NOT NULL,
  dev_seed_product_code_conflict INT NOT NULL,
  dev_seed_sku_code_conflict INT NOT NULL
);
INSERT INTO dev_seed_product_guard
SELECT
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_3C_DEMO') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (
    SELECT 1 FROM product_category c
    JOIN (
      SELECT 'M_3C_DEMO' AS code, '充电器' AS name UNION ALL
      SELECT 'M_3C_DEMO', '数据线' UNION ALL
      SELECT 'M_3C_DEMO', '耳机' UNION ALL
      SELECT 'M_3C_DEMO', '车载充电器' UNION ALL
      SELECT 'M_ISOLATION_TEST', '对照分类'
    ) s JOIN merchant m ON m.merchant_code = s.code
    WHERE c.merchant_id = m.id AND c.name = s.name
      AND c.id NOT BETWEEN 2500000000000101 AND 2500000000000105
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM product p
    JOIN (
      SELECT 'M_3C_DEMO' AS code, 'P-CHG-65W' AS product_code UNION ALL
      SELECT 'M_3C_DEMO', 'P-CBL-USBC' UNION ALL
      SELECT 'M_3C_DEMO', 'P-EAR-TWS' UNION ALL
      SELECT 'M_3C_DEMO', 'P-CHG-MAG' UNION ALL
      SELECT 'M_3C_DEMO', 'P-CHG-CAR' UNION ALL
      SELECT 'M_ISOLATION_TEST', 'P-ISO-001'
    ) s JOIN merchant m ON m.merchant_code = s.code
    WHERE p.merchant_id = m.id AND p.product_code = s.product_code
      AND p.id NOT BETWEEN 2500000000000201 AND 2500000000000206
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM product_sku k
    JOIN (
      SELECT 'M_3C_DEMO' AS code, 'SKU-CHG-65W-CN' AS sku_code UNION ALL
      SELECT 'M_3C_DEMO', 'SKU-CHG-65W-EU' UNION ALL
      SELECT 'M_3C_DEMO', 'SKU-CHG-65W-US' UNION ALL
      SELECT 'M_3C_DEMO', 'SKU-CBL-USBC-1M-BK' UNION ALL
      SELECT 'M_3C_DEMO', 'SKU-CBL-USBC-1M-WH' UNION ALL
      SELECT 'M_3C_DEMO', 'SKU-CBL-USBC-2M-BK' UNION ALL
      SELECT 'M_3C_DEMO', 'SKU-EAR-TWS-BK' UNION ALL
      SELECT 'M_3C_DEMO', 'SKU-EAR-TWS-WH' UNION ALL
      SELECT 'M_ISOLATION_TEST', 'SKU-ISO-001'
    ) s JOIN merchant m ON m.merchant_code = s.code
    WHERE k.merchant_id = m.id AND k.sku_code = s.sku_code
      AND k.id NOT BETWEEN 2500000000000301 AND 2500000000000309
  ) THEN NULL ELSE 1 END;

-- Categories: three 3C roots plus one second-level demo child, and one isolation tenant root.
INSERT INTO product_category (id, merchant_id, parent_id, name, sort_no, status, created_at, updated_at)
SELECT s.id, m.id, s.parent_id, s.name, s.sort_no, s.status, '2026-11-01 00:00:00.000', '2026-11-01 00:00:00.000'
FROM (
  SELECT 2500000000000101 AS id, NULL AS parent_id, '充电器' AS name, 10 AS sort_no, 'ACTIVE' AS status
  UNION ALL SELECT 2500000000000102, NULL, '数据线', 20, 'ACTIVE'
  UNION ALL SELECT 2500000000000103, NULL, '耳机', 30, 'ACTIVE'
  UNION ALL SELECT 2500000000000104, 2500000000000101, '车载充电器', 10, 'ACTIVE'
  UNION ALL SELECT 2500000000000105, NULL, '对照分类', 10, 'ACTIVE'
) s
JOIN merchant m ON (s.id BETWEEN 2500000000000101 AND 2500000000000104 AND m.merchant_code = 'M_3C_DEMO')
              OR (s.id = 2500000000000105 AND m.merchant_code = 'M_ISOLATION_TEST')
WHERE NOT EXISTS (SELECT 1 FROM product_category c WHERE c.id = s.id);

-- Products: published 3C demos, one unpublished, one draft, and one isolation-tenant product.
INSERT INTO product (id, merchant_id, category_id, product_code, name, brand, description, status, created_at, updated_at)
SELECT s.id, m.id, s.category_id, s.product_code, s.name, s.brand, s.description, s.status,
       '2026-11-01 00:00:00.000', '2026-11-01 00:00:00.000'
FROM (
  SELECT 2500000000000201 AS id, 2500000000000101 AS category_id, 'P-CHG-65W' AS product_code,
         '65W GaN 氮化镓充电器' AS name, 'GbaPower' AS brand, '65W 双口氮化镓快充充电器' AS description,
         'PUBLISHED' AS status
  UNION ALL SELECT 2500000000000202, 2500000000000102, 'P-CBL-USBC', 'USB-C 数据线', 'GbaPower', '编织尼龙 USB-C 转 USB-C 数据线', 'PUBLISHED'
  UNION ALL SELECT 2500000000000203, 2500000000000103, 'P-EAR-TWS', 'TWS 降噪蓝牙耳机', 'GbaSound', '主动降噪真无线蓝牙耳机', 'PUBLISHED'
  UNION ALL SELECT 2500000000000204, 2500000000000101, 'P-CHG-MAG', '磁吸无线充电器', 'GbaPower', '磁吸对齐无线快充（未发布）', 'UNPUBLISHED'
  UNION ALL SELECT 2500000000000205, 2500000000000104, 'P-CHG-CAR', '车载快充充电器', 'GbaPower', '车充样例（草稿）', 'DRAFT'
  UNION ALL SELECT 2500000000000206, 2500000000000105, 'P-ISO-001', '对照租户商品', 'IsoBrand', '隔离验证对照商品', 'PUBLISHED'
) s
JOIN merchant m ON (s.id BETWEEN 2500000000000201 AND 2500000000000205 AND m.merchant_code = 'M_3C_DEMO')
              OR (s.id = 2500000000000206 AND m.merchant_code = 'M_ISOLATION_TEST')
WHERE NOT EXISTS (SELECT 1 FROM product p WHERE p.id = s.id);

-- SKUs: charger combines power+plug, cable length+color, earbuds color; includes one discontinued demo.
INSERT INTO product_sku (id, merchant_id, product_id, sku_code, title, price, attributes_json, status, created_at, updated_at)
SELECT s.id, m.id, s.product_id, s.sku_code, s.title, s.price, s.attributes_json, s.status,
       '2026-11-01 00:00:00.000', '2026-11-01 00:00:00.000'
FROM (
  SELECT 2500000000000301 AS id, 2500000000000201 AS product_id, 'SKU-CHG-65W-CN' AS sku_code,
         '65W 充电器 国标插头' AS title, 99.00 AS price,
         '{"功率":"65W","插头":"国标"}' AS attributes_json, 'ACTIVE' AS status
  UNION ALL SELECT 2500000000000302, 2500000000000201, 'SKU-CHG-65W-EU', '65W 充电器 欧标插头', 99.00,
         '{"功率":"65W","插头":"欧标"}', 'ACTIVE'
  UNION ALL SELECT 2500000000000303, 2500000000000201, 'SKU-CHG-65W-US', '65W 充电器 美标插头', 89.00,
         '{"功率":"65W","插头":"美标"}', 'DISCONTINUED'
  UNION ALL SELECT 2500000000000304, 2500000000000202, 'SKU-CBL-USBC-1M-BK', '数据线 1米 黑色', 19.90,
         '{"长度":"1米","颜色":"黑色"}', 'ACTIVE'
  UNION ALL SELECT 2500000000000305, 2500000000000202, 'SKU-CBL-USBC-1M-WH', '数据线 1米 白色', 19.90,
         '{"长度":"1米","颜色":"白色"}', 'ACTIVE'
  UNION ALL SELECT 2500000000000306, 2500000000000202, 'SKU-CBL-USBC-2M-BK', '数据线 2米 黑色', 29.90,
         '{"长度":"2米","颜色":"黑色"}', 'ACTIVE'
  UNION ALL SELECT 2500000000000307, 2500000000000203, 'SKU-EAR-TWS-BK', 'TWS 耳机 黑色', 199.00,
         '{"颜色":"黑色"}', 'ACTIVE'
  UNION ALL SELECT 2500000000000308, 2500000000000203, 'SKU-EAR-TWS-WH', 'TWS 耳机 白色', 199.00,
         '{"颜色":"白色"}', 'ACTIVE'
  UNION ALL SELECT 2500000000000309, 2500000000000206, 'SKU-ISO-001', '对照租户 SKU', 199.00,
         '{"规格":"标准"}', 'ACTIVE'
) s
JOIN merchant m ON (s.id BETWEEN 2500000000000301 AND 2500000000000308 AND m.merchant_code = 'M_3C_DEMO')
              OR (s.id = 2500000000000309 AND m.merchant_code = 'M_ISOLATION_TEST')
WHERE NOT EXISTS (SELECT 1 FROM product_sku k WHERE k.id = s.id);

-- Structured, filterable compatibility attributes; one row per (product, attr_name).
INSERT INTO product_attribute (id, merchant_id, product_id, attr_name, attr_value, searchable, created_at, updated_at)
SELECT s.id, m.id, s.product_id, s.attr_name, s.attr_value, s.searchable,
       '2026-11-01 00:00:00.000', '2026-11-01 00:00:00.000'
FROM (
  SELECT 2500000000000401 AS id, 2500000000000201 AS product_id, '兼容机型' AS attr_name,
         'iPhone 15;小米 14;华为 Mate 60' AS attr_value, 1 AS searchable
  UNION ALL SELECT 2500000000000402, 2500000000000201, '接口类型', 'USB-C', 1
  UNION ALL SELECT 2500000000000403, 2500000000000201, '快充协议', 'PD3.0;QC4+;PPS', 1
  UNION ALL SELECT 2500000000000404, 2500000000000201, '额定功率', '65W', 1
  UNION ALL SELECT 2500000000000405, 2500000000000202, '接口类型', 'USB-C/USB-C', 1
  UNION ALL SELECT 2500000000000406, 2500000000000202, '传输速率', '480Mbps', 1
  UNION ALL SELECT 2500000000000407, 2500000000000203, '降噪类型', '主动降噪', 1
  UNION ALL SELECT 2500000000000408, 2500000000000203, '蓝牙版本', '5.3', 1
) s
JOIN merchant m ON m.id = (SELECT id FROM merchant WHERE merchant_code = 'M_3C_DEMO')
WHERE s.product_id BETWEEN 2500000000000201 AND 2500000000000203
  AND NOT EXISTS (SELECT 1 FROM product_attribute a WHERE a.id = s.id);

DROP TEMPORARY TABLE dev_seed_product_guard;
