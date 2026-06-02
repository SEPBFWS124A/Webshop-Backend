-- #122 Add supplier fields to products
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS supplier_name          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS supplier_lead_time_days INTEGER DEFAULT 7;

-- #124 Create purchase_orders table
CREATE TABLE purchase_orders (
    id                   BIGSERIAL PRIMARY KEY,
    product_id           BIGINT        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity             INTEGER       NOT NULL CHECK (quantity > 0),
    supplier_name        VARCHAR(255),
    status               VARCHAR(20)   NOT NULL DEFAULT 'ORDERED',
    ai_suggested_quantity INTEGER,
    ordered_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    received_at          TIMESTAMPTZ,
    ordered_by_user_id   BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_purchase_orders_product_id ON purchase_orders(product_id);
CREATE INDEX idx_purchase_orders_status ON purchase_orders(status);
