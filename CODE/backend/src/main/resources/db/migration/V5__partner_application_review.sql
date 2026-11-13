-- T3.2: partner application review outcomes and one-owner-one-store (roadmap D8).
ALTER TABLE partner_application DROP CONSTRAINT partner_application_status_check;
ALTER TABLE partner_application ADD CONSTRAINT partner_application_status_check
    CHECK (status IN ('SUBMITTED', 'NEEDS_INFO', 'APPROVED', 'REJECTED'));
-- At most one revision awaiting review per store.
CREATE UNIQUE INDEX ux_partner_application_pending ON partner_application (store_id) WHERE status = 'SUBMITTED';
-- An account owns at most one store that is not closed.
CREATE UNIQUE INDEX ux_store_open_owner ON store (owner_id) WHERE status <> 'CLOSED';
