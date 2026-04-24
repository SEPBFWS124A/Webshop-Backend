-- Issue #134: Verwaltung multipler Mitarbeiterrollen pro Benutzer
-- Migriert von 1:1 (users.role) zu m:n (user_roles Pivot-Tabelle)

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- Vorhandene Einzelrollen in die neue Pivot-Tabelle übertragen
INSERT INTO user_roles (user_id, role)
SELECT id, role::text FROM users;

-- Alte Spalte entfernen (Daten sind jetzt in user_roles)
ALTER TABLE users DROP COLUMN role;
