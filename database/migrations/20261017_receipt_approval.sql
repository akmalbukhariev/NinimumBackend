CREATE TABLE IF NOT EXISTS warehouse_receipt_counts (
 receipt_id BIGINT NOT NULL, product_id BIGINT NOT NULL, counted_quantity INT NOT NULL,
 counted_by VARCHAR(160) NOT NULL, counted_at DATETIME NOT NULL,
 PRIMARY KEY(receipt_id,product_id)
);
