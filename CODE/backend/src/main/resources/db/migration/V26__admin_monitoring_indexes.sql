-- T8.5: Read-only monitoring indexes. V24 already provides idx_ledger_entry_store_occurred (store_id, occurred_at DESC, id).
CREATE INDEX idx_audit_log_store_occurred ON audit_log(store_id,occurred_at DESC,id);
CREATE INDEX idx_audit_log_occurred ON audit_log(occurred_at DESC,id);
CREATE INDEX idx_audit_log_entity_occurred ON audit_log(entity_type,entity_id,occurred_at DESC);
CREATE INDEX idx_customer_order_store_accepted ON customer_order(store_id,accepted_at DESC);
