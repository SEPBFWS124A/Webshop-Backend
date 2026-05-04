ALTER TYPE order_status ADD VALUE IF NOT EXISTS 'Pending_Approval';

ALTER TABLE orders
    ADD COLUMN approval_reason VARCHAR(1000),
    ADD COLUMN approval_budget_limit NUMERIC(12, 2);
