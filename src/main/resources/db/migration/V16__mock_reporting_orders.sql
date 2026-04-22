-- =============================================================================
-- V16 – Mock-Bestellungen für Reporting & Umsatzstatistiken (Issue #89)
-- Erstellt realistische Bestellhistorie für demo_kunde1 und demo_kunde2
-- mit Standard- und Rabattpreisen, verteilt über mehrere Monate
-- =============================================================================

DO $$
DECLARE
    v_cust1     BIGINT;
    v_cust2     BIGINT;
    v_prod1     BIGINT;
    v_prod2     BIGINT;
    v_prod3     BIGINT;
    v_prod4     BIGINT;
    v_prod5     BIGINT;
    v_rrp1      NUMERIC(10,2);
    v_rrp2      NUMERIC(10,2);
    v_rrp3      NUMERIC(10,2);
    v_rrp4      NUMERIC(10,2);
    v_rrp5      NUMERIC(10,2);
    v_order_id  BIGINT;
BEGIN
    SELECT id INTO v_cust1 FROM users WHERE username = 'demo_kunde1';
    SELECT id INTO v_cust2 FROM users WHERE username = 'demo_kunde2';

    SELECT id, recommended_retail_price INTO v_prod1, v_rrp1
      FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 0;
    SELECT id, recommended_retail_price INTO v_prod2, v_rrp2
      FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 1;
    SELECT id, recommended_retail_price INTO v_prod3, v_rrp3
      FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 2;
    SELECT id, recommended_retail_price INTO v_prod4, v_rrp4
      FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 3;
    SELECT id, recommended_retail_price INTO v_prod5, v_rrp5
      FROM products WHERE purchasable = TRUE ORDER BY id LIMIT 1 OFFSET 4;

    IF v_cust1 IS NULL OR v_prod1 IS NULL THEN
        RETURN;
    END IF;

    -- -------------------------------------------------------------------------
    -- demo_kunde1: Bestellung 1 – vor 5 Monaten, Standardpreis
    -- -------------------------------------------------------------------------
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, discount_amount, created_at)
    VALUES (v_cust1,
            'ORD-DEMO-001',
            'kunde1@demo.de', 'Demo Kunde 1',
            'Musterstraße 1', 'Berlin', '10115', 'Deutschland',
            ROUND(v_rrp1 * 2 * 1.19 + 4.99, 2),
            ROUND(v_rrp1 * 2 * 0.19, 2),
            4.99, 'STANDARD',
            'DELIVERED', 0.00,
            NOW() - INTERVAL '5 months')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod1, 2, v_rrp1);
    END IF;

    -- -------------------------------------------------------------------------
    -- demo_kunde1: Bestellung 2 – vor 4 Monaten, Artikel 1 mit Rabatt + Artikel 2
    -- -------------------------------------------------------------------------
    v_order_id := NULL;
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, discount_amount, created_at)
    VALUES (v_cust1,
            'ORD-DEMO-002',
            'kunde1@demo.de', 'Demo Kunde 1',
            'Musterstraße 1', 'Berlin', '10115', 'Deutschland',
            ROUND((v_rrp1 * 0.85 + v_rrp2) * 1.19 + 4.99, 2),
            ROUND((v_rrp1 * 0.85 + v_rrp2) * 0.19, 2),
            4.99, 'STANDARD',
            'DELIVERED', ROUND(v_rrp1 * 0.15, 2),
            NOW() - INTERVAL '4 months')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod1, 1, ROUND(v_rrp1 * 0.85, 2)),
               (v_order_id, v_prod2, 1, v_rrp2);
    END IF;

    -- -------------------------------------------------------------------------
    -- demo_kunde1: Bestellung 3 – vor 3 Monaten, Express-Versand
    -- -------------------------------------------------------------------------
    v_order_id := NULL;
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, discount_amount, created_at)
    VALUES (v_cust1,
            'ORD-DEMO-003',
            'kunde1@demo.de', 'Demo Kunde 1',
            'Musterstraße 1', 'Berlin', '10115', 'Deutschland',
            ROUND(v_rrp3 * 3 * 1.19 + 9.99, 2),
            ROUND(v_rrp3 * 3 * 0.19, 2),
            9.99, 'EXPRESS',
            'SHIPPED', 0.00,
            NOW() - INTERVAL '3 months')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod3, 3, v_rrp3);
    END IF;

    -- -------------------------------------------------------------------------
    -- demo_kunde1: Bestellung 4 – vor 2 Monaten, Rabatt 10%
    -- -------------------------------------------------------------------------
    v_order_id := NULL;
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, discount_amount, created_at)
    VALUES (v_cust1,
            'ORD-DEMO-004',
            'kunde1@demo.de', 'Demo Kunde 1',
            'Musterstraße 1', 'Berlin', '10115', 'Deutschland',
            ROUND((v_rrp2 * 0.90 + v_rrp4) * 1.19 + 4.99, 2),
            ROUND((v_rrp2 * 0.90 + v_rrp4) * 0.19, 2),
            4.99, 'STANDARD',
            'CONFIRMED', ROUND(v_rrp2 * 0.10, 2),
            NOW() - INTERVAL '2 months')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL AND v_prod4 IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod2, 1, ROUND(v_rrp2 * 0.90, 2)),
               (v_order_id, v_prod4, 1, v_rrp4);
    ELSIF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod2, 1, ROUND(v_rrp2 * 0.90, 2));
    END IF;

    -- -------------------------------------------------------------------------
    -- demo_kunde1: Bestellung 5 – vor 1 Monat, mit Coupon 15%
    -- -------------------------------------------------------------------------
    v_order_id := NULL;
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, coupon_code, discount_amount, created_at)
    VALUES (v_cust1,
            'ORD-DEMO-005',
            'kunde1@demo.de', 'Demo Kunde 1',
            'Musterstraße 1', 'Berlin', '10115', 'Deutschland',
            ROUND(v_rrp1 * 0.85 * 2 * 1.19, 2),
            ROUND(v_rrp1 * 0.85 * 2 * 0.19, 2),
            0.00, 'STANDARD',
            'PENDING', 'SUMMER15', ROUND(v_rrp1 * 0.15 * 2, 2),
            NOW() - INTERVAL '1 month')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod1, 2, ROUND(v_rrp1 * 0.85, 2));
    END IF;

    -- -------------------------------------------------------------------------
    -- demo_kunde1: Bestellung 6 – letzte Woche
    -- -------------------------------------------------------------------------
    v_order_id := NULL;
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, discount_amount, created_at)
    VALUES (v_cust1,
            'ORD-DEMO-006',
            'kunde1@demo.de', 'Demo Kunde 1',
            'Musterstraße 1', 'Berlin', '10115', 'Deutschland',
            ROUND((v_rrp1 + v_rrp3) * 1.19 + 4.99, 2),
            ROUND((v_rrp1 + v_rrp3) * 0.19, 2),
            4.99, 'STANDARD',
            'CONFIRMED', 0.00,
            NOW() - INTERVAL '7 days')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod1, 1, v_rrp1),
               (v_order_id, v_prod3, 1, v_rrp3);
    END IF;

    -- =========================================================================
    -- demo_kunde2: 4 Bestellungen (Geschäftskunde, größere Mengen)
    -- =========================================================================
    IF v_cust2 IS NULL THEN RETURN; END IF;

    -- Bestellung 1 – vor 6 Monaten
    v_order_id := NULL;
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, discount_amount, created_at)
    VALUES (v_cust2,
            'ORD-DEMO-007',
            'kunde2@demo.de', 'Demo GmbH',
            'Industriestraße 10', 'Hamburg', '20095', 'Deutschland',
            ROUND(v_rrp2 * 5 * 1.19, 2),
            ROUND(v_rrp2 * 5 * 0.19, 2),
            0.00, 'STANDARD',
            'DELIVERED', 0.00,
            NOW() - INTERVAL '6 months')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod2, 5, v_rrp2);
    END IF;

    -- Bestellung 2 – vor 4 Monaten, Rabatt 20%
    v_order_id := NULL;
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, discount_amount, created_at)
    VALUES (v_cust2,
            'ORD-DEMO-008',
            'kunde2@demo.de', 'Demo GmbH',
            'Industriestraße 10', 'Hamburg', '20095', 'Deutschland',
            ROUND(v_rrp1 * 0.80 * 10 * 1.19, 2),
            ROUND(v_rrp1 * 0.80 * 10 * 0.19, 2),
            0.00, 'STANDARD',
            'DELIVERED', ROUND(v_rrp1 * 0.20 * 10, 2),
            NOW() - INTERVAL '4 months')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod1, 10, ROUND(v_rrp1 * 0.80, 2));
    END IF;

    -- Bestellung 3 – vor 2 Monaten
    v_order_id := NULL;
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, discount_amount, created_at)
    VALUES (v_cust2,
            'ORD-DEMO-009',
            'kunde2@demo.de', 'Demo GmbH',
            'Industriestraße 10', 'Hamburg', '20095', 'Deutschland',
            ROUND((v_rrp3 * 4 + v_rrp2 * 2) * 1.19 + 4.99, 2),
            ROUND((v_rrp3 * 4 + v_rrp2 * 2) * 0.19, 2),
            4.99, 'STANDARD',
            'SHIPPED', 0.00,
            NOW() - INTERVAL '2 months')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod3, 4, v_rrp3),
               (v_order_id, v_prod2, 2, v_rrp2);
    END IF;

    -- Bestellung 4 – letzte Woche, Rabatt 25%
    v_order_id := NULL;
    INSERT INTO orders (customer_id, order_number, customer_email, customer_name,
                        delivery_street, delivery_city, delivery_postal_code, delivery_country,
                        total_price, tax_amount, shipping_cost, shipping_method,
                        status, discount_amount, created_at)
    VALUES (v_cust2,
            'ORD-DEMO-010',
            'kunde2@demo.de', 'Demo GmbH',
            'Industriestraße 10', 'Hamburg', '20095', 'Deutschland',
            ROUND(v_rrp3 * 0.75 * 6 * 1.19, 2),
            ROUND(v_rrp3 * 0.75 * 6 * 0.19, 2),
            0.00, 'EXPRESS',
            'PENDING', ROUND(v_rrp3 * 0.25 * 6, 2),
            NOW() - INTERVAL '5 days')
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_order_id;

    IF v_order_id IS NOT NULL THEN
        INSERT INTO order_items (order_id, product_id, quantity, price_at_order_time)
        VALUES (v_order_id, v_prod3, 6, ROUND(v_rrp3 * 0.75, 2));
    END IF;

END $$;
