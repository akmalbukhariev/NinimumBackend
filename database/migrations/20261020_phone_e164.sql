-- M11: store customer phone numbers in E.164 (+998XXXXXXXXX). Safe to run more than once.
-- A number whose E.164 form would collide with another account (a duplicate) is left unchanged;
-- database/checks/phone_duplicates.sql lists those, and the owner merges them in web admin (Customers → Duplicates).
CREATE TABLE IF NOT EXISTS customer_merges (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 kept_user_id BIGINT NOT NULL,
 merged_user_id BIGINT NOT NULL,
 merged_phone VARCHAR(20) NOT NULL,
 moved_rows TEXT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 merged_by VARCHAR(100) NOT NULL,
 merged_at DATETIME NOT NULL,
 UNIQUE KEY uk_customer_merges_merged(merged_user_id),
 INDEX ix_customer_merges_kept(kept_user_id)
) ENGINE=InnoDB;

DROP TEMPORARY TABLE IF EXISTS tmp_phone_e164;
CREATE TEMPORARY TABLE tmp_phone_e164 AS
SELECT id,phone_number,
  CASE
    WHEN phone_number REGEXP '^[0-9]{9}$' THEN CONCAT('+998',phone_number)
    WHEN REGEXP_REPLACE(phone_number,'[ ()-]','') REGEXP '^\\+?998[0-9]{9}$' THEN CONCAT('+',RIGHT(REGEXP_REPLACE(phone_number,'[^0-9]',''),12))
  END e164
FROM users;
DELETE FROM tmp_phone_e164 WHERE e164 IS NULL;

-- A target form claimed by exactly one account (counting accounts already stored in E.164).
DROP TEMPORARY TABLE IF EXISTS tmp_phone_e164_unique;
CREATE TEMPORARY TABLE tmp_phone_e164_unique AS
SELECT e164 FROM tmp_phone_e164 GROUP BY e164 HAVING COUNT(*)=1;

UPDATE users u
JOIN tmp_phone_e164 t ON t.id=u.id
JOIN tmp_phone_e164_unique q ON q.e164=t.e164
SET u.phone_number=t.e164
WHERE u.phone_number<>t.e164;

DROP TEMPORARY TABLE IF EXISTS tmp_phone_e164_unique;
DROP TEMPORARY TABLE IF EXISTS tmp_phone_e164;
