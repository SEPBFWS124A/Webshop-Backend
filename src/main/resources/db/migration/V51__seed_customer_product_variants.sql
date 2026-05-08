DO $$
DECLARE
    parent_id BIGINT;
    variant_space_gray_16 BIGINT;
    variant_space_gray_32 BIGINT;
    variant_silver_16 BIGINT;
BEGIN
    SELECT id INTO parent_id
    FROM products
    WHERE name = 'Laptop Pro 15'
      AND parent_product_id IS NULL
    LIMIT 1;

    IF parent_id IS NULL THEN
        RETURN;
    END IF;

    UPDATE products
    SET has_variants = TRUE,
        sku = COALESCE(sku, 'LAPTOP-PRO-15')
    WHERE id = parent_id;

    IF NOT EXISTS (
        SELECT 1
        FROM product_variant_attributes
        WHERE product_id = parent_id
    ) THEN
        WITH color_attribute AS (
            INSERT INTO product_variant_attributes (product_id, name, display_order)
            VALUES (parent_id, 'Farbe', 0)
            RETURNING id
        )
        INSERT INTO product_variant_attribute_values (attribute_id, value, display_order)
        SELECT id, value, display_order
        FROM color_attribute
        CROSS JOIN (
            VALUES ('Space Gray', 0), ('Silver', 1)
        ) AS values_to_insert(value, display_order);

        WITH memory_attribute AS (
            INSERT INTO product_variant_attributes (product_id, name, display_order)
            VALUES (parent_id, 'Arbeitsspeicher', 1)
            RETURNING id
        )
        INSERT INTO product_variant_attribute_values (attribute_id, value, display_order)
        SELECT id, value, display_order
        FROM memory_attribute
        CROSS JOIN (
            VALUES ('16 GB', 0), ('32 GB', 1)
        ) AS values_to_insert(value, display_order);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM products
        WHERE parent_product_id = parent_id
    ) THEN
        INSERT INTO products (
            name,
            description,
            image_url,
            recommended_retail_price,
            co2_emission_kg,
            eco_score,
            category,
            stock,
            sku,
            warehouse_position,
            purchasable,
            promoted,
            has_variants,
            parent_product_id
        )
        SELECT
            name || ' - Space Gray / 16 GB',
            description,
            image_url,
            recommended_retail_price,
            co2_emission_kg,
            eco_score,
            category,
            3,
            'LAPTOP-PRO-15-SG-16',
            warehouse_position,
            purchasable,
            FALSE,
            FALSE,
            parent_id
        FROM products
        WHERE id = parent_id
        RETURNING id INTO variant_space_gray_16;

        INSERT INTO products (
            name,
            description,
            image_url,
            recommended_retail_price,
            co2_emission_kg,
            eco_score,
            category,
            stock,
            sku,
            warehouse_position,
            purchasable,
            promoted,
            has_variants,
            parent_product_id
        )
        SELECT
            name || ' - Space Gray / 32 GB',
            description,
            image_url,
            recommended_retail_price + 220.00,
            co2_emission_kg,
            eco_score,
            category,
            0,
            'LAPTOP-PRO-15-SG-32',
            warehouse_position,
            purchasable,
            FALSE,
            FALSE,
            parent_id
        FROM products
        WHERE id = parent_id
        RETURNING id INTO variant_space_gray_32;

        INSERT INTO products (
            name,
            description,
            image_url,
            recommended_retail_price,
            co2_emission_kg,
            eco_score,
            category,
            stock,
            sku,
            warehouse_position,
            purchasable,
            promoted,
            has_variants,
            parent_product_id
        )
        SELECT
            name || ' - Silver / 16 GB',
            description,
            image_url,
            recommended_retail_price,
            co2_emission_kg,
            eco_score,
            category,
            5,
            'LAPTOP-PRO-15-SI-16',
            warehouse_position,
            purchasable,
            FALSE,
            FALSE,
            parent_id
        FROM products
        WHERE id = parent_id
        RETURNING id INTO variant_silver_16;

        INSERT INTO product_variant_options (product_id, attribute_name, attribute_value, display_order)
        VALUES
            (variant_space_gray_16, 'Farbe', 'Space Gray', 0),
            (variant_space_gray_16, 'Arbeitsspeicher', '16 GB', 1),
            (variant_space_gray_32, 'Farbe', 'Space Gray', 0),
            (variant_space_gray_32, 'Arbeitsspeicher', '32 GB', 1),
            (variant_silver_16, 'Farbe', 'Silver', 0),
            (variant_silver_16, 'Arbeitsspeicher', '16 GB', 1);
    END IF;
END $$;
