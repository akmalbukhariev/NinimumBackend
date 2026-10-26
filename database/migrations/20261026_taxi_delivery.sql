-- Taxi delivery: the customer can choose a fast delivery by an outside taxi and pays the driver directly.
-- Couriers never see these orders (no delivery job is created); an operator books the taxi in web admin.
-- Safe to run more than once. Run BEFORE the new backend (orders are inserted with delivery_method).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_method VARCHAR(20) NOT NULL DEFAULT 'COURIER';
CREATE TABLE IF NOT EXISTS taxi_deliveries (
  order_id BIGINT PRIMARY KEY,
  company VARCHAR(80) NOT NULL,
  driver_name VARCHAR(120) NULL,
  driver_phone VARCHAR(40) NOT NULL,
  car_number VARCHAR(30) NOT NULL,
  quoted_fare DECIMAL(12,2) NULL,
  note VARCHAR(500) NULL,
  booked_by VARCHAR(160) NOT NULL,
  booked_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  delivered_at DATETIME NULL,
  failed_reason VARCHAR(500) NULL
);
