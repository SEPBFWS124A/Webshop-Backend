ALTER TABLE products
    ADD COLUMN stock INT NOT NULL DEFAULT 25;

UPDATE products
SET stock = CASE
    WHEN purchasable THEN 25
    ELSE 0
END;

ALTER TABLE products
    ADD CONSTRAINT chk_products_stock_non_negative CHECK (stock >= 0);

ALTER TABLE orders
    ALTER COLUMN customer_id DROP NOT NULL;

ALTER TABLE orders
    ADD COLUMN order_number VARCHAR(30),
    ADD COLUMN customer_email VARCHAR(255),
    ADD COLUMN customer_name VARCHAR(255),
    ADD COLUMN delivery_street VARCHAR(255),
    ADD COLUMN delivery_city VARCHAR(100),
    ADD COLUMN delivery_postal_code VARCHAR(20),
    ADD COLUMN delivery_country VARCHAR(100),
    ADD COLUMN payment_method_type payment_method_type,
    ADD COLUMN payment_masked_details VARCHAR(255),
    ADD COLUMN discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0;

UPDATE orders o
SET order_number = 'ORD-' || LPAD(o.id::text, 8, '0'),
    customer_email = u.email,
    customer_name = u.username,
    delivery_street = da.street,
    delivery_city = da.city,
    delivery_postal_code = da.postal_code,
    delivery_country = da.country,
    payment_method_type = pm.method_type,
    payment_masked_details = pm.masked_details
FROM users u
LEFT JOIN delivery_addresses da ON da.user_id = u.id
LEFT JOIN payment_methods pm ON pm.user_id = u.id
WHERE o.customer_id = u.id;

ALTER TABLE orders
    ALTER COLUMN order_number SET NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT uq_orders_order_number UNIQUE (order_number);
