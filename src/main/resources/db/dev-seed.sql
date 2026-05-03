-- ============================================================
-- Development seed data - run once after Flyway migrations
-- Usage:
-- Get-Content src\main\resources\db\dev-seed.sql | docker exec -i webshop-postgres psql -U webshop -d webshop
-- ============================================================

-- Users
-- All passwords are: Password1!
INSERT INTO users (username, email, password_hash, user_type, customer_number) VALUES
  ('alice', 'alice@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'PRIVATE', nextval('customer_number_sequence')),
  ('bob', 'bob@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'BUSINESS', nextval('customer_number_sequence')),
  ('carol', 'carol@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'INTERNAL', NULL),
  ('dave', 'dave@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'INTERNAL', NULL),
  ('lager', 'lager@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'INTERNAL', NULL),
  ('admin', 'admin@example.com', '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze', 'INTERNAL', NULL);

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
    warehouse_position,
    purchasable,
    promoted
) VALUES
  ('Laptop Pro 15', 'High-performance laptop with 15" display', '/laptop-pro-15.jpg', 1299.99, 214.500, 'E', 'Electronics', 8, 'A-01-01', TRUE, TRUE),
  ('Wireless Mouse', 'Ergonomic wireless mouse, 2.4 GHz', '/wireless-mouse.jpg', 29.99, 2.100, 'B', 'Electronics', 120, 'A-03-07', TRUE, FALSE),
  ('Standing Desk', 'Height-adjustable standing desk 140x70 cm', '/standing-desk.jpg', 499.99, 58.750, 'D', 'Furniture', 12, 'B-01-03', TRUE, FALSE),
  ('USB-C Hub', '7-in-1 USB-C hub with HDMI and SD card', '/usb-c-hub.jpg', 49.99, 5.400, 'C', 'Electronics', 40, 'A-04-02', TRUE, FALSE),
  ('Office Chair', 'Lumbar support mesh chair', '/office-chair.jpg', 349.99, 33.200, 'D', 'Furniture', 18, 'B-02-05', TRUE, TRUE),
  ('Notebook (Draft)', 'Not yet available to customers', NULL, 9.99, 0.350, 'A', 'Stationery', 0, 'C-01-01', FALSE, FALSE);

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
