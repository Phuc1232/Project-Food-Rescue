-- AC-01..12 (T7.5): box impact replay and post-delivery safety complaints.
ALTER TABLE physical_box ADD COLUMN unfit_idempotency_key varchar(255);
ALTER TABLE physical_box ADD COLUMN unfit_fingerprint text;
ALTER TABLE physical_box ADD COLUMN unfit_result_json text;
CREATE UNIQUE INDEX uq_physical_box_unfit_idempotency ON physical_box(id,unfit_idempotency_key) WHERE unfit_idempotency_key IS NOT NULL;
ALTER TABLE complaint ADD COLUMN source_box_id uuid REFERENCES physical_box(id);
ALTER TABLE complaint ADD COLUMN idempotency_key varchar(255);
CREATE INDEX idx_complaint_source_box ON complaint(source_box_id) WHERE source_box_id IS NOT NULL;
CREATE UNIQUE INDEX uq_complaint_open_source_box ON complaint(source_box_id) WHERE source_box_id IS NOT NULL AND status IN ('OPEN','INVESTIGATING','AWAITING_EVIDENCE');
CREATE UNIQUE INDEX uq_complaint_source_box_key ON complaint(source_box_id,idempotency_key) WHERE source_box_id IS NOT NULL AND idempotency_key IS NOT NULL;
