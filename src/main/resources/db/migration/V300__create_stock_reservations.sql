CREATE TABLE stock_reservations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    cart_item_id BIGINT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(30) NOT NULL,
    reserved_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_reservations_product_status
    ON stock_reservations(product_id, status, reserved_until);

CREATE INDEX idx_stock_reservations_user_status
    ON stock_reservations(user_id, status);

CREATE INDEX idx_stock_reservations_cart_item_status
    ON stock_reservations(cart_item_id, status);

CREATE TABLE availability_notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notified_at TIMESTAMPTZ,
    CONSTRAINT uk_availability_notifications_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_availability_notifications_product_active
    ON availability_notifications(product_id, active);
