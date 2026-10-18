-- Preserve existing locations as bins; no inventory is assigned to invented locations.
ALTER TABLE warehouse_locations MODIFY COLUMN type VARCHAR(20) NOT NULL DEFAULT 'SHELF';
ALTER TABLE warehouse_locations ADD COLUMN IF NOT EXISTS hierarchy_level VARCHAR(10) NOT NULL DEFAULT 'BIN';
ALTER TABLE warehouse_locations ADD COLUMN IF NOT EXISTS parent_id BIGINT NULL;
