-- V1 order items have a product snapshot but no product identifier.
LOCK TABLE orders, order_items IN ACCESS EXCLUSIVE MODE;
LOCK TABLE products IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(50)
);

INSERT INTO customers (full_name, address, phone)
SELECT DISTINCT customer_full_name, customer_address, customer_phone FROM orders;

CREATE INDEX customers_address_idx ON customers (address);
ALTER TABLE orders ADD COLUMN customer_id BIGINT;
UPDATE orders o SET customer_id = c.id
FROM customers c
WHERE c.full_name = o.customer_full_name
  AND c.address IS NOT DISTINCT FROM o.customer_address
  AND c.phone IS NOT DISTINCT FROM o.customer_phone;

INSERT INTO products (name, price)
SELECT DISTINCT i.product_name, i.product_price
FROM order_items i
WHERE NOT EXISTS (
    SELECT 1 FROM products p WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items ADD COLUMN product_id BIGINT;
UPDATE order_items i SET product_id = p.product_id
FROM (SELECT name, price, min(id) AS product_id FROM products GROUP BY name, price) p
WHERE p.name = i.product_name AND p.price = i.product_price;

ALTER TABLE orders
    ALTER COLUMN customer_id SET NOT NULL,
    ADD FOREIGN KEY (customer_id) REFERENCES customers (id),
    DROP COLUMN customer_full_name,
    DROP COLUMN customer_address,
    DROP COLUMN customer_phone;
ALTER TABLE order_items
    ALTER COLUMN product_id SET NOT NULL,
    ADD FOREIGN KEY (product_id) REFERENCES products (id),
    DROP COLUMN product_name,
    DROP COLUMN product_price;

CREATE INDEX orders_customer_idx ON orders (customer_id);
CREATE INDEX order_items_order_idx ON order_items (order_id);
CREATE INDEX order_items_product_idx ON order_items (product_id);

-- Both application generations acquire the same transaction lock before testing identity.
-- The hash only selects a lock. Exact values distinguish customers, including NULL vs ''.
CREATE FUNCTION resolve_customer(p_name text, p_address text, p_phone text)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE
    customer_id bigint;
    contact_name varchar(255) := p_name;
    contact_address customers.address%TYPE := p_address;
    contact_phone customers.phone%TYPE := p_phone;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(contact_name, 0));
    SELECT id INTO customer_id FROM customers
    WHERE full_name = contact_name AND (address = contact_address OR (address IS NULL AND contact_address IS NULL))
      AND phone IS NOT DISTINCT FROM contact_phone
    ORDER BY id LIMIT 1;
    IF customer_id IS NULL THEN
        INSERT INTO customers (full_name, address, phone) VALUES (contact_name, contact_address, contact_phone)
        RETURNING id INTO customer_id;
    END IF;
    RETURN customer_id;
END;
$$;
