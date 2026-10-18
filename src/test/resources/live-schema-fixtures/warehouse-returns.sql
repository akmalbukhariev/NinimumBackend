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