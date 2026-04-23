-- =============================================================================
-- V21 – Demo-Daten für Kundenbindungsprogramm (Issue #116)
-- =============================================================================

-- Standard-Gewinntöpfe des Glücksrads (administrierbar)
INSERT INTO lucky_wheel_prizes (label, prize_type, discount_percent, probability, active)
VALUES
    ('Leider kein Gewinn',     'NO_WIN',        NULL,  0.950000, TRUE),
    ('Gratis-Versand',         'FREE_SHIPPING',  NULL,  0.040000, TRUE),
    ('5% Rabattgutschein',     'COUPON',         5.00,  0.010000, TRUE);

-- Demo-Kunde 1: aktiver 12-Tage-Streak
UPDATE users
SET current_login_streak = 12,
    last_login_date       = CURRENT_DATE
WHERE username = 'demo_kunde1';

-- Demo-Kunde 2: 25-Tage-Streak (kurz vor der Belohnung)
UPDATE users
SET current_login_streak = 25,
    last_login_date       = CURRENT_DATE
WHERE username = 'demo_kunde2';

-- Demo-Spin-History: demo_kunde1 hat gestern gedreht (kann heute wieder drehen)
INSERT INTO lucky_wheel_spins (user_id, prize_id, won, spun_at)
SELECT
    u.id,
    p.id,
    FALSE,
    NOW() - INTERVAL '25 hours'
FROM users u, lucky_wheel_prizes p
WHERE u.username = 'demo_kunde1'
  AND p.prize_type = 'NO_WIN'
LIMIT 1;

-- Demo-Spin-History: demo_kunde2 hat heute bereits gedreht (Gratis-Versand gewonnen)
INSERT INTO lucky_wheel_spins (user_id, prize_id, won, spun_at)
SELECT
    u.id,
    p.id,
    TRUE,
    NOW() - INTERVAL '2 hours'
FROM users u, lucky_wheel_prizes p
WHERE u.username = 'demo_kunde2'
  AND p.prize_type = 'FREE_SHIPPING'
LIMIT 1;
