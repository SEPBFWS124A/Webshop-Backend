ALTER TABLE system_notifications
    ADD COLUMN IF NOT EXISTS target_url VARCHAR(500);

CREATE TABLE support_tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    related_order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    subject VARCHAR(180) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE support_ticket_messages (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_text TEXT NOT NULL,
    internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_support_tickets_customer_updated
    ON support_tickets(customer_id, updated_at DESC);

CREATE INDEX idx_support_tickets_status_updated
    ON support_tickets(status, updated_at DESC);

CREATE INDEX idx_support_ticket_messages_ticket_created
    ON support_ticket_messages(ticket_id, created_at ASC);
