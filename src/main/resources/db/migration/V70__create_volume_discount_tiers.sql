CREATE TABLE IF NOT EXISTS volume_discount_tiers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    min_order_value NUMERIC(10, 2),
    min_item_count INTEGER,
    discount_percent NUMERIC(5, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_volume_discount_threshold
        CHECK (
            (min_order_value IS NOT NULL AND min_order_value > 0)
            OR (min_item_count IS NOT NULL AND min_item_count > 0)
        ),
    CONSTRAINT chk_volume_discount_percent
        CHECK (discount_percent > 0 AND discount_percent <= 100)
);

INSERT INTO volume_discount_tiers (name, min_order_value, min_item_count, discount_percent, active)
VALUES
    ('Großbestellung 10%', 1000.00, 25, 10.00, TRUE),
    ('Bestellvolumen 5%', 500.00, 10, 5.00, TRUE);
