UPDATE products
SET sku = CASE name
    WHEN 'Laptop Pro 15' THEN 'LAPTOP-PRO-15'
    WHEN 'Wireless Mouse' THEN 'WIRELESS-MOUSE'
    WHEN 'Standing Desk' THEN 'STANDING-DESK'
    WHEN 'USB-C Hub' THEN 'USB-C-HUB'
    WHEN 'Office Chair' THEN 'OFFICE-CHAIR'
    WHEN 'Notebook (Draft)' THEN 'NOTEBOOK-DRAFT'
    ELSE sku
END
WHERE parent_product_id IS NULL
  AND (sku IS NULL OR sku = '')
  AND name IN (
      'Laptop Pro 15',
      'Wireless Mouse',
      'Standing Desk',
      'USB-C Hub',
      'Office Chair',
      'Notebook (Draft)'
  );
