ALTER TABLE account_links
    ADD COLUMN source_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    ADD COLUMN target_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

UPDATE account_links
SET source_user_id = parsed.source_user_id,
    target_user_id = parsed.target_user_id
FROM (
    SELECT
        entity_id AS account_link_id,
        (regexp_match(details, 'Admin linked users ([0-9]+) and ([0-9]+)'))[1]::BIGINT AS source_user_id,
        (regexp_match(details, 'Admin linked users ([0-9]+) and ([0-9]+)'))[2]::BIGINT AS target_user_id
    FROM audit_log
    WHERE action = 'CREATE_ACCOUNT_LINK'
      AND entity_type = 'AccountLink'
      AND details ~ 'Admin linked users [0-9]+ and [0-9]+'
) parsed
WHERE account_links.id = parsed.account_link_id;

UPDATE account_links
SET source_user_id = user_a_id,
    target_user_id = user_b_id
WHERE source_user_id IS NULL
  AND target_user_id IS NULL;

ALTER TABLE account_links
    ALTER COLUMN source_user_id SET NOT NULL,
    ALTER COLUMN target_user_id SET NOT NULL,
    ADD CONSTRAINT chk_account_links_direction_distinct CHECK (source_user_id <> target_user_id);

CREATE INDEX idx_account_links_source_user ON account_links(source_user_id);
CREATE INDEX idx_account_links_target_user ON account_links(target_user_id);
