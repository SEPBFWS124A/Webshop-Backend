CREATE TABLE advertisements (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    image_url VARCHAR(500),
    target_url VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO advertisements (title, description, content_type, image_url, target_url, active)
SELECT
    'Sommeraktion im Home Office',
    'Ergonomische Favoriten, clevere Bundles und schnelle Upgrades für deinen Arbeitsplatz.',
    'IMAGE',
    '/standing-desk.jpg',
    '/products/3',
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM advertisements);

INSERT INTO advertisements (title, description, content_type, image_url, target_url, active)
SELECT
    'Top Auswahl für Entscheider',
    'Vergleiche Bestseller, Empfehlungen und sofort verfügbare Geräte direkt im Sortiment.',
    'TEXT',
    NULL,
    '/',
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM advertisements WHERE title = 'Top Auswahl für Entscheider'
);

INSERT INTO advertisements (title, description, content_type, image_url, target_url, active)
SELECT
    'Verkäufer-Aktion vorbereiten',
    'Diese Werbefläche ist angelegt, aber noch nicht aktiv geschaltet.',
    'TEXT',
    NULL,
    '/admin/marketing/placements',
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM advertisements WHERE title = 'Verkäufer-Aktion vorbereiten'
);
