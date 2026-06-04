-- ============================================================
-- Development seed data - run once after Flyway migrations
-- Usage:
-- Get-Content src\main\resources\db\dev-seed.sql | docker exec -i webshop-postgres psql -U webshop -d webshop
-- ============================================================

-- Users
-- All passwords are: Password1!
INSERT INTO users (username, email, password_hash, user_type, customer_number, employee_number) VALUES
  ('alice', 'alice@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'PRIVATE', 'K-' || to_char(nextval('customer_number_sequence'), 'FM000000'), NULL),
  ('bob', 'bob@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'BUSINESS', 'K-' || to_char(nextval('customer_number_sequence'), 'FM000000'), NULL),
  ('carol', 'carol@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'INTERNAL', NULL, 'MA-' || to_char(nextval('employee_number_sequence'), 'FM00000')),
  ('dave', 'dave@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'INTERNAL', NULL, 'MA-' || to_char(nextval('employee_number_sequence'), 'FM00000')),
  ('lager', 'lager@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'INTERNAL', NULL, 'MA-' || to_char(nextval('employee_number_sequence'), 'FM00000')),
  ('admin', 'admin@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'INTERNAL', NULL, 'MA-' || to_char(nextval('employee_number_sequence'), 'FM00000'));

-- Roles (m:n via user_roles pivot table, introduced in Migration V24)
INSERT INTO user_roles (user_id, role)
SELECT id, 'CUSTOMER' FROM users WHERE username IN ('alice', 'bob');

INSERT INTO user_roles (user_id, role)
SELECT id, 'EMPLOYEE' FROM users WHERE username = 'carol';

INSERT INTO user_roles (user_id, role)
SELECT id, 'SALES_EMPLOYEE' FROM users WHERE username = 'dave';

INSERT INTO user_roles (user_id, role)
SELECT id, 'WAREHOUSE_EMPLOYEE' FROM users WHERE username = 'lager';

INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE username = 'admin';

-- Business info for bob
INSERT INTO business_info (user_id, company_name, industry, company_size)
SELECT id, 'Bob Corp GmbH', 'Technology', '10-50'
FROM users
WHERE username = 'bob';

-- Delivery address for alice (real address in Koeln near main warehouse)
INSERT INTO delivery_addresses (user_id, street, city, postal_code, country)
SELECT id, 'Ehrenstrasse 24', 'Koeln', '50672', 'Deutschland'
FROM users
WHERE username = 'alice';

-- Payment methods
INSERT INTO payment_methods (user_id, method_type, masked_details)
SELECT id, 'SEPA_DIRECT_DEBIT', 'DE89****4321'
FROM users
WHERE username = 'alice';

-- Products
INSERT INTO products (
    name,
    description,
    image_url,
    recommended_retail_price,
    co2_emission_kg,
    eco_score,
    category,
    stock,
    sku,
    warehouse_position,
    purchasable,
    promoted
) VALUES
  ('Laptop Pro 15', 'High-performance laptop with 15" display', '/laptop-pro-15.jpg', 1299.99, 214.500, 'E', 'Electronics', 50, 'LAPTOP-PRO-15', 'A-01-01', TRUE, TRUE),
  ('Wireless Mouse', 'Ergonomic wireless mouse, 2.4 GHz', '/wireless-mouse.jpg', 29.99, 2.100, 'B', 'Electronics', 200, 'WIRELESS-MOUSE', 'A-03-07', TRUE, FALSE),
  ('Standing Desk', 'Height-adjustable standing desk 140x70 cm', '/standing-desk.jpg', 499.99, 58.750, 'D', 'Furniture', 30, 'STANDING-DESK', 'B-01-03', TRUE, FALSE),
  ('USB-C Hub', '7-in-1 USB-C hub with HDMI and SD card', '/usb-c-hub.jpg', 49.99, 5.400, 'C', 'Electronics', 100, 'USB-C-HUB', 'A-04-02', TRUE, FALSE),
  ('Office Chair', 'Lumbar support mesh chair', '/office-chair.jpg', 349.99, 33.200, 'D', 'Furniture', 25, 'OFFICE-CHAIR', 'B-02-05', TRUE, TRUE),
  ('Notebook (Draft)', 'Not yet available to customers', NULL, 9.99, 0.350, 'A', 'Stationery', 0, 'NOTEBOOK-DRAFT', 'C-01-01', FALSE, FALSE);

-- Preisverlauf (UVP) fuer alle Produkte: drei juengere Aenderungen fuer Demo-Charts.
INSERT INTO product_price_history (product_id, old_price, new_price, change_reason, changed_by, changed_at)
SELECT p.id, NULL, ROUND((p.recommended_retail_price * 1.18)::numeric, 2), 'INITIAL', NULL, NOW() - INTERVAL '70 days'
FROM products p
WHERE p.recommended_retail_price IS NOT NULL
  AND p.recommended_retail_price > 0;

INSERT INTO product_price_history (product_id, old_price, new_price, change_reason, changed_by, changed_at)
SELECT p.id,
       ROUND((p.recommended_retail_price * 1.18)::numeric, 2),
       ROUND((p.recommended_retail_price * 1.08)::numeric, 2),
       'MANUAL',
       NULL,
       NOW() - INTERVAL '28 days'
FROM products p
WHERE p.recommended_retail_price IS NOT NULL
  AND p.recommended_retail_price > 0;

INSERT INTO product_price_history (product_id, old_price, new_price, change_reason, changed_by, changed_at)
SELECT p.id,
       ROUND((p.recommended_retail_price * 1.08)::numeric, 2),
       p.recommended_retail_price,
       'PROMOTION',
       NULL,
       NOW() - INTERVAL '6 days'
FROM products p
WHERE p.recommended_retail_price IS NOT NULL
  AND p.recommended_retail_price > 0;

-- Warehouse stock per location
INSERT INTO warehouse_product_stocks (product_id, warehouse_location_id, quantity)
SELECT
    p.id,
    wl.id,
    CASE
        WHEN wl.code = 'MAIN' THEN p.stock
        WHEN wl.code = 'WAREHOUSE_B' AND p.category = 'Furniture' THEN GREATEST(0, p.stock / 2)
        WHEN wl.code = 'WAREHOUSE_B' AND p.category = 'Electronics' THEN GREATEST(0, p.stock / 4)
        WHEN wl.code = 'WAREHOUSE_C' AND p.category = 'Electronics' THEN GREATEST(0, p.stock / 3)
        WHEN wl.code = 'WAREHOUSE_C' AND p.category = 'Furniture' THEN GREATEST(0, p.stock / 4)
        ELSE 0
    END
FROM products p
CROSS JOIN warehouse_locations wl
WHERE wl.code IN ('MAIN', 'WAREHOUSE_B', 'WAREHOUSE_C')
ON CONFLICT (product_id, warehouse_location_id) DO NOTHING;

-- Advertisements
INSERT INTO advertisements (title, description, content_type, image_url, target_url, active, start_date, end_date)
SELECT title, description, content_type, image_url, target_url, active, start_date, end_date
FROM (
    VALUES
      ('Sommeraktion im Home Office', 'Ergonomische Favoriten, clevere Bundles und schnelle Upgrades für deinen Arbeitsplatz.', 'IMAGE', '/standing-desk.jpg', '/products/3', TRUE, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days'),
      ('Top Auswahl für Entscheider', 'Vergleiche Bestseller, Empfehlungen und sofort verfuegbare Geraete direkt im Sortiment.', 'TEXT', NULL, '/', TRUE, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days'),
      ('Verkaeufer-Aktion vorbereiten', 'Diese Werbeflaeche ist angelegt, aber noch nicht aktiv geschaltet.', 'TEXT', NULL, '/admin/marketing/placements', FALSE, CURRENT_DATE + INTERVAL '7 days', CURRENT_DATE + INTERVAL '21 days')
) AS seed_data(title, description, content_type, image_url, target_url, active, start_date, end_date)
WHERE NOT EXISTS (
    SELECT 1
    FROM advertisements existing
    WHERE existing.title = seed_data.title
);

-- Discounts
INSERT INTO discounts (customer_id, product_id, discount_percent, valid_from, valid_until)
SELECT u.id, p.id, 10.00, CURRENT_DATE, NULL
FROM users u, products p
WHERE u.username = 'alice' AND p.name = 'Wireless Mouse';

INSERT INTO discounts (customer_id, product_id, discount_percent, valid_from, valid_until)
SELECT u.id, p.id, 15.00, CURRENT_DATE, (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::DATE
FROM users u, products p
WHERE u.username = 'bob' AND p.name = 'Laptop Pro 15';

-- Coupon
INSERT INTO coupons (customer_id, code, discount_percent, valid_until, used)
SELECT id, 'WELCOME10', 10.00, CURRENT_DATE + INTERVAL '30 days', FALSE
FROM users
WHERE username = 'alice'
ON CONFLICT (code) DO NOTHING;

-- Cart items for alice
INSERT INTO cart_items (user_id, product_id, quantity)
SELECT u.id, p.id, 2
FROM users u, products p
WHERE u.username = 'alice' AND p.name = 'Wireless Mouse';

-- ============================================================
-- WAREHOUSE DEMO ORDERS
-- Real geocodable addresses in NRW near the 3 warehouses:
--   MAIN:        Koeln (50769) - Marconistrasse 10
--   WAREHOUSE_B: Duesseldorf (40233) - Industriestrasse 22
--   WAREHOUSE_C: Dortmund (44147) - Hafenallee 7
-- ============================================================

-- ============================================================
-- 1. CONFIRMED orders (not yet picked - fresh in the warehouse)
-- ============================================================

-- Order 1001: Koeln area, waiting for pick at MAIN warehouse
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id, created_at)
    SELECT u.id, 'ORD-WH-1001', 'alice@example.com', 'Alice Mueller',
        'Ehrenstrasse 24', 'Koeln', '50672', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 64.47, 10.29,
        4.99, 'STANDARD', 'CONFIRMED', 0.00, wl.id, NOW() - INTERVAL '2 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
SELECT ord.id, p.id, v.qty, v.price
FROM ord
CROSS JOIN (VALUES ('Wireless Mouse', 1, 29.99::NUMERIC), ('USB-C Hub', 1, 49.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- Order 1002: Bonn area, waiting for pick at MAIN warehouse
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id, created_at)
    SELECT u.id, 'ORD-WH-1002', 'bob@example.com', 'Bob Schmidt',
        'Oxfordstrasse 20', 'Bonn', '53111', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 1364.98, 217.85,
        0.00, 'EXPRESS', 'CONFIRMED', 0.00, wl.id, NOW() - INTERVAL '3 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'bob' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
SELECT ord.id, p.id, 1, 1299.99
FROM ord JOIN products p ON p.name = 'Laptop Pro 15';

-- Order 1003: Leverkusen, waiting for pick at MAIN warehouse (multiple items)
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id, created_at)
    SELECT u.id, 'ORD-WH-1003', 'alice@example.com', 'Alice Mueller',
        'Wiesdorfer Platz 12', 'Leverkusen', '51373', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 429.97, 68.63,
        0.00, 'STANDARD', 'CONFIRMED', 0.00, wl.id, NOW() - INTERVAL '5 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
SELECT ord.id, p.id, v.qty, v.price
FROM ord
CROSS JOIN (VALUES ('Office Chair', 1, 349.99::NUMERIC), ('Wireless Mouse', 2, 29.99::NUMERIC), ('USB-C Hub', 1, 49.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- ============================================================
-- 2. CONFIRMED orders with items PARTIALLY PICKED
-- ============================================================

-- Order 1004: Duesseldorf, 1 of 2 items picked at WAREHOUSE_B
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, created_at)
    SELECT u.id, 'ORD-WH-1004', 'bob@example.com', 'Bob Schmidt',
        'Koenigsallee 60', 'Duesseldorf', '40212', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 379.98, 60.68,
        0.00, 'EXPRESS', 'CONFIRMED', 0.00, wl.id,
        NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '4 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'bob' AND wl.code = 'WAREHOUSE_B'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, v.picked_at, v.picked_by, v.picked_qty
FROM ord
CROSS JOIN (VALUES
    ('Office Chair', 1, 349.99::NUMERIC, NOW() - INTERVAL '20 minutes', 'lager', 1),
    ('Wireless Mouse', 1, 29.99::NUMERIC, NULL::TIMESTAMP, NULL::TEXT, NULL::INT)
) AS v(pname, qty, price, picked_at, picked_by, picked_qty)
JOIN products p ON p.name = v.pname;

-- Order 1005: Essen, all items picked (ready to complete packing) at WAREHOUSE_C
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, created_at)
    SELECT u.id, 'ORD-WH-1005', 'alice@example.com', 'Alice Mueller',
        'Kettwiger Strasse 36', 'Essen', '45127', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 79.98, 12.77,
        4.99, 'STANDARD', 'CONFIRMED', 0.00, wl.id,
        NOW() - INTERVAL '45 minutes', NOW() - INTERVAL '6 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'WAREHOUSE_C'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, NOW() - INTERVAL '15 minutes', 'lager', v.qty
FROM ord
CROSS JOIN (VALUES ('Wireless Mouse', 2, 29.99::NUMERIC), ('USB-C Hub', 1, 49.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- ============================================================
-- 3. PACKED_IN_WAREHOUSE orders (picked, packed, waiting for truck)
-- ============================================================

-- Order 1006: Bergisch Gladbach (near Koeln), packed at MAIN
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id, created_at)
    SELECT u.id, 'ORD-WH-1006', 'alice@example.com', 'Alice Mueller',
        'Hauptstrasse 128', 'Bergisch Gladbach', '51465', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 534.98, 85.42,
        0.00, 'STANDARD', 'PACKED_IN_WAREHOUSE', 0.00, wl.id,
        NOW() - INTERVAL '3 hours', NOW() - INTERVAL '2 hours', 'lager', NOW() - INTERVAL '10 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, NOW() - INTERVAL '3 hours', 'lager', v.qty
FROM ord
CROSS JOIN (VALUES ('Standing Desk', 1, 499.99::NUMERIC), ('Wireless Mouse', 1, 29.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- Order 1007: Troisdorf (near Koeln/Bonn), packed at MAIN
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id, created_at)
    SELECT u.id, 'ORD-WH-1007', 'bob@example.com', 'Bob Schmidt',
        'Kerpener Strasse 180', 'Troisdorf', '53844', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 1349.98, 215.55,
        0.00, 'EXPRESS', 'PACKED_IN_WAREHOUSE', 0.00, wl.id,
        NOW() - INTERVAL '4 hours', NOW() - INTERVAL '3 hours', 'lager', NOW() - INTERVAL '12 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'bob' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, NOW() - INTERVAL '4 hours', 'lager', v.qty
FROM ord
CROSS JOIN (VALUES ('Laptop Pro 15', 1, 1299.99::NUMERIC), ('USB-C Hub', 1, 49.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- Order 1008: Neuss (near Duesseldorf), packed at WAREHOUSE_B
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id, created_at)
    SELECT u.id, 'ORD-WH-1008', 'alice@example.com', 'Alice Mueller',
        'Niederstrasse 5', 'Neuss', '41460', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 59.98, 9.58,
        4.99, 'STANDARD', 'PACKED_IN_WAREHOUSE', 0.00, wl.id,
        NOW() - INTERVAL '5 hours', NOW() - INTERVAL '4 hours', 'lager', NOW() - INTERVAL '14 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'WAREHOUSE_B'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, NOW() - INTERVAL '5 hours', 'lager', v.qty
FROM ord
CROSS JOIN (VALUES ('Wireless Mouse', 1, 29.99::NUMERIC), ('USB-C Hub', 1, 29.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- Order 1009: Bochum (near Dortmund), packed at WAREHOUSE_C
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id, created_at)
    SELECT u.id, 'ORD-WH-1009', 'bob@example.com', 'Bob Schmidt',
        'Huestrasse 9', 'Bochum', '44787', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 349.99, 55.87,
        0.00, 'STANDARD', 'PACKED_IN_WAREHOUSE', 0.00, wl.id,
        NOW() - INTERVAL '6 hours', NOW() - INTERVAL '5 hours', 'lager', NOW() - INTERVAL '16 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'bob' AND wl.code = 'WAREHOUSE_C'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, 1, 349.99, NOW() - INTERVAL '6 hours', 'lager', 1
FROM ord JOIN products p ON p.name = 'Office Chair';

-- ============================================================
-- 4. IN_TRUCK orders (loaded onto truck, waiting for departure)
-- ============================================================

-- Order 1010: Bruehl (Koeln area), in truck LKW-2024-001 at MAIN
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, route_optimization_id, created_at)
    SELECT u.id, 'ORD-WH-1010', 'alice@example.com', 'Alice Mueller',
        'Uhlstrasse 3', 'Bruehl', '50321', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 529.98, 84.62,
        0.00, 'STANDARD', 'IN_TRUCK', 0.00, wl.id,
        NOW() - INTERVAL '8 hours', NOW() - INTERVAL '7 hours', 'lager',
        'LKW-2024-001', NOW() - INTERVAL '2 hours', 'route-koeln-sued-001', NOW() - INTERVAL '1 day'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, NOW() - INTERVAL '8 hours', 'lager', v.qty
FROM ord
CROSS JOIN (VALUES ('Standing Desk', 1, 499.99::NUMERIC), ('Wireless Mouse', 1, 29.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- Order 1011: Huerth (Koeln area), in same truck LKW-2024-001 at MAIN
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, route_optimization_id, created_at)
    SELECT u.id, 'ORD-WH-1011', 'bob@example.com', 'Bob Schmidt',
        'Luxemburger Strasse 1', 'Huerth', '50354', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 1299.99, 207.62,
        0.00, 'EXPRESS', 'IN_TRUCK', 0.00, wl.id,
        NOW() - INTERVAL '9 hours', NOW() - INTERVAL '8 hours', 'lager',
        'LKW-2024-001', NOW() - INTERVAL '2 hours', 'route-koeln-sued-001', NOW() - INTERVAL '1 day'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'bob' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, 1, 1299.99, NOW() - INTERVAL '9 hours', 'lager', 1
FROM ord JOIN products p ON p.name = 'Laptop Pro 15';

-- Order 1012: Meerbusch (Duesseldorf area), in truck LKW-2024-101 at WAREHOUSE_B
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, route_optimization_id, created_at)
    SELECT u.id, 'ORD-WH-1012', 'alice@example.com', 'Alice Mueller',
        'Moerser Strasse 55', 'Meerbusch', '40667', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 79.98, 12.77,
        4.99, 'STANDARD', 'IN_TRUCK', 0.00, wl.id,
        NOW() - INTERVAL '7 hours', NOW() - INTERVAL '6 hours', 'lager',
        'LKW-2024-101', NOW() - INTERVAL '1 hour', 'route-ddorf-nord-001', NOW() - INTERVAL '20 hours'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'WAREHOUSE_B'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, NOW() - INTERVAL '7 hours', 'lager', v.qty
FROM ord
CROSS JOIN (VALUES ('Wireless Mouse', 1, 29.99::NUMERIC), ('USB-C Hub', 1, 49.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- ============================================================
-- 5. SHIPPED orders (truck departed, en route)
-- ============================================================

-- Order 1013: Frechen (Koeln west), shipped via LKW-2024-002 from MAIN
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, route_optimization_id, created_at)
    SELECT u.id, 'ORD-WH-1013', 'bob@example.com', 'Bob Schmidt',
        'Koelner Strasse 99', 'Frechen', '50226', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 399.98, 63.88,
        0.00, 'STANDARD', 'SHIPPED', 0.00, wl.id,
        NOW() - INTERVAL '1 day', NOW() - INTERVAL '22 hours', 'lager',
        'LKW-2024-002', NOW() - INTERVAL '18 hours', 'route-koeln-west-001', NOW() - INTERVAL '2 days'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'bob' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, NOW() - INTERVAL '1 day', 'lager', v.qty
FROM ord
CROSS JOIN (VALUES ('Office Chair', 1, 349.99::NUMERIC), ('USB-C Hub', 1, 49.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- Order 1014: Pulheim (Koeln northwest), shipped same truck LKW-2024-002
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, route_optimization_id, created_at)
    SELECT u.id, 'ORD-WH-1014', 'alice@example.com', 'Alice Mueller',
        'Venloer Strasse 51', 'Pulheim', '50259', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 29.99, 4.79,
        4.99, 'STANDARD', 'SHIPPED', 0.00, wl.id,
        NOW() - INTERVAL '1 day', NOW() - INTERVAL '22 hours', 'lager',
        'LKW-2024-002', NOW() - INTERVAL '18 hours', 'route-koeln-west-001', NOW() - INTERVAL '2 days'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, 1, 29.99, NOW() - INTERVAL '1 day', 'lager', 1
FROM ord JOIN products p ON p.name = 'Wireless Mouse';

-- Order 1015: Hagen (Dortmund area), shipped via LKW-2024-201 from WAREHOUSE_C
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, route_optimization_id, created_at)
    SELECT u.id, 'ORD-WH-1015', 'bob@example.com', 'Bob Schmidt',
        'Elberfelder Strasse 30', 'Hagen', '58095', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 499.99, 79.83,
        0.00, 'EXPRESS', 'SHIPPED', 0.00, wl.id,
        NOW() - INTERVAL '1 day', NOW() - INTERVAL '20 hours', 'lager',
        'LKW-2024-201', NOW() - INTERVAL '14 hours', 'route-dortmund-sued-001', NOW() - INTERVAL '2 days'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'bob' AND wl.code = 'WAREHOUSE_C'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, 1, 499.99, NOW() - INTERVAL '1 day', 'lager', 1
FROM ord JOIN products p ON p.name = 'Standing Desk';

-- Order 1016: Witten (Dortmund area), same truck LKW-2024-201
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, route_optimization_id, created_at)
    SELECT u.id, 'ORD-WH-1016', 'alice@example.com', 'Alice Mueller',
        'Bahnhofstrasse 22', 'Witten', '58452', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 59.98, 9.58,
        4.99, 'STANDARD', 'SHIPPED', 0.00, wl.id,
        NOW() - INTERVAL '1 day', NOW() - INTERVAL '20 hours', 'lager',
        'LKW-2024-201', NOW() - INTERVAL '14 hours', 'route-dortmund-sued-001', NOW() - INTERVAL '2 days'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'WAREHOUSE_C'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, NOW() - INTERVAL '1 day', 'lager', v.qty
FROM ord
CROSS JOIN (VALUES ('Wireless Mouse', 1, 29.99::NUMERIC), ('USB-C Hub', 1, 29.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- ============================================================
-- 6. DELIVERED orders (completed)
-- ============================================================

-- Order 1017: Koeln Deutz, delivered
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, delivered_at, created_at)
    SELECT u.id, 'ORD-WH-1017', 'alice@example.com', 'Alice Mueller',
        'Deutzer Freiheit 72', 'Koeln', '50679', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 29.99, 4.79,
        4.99, 'STANDARD', 'DELIVERED', 0.00, wl.id,
        NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', 'lager',
        'LKW-2024-001', NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days', NOW() - INTERVAL '7 days'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'MAIN'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, 1, 29.99, NOW() - INTERVAL '5 days', 'lager', 1
FROM ord JOIN products p ON p.name = 'Wireless Mouse';

-- Order 1018: Duesseldorf Bilk, delivered
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, delivered_at, created_at)
    SELECT u.id, 'ORD-WH-1018', 'bob@example.com', 'Bob Schmidt',
        'Friedrichstrasse 133', 'Duesseldorf', '40217', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 1299.99, 207.62,
        0.00, 'EXPRESS', 'DELIVERED', 0.00, wl.id,
        NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days', 'lager',
        'LKW-2024-101', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '8 days'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'bob' AND wl.code = 'WAREHOUSE_B'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, 1, 1299.99, NOW() - INTERVAL '6 days', 'lager', 1
FROM ord JOIN products p ON p.name = 'Laptop Pro 15';

-- Order 1019: Dortmund city, delivered
WITH ord AS (
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
        delivery_street, delivery_city, delivery_postal_code, delivery_country,
        payment_method_type, payment_masked_details, total_price, tax_amount,
        shipping_cost, shipping_method, status, discount_amount, fulfillment_warehouse_id,
        packing_started_at, packed_at, packed_by_user_id,
        truck_identifier, truck_assigned_at, delivered_at, created_at)
    SELECT u.id, 'ORD-WH-1019', 'alice@example.com', 'Alice Mueller',
        'Westenhellweg 102', 'Dortmund', '44137', 'Deutschland',
        'SEPA_DIRECT_DEBIT', 'DE89****4321', 549.98, 87.83,
        0.00, 'STANDARD', 'DELIVERED', 0.00, wl.id,
        NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days', 'lager',
        'LKW-2024-201', NOW() - INTERVAL '9 days', NOW() - INTERVAL '8 days', NOW() - INTERVAL '12 days'
    FROM users u, warehouse_locations wl
    WHERE u.username = 'alice' AND wl.code = 'WAREHOUSE_C'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time, picked_at, picked_by_user_id, picked_quantity)
SELECT ord.id, p.id, v.qty, v.price, NOW() - INTERVAL '10 days', 'lager', v.qty
FROM ord
CROSS JOIN (VALUES ('Standing Desk', 1, 499.99::NUMERIC), ('USB-C Hub', 1, 49.99::NUMERIC)) AS v(pname, qty, price)
JOIN products p ON p.name = v.pname;

-- ============================================================
-- 7. Update truck states to match assigned orders
-- ============================================================

-- LKW-2024-001: has IN_TRUCK orders -> LOADED status
UPDATE warehouse_trucks
SET status = 'LOADED', updated_at = NOW()
WHERE truck_identifier = 'LKW-2024-001';

-- LKW-2024-002: has SHIPPED orders -> DEPARTED status
UPDATE warehouse_trucks
SET status = 'DEPARTED', departure_time = NOW() - INTERVAL '18 hours', updated_at = NOW(),
    current_warehouse_location_id = NULL
WHERE truck_identifier = 'LKW-2024-002';

-- LKW-2024-101: has IN_TRUCK orders -> LOADED status
UPDATE warehouse_trucks
SET status = 'LOADED', updated_at = NOW()
WHERE truck_identifier = 'LKW-2024-101';

-- LKW-2024-201: has SHIPPED orders -> DEPARTED status
UPDATE warehouse_trucks
SET status = 'DEPARTED', departure_time = NOW() - INTERVAL '14 hours', updated_at = NOW(),
    current_warehouse_location_id = NULL
WHERE truck_identifier = 'LKW-2024-201';

-- ============================================================
-- Seller mapping
-- ============================================================
UPDATE products
SET seller_name = CASE
    WHEN name IN ('Digitaler Geschenkgutschein', 'Laptop Pro 15') THEN 'TechPartner GmbH'
    WHEN name IN ('Wireless Mouse', 'Standing Desk') THEN 'Green Devices AG'
    ELSE seller_name
END
WHERE name IN (
    'Digitaler Geschenkgutschein',
    'Laptop Pro 15',
    'Wireless Mouse',
    'Standing Desk'
);

UPDATE order_items oi
SET seller_name = COALESCE(NULLIF(p.seller_name, ''), 'Webshop')
FROM products p
WHERE oi.product_id = p.id
  AND oi.seller_name = 'Webshop';

-- ============================================================
-- Newsletter-Posts (Demo-Inhalte)
-- ============================================================
INSERT INTO newsletter_posts (title, content, category_id, author_id, published, published_at, created_at, updated_at)
SELECT
    '🎉 Sommerrabatt: Bis zu 30% auf ausgewählte Produkte',
    E'## Sommerangebote – jetzt zugreifen!\n\nDer Sommer ist da und wir feiern ihn mit unseren bisher besten Angeboten des Jahres.\n\n### Highlights\n\n- **Laptops & PCs** – bis zu 25% Rabatt\n- **Bürostühle & Schreibtische** – jetzt 30% günstiger\n- **Zubehör** – 2 kaufen, 1 gratis\n\n> Die Aktion gilt vom 01.07. bis 31.07. Solange der Vorrat reicht.\n\nJetzt im Shop stöbern und Lieblingsprodukte zum Schnäppchenpreis sichern!',
    (SELECT id FROM newsletter_categories WHERE slug = 'angebote'),
    (SELECT id FROM users WHERE username = 'admin'),
    true,
    NOW() - INTERVAL '5 days',
    NOW() - INTERVAL '5 days',
    NOW() - INTERVAL '5 days';

INSERT INTO newsletter_posts (title, content, category_id, author_id, published, published_at, created_at, updated_at)
SELECT
    'Neue Produkte im Sortiment: Ergonomie-Serie erweitert',
    E'## Erweiterte Ergonomie-Kollektion\n\nWir haben unser Sortiment um neue ergonomische Produkte erweitert, die deinen Arbeitsalltag noch angenehmer machen.\n\n### Neue Artikel\n\n**ErgoStand Pro X**\nDer höhenverstellbare Laptop-Ständer mit integriertem USB-Hub. Perfekt für das Home Office.\n\n**ComfortPad 3.0**\nGelkissen für Tastatur und Maus – ideal für lange Arbeitstage.\n\n**BackCare Chair Light**\nLeichter Bürostuhl mit Lendenwirbelstütze für unter 300 €.\n\nAlle neuen Produkte sind ab sofort im Shop verfügbar. Kostenloser Versand ab 50 €!',
    (SELECT id FROM newsletter_categories WHERE slug = 'produktneuheiten'),
    (SELECT id FROM users WHERE username = 'admin'),
    true,
    NOW() - INTERVAL '12 days',
    NOW() - INTERVAL '12 days',
    NOW() - INTERVAL '12 days';

INSERT INTO newsletter_posts (title, content, category_id, author_id, published, published_at, created_at, updated_at)
SELECT
    'Webshop Update: Neue Features für ein besseres Einkaufserlebnis',
    E'## Was ist neu?\n\nWir haben in den letzten Wochen hart daran gearbeitet, euren Einkauf noch angenehmer zu gestalten. Hier sind die wichtigsten Neuerungen:\n\n### Preisalarm\nSetze jetzt Preisalarme für deine Wunschprodukte. Du wirst automatisch benachrichtigt, sobald der Preis fällt.\n\n### Geteilte Merklisten\nTeile deine Merkliste mit Freunden und Familie – ideal für gemeinsame Wunschlisten.\n\n### Schnellerer Checkout\nUnser überarbeiteter Checkout-Prozess spart dir wertvolle Zeit.\n\nWir freuen uns auf euer Feedback! Schreibt uns über das Support-Ticket-System.',
    (SELECT id FROM newsletter_categories WHERE slug = 'news'),
    (SELECT id FROM users WHERE username = 'admin'),
    true,
    NOW() - INTERVAL '20 days',
    NOW() - INTERVAL '20 days',
    NOW() - INTERVAL '20 days';

INSERT INTO newsletter_posts (title, content, category_id, author_id, published, published_at, created_at, updated_at)
SELECT
    'Flash-Sale diesen Freitag: 24 Stunden, unschlagbare Preise',
    E'## Flash-Sale am Freitag!\n\nNur für 24 Stunden – von Freitag 00:00 bis Samstag 00:00 Uhr – gibt es exklusive Blitzangebote.\n\n**Was dich erwartet:**\n\n- Tagesangebote im Stundentakt\n- Limitierte Stückzahlen\n- Bis zu 50% Rabatt auf Top-Produkte\n\nTrage den Termin in deinen Kalender ein und sei rechtzeitig dabei – die besten Deals sind schnell weg!',
    (SELECT id FROM newsletter_categories WHERE slug = 'angebote'),
    (SELECT id FROM users WHERE username = 'admin'),
    false,
    NULL,
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day';

-- Newsletter-Abonnements für Demo-Nutzer
INSERT INTO newsletter_subscriptions (user_id, category_id, subscribed, updated_at)
SELECT u.id, c.id, true, NOW()
FROM users u, newsletter_categories c
WHERE u.username = 'alice'
ON CONFLICT (user_id, category_id) DO NOTHING;

INSERT INTO newsletter_subscriptions (user_id, category_id, subscribed, updated_at)
SELECT u.id, c.id, CASE WHEN c.slug = 'angebote' THEN true ELSE false END, NOW()
FROM users u, newsletter_categories c
WHERE u.username = 'bob'
ON CONFLICT (user_id, category_id) DO NOTHING;

-- ============================================================
-- Über-Uns-Sektionen (Demo-Inhalte)
-- ============================================================
INSERT INTO about_us_sections (title, content, display_order, created_at, updated_at) VALUES
(
    'Wer wir sind',
    E'## Unser Unternehmen\n\nWir sind ein junges, innovatives Unternehmen aus dem Herzen Deutschlands, das es sich zur Aufgabe gemacht hat, Shopping neu zu denken.\n\nSeit unserer Gründung im Jahr 2022 wachsen wir stetig und bieten unseren Kunden ein einzigartiges Online-Einkaufserlebnis – von hochwertigen Produkten bis hin zu persönlichem Kundenservice.\n\n**Unser Versprechen:** Qualität, Transparenz und Nachhaltigkeit in allem, was wir tun.',
    0,
    NOW(),
    NOW()
),
(
    'Unsere Mission',
    E'## Mission & Vision\n\nUnsere Mission ist es, Online-Shopping so einfach, sicher und angenehm wie möglich zu gestalten.\n\n### Was uns antreibt\n\n- **Kundenzufriedenheit** steht bei uns an erster Stelle\n- Wir setzen auf **nachhaltige Produkte** und umweltfreundliche Verpackungen\n- **Faire Preise** ohne versteckte Kosten\n- Ein **transparenter Marktplatz**, auf dem Verkäufer und Käufer sich auf Augenhöhe begegnen\n\nWir glauben daran, dass guter Handel auf gegenseitigem Vertrauen basiert.',
    1,
    NOW(),
    NOW()
),
(
    'Unser Team',
    E'## Die Menschen hinter dem Webshop\n\nUnser Team besteht aus leidenschaftlichen Entwicklern, kreativen Designern und erfahrenen Kaufleuten, die täglich daran arbeiten, euer Einkaufserlebnis zu verbessern.\n\nWir kommen aus verschiedenen Ecken Deutschlands und teilen eine gemeinsame Vision: einen Webshop zu bauen, den wir selbst gerne nutzen würden.\n\n> *"Wir bauen nicht nur einen Shop, wir bauen eine Community."*\n> — Das Gründerteam\n\nLust auf Mitarbeit? Wir freuen uns immer über motivierte Talente!',
    2,
    NOW(),
    NOW()
),
(
    'Nachhaltigkeit & Verantwortung',
    E'## Unser Beitrag zur Umwelt\n\nNachhaltigkeit ist für uns kein Marketing-Begriff, sondern gelebte Praxis:\n\n- Alle Verpackungen bestehen aus **recyceltem oder recycelbarem Material**\n- Wir gleichen unseren CO₂-Fußabdruck durch zertifizierte **Klimaschutzprojekte** aus\n- Unser **Öko-Score** zeigt transparent, wie nachhaltig jedes Produkt ist\n- Mit dem **Trade-In-Programm** geben wir gebrauchten Geräten ein zweites Leben\n\nGemeinsam können wir einen Unterschied machen.',
    3,
    NOW(),
    NOW()
);

-- ============================================================
-- Jobs / Karriere (Demo-Daten)
-- ============================================================
INSERT INTO job_postings (title, description, employment_type, location, status, display_order, created_at, updated_at) VALUES
(
    'Frontend-Entwickler (React)',
    E'## Deine Aufgaben\n\n- Entwicklung und Weiterentwicklung unserer React-basierten Webshop-Oberfläche\n- Enge Zusammenarbeit mit UX/Design und Backend-Teams\n- Code Reviews und technische Dokumentation\n\n## Dein Profil\n\n- Mindestens 2 Jahre Erfahrung mit React\n- Kenntnisse in TypeScript, CSS und REST-APIs\n- Teamfähigkeit und eigenverantwortliches Arbeiten\n\n## Was wir bieten\n\n- Flexible Arbeitszeiten und Remote-Option\n- Modernes Tech-Stack\n- Flache Hierarchien und kurze Entscheidungswege',
    'VOLLZEIT', 'Berlin', 'ACTIVE', 0,
    NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'
),
(
    'Backend-Entwicklerin / Backend-Entwickler (Java / Spring Boot)',
    E'## Deine Aufgaben\n\n- Entwicklung und Pflege unserer Spring-Boot-Microservices\n- Datenbankmodellierung und Query-Optimierung (PostgreSQL)\n- Mitgestaltung der API-Architektur (REST)\n\n## Dein Profil\n\n- Sehr gute Java-Kenntnisse (Java 17+)\n- Erfahrung mit Spring Boot, JPA/Hibernate und Flyway\n- Grundkenntnisse in Docker und CI/CD\n\n## Was wir bieten\n\n- 30 Tage Urlaub\n- Weiterbildungsbudget\n- Gemeinsame Team-Events',
    'VOLLZEIT', 'Berlin', 'ACTIVE', 1,
    NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'
),
(
    'Werkstudent/in Kundenservice',
    E'## Deine Aufgaben\n\n- Beantwortung von Kundenanfragen per E-Mail und Chat\n- Bearbeitung von Retouren und Beschwerden\n- Pflege unseres FAQ-Bereichs\n\n## Dein Profil\n\n- Laufendes Studium (BWL, Kommunikation o. Ä.)\n- Sehr gute Deutschkenntnisse in Wort und Schrift\n- Freundliches und lösungsorientiertes Auftreten\n\n## Was wir bieten\n\n- Flexible Arbeitszeiten passend zum Studium\n- Übernahme-Möglichkeit nach dem Studium',
    'TEILZEIT', 'Hamburg', 'ACTIVE', 2,
    NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'
),
(
    'Minijob: Lagermitarbeiter/in',
    E'## Deine Aufgaben\n\n- Wareneingangskontrolle und Einlagerung\n- Kommissionierung von Bestellungen\n- Pflege der Lagerfläche\n\n## Dein Profil\n\n- Körperliche Belastbarkeit\n- Zuverlässigkeit und Pünktlichkeit\n- Erfahrung im Lager von Vorteil, aber kein Muss\n\n## Was wir bieten\n\n- Fester Stundenlohn über Mindestlohn\n- Kollegiales Team',
    'MINIJOB', 'Hamburg', 'ACTIVE', 3,
    NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'
),
(
    'DevOps-Engineer (m/w/d)',
    E'## Deine Aufgaben\n\n- Aufbau und Pflege unserer CI/CD-Pipelines\n- Container-Orchestrierung mit Docker und Kubernetes\n- Monitoring und Incident-Management\n\n## Dein Profil\n\n- Erfahrung mit Docker, Kubernetes, GitHub Actions\n- Linux-Kenntnisse\n- Interesse an Cloud-nativen Architekturen\n\n## Was wir bieten\n\n- 100 % Remote möglich\n- Home-Office-Ausstattung inklusive',
    'VOLLZEIT', 'Remote', 'INACTIVE', 4,
    NOW() - INTERVAL '30 days', NOW() - INTERVAL '15 days'
),
(
    'Praktikum Marketing & Social Media',
    E'## Deine Aufgaben\n\n- Erstellung von Social-Media-Content (Instagram, LinkedIn)\n- Unterstützung bei Newsletter-Kampagnen\n- Analyse von Marketing-KPIs\n\n## Dein Profil\n\n- Studium im Bereich Marketing, Medien oder Kommunikation\n- Kreativität und Gespür für Trends\n- Erfahrung mit Canva oder Adobe Express von Vorteil\n\n## Was wir bieten\n\n- Praxisnahes Arbeiten im echten Unternehmen\n- Pflichtpraktikum oder freiwilliges Praktikum möglich',
    'TEILZEIT', 'Berlin', 'ARCHIVED', 5,
    NOW() - INTERVAL '60 days', NOW() - INTERVAL '30 days'
);

-- Bewerbungen (offene + bearbeitete)
INSERT INTO job_applications (job_posting_id, applicant_name, applicant_email, applicant_phone, motivation_text, status, created_at, updated_at)
VALUES
(
    (SELECT id FROM job_postings WHERE title = 'Frontend-Entwickler (React)'),
    'Max Mustermann',
    'max.mustermann@example.com',
    '+49 151 12345678',
    E'Sehr geehrte Damen und Herren,\n\nals begeisterter React-Entwickler mit 3 Jahren Berufserfahrung möchte ich mich herzlich für die ausgeschriebene Stelle bewerben. In meiner aktuellen Tätigkeit habe ich umfangreiche Erfahrungen mit modernen React-Patterns, TypeScript und REST-APIs gesammelt.\n\nIch freue mich auf ein persönliches Gespräch.',
    'OPEN',
    NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'
),
(
    (SELECT id FROM job_postings WHERE title = 'Frontend-Entwickler (React)'),
    'Lena Schmidt',
    'lena.schmidt@example.com',
    NULL,
    E'Hallo,\n\nich bin Absolventin der TU Berlin (Informatik) und suche meinen ersten Job als Frontend-Entwicklerin. React und JavaScript sind meine Leidenschaft. Mein Portfolio: github.com/lena-dev.',
    'ACCEPTED',
    NOW() - INTERVAL '7 days', NOW() - INTERVAL '2 days'
),
(
    (SELECT id FROM job_postings WHERE title = 'Backend-Entwicklerin / Backend-Entwickler (Java / Spring Boot)'),
    'Jonas Weber',
    'jonas.weber@example.com',
    '+49 176 87654321',
    E'Guten Tag,\n\nich bringe 5 Jahre Erfahrung in der Java-Backend-Entwicklung mit, davon 3 Jahre mit Spring Boot und PostgreSQL. Ich schätze saubere Architektur und testgetriebene Entwicklung.',
    'OPEN',
    NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'
),
(
    (SELECT id FROM job_postings WHERE title = 'Backend-Entwicklerin / Backend-Entwickler (Java / Spring Boot)'),
    'Anna Bauer',
    'anna.bauer@example.com',
    '+49 160 11223344',
    NULL,
    'REJECTED',
    NOW() - INTERVAL '6 days', NOW() - INTERVAL '1 day'
),
(
    (SELECT id FROM job_postings WHERE title = 'Werkstudent/in Kundenservice'),
    'Tom Fischer',
    'tom.fischer@example.com',
    NULL,
    E'Ich studiere BWL im 4. Semester und suche eine Werkstudentenstelle, die mir praktische Erfahrungen ermöglicht. Kundenservice und Kommunikation liegen mir sehr.',
    'OPEN',
    NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'
);
