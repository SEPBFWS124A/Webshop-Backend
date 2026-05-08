CREATE TABLE warehouse_locations (
    id              BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    street          VARCHAR(255) NOT NULL,
    postal_code     VARCHAR(20)  NOT NULL,
    city            VARCHAR(100) NOT NULL,
    country         VARCHAR(100) NOT NULL,
    main_location   BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO warehouse_locations (code, name, street, postal_code, city, country, main_location, active)
VALUES
    ('MAIN', 'Hauptlager Koeln', 'Marconistrasse 10', '50769', 'Koeln', 'Deutschland', TRUE, TRUE),
    ('WAREHOUSE_B', 'Lager B Duesseldorf', 'Industriestrasse 22', '40233', 'Duesseldorf', 'Deutschland', FALSE, TRUE),
    ('WAREHOUSE_C', 'Lager C Dortmund', 'Hafenallee 7', '44147', 'Dortmund', 'Deutschland', FALSE, TRUE);

CREATE TABLE warehouse_product_stocks (
    id                      BIGSERIAL PRIMARY KEY,
    product_id              BIGINT    NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    warehouse_location_id   BIGINT    NOT NULL REFERENCES warehouse_locations(id) ON DELETE CASCADE,
    quantity                INT       NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    CONSTRAINT uk_warehouse_product_stock_product_location UNIQUE (product_id, warehouse_location_id)
);

INSERT INTO warehouse_product_stocks (product_id, warehouse_location_id, quantity)
SELECT p.id, location.id, p.stock
FROM products p
JOIN warehouse_locations location ON location.code = 'MAIN'
ON CONFLICT (product_id, warehouse_location_id) DO NOTHING;

ALTER TABLE orders
    ADD COLUMN fulfillment_warehouse_id BIGINT;

UPDATE orders
SET fulfillment_warehouse_id = (SELECT id FROM warehouse_locations WHERE code = 'MAIN')
WHERE fulfillment_warehouse_id IS NULL;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_fulfillment_warehouse
    FOREIGN KEY (fulfillment_warehouse_id) REFERENCES warehouse_locations(id);

CREATE INDEX idx_orders_fulfillment_warehouse_id ON orders(fulfillment_warehouse_id);
CREATE INDEX idx_warehouse_product_stocks_location_product
    ON warehouse_product_stocks(warehouse_location_id, product_id);
