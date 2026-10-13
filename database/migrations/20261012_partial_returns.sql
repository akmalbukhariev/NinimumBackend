-- Apply BEFORE deploying partial-return changes. No existing orders or returns are changed.
-- A partial return completes delivery, then waits for physical warehouse receipt.
-- Money is owed for returned goods; delivery fee is retained. This does not execute a refund.
CREATE TABLE IF NOT EXISTS delivery_partial_returns (
  order_id BIGINT NOT NULL PRIMARY KEY,
  delivery_job_id BIGINT NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'RETURNING',
  reason VARCHAR(500) NOT NULL,
  actor VARCHAR(100) NOT NULL,
  refund_amount DECIMAL(16,2) NOT NULL,
  refund_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL,
  received_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS delivery_partial_return_items (
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(255) NOT NULL,
  quantity INT NOT NULL,
  refund_amount DECIMAL(16,2) NOT NULL,
  PRIMARY KEY (order_id,product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
