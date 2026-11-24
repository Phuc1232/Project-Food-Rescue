-- AC-05..15 (T7.3): cancellation review, idempotency and evidence ownership.
CREATE TABLE order_cancellation_request (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    order_id uuid NOT NULL REFERENCES customer_order(id),
    requested_by_id uuid NOT NULL REFERENCES account(id),
    initiator varchar(50) NOT NULL CHECK (initiator IN ('CUSTOMER','STORE')),
    status varchar(50) NOT NULL CHECK (status IN ('SUBMITTED','NEEDS_INFO','APPROVED','REJECTED','WITHDRAWN')),
    reason text NOT NULL CHECK (length(trim(reason)) > 0),
    store_fault boolean NOT NULL,
    requested_at timestamptz NOT NULL,
    idempotency_key varchar(255),
    request_fingerprint text NOT NULL,
    reviewer_id uuid REFERENCES account(id),
    reviewed_at timestamptz,
    review_note text,
    approved_refund_amount numeric(19,2) CHECK (approved_refund_amount >= 0 AND approved_refund_amount = trunc(approved_refund_amount)),
    refund_request_id uuid REFERENCES refund_request(id),
    decision_key varchar(255),
    decision_fingerprint text
);
CREATE UNIQUE INDEX uq_order_open_cancellation ON order_cancellation_request(order_id) WHERE status IN ('SUBMITTED','NEEDS_INFO');
CREATE UNIQUE INDEX uq_order_cancellation_idempotency ON order_cancellation_request(order_id,idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ON order_cancellation_request(status, requested_at);
ALTER TABLE attachment ADD COLUMN order_cancellation_request_id uuid REFERENCES order_cancellation_request(id);
CREATE INDEX ON attachment(order_cancellation_request_id);
ALTER TABLE attachment DROP CONSTRAINT attachment_check;
ALTER TABLE attachment ADD CONSTRAINT attachment_check CHECK (num_nonnulls(partner_application_id, menu_item_id, box_id,
    complaint_id, no_show_case_id, payment_id, refund_id, payout_id, store_change_request_id, price_document_id, order_cancellation_request_id) = 1);
