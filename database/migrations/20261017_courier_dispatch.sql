-- Existing courier self-claim behavior remains until owner enables automatic dispatch.
CREATE TABLE IF NOT EXISTS courier_dispatch_settings (
 id INT NOT NULL PRIMARY KEY, enabled BOOLEAN NOT NULL DEFAULT FALSE,
 accept_minutes INT NOT NULL DEFAULT 5, last_worker_id BIGINT NOT NULL DEFAULT 0,
 updated_by VARCHAR(160) NULL, updated_at DATETIME NULL
) ENGINE=InnoDB;
INSERT IGNORE INTO courier_dispatch_settings(id) VALUES(1);
CREATE TABLE IF NOT EXISTS courier_dispatch_offers (
 job_id BIGINT NOT NULL PRIMARY KEY, worker_id BIGINT NULL,
 status VARCHAR(20) NOT NULL, attempt INT NOT NULL,
 offered_at DATETIME NULL, expires_at DATETIME NULL, updated_at DATETIME NOT NULL,
 INDEX dispatch_worker(worker_id,status),INDEX dispatch_deadline(status,expires_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS courier_dispatch_history (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 job_id BIGINT NOT NULL, attempt INT NOT NULL, worker_id BIGINT NULL,
 event VARCHAR(30) NOT NULL, actor VARCHAR(160) NOT NULL,
 occurred_at DATETIME NOT NULL, note VARCHAR(500) NULL,
 INDEX dispatch_history_job(job_id,id)
) ENGINE=InnoDB;
