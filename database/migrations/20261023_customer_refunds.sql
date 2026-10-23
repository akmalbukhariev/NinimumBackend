-- K5 (TZ v11): refunds the owner made outside Payme (partial returns can't be cancelled in Payme Business).
-- One row per order and kind; the money place it was paid from shows as money out on the Pul page. Safe to run more than once.
CREATE TABLE IF NOT EXISTS customer_refunds (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  amount DECIMAL(16,2) NOT NULL,
  account_id BIGINT NOT NULL,
  refund_date DATE NOT NULL,
  note VARCHAR(500) NOT NULL,
  created_by VARCHAR(160) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY customer_refund_order (order_id, kind)
);
