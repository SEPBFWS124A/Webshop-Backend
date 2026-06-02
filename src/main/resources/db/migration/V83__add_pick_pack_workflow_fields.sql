ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS picked_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS picked_by_user_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS picked_quantity INT;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS packing_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS packed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS packed_by_user_id VARCHAR(100);

UPDATE order_items
SET picked_at = NULL,
    picked_by_user_id = NULL,
    picked_quantity = NULL
WHERE picked_at IS NOT NULL
   OR picked_by_user_id IS NOT NULL
   OR picked_quantity IS NOT NULL;
