CREATE TABLE quote_requests (
    id BIGSERIAL PRIMARY KEY,
    quote_number VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    customer_email VARCHAR(255) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    company_name VARCHAR(255),
    company_details VARCHAR(500),
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ NOT NULL,
    subtotal NUMERIC(10, 2) NOT NULL,
    discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(10, 2) NOT NULL,
    shipping_cost NUMERIC(10, 2) NOT NULL,
    total_price NUMERIC(10, 2) NOT NULL,
    pdf_document BYTEA NOT NULL
);

CREATE TABLE quote_request_items (
    id BIGSERIAL PRIMARY KEY,
    quote_request_id BIGINT NOT NULL REFERENCES quote_requests(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    line_total NUMERIC(10, 2) NOT NULL,
    personalization_text VARCHAR(1000),
    gift_card_amount NUMERIC(10, 2),
    gift_card_recipient_email VARCHAR(255),
    gift_card_message VARCHAR(1000)
);

CREATE INDEX idx_quote_requests_customer_created ON quote_requests(customer_id, created_at DESC);
CREATE INDEX idx_quote_request_items_quote ON quote_request_items(quote_request_id);
