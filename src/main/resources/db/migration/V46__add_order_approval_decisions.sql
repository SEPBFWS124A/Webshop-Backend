ALTER TYPE order_status ADD VALUE IF NOT EXISTS 'Rejected';

ALTER TABLE orders
    ADD COLUMN approval_rejection_reason VARCHAR(1000),
    ADD COLUMN approval_decided_at TIMESTAMP,
    ADD COLUMN approval_decided_by_id BIGINT REFERENCES users(id);
