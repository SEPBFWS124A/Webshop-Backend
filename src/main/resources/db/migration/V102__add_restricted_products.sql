-- #147/#148/#149 Restricted products: age/B2B purchase restrictions and verification audit

-- 1. Product restriction flags
ALTER TABLE products
    ADD COLUMN restricted        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN restriction_type  VARCHAR(40);

-- 2. Customer verification status (bonus: skip verification on future purchases)
ALTER TABLE users
    ADD COLUMN verified               BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN verified_at            TIMESTAMPTZ,
    ADD COLUMN verification_reference VARCHAR(120);

-- 3. Order fulfillment tag + verification audit reference
ALTER TABLE orders
    ADD COLUMN restricted_shipping              BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN restriction_verified_at          TIMESTAMPTZ,
    ADD COLUMN restriction_verification_reference VARCHAR(120);

CREATE INDEX idx_products_restricted ON products(restricted) WHERE restricted = TRUE;
CREATE INDEX idx_orders_restricted_shipping ON orders(restricted_shipping) WHERE restricted_shipping = TRUE;
