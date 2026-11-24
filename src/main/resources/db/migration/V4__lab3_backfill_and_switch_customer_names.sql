UPDATE customers c SET (first_name, last_name) =
    (SELECT s.first_name, s.last_name FROM split_customer_name(c.full_name) s)
WHERE c.first_name IS NULL OR c.last_name IS NULL;

ALTER TABLE customers
    VALIDATE CONSTRAINT customer_name_parts_present,
    VALIDATE CONSTRAINT customer_name_length;

-- Keep the V2 checkout function callable while switching its storage to the new fields.
CREATE OR REPLACE FUNCTION resolve_customer(p_name text, p_address text, p_phone text)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE
    customer_id bigint;
    contact_name varchar(255) := p_name;
    contact_address customers.address%TYPE := p_address;
    contact_phone customers.phone%TYPE := p_phone;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(contact_name, 0));
    SELECT id INTO customer_id FROM customers
    WHERE customer_display_name(first_name, last_name) = contact_name
      AND (address = contact_address OR (address IS NULL AND contact_address IS NULL))
      AND phone IS NOT DISTINCT FROM contact_phone
    ORDER BY id LIMIT 1;
    IF customer_id IS NULL THEN
        INSERT INTO customers (first_name, last_name, address, phone)
        SELECT s.first_name, s.last_name, contact_address, contact_phone FROM split_customer_name(contact_name) s
        RETURNING id INTO customer_id;
    END IF;
    RETURN customer_id;
END;
$$;
