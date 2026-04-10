CREATE TYPE user_role AS ENUM ('CUSTOMER', 'EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN');
CREATE TYPE user_type AS ENUM ('PRIVATE', 'BUSINESS');

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(100) NOT NULL UNIQUE,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    role                user_role    NOT NULL DEFAULT 'CUSTOMER',
    user_type           user_type    NOT NULL DEFAULT 'PRIVATE',
    customer_number     VARCHAR(20)  UNIQUE,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Generate customer numbers automatically for CUSTOMER role
CREATE SEQUENCE customer_number_sequence START 100000;
