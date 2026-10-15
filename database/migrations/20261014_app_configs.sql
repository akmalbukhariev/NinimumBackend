-- Application settings required by the admin and customer configuration queries.
-- Repeatable: existing settings are preserved.
CREATE TABLE IF NOT EXISTS app_configs (
    config_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    app_version VARCHAR(50) NOT NULL DEFAULT '',
    force_update_yn CHAR(1) NOT NULL DEFAULT 'N',
    maintenance_yn CHAR(1) NOT NULL DEFAULT 'N',
    maintenance_message TEXT NULL,
    terms_url VARCHAR(2048) NULL,
    privacy_url VARCHAR(2048) NULL,
    use_yn CHAR(1) NOT NULL DEFAULT 'Y'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO app_configs (app_version, force_update_yn, maintenance_yn, use_yn)
SELECT '', 'N', 'N', 'Y'
WHERE NOT EXISTS (SELECT 1 FROM app_configs);
