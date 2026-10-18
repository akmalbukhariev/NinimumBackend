-- TEST FIXTURE ONLY. Table definitions from read-only live schema inspection 2026-10-17.
CREATE TABLE IF NOT EXISTS `warehouse_app_schema` (
  `id` int(11) NOT NULL,
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_workers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `worker_code` varchar(50) NOT NULL,
  `full_name` varchar(120) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `worker_code` (`worker_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_app_sessions` (
  `worker_code` varchar(50) NOT NULL,
  `token_hash` char(64) NOT NULL,
  PRIMARY KEY (`worker_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_preparations` (
  `order_id` bigint(20) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'WAITING',
  `worker_code` varchar(50) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `ready_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`order_id`),
  KEY `idx_preparation_worker` (`worker_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_preparation_items` (
  `order_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `barcode` varchar(120) DEFAULT NULL,
  `required_quantity` int(11) NOT NULL,
  `checked_quantity` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`order_id`,`product_id`),
  CONSTRAINT `fk_preparation_item` FOREIGN KEY (`order_id`) REFERENCES `warehouse_preparations` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_preparation_events` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL,
  `actor` varchar(50) NOT NULL,
  `kind` varchar(30) NOT NULL,
  `product_id` bigint(20) DEFAULT NULL,
  `quantity` int(11) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_preparation_events` (`order_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_order_returns` (
  `order_id` bigint(20) NOT NULL,
  `worker_code` varchar(50) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'CHECKING',
  `received_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `warehouse_order_return_items` (
  `order_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `barcode` varchar(120) DEFAULT NULL,
  `required_quantity` int(11) NOT NULL,
  `checked_quantity` int(11) NOT NULL DEFAULT 0,
  `restocked_quantity` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`order_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO warehouse_app_schema VALUES(1,1);
