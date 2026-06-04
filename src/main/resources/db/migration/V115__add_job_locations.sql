-- Physische Standorte (Filialen, Lager, Büros) für Stellenausschreibungen
CREATE TABLE job_locations (
    id            BIGSERIAL        PRIMARY KEY,
    name          VARCHAR(255)     NOT NULL,
    street        VARCHAR(255)     NOT NULL,
    house_number  VARCHAR(20)      NOT NULL,
    postal_code   VARCHAR(10)      NOT NULL,
    city          VARCHAR(100)     NOT NULL,
    location_type VARCHAR(50)      NOT NULL, -- FILIALE | LAGER | VERWALTUNG
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_locations_type ON job_locations(location_type);

-- Stellenausschreibungen um FK auf Standort erweitern
ALTER TABLE job_postings
    ADD COLUMN job_location_id BIGINT REFERENCES job_locations(id) ON DELETE SET NULL;

ALTER TABLE job_postings ALTER COLUMN location DROP NOT NULL;

CREATE INDEX idx_job_postings_location_fk ON job_postings(job_location_id);
