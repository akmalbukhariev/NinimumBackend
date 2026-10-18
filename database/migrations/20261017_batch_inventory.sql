-- Opt-in per product through approved opening stock. No legacy bins, expiry or costs invented.
CREATE TABLE IF NOT EXISTS inventory_products (
 product_id BIGINT NOT NULL PRIMARY KEY,opened_by VARCHAR(160) NOT NULL,opened_at DATETIME NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS inventory_batches (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,product_id BIGINT NOT NULL,location_id BIGINT NOT NULL,
 receipt_id BIGINT NULL,batch_number VARCHAR(100) NULL,manufacture_date DATE NULL,expiry_date DATE NULL,
 unit_cost DECIMAL(16,4) NULL,physical_quantity BIGINT NOT NULL DEFAULT 0,reserved_quantity BIGINT NOT NULL DEFAULT 0,accounted_quantity BIGINT NOT NULL DEFAULT 0,
 quarantined BOOLEAN NOT NULL DEFAULT FALSE,reference VARCHAR(160) NOT NULL,
 created_by VARCHAR(160) NOT NULL,created_at DATETIME NOT NULL,
 INDEX inventory_fefo(product_id,expiry_date,id),INDEX inventory_bin(location_id,product_id),INDEX inventory_receipt(receipt_id,product_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS inventory_allocations (
 order_id BIGINT NOT NULL,batch_id BIGINT NOT NULL,product_id BIGINT NOT NULL,
 quantity BIGINT NOT NULL,issued_quantity BIGINT NOT NULL DEFAULT 0,released_quantity BIGINT NOT NULL DEFAULT 0,
 unit_cost DECIMAL(16,4) NOT NULL,created_at DATETIME NOT NULL,
 PRIMARY KEY(order_id,batch_id),INDEX inventory_allocation_product(product_id,order_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS inventory_documents (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,kind VARCHAR(30) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 product_id BIGINT NULL,location_id BIGINT NULL,reason VARCHAR(500) NOT NULL,payload LONGTEXT NOT NULL,
 expected_stock BIGINT NULL,created_by VARCHAR(160) NOT NULL,created_at DATETIME NOT NULL,
 counted_by VARCHAR(160) NULL,counted_at DATETIME NULL,approved_by VARCHAR(160) NULL,approved_at DATETIME NULL,cost_total DECIMAL(18,4) NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS inventory_bin_locks (
 location_id BIGINT NOT NULL PRIMARY KEY,document_id BIGINT NOT NULL,created_at DATETIME NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS inventory_movements (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,batch_id BIGINT NOT NULL,product_id BIGINT NOT NULL,location_id BIGINT NOT NULL,product_name VARCHAR(255) NOT NULL,location_code VARCHAR(40) NOT NULL,
 physical_change BIGINT NOT NULL,reserved_change BIGINT NOT NULL,available_change BIGINT NOT NULL DEFAULT 0,available_after BIGINT NOT NULL DEFAULT 0,physical_after BIGINT NOT NULL,reserved_after BIGINT NOT NULL,
 kind VARCHAR(30) NOT NULL,reference VARCHAR(160) NOT NULL,actor VARCHAR(160) NOT NULL,reason VARCHAR(500) NULL,
 device_code VARCHAR(100) NULL,created_at DATETIME NOT NULL,
 INDEX inventory_movement_product(product_id,id),INDEX inventory_movement_batch(batch_id,id)
) ENGINE=InnoDB;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS unit_cost_snapshot DECIMAL(16,4) NULL,
 ADD COLUMN IF NOT EXISTS cost_recorded_at DATETIME NULL;
CREATE TABLE IF NOT EXISTS inventory_settings (
 id INT NOT NULL PRIMARY KEY,writeoff_warning_percent DECIMAL(5,2) NOT NULL DEFAULT 0.30,
 expiry_warning_days INT NOT NULL DEFAULT 30,updated_by VARCHAR(160) NULL,updated_at DATETIME NULL
) ENGINE=InnoDB;
INSERT IGNORE INTO inventory_settings(id) VALUES(1);
