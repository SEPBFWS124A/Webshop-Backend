-- =============================================================================
-- V309 – Affiliate-Programm
-- Neue Rolle AFFILIATE_CUSTOMER, Tabellen für Anträge, Profile, Links und
-- Conversions sowie ein Demo-Affiliate-Konto (Passwort: Password1!)
-- =============================================================================

-- Einzel-Rollen-Einschränkung aus V25 aufheben, damit Kunden zusätzlich
-- AFFILIATE_CUSTOMER halten können ohne ihre CUSTOMER-Rolle zu verlieren.
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS uq_user_roles_single_role;

-- -----------------------------------------------------------------------------
-- Affiliate-Anträge
-- -----------------------------------------------------------------------------
CREATE TABLE affiliate_applications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    motivation_text TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by     BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    review_note     VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_affiliate_app_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_affiliate_applications_user_id ON affiliate_applications(user_id);
CREATE INDEX idx_affiliate_applications_status  ON affiliate_applications(status);

-- -----------------------------------------------------------------------------
-- Affiliate-Profile (1:1 mit User, wird bei Genehmigung angelegt)
-- -----------------------------------------------------------------------------
CREATE TABLE affiliate_profiles (
    id                        BIGSERIAL       PRIMARY KEY,
    user_id                   BIGINT          NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    commission_rate           NUMERIC(5,4)    NOT NULL DEFAULT 0.0500,
    total_earnings_confirmed  NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    pending_earnings          NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    active                    BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- Affiliate-Links (ein Affiliate kann pro Produkt mehrere Links generieren)
-- -----------------------------------------------------------------------------
CREATE TABLE affiliate_links (
    id                   BIGSERIAL     PRIMARY KEY,
    affiliate_profile_id BIGINT        NOT NULL REFERENCES affiliate_profiles(id) ON DELETE CASCADE,
    product_id           BIGINT        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tracking_code        VARCHAR(12)   NOT NULL UNIQUE,
    click_count          INTEGER       NOT NULL DEFAULT 0,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_affiliate_links_profile   ON affiliate_links(affiliate_profile_id);
CREATE INDEX idx_affiliate_links_code      ON affiliate_links(tracking_code);

-- -----------------------------------------------------------------------------
-- Affiliate-Conversions (Kauf über einen Affiliate-Link)
-- -----------------------------------------------------------------------------
CREATE TABLE affiliate_conversions (
    id                   BIGSERIAL      PRIMARY KEY,
    affiliate_link_id    BIGINT         NOT NULL REFERENCES affiliate_links(id) ON DELETE CASCADE,
    order_id             BIGINT         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    order_item_id        BIGINT         REFERENCES order_items(id) ON DELETE SET NULL,
    purchase_amount      NUMERIC(12,2)  NOT NULL,
    commission_amount    NUMERIC(12,2)  NOT NULL,
    status               VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_affiliate_conversion_status CHECK (status IN ('PENDING', 'CONFIRMED', 'PAID'))
);

CREATE INDEX idx_affiliate_conversions_link  ON affiliate_conversions(affiliate_link_id);
CREATE INDEX idx_affiliate_conversions_order ON affiliate_conversions(order_id);

-- =============================================================================
-- Demo-Affiliate-Konto
-- Passwort: Password1!  (BCrypt-Hash cost 10)
-- =============================================================================

INSERT INTO users (username, email, password_hash, user_type, customer_number, active)
VALUES (
    'demo_affiliate',
    'affiliate@demo.de',
    '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze',
    'PRIVATE',
    'K-' || to_char(nextval('customer_number_sequence'), 'FM000000'),
    TRUE
) ON CONFLICT (username) DO NOTHING;

-- Rollen: CUSTOMER + AFFILIATE_CUSTOMER
INSERT INTO user_roles (user_id, role)
SELECT id, 'CUSTOMER'
FROM users WHERE username = 'demo_affiliate'
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT id, 'AFFILIATE_CUSTOMER'
FROM users WHERE username = 'demo_affiliate'
ON CONFLICT (user_id, role) DO NOTHING;

-- Affiliate-Antrag (bereits genehmigt)
INSERT INTO affiliate_applications (user_id, motivation_text, status, review_note, created_at, updated_at)
SELECT
    id,
    'Ich bin seit Jahren begeisterter Kunde und möchte den Shop in meinem Tech-Blog und auf Social Media empfehlen. Mit meinen 12.000 Followern kann ich gezielt kaufbereite Zielgruppen ansprechen.',
    'APPROVED',
    'Demo-Account – automatisch genehmigt.',
    NOW() - INTERVAL '30 days',
    NOW() - INTERVAL '25 days'
FROM users WHERE username = 'demo_affiliate';

-- Affiliate-Profil
INSERT INTO affiliate_profiles (user_id, commission_rate, total_earnings_confirmed, pending_earnings, active)
SELECT id, 0.0500, 12.75, 3.50, TRUE
FROM users WHERE username = 'demo_affiliate'
ON CONFLICT (user_id) DO NOTHING;

-- Beispiel-Link auf das erste kaufbare Produkt mit 47 Demo-Klicks
INSERT INTO affiliate_links (affiliate_profile_id, product_id, tracking_code, click_count, active, created_at)
SELECT
    ap.id,
    p.id,
    'DEMOAFFIL3001',
    47,
    TRUE,
    NOW() - INTERVAL '20 days'
FROM affiliate_profiles ap
JOIN users u ON u.id = ap.user_id
CROSS JOIN (SELECT id FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1) p
WHERE u.username = 'demo_affiliate'
ON CONFLICT (tracking_code) DO NOTHING;
