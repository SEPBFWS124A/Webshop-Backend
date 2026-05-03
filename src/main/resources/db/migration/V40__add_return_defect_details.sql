ALTER TABLE return_requests
    ADD COLUMN IF NOT EXISTS defect_description VARCHAR(500);

CREATE TABLE IF NOT EXISTS return_request_images (
    id                BIGSERIAL PRIMARY KEY,
    return_request_id BIGINT NOT NULL REFERENCES return_requests(id) ON DELETE CASCADE,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(40) NOT NULL,
    size_bytes        BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    image_data        BYTEA NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_return_request_images_request
    ON return_request_images (return_request_id);
