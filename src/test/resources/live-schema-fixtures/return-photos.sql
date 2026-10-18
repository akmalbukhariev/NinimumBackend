-- TEST FIXTURE ONLY. Table definitions from read-only live schema inspection 2026-10-17.
CREATE TABLE IF NOT EXISTS `delivery_return_photos` (
  `delivery_job_id` bigint(20) NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `actor` varchar(100) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `photo_jpeg` mediumblob NOT NULL,
  `upload_sha256` char(64) NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`delivery_job_id`),
  KEY `idx_return_photo_order` (`order_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

