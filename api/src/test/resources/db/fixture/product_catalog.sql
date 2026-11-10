-- Explicit @Sql fixture only; run inside a rollback test transaction.
-- Two tenants: M_3C_DEMO published/unpublished products, M_ISOLATION_TEST cross-tenant control.
INSERT INTO product_category (id, merchant_id, parent_id, name, sort_no, status, created_at, updated_at)
SELECT 2600000000000101, id, NULL, 'Fixture 分类', 10, 'ACTIVE', '2026-11-08 00:00:00.000', '2026-11-08 00:00:00.000'
FROM merchant WHERE merchant_code = 'M_3C_DEMO'
AND NOT EXISTS (SELECT 1 FROM product_category c WHERE c.id = 2600000000000101);

INSERT INTO product_category (id, merchant_id, parent_id, name, sort_no, status, created_at, updated_at)
SELECT 2600000000000102, id, NULL, 'Fixture 对照分类', 10, 'ACTIVE', '2026-11-08 00:00:00.000', '2026-11-08 00:00:00.000'
FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST'
AND NOT EXISTS (SELECT 1 FROM product_category c WHERE c.id = 2600000000000102);

INSERT INTO product (id, merchant_id, category_id, product_code, name, brand, description, status, created_at, updated_at)
SELECT s.id, m.id, s.category_id, s.product_code, s.name, s.brand, s.description, s.status,
       '2026-11-08 00:00:00.000', s.updated_at
FROM (
  SELECT 2600000000000201 AS id, 2600000000000101 AS category_id, 'FIX-PROD-1' AS product_code,
         'Fixture 已发布商品' AS name, 'FixtureBrand' AS brand, '公开可见样例' AS description,
         'PUBLISHED' AS status, '2026-11-08 00:00:00.000' AS updated_at
  UNION ALL
  SELECT 2600000000000202, 2600000000000101, 'FIX-PROD-2', 'Fixture 未发布商品', 'FixtureBrand', '公开不可见样例',
         'UNPUBLISHED', '2026-11-08 00:01:00.000'
  UNION ALL
  SELECT 2600000000000203, 2600000000000102, 'FIX-PROD-3', 'Fixture 对照商品', 'IsoBrand', '隔离对照样例',
         'PUBLISHED', '2026-11-08 00:00:00.000'
) s
JOIN merchant m ON (s.id IN (2600000000000201, 2600000000000202) AND m.merchant_code = 'M_3C_DEMO')
              OR (s.id = 2600000000000203 AND m.merchant_code = 'M_ISOLATION_TEST')
WHERE NOT EXISTS (SELECT 1 FROM product p WHERE p.id = s.id);

INSERT INTO product_sku (id, merchant_id, product_id, sku_code, title, price, attributes_json, status, created_at, updated_at)
SELECT s.id, m.id, s.product_id, s.sku_code, s.title, s.price, s.attributes_json, s.status,
       '2026-11-08 00:00:00.000', '2026-11-08 00:00:00.000'
FROM (
  SELECT 2600000000000301 AS id, 2600000000000201 AS product_id, 'FIX-SKU-1' AS sku_code,
         'Fixture SKU 一' AS title, 99.00 AS price, '{"规格":"标准"}' AS attributes_json, 'ACTIVE' AS status
  UNION ALL
  SELECT 2600000000000302, 2600000000000202, 'FIX-SKU-2', 'Fixture SKU 二', 59.00, '{"规格":"隐藏"}', 'ACTIVE'
  UNION ALL
  SELECT 2600000000000303, 2600000000000203, 'FIX-SKU-3', 'Fixture 对照 SKU', 129.00, '{"规格":"对照"}', 'ACTIVE'
) s
JOIN merchant m ON (s.id IN (2600000000000301, 2600000000000302) AND m.merchant_code = 'M_3C_DEMO')
              OR (s.id = 2600000000000303 AND m.merchant_code = 'M_ISOLATION_TEST')
WHERE NOT EXISTS (SELECT 1 FROM product_sku k WHERE k.id = s.id);

INSERT INTO product_attribute (id, merchant_id, product_id, attr_name, attr_value, searchable, created_at, updated_at)
SELECT 2600000000000401, m.id, 2600000000000201, '兼容机型', 'iPhone 15;小米 14', 1,
       '2026-11-08 00:00:00.000', '2026-11-08 00:00:00.000'
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO'
AND NOT EXISTS (SELECT 1 FROM product_attribute a WHERE a.id = 2600000000000401);
