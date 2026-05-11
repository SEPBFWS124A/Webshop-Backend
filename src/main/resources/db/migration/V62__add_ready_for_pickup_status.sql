ALTER TYPE order_status ADD VALUE IF NOT EXISTS 'READY_FOR_PICKUP';

UPDATE orders
SET delivery_street = pickup_stores.street,
    delivery_city = pickup_stores.city,
    delivery_postal_code = pickup_stores.postal_code,
    delivery_country = pickup_stores.country,
    truck_identifier = NULL
FROM pickup_stores
WHERE orders.pickup_store_id = pickup_stores.id;
