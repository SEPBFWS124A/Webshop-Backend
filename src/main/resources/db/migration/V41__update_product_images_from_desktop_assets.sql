UPDATE products
SET image_url = CASE name
    WHEN 'Laptop Pro 15' THEN '/laptop-pro-15.jpg'
    WHEN 'Wireless Mouse' THEN '/wireless-mouse.jpg'
    WHEN 'Standing Desk' THEN '/standing-desk.jpg'
    WHEN 'USB-C Hub' THEN '/usb-c-hub.jpg'
    WHEN 'Office Chair' THEN '/office-chair.jpg'
    ELSE image_url
END
WHERE name IN (
    'Laptop Pro 15',
    'Wireless Mouse',
    'Standing Desk',
    'USB-C Hub',
    'Office Chair'
);
