-- Historical receipts remain unchanged; metadata is required on new receipts.
ALTER TABLE warehouse_receipt_items
 ADD COLUMN IF NOT EXISTS unit_price DECIMAL(14,2) NULL,
 ADD COLUMN IF NOT EXISTS manufacture_date DATE NULL,
 ADD COLUMN IF NOT EXISTS expiry_date DATE NULL,
 ADD COLUMN IF NOT EXISTS input_unit VARCHAR(10) NOT NULL DEFAULT 'PIECE',
 ADD COLUMN IF NOT EXISTS input_quantity INT NULL,
 ADD COLUMN IF NOT EXISTS units_per_block INT NOT NULL DEFAULT 1,
 ADD COLUMN IF NOT EXISTS batch_number VARCHAR(100) NULL,
 ADD COLUMN IF NOT EXISTS metadata_updated_by VARCHAR(160) NULL,
 ADD COLUMN IF NOT EXISTS metadata_updated_at DATETIME NULL;
ALTER TABLE warehouse_receipts ADD COLUMN IF NOT EXISTS invoice_image MEDIUMBLOB NULL;
