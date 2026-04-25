CREATE TABLE account_links (
    id          BIGSERIAL PRIMARY KEY,
    user_a_id   BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id   BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_account_links_distinct CHECK (user_a_id <> user_b_id),
    CONSTRAINT chk_account_links_ordered CHECK (user_a_id < user_b_id),
    CONSTRAINT uq_account_links_pair UNIQUE (user_a_id, user_b_id)
);

CREATE INDEX idx_account_links_user_a ON account_links(user_a_id);
CREATE INDEX idx_account_links_user_b ON account_links(user_b_id);
