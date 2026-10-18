CREATE TABLE IF NOT EXISTS `user_addresses` (
  `address_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `receiver_name` varchar(255) NOT NULL DEFAULT '',
  `receiver_phone` varchar(50) NOT NULL DEFAULT '',
  `address` text NOT NULL,
  `detail_address` text DEFAULT NULL,
  `default_yn` char(1) NOT NULL DEFAULT 'N',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`address_id`),
  KEY `idx_user_addresses_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;