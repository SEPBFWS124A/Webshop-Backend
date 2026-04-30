UPDATE products
SET co2_emission_kg = CASE
    WHEN LOWER(name) LIKE '%laptop%' THEN 214.500
    WHEN LOWER(name) LIKE '%mouse%' THEN 2.100
    WHEN LOWER(name) LIKE '%desk%' THEN 58.750
    WHEN LOWER(name) LIKE '%hub%' THEN 5.400
    WHEN LOWER(name) LIKE '%chair%' THEN 33.200
    WHEN LOWER(name) LIKE '%notebook%' THEN 0.350
    WHEN LOWER(category) IN ('electronics', 'elektronik', 'audio', 'gaming') THEN 18.750
    WHEN LOWER(category) IN ('furniture', 'buero', 'büro') THEN 42.500
    WHEN LOWER(category) IN ('stationery', 'haushalt') THEN 1.250
    WHEN recommended_retail_price >= 1000 THEN 180.000
    WHEN recommended_retail_price >= 250 THEN 36.000
    WHEN recommended_retail_price >= 50 THEN 7.500
    WHEN recommended_retail_price >= 10 THEN 2.000
    ELSE 0.750
END
WHERE co2_emission_kg IS NULL;
