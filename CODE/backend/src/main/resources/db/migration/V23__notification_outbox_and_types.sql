-- AC-01..08 (T8.2): Transactional notification outbox.
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN
 ('ORDER_PLACED','ORDER_CONFIRMED','ORDER_CANCELLED','ORDER_STATUS','PAYMENT','NO_SHOW','REFUND','COMPLAINT','STORE_REVIEW','SETTLEMENT'));
ALTER TABLE notification
 ADD COLUMN event_code varchar(64), ADD COLUMN title varchar(255), ADD COLUMN dedupe_key varchar(255),
 ADD COLUMN attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count>=0),
 ADD COLUMN next_attempt_at timestamptz, ADD COLUMN last_error text;
CREATE UNIQUE INDEX uq_notification_dedupe_key ON notification(dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_notification_outbox_due ON notification(status,next_attempt_at,created_at,id) WHERE status='PENDING';
CREATE INDEX idx_notification_recipient_created ON notification(recipient_id,created_at DESC,id);
