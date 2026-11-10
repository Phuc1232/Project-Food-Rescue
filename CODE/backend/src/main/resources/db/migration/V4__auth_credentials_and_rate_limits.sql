-- AC-18,21..25: authentication credentials and distributed counters.
ALTER TABLE account ADD COLUMN email_verified_at timestamptz;
ALTER TABLE account DROP CONSTRAINT account_status_check;
ALTER TABLE account ADD CONSTRAINT account_status_check CHECK(status IN ('ACTIVE','LOCKED','DISABLED','PENDING_VERIFICATION'));
ALTER TABLE login_session ALTER COLUMN token_hash DROP NOT NULL;
UPDATE login_session SET revoked_at=now(), updated_at=now(), version=version+1 WHERE revoked_at IS NULL;
CREATE INDEX ix_session_account_state ON login_session(account_id,revoked_at,expires_at);
CREATE TABLE refresh_token (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, session_id uuid NOT NULL REFERENCES login_session(id),
 token_hash varchar(64) NOT NULL UNIQUE, expires_at timestamptz NOT NULL, consumed_at timestamptz,
 replaced_by_id uuid REFERENCES refresh_token(id), CHECK(expires_at > created_at)
);
CREATE UNIQUE INDEX ux_refresh_current ON refresh_token(session_id) WHERE consumed_at IS NULL;
CREATE INDEX ix_refresh_expiry ON refresh_token(expires_at);
CREATE TABLE account_action_token (
 id uuid PRIMARY KEY, created_at timestamptz NOT NULL, account_id uuid NOT NULL REFERENCES account(id),
 purpose varchar(32) NOT NULL CHECK(purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
 token_hash varchar(64) NOT NULL UNIQUE, expires_at timestamptz NOT NULL, consumed_at timestamptz,
 CHECK(expires_at > created_at)
);
CREATE UNIQUE INDEX ux_action_current ON account_action_token(account_id,purpose) WHERE consumed_at IS NULL;
CREATE INDEX ix_action_expiry ON account_action_token(expires_at);
CREATE TABLE auth_rate_limit (
 policy varchar(64) NOT NULL, key_hash varchar(64) NOT NULL, window_start timestamptz NOT NULL,
 count integer NOT NULL CHECK(count >= 0), expires_at timestamptz NOT NULL,
 PRIMARY KEY(policy,key_hash,window_start), CHECK(expires_at > window_start)
);
CREATE INDEX ix_rate_expiry ON auth_rate_limit(expires_at);
