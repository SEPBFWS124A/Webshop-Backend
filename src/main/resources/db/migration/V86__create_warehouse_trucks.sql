CREATE TABLE warehouse_trucks (
    id                         BIGSERIAL PRIMARY KEY,
    truck_identifier           VARCHAR(50)  NOT NULL UNIQUE,
    origin_warehouse_location_id BIGINT     REFERENCES warehouse_locations(id),
    current_warehouse_location_id BIGINT    REFERENCES warehouse_locations(id),
    driver_id                  VARCHAR(100),
    status                     VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE',
    departure_time             TIMESTAMP,
    completed_at               TIMESTAMP,
    route_optimization_id      VARCHAR(100),
    current_latitude           DOUBLE PRECISION,
    current_longitude          DOUBLE PRECISION,
    capacity_orders            INT          NOT NULL DEFAULT 10,
    updated_at                 TIMESTAMP
);

CREATE INDEX idx_warehouse_trucks_current_location ON warehouse_trucks(current_warehouse_location_id);
CREATE INDEX idx_warehouse_trucks_origin_location ON warehouse_trucks(origin_warehouse_location_id);
CREATE INDEX idx_warehouse_trucks_status ON warehouse_trucks(status);

INSERT INTO warehouse_trucks (truck_identifier, origin_warehouse_location_id, current_warehouse_location_id, status, capacity_orders, updated_at)
SELECT 'LKW-2024-001', id, id, 'AVAILABLE', 10, NOW() FROM warehouse_locations WHERE code = 'MAIN'
ON CONFLICT (truck_identifier) DO NOTHING;

INSERT INTO warehouse_trucks (truck_identifier, origin_warehouse_location_id, current_warehouse_location_id, status, capacity_orders, updated_at)
SELECT 'LKW-2024-002', id, id, 'AVAILABLE', 10, NOW() FROM warehouse_locations WHERE code = 'MAIN'
ON CONFLICT (truck_identifier) DO NOTHING;

INSERT INTO warehouse_trucks (truck_identifier, origin_warehouse_location_id, current_warehouse_location_id, status, capacity_orders, updated_at)
SELECT 'LKW-2024-101', id, id, 'AVAILABLE', 10, NOW() FROM warehouse_locations WHERE code = 'WAREHOUSE_B'
ON CONFLICT (truck_identifier) DO NOTHING;

INSERT INTO warehouse_trucks (truck_identifier, origin_warehouse_location_id, current_warehouse_location_id, status, capacity_orders, updated_at)
SELECT 'LKW-2024-201', id, id, 'AVAILABLE', 10, NOW() FROM warehouse_locations WHERE code = 'WAREHOUSE_C'
ON CONFLICT (truck_identifier) DO NOTHING;

