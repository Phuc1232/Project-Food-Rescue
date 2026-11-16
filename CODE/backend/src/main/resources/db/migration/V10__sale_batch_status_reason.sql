-- T4.3: reason for an admin/system set batch status (BLOCKED); history stays in audit_log.
ALTER TABLE sale_batch ADD COLUMN status_reason text;
UPDATE sale_batch SET status_reason = 'Store suspended' WHERE status = 'BLOCKED' AND status_reason IS NULL;
