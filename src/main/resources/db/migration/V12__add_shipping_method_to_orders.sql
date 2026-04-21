ALTER TABLE orders
    ADD COLUMN shipping_method VARCHAR(30) NOT NULL DEFAULT 'STANDARD';

UPDATE orders
SET shipping_method = 'STANDARD'
WHERE shipping_method IS NULL;
