-- Explicit @Sql fixture only; run inside a rollback test transaction.
-- Shipment fixture for ShipmentFlowIT/ShipmentSoapIT; depends on merchants from db/seed/nonprod.
-- Depends on auth, product catalog, and order fixtures.
INSERT INTO shipment (id, merchant_id, order_id, order_no, status, carrier_name, carrier_code, tracking_no, estimated_delivery_at)
SELECT 4000000000000401, m.id, 2000000000000303, 'DEV-2026-0903', 'IN_TRANSIT', '顺丰速运', 'SF', 'FIX1000001', UTC_TIMESTAMP(6) + INTERVAL 2 DAY
FROM merchant m WHERE merchant_code = 'M_3C_DEMO';

INSERT INTO shipment (id, merchant_id, order_id, order_no, status, carrier_name, carrier_code, tracking_no, delivered_at)
SELECT 4000000000000402, m.id, 2000000000000301, 'DEV-2026-0901', 'DELIVERED', '顺丰速运', 'SF', 'FIX1000002', UTC_TIMESTAMP(6) - INTERVAL 1 DAY
FROM merchant m WHERE merchant_code = 'M_3C_DEMO';

INSERT INTO shipment (id, merchant_id, order_id, order_no, status, carrier_name, carrier_code, tracking_no)
SELECT 4000000000000403, m.id, 2000000000000304, 'DEV-2026-0901', 'IN_TRANSIT', '中通快递', 'ZTO', 'FIX1000003'
FROM merchant m WHERE merchant_code = 'M_ISOLATION_TEST';

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 4000000000000501, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 2 DAY, 'IN_TRANSIT', 'Fixture 包裹已发出', '广州'
FROM shipment s WHERE s.tracking_no = 'FIX1000001';

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 4000000000000502, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'IN_TRANSIT', 'Fixture 包裹运输中', '深圳'
FROM shipment s WHERE s.tracking_no = 'FIX1000001';

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 4000000000000511, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'DELIVERED', 'Fixture 包裹已签收', '深圳'
FROM shipment s WHERE s.tracking_no = 'FIX1000002';

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 4000000000000521, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'IN_TRANSIT', 'Fixture 隔离租户轨迹', '杭州'
FROM shipment s WHERE s.tracking_no = 'FIX1000003';
