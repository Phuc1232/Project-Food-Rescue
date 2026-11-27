-- T8.4: Recipient encryption, settlement snapshots and manual payout audit details.
ALTER TABLE store_payout_account
 ADD COLUMN key_id varchar(64), ADD COLUMN account_number_masked varchar(64),
 ADD COLUMN created_by_id uuid REFERENCES account(id), ADD COLUMN verified_by_id uuid REFERENCES account(id),
 ADD COLUMN verification_note text;
CREATE UNIQUE INDEX uq_store_payout_account_active ON store_payout_account(store_id) WHERE effective_until IS NULL;
ALTER TABLE settlement ADD COLUMN settlement_code varchar(64), ADD COLUMN snapshot_fingerprint varchar(128),
 ADD COLUMN snapshot_taken_at timestamptz, ADD COLUMN held_exceptions_snapshot text;
CREATE UNIQUE INDEX uq_settlement_code ON settlement(settlement_code) WHERE settlement_code IS NOT NULL;
CREATE UNIQUE INDEX uq_settlement_draft_per_store ON settlement(store_id) WHERE status='DRAFT';
ALTER TABLE store_payout ADD COLUMN transferred_at timestamptz, ADD COLUMN recorded_by_id uuid REFERENCES account(id),
 ADD COLUMN failure_reason text, ADD COLUMN note text;
CREATE INDEX idx_settlement_store_status ON settlement(store_id,status,period_ends_at DESC);
CREATE INDEX idx_store_payout_settlement_status ON store_payout(settlement_id,status,created_at DESC);
