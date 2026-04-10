CREATE TYPE order_status AS ENUM ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED');

CREATE TABLE orders (
    id              BIGSERIAL      PRIMARY KEY,
    customer_id     BIGINT         NOT NULL REFERENCES users(id),
    total_price     NUMERIC(10, 2) NOT NULL,
    tax_amount      NUMERIC(10, 2) NOT NULL,
    shipping_cost   NUMERIC(10, 2) NOT NULL DEFAULT 0,
    status          order_status   NOT NULL DEFAULT 'PENDING',
    coupon_code     VARCHAR(50),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE TABLE order_items (
    id                  BIGSERIAL      PRIMARY KEY,
    order_id            BIGINT         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id          BIGINT         NOT NULL REFERENCES products(id),
    quantity            INT            NOT NULL CHECK (quantity > 0),
    price_at_order_time NUMERIC(10, 2) NOT NULL
);
