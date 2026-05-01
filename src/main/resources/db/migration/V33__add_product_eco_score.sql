ALTER TABLE products
    ADD COLUMN IF NOT EXISTS eco_score VARCHAR(20) NOT NULL DEFAULT 'NONE';

UPDATE products
SET eco_score = CASE
    WHEN co2_emission_kg < 1 THEN 'A'
    WHEN co2_emission_kg < 5 THEN 'B'
    WHEN co2_emission_kg < 20 THEN 'C'
    WHEN co2_emission_kg < 80 THEN 'D'
    ELSE 'E'
END
WHERE eco_score = 'NONE'
  AND co2_emission_kg IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'products_eco_score_check'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT products_eco_score_check
            CHECK (eco_score IN ('A', 'B', 'C', 'D', 'E', 'NONE'));
    END IF;
END $$;
