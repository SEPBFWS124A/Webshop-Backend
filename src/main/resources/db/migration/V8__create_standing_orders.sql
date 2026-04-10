CREATE TABLE standing_orders (
    id                      BIGSERIAL  PRIMARY KEY,
    customer_id             BIGINT     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    interval_days           INT        NOT NULL CHECK (interval_days > 0),
    next_execution_date     DATE       NOT NULL,
    active                  BOOLEAN    NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE TABLE standing_order_items (
    id                  BIGSERIAL  PRIMARY KEY,
    standing_order_id   BIGINT     NOT NULL REFERENCES standing_orders(id) ON DELETE CASCADE,
    product_id          BIGINT     NOT NULL REFERENCES products(id),
    quantity            INT        NOT NULL CHECK (quantity > 0)
);
