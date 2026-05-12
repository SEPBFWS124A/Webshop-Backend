ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS gift_card_redeemed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS gift_card_redeemed_order_number VARCHAR(30);

CREATE INDEX IF NOT EXISTS ix_order_items_gift_card_code
    ON order_items (gift_card_code)
    WHERE gift_card_code IS NOT NULL;
