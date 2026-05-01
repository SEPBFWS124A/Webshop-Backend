DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'trade_in_condition') THEN
        CREATE TYPE trade_in_condition AS ENUM ('LIKE_NEW', 'LIGHT_WEAR', 'DEFECTIVE');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'trade_in_status') THEN
        CREATE TYPE trade_in_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS trade_in_requests (
    id                 BIGSERIAL PRIMARY KEY,
    customer_id        BIGINT NOT NULL REFERENCES users(id),
    order_id           BIGINT NOT NULL REFERENCES orders(id),
    order_item_id      BIGINT NOT NULL REFERENCES order_items(id),
    product_name       VARCHAR(255) NOT NULL,
    condition          trade_in_condition NOT NULL,
    status             trade_in_status NOT NULL DEFAULT 'PENDING',
    estimated_value    DECIMAL(10, 2) NOT NULL,
    coupon_code        VARCHAR(50),
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP
);
