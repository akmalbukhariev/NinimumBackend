-- Q-16 (TZ v10 §10): period closing. Each row is one close/reopen; the latest row is the current "closed until" date (NULL = nothing closed).
-- Needs 20261021_strict_rules.sql. Safe to run more than once.
CREATE TABLE IF NOT EXISTS period_closings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  closed_until DATE NULL,
  reason VARCHAR(500) NULL,
  changed_by VARCHAR(100) NOT NULL,
  changed_at DATETIME NOT NULL
);
INSERT IGNORE INTO strict_rules(code,staff_enabled,owner_enabled) VALUES ('Q-16',1,1);
