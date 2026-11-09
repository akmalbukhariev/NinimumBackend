-- TZ v13 item 9: how many days before a batch's expiry the "Muddati yaqin" alert starts, per product.
-- Empty = the general value in inventory_settings.expiry_warning_days. Run BEFORE the new backend (product save writes it).
ALTER TABLE products ADD COLUMN IF NOT EXISTS expiry_warning_days INT NULL AFTER shelf_life_days;
