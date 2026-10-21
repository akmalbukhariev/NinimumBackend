-- M14 (TZ v10 §10): web admin staff roles and per-employee permissions. Safe to run more than once.
-- role stays ADMIN / SUPER_ADMIN (login authority). staff_role picks the default permission set for ADMIN accounts;
-- the existing value 'ADMIN' keeps exactly the pages restricted administrators had before.
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS staff_role VARCHAR(30) NOT NULL DEFAULT 'ADMIN';
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS permissions_added TEXT NULL;
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS permissions_removed TEXT NULL;
CREATE TABLE IF NOT EXISTS admin_access_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 admin_id BIGINT NOT NULL,
 changed_by VARCHAR(100) NOT NULL,
 old_value TEXT NOT NULL,
 new_value TEXT NOT NULL,
 changed_at DATETIME NOT NULL,
 INDEX ix_admin_access_audit_admin(admin_id,changed_at)
) ENGINE=InnoDB;
