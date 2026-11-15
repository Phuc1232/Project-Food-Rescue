# Business Definition: T4.5 — Chính sách tài chính theo phiên bản

**Status:** Gate A tự chốt bởi agent 2026-11-15 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t45](../roadmap-3-9.md) · **Nhánh:** `feature/t4-5-financial-policy` (từ `feature/t4-4-box-packing` e76dd84)
**Tài liệu nguồn:** UC-84 (hoa hồng), UC-85 (giữ lại no-show), UC-86 (hủy, hoàn), UC-87 (chính sách no-show), BR-33, BR-42, BR-43,
BR-46, BR-47, NFR (làm tròn tiền thống nhất, lưu chính sách đã áp dụng).

## 1. Bối cảnh

Chính sách tài chính (hoa hồng, tỷ lệ giữ lại no-show, giờ phản ánh 48h, điều khoản hủy, điều khoản hoàn) có phiên bản, không sửa phiên
bản cũ, có thời điểm hiệu lực (có thể tương lai, UC-84 A2). Đơn lưu phiên bản lúc đặt (BR-33, T5.2) nên phiên bản mới không hồi tố.

## 2. Khảo sát mã

- `financial_policy` (policy_version UNIQUE > 0, effective_from, effective_until, commission_rate & no_show_retention_rate ∈ [0,1]
  numeric(9,6), dispute_window_hours > 0, cancellation_terms, refund_terms, created_by_id NOT NULL) — **trigger V3 chặn UPDATE/DELETE**
  → không thể "đóng" phiên bản cũ bằng `effective_until`. **Chưa có seed, chưa có cột lý do.** Entity `finance/entity/FinancialPolicy`
  (@Immutable) và repository rỗng có sẵn.
- `customer_order.policy_id` + snapshot tỷ lệ/giờ/điều khoản (V1) — T5.2 dùng.
- Migration mới nhất V11 → **V12**. Baseline e76dd84: 158 tests, 157 pass, 1 skipped.

## 3. Quyền

ADMIN: xem mọi phiên bản, tạo phiên bản mới (BR-46). Mọi tài khoản đăng nhập (owner, khách): xem phiên bản đang áp dụng (BR-46 owner
xem; UC-85 bước 6 khách xem mức giữ lại trước checkout). Không phải ADMIN tạo → 403.

## 4. Luồng

- Xem (bước 1–2): danh sách phiên bản kèm trạng thái `ACTIVE` (đang áp dụng tại now), `SCHEDULED` (hiệu lực tương lai), `SUPERSEDED`;
  quy tắc làm tròn công bố.
- Tạo (bước 3–6): tỷ lệ hoa hồng, tỷ lệ giữ lại, giờ phản ánh, điều khoản hủy/hoàn, `effectiveFrom` (bỏ trống = ngay), lý do,
  `basedOnVersion` (phiên bản mới nhất admin đã xem — E2). Lưu phiên bản `max+1`.
- Chính sách tại thời điểm t (cho T5.2): phiên bản có `effective_from` lớn nhất ≤ t.

## 5. Quy tắc

- **BR-T45-01** Tỷ lệ ∈ [0, 1], tối đa 4 chữ số thập phân (vd 0.1250 = 12,5%). Ngoài miền → 400.
- **BR-T45-02** Giờ phản ánh = 48 (BR-43, quy tắc MVP); khác → 400.
- **BR-T45-03** Điều khoản hủy và hoàn 1–4000 ký tự; lý do 1–1000.
- **BR-T45-04** `effectiveFrom` ≥ now (không hồi tố) và **lớn hơn** `effective_from` của mọi phiên bản đã có → nếu không: 409 (hai phiên bản
  cùng hiệu lực / chèn vào giữa). Phiên bản tương lai đã tạo không hủy được (bảng bất biến); tạo phiên bản mới hơn để thay.
