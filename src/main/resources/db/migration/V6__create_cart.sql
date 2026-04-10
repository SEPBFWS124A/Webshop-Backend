CREATE TABLE cart_items (
    id          BIGSERIAL  PRIMARY KEY,
    user_id     BIGINT     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id  BIGINT     NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity    INT        NOT NULL DEFAULT 1 CHECK (quantity > 0),
    added_at    TIMESTAMP  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, product_id)
);
