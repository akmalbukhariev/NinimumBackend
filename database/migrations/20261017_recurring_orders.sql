-- Recurring merchandise drafts only. Does not authorize automatic debit or delivery.
CREATE TABLE IF NOT EXISTS recurring_orders (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, source_order_id BIGINT NOT NULL,
 max_total BIGINT NOT NULL, interval_days INT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 next_run_at DATETIME NOT NULL, last_order_id BIGINT NULL,
 consented_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 UNIQUE KEY recurring_source(user_id,source_order_id),
 INDEX recurring_due(status,next_run_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS recurring_order_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 recurring_id BIGINT NOT NULL,event VARCHAR(30) NOT NULL,
 order_id BIGINT NULL,note VARCHAR(500) NULL,occurred_at DATETIME NOT NULL,
 INDEX recurring_event(recurring_id,id)
) ENGINE=InnoDB;
