CREATE TABLE seller_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(180) NOT NULL UNIQUE,
    commission_rate NUMERIC(5,2) NOT NULL DEFAULT 12.50,
    payout_iban VARCHAR(34),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE seller_payouts (
    id BIGSERIAL PRIMARY KEY,
    seller_profile_id BIGINT NOT NULL REFERENCES seller_profiles(id) ON DELETE CASCADE,
    payout_number VARCHAR(40) NOT NULL UNIQUE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    gross_sales_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    commission_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    fee_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    open_return_holdback_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    settled_return_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    manual_adjustment_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    net_payout_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    correction_reason VARCHAR(500),
    admin_note VARCHAR(500),
    processed_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMP WITH TIME ZONE,
    paid_out_at TIMESTAMP WITH TIME ZONE,
    corrected_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_seller_payout_period UNIQUE (seller_profile_id, period_start)
);

CREATE TABLE seller_payout_items (
    id BIGSERIAL PRIMARY KEY,
    payout_id BIGINT NOT NULL REFERENCES seller_payouts(id) ON DELETE CASCADE,
    order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    return_request_id BIGINT REFERENCES return_requests(id) ON DELETE SET NULL,
    item_type VARCHAR(30) NOT NULL,
    reference_number VARCHAR(80),
    description VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    gross_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    commission_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    fee_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    return_holdback_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    return_settlement_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    manual_adjustment_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00
);

INSERT INTO users (username, email, password_hash, user_type, customer_number, active)
VALUES
    ('demo_seller_alpha', 'seller.alpha@demo.de',
     '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze',
     'BUSINESS',
     'K-' || to_char(nextval('customer_number_sequence'), 'FM000000'),
     TRUE),
    ('demo_seller_beta', 'seller.beta@demo.de',
     '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze',
     'BUSINESS',
     'K-' || to_char(nextval('customer_number_sequence'), 'FM000000'),
     TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT u.id, 'SELLER'
FROM users u
WHERE u.username IN ('demo_seller_alpha', 'demo_seller_beta')
ON CONFLICT (user_id) DO UPDATE SET role = EXCLUDED.role;

INSERT INTO seller_profiles (user_id, display_name, commission_rate, payout_iban, active)
SELECT u.id, 'TechPartner GmbH', 12.50, 'DE02120300000000202051', TRUE
FROM users u
WHERE u.username = 'demo_seller_alpha'
  AND NOT EXISTS (SELECT 1 FROM seller_profiles sp WHERE sp.user_id = u.id);

INSERT INTO seller_profiles (user_id, display_name, commission_rate, payout_iban, active)
SELECT u.id, 'Green Devices AG', 9.75, 'DE44500105175407324931', TRUE
FROM users u
WHERE u.username = 'demo_seller_beta'
  AND NOT EXISTS (SELECT 1 FROM seller_profiles sp WHERE sp.user_id = u.id);

WITH ranked_products AS (
    SELECT id,
           ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM products
    WHERE purchasable = TRUE
)
UPDATE products p
SET seller_name = CASE
    WHEN ranked_products.rn IN (1, 2) THEN 'TechPartner GmbH'
    WHEN ranked_products.rn IN (3, 4) THEN 'Green Devices AG'
    ELSE p.seller_name
END
FROM ranked_products
WHERE p.id = ranked_products.id;

UPDATE order_items oi
SET seller_name = p.seller_name
FROM products p
WHERE oi.product_id = p.id
  AND (oi.seller_name IS NULL OR oi.seller_name = '' OR oi.seller_name = 'Webshop');
