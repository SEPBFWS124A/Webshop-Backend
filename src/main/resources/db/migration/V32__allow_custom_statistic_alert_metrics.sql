ALTER TABLE statistic_alert_thresholds
    ADD COLUMN metric_label VARCHAR(100),
    ADD COLUMN calculation_metric VARCHAR(50);

UPDATE statistic_alert_thresholds
SET metric_label = CASE metric
        WHEN 'REVENUE' THEN 'Umsatz'
        WHEN 'ORDER_COUNT' THEN 'Bestellungen'
        WHEN 'ACTIVE_CUSTOMERS' THEN 'Aktive Kunden'
        WHEN 'AVG_ORDER_VALUE' THEN 'Durchschnittlicher Bestellwert'
        ELSE metric
    END,
    calculation_metric = metric;

ALTER TABLE statistic_alert_thresholds
    ALTER COLUMN metric_label SET NOT NULL,
    ALTER COLUMN calculation_metric SET NOT NULL;

ALTER TABLE statistic_alert_thresholds
    DROP CONSTRAINT IF EXISTS statistic_alert_thresholds_metric_key;

CREATE UNIQUE INDEX uk_statistic_alert_thresholds_metric_label_lower
    ON statistic_alert_thresholds (LOWER(metric_label));

ALTER TABLE statistic_alert_warnings
    ADD COLUMN metric_label VARCHAR(100);

UPDATE statistic_alert_warnings
SET metric_label = CASE metric
        WHEN 'REVENUE' THEN 'Umsatz'
        WHEN 'ORDER_COUNT' THEN 'Bestellungen'
        WHEN 'ACTIVE_CUSTOMERS' THEN 'Aktive Kunden'
        WHEN 'AVG_ORDER_VALUE' THEN 'Durchschnittlicher Bestellwert'
        ELSE metric
    END;

ALTER TABLE statistic_alert_warnings
    ALTER COLUMN metric_label SET NOT NULL;
