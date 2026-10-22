-- Q-3 / Q-4 (TZ v10 §5.2): receipt line condition with photo, and a note when the purchase price jumps. Safe to run more than once.
ALTER TABLE warehouse_receipt_items ADD COLUMN IF NOT EXISTS price_note VARCHAR(500) NULL;
-- counted_quantity stays the fit (sellable) quantity; unfit goods are recorded here and never become stock.
ALTER TABLE warehouse_receipt_counts
 ADD COLUMN IF NOT EXISTS unfit_quantity INT NOT NULL DEFAULT 0,
 ADD COLUMN IF NOT EXISTS unfit_condition VARCHAR(20) NULL,
 ADD COLUMN IF NOT EXISTS unfit_photo MEDIUMBLOB NULL;
