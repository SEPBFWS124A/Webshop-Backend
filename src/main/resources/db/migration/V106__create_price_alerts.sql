-- Preisalarm-Feature: Tabelle für Preisalarme
CREATE TABLE price_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    target_price NUMERIC(12, 2) NOT NULL CHECK (target_price > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notify_by_email BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    triggered_at TIMESTAMP WITH TIME ZONE,
    last_checked_at TIMESTAMP WITH TIME ZONE
);

-- Partial unique index: ein User darf pro Produkt+Zielpreis nur einen aktiven Alarm haben
CREATE UNIQUE INDEX uq_user_product_price_active
    ON price_alerts (user_id, product_id, target_price)
    WHERE (active = TRUE AND status = 'ACTIVE');

CREATE INDEX idx_price_alerts_active ON price_alerts (status, active) WHERE active = TRUE;
CREATE INDEX idx_price_alerts_user ON price_alerts (user_id);
CREATE INDEX idx_price_alerts_product ON price_alerts (product_id);

