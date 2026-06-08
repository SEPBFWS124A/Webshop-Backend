-- Stellenausschreibungen
CREATE TABLE job_postings (
    id              BIGSERIAL    PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    description     TEXT         NOT NULL,
    employment_type VARCHAR(50)  NOT NULL,  -- VOLLZEIT | TEILZEIT | MINIJOB
    location        VARCHAR(255) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | INACTIVE | ARCHIVED
    display_order   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_postings_status ON job_postings(status);
CREATE INDEX idx_job_postings_order  ON job_postings(display_order);

-- Bewerbungen
CREATE TABLE job_applications (
    id              BIGSERIAL    PRIMARY KEY,
    job_posting_id  BIGINT       NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    applicant_name  VARCHAR(255) NOT NULL,
    applicant_email VARCHAR(255) NOT NULL,
    applicant_phone VARCHAR(50),
    motivation_text TEXT,
    status          VARCHAR(50)  NOT NULL DEFAULT 'OPEN', -- OPEN | ACCEPTED | REJECTED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_applications_posting ON job_applications(job_posting_id);
CREATE INDEX idx_job_applications_status  ON job_applications(status);

-- Hochgeladene Dateien pro Bewerbung (Anschreiben, Lebenslauf, etc.)
CREATE TABLE job_application_files (
    id                BIGSERIAL    PRIMARY KEY,
    application_id    BIGINT       NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    file_data         BYTEA        NOT NULL,
    content_type      VARCHAR(80)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    file_type         VARCHAR(50)  NOT NULL, -- COVER_LETTER | RESUME | OTHER
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_application_files_application ON job_application_files(application_id);
