UPDATE users
SET user_type = 'INTERNAL'
WHERE id IN (
    SELECT user_id
    FROM user_roles
    WHERE role IN ('EMPLOYEE', 'SALES_EMPLOYEE', 'WAREHOUSE_EMPLOYEE', 'ADMIN')
);

