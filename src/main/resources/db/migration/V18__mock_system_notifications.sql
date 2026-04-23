-- =============================================================================
-- V14 – Demo-Benachrichtigungen für Verkaufs-Monitoring (Issue #90)
-- Referenziert die ersten drei kaufbaren Produkte aus dem Produktkatalog.
-- =============================================================================

DO $$
DECLARE
    v_prod1 BIGINT;
    v_prod2 BIGINT;
    v_prod3 BIGINT;
    v_prod4 BIGINT;
BEGIN
    SELECT id INTO v_prod1 FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 0;
    SELECT id INTO v_prod2 FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 1;
    SELECT id INTO v_prod3 FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 2;
    SELECT id INTO v_prod4 FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 3;

    -- Ungelesen: Starker Verkaufsrückgang (-42,5 %)
    IF v_prod1 IS NOT NULL THEN
        INSERT INTO system_notifications
            (type, product_id, product_name, change_percent, current_period_units, previous_period_units, is_read, created_at)
        SELECT 'SALES_DROP', v_prod1, p.name, -42.50, 23, 40, FALSE, NOW() - INTERVAL '2 days'
        FROM products p WHERE p.id = v_prod1;
    END IF;

    -- Ungelesen: Deutlicher Verkaufsanstieg (+35 %)
    IF v_prod2 IS NOT NULL THEN
        INSERT INTO system_notifications
            (type, product_id, product_name, change_percent, current_period_units, previous_period_units, is_read, created_at)
        SELECT 'SALES_INCREASE', v_prod2, p.name, 35.00, 54, 40, FALSE, NOW() - INTERVAL '2 days'
        FROM products p WHERE p.id = v_prod2;
    END IF;

    -- Ungelesen: Keine Verkäufe im letzten Monat
    IF v_prod3 IS NOT NULL THEN
        INSERT INTO system_notifications
            (type, product_id, product_name, change_percent, current_period_units, previous_period_units, is_read, created_at)
        SELECT 'ZERO_SALES', v_prod3, p.name, -100.00, 0, 15, FALSE, NOW() - INTERVAL '1 day'
        FROM products p WHERE p.id = v_prod3;
    END IF;

    -- Ungelesen: Moderater Rückgang (-25 %)
    IF v_prod4 IS NOT NULL THEN
        INSERT INTO system_notifications
            (type, product_id, product_name, change_percent, current_period_units, previous_period_units, is_read, created_at)
        SELECT 'SALES_DROP', v_prod4, p.name, -25.00, 30, 40, FALSE, NOW() - INTERVAL '3 days'
        FROM products p WHERE p.id = v_prod4;
    END IF;

    -- Bereits gelesen: ältere Benachrichtigung (historischer Kontext)
    IF v_prod1 IS NOT NULL THEN
        INSERT INTO system_notifications
            (type, product_id, product_name, change_percent, current_period_units, previous_period_units, is_read, created_at)
        SELECT 'ZERO_SALES', v_prod1, p.name, -100.00, 0, 8, TRUE, NOW() - INTERVAL '35 days'
        FROM products p WHERE p.id = v_prod1;
    END IF;

    IF v_prod2 IS NOT NULL THEN
        INSERT INTO system_notifications
            (type, product_id, product_name, change_percent, current_period_units, previous_period_units, is_read, created_at)
        SELECT 'SALES_DROP', v_prod2, p.name, -22.00, 78, 100, TRUE, NOW() - INTERVAL '40 days'
        FROM products p WHERE p.id = v_prod2;
    END IF;
END $$;
