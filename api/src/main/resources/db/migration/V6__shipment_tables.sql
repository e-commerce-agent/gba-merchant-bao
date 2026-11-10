-- Shipment module tables (roadmap section 5). Logical reference to the order module only:
-- order tables are delivered by a later migration, so no physical foreign key to orders is created.
-- Numbering: V6. V5 is already taken by the agent/conversation module on main, so the shipment
-- module takes V6 and the order (V7) / inventory (V8) modules follow after it.

CREATE TABLE `shipment` (
  `id` bigint NOT NULL COMMENT 'Snowflake primary key',
  `merchant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL COMMENT 'Logical reference to the order module orders.id; authorization enforced in Service/SQL',
  `order_no` varchar(64) NOT NULL COMMENT 'Order number snapshot taken at fulfillment time',
  `status` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_SHIPPED',
  `carrier_name` varchar(64) NOT NULL COMMENT 'Carrier display name',
  `carrier_code` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Carrier code, optional',
  `tracking_no` varchar(64) NOT NULL COMMENT 'Waybill number; never a public lookup key',
  `estimated_delivery_at` timestamp(6) NULL COMMENT 'Returned only when data provides it',
  `delivered_at` timestamp(6) NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_merchant_order` (`merchant_id`, `order_id`),
  UNIQUE KEY `uk_shipment_merchant_tracking` (`merchant_id`, `tracking_no`),
  KEY `idx_shipment_merchant_order_no` (`merchant_id`, `order_no`),
  CONSTRAINT `fk_shipment_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `ck_shipment_status` CHECK (`status` IN ('NOT_SHIPPED', 'IN_TRANSIT', 'DELIVERED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `shipment_track` (
  `id` bigint NOT NULL COMMENT 'Snowflake primary key',
  `shipment_id` bigint NOT NULL,
  `merchant_id` bigint NOT NULL COMMENT 'Redundant tenant column; isolation queries never join the main table',
  `event_time` timestamp(6) NOT NULL COMMENT 'Track event time, UTC',
  `status` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `description` varchar(255) NOT NULL COMMENT 'Desensitized track description',
  `location` varchar(128) NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_shipment_track_timeline` (`shipment_id`, `event_time`),
  UNIQUE KEY `uk_shipment_track_event` (`shipment_id`, `event_time`, `description`),
  CONSTRAINT `fk_shipment_track_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `ck_shipment_track_status` CHECK (`status` IN ('NOT_SHIPPED', 'IN_TRANSIT', 'DELIVERED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
