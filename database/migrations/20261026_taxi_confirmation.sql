-- Taxi delivery: the customer confirms receipt ("Mahsulotni oldim") or reports a problem ("Muammo bor").
-- Needs 20261026_taxi_delivery.sql. Safe to run more than once.
ALTER TABLE taxi_deliveries
  ADD COLUMN IF NOT EXISTS confirmed_by_customer TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS customer_problem VARCHAR(500) NULL,
  ADD COLUMN IF NOT EXISTS customer_problem_at DATETIME NULL;
