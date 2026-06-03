-- #134 Create subscriptions table for Webshop Plus membership
-- Using VARCHAR instead of native PostgreSQL ENUM to avoid Hibernate type cast issues
CREATE TABLE subscriptions (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status               VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    plan                 VARCHAR(30)    NOT NULL DEFAULT 'PLUS',
    monthly_price        DECIMAL(10, 2) NOT NULL DEFAULT 9.99,
    started_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    current_period_start TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    current_period_end   TIMESTAMPTZ    NOT NULL DEFAULT (NOW() + INTERVAL '1 month'),
    cancelled_at         TIMESTAMPTZ
);

CREATE INDEX idx_subscriptions_user_id    ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_status     ON subscriptions(status);
CREATE INDEX idx_subscriptions_period_end ON subscriptions(current_period_end)
    WHERE status = 'ACTIVE';
