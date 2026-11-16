-- T6.2: numeric order code sent to the payment provider (payOS orderCode is an integer), stored QR payload, and at most one live request per order.
CREATE SEQUENCE payment_order_code_seq START WITH 100000;
ALTER TABLE payment_request ADD COLUMN provider_order_code bigint;
ALTER TABLE payment_request ADD COLUMN qr_code text;
CREATE UNIQUE INDEX ux_payment_request_provider_order_code ON payment_request (provider_order_code) WHERE provider_order_code IS NOT NULL;
CREATE UNIQUE INDEX ux_payment_request_live ON payment_request (order_id) WHERE status IN ('PENDING', 'READY');
CREATE INDEX ON payment_request (order_id);
