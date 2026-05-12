ALTER TABLE products
    ADD COLUMN IF NOT EXISTS product_type VARCHAR(40) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE cart_items
    ADD COLUMN IF NOT EXISTS gift_card_amount NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS gift_card_recipient_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS gift_card_message VARCHAR(1000);

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS gift_card_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS gift_card_amount NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS gift_card_recipient_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS gift_card_message VARCHAR(1000);

INSERT INTO products (
    name,
    description,
    image_url,
    recommended_retail_price,
    co2_emission_kg,
    eco_score,
    category,
    product_type,
    stock,
    sku,
    warehouse_position,
    purchasable,
    promoted,
    personalizable,
    personalization_max_length
)
SELECT
    'Digitaler Geschenkgutschein',
    'Digitaler Gutschein mit frei wählbarem Betrag. Optional direkt per E-Mail mit persönlicher Nachricht versenden.',
    NULL,
    15.00,
    0.000,
    'A',
    'Gutscheine',
    'DIGITAL_GIFT_CARD',
    999999,
    'DIGITAL-GIFT-CARD',
    NULL,
    TRUE,
    TRUE,
    FALSE,
    NULL
WHERE NOT EXISTS (
    SELECT 1 FROM products WHERE product_type = 'DIGITAL_GIFT_CARD'
);
