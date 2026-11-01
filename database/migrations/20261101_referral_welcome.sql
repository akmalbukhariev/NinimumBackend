-- Promo period option: every new customer gets the free month, with or without a friend's code. OFF until the owner turns it on.
ALTER TABLE referral_settings ADD COLUMN IF NOT EXISTS welcome_for_all TINYINT(1) NOT NULL DEFAULT 0 AFTER friends_needed;
