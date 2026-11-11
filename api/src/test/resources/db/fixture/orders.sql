-- Explicit @Sql fixture only; run inside a rollback test transaction.
-- Depends on auth_accounts.sql tenants/consumers/staff (same stable id space).
-- Tables are trade_order/trade_order_item (legacy baseline owns `orders`).
-- Depends on product_catalog.sql; item snapshots remain independent of current catalog values.
INSERT INTO trade_order (id, merchant_id, order_no, customer_id, status, pay_status, total_amount, paid_amount, currency, placed_at)
SELECT 2000000000000301, m.id, 'DEV-2026-0901', 2000000000000201, 'COMPLETED', 'PAID', 158.00, 158.00, 'CNY', '2026-10-20 10:00:00'
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO';
INSERT INTO trade_order (id, merchant_id, order_no, customer_id, status, pay_status, total_amount, paid_amount, currency, placed_at)
SELECT 2000000000000302, m.id, 'DEV-2026-0902', 2000000000000201, 'PENDING_PAYMENT', 'UNPAID', 89.90, NULL, 'CNY', '2026-10-21 11:00:00'
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO';
INSERT INTO trade_order (id, merchant_id, order_no, customer_id, status, pay_status, total_amount, paid_amount, currency, placed_at)
SELECT 2000000000000303, m.id, 'DEV-2026-0903', 2000000000000202, 'SHIPPED', 'PAID', 45.00, 45.00, 'CNY', '2026-10-22 12:00:00'
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO';
INSERT INTO trade_order (id, merchant_id, order_no, customer_id, status, pay_status, total_amount, paid_amount, currency, placed_at)
SELECT 2000000000000304, m.id, 'DEV-2026-0901', 2000000000000203, 'SHIPPED', 'PAID', 35.00, 35.00, 'CNY', '2026-10-23 13:00:00'
FROM merchant m WHERE m.merchant_code = 'M_ISOLATION_TEST';

INSERT INTO trade_order_item (id, merchant_id, order_id, product_id, sku_id, product_name_snapshot, sku_attrs_snapshot, unit_price, quantity, amount)
SELECT 2000000000000311, m.id, 2000000000000301, 2600000000000201, 2600000000000301,
       '65W 双口充电器', '{"颜色":"白色","插头":"国标","接口":"USB-C"}', 79.00, 1, 79.00
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO';
INSERT INTO trade_order_item (id, merchant_id, order_id, product_id, sku_id, product_name_snapshot, sku_attrs_snapshot, unit_price, quantity, amount)
SELECT 2000000000000312, m.id, 2000000000000301, 2600000000000202, 2600000000000302,
       '编织快充数据线', '{"颜色":"黑色","长度":"1米"}', 79.00, 1, 79.00
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO';
INSERT INTO trade_order_item (id, merchant_id, order_id, product_id, sku_id, product_name_snapshot, sku_attrs_snapshot, unit_price, quantity, amount)
SELECT 2000000000000313, m.id, 2000000000000303, 2600000000000202, 2600000000000302,
       '车载充电器', '{"功率":"30W"}', 45.00, 1, 45.00
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO';
INSERT INTO trade_order_item (id, merchant_id, order_id, product_id, sku_id, product_name_snapshot, sku_attrs_snapshot, unit_price, quantity, amount)
SELECT 2000000000000314, m.id, 2000000000000304, 2600000000000203, 2600000000000303,
       '隔离测试充电线', '{"颜色":"黑色"}', 35.00, 1, 35.00
FROM merchant m WHERE m.merchant_code = 'M_ISOLATION_TEST';
