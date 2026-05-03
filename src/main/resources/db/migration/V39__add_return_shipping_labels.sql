ALTER TABLE return_requests
    ADD COLUMN IF NOT EXISTS label_created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS carrier_name VARCHAR(80) NOT NULL DEFAULT 'Webshop Retouren',
    ADD COLUMN IF NOT EXISTS tracking_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS qr_code_payload VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sender_street VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sender_postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS sender_city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS sender_country VARCHAR(100),
    ADD COLUMN IF NOT EXISTS return_center_name VARCHAR(255) NOT NULL DEFAULT 'Webshop Ruecksendezentrum',
    ADD COLUMN IF NOT EXISTS return_center_street VARCHAR(255) NOT NULL DEFAULT 'Retourenstrasse 12',
    ADD COLUMN IF NOT EXISTS return_center_postal_code VARCHAR(20) NOT NULL DEFAULT '33602',
    ADD COLUMN IF NOT EXISTS return_center_city VARCHAR(100) NOT NULL DEFAULT 'Bielefeld',
    ADD COLUMN IF NOT EXISTS return_center_country VARCHAR(100) NOT NULL DEFAULT 'Deutschland';

UPDATE return_requests rr
SET tracking_id = COALESCE(rr.tracking_id, 'WSR-' || rr.id || '-' || TO_CHAR(rr.created_at, 'YYYYMMDD')),
    qr_code_payload = COALESCE(rr.qr_code_payload, 'webshop-return:' || rr.id || ':' || COALESCE(rr.tracking_id, 'WSR-' || rr.id || '-' || TO_CHAR(rr.created_at, 'YYYYMMDD'))),
    sender_name = COALESCE(rr.sender_name, COALESCE(o.customer_name, u.username, 'Kunde')),
    sender_street = COALESCE(rr.sender_street, COALESCE(o.delivery_street, 'Adresse unbekannt')),
    sender_postal_code = COALESCE(rr.sender_postal_code, COALESCE(o.delivery_postal_code, '00000')),
    sender_city = COALESCE(rr.sender_city, COALESCE(o.delivery_city, 'Unbekannt')),
    sender_country = COALESCE(rr.sender_country, COALESCE(o.delivery_country, 'Deutschland'))
FROM orders o, users u
WHERE o.id = rr.order_id
  AND u.id = rr.customer_id;

ALTER TABLE return_requests
    ALTER COLUMN tracking_id SET NOT NULL,
    ALTER COLUMN qr_code_payload SET NOT NULL,
    ALTER COLUMN sender_name SET NOT NULL,
    ALTER COLUMN sender_street SET NOT NULL,
    ALTER COLUMN sender_postal_code SET NOT NULL,
    ALTER COLUMN sender_city SET NOT NULL,
    ALTER COLUMN sender_country SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_return_requests_tracking_id
    ON return_requests (tracking_id);
