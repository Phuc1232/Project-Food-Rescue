-- AC-01,02,12,14 (T7.1): reversible pickup credentials live on the order, alongside the existing unique lookup hash.
ALTER TABLE customer_order ADD COLUMN pickup_code_encrypted text;
ALTER TABLE customer_order ADD COLUMN pickup_code_key_id varchar(64);
ALTER TABLE customer_order ADD CONSTRAINT ck_order_pickup_code_storage CHECK (
    (pickup_code_encrypted IS NULL AND pickup_code_key_id IS NULL)
    OR (pickup_code_encrypted IS NOT NULL AND pickup_code_key_id IS NOT NULL AND pickup_code_hash IS NOT NULL)
);

-- Extend the mutable credential fields only. Accepted order terms remain immutable.
CREATE OR REPLACE FUNCTION protect_order_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (to_jsonb(NEW) - ARRAY['status','pickup_code_hash','pickup_code_encrypted','pickup_code_key_id','completed_at','updated_at','version','status_reason','status_changed_at'])
       IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['status','pickup_code_hash','pickup_code_encrypted','pickup_code_key_id','completed_at','updated_at','version','status_reason','status_changed_at']) THEN
        RAISE EXCEPTION 'Accepted order terms cannot change' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
