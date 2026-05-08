ALTER TABLE products
    ADD COLUMN personalizable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN personalization_max_length INTEGER,
    ADD CONSTRAINT chk_products_personalization_length
        CHECK (personalizable = FALSE OR personalization_max_length > 0);

ALTER TABLE cart_items
    ADD COLUMN personalization_text VARCHAR(1000);

ALTER TABLE cart_items
    DROP CONSTRAINT IF EXISTS cart_items_user_id_product_id_key;

CREATE INDEX ix_cart_items_user_product_personalization
    ON cart_items (user_id, product_id, personalization_text);

ALTER TABLE order_items
    ADD COLUMN personalization_text VARCHAR(1000);
