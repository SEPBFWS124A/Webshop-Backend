ALTER TABLE products
    ADD COLUMN IF NOT EXISTS co2_emission_kg NUMERIC(10, 3);

UPDATE products
SET co2_emission_kg = CASE
    WHEN LOWER(name) LIKE '%laptop%' THEN 214.500
    WHEN LOWER(name) LIKE '%mouse%' THEN 2.100
    WHEN LOWER(name) LIKE '%desk%' THEN 58.750
    WHEN LOWER(name) LIKE '%hub%' THEN 5.400
    WHEN LOWER(name) LIKE '%chair%' THEN 33.200
    ELSE co2_emission_kg
END
WHERE co2_emission_kg IS NULL;
