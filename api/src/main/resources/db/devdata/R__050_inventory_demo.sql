-- Development only. Seed stock for existing catalog SKUs without replacing operator values.
-- No inventory transaction rows are synthesized by this fixture.
-- Stable fixture IDs only; runtime inserts use the shared Snowflake IdGenerator.
DROP TEMPORARY TABLE IF EXISTS dev_seed_inventory_guard;
CREATE TEMPORARY TABLE dev_seed_inventory_guard (
  dev_seed_missing_M_3C_DEMO INT NOT NULL,
  dev_seed_missing_M_ISOLATION_TEST INT NOT NULL,
  dev_seed_missing_catalog_sku INT NOT NULL,
  dev_seed_inventory_identity_conflict INT NOT NULL
);
INSERT INTO dev_seed_inventory_guard
SELECT
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_3C_DEMO') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (
    SELECT 1 FROM (
      SELECT 2500000000000201 AS product_id, 2500000000000301 AS sku_id, 'M_3C_DEMO' AS code UNION ALL
      SELECT 2500000000000201, 2500000000000302, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000201, 2500000000000303, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000202, 2500000000000304, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000202, 2500000000000305, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000202, 2500000000000306, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000203, 2500000000000307, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000206, 2500000000000309, 'M_ISOLATION_TEST'
    ) expected
    JOIN merchant m ON m.merchant_code = expected.code
    LEFT JOIN product_sku k ON k.id = expected.sku_id AND k.merchant_id = m.id AND k.product_id = expected.product_id
    WHERE k.id IS NULL
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM (
      SELECT 2500000000000501 AS id, 2500000000000301 AS sku_id, 'M_3C_DEMO' AS code UNION ALL
      SELECT 2500000000000502, 2500000000000302, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000503, 2500000000000303, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000504, 2500000000000304, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000505, 2500000000000305, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000506, 2500000000000306, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000507, 2500000000000307, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000509, 2500000000000309, 'M_ISOLATION_TEST'
    ) expected
    JOIN merchant m ON m.merchant_code = expected.code
    JOIN inventory i ON i.id = expected.id OR (i.merchant_id = m.id AND i.sku_id = expected.sku_id)
    WHERE i.id <> expected.id OR i.merchant_id <> m.id OR i.sku_id <> expected.sku_id
  ) THEN NULL ELSE 1 END;

INSERT INTO inventory (id, merchant_id, sku_id, available_qty, reserved_qty, version)
SELECT seed.id, m.id, seed.sku_id, seed.available_qty, seed.reserved_qty, 0
FROM (
  SELECT 2500000000000501 AS id, 2500000000000301 AS sku_id, 'M_3C_DEMO' AS code, 20 AS available_qty, 3 AS reserved_qty UNION ALL
  SELECT 2500000000000502, 2500000000000302, 'M_3C_DEMO', 0, 0 UNION ALL
  SELECT 2500000000000503, 2500000000000303, 'M_3C_DEMO', 5, 1 UNION ALL
  SELECT 2500000000000504, 2500000000000304, 'M_3C_DEMO', 12, 0 UNION ALL
  SELECT 2500000000000505, 2500000000000305, 'M_3C_DEMO', 0, 0 UNION ALL
  SELECT 2500000000000506, 2500000000000306, 'M_3C_DEMO', 0, 2 UNION ALL
  SELECT 2500000000000507, 2500000000000307, 'M_3C_DEMO', 3, 0 UNION ALL
  SELECT 2500000000000509, 2500000000000309, 'M_ISOLATION_TEST', 7, 1
) seed
JOIN merchant m ON m.merchant_code = seed.code
WHERE NOT EXISTS (
  SELECT 1 FROM inventory i WHERE i.merchant_id = m.id AND i.sku_id = seed.sku_id
);

DROP TEMPORARY TABLE dev_seed_inventory_guard;
