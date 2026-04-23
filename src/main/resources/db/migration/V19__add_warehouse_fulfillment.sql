DO $$
BEGIN
    ALTER TYPE user_role ADD VALUE 'WAREHOUSE_EMPLOYEE';
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TYPE order_status ADD VALUE 'PACKED_IN_WAREHOUSE';
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TYPE order_status ADD VALUE 'IN_TRUCK';
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS warehouse_position VARCHAR(80);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS truck_identifier VARCHAR(50);
