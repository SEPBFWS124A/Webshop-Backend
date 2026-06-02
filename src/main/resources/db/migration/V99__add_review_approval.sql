ALTER TABLE product_feedback ADD COLUMN approved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE seller_reviews   ADD COLUMN approved BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE product_feedback SET approved = TRUE;
UPDATE seller_reviews   SET approved = TRUE;
