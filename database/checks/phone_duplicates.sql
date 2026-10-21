-- M11 read-only check: customer accounts that are the same phone number written in different forms.
-- Changes nothing. Each row is one account; accounts with the same e164 value are duplicates.
SELECT d.e164,u.id user_id,u.phone_number,CONCAT_WS(' ',u.first_name,u.last_name) customer_name,u.status,u.is_active,
  DATE_FORMAT(CONVERT_TZ(u.created_at,'+00:00','+05:00'),'%d.%m.%Y %H:%i') created_tashkent,
  (SELECT COUNT(*) FROM orders o WHERE o.user_id=u.id) orders
FROM users u
JOIN (
  SELECT id,CASE
    WHEN phone_number REGEXP '^[0-9]{9}$' THEN CONCAT('+998',phone_number)
    WHEN REGEXP_REPLACE(phone_number,'[ ()-]','') REGEXP '^\\+?998[0-9]{9}$' THEN CONCAT('+',RIGHT(REGEXP_REPLACE(phone_number,'[^0-9]',''),12))
  END e164 FROM users
) d ON d.id=u.id
WHERE d.e164 IN (
  SELECT e164 FROM (
    SELECT CASE
      WHEN phone_number REGEXP '^[0-9]{9}$' THEN CONCAT('+998',phone_number)
      WHEN REGEXP_REPLACE(phone_number,'[ ()-]','') REGEXP '^\\+?998[0-9]{9}$' THEN CONCAT('+',RIGHT(REGEXP_REPLACE(phone_number,'[^0-9]',''),12))
    END e164 FROM users WHERE is_active=1
  ) x WHERE e164 IS NOT NULL GROUP BY e164 HAVING COUNT(*)>1
)
ORDER BY d.e164,u.id;
