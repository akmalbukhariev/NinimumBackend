-- Courier GPS V1b: background tracking from the courier phone while "Ishga tayyor" is on.
-- Points arrive in batches (the phone keeps them while offline). Kept 90 days (a daily job deletes older ones).
-- Idempotent. The backend works before this migration (uploads are ignored, the map is empty).
CREATE TABLE IF NOT EXISTS courier_locations (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  worker_id BIGINT NOT NULL,
  recorded_at DATETIME(3) NOT NULL,
  received_at DATETIME NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  accuracy_m DOUBLE NULL,
  speed_mps DOUBLE NULL,
  battery_pct TINYINT NULL,
  is_mock TINYINT(1) NOT NULL DEFAULT 0,
  UNIQUE KEY uq_courier_point (worker_id, recorded_at),
  INDEX ix_courier_time (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS courier_last_location (
  worker_id BIGINT NOT NULL PRIMARY KEY,
  recorded_at DATETIME(3) NOT NULL,
  received_at DATETIME NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  accuracy_m DOUBLE NULL,
  speed_mps DOUBLE NULL,
  battery_pct TINYINT NULL,
  is_mock TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
