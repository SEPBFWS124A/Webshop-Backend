CREATE TABLE delivery_addresses (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    street       VARCHAR(255) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    postal_code  VARCHAR(20)  NOT NULL,
    country      VARCHAR(100) NOT NULL DEFAULT 'Germany'
);

CREATE TYPE payment_method_type AS ENUM ('SEPA_DIRECT_DEBIT', 'CREDIT_CARD', 'BANK_TRANSFER');

CREATE TABLE payment_methods (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT               NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    method_type     payment_method_type  NOT NULL,
    masked_details  VARCHAR(255)         NOT NULL
);

CREATE TABLE business_info (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    company_name    VARCHAR(255) NOT NULL,
    industry        VARCHAR(100),
    company_size    VARCHAR(50)
);
