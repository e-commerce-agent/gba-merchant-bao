-- Repeatable local/test fixture. This file is never included by the production profile.
INSERT INTO `merchant`
  (`id`, `merchant_code`, `merchant_name`, `status`, `timezone`, `currency_code`, `version`)
VALUES
  (1000000000000001, 'M_3C_DEMO', '3C demo tenant', 'ACTIVE', 'Asia/Shanghai', 'CNY', 0),
  (1000000000000002, 'M_ISOLATION_TEST', 'Isolation test tenant', 'ACTIVE', 'Asia/Shanghai', 'CNY', 0)
ON DUPLICATE KEY UPDATE
  `merchant_name` = VALUES(`merchant_name`),
  `status` = VALUES(`status`),
  `timezone` = VALUES(`timezone`),
  `currency_code` = VALUES(`currency_code`);
