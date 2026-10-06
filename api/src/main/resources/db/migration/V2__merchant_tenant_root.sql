CREATE TABLE `merchant` (
  `id` bigint NOT NULL COMMENT 'Snowflake primary key; opaque outside the service',
  `merchant_code` varchar(64) NOT NULL COMMENT 'Stable business code',
  `merchant_name` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `timezone` varchar(64) NOT NULL DEFAULT 'UTC',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `created_by` bigint NULL,
  `updated_by` bigint NULL,
  `version` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_code` (`merchant_code`),
  KEY `idx_merchant_status` (`status`, `merchant_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tenant root';
