-- SMS login: a phone that confirmed an SMS code once gets a key (stored only as a hash), so the app's automatic
-- login at start-up doesn't need a new SMS. Unused keys stop working after 180 days.
CREATE TABLE IF NOT EXISTS trusted_devices (
  token_hash CHAR(64) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  last_used_at DATETIME NOT NULL,
  KEY idx_trusted_devices_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
