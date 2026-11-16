-- T3.5: reason and time of the current admin-set account status (UC-70); full history stays in audit_log.
ALTER TABLE account ADD COLUMN status_reason text;
ALTER TABLE account ADD COLUMN status_changed_at timestamptz;
