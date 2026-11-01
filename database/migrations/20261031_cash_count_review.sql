-- TZ v12 B3: a cash count with a difference waits for approval, and any count can be cancelled with a reason. Safe to run more than once.
-- Needs 20261022_finance.sql. Run BEFORE the new backend: it reads cash_counts.status.
-- status: POSTED (no review needed, also every older count), PENDING (difference, waiting), APPROVED (document_id = shortage or surplus document), VOIDED.
ALTER TABLE cash_counts
 ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
 ADD COLUMN IF NOT EXISTS document_id BIGINT NULL,
 ADD COLUMN IF NOT EXISTS approved_by VARCHAR(160) NULL,
 ADD COLUMN IF NOT EXISTS approved_at DATETIME NULL,
 ADD COLUMN IF NOT EXISTS voided_by VARCHAR(160) NULL,
 ADD COLUMN IF NOT EXISTS voided_at DATETIME NULL,
 ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500) NULL;
INSERT IGNORE INTO expense_types(name) VALUES ('Kassa kamomadi');
