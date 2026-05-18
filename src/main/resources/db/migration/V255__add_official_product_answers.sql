ALTER TABLE product_answers
    ADD COLUMN IF NOT EXISTS official_answer BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS official_marked_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS official_marked_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_product_answers_official
    ON product_answers(question_id, official_answer DESC, created_at ASC);
