-- T4.5: reason of a policy version, system-created seed version (no account), default MVP policy.
ALTER TABLE financial_policy ADD COLUMN reason text;
ALTER TABLE financial_policy ALTER COLUMN created_by_id DROP NOT NULL;

INSERT INTO financial_policy (id, created_at, policy_version, effective_from, effective_until, commission_rate, no_show_retention_rate,
                              dispute_window_hours, cancellation_terms, refund_terms, created_by_id, reason)
SELECT gen_random_uuid(), now(), 1, now(), NULL, 0.100000, 0.500000, 48,
       'Khách được hủy đơn đã xác nhận trước giờ bắt đầu nhận hàng và được hoàn toàn bộ số tiền đã chuyển. Cửa hàng hủy hoặc không bàn giao được do lỗi của cửa hàng: khách được hoàn toàn bộ.',
       'Khoản hoàn được trả về theo giao dịch chuyển khoản gốc, không vượt số tiền đã xác minh. No-show hợp lệ: cửa hàng được giữ lại phần theo tỷ lệ của phiên bản chính sách khách đã chấp nhận, phần còn lại hoàn cho khách sau khi hết 48 giờ phản ánh hoặc sau khi phản ánh được giải quyết.',
       NULL, 'Default MVP policy (seed)'
WHERE NOT EXISTS (SELECT 1 FROM financial_policy);
