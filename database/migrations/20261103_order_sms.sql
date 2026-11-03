-- Customer SMS about an order (on the way, delivered). One row per order and kind, so nothing is sent twice.
CREATE TABLE IF NOT EXISTS order_sms (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  phone VARCHAR(40) NOT NULL,
  text VARCHAR(300) NOT NULL,
  status VARCHAR(20) NOT NULL,
  error VARCHAR(300) NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uq_order_sms (order_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
