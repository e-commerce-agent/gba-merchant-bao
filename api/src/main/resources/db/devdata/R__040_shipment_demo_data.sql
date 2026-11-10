-- Development only. Shipment demo data for the shipment module (registry entry R__040;
-- R__030 is taken by the agent/conversation demo seed on main).
-- order_id/order_no align with the order module devdata (R__050_order_demo_orders.sql):
-- DEV-2026-0001 (COMPLETED) carries the DELIVERED shipment, DEV-2026-0002 (SHIPPED) the
-- IN_TRANSIT one, and the isolation tenant its own DEV-2026-0001.
-- NOT_SHIPPED is synthesized by the service when no shipment row exists, so no NOT_SHIPPED rows are seeded.
DROP TEMPORARY TABLE IF EXISTS dev_seed_shipment_guard;
CREATE TEMPORARY TABLE dev_seed_shipment_guard (
  dev_seed_missing_M_3C_DEMO INT NOT NULL,
  dev_seed_missing_M_ISOLATION_TEST INT NOT NULL,
  dev_seed_shipment_identity_conflict INT NOT NULL
);
INSERT INTO dev_seed_shipment_guard
SELECT
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_3C_DEMO') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (
    SELECT 1 FROM shipment s
    JOIN (
      SELECT 3000000000000301 AS id, 2100000000000302 AS order_id, 'M_3C_DEMO' AS code
      UNION ALL SELECT 3000000000000302, 2100000000000301, 'M_3C_DEMO'
      UNION ALL SELECT 3000000000000304, 2100000000000307, 'M_ISOLATION_TEST'
    ) d JOIN merchant m ON m.merchant_code = d.code
    WHERE (s.id = d.id OR (s.merchant_id = m.id AND s.order_id = d.order_id))
      AND (s.id <> d.id OR s.merchant_id <> m.id)
  ) THEN NULL ELSE 1 END;

INSERT INTO shipment (id, merchant_id, order_id, order_no, status, carrier_name, carrier_code, tracking_no, estimated_delivery_at)
SELECT 3000000000000301, m.id, 2100000000000302, 'DEV-2026-0002', 'IN_TRANSIT', '顺丰速运', 'SF', 'SF1000000000301',
       UTC_TIMESTAMP(6) + INTERVAL 2 DAY
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO'
AND NOT EXISTS (SELECT 1 FROM shipment s WHERE s.merchant_id = m.id AND s.order_id = 2100000000000302);

INSERT INTO shipment (id, merchant_id, order_id, order_no, status, carrier_name, carrier_code, tracking_no, delivered_at)
SELECT 3000000000000302, m.id, 2100000000000301, 'DEV-2026-0001', 'DELIVERED', '顺丰速运', 'SF', 'SF1000000000302',
       UTC_TIMESTAMP(6) - INTERVAL 1 DAY
FROM merchant m WHERE m.merchant_code = 'M_3C_DEMO'
AND NOT EXISTS (SELECT 1 FROM shipment s WHERE s.merchant_id = m.id AND s.order_id = 2100000000000301);

INSERT INTO shipment (id, merchant_id, order_id, order_no, status, carrier_name, carrier_code, tracking_no, estimated_delivery_at)
SELECT 3000000000000304, m.id, 2100000000000307, 'DEV-2026-0001', 'IN_TRANSIT', '中通快递', 'ZTO', 'ZTO1000000000304',
       UTC_TIMESTAMP(6) + INTERVAL 3 DAY
FROM merchant m WHERE m.merchant_code = 'M_ISOLATION_TEST'
AND NOT EXISTS (SELECT 1 FROM shipment s WHERE s.merchant_id = m.id AND s.order_id = 2100000000000307);

-- Tracks (idempotent via uk_shipment_track_event).
INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000401, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 2 DAY, 'IN_TRANSIT', '包裹已从广州转运中心发出', '广州'
FROM shipment s WHERE s.tracking_no = 'SF1000000000301'
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹已从广州转运中心发出');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000402, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'IN_TRANSIT', '包裹到达深圳集散中心', '深圳'
FROM shipment s WHERE s.tracking_no = 'SF1000000000301'
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹到达深圳集散中心');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000403, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 2 HOUR, 'IN_TRANSIT', '包裹已从深圳集散中心发出，派送中', '深圳'
FROM shipment s WHERE s.tracking_no = 'SF1000000000301'
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹已从深圳集散中心发出，派送中');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000411, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 3 DAY, 'IN_TRANSIT', '包裹已从东莞转运中心发出', '东莞'
FROM shipment s WHERE s.tracking_no = 'SF1000000000302'
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹已从东莞转运中心发出');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000412, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'DELIVERED', '包裹已签收，签收人：本人', '深圳'
FROM shipment s WHERE s.tracking_no = 'SF1000000000302'
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹已签收，签收人：本人');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000421, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'IN_TRANSIT', '包裹到达杭州转运中心', '杭州'
FROM shipment s WHERE s.tracking_no = 'ZTO1000000000304'
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹到达杭州转运中心');

DROP TEMPORARY TABLE dev_seed_shipment_guard;
