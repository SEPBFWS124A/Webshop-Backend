-- Align demo/seed data with the current warehouse + truck schema.
-- This migration is idempotent and only updates sample data.

-- Ensure all existing orders have a fulfillment warehouse assigned.
UPDATE orders
SET fulfillment_warehouse_id = (
    SELECT id FROM warehouse_locations WHERE code = 'MAIN' LIMIT 1
)
WHERE fulfillment_warehouse_id IS NULL;

-- Ensure seeded warehouses have geo data for routing/selection.
UPDATE warehouse_locations
SET latitude = 50.9779,
    longitude = 6.8944
WHERE code = 'MAIN' AND (latitude IS NULL OR longitude IS NULL);

UPDATE warehouse_locations
SET latitude = 51.2217,
    longitude = 6.7762
WHERE code = 'WAREHOUSE_B' AND (latitude IS NULL OR longitude IS NULL);

UPDATE warehouse_locations
SET latitude = 51.5141,
    longitude = 7.4653
WHERE code = 'WAREHOUSE_C' AND (latitude IS NULL OR longitude IS NULL);

-- Normalize fleet capacities for demo use cases.
DO $$
BEGIN
    IF to_regclass('public.warehouse_trucks') IS NOT NULL THEN
        UPDATE warehouse_trucks
        SET capacity_orders = 30,
            updated_at = NOW()
        WHERE capacity_orders < 30;

        -- Make sure demo fleet trucks exist (safe for repeated runs).
        INSERT INTO warehouse_trucks (
            truck_identifier,
            origin_warehouse_location_id,
            current_warehouse_location_id,
            status,
            capacity_orders,
            updated_at
        )
        SELECT 'LKW-2024-003', location.id, location.id, 'AVAILABLE', 30, NOW()
        FROM warehouse_locations location
        WHERE location.code = 'MAIN'
        ON CONFLICT (truck_identifier) DO NOTHING;

        INSERT INTO warehouse_trucks (
            truck_identifier,
            origin_warehouse_location_id,
            current_warehouse_location_id,
            status,
            capacity_orders,
            updated_at
        )
        SELECT 'LKW-2024-102', location.id, location.id, 'AVAILABLE', 30, NOW()
        FROM warehouse_locations location
        WHERE location.code = 'WAREHOUSE_B'
        ON CONFLICT (truck_identifier) DO NOTHING;
    END IF;
END $$;

-- Backfill missing order metadata for routing demo flows.
UPDATE orders
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

-- Assign truck identifiers to seeded active route orders if missing.
UPDATE orders
SET truck_identifier = 'LKW-2024-001',
    truck_assigned_at = COALESCE(truck_assigned_at, NOW()),
    route_optimization_id = COALESCE(route_optimization_id, 'seed-route-main-001')
WHERE order_number IN ('ORD-DEMO-003', 'ORD-DEMO-006')
  AND status IN ('PACKED_IN_WAREHOUSE', 'IN_TRUCK', 'SHIPPED')
  AND (truck_identifier IS NULL OR truck_identifier = '');

UPDATE orders
SET truck_identifier = 'LKW-2024-101',
    truck_assigned_at = COALESCE(truck_assigned_at, NOW()),
    route_optimization_id = COALESCE(route_optimization_id, 'seed-route-b-001')
WHERE order_number IN ('ORD-DEMO-009', 'ORD-DEMO-010')
  AND status IN ('PACKED_IN_WAREHOUSE', 'IN_TRUCK', 'SHIPPED')
  AND (truck_identifier IS NULL OR truck_identifier = '');

-- Keep truck state consistent with currently assigned orders.
DO $$
BEGIN
    IF to_regclass('public.warehouse_trucks') IS NOT NULL THEN
        UPDATE warehouse_trucks truck
        SET status = CASE
                WHEN EXISTS (
                    SELECT 1 FROM orders o
                    WHERE o.truck_identifier = truck.truck_identifier
                      AND o.status = 'SHIPPED'
                ) THEN 'DEPARTED'
                WHEN EXISTS (
                    SELECT 1 FROM orders o
                    WHERE o.truck_identifier = truck.truck_identifier
                      AND o.status = 'IN_TRUCK'
                ) THEN 'LOADED'
                WHEN EXISTS (
                    SELECT 1 FROM orders o
                    WHERE o.truck_identifier = truck.truck_identifier
                      AND o.status = 'PACKED_IN_WAREHOUSE'
                ) THEN 'AVAILABLE'
                WHEN EXISTS (
                    SELECT 1 FROM orders o
                    WHERE o.truck_identifier = truck.truck_identifier
                      AND o.status = 'DELIVERED'
                ) THEN 'COMPLETED'
                ELSE 'AVAILABLE'
            END,
            departure_time = CASE
                WHEN EXISTS (
                    SELECT 1 FROM orders o
                    WHERE o.truck_identifier = truck.truck_identifier
                      AND o.status = 'SHIPPED'
                ) THEN COALESCE(departure_time, NOW() - INTERVAL '1 day')
                ELSE NULL
            END,
            current_warehouse_location_id = CASE
                WHEN EXISTS (
                    SELECT 1 FROM orders o
                    WHERE o.truck_identifier = truck.truck_identifier
                      AND o.status = 'SHIPPED'
                ) THEN NULL
                ELSE COALESCE(current_warehouse_location_id, origin_warehouse_location_id)
            END,
            updated_at = NOW();
    END IF;
END $$;

