-- T3.4: staff invitations (UC-44 A1), staff setup token (D01) and the staff permission catalogue (BR-03).
ALTER TABLE staff_assignment DROP CONSTRAINT staff_assignment_status_check;
ALTER TABLE staff_assignment ADD CONSTRAINT staff_assignment_status_check
    CHECK (status IN ('INVITED', 'ACTIVE', 'DECLINED', 'REVOKED'));

ALTER TABLE account_action_token DROP CONSTRAINT account_action_token_purpose_check;
ALTER TABLE account_action_token ADD CONSTRAINT account_action_token_purpose_check
    CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'SET_PASSWORD'));

-- Staff-grantable permissions only. Owner-only codes (STORE_PROFILE_MANAGE, STAFF_MANAGE) and price, commission,
-- policy or admin rights are deliberately absent, so they can never be granted to staff.
INSERT INTO permission (id, created_at, version, updated_at, code, description) VALUES
    (gen_random_uuid(), now(), 0, now(), 'MENU_VIEW', 'View the internal menu (read only)'),
    (gen_random_uuid(), now(), 0, now(), 'BOX_PACK', 'Pack physical boxes from templates'),
    (gen_random_uuid(), now(), 0, now(), 'BOX_MANAGE', 'Inspect boxes, mark boxes unfit, recheck no-show boxes'),
    (gen_random_uuid(), now(), 0, now(), 'PICKUP_HANDLE', 'Verify pickup codes and hand over orders'),
    (gen_random_uuid(), now(), 0, now(), 'ORDER_VIEW', 'View store orders'),
    (gen_random_uuid(), now(), 0, now(), 'NO_SHOW_CONFIRM', 'Confirm a customer no-show');
