UPDATE pickup_stores
SET active = FALSE;

INSERT INTO pickup_stores (name, street, postal_code, city, country, opening_hours, active)
SELECT 'Zentrallager Köln',
       'Marconistraße 10',
       '50769',
       'Köln',
       'Deutschland',
       'Mo-Fr 09:00-18:00',
       TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM pickup_stores
    WHERE name = 'Zentrallager Köln'
);

UPDATE pickup_stores
SET street = 'Marconistraße 10',
    postal_code = '50769',
    city = 'Köln',
    country = 'Deutschland',
    opening_hours = 'Mo-Fr 09:00-18:00',
    active = TRUE
WHERE name = 'Zentrallager Köln';
