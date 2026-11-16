# Business Definition: T5.2 — Tạo đơn và giữ hộp

**Status:** Gate A tự chốt bởi agent 2026-11-15 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t52](../roadmap-3-9.md) · **Nhánh:** `feature/t5-2-order-hold` (từ `feature/t5-1-search` 2bfd3d6)
**Tài liệu nguồn:** UC-13 bước 1–6, A1, A2, E1; BR-21, BR-22, BR-23, BR-27, BR-29, BR-33; roadmap D5 (giữ 10 phút, cấu hình được);
D02 của tài liệu (điều kiện khách chấp nhận); T3.3 `StoreSalesPolicy`; T4.3 `BatchAvailability`; T4.5 `FinancialPolicyResolver`.

## 1. Bối cảnh

Khách chọn số lượng hộp của một đợt, xem trước toàn bộ điều kiện (UC-13 bước 4), xác nhận → hệ thống kiểm tra lại, tạo đơn `PENDING_PAYMENT`
với snapshot đầy đủ (giá, giá trị cam kết, cảnh báo, nơi/giờ nhận, chính sách tài chính) và giữ đúng N hộp thực tế trong 10 phút. Không giữ
một phần (E1); một hộp không thuộc hai đơn (BR-27). Thanh toán là giai đoạn 6.

## 2. Khảo sát mã

- `customer_order` (snapshot đầy đủ; total = unit_price × quantity; hold_expires_at > accepted_at; trigger V3 chặn sửa điều khoản),
  `box_allocation` (HELD/CONFIRMED/RELEASED/DELIVERED; unique một allocation HELD/CONFIRMED mỗi hộp — `uq_box_active_allocation`),
  `physical_box.status` RESERVED. Entity có sẵn; **không có cột khóa idempotency trên đơn**.
- Khóa: đình chỉ cửa hàng (T3.3) khóa `store` FOR UPDATE; tạm dừng/kết thúc/chặn đợt (T4.3) khóa `sale_batch` FOR UPDATE.
- Migration mới nhất V12 → **V13**. Baseline 2bfd3d6: 170 tests, 169 pass, 1 skipped.

## 3. Quyền

Mọi tài khoản đăng nhập đặt được, trừ chủ của chính cửa hàng đó (409 — tránh tự mua/no-show giả). Không token → 401.

## 4. Luồng

1. Preview (UC-13 bước 1–4): `batchId`, `quantity` → kiểm tra đợt đang bán và đủ hộp; trả số lượng, đơn giá, tổng tiền, giá trị cam kết mỗi hộp,
   cảnh báo (như T5.1), địa chỉ, khung nhận, thời gian giữ, chính sách hiện hành (phiên bản, điều khoản hủy/hoàn, tỷ lệ và số tiền giữ lại
   no-show tính sẵn). Không tạo gì (A2).
2. Xác nhận (bước 5–6): header `Idempotency-Key` + `batchId`, `quantity`, `acceptedPolicyVersion`. Trong một transaction: khóa tài khoản khách
   (tuần tự hóa cùng khách, idempotency), khóa đọc `store` và `sale_batch` (FOR SHARE: đình chỉ/tạm dừng đồng thời phải chờ), kiểm tra lại,
   chọn N hộp khả dụng `FOR UPDATE SKIP LOCKED` (hạn an toàn sớm nhất trước) → đủ N mới ghi: đơn PENDING_PAYMENT + snapshot + N allocation HELD
   + hộp RESERVED; `hold_expires_at = now + 10 phút`.
3. Gửi lại cùng key (cùng khách) → trả đơn cũ (200), không giữ thêm.

## 5. Quy tắc

- **BR-T52-01** Số lượng 1..5 mỗi đơn.
- **BR-T52-02** Đặt được khi: store ACTIVE (409 `STORE_NOT_ACTIVE`), đợt OPEN (409 `BATCH_NOT_OPEN`), chưa quá hạn đặt (409 `ORDER_DEADLINE_PASSED`),
  đủ hộp (409 `NOT_ENOUGH_BOXES`, kèm số khả dụng), chính sách hiện hành = `acceptedPolicyVersion` (409 `POLICY_CHANGED` — khách xem lại, D02).
