-- Add truck logistics and delivery coordinate fields to orders table
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS truck_assigned_at    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS route_optimization_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_at            TIMESTAMP,
    ADD COLUMN IF NOT EXISTS delivery_latitude     DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS delivery_longitude    DOUBLE PRECISION;

-- Backfill updated_at for existing rows
UPDATE orders
SET updated_at = created_at
WHERE updated_at IS NULL;

-- Add geo-coordinates to warehouse locations (nullable, populated by ops/seed)
ALTER TABLE warehouse_locations
    ADD COLUMN IF NOT EXISTS latitude  DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

-- Seed approximate geo-coordinates for existing warehouses
UPDATE warehouse_locations SET latitude = 50.9779,  longitude = 6.8944  WHERE code = 'MAIN';
UPDATE warehouse_locations SET latitude = 51.2217,  longitude = 6.7762  WHERE code = 'WAREHOUSE_B';
UPDATE warehouse_locations SET latitude = 51.5141,  longitude = 7.4653  WHERE code = 'WAREHOUSE_C';

-- Index to speed up truck-based lookups
CREATE INDEX IF NOT EXISTS idx_orders_truck_identifier ON orders(truck_identifier);
CREATE INDEX IF NOT EXISTS idx_orders_status           ON orders(status);

