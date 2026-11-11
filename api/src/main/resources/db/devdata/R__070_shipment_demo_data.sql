-- Development only. Shipment demo data for the shipment module (registry entry R__070).
-- order_id/order_no align with the order module devdata (R__060_order_demo_orders.sql):
-- DEV-2026-0001 (COMPLETED) carries the DELIVERED shipment, DEV-2026-0002 (SHIPPED) the
-- IN_TRANSIT one, and the isolation tenant its own DEV-2026-0001.
-- NOT_SHIPPED is synthesized by the service when no shipment row exists, so no NOT_SHIPPED rows are seeded.
DROP TEMPORARY TABLE IF EXISTS dev_seed_shipment_guard;
CREATE TEMPORARY TABLE dev_seed_shipment_guard (
  dev_seed_missing_M_3C_DEMO INT NOT NULL,
  dev_seed_missing_M_ISOLATION_TEST INT NOT NULL,
  dev_seed_missing_order INT NOT NULL,
  dev_seed_order_relation_conflict INT NOT NULL,
  dev_seed_shipment_identity_conflict INT NOT NULL,
  dev_seed_track_identity_conflict INT NOT NULL
);
INSERT INTO dev_seed_shipment_guard
SELECT
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_3C_DEMO') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (
    SELECT 1 FROM (
      SELECT 2100000000000301 AS id, 'M_3C_DEMO' AS code UNION ALL
      SELECT 2100000000000302, 'M_3C_DEMO' UNION ALL
      SELECT 2100000000000307, 'M_ISOLATION_TEST'
    ) expected JOIN merchant m ON m.merchant_code = expected.code
    LEFT JOIN trade_order o ON o.id = expected.id AND o.merchant_id = m.id
    WHERE o.id IS NULL
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM (
      SELECT 2100000000000301 AS id, 'M_3C_DEMO' AS code, 'DEV-2026-0001' AS order_no, 'COMPLETED' AS status UNION ALL
      SELECT 2100000000000302, 'M_3C_DEMO', 'DEV-2026-0002', 'SHIPPED' UNION ALL
      SELECT 2100000000000307, 'M_ISOLATION_TEST', 'DEV-2026-0001', 'SHIPPED'
    ) expected JOIN merchant m ON m.merchant_code = expected.code
    JOIN trade_order o ON o.id = expected.id OR (o.merchant_id = m.id AND o.order_no = expected.order_no)
    WHERE o.merchant_id <> m.id OR o.id <> expected.id OR o.order_no <> expected.order_no OR o.status <> expected.status
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM shipment s
    JOIN (
      SELECT 3000000000000301 AS id, 2100000000000302 AS order_id, 'DEV-2026-0002' AS order_no, 'SF1000000000301' AS tracking_no, 'M_3C_DEMO' AS code
      UNION ALL SELECT 3000000000000302, 2100000000000301, 'DEV-2026-0001', 'SF1000000000302', 'M_3C_DEMO'
      UNION ALL SELECT 3000000000000304, 2100000000000307, 'DEV-2026-0001', 'ZTO1000000000304', 'M_ISOLATION_TEST'
    ) d JOIN merchant m ON m.merchant_code = d.code
    WHERE (s.id = d.id OR (s.merchant_id = m.id AND (s.order_id = d.order_id OR s.tracking_no = d.tracking_no)))
      AND (s.id <> d.id OR s.merchant_id <> m.id OR s.order_id <> d.order_id
           OR s.order_no <> d.order_no OR s.tracking_no <> d.tracking_no)
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM shipment_track t
    JOIN (
      SELECT 3000000000000401 AS id, 'M_3C_DEMO' AS code, 3000000000000301 AS shipment_id, '包裹已从广州转运中心发出' AS description UNION ALL
      SELECT 3000000000000402, 'M_3C_DEMO', 3000000000000301, '包裹到达深圳集散中心' UNION ALL
      SELECT 3000000000000403, 'M_3C_DEMO', 3000000000000301, '包裹已从深圳集散中心发出，派送中' UNION ALL
      SELECT 3000000000000411, 'M_3C_DEMO', 3000000000000302, '包裹已从东莞转运中心发出' UNION ALL
      SELECT 3000000000000412, 'M_3C_DEMO', 3000000000000302, '包裹已签收，签收人：本人' UNION ALL
      SELECT 3000000000000421, 'M_ISOLATION_TEST', 3000000000000304, '包裹到达杭州转运中心'
    ) expected ON expected.id = t.id OR t.description = expected.description
    JOIN merchant m ON m.merchant_code = expected.code
    WHERE (t.id = expected.id OR (t.merchant_id = m.id AND t.shipment_id = expected.shipment_id AND t.description = expected.description))
      AND (t.id <> expected.id OR t.merchant_id <> m.id OR t.shipment_id <> expected.shipment_id OR t.description <> expected.description)
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
FROM shipment s JOIN merchant m ON m.id = s.merchant_id WHERE m.merchant_code = 'M_3C_DEMO' AND s.id = 3000000000000301
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹已从广州转运中心发出');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000402, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'IN_TRANSIT', '包裹到达深圳集散中心', '深圳'
FROM shipment s JOIN merchant m ON m.id = s.merchant_id WHERE m.merchant_code = 'M_3C_DEMO' AND s.id = 3000000000000301
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹到达深圳集散中心');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000403, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 2 HOUR, 'IN_TRANSIT', '包裹已从深圳集散中心发出，派送中', '深圳'
FROM shipment s JOIN merchant m ON m.id = s.merchant_id WHERE m.merchant_code = 'M_3C_DEMO' AND s.id = 3000000000000301
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹已从深圳集散中心发出，派送中');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000411, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 3 DAY, 'IN_TRANSIT', '包裹已从东莞转运中心发出', '东莞'
FROM shipment s JOIN merchant m ON m.id = s.merchant_id WHERE m.merchant_code = 'M_3C_DEMO' AND s.id = 3000000000000302
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹已从东莞转运中心发出');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000412, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'DELIVERED', '包裹已签收，签收人：本人', '深圳'
FROM shipment s JOIN merchant m ON m.id = s.merchant_id WHERE m.merchant_code = 'M_3C_DEMO' AND s.id = 3000000000000302
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹已签收，签收人：本人');

INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description, location)
SELECT 3000000000000421, s.id, s.merchant_id, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, 'IN_TRANSIT', '包裹到达杭州转运中心', '杭州'
FROM shipment s JOIN merchant m ON m.id = s.merchant_id WHERE m.merchant_code = 'M_ISOLATION_TEST' AND s.id = 3000000000000304
AND NOT EXISTS (SELECT 1 FROM shipment_track t WHERE t.shipment_id = s.id AND t.description = '包裹到达杭州转运中心');

DROP TEMPORARY TABLE dev_seed_shipment_guard;
