-- Coupons assigned to specific customers (US #24)
CREATE TABLE coupons (
    id                  BIGSERIAL      PRIMARY KEY,
    customer_id         BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code                VARCHAR(50)    NOT NULL UNIQUE,
    discount_percent    NUMERIC(5, 2)  NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100),
    valid_until         DATE,
    used                BOOLEAN        NOT NULL DEFAULT FALSE,
    used_at             TIMESTAMP,
    created_by_user_id  BIGINT         REFERENCES users(id)
);
