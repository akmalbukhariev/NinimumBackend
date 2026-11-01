-- Promo period: tariffs hidden, customers invite friends with a personal code. Nothing changes until the owner turns promo_mode on.
CREATE TABLE IF NOT EXISTS referral_settings (
  id INT NOT NULL PRIMARY KEY,
  promo_mode TINYINT(1) NOT NULL DEFAULT 0,
  friends_needed INT NOT NULL DEFAULT 5,
  tariff_plan_id BIGINT NULL,
  updated_by VARCHAR(100) NULL,
  updated_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
INSERT IGNORE INTO referral_settings(id) VALUES(1);

CREATE TABLE IF NOT EXISTS referral_codes (
  user_id BIGINT NOT NULL PRIMARY KEY,
  code VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uq_referral_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- One row per new customer: a code can be entered once, at registration.
CREATE TABLE IF NOT EXISTS referrals (
  invited_user_id BIGINT NOT NULL PRIMARY KEY,
  inviter_user_id BIGINT NOT NULL,
  code VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL,
  KEY idx_referrals_inviter (inviter_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- WELCOME = the new friend's free month; INVITER = the month earned by inviting. Each at most once per customer.
CREATE TABLE IF NOT EXISTS referral_rewards (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  user_tariff_id BIGINT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uq_referral_reward (user_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
