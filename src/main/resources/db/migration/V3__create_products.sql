CREATE TABLE products (
    id                          BIGSERIAL       PRIMARY KEY,
    name                        VARCHAR(255)    NOT NULL,
    description                 TEXT,
    image_url                   VARCHAR(500),
    recommended_retail_price    NUMERIC(10, 2)  NOT NULL,
    category                    VARCHAR(100),
    purchasable                 BOOLEAN         NOT NULL DEFAULT FALSE,
    promoted                    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW()
);
