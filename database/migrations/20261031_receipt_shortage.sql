-- TZ v12 B5: a supplier delivered less than the invoice: goods are accepted as counted, the debt falls and a claim is recorded. Safe to run more than once.
-- Needs 20261017_receipt_approval.sql. Run BEFORE the new backend: it reads these columns and the table.
ALTER TABLE warehouse_receipt_counts
 ADD COLUMN IF NOT EXISTS shortage_quantity INT NOT NULL DEFAULT 0,
 ADD COLUMN IF NOT EXISTS shortage_reason VARCHAR(500) NULL;
-- status: OPEN, CANCELLED (the receipt was reversed). supplier_id is empty for a supplier outside the register.
CREATE TABLE IF NOT EXISTS supplier_claims (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 receipt_id BIGINT NOT NULL,
 supplier_id BIGINT NULL,
 product_id BIGINT NOT NULL,
 product_name VARCHAR(255) NOT NULL,
 quantity INT NOT NULL,
 unit_price DECIMAL(16,2) NULL,
 amount DECIMAL(16,2) NOT NULL DEFAULT 0,
 reason VARCHAR(500) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
 created_by VARCHAR(160) NOT NULL,
 created_at DATETIME NOT NULL,
 INDEX ix_supplier_claims_supplier(supplier_id,id),
 INDEX ix_supplier_claims_receipt(receipt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
