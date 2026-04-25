ALTER TABLE standing_orders
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
