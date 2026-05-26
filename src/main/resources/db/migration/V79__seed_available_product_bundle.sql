WITH inserted_bundle AS (
    INSERT INTO product_bundles (title, description, image_url, discount_percent, active, featured, created_at, updated_at)
    SELECT
        'Sofort einsatzbereit Bundle',
        'USB-C-Hub plus digitaler Gutschein als sofort verfügbares Bundle für schnellen Einstieg oder als Geschenk.',
        NULL,
        8.00,
        TRUE,
        TRUE,
        NOW(),
        NOW()
    WHERE NOT EXISTS (
        SELECT 1
        FROM product_bundles
        WHERE title = 'Sofort einsatzbereit Bundle'
    )
    RETURNING id
)
INSERT INTO product_bundle_items (bundle_id, product_id, quantity, display_order)
SELECT inserted_bundle.id, product_id, quantity, display_order
FROM inserted_bundle
CROSS JOIN (
    VALUES
        ((SELECT id FROM products WHERE name = 'USB-C Hub'), 1, 0),
        ((SELECT id FROM products WHERE name = 'Digitaler Geschenkgutschein'), 1, 1)
) AS items(product_id, quantity, display_order)
WHERE product_id IS NOT NULL;
