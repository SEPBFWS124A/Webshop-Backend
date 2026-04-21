-- Erweitert Daueraufträge um flexible Intervall-Typen
ALTER TABLE standing_orders
    ADD COLUMN interval_type  VARCHAR(20) NOT NULL DEFAULT 'DAYS',
    ADD COLUMN day_of_week    INT,        -- 1=Montag, 7=Sonntag
    ADD COLUMN day_of_month   INT,        -- 1-31
    ADD COLUMN month_of_year  INT;        -- 1-12

-- Validierung der Typen
ALTER TABLE standing_orders
    ADD CONSTRAINT chk_interval_type
        CHECK (interval_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', 'DAYS'));

-- interval_days darf jetzt NULL sein (wird nur bei 'DAYS' benötigt)
ALTER TABLE standing_orders ALTER COLUMN interval_days DROP NOT NULL;

-- Alten Constraint entfernen und durch flexiblen ersetzen
ALTER TABLE standing_orders DROP CONSTRAINT IF EXISTS standing_orders_interval_days_check;
ALTER TABLE standing_orders ADD CONSTRAINT chk_interval_days
        CHECK (interval_days IS NULL OR interval_days > 0);