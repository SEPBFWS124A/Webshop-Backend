ALTER TABLE account_links
    ADD COLUMN max_order_value_limit NUMERIC(12, 2);

ALTER TABLE account_links
    ADD CONSTRAINT chk_account_links_budget_limit_non_negative
        CHECK (max_order_value_limit IS NULL OR max_order_value_limit >= 0);
