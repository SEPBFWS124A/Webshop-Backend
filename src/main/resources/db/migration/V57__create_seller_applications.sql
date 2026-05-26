CREATE TABLE IF NOT EXISTS seller_applications (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(180) NOT NULL,
    contact_name VARCHAR(180) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(60),
    website VARCHAR(255),
    product_category VARCHAR(120) NOT NULL,
    message TEXT,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
