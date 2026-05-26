CREATE TABLE product_bundles (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    discount_percent NUMERIC(5,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE product_bundle_items (
    id BIGSERIAL PRIMARY KEY,
    bundle_id BIGINT NOT NULL REFERENCES product_bundles(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_product_bundle_items_bundle_id ON product_bundle_items(bundle_id);
CREATE INDEX idx_product_bundle_items_product_id ON product_bundle_items(product_id);

ALTER TABLE cart_items
    ADD COLUMN bundle_id BIGINT REFERENCES product_bundles(id) ON DELETE SET NULL,
    ADD COLUMN bundle_title VARCHAR(180),
    ADD COLUMN bundle_group_key VARCHAR(80),
    ADD COLUMN bundle_discount_percent NUMERIC(5,2),
    ADD COLUMN price_override NUMERIC(10,2);

ALTER TABLE order_items
    ADD COLUMN bundle_id BIGINT REFERENCES product_bundles(id) ON DELETE SET NULL,
    ADD COLUMN bundle_title VARCHAR(180),
    ADD COLUMN bundle_group_key VARCHAR(80),
    ADD COLUMN bundle_discount_percent NUMERIC(5,2);

WITH created_bundle AS (
    INSERT INTO product_bundles (title, description, image_url, discount_percent, active, featured)
    SELECT
        'Home-Office Starter-Set',
        'Laptop, Maus und USB-C-Hub als sofort einsatzbereites Arbeitsplatz-Bundle mit 10 % Set-Rabatt.',
        '/laptop-pro-15.jpg',
        10.00,
        TRUE,
        TRUE
    WHERE NOT EXISTS (
        SELECT 1
        FROM product_bundles
        WHERE title = 'Home-Office Starter-Set'
    )
    RETURNING id
)
INSERT INTO product_bundle_items (bundle_id, product_id, quantity, display_order)
SELECT created_bundle.id, products.id, seed.quantity, seed.display_order
FROM created_bundle
JOIN (
    VALUES
        ('LAPTOP-PRO-15', 1, 0),
        ('WIRELESS-MOUSE', 1, 1),
        ('USB-C-HUB', 1, 2)
) AS seed(sku, quantity, display_order) ON TRUE
JOIN products ON products.sku = seed.sku;

WITH created_bundle AS (
    INSERT INTO product_bundles (title, description, image_url, discount_percent, active, featured)
    SELECT
        'Ergonomie Upgrade Bundle',
        'Höhenverstellbarer Schreibtisch plus ergonomischer Bürostuhl als Arbeitsplatz-Upgrade mit 12 % Rabatt.',
        '/standing-desk.jpg',
        12.00,
        TRUE,
        FALSE
    WHERE NOT EXISTS (
        SELECT 1
        FROM product_bundles
        WHERE title = 'Ergonomie Upgrade Bundle'
    )
    RETURNING id
)
INSERT INTO product_bundle_items (bundle_id, product_id, quantity, display_order)
SELECT created_bundle.id, products.id, seed.quantity, seed.display_order
FROM created_bundle
JOIN (
    VALUES
        ('STANDING-DESK', 1, 0),
        ('OFFICE-CHAIR', 1, 1)
) AS seed(sku, quantity, display_order) ON TRUE
JOIN products ON products.sku = seed.sku;

WITH created_bundle AS (
    INSERT INTO product_bundles (title, description, image_url, discount_percent, active, featured)
    SELECT
        'Schreibtisch Essentials',
        'Noch in Vorbereitung: kompaktes Bundle für den Arbeitsalltag am Schreibtisch.',
        '/wireless-mouse.jpg',
        8.00,
        FALSE,
        FALSE
    WHERE NOT EXISTS (
        SELECT 1
        FROM product_bundles
        WHERE title = 'Schreibtisch Essentials'
    )
    RETURNING id
)
INSERT INTO product_bundle_items (bundle_id, product_id, quantity, display_order)
SELECT created_bundle.id, products.id, seed.quantity, seed.display_order
FROM created_bundle
JOIN (
    VALUES
        ('WIRELESS-MOUSE', 1, 0),
        ('USB-C-HUB', 1, 1)
) AS seed(sku, quantity, display_order) ON TRUE
JOIN products ON products.sku = seed.sku;
