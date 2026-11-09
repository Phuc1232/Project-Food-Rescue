-- Initial persistence schema. Money is VND, rates are fractions in [0,1].

CREATE TABLE account (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    login_identifier varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(255) NOT NULL,
    status varchar(50) NOT NULL,
    UNIQUE (login_identifier),
    CHECK (login_identifier = lower(trim(login_identifier)) AND length(login_identifier) > 0),
    CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE TABLE account_role (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    account_id uuid NOT NULL,
    role varchar(50) NOT NULL,
    UNIQUE (account_id, role),
    CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE login_session (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    account_id uuid NOT NULL,
    token_hash varchar(255) NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    UNIQUE (token_hash)
);

CREATE TABLE permission (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    code varchar(255) NOT NULL,
    description text NOT NULL,
    UNIQUE (code)
);

CREATE TABLE store (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    owner_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    address text NOT NULL,
    latitude numeric(10,7) NOT NULL,
    longitude numeric(10,7) NOT NULL,
    timezone varchar(255) NOT NULL,
    status varchar(50) NOT NULL,
    CHECK (latitude BETWEEN -90 AND 90),
    CHECK (longitude BETWEEN -180 AND 180),
    CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE TABLE staff_assignment (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    account_id uuid NOT NULL,
    store_id uuid NOT NULL,
    status varchar(50) NOT NULL,
    assigned_at timestamptz NOT NULL,
    revoked_at timestamptz,
    UNIQUE (store_id, account_id),
    CHECK (status IN ('ACTIVE', 'REVOKED')),
    UNIQUE (id, store_id)
);

CREATE TABLE staff_permission (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    assignment_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    granted_by_id uuid NOT NULL,
    granted_at timestamptz NOT NULL,
    UNIQUE (assignment_id, permission_id)
);

CREATE TABLE partner_application (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    applicant_id uuid NOT NULL,
    revision integer NOT NULL,
    business_details text NOT NULL,
    status varchar(50) NOT NULL,
    reviewer_id uuid,
    reviewed_at timestamptz,
    review_reason text,
    submitted_at timestamptz NOT NULL,
    UNIQUE (store_id, revision),
    CHECK (revision > 0),
    CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    UNIQUE (id, store_id)
);

CREATE TABLE store_opening_hour (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    day_of_week integer NOT NULL,
    shift_number integer NOT NULL,
    opens_at time NOT NULL,
    closes_at time NOT NULL,
    closes_next_day boolean NOT NULL,
    UNIQUE (store_id, day_of_week, shift_number),
    CHECK (day_of_week BETWEEN 1 AND 7),
    CHECK (shift_number > 0),
    CHECK (closes_next_day OR closes_at > opens_at),
    UNIQUE (id, store_id)
);

CREATE TABLE stored_file (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    storage_key varchar(255) NOT NULL,
    original_name varchar(255) NOT NULL,
    mime_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum varchar(255) NOT NULL,
    uploaded_by_id uuid NOT NULL,
    UNIQUE (storage_key),
    CHECK (size_bytes >= 0)
);

CREATE TABLE audit_log (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    actor_id uuid,
    store_id uuid,
    entity_type varchar(255) NOT NULL,
    entity_id uuid NOT NULL,
    action varchar(255) NOT NULL,
    before_state text,
    after_state text,
    correlation_id varchar(255),
    occurred_at timestamptz NOT NULL,
    UNIQUE (id, store_id)
);

CREATE TABLE food_category (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    code varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    active boolean NOT NULL,
    UNIQUE (code)
);

CREATE TABLE menu_item (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    category_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    listed_price numeric(19,2) NOT NULL,
    ingredients text NOT NULL,
    safety_warnings text NOT NULL,
    status varchar(50) NOT NULL,
    CHECK (listed_price > 0),
    CHECK (listed_price = trunc(listed_price)),
    CHECK (status IN ('ACTIVE', 'DISCONTINUED')),
    UNIQUE (id, store_id)
);

CREATE TABLE menu_price_history (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    menu_item_id uuid NOT NULL,
    previous_price numeric(19,2) NOT NULL,
    new_price numeric(19,2) NOT NULL,
    changed_by_id uuid NOT NULL,
    changed_at timestamptz NOT NULL,
    reason text NOT NULL,
    CHECK (previous_price > 0 AND new_price > 0),
    CHECK (previous_price = trunc(previous_price)),
    CHECK (new_price = trunc(new_price))
);

CREATE TABLE box_template (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    category_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    description text NOT NULL,
    safety_warnings text NOT NULL,
    default_selling_price numeric(19,2) NOT NULL,
    default_minimum_value numeric(19,2) NOT NULL,
    cover_file_id uuid,
    active boolean NOT NULL,
    CHECK (default_selling_price > 0 AND default_minimum_value > 0),
    CHECK (default_selling_price = trunc(default_selling_price)),
    CHECK (default_minimum_value = trunc(default_minimum_value)),
    UNIQUE (id, store_id)
);

CREATE TABLE sale_batch (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    template_id uuid NOT NULL,
    sale_date date NOT NULL,
    selling_price numeric(19,2) NOT NULL,
    minimum_value numeric(19,2) NOT NULL,
    order_deadline timestamptz NOT NULL,
    pickup_starts_at timestamptz NOT NULL,
    pickup_ends_at timestamptz NOT NULL,
    safety_warnings text NOT NULL,
    status varchar(50) NOT NULL,
    CHECK (selling_price > 0 AND minimum_value > 0),
    CHECK (order_deadline <= pickup_starts_at AND pickup_starts_at < pickup_ends_at),
    CHECK (selling_price = trunc(selling_price)),
    CHECK (minimum_value = trunc(minimum_value)),
    CHECK (status IN ('DRAFT', 'OPEN', 'PAUSED', 'ENDED', 'BLOCKED')),
    UNIQUE (id, store_id)
);

CREATE TABLE physical_box (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    current_batch_id uuid NOT NULL,
    box_code varchar(255) NOT NULL,
    packed_by_id uuid NOT NULL,
    packed_at timestamptz NOT NULL,
    total_listed_value numeric(19,2) NOT NULL,
    safe_until timestamptz NOT NULL,
    storage_instructions text NOT NULL,
    safety_warnings text NOT NULL,
    status varchar(50) NOT NULL,
    UNIQUE (box_code),
    CHECK (total_listed_value > 0),
    CHECK (safe_until > packed_at),
    CHECK (total_listed_value = trunc(total_listed_value)),
    CHECK (status IN ('AVAILABLE', 'RESERVED', 'ALLOCATED', 'DELIVERED', 'BLOCKED', 'AWAITING_INSPECTION')),
    UNIQUE (id, store_id)
);

CREATE TABLE box_item (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    box_id uuid NOT NULL,
    menu_item_id uuid NOT NULL,
    quantity integer NOT NULL,
    item_name_snapshot varchar(255) NOT NULL,
    unit_price_snapshot numeric(19,2) NOT NULL,
    safety_warnings_snapshot text NOT NULL,
    expires_at timestamptz NOT NULL,
    UNIQUE (box_id, menu_item_id),
    CHECK (quantity > 0 AND unit_price_snapshot > 0),
    CHECK (unit_price_snapshot = trunc(unit_price_snapshot)),
    UNIQUE (id, store_id)
);

CREATE TABLE box_inspection (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    box_id uuid NOT NULL,
    inspector_id uuid NOT NULL,
    inspected_at timestamptz NOT NULL,
    result varchar(50) NOT NULL,
    safe_until timestamptz NOT NULL,
    notes text NOT NULL,
    CHECK (result IN ('PASSED', 'FAILED'))
);

CREATE TABLE financial_policy (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    policy_version integer NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_until timestamptz,
    commission_rate numeric(9,6) NOT NULL,
    no_show_retention_rate numeric(9,6) NOT NULL,
    dispute_window_hours integer NOT NULL,
    cancellation_terms text NOT NULL,
    refund_terms text NOT NULL,
    created_by_id uuid NOT NULL,
    UNIQUE (policy_version),
    CHECK (policy_version > 0),
    CHECK (effective_until IS NULL OR effective_until > effective_from),
    CHECK (dispute_window_hours > 0),
    CHECK (commission_rate BETWEEN 0 AND 1),
    CHECK (no_show_retention_rate BETWEEN 0 AND 1)
);

CREATE TABLE customer_order (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    customer_id uuid NOT NULL,
    store_id uuid NOT NULL,
    sale_batch_id uuid NOT NULL,
    order_code varchar(255) NOT NULL,
    quantity integer NOT NULL,
    status varchar(50) NOT NULL,
    hold_expires_at timestamptz NOT NULL,
    unit_price numeric(19,2) NOT NULL,
    total_amount numeric(19,2) NOT NULL,
    minimum_value numeric(19,2) NOT NULL,
    currency varchar(3) NOT NULL,
    safety_warnings_snapshot text NOT NULL,
    pickup_address_snapshot text NOT NULL,
    pickup_starts_at timestamptz NOT NULL,
    pickup_ends_at timestamptz NOT NULL,
    policy_id uuid NOT NULL,
    commission_rate numeric(9,6) NOT NULL,
    no_show_retention_rate numeric(9,6) NOT NULL,
    dispute_window_hours integer NOT NULL,
    cancellation_terms_snapshot text NOT NULL,
    refund_terms_snapshot text NOT NULL,
    accepted_at timestamptz NOT NULL,
    pickup_code_hash varchar(255),
    completed_at timestamptz,
    UNIQUE (order_code),
    UNIQUE (pickup_code_hash),
    CHECK (quantity > 0 AND unit_price > 0 AND total_amount = unit_price * quantity),
    CHECK (minimum_value > 0),
    CHECK (hold_expires_at > accepted_at),
    CHECK (pickup_starts_at < pickup_ends_at),
    CHECK (dispute_window_hours > 0),
    CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'HANDOVER_IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'PENDING_NO_SHOW', 'NO_SHOW')),
    CHECK (unit_price = trunc(unit_price)),
    CHECK (total_amount = trunc(total_amount)),
    CHECK (minimum_value = trunc(minimum_value)),
    CHECK (currency = 'VND'),
    CHECK (commission_rate BETWEEN 0 AND 1),
    CHECK (no_show_retention_rate BETWEEN 0 AND 1),
    UNIQUE (id, store_id),
    UNIQUE (id, store_id, sale_batch_id)
);

CREATE TABLE box_allocation (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    order_id uuid NOT NULL,
    box_id uuid NOT NULL,
    sale_batch_at_allocation_id uuid NOT NULL,
    status varchar(50) NOT NULL,
    reserved_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    confirmed_at timestamptz,
    released_at timestamptz,
    release_reason text,
    CHECK (expires_at > reserved_at),
    CHECK ((status = 'RELEASED') = (released_at IS NOT NULL)),
    CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'DELIVERED')),
    UNIQUE (id, store_id)
);

CREATE TABLE pickup_session (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    order_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    status varchar(50) NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    idempotency_key varchar(255) NOT NULL,
    UNIQUE (idempotency_key),
    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CHECK (status IN ('OPEN', 'RECONCILING', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE pickup_incident (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    order_id uuid NOT NULL,
    session_id uuid,
    actor_id uuid NOT NULL,
    reason text NOT NULL,
    customer_arrived boolean NOT NULL,
    occurred_at timestamptz NOT NULL,
    resolution text
);

CREATE TABLE notification (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    recipient_id uuid NOT NULL,
    order_id uuid,
    type varchar(50) NOT NULL,
    payload text NOT NULL,
    status varchar(50) NOT NULL,
    sent_at timestamptz,
    delivered_at timestamptz,
    read_at timestamptz,
    CHECK (type IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED', 'NO_SHOW', 'REFUND', 'COMPLAINT', 'SETTLEMENT')),
    CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED'))
);

CREATE TABLE no_show_case (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    order_id uuid NOT NULL,
    verified_by_id uuid NOT NULL,
    verified_at timestamptz NOT NULL,
    evidence text NOT NULL,
    notification_id uuid,
    notification_received_at timestamptz,
    dispute_deadline timestamptz,
    retained_amount numeric(19,2) NOT NULL,
    compensation_status varchar(50) NOT NULL,
    UNIQUE (order_id),
    UNIQUE (notification_id),
    CHECK (retained_amount >= 0),
    CHECK ((notification_received_at IS NULL) = (dispute_deadline IS NULL)),
    CHECK (dispute_deadline IS NULL OR dispute_deadline > notification_received_at),
    CHECK (retained_amount = trunc(retained_amount)),
    CHECK (compensation_status IN ('HELD', 'DISPUTED', 'ELIGIBLE', 'FORFEITED', 'SETTLED'))
);

CREATE TABLE complaint (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    type varchar(50) NOT NULL,
    description text NOT NULL,
    status varchar(50) NOT NULL,
    handled_by_id uuid,
    decision text,
    resolved_at timestamptz,
    CHECK (type IN ('FOOD_SAFETY', 'BOX_VALUE', 'PAYMENT', 'PICKUP', 'NO_SHOW', 'OTHER')),
    CHECK (status IN ('OPEN', 'INVESTIGATING', 'AWAITING_EVIDENCE', 'DECIDED', 'CLOSED')),
    UNIQUE (id, store_id)
);

CREATE TABLE payment_request (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    order_id uuid NOT NULL,
    provider varchar(255) NOT NULL,
    provider_request_id varchar(255),
    idempotency_key varchar(255) NOT NULL,
    expected_amount numeric(19,2) NOT NULL,
    currency varchar(3) NOT NULL,
    payment_url text,
    expires_at timestamptz NOT NULL,
    status varchar(50) NOT NULL,
    UNIQUE (idempotency_key),
    UNIQUE (provider, provider_request_id),
    CHECK (expected_amount > 0),
    CHECK (expected_amount = trunc(expected_amount)),
    CHECK (currency = 'VND'),
    CHECK (status IN ('PENDING', 'READY', 'EXPIRED', 'FAILED'))
);

CREATE TABLE payment (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    request_id uuid,
    order_id uuid,
    provider varchar(255) NOT NULL,
    provider_transaction_id varchar(255) NOT NULL,
    received_amount numeric(19,2) NOT NULL,
    currency varchar(3) NOT NULL,
    status varchar(50) NOT NULL,
    verified_at timestamptz,
    received_at timestamptz NOT NULL,
    UNIQUE (provider, provider_transaction_id),
    CHECK (received_amount > 0),
    CHECK (status <> 'VERIFIED' OR verified_at IS NOT NULL),
    CHECK (received_amount = trunc(received_amount)),
    CHECK (currency = 'VND'),
    CHECK (status IN ('RECEIVED', 'VERIFIED', 'REQUIRES_REVIEW', 'REJECTED'))
);

CREATE TABLE refund_request (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    order_id uuid NOT NULL,
    complaint_id uuid,
    reason text NOT NULL,
    requested_amount numeric(19,2) NOT NULL,
    approved_amount numeric(19,2),
    status varchar(50) NOT NULL,
    approved_by_id uuid,
    approved_at timestamptz,
    CHECK (requested_amount > 0),
    CHECK (approved_amount IS NULL OR (approved_amount > 0 AND approved_amount <= requested_amount)),
    CHECK (requested_amount = trunc(requested_amount)),
    CHECK (approved_amount = trunc(approved_amount)),
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'FULFILLED'))
);

CREATE TABLE refund (
    order_id uuid NOT NULL,
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    request_id uuid NOT NULL,
    original_payment_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    status varchar(50) NOT NULL,
    provider_refund_id varchar(255),
    idempotency_key varchar(255) NOT NULL,
    verified_at timestamptz,
    UNIQUE (idempotency_key),
    UNIQUE (provider_refund_id),
    CHECK (amount > 0),
    CHECK (status <> 'SUCCEEDED' OR verified_at IS NOT NULL),
    CHECK (amount = trunc(amount)),
    CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
);

CREATE TABLE store_payout_account (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    bank_code varchar(255) NOT NULL,
    account_number_ciphertext text NOT NULL,
    account_holder_name varchar(255) NOT NULL,
    account_version integer NOT NULL,
    verified_at timestamptz,
    effective_from timestamptz NOT NULL,
    effective_until timestamptz,
    UNIQUE (store_id, account_version),
    CHECK (account_version > 0),
    CHECK (effective_until IS NULL OR effective_until > effective_from),
    UNIQUE (id, store_id)
);

CREATE TABLE financial_event (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    order_id uuid NOT NULL,
    type varchar(50) NOT NULL,
    payment_id uuid,
    refund_id uuid,
    no_show_case_id uuid,
    complaint_id uuid,
    occurred_at timestamptz NOT NULL,
    business_key varchar(255) NOT NULL,
    UNIQUE (business_key),
    CHECK (type IN ('PAYMENT_RECEIVED', 'ORDER_COMPLETED', 'REFUND_SUCCEEDED', 'NO_SHOW_CONFIRMED', 'COMPLAINT_RESOLVED', 'ADJUSTMENT'))
);

CREATE TABLE ledger_entry (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    event_id uuid NOT NULL,
    store_id uuid NOT NULL,
    type varchar(50) NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency varchar(3) NOT NULL,
    original_entry_id uuid,
    occurred_at timestamptz NOT NULL,
    CHECK (type IN ('PAYMENT_RECEIPT', 'CUSTOMER_REFUND', 'PLATFORM_COMMISSION', 'STORE_PAYABLE', 'NO_SHOW_COMPENSATION', 'ADJUSTMENT')),
    CHECK (amount = trunc(amount)),
    CHECK (currency = 'VND'),
    UNIQUE (id, store_id)
);

CREATE TABLE settlement (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    period_starts_at timestamptz NOT NULL,
    period_ends_at timestamptz NOT NULL,
    status varchar(50) NOT NULL,
    total_amount numeric(19,2) NOT NULL,
    confirmed_by_id uuid,
    confirmed_at timestamptz,
    previous_settlement_id uuid,
    CHECK (period_ends_at > period_starts_at),
    CHECK (status IN ('DRAFT', 'CONFIRMED', 'PARTIALLY_PAID', 'PAID')),
    CHECK (total_amount = trunc(total_amount)),
    UNIQUE (id, store_id)
);

CREATE TABLE settlement_line (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    settlement_id uuid NOT NULL,
    ledger_entry_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    UNIQUE (ledger_entry_id),
    CHECK (amount = trunc(amount)),
    UNIQUE (id, store_id)
);

CREATE TABLE store_payout (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL,
    settlement_id uuid NOT NULL,
    payout_account_id uuid NOT NULL,
    recipient_snapshot text NOT NULL,
    amount numeric(19,2) NOT NULL,
    transfer_reference varchar(255) NOT NULL,
    confirmed_by_id uuid,
    confirmed_at timestamptz,
    status varchar(50) NOT NULL,
    UNIQUE (transfer_reference),
    CHECK (amount > 0),
    CHECK (status <> 'VERIFIED' OR (confirmed_at IS NOT NULL AND confirmed_by_id IS NOT NULL)),
    CHECK (amount = trunc(amount)),
    CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED')),
    UNIQUE (id, store_id)
);

CREATE TABLE attachment (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    file_id uuid NOT NULL,
    partner_application_id uuid,
    menu_item_id uuid,
    box_id uuid,
    complaint_id uuid,
    no_show_case_id uuid,
    payment_id uuid,
    refund_id uuid,
    payout_id uuid,
    CHECK (num_nonnulls(partner_application_id, menu_item_id, box_id, complaint_id, no_show_case_id, payment_id, refund_id, payout_id) = 1)
);
