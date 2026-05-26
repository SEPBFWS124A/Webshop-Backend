UPDATE products
SET stock = GREATEST(stock, 5)
WHERE name = 'Wireless Mouse';

UPDATE product_bundles
SET description = 'USB-C-Hub plus Wireless Mouse als sofort verfügbares Bundle für einen schnellen Arbeitsplatz-Upgrade.',
    updated_at = NOW()
WHERE title = 'Sofort einsatzbereit Bundle';

DELETE FROM product_bundle_items
WHERE bundle_id = (
    SELECT id
    FROM product_bundles
    WHERE title = 'Sofort einsatzbereit Bundle'
);

INSERT INTO product_bundle_items (bundle_id, product_id, quantity, display_order)
SELECT bundle.id, item.product_id, item.quantity, item.display_order
FROM product_bundles bundle
CROSS JOIN (
    VALUES
        ((SELECT id FROM products WHERE name = 'USB-C Hub'), 1, 0),
        ((SELECT id FROM products WHERE name = 'Wireless Mouse'), 1, 1)
) AS item(product_id, quantity, display_order)
WHERE bundle.title = 'Sofort einsatzbereit Bundle'
  AND item.product_id IS NOT NULL;
