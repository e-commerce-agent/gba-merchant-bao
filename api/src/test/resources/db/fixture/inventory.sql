-- Explicit @Sql fixture only; loaded before the whole test class.
-- Depends on auth_accounts.sql tenants/staff (same stable id space).
-- product/sku tables do not exist yet: sku_id is a trace-only reference, no FK is declared.
INSERT INTO inventory (merchant_id, sku_id, available_qty, reserved_qty, version)
SELECT m.id, 2000000000000902, 10, 0, 0
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO';

INSERT INTO inventory (merchant_id, sku_id, available_qty, reserved_qty, version)
SELECT m.id, 2000000000000908, 5, 0, 0
FROM merchant m WHERE m.merchant_code = 'M_ISOLATION_TEST';
