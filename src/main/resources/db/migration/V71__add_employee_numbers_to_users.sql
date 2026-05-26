CREATE SEQUENCE IF NOT EXISTS employee_number_sequence START 10001;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS employee_number VARCHAR(20);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_employee_number
    ON users(employee_number);

WITH internal_users AS (
    SELECT u.id, nextval('employee_number_sequence') AS next_employee_number
    FROM users u
    WHERE u.employee_number IS NULL
      AND (
          u.user_type = 'INTERNAL'
          OR EXISTS (
              SELECT 1
              FROM user_roles ur
              WHERE ur.user_id = u.id
                AND ur.role IN ('EMPLOYEE', 'SALES_EMPLOYEE', 'WAREHOUSE_EMPLOYEE', 'ADMIN')
          )
      )
)
UPDATE users u
SET employee_number = 'MA-' || to_char(internal_users.next_employee_number, 'FM00000')
FROM internal_users
WHERE u.id = internal_users.id;

