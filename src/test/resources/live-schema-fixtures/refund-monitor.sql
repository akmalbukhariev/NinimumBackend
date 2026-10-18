-- Test-only table definition recovered from live schema, no production rows.
CREATE TABLE `payment_refund_checks` (
  `payment_id` bigint(20) NOT NULL,
  `checked_at` datetime NOT NULL,
  `last_success_at` datetime DEFAULT NULL,
  `receipt_state` int(11) DEFAULT NULL,
  `error_code` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
