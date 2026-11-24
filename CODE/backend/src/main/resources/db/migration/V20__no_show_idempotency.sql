-- AC-04..08 (T7.4): retry-safe no-show confirmation and immutable amount snapshot.
ALTER TABLE no_show_case ADD COLUMN idempotency_key varchar(255);
ALTER TABLE no_show_case ADD COLUMN request_fingerprint text;
ALTER TABLE no_show_case ADD COLUMN refund_amount numeric(19,2) NOT NULL DEFAULT 0
    CHECK (refund_amount >= 0 AND refund_amount = trunc(refund_amount));
CREATE UNIQUE INDEX uq_no_show_case_idempotency ON no_show_case(order_id,idempotency_key) WHERE idempotency_key IS NOT NULL;
