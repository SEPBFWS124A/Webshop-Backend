-- #147 Seed two demo products that are restricted from the start (for testing).
-- One age-restricted (18+) and one B2B-proof product. Idempotent via SKU guard.

INSERT INTO products (
    name, description, image_url, recommended_retail_price, co2_emission_kg,
    eco_score, category, seller_name, product_type, stock, sku,
    purchasable, trade_in_enabled, promoted, personalizable, has_variants,
    supplier_name, supplier_lead_time_days, restricted, restriction_type
)
SELECT
    'Hochprozentiger Premium-Whisky 0,7l',
    'Edler Single Malt Whisky (42% vol.). Abgabe nur an Personen ab 18 Jahren – Altersnachweis erforderlich.',
    NULL, 49.90, 1.200,
    'C', 'Spirituosen', 'Webshop', 'STANDARD', 40, 'RESTRICTED-WHISKY-18',
    TRUE, FALSE, TRUE, FALSE, FALSE,
    'Highland Distillery Ltd.', 5, TRUE, 'AGE_18'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'RESTRICTED-WHISKY-18');

INSERT INTO products (
    name, description, image_url, recommended_retail_price, co2_emission_kg,
    eco_score, category, seller_name, product_type, stock, sku,
    purchasable, trade_in_enabled, promoted, personalizable, has_variants,
    supplier_name, supplier_lead_time_days, restricted, restriction_type
)
SELECT
    'Profi-Laserentfernungsmesser (Gewerbe)',
    'Industrielles Vermessungsgerät. Verkauf ausschließlich an Gewerbekunden – B2B-Nachweis erforderlich.',
    NULL, 329.00, 3.500,
    'B', 'Werkzeug', 'Webshop', 'STANDARD', 25, 'RESTRICTED-B2B-LASER',
    TRUE, TRUE, FALSE, FALSE, FALSE,
    'Industrial Tools GmbH', 7, TRUE, 'B2B_PROOF'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'RESTRICTED-B2B-LASER');
