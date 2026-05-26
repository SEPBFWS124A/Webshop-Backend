UPDATE orders
SET pickup_store_id = (
    SELECT id
    FROM pickup_stores
    WHERE name = 'Zentrallager Köln'
)
WHERE pickup_store_id IS NOT NULL
  AND pickup_store_id <> (
      SELECT id
      FROM pickup_stores
      WHERE name = 'Zentrallager Köln'
  );

DELETE FROM pickup_stores
WHERE name <> 'Zentrallager Köln';
