-- Keep only one role per user before enforcing the new single-role rule.
-- Priority matches User#getPrimaryRole for users that currently have multiple roles.
WITH ranked_roles AS (
    SELECT
        user_id,
        role,
        ROW_NUMBER() OVER (
            PARTITION BY user_id
            ORDER BY CASE role
                WHEN 'ADMIN' THEN 1
                WHEN 'SALES_EMPLOYEE' THEN 2
                WHEN 'WAREHOUSE_EMPLOYEE' THEN 3
                WHEN 'EMPLOYEE' THEN 4
                WHEN 'CUSTOMER' THEN 5
                ELSE 6
            END
        ) AS role_rank
    FROM user_roles
)
DELETE FROM user_roles ur
USING ranked_roles rr
WHERE ur.user_id = rr.user_id
  AND ur.role = rr.role
  AND rr.role_rank > 1;

ALTER TABLE user_roles
    ADD CONSTRAINT uq_user_roles_single_role UNIQUE (user_id);
