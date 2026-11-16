-- T3.3: store profile (description, status reason) and review requests for D04-bound store data.
ALTER TABLE store ADD COLUMN description text;
ALTER TABLE store ADD COLUMN status_reason text;
ALTER TABLE store ADD COLUMN status_changed_at timestamptz;

-- Description used to live only in the partner application snapshot (T3.2 Q6); copy the latest revision's value.
UPDATE store s SET description = NULLIF(btrim(a.business_details::jsonb ->> 'businessDescription'), '')
FROM partner_application a
WHERE a.store_id = s.id
  AND a.revision = (SELECT max(x.revision) FROM partner_application x WHERE x.store_id = s.id);

CREATE TABLE store_change_request (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL REFERENCES store(id),
    requested_by_id uuid NOT NULL REFERENCES account(id),
    current_values text NOT NULL,
    proposed_values text NOT NULL,
    documents text NOT NULL,
    note text,
    status varchar(50) NOT NULL,
    requested_at timestamptz NOT NULL,
    resolved_by_id uuid REFERENCES account(id),
    resolved_at timestamptz,
    resolution_reason text,
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CHECK ((status = 'PENDING') = (resolved_at IS NULL))
);
CREATE INDEX ON store_change_request (store_id);
CREATE INDEX ON store_change_request (status);
-- At most one change request awaiting review per store (BR-T33-03).
CREATE UNIQUE INDEX ux_store_change_request_pending ON store_change_request (store_id) WHERE status = 'PENDING';

ALTER TABLE attachment ADD COLUMN store_change_request_id uuid REFERENCES store_change_request(id);
CREATE INDEX ON attachment (store_change_request_id);
ALTER TABLE attachment DROP CONSTRAINT attachment_check;
ALTER TABLE attachment ADD CONSTRAINT attachment_check CHECK (num_nonnulls(partner_application_id, menu_item_id, box_id,
    complaint_id, no_show_case_id, payment_id, refund_id, payout_id, store_change_request_id) = 1);
