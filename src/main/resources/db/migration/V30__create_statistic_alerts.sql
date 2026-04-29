CREATE TABLE statistic_alert_thresholds (
    id                 BIGSERIAL PRIMARY KEY,
    metric             VARCHAR(50)    NOT NULL UNIQUE,
    period_days        INT            NOT NULL CHECK (period_days BETWEEN 1 AND 365),
    deviation_percent  NUMERIC(7, 2)  NOT NULL CHECK (deviation_percent > 0),
    enabled            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE TABLE statistic_alert_warnings (
    id                 BIGSERIAL PRIMARY KEY,
    threshold_id       BIGINT         NOT NULL REFERENCES statistic_alert_thresholds (id) ON DELETE CASCADE,
    metric             VARCHAR(50)    NOT NULL,
    period_start       DATE           NOT NULL,
    period_end         DATE           NOT NULL,
    comparison_start   DATE           NOT NULL,
    comparison_end     DATE           NOT NULL,
    current_value      NUMERIC(14, 2) NOT NULL,
    comparison_value   NUMERIC(14, 2) NOT NULL,
    deviation_percent  NUMERIC(8, 2)  NOT NULL,
    reason             TEXT           NOT NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    created_at         TIMESTAMP      NOT NULL DEFAULT NOW(),
    read_at            TIMESTAMP,
    resolved_at        TIMESTAMP,
    CONSTRAINT statistic_alert_warnings_period_unique UNIQUE (threshold_id, period_start, period_end)
);

CREATE INDEX idx_statistic_alert_warnings_status_created
    ON statistic_alert_warnings (status, created_at DESC);

INSERT INTO statistic_alert_thresholds (metric, period_days, deviation_percent, enabled)
VALUES
    ('REVENUE', 30, 30.00, TRUE),
    ('ORDER_COUNT', 30, 30.00, TRUE),
    ('ACTIVE_CUSTOMERS', 30, 30.00, TRUE);
