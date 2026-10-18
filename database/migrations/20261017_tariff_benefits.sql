-- Existing tariffs retain their subscription-price behavior until explicit product rules are enabled.
ALTER TABLE tariff_plans
 ADD COLUMN IF NOT EXISTS use_product_rules BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN IF NOT EXISTS free_delivery BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN IF NOT EXISTS recurring_order_days INT NULL;
CREATE TABLE IF NOT EXISTS tariff_product_benefits (
 tariff_id BIGINT NOT NULL, product_id BIGINT NOT NULL, discount_percent INT NOT NULL,
 PRIMARY KEY(tariff_id,product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS tariff_benefit_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tariff_id BIGINT NOT NULL,
 changed_by VARCHAR(160) NOT NULL, changed_at DATETIME NOT NULL,
 before_json MEDIUMTEXT NOT NULL, after_json MEDIUMTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
