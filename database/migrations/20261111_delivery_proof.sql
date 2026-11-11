-- Courier GPS V1a: proof of safe delivery.
-- 1) Every courier action (accept, on the way, delivered, failed, partial return) keeps where the phone was.
-- 2) A photo is required when the courier presses "Yetkazildi".
-- 3) The customer confirms "Mahsulotni oldim" or reports "Muammo bor"; an admin can mark the delivery as checked.
-- Idempotent. The backend works before this migration (it skips what isn't there), but run it before the new courier app is used.
ALTER TABLE delivery_job_tracking
  ADD COLUMN IF NOT EXISTS latitude DOUBLE NULL,
  ADD COLUMN IF NOT EXISTS longitude DOUBLE NULL,
  ADD COLUMN IF NOT EXISTS accuracy_m DOUBLE NULL,
  ADD COLUMN IF NOT EXISTS is_mock TINYINT(1) NULL,
  ADD COLUMN IF NOT EXISTS location_error VARCHAR(40) NULL,
  ADD COLUMN IF NOT EXISTS distance_m INT NULL;

CREATE TABLE IF NOT EXISTS delivery_proof_photos (
  delivery_job_id BIGINT NOT NULL PRIMARY KEY,
  order_id BIGINT NOT NULL,
  actor VARCHAR(160) NOT NULL,
  photo_jpeg MEDIUMBLOB NOT NULL,
  upload_sha256 CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX ix_delivery_proof_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS delivery_confirmations (
  order_id BIGINT NOT NULL PRIMARY KEY,
  received_at DATETIME NULL,
  problem VARCHAR(500) NULL,
  problem_at DATETIME NULL,
  checked_at DATETIME NULL,
  checked_by VARCHAR(160) NULL,
  check_note VARCHAR(500) NULL,
  updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
