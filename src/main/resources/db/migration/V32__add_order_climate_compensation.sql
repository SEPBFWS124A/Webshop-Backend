ALTER TABLE orders
    ADD COLUMN climate_contribution_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN carbon_compensation_selected BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN total_co2_emission_kg NUMERIC(10, 3) NOT NULL DEFAULT 0;
