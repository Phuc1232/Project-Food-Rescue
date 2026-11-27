ALTER TABLE ledger_entry ADD COLUMN business_key varchar(255);
CREATE UNIQUE INDEX uq_ledger_entry_business_key ON ledger_entry(business_key) WHERE business_key IS NOT NULL;
CREATE INDEX idx_ledger_entry_store_occurred ON ledger_entry(store_id, occurred_at DESC, id);
