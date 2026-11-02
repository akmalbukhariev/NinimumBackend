-- Where the product sits in the warehouse, typed freely in web admin (e.g. "1-stol, 2-ustun, 3-qator"). Shown on the warehouse packing screen.
ALTER TABLE products ADD COLUMN IF NOT EXISTS storage_location VARCHAR(160) NULL AFTER storage_temperature;
