ALTER TABLE standing_order_items
    ADD COLUMN notified_unavailable BOOLEAN NOT NULL DEFAULT FALSE;
