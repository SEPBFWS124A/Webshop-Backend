CREATE TABLE cart_change_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_username VARCHAR(255) NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    customer_number VARCHAR(64),
    cart_item_id BIGINT,
    product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    product_sku VARCHAR(100),
    product_name VARCHAR(255) NOT NULL,
    action VARCHAR(32) NOT NULL,
    quantity_delta INTEGER NOT NULL,
    resulting_quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cart_change_logs_customer_created
    ON cart_change_logs(customer_id, created_at DESC);

CREATE INDEX idx_cart_change_logs_actor_created
    ON cart_change_logs(actor_user_id, created_at DESC);
