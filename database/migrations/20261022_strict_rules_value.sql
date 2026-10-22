-- Q-3 / Q-4 become switchable; Q-4 has a setting (price difference percent). Needs 20261021_strict_rules.sql. Safe to run more than once.
ALTER TABLE strict_rules ADD COLUMN IF NOT EXISTS value VARCHAR(50) NULL;
INSERT IGNORE INTO strict_rules(code,staff_enabled,owner_enabled,value) VALUES ('Q-3',1,0,NULL),('Q-4',1,0,'20');
