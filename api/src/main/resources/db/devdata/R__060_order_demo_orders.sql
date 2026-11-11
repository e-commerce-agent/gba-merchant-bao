-- Development only. Add missing demo orders for the 3C demo tenant and the isolation tenant.
-- Order module owns orders + order_item seed data (db/devdata/README.md numbering 060).
-- Item product_id/sku_id reference the preceding product seed; the persisted item names,
-- attributes, prices, and amounts remain independent trade snapshots.
DROP TEMPORARY TABLE IF EXISTS dev_seed_order_guard;
CREATE TEMPORARY TABLE dev_seed_order_guard (
  dev_seed_missing_M_3C_DEMO INT NOT NULL,
  dev_seed_missing_M_ISOLATION_TEST INT NOT NULL,
  dev_seed_missing_consumer INT NOT NULL,
  dev_seed_missing_catalog_sku INT NOT NULL,
  dev_seed_order_identity_conflict INT NOT NULL,
  dev_seed_order_item_identity_conflict INT NOT NULL
);
INSERT INTO dev_seed_order_guard
SELECT
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_3C_DEMO') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (
    SELECT 1 FROM (
    SELECT 2100000000000201 AS id, 'M_3C_DEMO' AS code
      UNION ALL SELECT 2100000000000202, 'M_3C_DEMO'
      UNION ALL SELECT 2100000000000203, 'M_ISOLATION_TEST'
    ) s JOIN merchant m ON m.merchant_code = s.code
    LEFT JOIN consumer_account a ON a.id = s.id AND a.merchant_id = m.id
    WHERE a.id IS NULL
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM (
      SELECT 2500000000000201 AS product_id, 2500000000000301 AS sku_id, 'M_3C_DEMO' AS code UNION ALL
      SELECT 2500000000000202, 2500000000000304, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000203, 2500000000000308, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000202, 2500000000000306, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000201, 2500000000000302, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000202, 2500000000000305, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000203, 2500000000000307, 'M_3C_DEMO' UNION ALL
      SELECT 2500000000000206, 2500000000000309, 'M_ISOLATION_TEST'
    ) expected
    JOIN merchant m ON m.merchant_code = expected.code
    LEFT JOIN product_sku k ON k.id = expected.sku_id AND k.merchant_id = m.id AND k.product_id = expected.product_id
    WHERE k.id IS NULL
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM trade_order o
    JOIN (
      SELECT 2100000000000301 AS id, 'M_3C_DEMO' AS code, 'DEV-2026-0001' AS order_no, 2100000000000201 AS customer_id
      UNION ALL SELECT 2100000000000302, 'M_3C_DEMO', 'DEV-2026-0002', 2100000000000201
      UNION ALL SELECT 2100000000000303, 'M_3C_DEMO', 'DEV-2026-0003', 2100000000000201
      UNION ALL SELECT 2100000000000304, 'M_3C_DEMO', 'DEV-2026-0004', 2100000000000201
      UNION ALL SELECT 2100000000000305, 'M_3C_DEMO', 'DEV-2026-0005', 2100000000000201
      UNION ALL SELECT 2100000000000306, 'M_3C_DEMO', 'DEV-2026-0006', 2100000000000202
      UNION ALL SELECT 2100000000000307, 'M_ISOLATION_TEST', 'DEV-2026-0001', 2100000000000203
    ) s JOIN merchant m ON m.merchant_code = s.code
    WHERE (o.id = s.id OR (o.merchant_id = m.id AND o.order_no = s.order_no))
      AND (o.id <> s.id OR o.merchant_id <> m.id OR o.order_no <> s.order_no OR o.customer_id <> s.customer_id)
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM trade_order_item existing
    JOIN (
      SELECT 2100000000000311 AS id, 'M_3C_DEMO' AS code, 'DEV-2026-0001' AS order_no, 2500000000000201 AS product_id, 2500000000000301 AS sku_id UNION ALL
      SELECT 2100000000000312, 'M_3C_DEMO', 'DEV-2026-0001', 2500000000000202, 2500000000000304 UNION ALL
      SELECT 2100000000000313, 'M_3C_DEMO', 'DEV-2026-0002', 2500000000000203, 2500000000000308 UNION ALL
      SELECT 2100000000000314, 'M_3C_DEMO', 'DEV-2026-0003', 2500000000000202, 2500000000000306 UNION ALL
      SELECT 2100000000000315, 'M_3C_DEMO', 'DEV-2026-0004', 2500000000000201, 2500000000000302 UNION ALL
      SELECT 2100000000000316, 'M_3C_DEMO', 'DEV-2026-0005', 2500000000000202, 2500000000000305 UNION ALL
      SELECT 2100000000000317, 'M_3C_DEMO', 'DEV-2026-0006', 2500000000000203, 2500000000000307 UNION ALL
      SELECT 2100000000000318, 'M_ISOLATION_TEST', 'DEV-2026-0001', 2500000000000206, 2500000000000309
    ) expected ON expected.id = existing.id
    JOIN merchant m ON m.merchant_code = expected.code
    LEFT JOIN trade_order o ON o.merchant_id = m.id AND o.order_no = expected.order_no
    WHERE o.id IS NULL OR existing.merchant_id <> m.id OR existing.order_id <> o.id
       OR existing.product_id <> expected.product_id OR existing.sku_id <> expected.sku_id
  ) THEN NULL ELSE 1 END;

