ALTER TABLE affiliate_profiles
    ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'TIER_3';

UPDATE affiliate_profiles ap
SET tier = CASE
    WHEN (
        SELECT COALESCE(SUM(ac.purchase_amount), 0)
        FROM affiliate_conversions ac
        JOIN affiliate_links al ON ac.affiliate_link_id = al.id
        WHERE al.affiliate_profile_id = ap.id
    ) >= 50000 THEN 'TIER_1'
    WHEN (
        SELECT COALESCE(SUM(ac.purchase_amount), 0)
        FROM affiliate_conversions ac
        JOIN affiliate_links al ON ac.affiliate_link_id = al.id
        WHERE al.affiliate_profile_id = ap.id
    ) >= 10000 THEN 'TIER_2'
    ELSE 'TIER_3'
END;
