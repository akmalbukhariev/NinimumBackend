-- Test-only table definitions recovered from the live schema; no production data.
CREATE TABLE `order_timer_settings` (
  `id` int(11) NOT NULL,
  `expiry_enabled` tinyint(4) NOT NULL DEFAULT 0,
  `unpaid_minutes` int(11) NOT NULL DEFAULT 30,
  `queue_minutes` int(11) NOT NULL DEFAULT 30,
  `preparation_minutes` int(11) NOT NULL DEFAULT 30,
  `courier_wait_minutes` int(11) NOT NULL DEFAULT 30,
  `delivery_minutes` int(11) NOT NULL DEFAULT 120,
  `question_minutes` int(11) NOT NULL DEFAULT 1440,
  `working_days` int(11) NOT NULL DEFAULT 127,
  `working_start` varchar(5) NOT NULL DEFAULT '00:00',
  `working_end` varchar(5) NOT NULL DEFAULT '24:00',
  `updated_by` varchar(100) DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `last_scanned_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `order_problem_alerts` (
  `entity_type` varchar(20) NOT NULL,
  `entity_id` bigint(20) NOT NULL,
  `kind` varchar(40) NOT NULL,
  `order_status` varchar(30) DEFAULT NULL,
  `responsible` varchar(100) DEFAULT NULL,
  `state_since` datetime NOT NULL,
  `elapsed_minutes` bigint(20) NOT NULL,
  `opened_at` datetime NOT NULL,
  `resolved_at` datetime DEFAULT NULL,
  PRIMARY KEY (`entity_type`,`entity_id`,`kind`),
  KEY `resolved_at` (`resolved_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `order_problem_events` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(20) NOT NULL,
  `entity_id` bigint(20) NOT NULL,
  `kind` varchar(40) NOT NULL,
  `event` varchar(20) NOT NULL,
  `occurred_at` datetime NOT NULL,
  `actor` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `entity_type` (`entity_type`,`entity_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
INSERT INTO order_timer_settings(id) VALUES(1);
