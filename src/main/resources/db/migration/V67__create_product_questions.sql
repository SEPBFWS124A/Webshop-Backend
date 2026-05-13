CREATE TABLE product_questions (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    author_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_text VARCHAR(500) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE product_answers (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES product_questions(id) ON DELETE CASCADE,
    author_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    answer_text VARCHAR(500) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_questions_product_created ON product_questions(product_id, created_at DESC);
CREATE INDEX idx_product_answers_question_created ON product_answers(question_id, created_at ASC);

ALTER TABLE system_notifications
    ADD COLUMN recipient_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    ADD COLUMN custom_message TEXT;

CREATE INDEX idx_sysnotif_recipient_read ON system_notifications(recipient_user_id, is_read);
