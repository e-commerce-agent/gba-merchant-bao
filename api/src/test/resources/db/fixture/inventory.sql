-- Explicit @Sql fixture only; loaded before the whole test class.
-- Depends on auth_accounts.sql and product_catalog.sql (same stable fixture tenant ids).
INSERT INTO inventory (merchant_id, sku_id, available_qty, reserved_qty, version)
SELECT m.id, 2600000000000301, 10, 0, 0
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO';

INSERT INTO inventory (merchant_id, sku_id, available_qty, reserved_qty, version)
SELECT m.id, 2600000000000303, 5, 0, 0
FROM merchant m WHERE m.merchant_code = 'M_ISOLATION_TEST';
