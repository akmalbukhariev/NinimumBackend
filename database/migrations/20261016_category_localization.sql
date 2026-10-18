-- Apply before deploying category localization backend/admin/mobile.
ALTER TABLE categories ADD COLUMN IF NOT EXISTS name_uz VARCHAR(255) NULL;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS name_ru VARCHAR(255) NULL;
-- Leave translations empty until explicitly supplied; legacy name remains fallback.