- **BR-T45-05** `basedOnVersion` khác phiên bản mới nhất → 409 (UC-84..87 E2). Tạo đồng thời → một thắng (khóa tư vấn + unique version).
- **BR-T45-06** Làm tròn: khoản tính theo tỷ lệ (hoa hồng, giữ lại) làm tròn HALF_UP về đồng (NFR). Công bố trong API.
- **BR-T45-07** Seed V12: phiên bản 1, hoa hồng 0,10, giữ lại no-show 0,50, 48h, điều khoản mặc định (tiếng Việt), hiệu lực từ thời điểm migrate,
  người tạo NULL (hệ thống) → V12 cho phép `created_by_id` NULL.
- **BR-T45-08** Audit `FINANCIAL_POLICY_CREATED` (phiên bản, tỷ lệ, hiệu lực, lý do).

## 6. Phạm vi

Trong: API admin (list, tạo), API xem hiện hành, `FinancialPolicyResolver.at(t)`, V12, OpenAPI, test. Ngoài: hoa hồng riêng từng cửa hàng
(tài liệu dòng 148 nhắc tới, card chỉ yêu cầu chính sách chung — ghi nhận, không làm); áp chính sách vào đơn (T5.2); tính tiền (T6–T8).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Sau migrate có phiên bản 1 ACTIVE (0,10 / 0,50 / 48h); `GET current` trả phiên bản đó cho owner/khách; ADMIN liệt kê. |
| AC-02 | ADMIN tạo phiên bản hiệu lực ngay → 201 version 2, ACTIVE; phiên bản 1 SUPERSEDED và không đổi dữ liệu; audit có lý do. |
| AC-03 | Tạo phiên bản tương lai → SCHEDULED; `current` vẫn phiên bản trước; resolver tại thời điểm sau hiệu lực trả phiên bản mới. |
| AC-04 | Tỷ lệ < 0, > 1, > 4 chữ số lẻ; giờ ≠ 48; thiếu điều khoản/lý do → 400 field. |
| AC-05 | `effectiveFrom` trong quá khứ hoặc ≤ hiệu lực của phiên bản đã có → 409; `basedOnVersion` cũ → 409; hai tạo đồng thời cùng basedOn → một 201, một 409. |
| AC-06 | Không phải ADMIN tạo/list → 403; không token → 401; UPDATE/DELETE trực tiếp bảng bị DB từ chối. |
| AC-07 | Đơn đã lưu chính sách cũ không đổi khi có phiên bản mới (snapshot customer_order giữ nguyên). OpenAPI có endpoint. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-84..87 bước 1–2 (xem phiên bản, lịch sử) | AC-01 |
| UC-84..87 bước 3–5 (cấu hình, hiệu lực, lý do, người sửa) | AC-02, AC-04 |
| UC-84..87 A2 (hiệu lực tương lai) | AC-03 |
| UC-84..87 E1 (không hợp lệ, xung đột thời điểm, trái MVP) | AC-04, AC-05 |
| UC-84..87 E2 (thay đổi đồng thời) | AC-05 |
| UC-84..87 bước 6, BR-33 (không hồi tố) | AC-07 |
| UC-85 bước 6 (khách xem mức giữ lại) | AC-01 |
| BR-43 (48 giờ) | AC-04 |
| BR-46 (admin thiết lập, owner xem) | AC-01, AC-06 |
| Card: tỷ lệ ngoài [0,1] → 400; chồng hiệu lực → 409; đồng thời một thắng; seed mặc định | AC-01, AC-04, AC-05 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 Số mặc định: hoa hồng 10%, giữ lại 50% (tài liệu không nêu số; admin đổi được ngay bằng phiên bản 2).
- [x] Q2 Không dùng `effective_until` (bảng bất biến); hiệu lực = phiên bản có `effective_from` lớn nhất ≤ t.
- [x] Q3 V12: `financial_policy.reason text`, `created_by_id` cho phép NULL (seed hệ thống).
- [x] Q4 Đường dẫn: `GET/POST /api/v1/admin/financial-policies`, `GET /api/v1/financial-policies/current`.