- **BR-T52-03** Tổng tiền = giá bán đợt × số lượng (nguyên đồng, BR-29); đơn lưu giá, giá trị tối thiểu, cảnh báo, địa chỉ, khung nhận, phiên bản
  chính sách + tỷ lệ + giờ phản ánh + điều khoản (BR-33). Snapshot không đổi khi giá/chính sách đổi sau đó.
- **BR-T52-04** Thời gian giữ cấu hình `orders.hold-minutes` (mặc định 10, D5).
- **BR-T52-05** `Idempotency-Key` bắt buộc (1–64 ký tự an toàn); duy nhất theo khách (V13). Cùng key khác `batchId`/`quantity` → 409 `IDEMPOTENCY_KEY_REUSED`.
- **BR-T52-06** Mã đơn `FR-yyMMdd-XXXXXX` duy nhất.
- **BR-T52-07** Audit `ORDER_CREATED` (store, đơn, số hộp) vào lịch sử cửa hàng.

## 6. Phạm vi

Trong: preview, tạo đơn + giữ hộp, xem đơn vừa tạo (`GET /api/v1/orders/{id}` của chính khách — tối thiểu; danh sách/lịch sử đầy đủ ở T5.3),
V13, OpenAPI, test (gồm tranh hộp cuối). Ngoài: hết hạn giữ (T5.3), thanh toán (T6.x), hủy (T5.3/T7.3).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Preview đợt đang bán → 200 với tổng tiền = giá × SL, cảnh báo, địa chỉ, khung nhận, chính sách (phiên bản, số tiền giữ lại no-show); không tạo đơn/không giữ hộp. |
| AC-02 | Xác nhận hợp lệ → 201 PENDING_PAYMENT, mã đơn, `holdExpiresAt` = now+10′, snapshot đầy đủ; đúng N allocation HELD, N hộp RESERVED; số khả dụng giảm N. |
| AC-03 | Không đủ hộp → 409 `NOT_ENOUGH_BOXES`, không giữ hộp nào, không tạo đơn. |
| AC-04 | Hai khách đồng thời tranh hộp cuối → một 201, một 409; hộp chỉ có một allocation. Nhiều khách đặt song song hết N hộp → tổng giữ = N, không hộp nào hai lần. |
| AC-05 | Gửi lại cùng `Idempotency-Key` → 200 cùng đơn, không giữ thêm; đồng thời cùng key → một đơn; cùng key khác SL → 409; thiếu key → 400. |
| AC-06 | Đổi giá đợt/menu/chính sách sau khi đặt → snapshot đơn giữ nguyên. Chính sách đổi giữa preview và xác nhận → 409 `POLICY_CHANGED`. |
| AC-07 | Đợt PAUSED/quá hạn/store SUSPENDED → 409 tương ứng; SL 0 hoặc > 5 → 400; chủ cửa hàng tự đặt → 409; không token → 401. |
| AC-08 | Khách xem đơn của mình; người khác → 404. OpenAPI có endpoint. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-13 bước 1–4, A1, A2 | AC-01 |
| UC-13 bước 5–6; D02; BR-22, BR-23, D5 | AC-02, AC-06 |
| UC-13 E1; BR-21 | AC-03, AC-07 |
| BR-27 (một hộp một đơn) | AC-04 |
| BR-29 (tổng tiền chốt khi xác nhận) | AC-02 |
| BR-33 (lưu chính sách) | AC-02, AC-06 |
| Card: idempotency, không giữ một phần, tranh hộp cuối, snapshot | AC-03..06 |
| T3.3: khóa cửa hàng chặn đơn mới | AC-07 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 Tối đa 5 hộp/đơn.
- [x] Q2 Chủ cửa hàng không đặt ở cửa hàng mình (staff vẫn được — họ là khách bình thường ở nơi khác; trong cửa hàng mình: cũng chặn).
- [x] Q3 `acceptedPolicyVersion` bắt buộc khi xác nhận (khách đã thấy điều kiện trong preview).
- [x] Q4 Đường dẫn: `POST /api/v1/orders/preview`, `POST /api/v1/orders` (header `Idempotency-Key`), `GET /api/v1/orders/{orderId}`.
