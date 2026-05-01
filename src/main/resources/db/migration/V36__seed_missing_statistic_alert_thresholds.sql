INSERT INTO statistic_alert_thresholds (metric, period_days, deviation_percent, enabled)
VALUES
    ('REVENUE', 30, 30.00, TRUE),
    ('ORDER_COUNT', 30, 30.00, TRUE),
    ('ACTIVE_CUSTOMERS', 30, 30.00, TRUE),
    ('AVG_ORDER_VALUE', 30, 30.00, TRUE)
ON CONFLICT (metric) DO NOTHING;
