-- ============================================================
-- Development seed data — run ONCE after Flyway migrations
-- Usage: psql -U webshop -d webshop -f src/main/resources/db/dev-seed.sql
-- ============================================================

-- ── Users ────────────────────────────────────────────────────
-- All passwords are: Password1!
-- BCrypt hash for "Password1!" (cost 10)
INSERT INTO users (username, email, password_hash, role, user_type, customer_number) VALUES
  ('alice',   'alice@example.com',   '$2a$10$7EqJtq98hPqEX7fNZaFWoO5Pl9MhFzDgBExqSiNq.WQgYMUFMKG.G', 'CUSTOMER',       'PRIVATE',  nextval('customer_number_sequence')),
  ('bob',     'bob@example.com',     '$2a$10$7EqJtq98hPqEX7fNZaFWoO5Pl9MhFzDgBExqSiNq.WQgYMUFMKG.G', 'CUSTOMER',       'BUSINESS', nextval('customer_number_sequence')),
  ('carol',   'carol@example.com',   '$2a$10$7EqJtq98hPqEX7fNZaFWoO5Pl9MhFzDgBExqSiNq.WQgYMUFMKG.G', 'EMPLOYEE',       'PRIVATE',  NULL),
  ('dave',    'dave@example.com',    '$2a$10$7EqJtq98hPqEX7fNZaFWoO5Pl9MhFzDgBExqSiNq.WQgYMUFMKG.G', 'SALES_EMPLOYEE', 'PRIVATE',  NULL),
  ('admin',   'admin@example.com',   '$2a$10$7EqJtq98hPqEX7fNZaFWoO5Pl9MhFzDgBExqSiNq.WQgYMUFMKG.G', 'ADMIN',          'PRIVATE',  NULL);

-- ── Business info for bob ────────────────────────────────────
INSERT INTO business_info (user_id, company_name, industry, company_size)
  SELECT id, 'Bob Corp GmbH', 'Technology', '10-50' FROM users WHERE username = 'bob';

-- ── Delivery address for alice ───────────────────────────────
INSERT INTO delivery_addresses (user_id, street, city, postal_code, country)
  SELECT id, 'Hauptstraße 1', 'Bielefeld', '33602', 'Germany' FROM users WHERE username = 'alice';

-- ── Payment methods ──────────────────────────────────────────
INSERT INTO payment_methods (user_id, method_type, masked_details)
  SELECT id, 'SEPA_DIRECT_DEBIT', 'DE89****4321' FROM users WHERE username = 'alice';

-- ── Products ─────────────────────────────────────────────────
INSERT INTO products (name, description, image_url, recommended_retail_price, category, purchasable, promoted) VALUES
  ('Laptop Pro 15',        'High-performance laptop with 15" display',    'https://placehold.co/400x300?text=Laptop',   1299.99, 'Electronics', TRUE,  TRUE),
  ('Wireless Mouse',       'Ergonomic wireless mouse, 2.4 GHz',           'https://placehold.co/400x300?text=Mouse',      29.99, 'Electronics', TRUE,  FALSE),
  ('Standing Desk',        'Height-adjustable standing desk 140x70 cm',   'https://placehold.co/400x300?text=Desk',      499.99, 'Furniture',   TRUE,  FALSE),
  ('USB-C Hub',            '7-in-1 USB-C hub with HDMI and SD card',      'https://placehold.co/400x300?text=Hub',        49.99, 'Electronics', TRUE,  FALSE),
  ('Office Chair',         'Lumbar support mesh chair',                   'https://placehold.co/400x300?text=Chair',     349.99, 'Furniture',   TRUE,  TRUE),
  ('Notebook (Draft)',     'Not yet available to customers',               NULL,                                           9.99,  'Stationery',  FALSE, FALSE);

-- ── Discounts ────────────────────────────────────────────────
-- alice gets 10% off Wireless Mouse (permanent)
INSERT INTO discounts (customer_id, product_id, discount_percent, valid_from, valid_until)
  SELECT u.id, p.id, 10.00, CURRENT_DATE, NULL
  FROM users u, products p
  WHERE u.username = 'alice' AND p.name = 'Wireless Mouse';

-- bob gets 15% off Laptop Pro 15 until end of year
INSERT INTO discounts (customer_id, product_id, discount_percent, valid_from, valid_until)
  SELECT u.id, p.id, 15.00, CURRENT_DATE, (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::DATE
  FROM users u, products p
  WHERE u.username = 'bob' AND p.name = 'Laptop Pro 15';

-- ── Coupon ───────────────────────────────────────────────────
INSERT INTO coupons (customer_id, code, discount_percent, valid_until, used)
  SELECT id, 'WELCOME10', 10.00, CURRENT_DATE + INTERVAL '30 days', FALSE
  FROM users WHERE username = 'alice';

-- ── Cart items for alice ─────────────────────────────────────
INSERT INTO cart_items (user_id, product_id, quantity)
  SELECT u.id, p.id, 2
  FROM users u, products p
  WHERE u.username = 'alice' AND p.name = 'Wireless Mouse';

-- ── A completed order for alice ──────────────────────────────
WITH new_order AS (
  INSERT INTO orders (customer_id, total_price, tax_amount, shipping_cost, status)
    SELECT id, 35.69, 5.70, 0.00, 'DELIVERED'
    FROM users WHERE username = 'alice'
    RETURNING id
)
INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
  SELECT new_order.id, p.id, 1, 29.99
  FROM new_order, products p
  WHERE p.name = 'Wireless Mouse';
