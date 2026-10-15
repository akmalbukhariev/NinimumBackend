-- Supplier register and invoice links. No existing receipts are reassigned.
CREATE TABLE IF NOT EXISTS suppliers (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(160) NOT NULL,
 contact_name VARCHAR(160) NOT NULL DEFAULT '',
 phone VARCHAR(40) NOT NULL DEFAULT '',
 tax_id VARCHAR(40) NOT NULL DEFAULT '',
 address VARCHAR(500) NOT NULL DEFAULT '',
 payment_term_days INT NOT NULL DEFAULT 0,
 note VARCHAR(1000) NOT NULL DEFAULT '',
 is_active BOOLEAN NOT NULL DEFAULT TRUE,
 created_by VARCHAR(160) NOT NULL,
 created_at DATETIME NOT NULL,
 updated_at DATETIME NOT NULL,
 INDEX supplier_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS supplier_payment_records (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 supplier_id BIGINT NOT NULL,
 receipt_id BIGINT NOT NULL,
 amount DECIMAL(16,2) NOT NULL,
 reference VARCHAR(160) NOT NULL,
 paid_date DATE NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'RECORDED',
 created_by VARCHAR(160) NOT NULL,
 created_at DATETIME NOT NULL,
 voided_by VARCHAR(160) NULL,
 void_reason VARCHAR(500) NULL,
 voided_at DATETIME NULL,
 UNIQUE KEY supplier_payment_reference(supplier_id,reference),
 INDEX supplier_payment_receipt(receipt_id),
 CONSTRAINT supplier_payment_supplier FOREIGN KEY(supplier_id) REFERENCES suppliers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS supplier_invoices (
 receipt_id BIGINT NOT NULL PRIMARY KEY,
 supplier_id BIGINT NOT NULL,
 invoice_number VARCHAR(80) NOT NULL,
 invoice_amount DECIMAL(16,2) NOT NULL,
 invoice_date DATE NOT NULL,
 due_date DATE NOT NULL,
 created_by VARCHAR(160) NOT NULL,
 created_at DATETIME NOT NULL,
 INDEX supplier_due(supplier_id,due_date),
 UNIQUE KEY supplier_invoice_number(supplier_id,invoice_number),
 CONSTRAINT supplier_invoice_supplier FOREIGN KEY(supplier_id) REFERENCES suppliers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
