-- Development only. Add missing demo orders for the 3C demo tenant and the isolation tenant.
-- Order module owns orders + order_item seed data (db/devdata/README.md numbering 030).
-- Item snapshots are self-contained: product_id/sku_id reference the future product module
-- seeds but no FK is declared, so this seed loads before product tables exist.
DROP TEMPORARY TABLE IF EXISTS dev_seed_order_guard;
CREATE TEMPORARY TABLE dev_seed_order_guard (
  dev_seed_missing_M_3C_DEMO INT NOT NULL,
  dev_seed_missing_M_ISOLATION_TEST INT NOT NULL,
  dev_seed_missing_consumer INT NOT NULL,
  dev_seed_order_identity_conflict INT NOT NULL
);
INSERT INTO dev_seed_order_guard
SELECT
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_3C_DEMO') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (
    SELECT 1 FROM consumer_account a
    JOIN (
      SELECT 2100000000000201 AS id, 'M_3C_DEMO' AS code
      UNION ALL SELECT 2100000000000202, 'M_3C_DEMO'
      UNION ALL SELECT 2100000000000203, 'M_ISOLATION_TEST'
    ) s JOIN merchant m ON m.merchant_code = s.code
    WHERE (a.id = s.id AND a.merchant_id = m.id)
  ) THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (
    SELECT 1 FROM trade_order o
    JOIN (
      SELECT 2100000000000301 AS id, 'M_3C_DEMO' AS code, 'DEV-2026-0001' AS order_no
      UNION ALL SELECT 2100000000000302, 'M_3C_DEMO', 'DEV-2026-0002'
      UNION ALL SELECT 2100000000000303, 'M_3C_DEMO', 'DEV-2026-0003'
      UNION ALL SELECT 2100000000000304, 'M_3C_DEMO', 'DEV-2026-0004'
      UNION ALL SELECT 2100000000000305, 'M_3C_DEMO', 'DEV-2026-0005'
      UNION ALL SELECT 2100000000000306, 'M_3C_DEMO', 'DEV-2026-0006'
      UNION ALL SELECT 2100000000000307, 'M_ISOLATION_TEST', 'DEV-2026-0001'
    ) s JOIN merchant m ON m.merchant_code = s.code
    WHERE (o.id = s.id OR (o.merchant_id = m.id AND o.order_no = s.order_no))
      AND (o.id <> s.id OR o.merchant_id <> m.id OR o.order_no <> s.order_no)
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
         2100000000000203, 'PENDING_SHIPMENT', 'PAID', 35.00, 35.00, '2026-11-07 08:00:00'
) o
JOIN merchant m ON m.merchant_code = o.code
JOIN consumer_account c ON c.id = o.customer_id AND c.merchant_id = m.id
WHERE NOT EXISTS (SELECT 1 FROM trade_order x WHERE x.id = o.id AND x.merchant_id = m.id AND x.order_no = o.order_no);

INSERT INTO trade_order_item (id, merchant_id, order_id, product_id, sku_id, product_name_snapshot, sku_attrs_snapshot, unit_price, quantity, amount)
SELECT i.id, m.id, o.id, i.product_id, i.sku_id, i.product_name_snapshot, i.sku_attrs_snapshot, i.unit_price, i.quantity, i.amount
FROM (
  SELECT 2100000000000311 AS id, 'M_3C_DEMO' AS code, 'DEV-2026-0001' AS order_no,
         2100000000000901 AS product_id, 2100000000000902 AS sku_id,
         '65W 双口充电器' AS product_name_snapshot,
         '{"颜色":"白色","插头":"国标","接口":"USB-C"}' AS sku_attrs_snapshot,
         79.00 AS unit_price, 1 AS quantity, 79.00 AS amount
  UNION ALL SELECT 2100000000000312, 'M_3C_DEMO', 'DEV-2026-0001',
         2100000000000903, 2100000000000904,
         '编织快充数据线', '{"颜色":"黑色","长度":"1米","接口":"USB-C 转 Lightning"}',
         79.00, 1, 79.00
  UNION ALL SELECT 2100000000000313, 'M_3C_DEMO', 'DEV-2026-0002',
         2100000000000905, 2100000000000906,
         '主动降噪耳机', '{"颜色":"白色","接口":"USB-C"}',
         89.90, 1, 89.90
  UNION ALL SELECT 2100000000000314, 'M_3C_DEMO', 'DEV-2026-0003',
         2100000000000907, 2100000000000908,
         '车载充电器', '{"颜色":"黑色","插头":"车充","功率":"30W"}',
         45.00, 1, 45.00
  UNION ALL SELECT 2100000000000315, 'M_3C_DEMO', 'DEV-2026-0004',
         2100000000000909, 2100000000000910,
         '氮化镓充电器', '{"颜色":"白色","功率":"120W"}',
         128.00, 1, 128.00
  UNION ALL SELECT 2100000000000316, 'M_3C_DEMO', 'DEV-2026-0005',
         2100000000000911, 2100000000000912,
         '三合一数据线', '{"颜色":"灰色","长度":"1.2米"}',
         66.00, 1, 66.00
  UNION ALL SELECT 2100000000000317, 'M_3C_DEMO', 'DEV-2026-0006',
         2100000000000913, 2100000000000914,
         '头戴式耳机', '{"颜色":"黑色","接口":"3.5mm"}',
         199.00, 1, 199.00
  UNION ALL SELECT 2100000000000318, 'M_ISOLATION_TEST', 'DEV-2026-0001',
         2100000000000915, 2100000000000916,
         '隔离测试充电线', '{"颜色":"黑色","长度":"0.5米"}',
         35.00, 1, 35.00
) i
JOIN trade_order o ON o.order_no = i.order_no
JOIN merchant m ON m.merchant_code = i.code AND o.merchant_id = m.id
WHERE NOT EXISTS (SELECT 1 FROM trade_order_item x WHERE x.id = i.id AND x.merchant_id = m.id);

DROP TEMPORARY TABLE dev_seed_order_guard;
