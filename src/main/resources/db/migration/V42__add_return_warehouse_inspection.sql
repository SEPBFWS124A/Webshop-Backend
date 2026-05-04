ALTER TYPE return_request_status ADD VALUE IF NOT EXISTS 'COMPLETED';

ALTER TABLE return_requests
    ADD COLUMN IF NOT EXISTS inspected_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS inspection_condition VARCHAR(30),
    ADD COLUMN IF NOT EXISTS refund_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS refund_method VARCHAR(30),
    ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS refund_reference VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_return_requests_status_created
    ON return_requests (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_return_requests_tracking_lookup
    ON return_requests (LOWER(tracking_id));
