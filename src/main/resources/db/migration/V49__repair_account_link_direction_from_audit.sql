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
WHERE account_links.id = parsed.account_link_id
  AND (account_links.source_user_id <> parsed.source_user_id
       OR account_links.target_user_id <> parsed.target_user_id);
