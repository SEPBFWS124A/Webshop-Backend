ALTER TABLE cart_items
    ADD COLUMN IF NOT EXISTS shared_wishlist_token VARCHAR(80),
    ADD COLUMN IF NOT EXISTS shared_wishlist_list_id VARCHAR(140);

CREATE INDEX IF NOT EXISTS ix_cart_items_shared_wishlist
    ON cart_items (shared_wishlist_token, shared_wishlist_list_id);

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS shared_wishlist_token VARCHAR(80),
    ADD COLUMN IF NOT EXISTS shared_wishlist_list_id VARCHAR(140);

CREATE INDEX IF NOT EXISTS ix_order_items_shared_wishlist
    ON order_items (shared_wishlist_token, shared_wishlist_list_id);
