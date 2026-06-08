-- Newsletter: manuelle Anzeigereihenfolge
ALTER TABLE newsletter_posts ADD COLUMN display_order INT NOT NULL DEFAULT 0;
CREATE INDEX idx_newsletter_posts_display_order ON newsletter_posts(display_order);

-- Newsletter: geplante Veröffentlichung
ALTER TABLE newsletter_posts ADD COLUMN scheduled_publish_at TIMESTAMPTZ;

-- Über Uns: Layout-Typ pro Sektion
ALTER TABLE about_us_sections ADD COLUMN layout_type VARCHAR(20) NOT NULL DEFAULT 'TEXT_ONLY';
