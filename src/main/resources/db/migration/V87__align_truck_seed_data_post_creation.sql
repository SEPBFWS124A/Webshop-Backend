-- Apply truck seed-data alignment after warehouse_trucks creation.
-- Idempotent and safe for repeated runs.

UPDATE warehouse_trucks
SET capacity_orders = 30,
    updated_at = NOW()
WHERE capacity_orders < 30;

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

