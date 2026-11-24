-- A busy database can retry this transactional step without partially dropping the old API.
SET LOCAL lock_timeout = '10s';
LOCK TABLE customers IN ACCESS EXCLUSIVE MODE;

ALTER TABLE customers
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL;

DROP TRIGGER sync_customer_names ON customers;
DROP FUNCTION sync_customer_names();

ALTER TABLE customers
    DROP CONSTRAINT customer_name_parts_present,
    DROP COLUMN full_name;
