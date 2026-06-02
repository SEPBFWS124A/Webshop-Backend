ALTER TABLE product_feedback
    ADD CONSTRAINT uq_product_feedback_user_product UNIQUE (product_id, user_id);

CREATE INDEX IF NOT EXISTS idx_product_feedback_product_id ON product_feedback(product_id);
