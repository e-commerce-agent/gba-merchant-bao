ALTER TABLE `merchant`
  ADD COLUMN `currency_code` varchar(3) NOT NULL DEFAULT 'CNY' AFTER `timezone`;
