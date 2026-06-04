-- Über-Uns-Sektionen (Admin pflegt Reihenfolge und Inhalt)
CREATE TABLE about_us_sections (
    id            BIGSERIAL    PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    content       TEXT         NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_about_us_sections_order ON about_us_sections(display_order);

-- Bilder pro Sektion (BYTEA, analog newsletter_post_images)
CREATE TABLE about_us_images (
    id                BIGSERIAL    PRIMARY KEY,
    section_id        BIGINT       NOT NULL REFERENCES about_us_sections(id) ON DELETE CASCADE,
    image_data        BYTEA        NOT NULL,
    content_type      VARCHAR(80)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_about_us_images_section ON about_us_images(section_id, created_at);
