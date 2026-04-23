-- =============================================================================
-- V20 – Kundenbindungsprogramm (Issue #116)
--   · Login-Streak-Felder in users
--   · Glücksrad: Prizes + Spin-History
-- =============================================================================

-- Login-Streak-Tracking direkt am User-Datensatz
ALTER TABLE users
    ADD COLUMN last_login_date     DATE,
    ADD COLUMN current_login_streak INT NOT NULL DEFAULT 0;

-- Konfigurierbare Gewinntöpfe für das Glücksrad (administrierbar im Backend)
CREATE TABLE lucky_wheel_prizes (
    id               BIGSERIAL       PRIMARY KEY,
    label            VARCHAR(100)    NOT NULL,
    prize_type       VARCHAR(50)     NOT NULL,  -- NO_WIN | FREE_SHIPPING | COUPON
    discount_percent NUMERIC(5, 2),
    probability      NUMERIC(7, 6)   NOT NULL CHECK (probability >= 0 AND probability <= 1),
    active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Spin-History: max 1 Spin pro User pro 24 h (enforced in service)
CREATE TABLE lucky_wheel_spins (
    id         BIGSERIAL  PRIMARY KEY,
    user_id    BIGINT     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    prize_id   BIGINT     REFERENCES lucky_wheel_prizes(id),
    won        BOOLEAN    NOT NULL DEFAULT FALSE,
    coupon_id  BIGINT     REFERENCES coupons(id),
    spun_at    TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lws_user_spun ON lucky_wheel_spins(user_id, spun_at DESC);
