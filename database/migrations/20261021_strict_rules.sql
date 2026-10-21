-- M14 (TZ v10 §10): switchable strictness rules, separately for staff and for the owner. Safe to run more than once.
-- Only switchable rules are stored; defaults keep today's behaviour (see StrictRules.RULES).
CREATE TABLE IF NOT EXISTS strict_rules (
 code VARCHAR(10) NOT NULL PRIMARY KEY,
 staff_enabled TINYINT(1) NOT NULL,
 owner_enabled TINYINT(1) NOT NULL,
 updated_by VARCHAR(100) NULL,
 updated_at DATETIME NULL
) ENGINE=InnoDB;
INSERT IGNORE INTO strict_rules(code,staff_enabled,owner_enabled) VALUES
 ('Q-5',1,1),('Q-6',1,0),('Q-8',1,1),('Q-9',1,1),('Q-10',1,0),('Q-14',0,0);
CREATE TABLE IF NOT EXISTS strict_rules_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(10) NOT NULL,
 changed_by VARCHAR(100) NOT NULL,
 old_value VARCHAR(100) NOT NULL,
 new_value VARCHAR(100) NOT NULL,
 changed_at DATETIME NOT NULL,
 INDEX ix_strict_rules_audit(code,changed_at)
) ENGINE=InnoDB;
