-- V107: Preisverlauf-Tabelle für UVP-Änderungshistorie
CREATE TABLE product_price_history (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT         NOT NULL
                    REFERENCES products(id) ON DELETE CASCADE,
    old_price     NUMERIC(12, 2),
    new_price     NUMERIC(12, 2) NOT NULL,
    change_reason VARCHAR(50)    NOT NULL DEFAULT 'MANUAL',
    changed_by    BIGINT
                    REFERENCES users(id) ON DELETE SET NULL,
    changed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Einträge sind immutable (App-Layer-Guard + Kommentar für DB-Reviewer)
-- Kein UPDATE/DELETE auf dieser Tabelle erlaubt

CREATE INDEX idx_pph_product_date
    ON product_price_history (product_id, changed_at DESC);

CREATE INDEX idx_pph_changed_by
    ON product_price_history (changed_by);
