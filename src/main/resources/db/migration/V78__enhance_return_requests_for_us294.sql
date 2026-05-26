ALTER TYPE return_reason ADD VALUE IF NOT EXISTS 'DAMAGED';
ALTER TYPE return_reason ADD VALUE IF NOT EXISTS 'WRONG_ITEM';
ALTER TYPE return_reason ADD VALUE IF NOT EXISTS 'OTHER';

ALTER TYPE return_request_status ADD VALUE IF NOT EXISTS 'IN_REVIEW';
ALTER TYPE return_request_status ADD VALUE IF NOT EXISTS 'APPROVED';
ALTER TYPE return_request_status ADD VALUE IF NOT EXISTS 'REJECTED';
ALTER TYPE return_request_status ADD VALUE IF NOT EXISTS 'GOODS_RECEIVED';
ALTER TYPE return_request_status ADD VALUE IF NOT EXISTS 'REFUNDED';

ALTER TABLE return_requests
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS goods_received_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS decision_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS decided_by_id BIGINT REFERENCES users(id);

ALTER TABLE return_request_items
    ADD COLUMN IF NOT EXISTS reason VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS customer_comment VARCHAR(500);

ALTER TABLE return_request_items
    DROP CONSTRAINT IF EXISTS uq_return_request_items_order_item;

CREATE INDEX IF NOT EXISTS idx_return_request_items_order_item
    ON return_request_items (order_item_id);

CREATE INDEX IF NOT EXISTS idx_return_requests_decided_by
    ON return_requests (decided_by_id);
