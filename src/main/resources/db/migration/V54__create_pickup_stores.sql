CREATE TABLE pickup_stores (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(160) NOT NULL,
    street        VARCHAR(255) NOT NULL,
    postal_code   VARCHAR(20)  NOT NULL,
    city          VARCHAR(100) NOT NULL,
    country       VARCHAR(100) NOT NULL,
    opening_hours VARCHAR(255) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO pickup_stores (name, street, postal_code, city, country, opening_hours, active)
VALUES
    ('Webshop Store Bielefeld Mitte', 'Bahnhofstrasse 12', '33602', 'Bielefeld', 'Germany', 'Mo-Fr 09:00-18:00, Sa 10:00-16:00', TRUE),
    ('Webshop Store Paderborn', 'Westernstrasse 31', '33098', 'Paderborn', 'Germany', 'Mo-Fr 09:30-18:30, Sa 10:00-15:00', TRUE),
    ('Webshop Store Guetersloh', 'Berliner Strasse 45', '33330', 'Guetersloh', 'Germany', 'Mo-Fr 10:00-18:00', TRUE);

ALTER TABLE orders
    ADD COLUMN pickup_store_id BIGINT;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_pickup_store
        FOREIGN KEY (pickup_store_id)
        REFERENCES pickup_stores(id);

CREATE INDEX idx_orders_pickup_store_id ON orders(pickup_store_id);
