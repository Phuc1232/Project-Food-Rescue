-- T5.3: why and when the order status last changed. The accepted terms stay immutable: the V3 trigger is replaced so that only these
-- two state columns are added to the list of columns allowed to change.
ALTER TABLE customer_order ADD COLUMN status_reason text;
ALTER TABLE customer_order ADD COLUMN status_changed_at timestamptz;

CREATE OR REPLACE FUNCTION protect_order_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (to_jsonb(NEW) - ARRAY['status','pickup_code_hash','completed_at','updated_at','version','status_reason','status_changed_at'])
       IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['status','pickup_code_hash','completed_at','updated_at','version','status_reason','status_changed_at']) THEN
        RAISE EXCEPTION 'Accepted order terms cannot change' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
