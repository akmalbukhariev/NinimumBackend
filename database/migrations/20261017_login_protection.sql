-- Apply before deploying backend. No passwords or tokens stored here.
CREATE TABLE IF NOT EXISTS login_attempt_state (
 realm VARCHAR(20) NOT NULL,
 identity_key CHAR(64) NOT NULL,
 failures INT NOT NULL DEFAULT 0,
 locked_until DATETIME NULL,
 updated_at DATETIME NOT NULL,
 PRIMARY KEY(realm,identity_key)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS login_attempt_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 realm VARCHAR(20) NOT NULL,
 identity_key CHAR(64) NOT NULL,
 outcome VARCHAR(20) NOT NULL,
 occurred_at DATETIME NOT NULL,
 INDEX ix_login_audit_time(occurred_at),
 INDEX ix_login_audit_identity(realm,identity_key,occurred_at)
) ENGINE=InnoDB;
