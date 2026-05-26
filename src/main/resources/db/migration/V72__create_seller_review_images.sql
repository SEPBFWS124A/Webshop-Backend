CREATE TABLE IF NOT EXISTS seller_review_images (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES seller_reviews(id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    image_data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_seller_review_images_review_created
    ON seller_review_images(review_id, created_at);
