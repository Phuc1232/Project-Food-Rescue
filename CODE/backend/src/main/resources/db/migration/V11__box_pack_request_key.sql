-- T4.4: idempotency key of the packing request (UC-26 E3): resending the same request returns the same box.
ALTER TABLE physical_box ADD COLUMN pack_request_key uuid;
CREATE UNIQUE INDEX ux_physical_box_pack_request_key ON physical_box (pack_request_key) WHERE pack_request_key IS NOT NULL;
