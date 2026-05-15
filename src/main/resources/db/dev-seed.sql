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

-- Delivery address for alice
INSERT INTO delivery_addresses (user_id, street, city, postal_code, country)
SELECT id, 'Hauptstrasse 1', 'Bielefeld', '33602', 'Germany'
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
  ('Laptop Pro 15', 'High-performance laptop with 15" display', '/laptop-pro-15.jpg', 1299.99, 214.500, 'E', 'Electronics', 8, 'LAPTOP-PRO-15', 'A-01-01', TRUE, TRUE),
  ('Wireless Mouse', 'Ergonomic wireless mouse, 2.4 GHz', '/wireless-mouse.jpg', 29.99, 2.100, 'B', 'Electronics', 120, 'WIRELESS-MOUSE', 'A-03-07', TRUE, FALSE),
  ('Standing Desk', 'Height-adjustable standing desk 140x70 cm', '/standing-desk.jpg', 499.99, 58.750, 'D', 'Furniture', 12, 'STANDING-DESK', 'B-01-03', TRUE, FALSE),
  ('USB-C Hub', '7-in-1 USB-C hub with HDMI and SD card', '/usb-c-hub.jpg', 49.99, 5.400, 'C', 'Electronics', 40, 'USB-C-HUB', 'A-04-02', TRUE, FALSE),
  ('Office Chair', 'Lumbar support mesh chair', '/office-chair.jpg', 349.99, 33.200, 'D', 'Furniture', 18, 'OFFICE-CHAIR', 'B-02-05', TRUE, TRUE),
  ('Notebook (Draft)', 'Not yet available to customers', NULL, 9.99, 0.350, 'A', 'Stationery', 0, 'NOTEBOOK-DRAFT', 'C-01-01', FALSE, FALSE);

-- Warehouse stock per location
INSERT INTO warehouse_product_stocks (product_id, warehouse_location_id, quantity)
SELECT
    p.id,
    wl.id,
    CASE
        WHEN wl.code = 'MAIN' THEN p.stock
        WHEN wl.code = 'WAREHOUSE_B' AND p.category = 'Furniture' THEN GREATEST(0, p.stock / 2)
        WHEN wl.code = 'WAREHOUSE_C' AND p.category = 'Electronics' THEN GREATEST(0, p.stock / 3)
        ELSE 0
    END
FROM products p
CROSS JOIN warehouse_locations wl
WHERE wl.code IN ('MAIN', 'WAREHOUSE_B', 'WAREHOUSE_C')
ON CONFLICT (product_id, warehouse_location_id) DO NOTHING;

