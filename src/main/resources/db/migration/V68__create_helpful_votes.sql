CREATE TABLE helpful_votes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type VARCHAR(40) NOT NULL,
    target_id   BIGINT NOT NULL,
    helpful     BOOLEAN NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_helpful_votes_user_target UNIQUE (user_id, target_type, target_id)
);

CREATE INDEX idx_helpful_votes_target ON helpful_votes(target_type, target_id);