INSERT INTO trade_order (id, merchant_id, order_no, customer_id, status, pay_status, total_amount, paid_amount, currency, placed_at)
SELECT o.id, m.id, o.order_no, o.customer_id, o.status, o.pay_status, o.total_amount, o.paid_amount, 'CNY', o.placed_at
FROM (
  SELECT 2100000000000301 AS id, 'M_3C_DEMO' AS code, 'DEV-2026-0001' AS order_no,
         2100000000000201 AS customer_id, 'COMPLETED' AS status, 'PAID' AS pay_status,
         158.00 AS total_amount, 158.00 AS paid_amount, '2026-11-01 10:15:00' AS placed_at
  UNION ALL SELECT 2100000000000302, 'M_3C_DEMO', 'DEV-2026-0002',
         2100000000000201, 'SHIPPED', 'PAID', 89.90, 89.90, '2026-11-05 20:42:10'
  UNION ALL SELECT 2100000000000303, 'M_3C_DEMO', 'DEV-2026-0003',
         2100000000000201, 'PENDING_SHIPMENT', 'PAID', 45.00, 45.00, '2026-11-08 09:05:30'
  UNION ALL SELECT 2100000000000304, 'M_3C_DEMO', 'DEV-2026-0004',
         2100000000000201, 'PENDING_PAYMENT', 'UNPAID', 128.00, NULL, '2026-11-09 18:00:00'
  UNION ALL SELECT 2100000000000305, 'M_3C_DEMO', 'DEV-2026-0005',
         2100000000000201, 'CANCELLED', 'UNPAID', 66.00, NULL, '2026-11-06 14:30:00'
  UNION ALL SELECT 2100000000000306, 'M_3C_DEMO', 'DEV-2026-0006',
         2100000000000202, 'COMPLETED', 'REFUNDED', 199.00, 0.00, '2026-11-02 11:20:00'
  UNION ALL SELECT 2100000000000307, 'M_ISOLATION_TEST', 'DEV-2026-0001',
         2100000000000203, 'SHIPPED', 'PAID', 35.00, 35.00, '2026-11-07 08:00:00'
) o
JOIN merchant m ON m.merchant_code = o.code
JOIN consumer_account c ON c.id = o.customer_id AND c.merchant_id = m.id
WHERE NOT EXISTS (SELECT 1 FROM trade_order x WHERE x.id = o.id AND x.merchant_id = m.id AND x.order_no = o.order_no);

INSERT INTO trade_order_item (id, merchant_id, order_id, product_id, sku_id, product_name_snapshot, sku_attrs_snapshot, unit_price, quantity, amount)
SELECT i.id, m.id, o.id, i.product_id, i.sku_id, i.product_name_snapshot, i.sku_attrs_snapshot, i.unit_price, i.quantity, i.amount
FROM (
  SELECT 2100000000000311 AS id, 'M_3C_DEMO' AS code, 'DEV-2026-0001' AS order_no,
         2500000000000201 AS product_id, 2500000000000301 AS sku_id,
         '65W GaN 氮化镓充电器' AS product_name_snapshot,
         '{"功率":"65W","插头":"国标"}' AS sku_attrs_snapshot,
         79.00 AS unit_price, 1 AS quantity, 79.00 AS amount
  UNION ALL SELECT 2100000000000312, 'M_3C_DEMO', 'DEV-2026-0001',
         2500000000000202, 2500000000000304,
         'USB-C 数据线', '{"长度":"1米","颜色":"黑色"}',
         79.00, 1, 79.00
  UNION ALL SELECT 2100000000000313, 'M_3C_DEMO', 'DEV-2026-0002',
         2500000000000203, 2500000000000308,
         'TWS 降噪蓝牙耳机', '{"颜色":"白色"}',
         89.90, 1, 89.90
  UNION ALL SELECT 2100000000000314, 'M_3C_DEMO', 'DEV-2026-0003',
         2500000000000202, 2500000000000306,
         'USB-C 数据线', '{"长度":"2米","颜色":"黑色"}',
         45.00, 1, 45.00
  UNION ALL SELECT 2100000000000315, 'M_3C_DEMO', 'DEV-2026-0004',
         2500000000000201, 2500000000000302,
         '65W GaN 氮化镓充电器', '{"功率":"65W","插头":"欧标"}',
         128.00, 1, 128.00
  UNION ALL SELECT 2100000000000316, 'M_3C_DEMO', 'DEV-2026-0005',
         2500000000000202, 2500000000000305,
         'USB-C 数据线', '{"长度":"1米","颜色":"白色"}',
         66.00, 1, 66.00
  UNION ALL SELECT 2100000000000317, 'M_3C_DEMO', 'DEV-2026-0006',
         2500000000000203, 2500000000000307,
         'TWS 降噪蓝牙耳机', '{"颜色":"黑色"}',
         199.00, 1, 199.00
  UNION ALL SELECT 2100000000000318, 'M_ISOLATION_TEST', 'DEV-2026-0001',
         2500000000000206, 2500000000000309,
         '对照租户商品', '{"规格":"标准"}',
         35.00, 1, 35.00
) i
JOIN trade_order o ON o.order_no = i.order_no
JOIN merchant m ON m.merchant_code = i.code AND o.merchant_id = m.id
WHERE NOT EXISTS (SELECT 1 FROM trade_order_item x WHERE x.id = i.id AND x.merchant_id = m.id);

DROP TEMPORARY TABLE dev_seed_order_guard;