-- Advertisements / Werbeflächen
INSERT INTO advertisements (title, description, content_type, image_url, target_url, active, start_date, end_date)
SELECT title, description, content_type, image_url, target_url, active, start_date, end_date
FROM (
    VALUES
      ('Sommeraktion im Home Office', 'Ergonomische Favoriten, clevere Bundles und schnelle Upgrades für deinen Arbeitsplatz.', 'IMAGE', '/standing-desk.jpg', '/products/3', TRUE, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days'),
      ('Top Auswahl für Entscheider', 'Vergleiche Bestseller, Empfehlungen und sofort verfügbare Geräte direkt im Sortiment.', 'TEXT', NULL, '/', TRUE, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days'),
      ('Verkäufer-Aktion vorbereiten', 'Diese Werbefläche ist angelegt, aber noch nicht aktiv geschaltet.', 'TEXT', NULL, '/admin/marketing/placements', FALSE, CURRENT_DATE + INTERVAL '7 days', CURRENT_DATE + INTERVAL '21 days')
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
WHERE username = 'alice';

-- Cart items for alice
INSERT INTO cart_items (user_id, product_id, quantity)
SELECT u.id, p.id, 2
FROM users u, products p
WHERE u.username = 'alice' AND p.name = 'Wireless Mouse';

-- Warehouse demo orders
WITH confirmed_order AS (
    INSERT INTO orders (
        customer_id,
        order_number,
        customer_email,
        customer_name,
        delivery_street,
        delivery_city,
        delivery_postal_code,
        delivery_country,
        payment_method_type,
        payment_masked_details,
        total_price,
        tax_amount,
        shipping_cost,
        shipping_method,
        status,
        discount_amount,
        created_at
    )
    SELECT
        id,
        'ORD-WH-1001',
        'alice@example.com',
        'alice',
        'Hauptstrasse 1',
        'Bielefeld',
        '33602',
        'Germany',
        'SEPA_DIRECT_DEBIT',
        'DE89****4321',
        64.47,
        10.29,
        4.99,
        'STANDARD',
        'CONFIRMED',
        0.00,
        NOW() - INTERVAL '1 day'
    FROM users
    WHERE username = 'alice'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
SELECT confirmed_order.id, p.id, order_data.quantity, order_data.price_at_order_time
FROM confirmed_order
JOIN (
    VALUES
        ('Wireless Mouse', 1, 29.99::NUMERIC),
        ('USB-C Hub', 1, 29.49::NUMERIC)
) AS order_data(product_name, quantity, price_at_order_time) ON TRUE
JOIN products p ON p.name = order_data.product_name;

WITH packed_order AS (
    INSERT INTO orders (
        customer_id,
        order_number,
        customer_email,
        customer_name,
        delivery_street,
        delivery_city,
        delivery_postal_code,
        delivery_country,
        payment_method_type,
        payment_masked_details,
        total_price,
        tax_amount,
        shipping_cost,
        shipping_method,
        status,
        discount_amount,
        created_at
    )
    SELECT
        id,
        'ORD-WH-1002',
        'bob@example.com',
        'bob',
        'Industriestrasse 10',
        'Paderborn',
        '33098',
        'Germany',
        'SEPA_DIRECT_DEBIT',
        'DE89****4321',
        594.99,
        95.00,
        0.00,
        'STANDARD',
        'PACKED_IN_WAREHOUSE',
        0.00,
        NOW() - INTERVAL '18 hours'
    FROM users
    WHERE username = 'bob'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
SELECT packed_order.id, p.id, 1, 499.99
FROM packed_order
JOIN products p ON p.name = 'Standing Desk';

WITH in_truck_order AS (
    INSERT INTO orders (
        customer_id,
        order_number,
        customer_email,
        customer_name,
        delivery_street,
        delivery_city,
        delivery_postal_code,
        delivery_country,
        payment_method_type,
        payment_masked_details,
        total_price,
        tax_amount,
        shipping_cost,
        shipping_method,
        status,
        discount_amount,
        truck_identifier,
        created_at
    )
    SELECT
        id,
        'ORD-WH-1003',
        'alice@example.com',
        'alice',
        'Hauptstrasse 1',
        'Bielefeld',
        '33602',
        'Germany',
        'SEPA_DIRECT_DEBIT',
        'DE89****4321',
        709.97,
        113.39,
        0.00,
        'EXPRESS',
        'IN_TRUCK',
        0.00,
        'LKW-01',
        NOW() - INTERVAL '8 hours'
    FROM users
    WHERE username = 'alice'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
SELECT in_truck_order.id, p.id, order_data.quantity, order_data.price_at_order_time
FROM in_truck_order
JOIN (
    VALUES
        ('Office Chair', 1, 349.99::NUMERIC),
        ('Wireless Mouse', 2, 29.99::NUMERIC)
) AS order_data(product_name, quantity, price_at_order_time) ON TRUE
JOIN products p ON p.name = order_data.product_name;

WITH shipped_order AS (
    INSERT INTO orders (
        customer_id,
        order_number,
        customer_email,
        customer_name,
        delivery_street,
        delivery_city,
        delivery_postal_code,
        delivery_country,
        payment_method_type,
        payment_masked_details,
        total_price,
        tax_amount,
        shipping_cost,
        shipping_method,
        status,
        discount_amount,
        truck_identifier,
        created_at
    )
    SELECT
        id,
        'ORD-WH-1004',
        'bob@example.com',
        'bob',
        'Industriestrasse 10',
        'Paderborn',
        '33098',
        'Germany',
        'SEPA_DIRECT_DEBIT',
        'DE89****4321',
        1364.98,
        217.85,
        0.00,
        'STANDARD',
        'SHIPPED',
        0.00,
        'LKW-02',
        NOW() - INTERVAL '4 hours'
    FROM users
    WHERE username = 'bob'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
SELECT shipped_order.id, p.id, 1, 1299.99
FROM shipped_order
JOIN products p ON p.name = 'Laptop Pro 15';

WITH delivered_order AS (
    INSERT INTO orders (
        customer_id,
        order_number,
        customer_email,
        customer_name,
        delivery_street,
        delivery_city,
        delivery_postal_code,
        delivery_country,
        payment_method_type,
        payment_masked_details,
        total_price,
        tax_amount,
        shipping_cost,
        shipping_method,
        status,
        discount_amount,
        created_at
    )
    SELECT
        id,
        'ORD-WH-1005',
        'alice@example.com',
        'alice',
        'Hauptstrasse 1',
        'Bielefeld',
        '33602',
        'Germany',
        'SEPA_DIRECT_DEBIT',
        'DE89****4321',
        35.69,
        5.70,
        0.00,
        'STANDARD',
        'DELIVERED',
        0.00,
        NOW() - INTERVAL '14 days'
    FROM users
    WHERE username = 'alice'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
SELECT delivered_order.id, p.id, 1, 29.99
FROM delivered_order
JOIN products p ON p.name = 'Wireless Mouse';
