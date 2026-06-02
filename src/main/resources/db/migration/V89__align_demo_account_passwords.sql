-- Password for all listed accounts: Password1!

UPDATE users
SET password_hash = '$2a$10$yT.Ge6bLC.BWERriPv/wguUMUtBF4iA3W0Q5VNDklGalWYlGy3Zze'
WHERE username IN (
    'demo_vertrieb',
    'demo_kunde1',
    'demo_kunde2',
    'demo_seller_alpha',
    'demo_seller_beta'
);
