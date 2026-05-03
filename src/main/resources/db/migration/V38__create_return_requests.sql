ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;

UPDATE orders
SET delivered_at = created_at
WHERE status = 'DELIVERED'
  AND delivered_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'return_reason') THEN
        CREATE TYPE return_reason AS ENUM ('DOES_NOT_FIT', 'NOT_LIKED', 'DEFECTIVE');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'return_request_status') THEN
        CREATE TYPE return_request_status AS ENUM ('SUBMITTED');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS return_requests (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES users(id),
    order_id    BIGINT NOT NULL REFERENCES orders(id),
    reason      return_reason NOT NULL,
    status      return_request_status NOT NULL DEFAULT 'SUBMITTED',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS return_request_items (
    id                BIGSERIAL PRIMARY KEY,
    return_request_id BIGINT NOT NULL REFERENCES return_requests(id) ON DELETE CASCADE,
    order_item_id     BIGINT NOT NULL REFERENCES order_items(id),
    product_name      VARCHAR(255) NOT NULL,
    quantity          INT NOT NULL CHECK (quantity > 0),
    CONSTRAINT uq_return_request_items_order_item UNIQUE (order_item_id)
);

CREATE INDEX IF NOT EXISTS idx_return_requests_customer_created
    ON return_requests (customer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_return_requests_order
    ON return_requests (order_id);
