CREATE TABLE user_recently_viewed_products (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_recently_viewed_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_user_recently_viewed_products_user_viewed_at
    ON user_recently_viewed_products (user_id, viewed_at DESC);
