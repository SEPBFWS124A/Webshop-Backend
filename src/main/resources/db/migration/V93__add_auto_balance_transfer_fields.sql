-- V309: Add fields to support internal stock transfer orders (Auto-Balance)

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS order_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER_ORDER',
    ADD COLUMN IF NOT EXISTS is_internal_transfer BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS source_warehouse_id BIGINT REFERENCES warehouse_locations(id);

CREATE INDEX IF NOT EXISTS idx_orders_order_type ON orders(order_type);
CREATE INDEX IF NOT EXISTS idx_orders_internal_transfer ON orders(is_internal_transfer) WHERE is_internal_transfer = TRUE;

