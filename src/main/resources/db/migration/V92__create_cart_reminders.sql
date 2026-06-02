ALTER TABLE users
    ADD COLUMN IF NOT EXISTS cart_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS cart_reminders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    last_modified_at TIMESTAMP,
    reminder_sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cart_reminders_due
    ON cart_reminders(last_modified_at, reminder_sent_at);
