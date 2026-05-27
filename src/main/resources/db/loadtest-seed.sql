-- ============================================================
-- Load-test seed data — generates a realistic, large data volume
-- for performance/load testing (see docs/loadtest.md).
--
-- Run AFTER Flyway migrations, on a FRESH database (e.g. after
-- `./dev.bat rebuild`). Re-running on a non-fresh DB fails on the
-- UNIQUE constraints (username/email/sku), which is intentional.
--
-- Usage (PowerShell):
--   Get-Content src\main\resources\db\loadtest-seed.sql | docker exec -i webshop-postgres psql -U webshop -d webshop
-- Usage (bash):
--   docker exec -i webshop-postgres psql -U webshop -d webshop < src/main/resources/db/loadtest-seed.sql
--
-- Counts can be tuned via the two psql variables below.
-- ============================================================

\set product_count 5000
\set customer_count 1000

BEGIN;

-- ── Bulk products ───────────────────────────────────────────────────────────
-- All purchasable so they show up in the customer catalog endpoint under load.
INSERT INTO products (
    name, description, recommended_retail_price, co2_emission_kg,
    eco_score, category, stock, sku, purchasable, promoted
)
SELECT
    'Load Test Produkt ' || series,
    'Automatisch generiertes Produkt fuer den Lasttest, Nummer ' || series,
    ROUND((5 + random() * 995)::numeric, 2),
    ROUND((random() * 200)::numeric, 3),
    (ARRAY['A', 'B', 'C', 'D', 'E'])[1 + floor(random() * 5)::int],
    (ARRAY['Electronics', 'Furniture', 'Stationery', 'Kitchen', 'Sports'])[1 + floor(random() * 5)::int],
    (10 + floor(random() * 490))::int,
    'LOAD-SKU-' || series,
    TRUE,
    (random() < 0.1)
FROM generate_series(1, :product_count) AS series;

-- ── Bulk customers (all share the password "Password1!") ─────────────────────
INSERT INTO users (username, email, password_hash, user_type, customer_number)
SELECT
    'loaduser' || series,
    'loaduser' || series || '@example.test',
    '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze',
    'PRIVATE',
    'K-' || to_char(nextval('customer_number_sequence'), 'FM000000')
FROM generate_series(1, :customer_count) AS series;

INSERT INTO user_roles (user_id, role)
SELECT id, 'CUSTOMER' FROM users WHERE username LIKE 'loaduser%';

-- ── Dedicated high-stock product for the OPTIONAL real-order scenario ─────────
-- A practically unlimited stock so concurrent order placement never depletes it
-- (otherwise out-of-stock validation errors would pollute the error rate).
INSERT INTO products (
    name, description, recommended_retail_price, eco_score, category, stock, sku, purchasable
)
VALUES (
    'Load Test Bestell Artikel',
    'Hochlager-Artikel fuer den optionalen Bestell-Lasttest (siehe docs/loadtest.md)',
    9.99, 'C', 'Loadtest', 100000000, 'LOAD-ORDER-PRODUCT', TRUE
);

COMMIT;

-- Quick verification
SELECT
    (SELECT count(*) FROM products WHERE sku LIKE 'LOAD-SKU-%')      AS load_products,
    (SELECT count(*) FROM users    WHERE username LIKE 'loaduser%')  AS load_customers;
