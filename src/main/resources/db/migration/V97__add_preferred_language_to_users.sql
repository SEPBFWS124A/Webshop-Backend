ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(8) NOT NULL DEFAULT 'de';

UPDATE users
SET preferred_language = 'de'
WHERE preferred_language IS NULL OR preferred_language = '';
