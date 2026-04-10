-- Customer-specific discounts on specific products (US #33, #54)
CREATE TABLE discounts (
    id                  BIGSERIAL      PRIMARY KEY,
    customer_id         BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id          BIGINT         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    discount_percent    NUMERIC(5, 2)  NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100),
    valid_from          DATE           NOT NULL,
    valid_until         DATE,          -- NULL means unlimited (US #54)
    created_by_user_id  BIGINT         REFERENCES users(id),
    UNIQUE (customer_id, product_id)
);
