-- T5.2: Idempotency-Key of the order creation request, unique per customer (resending returns the same order).
ALTER TABLE customer_order ADD COLUMN idempotency_key varchar(64);
CREATE UNIQUE INDEX ux_customer_order_idempotency ON customer_order (customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
