-- =============================================================================
-- V13 – Systembenachrichtigungen für automatisiertes Verkaufs-Monitoring (Issue #90)
-- =============================================================================

CREATE TABLE system_notifications (
    id                    BIGSERIAL    PRIMARY KEY,
    type                  VARCHAR(30)  NOT NULL,
    product_id            BIGINT       REFERENCES products(id) ON DELETE SET NULL,
    product_name          VARCHAR(255) NOT NULL,
    change_percent        NUMERIC(7,2),
    current_period_units  BIGINT       NOT NULL DEFAULT 0,
    previous_period_units BIGINT       NOT NULL DEFAULT 0,
    is_read               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sysnotif_is_read    ON system_notifications(is_read);
CREATE INDEX idx_sysnotif_created_at ON system_notifications(created_at DESC);
CREATE INDEX idx_sysnotif_product    ON system_notifications(product_id);
