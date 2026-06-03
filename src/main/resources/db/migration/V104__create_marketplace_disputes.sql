CREATE TABLE marketplace_disputes (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    order_item_id BIGINT NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seller_name VARCHAR(180) NOT NULL,
    reason VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    description VARCHAR(2000),
    payout_blocked BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_marketplace_disputes_customer ON marketplace_disputes(customer_id, created_at DESC);
CREATE INDEX idx_marketplace_disputes_seller ON marketplace_disputes(LOWER(seller_name), created_at DESC);
CREATE INDEX idx_marketplace_disputes_status ON marketplace_disputes(status);

CREATE UNIQUE INDEX ux_marketplace_disputes_active_item
    ON marketplace_disputes(order_item_id)
    WHERE status IN ('OPEN', 'UNDER_REVIEW');

CREATE TABLE marketplace_dispute_history (
    id BIGSERIAL PRIMARY KEY,
    dispute_id BIGINT NOT NULL REFERENCES marketplace_disputes(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    changed_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    changed_by_username VARCHAR(120),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    note VARCHAR(1000)
);

CREATE INDEX idx_marketplace_dispute_history_dispute ON marketplace_dispute_history(dispute_id, changed_at ASC);

CREATE TABLE marketplace_dispute_images (
    id BIGSERIAL PRIMARY KEY,
    dispute_id BIGINT NOT NULL REFERENCES marketplace_disputes(id) ON DELETE CASCADE,
    image_url VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE seller_payouts
    ADD COLUMN payout_blocked BOOLEAN NOT NULL DEFAULT FALSE;
