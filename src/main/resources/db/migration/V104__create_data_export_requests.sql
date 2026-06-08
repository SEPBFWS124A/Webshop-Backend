-- #152/#153/#154 GDPR data export / subject access requests (Art. 15 & 20)
CREATE TABLE data_export_requests (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    email                    VARCHAR(255) NOT NULL,
    status                   VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    requester_type           VARCHAR(20)  NOT NULL DEFAULT 'GUEST',

    verification_token       VARCHAR(80),
    verification_expires_at  TIMESTAMPTZ,
    verified_at              TIMESTAMPTZ,

    download_token           VARCHAR(80),
    download_expires_at      TIMESTAMPTZ,
    download_count           INT          NOT NULL DEFAULT 0,
    max_downloads            INT          NOT NULL DEFAULT 3,

    archive_data             BYTEA,
    archive_filename         VARCHAR(160),
    archive_size_bytes       BIGINT,

    error_message            VARCHAR(500),
    escalated                BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processing_started_at    TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ
);

CREATE INDEX idx_data_export_email        ON data_export_requests(email);
CREATE INDEX idx_data_export_status       ON data_export_requests(status);
CREATE UNIQUE INDEX idx_data_export_verif_token ON data_export_requests(verification_token) WHERE verification_token IS NOT NULL;
CREATE UNIQUE INDEX idx_data_export_dl_token    ON data_export_requests(download_token)     WHERE download_token IS NOT NULL;
