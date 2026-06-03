-- Coupons um Festbetrag-Unterstützung erweitern
ALTER TABLE coupons ADD COLUMN fixed_amount_eur NUMERIC(10, 2) CHECK (fixed_amount_eur > 0);
ALTER TABLE coupons ALTER COLUMN discount_percent DROP NOT NULL;

-- Referral-Codes: jeder Kunde hat genau einen persistenten persönlichen Code
CREATE TABLE referral_codes (
    id                BIGSERIAL    PRIMARY KEY,
    referrer_user_id  BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    code              VARCHAR(50)  NOT NULL UNIQUE,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Referrals: Tracking wer sich über welchen Code registriert hat
CREATE TABLE referrals (
    id                   BIGSERIAL  PRIMARY KEY,
    referral_code_id     BIGINT     NOT NULL REFERENCES referral_codes(id),
    referred_user_id     BIGINT     NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    referrer_coupon_id   BIGINT     REFERENCES coupons(id),
    referred_coupon_id   BIGINT     REFERENCES coupons(id),
    created_at           TIMESTAMP  NOT NULL DEFAULT NOW()
);

-- Orders: zweiter Coupon-Slot für Stacking
ALTER TABLE orders ADD COLUMN coupon_code_2 VARCHAR(50);

CREATE INDEX idx_referral_codes_code ON referral_codes (code);
CREATE INDEX idx_referrals_referral_code ON referrals (referral_code_id);
