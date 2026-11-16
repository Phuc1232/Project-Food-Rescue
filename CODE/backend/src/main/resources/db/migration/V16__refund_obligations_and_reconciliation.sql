-- T6.4: a refund obligation points at the payment it returns (one obligation per payment) and says why; reconciliation notes on payment requests.
ALTER TABLE refund_request ADD COLUMN payment_id uuid REFERENCES payment(id);
ALTER TABLE refund_request ADD COLUMN reason_code varchar(50);
CREATE UNIQUE INDEX ux_refund_request_payment ON refund_request (payment_id) WHERE payment_id IS NOT NULL;

ALTER TABLE payment_request ADD COLUMN reconcile_note text;
ALTER TABLE payment_request ADD COLUMN reconciled_at timestamptz;
CREATE INDEX ON payment (order_id);
CREATE INDEX ON payment (request_id);
CREATE INDEX ON payment (status);
