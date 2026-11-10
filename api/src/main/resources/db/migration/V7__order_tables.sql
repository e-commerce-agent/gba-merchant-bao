-- Order query module: consumer/staff read-only order facts with item trade snapshots.
-- Table names trade_order/trade_order_item: the legacy baseline already owns `orders`,
-- so the module tables carry a trade_ prefix.
-- Design source: docs/initialization/设计手稿.md 1.1.4 (orders, order_item).
-- Read-only scope per api/docs/architecture/module-roadmap.md section 4:
-- no placement, payment or stock deduction writes are modelled in this change.
CREATE TABLE `trade_order` (
  `id` bigint NOT NULL COMMENT 'Snowflake primary key',
  `merchant_id` bigint NOT NULL,
  `order_no` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Tenant-visible order number',
  `customer_id` bigint NOT NULL COMMENT 'consumer_account.id of the owning consumer',
  `status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PENDING_PAYMENT/PENDING_SHIPMENT/SHIPPED/COMPLETED/CANCELLED',
  `pay_status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'UNPAID/PAID/REFUNDING/REFUNDED',
  `total_amount` decimal(12,2) NOT NULL COMMENT 'Order total in minor-independent currency units',
  `paid_amount` decimal(12,2) NULL COMMENT 'Settled amount; NULL while unpaid',
  `currency` varchar(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY' COMMENT 'ISO 4217 code captured at placement',
  `placed_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Placement time in UTC',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_order_merchant_order_no` (`merchant_id`, `order_no`),
  UNIQUE KEY `uk_trade_order_merchant_id` (`merchant_id`, `id`),
  KEY `idx_trade_order_merchant_customer_placed` (`merchant_id`, `customer_id`, `placed_at`),
  CONSTRAINT `fk_trade_order_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `fk_trade_order_customer` FOREIGN KEY (`merchant_id`, `customer_id`)
    REFERENCES `consumer_account` (`merchant_id`, `id`),
  CONSTRAINT `ck_trade_order_status` CHECK (`status` IN ('PENDING_PAYMENT', 'PENDING_SHIPMENT', 'SHIPPED', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT `ck_trade_order_pay_status` CHECK (`pay_status` IN ('UNPAID', 'PAID', 'REFUNDING', 'REFUNDED')),
  CONSTRAINT `ck_trade_order_total_amount` CHECK (`total_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `trade_order_item` (
  `id` bigint NOT NULL COMMENT 'Snowflake primary key',
  `merchant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL COMMENT 'product.id at placement; kept for trace only',
  `sku_id` bigint NOT NULL COMMENT 'product_sku.id at placement; kept for trace only',
  `product_name_snapshot` varchar(128) NOT NULL COMMENT 'Product name frozen at placement',
  `sku_attrs_snapshot` varchar(512) NOT NULL DEFAULT '' COMMENT 'SKU attribute JSON frozen at placement',
  `unit_price` decimal(12,2) NOT NULL COMMENT 'Deal unit price frozen at placement',
  `quantity` int NOT NULL COMMENT 'Purchased quantity',
  `amount` decimal(12,2) NOT NULL COMMENT 'unit_price * quantity frozen at placement',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_order_item_merchant_id` (`merchant_id`, `id`),
  KEY `idx_trade_order_item_merchant_order` (`merchant_id`, `order_id`),
  CONSTRAINT `fk_trade_order_item_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
  CONSTRAINT `fk_trade_order_item_order` FOREIGN KEY (`merchant_id`, `order_id`)
    REFERENCES `trade_order` (`merchant_id`, `id`),
  CONSTRAINT `ck_trade_order_item_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `ck_trade_order_item_amount` CHECK (`amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
