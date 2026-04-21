-- Tabelle anpassen: Spaltennamen vereinheitlichen und flexible Intervalle ermöglichen
ALTER TABLE standing_orders ADD COLUMN IF NOT EXISTS interval_type VARCHAR(20) NOT NULL DEFAULT 'DAYS';
ALTER TABLE standing_orders ADD COLUMN IF NOT EXISTS interval_value INT;
ALTER TABLE standing_orders ADD COLUMN IF NOT EXISTS day_of_week INT;
ALTER TABLE standing_orders ADD COLUMN IF NOT EXISTS day_of_month INT;
ALTER TABLE standing_orders ADD COLUMN IF NOT EXISTS month_of_year INT;
ALTER TABLE standing_orders ADD COLUMN IF NOT EXISTS count_backwards BOOLEAN DEFAULT FALSE;

-- Constraint an das Java-Enum IntervalType anpassen
ALTER TABLE standing_orders DROP CONSTRAINT IF EXISTS chk_interval_type;
ALTER TABLE standing_orders ADD CONSTRAINT chk_interval_type 
    CHECK (interval_type IN ('DAYS', 'WEEKS', 'MONTHS', 'YEARS'));

-- Falls noch eine alte Spalte existiert, diese entfernen
ALTER TABLE standing_orders DROP COLUMN IF EXISTS interval_days;
-- Bestehende Aufträge auf einen Standardwert setzen, damit es nicht kracht
UPDATE standing_orders SET interval_type = 'DAYS' WHERE interval_type IS NULL;
UPDATE standing_orders SET interval_value = 1 WHERE interval_value IS NULL;