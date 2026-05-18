ALTER TABLE product_answers
    ADD COLUMN official_answer BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN official_marked_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN official_marked_at TIMESTAMP;

CREATE INDEX idx_product_answers_official
    ON product_answers(question_id, official_answer DESC, created_at ASC);
