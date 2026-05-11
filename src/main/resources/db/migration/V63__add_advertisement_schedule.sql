ALTER TABLE advertisements
    ADD COLUMN IF NOT EXISTS start_date DATE,
    ADD COLUMN IF NOT EXISTS end_date DATE;

UPDATE advertisements
SET start_date = COALESCE(start_date, CURRENT_DATE),
    end_date = COALESCE(end_date, CURRENT_DATE + INTERVAL '30 days');

ALTER TABLE advertisements
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN end_date SET NOT NULL;

ALTER TABLE advertisements
    ADD CONSTRAINT chk_advertisements_valid_schedule
        CHECK (end_date >= start_date);
