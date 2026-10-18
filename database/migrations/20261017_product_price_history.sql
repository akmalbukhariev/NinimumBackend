-- New price edits only. Historical prices before this migration are not invented.
CREATE TABLE IF NOT EXISTS product_price_history (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 product_id BIGINT NOT NULL,
 field_name VARCHAR(30) NOT NULL,
 old_amount DECIMAL(14,2) NULL,
 new_amount DECIMAL(14,2) NULL,
 reason VARCHAR(500) NOT NULL,
 changed_by VARCHAR(160) NOT NULL,
 changed_at DATETIME NOT NULL,
 INDEX product_price_edits(product_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
