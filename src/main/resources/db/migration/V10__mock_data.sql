-- =============================================================================
-- V10 – Demo-Daten für Rabatt- und Promotionsmanagement (Issue #87)
-- Passwort für alle Demo-Accounts: Demo12345!
-- BCrypt-Hash (cost 10) von "Demo12345!"
-- =============================================================================

-- Demo-Vertriebsmitarbeiter
INSERT INTO users (username, email, password_hash, role, user_type, active)
VALUES (
    'demo_vertrieb',
    'vertrieb@demo.de',
    '$2a$10$8bCh1J0CQV8xLaHMcqEi7eCz5X5RqbVSG2VqTpWZzY5G.5X8P2GqO',
    'SALES_EMPLOYEE',
    'PRIVATE',
    TRUE
) ON CONFLICT (username) DO NOTHING;

-- Demo-Kunden
INSERT INTO users (username, email, password_hash, role, user_type, customer_number, active)
VALUES
    ('demo_kunde1', 'kunde1@demo.de',
     '$2a$10$8bCh1J0CQV8xLaHMcqEi7eCz5X5RqbVSG2VqTpWZzY5G.5X8P2GqO',
     'CUSTOMER', 'PRIVATE',
     'K-' || to_char(nextval('customer_number_sequence'), 'FM000000'),
     TRUE),
    ('demo_kunde2', 'kunde2@demo.de',
     '$2a$10$8bCh1J0CQV8xLaHMcqEi7eCz5X5RqbVSG2VqTpWZzY5G.5X8P2GqO',
     'CUSTOMER', 'BUSINESS',
     'K-' || to_char(nextval('customer_number_sequence'), 'FM000000'),
     TRUE)
ON CONFLICT (username) DO NOTHING;

-- Business-Info für Demo-Geschäftskunden
INSERT INTO business_info (user_id, company_name, industry, company_size)
SELECT u.id, 'Demo GmbH', 'IT & Software', '10–50'
FROM users u
WHERE u.username = 'demo_kunde2'
  AND NOT EXISTS (SELECT 1 FROM business_info bi WHERE bi.user_id = u.id);

-- Promoted-Flag auf den ersten 3 kaufbaren Produkten setzen
UPDATE products
SET promoted = TRUE
WHERE id IN (
    SELECT id FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 3
);

-- Rabatte und Coupons über DO-Block (referenziert User per username)
DO $$
DECLARE
    v_sales    BIGINT;
    v_cust1    BIGINT;
    v_cust2    BIGINT;
    v_prod1    BIGINT;
    v_prod2    BIGINT;
    v_prod3    BIGINT;
BEGIN
    SELECT id INTO v_sales FROM users WHERE username = 'demo_vertrieb';
    SELECT id INTO v_cust1  FROM users WHERE username = 'demo_kunde1';
    SELECT id INTO v_cust2  FROM users WHERE username = 'demo_kunde2';
    SELECT id INTO v_prod1  FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 0;
    SELECT id INTO v_prod2  FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 1;
    SELECT id INTO v_prod3  FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 2;

    -- Befristeter Rabatt: demo_kunde1 auf Produkt 1 (90 Tage)
    IF v_sales IS NOT NULL AND v_cust1 IS NOT NULL AND v_prod1 IS NOT NULL THEN
        INSERT INTO discounts (customer_id, product_id, discount_percent, valid_from, valid_until, created_by_user_id)
        VALUES (v_cust1, v_prod1, 15.00, CURRENT_DATE, CURRENT_DATE + INTERVAL '90 days', v_sales)
        ON CONFLICT (customer_id, product_id) DO NOTHING;
    END IF;

    -- Unbefristeter Rabatt: demo_kunde1 auf Produkt 2
    IF v_sales IS NOT NULL AND v_cust1 IS NOT NULL AND v_prod2 IS NOT NULL THEN
        INSERT INTO discounts (customer_id, product_id, discount_percent, valid_from, valid_until, created_by_user_id)
        VALUES (v_cust1, v_prod2, 10.00, CURRENT_DATE, NULL, v_sales)
        ON CONFLICT (customer_id, product_id) DO NOTHING;
    END IF;

    -- Befristeter Rabatt: demo_kunde2 auf Produkt 1 (30 Tage)
    IF v_sales IS NOT NULL AND v_cust2 IS NOT NULL AND v_prod1 IS NOT NULL THEN
        INSERT INTO discounts (customer_id, product_id, discount_percent, valid_from, valid_until, created_by_user_id)
        VALUES (v_cust2, v_prod1, 20.00, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', v_sales)
        ON CONFLICT (customer_id, product_id) DO NOTHING;
    END IF;

    -- Unbefristeter Rabatt: demo_kunde2 auf Produkt 3
    IF v_sales IS NOT NULL AND v_cust2 IS NOT NULL AND v_prod3 IS NOT NULL THEN
        INSERT INTO discounts (customer_id, product_id, discount_percent, valid_from, valid_until, created_by_user_id)
        VALUES (v_cust2, v_prod3, 25.00, CURRENT_DATE, NULL, v_sales)
        ON CONFLICT (customer_id, product_id) DO NOTHING;
    END IF;

    -- Coupons für demo_kunde1
    IF v_sales IS NOT NULL AND v_cust1 IS NOT NULL THEN
        INSERT INTO coupons (customer_id, code, discount_percent, valid_until, used, created_by_user_id)
        VALUES (v_cust1, 'WELCOME10', 10.00, CURRENT_DATE + INTERVAL '365 days', FALSE, v_sales)
        ON CONFLICT (code) DO NOTHING;

        INSERT INTO coupons (customer_id, code, discount_percent, valid_until, used, created_by_user_id)
        VALUES (v_cust1, 'SUMMER15', 15.00, CURRENT_DATE + INTERVAL '180 days', FALSE, v_sales)
        ON CONFLICT (code) DO NOTHING;
    END IF;

    -- Coupons für demo_kunde2
    IF v_sales IS NOT NULL AND v_cust2 IS NOT NULL THEN
        INSERT INTO coupons (customer_id, code, discount_percent, valid_until, used, created_by_user_id)
        VALUES (v_cust2, 'BUSINESS20', 20.00, NULL, FALSE, v_sales)
        ON CONFLICT (code) DO NOTHING;

        INSERT INTO coupons (customer_id, code, discount_percent, valid_until, used, created_by_user_id)
        VALUES (v_cust2, 'VIP25', 25.00, CURRENT_DATE + INTERVAL '365 days', FALSE, v_sales)
        ON CONFLICT (code) DO NOTHING;
    END IF;
END $$;
