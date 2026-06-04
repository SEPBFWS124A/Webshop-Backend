-- Newsletter-Kategorien (fest vordefiniert)
CREATE TABLE newsletter_categories (
    id   BIGSERIAL   PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO newsletter_categories (name, slug) VALUES
    ('Angebote',          'angebote'),
    ('News',              'news'),
    ('Produktneuheiten',  'produktneuheiten');

-- Newsletter-Posts (mit Draft/Published-Status)
CREATE TABLE newsletter_posts (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    category_id  BIGINT       NOT NULL REFERENCES newsletter_categories(id),
    author_id    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_newsletter_posts_category    ON newsletter_posts(category_id);
CREATE INDEX idx_newsletter_posts_published   ON newsletter_posts(published, published_at DESC);
CREATE INDEX idx_newsletter_posts_created     ON newsletter_posts(created_at DESC);

-- Bilder zu Posts (BYTEA, analog seller_review_images)
CREATE TABLE newsletter_post_images (
    id                BIGSERIAL    PRIMARY KEY,
    post_id           BIGINT       NOT NULL REFERENCES newsletter_posts(id) ON DELETE CASCADE,
    image_data        BYTEA        NOT NULL,
    content_type      VARCHAR(80)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_newsletter_post_images_post ON newsletter_post_images(post_id, created_at);

-- Nutzer-Abonnements: Opt-in / Opt-out pro Kategorie
CREATE TABLE newsletter_subscriptions (
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  BIGINT      NOT NULL REFERENCES newsletter_categories(id) ON DELETE CASCADE,
    subscribed   BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, category_id)
);
