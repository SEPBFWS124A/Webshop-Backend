CREATE TABLE follow_up_orders (
    id                  BIGSERIAL   PRIMARY KEY,
    customer_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_order_id     BIGINT      REFERENCES orders(id) ON DELETE SET NULL,
    execution_date      DATE        NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE follow_up_order_items (
    id                  BIGSERIAL   PRIMARY KEY,
    follow_up_order_id  BIGINT      NOT NULL REFERENCES follow_up_orders(id) ON DELETE CASCADE,
    product_id          BIGINT      NOT NULL REFERENCES products(id),
    quantity            INT         NOT NULL CHECK (quantity > 0)
);
