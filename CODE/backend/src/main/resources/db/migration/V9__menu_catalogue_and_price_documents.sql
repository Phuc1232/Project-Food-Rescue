-- T4.1: food categories (reference data) and versioned price reference documents (UC-53, BR-08).
INSERT INTO food_category (id, created_at, version, updated_at, code, name, active) VALUES
    (gen_random_uuid(), now(), 0, now(), 'BAKERY', 'Bánh mì, bánh ngọt', true),
    (gen_random_uuid(), now(), 0, now(), 'COOKED_MEAL', 'Món ăn nấu sẵn', true),
    (gen_random_uuid(), now(), 0, now(), 'NOODLE_SOUP', 'Bún, phở, mì', true),
    (gen_random_uuid(), now(), 0, now(), 'RICE_DISH', 'Cơm', true),
    (gen_random_uuid(), now(), 0, now(), 'FRESH_PRODUCE', 'Rau củ quả tươi', true),
    (gen_random_uuid(), now(), 0, now(), 'DAIRY', 'Sữa và chế phẩm từ sữa', true),
    (gen_random_uuid(), now(), 0, now(), 'DESSERT', 'Tráng miệng', true),
    (gen_random_uuid(), now(), 0, now(), 'BEVERAGE', 'Đồ uống', true),
    (gen_random_uuid(), now(), 0, now(), 'MIXED', 'Hỗn hợp', true)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE price_reference_document (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    store_id uuid NOT NULL REFERENCES store(id),
    menu_item_id uuid REFERENCES menu_item(id),
    file_id uuid NOT NULL REFERENCES stored_file(id),
    description text NOT NULL,
    applies_from date NOT NULL,
    uploaded_by_id uuid NOT NULL REFERENCES account(id),
    uploaded_at timestamptz NOT NULL,
    replaced_by_id uuid REFERENCES price_reference_document(id),
    CHECK (replaced_by_id IS NULL OR replaced_by_id <> id)
);
CREATE INDEX ON price_reference_document (store_id);
CREATE INDEX ON price_reference_document (menu_item_id);

ALTER TABLE attachment ADD COLUMN price_document_id uuid REFERENCES price_reference_document(id);
CREATE INDEX ON attachment (price_document_id);
ALTER TABLE attachment DROP CONSTRAINT attachment_check;
ALTER TABLE attachment ADD CONSTRAINT attachment_check CHECK (num_nonnulls(partner_application_id, menu_item_id, box_id,
    complaint_id, no_show_case_id, payment_id, refund_id, payout_id, store_change_request_id, price_document_id) = 1);
