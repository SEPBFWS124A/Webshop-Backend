-- #138 Add target warehouse location to purchase orders
ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS warehouse_location_id BIGINT
        REFERENCES warehouse_locations(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_purchase_orders_warehouse ON purchase_orders(warehouse_location_id);
