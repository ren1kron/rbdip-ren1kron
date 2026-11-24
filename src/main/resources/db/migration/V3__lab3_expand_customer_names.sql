SET LOCAL lock_timeout = '2s';

ALTER TABLE customers
    ADD COLUMN first_name VARCHAR(255),
    ADD COLUMN last_name VARCHAR(255),
    ADD CONSTRAINT customer_name_parts_present
        CHECK (first_name IS NOT NULL AND last_name IS NOT NULL) NOT VALID;

-- Split one inter-word space. Extra spaces stay in the original components,
-- so reconstructing the display name also works for mononyms and old whitespace.
CREATE FUNCTION split_customer_name(p_name text, OUT first_name text, OUT last_name text)
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT coalesce(parts[1], p_name), coalesce(parts[2], '')
    FROM (SELECT regexp_match(p_name, '^( *[^ ]+) (.*[^ ].*)$') AS parts) split
$$;

CREATE FUNCTION customer_display_name(p_first text, p_last text)
RETURNS text LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT CASE WHEN p_last = '' THEN p_first ELSE p_first || ' ' || p_last END
$$;

ALTER TABLE customers ADD CONSTRAINT customer_name_length
    CHECK (char_length(customer_display_name(first_name, last_name)) <= 255) NOT VALID;

CREATE FUNCTION sync_customer_names() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    full_changed boolean;
    parts_changed boolean;
BEGIN
    IF TG_OP = 'INSERT' THEN
        full_changed := NEW.full_name IS NOT NULL;
        parts_changed := NEW.first_name IS NOT NULL OR NEW.last_name IS NOT NULL;
    ELSE
        full_changed := NEW.full_name IS DISTINCT FROM OLD.full_name;
        parts_changed := (NEW.first_name, NEW.last_name) IS DISTINCT FROM (OLD.first_name, OLD.last_name);
    END IF;

    IF full_changed AND parts_changed AND
            NEW.full_name IS DISTINCT FROM customer_display_name(NEW.first_name, NEW.last_name) THEN
        RAISE EXCEPTION 'Conflicting customer name representations' USING ERRCODE = '23514';
    END IF;

    IF full_changed OR (NOT parts_changed AND NEW.first_name IS NULL) THEN
        SELECT s.first_name, s.last_name INTO NEW.first_name, NEW.last_name
        FROM split_customer_name(NEW.full_name) s;
    ELSIF parts_changed THEN
        NEW.full_name := customer_display_name(NEW.first_name, NEW.last_name);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER sync_customer_names BEFORE INSERT OR UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION sync_customer_names();
