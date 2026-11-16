-- T6.5 (roadmap D3): manual refunds are recorded by an administrator after the bank transfer: who, when it was transferred, and a note.
ALTER TABLE refund ADD COLUMN recorded_by_id uuid REFERENCES account(id);
ALTER TABLE refund ADD COLUMN transferred_at timestamptz;
ALTER TABLE refund ADD COLUMN note text;
