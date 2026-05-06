ALTER TABLE products
    ADD COLUMN sku VARCHAR(100),
    ADD COLUMN has_variants BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN parent_product_id BIGINT REFERENCES products(id) ON DELETE CASCADE;

CREATE INDEX ix_products_sku ON products (sku) WHERE sku IS NOT NULL;
CREATE INDEX ix_products_parent_product_id ON products (parent_product_id);

CREATE TABLE product_variant_attributes (
    id              BIGSERIAL       PRIMARY KEY,
    product_id      BIGINT          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name            VARCHAR(100)    NOT NULL,
    display_order   INTEGER         NOT NULL DEFAULT 0
);

CREATE TABLE product_variant_attribute_values (
    id              BIGSERIAL       PRIMARY KEY,
    attribute_id    BIGINT          NOT NULL REFERENCES product_variant_attributes(id) ON DELETE CASCADE,
    value           VARCHAR(100)    NOT NULL,
    display_order   INTEGER         NOT NULL DEFAULT 0
);

CREATE TABLE product_variant_options (
    id                  BIGSERIAL       PRIMARY KEY,
    product_id          BIGINT          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    attribute_name      VARCHAR(100)    NOT NULL,
    attribute_value     VARCHAR(100)    NOT NULL,
    display_order       INTEGER         NOT NULL DEFAULT 0
);

CREATE INDEX ix_variant_attributes_product_id ON product_variant_attributes(product_id);
CREATE INDEX ix_variant_attribute_values_attribute_id ON product_variant_attribute_values(attribute_id);
CREATE INDEX ix_variant_options_product_id ON product_variant_options(product_id);
