-- TEST FIXTURE ONLY. Table definitions from read-only live schema inspection 2026-10-17.
CREATE TABLE IF NOT EXISTS `warehouse_schema` (
  `id` int(11) NOT NULL,
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_locations` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(40) NOT NULL,
  `name` varchar(120) NOT NULL,
  `type` varchar(20) NOT NULL,
  `is_active` tinyint(4) NOT NULL DEFAULT 1,
  `created_by` varchar(255) NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_receipts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `supplier_name` varchar(160) NOT NULL,
  `invoice_number` varchar(80) NOT NULL DEFAULT '',
  `note` varchar(500) NOT NULL DEFAULT '',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `created_by` varchar(255) NOT NULL,
  `created_at` datetime NOT NULL,
  `posted_by` varchar(255) DEFAULT NULL,
  `posted_at` datetime DEFAULT NULL,
  `reversed_by` varchar(255) DEFAULT NULL,
  `reversed_at` datetime DEFAULT NULL,
  `reversal_reason` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_receipt_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `receipt_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `quantity` int(11) NOT NULL,
  `location_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `receipt_product` (`receipt_id`,`product_id`),
  KEY `wh_location_fk` (`location_id`),
  CONSTRAINT `wh_location_fk` FOREIGN KEY (`location_id`) REFERENCES `warehouse_locations` (`id`),
  CONSTRAINT `wh_receipt_fk` FOREIGN KEY (`receipt_id`) REFERENCES `warehouse_receipts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_stock_movements` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `kind` varchar(30) NOT NULL,
  `quantity_change` bigint(20) NOT NULL,
  `stock_after` bigint(20) NOT NULL,
  `reference` varchar(160) NOT NULL,
  `actor` varchar(255) NOT NULL,
  `note` varchar(500) NOT NULL DEFAULT '',
  `event_key` varchar(191) NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `event_key` (`event_key`),
  KEY `wh_product_history` (`product_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO warehouse_schema VALUES(1,1);
INSERT IGNORE INTO warehouse_stock_movements(product_id,product_name,kind,quantity_change,stock_after,reference,actor,note,event_key,created_at) SELECT id,name,'OPENING',stock_quantity,stock_quantity,'TEST-OPENING','SYSTEM','Test fixture',CONCAT('opening:',id),UTC_TIMESTAMP() FROM products;
