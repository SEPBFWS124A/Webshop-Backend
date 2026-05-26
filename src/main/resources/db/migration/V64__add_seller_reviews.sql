ALTER TABLE products
    ADD COLUMN IF NOT EXISTS seller_name VARCHAR(180) NOT NULL DEFAULT 'Webshop';

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS seller_name VARCHAR(180) NOT NULL DEFAULT 'Webshop';

UPDATE order_items
SET seller_name = COALESCE(NULLIF(p.seller_name, ''), 'Webshop')
FROM products p
WHERE order_items.product_id = p.id
  AND (order_items.seller_name IS NULL OR order_items.seller_name = 'Webshop');

CREATE TABLE IF NOT EXISTS seller_reviews (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seller_name VARCHAR(180) NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_seller_reviews_order_seller UNIQUE (order_id, seller_name)
);

CREATE INDEX IF NOT EXISTS idx_seller_reviews_customer_id
    ON seller_reviews(customer_id);

CREATE INDEX IF NOT EXISTS idx_seller_reviews_seller_name
    ON seller_reviews(seller_name);
