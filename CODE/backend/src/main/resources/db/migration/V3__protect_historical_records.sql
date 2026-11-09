-- Historical facts are append-only. Corrections create new linked records.
CREATE FUNCTION reject_historical_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Historical records in % are append-only', TG_TABLE_NAME USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER immutable_audit_log BEFORE UPDATE OR DELETE ON audit_log FOR EACH ROW EXECUTE FUNCTION reject_historical_change();
CREATE TRIGGER immutable_menu_price_history BEFORE UPDATE OR DELETE ON menu_price_history FOR EACH ROW EXECUTE FUNCTION reject_historical_change();
CREATE TRIGGER immutable_box_item BEFORE UPDATE OR DELETE ON box_item FOR EACH ROW EXECUTE FUNCTION reject_historical_change();
CREATE TRIGGER immutable_box_inspection BEFORE UPDATE OR DELETE ON box_inspection FOR EACH ROW EXECUTE FUNCTION reject_historical_change();
CREATE TRIGGER immutable_financial_policy BEFORE UPDATE OR DELETE ON financial_policy FOR EACH ROW EXECUTE FUNCTION reject_historical_change();
CREATE TRIGGER immutable_financial_event BEFORE UPDATE OR DELETE ON financial_event FOR EACH ROW EXECUTE FUNCTION reject_historical_change();
CREATE TRIGGER immutable_ledger_entry BEFORE UPDATE OR DELETE ON ledger_entry FOR EACH ROW EXECUTE FUNCTION reject_historical_change();
-- Draft statements may be rebuilt. Their lines freeze when the statement is confirmed.
CREATE FUNCTION protect_settlement_line() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status varchar;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        SELECT status INTO parent_status FROM settlement WHERE id = OLD.settlement_id FOR UPDATE;
        IF parent_status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Confirmed settlement lines cannot change' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        SELECT status INTO parent_status FROM settlement WHERE id = NEW.settlement_id FOR UPDATE;
        IF parent_status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Cannot add lines to confirmed settlement' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER protect_settlement_line BEFORE INSERT OR UPDATE OR DELETE ON settlement_line
FOR EACH ROW EXECUTE FUNCTION protect_settlement_line();

-- Mutable order state is separate from the commercial agreement accepted at checkout.
CREATE FUNCTION protect_order_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (to_jsonb(NEW) - ARRAY['status','pickup_code_hash','completed_at','updated_at','version'])
       IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['status','pickup_code_hash','completed_at','updated_at','version']) THEN
        RAISE EXCEPTION 'Accepted order terms cannot change' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_order_terms BEFORE UPDATE ON customer_order FOR EACH ROW EXECUTE FUNCTION protect_order_terms();
