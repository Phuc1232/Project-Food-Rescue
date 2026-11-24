-- AC-01..12: Complaint workflow and additional refunds on an original payment.
ALTER TABLE complaint
 ADD COLUMN complaint_code varchar(64),
 ADD COLUMN within_dispute_window boolean NOT NULL DEFAULT true,
 ADD COLUMN requested_info_note text,
 ADD COLUMN outcome varchar(32) CHECK (outcome IS NULL OR outcome IN ('APPROVED','PARTIAL','REJECTED')),
 ADD COLUMN responsibility varchar(32) CHECK (responsibility IS NULL OR responsibility IN ('STORE','PLATFORM','CUSTOMER','SHARED','NONE')),
 ADD COLUMN approved_refund_amount numeric(19,2) CHECK (approved_refund_amount IS NULL OR (approved_refund_amount>=0 AND approved_refund_amount=trunc(approved_refund_amount))),
 ADD COLUMN store_adjustment_amount numeric(19,2) CHECK (store_adjustment_amount IS NULL OR store_adjustment_amount=trunc(store_adjustment_amount)),
 ADD COLUMN commission_adjustment_amount numeric(19,2) CHECK (commission_adjustment_amount IS NULL OR commission_adjustment_amount=trunc(commission_adjustment_amount)),
 ADD COLUMN closed_at timestamptz,
 ADD COLUMN request_fingerprint text,
 ADD COLUMN decision_key varchar(255),
 ADD COLUMN decision_fingerprint text;
UPDATE complaint SET complaint_code='CMP-' || upper(replace(left(id::text,13),'-',''));
ALTER TABLE complaint ALTER COLUMN complaint_code SET NOT NULL;
CREATE UNIQUE INDEX uq_complaint_code ON complaint(complaint_code);
CREATE UNIQUE INDEX uq_complaint_customer_idempotency ON complaint(customer_id,idempotency_key) WHERE idempotency_key IS NOT NULL AND source_box_id IS NULL;
CREATE UNIQUE INDEX uq_complaint_decision_key ON complaint(id,decision_key) WHERE decision_key IS NOT NULL;
CREATE INDEX idx_complaint_status_created ON complaint(status,created_at DESC);
DROP INDEX IF EXISTS ux_refund_request_payment;
CREATE UNIQUE INDEX ux_refund_request_payment_non_complaint ON refund_request(payment_id) WHERE payment_id IS NOT NULL AND complaint_id IS NULL;
