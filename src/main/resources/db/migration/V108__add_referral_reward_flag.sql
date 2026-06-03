-- Referrer-Belohnung wird erst nach erster Bestellung der beworbenen Person gewährt
ALTER TABLE referrals ADD COLUMN referrer_rewarded BOOLEAN NOT NULL DEFAULT FALSE;
