-- K3: orders.status is the one order status. Every change is recorded in order_status_history,
-- and only the allowed forward steps are accepted. Going back is owner-only and needs a reason
-- (sources OWNER_CORRECTION and OWNER_RESET). Safe to run more than once.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS status_actor VARCHAR(255),ADD COLUMN IF NOT EXISTS status_source VARCHAR(40),ADD COLUMN IF NOT EXISTS status_reason VARCHAR(500);
CREATE TABLE IF NOT EXISTS `order_status_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL,
  `from_status` varchar(32) DEFAULT NULL,
  `to_status` varchar(32) NOT NULL,
  `actor` varchar(255) NOT NULL,
  `source` varchar(40) NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `changed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_status_history` (`order_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
DROP TRIGGER IF EXISTS orders_status_created;
DROP TRIGGER IF EXISTS orders_status_changed;
DELIMITER $$
CREATE TRIGGER orders_status_created AFTER INSERT ON orders FOR EACH ROW BEGIN
    INSERT INTO order_status_history(order_id,from_status,to_status,actor,source,changed_at)
    VALUES(NEW.id,NULL,NEW.status,CONCAT('CUSTOMER:',NEW.user_id),'CREATED',UTC_TIMESTAMP(6));
END$$
CREATE TRIGGER orders_status_changed BEFORE UPDATE ON orders FOR EACH ROW BEGIN
    IF NOT (OLD.status <=> NEW.status) THEN
        IF COALESCE(NEW.status_source,'') IN ('OWNER_CORRECTION','OWNER_RESET') THEN
            IF NULLIF(TRIM(NEW.status_reason),'') IS NULL THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORDER_STATUS_REASON_REQUIRED';
            END IF;
        ELSEIF CONCAT(OLD.status,'>',NEW.status) NOT IN (
            'PENDING>CONFIRMED','PENDING>CANCELLED',
            'CONFIRMED>PREPARING','CONFIRMED>READY','CONFIRMED>CANCELLED',
            'PREPARING>READY','PREPARING>CANCELLED',
            'READY>ON_THE_WAY','READY>RETURNING','READY>CANCELLED',
            'ON_THE_WAY>DELIVERED','ON_THE_WAY>RETURNING',
            'RETURNING>RETURNED') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORDER_STATUS_TRANSITION_NOT_ALLOWED';
        END IF;
        INSERT INTO order_status_history(order_id,from_status,to_status,actor,source,reason,changed_at)
        VALUES(NEW.id,OLD.status,NEW.status,COALESCE(NEW.status_actor,'SYSTEM'),
            COALESCE(NEW.status_source,'SYSTEM'),NEW.status_reason,UTC_TIMESTAMP(6));
    END IF;
    -- Do not reuse a previous actor for a later maintenance update.
    SET NEW.status_actor=NULL, NEW.status_source=NULL, NEW.status_reason=NULL;
END$$
DELIMITER ;
