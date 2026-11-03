-- Push notifications (Firebase). One row per phone; the same phone signing in to another account moves its row.
CREATE TABLE IF NOT EXISTS push_tokens (
  token VARCHAR(400) NOT NULL PRIMARY KEY,
  app VARCHAR(20) NOT NULL,
  owner VARCHAR(80) NOT NULL,
  updated_at DATETIME NOT NULL,
  KEY idx_push_tokens_owner (app, owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Each order status is announced once.
CREATE TABLE IF NOT EXISTS push_log (
  order_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (order_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
