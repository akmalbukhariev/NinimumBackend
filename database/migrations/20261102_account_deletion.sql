-- Account deletion: the reasons a customer ticks, and a one-way hash of each deleted number (no phone stored),
-- so a number that comes back after deletion doesn't get the promo welcome month again.
CREATE TABLE IF NOT EXISTS user_delete_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  reasons VARCHAR(500) NOT NULL,
  created_at DATETIME NOT NULL,
  KEY idx_user_delete_feedback_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS account_deletions (
  user_id BIGINT NOT NULL PRIMARY KEY,
  phone_hash CHAR(64) NOT NULL,
  deleted_at DATETIME NOT NULL,
  KEY idx_account_deletions_phone (phone_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
