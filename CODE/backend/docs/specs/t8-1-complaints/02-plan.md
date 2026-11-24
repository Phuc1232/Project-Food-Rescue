# Technical Plan: T8.1 — Khiếu nại (Tiếp nhận, Bổ sung, Giải quyết & Đóng hồ sơ)

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A đã được người dùng duyệt `Q1`–`Q5` ngày 2026-11-24.  
**Status:** Hoàn tất triển khai và kiểm chứng (315 PASS / 0 FAIL / 1 SKIPPED)  
**Updated:** 2026-11-24  
**Scope:** T8.1 (`UC-23`, `UC-63`, `UC-64`, `UC-65`, `BR-43`, `BR-45`, `D06`, `SD-10`).  
**Phụ thuộc:** T7.5 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Quyết định đã duyệt tại Gate A và Baseline

1. **Các quyết định Gate A đã chốt (`Q1`–`Q5`):**
   - **Q1 (API endpoints):**
     - Khách hàng (`CUSTOMER`): `POST /api/v1/complaints`, `GET /api/v1/complaints`, `GET /api/v1/complaints/{complaintId}`, `POST /api/v1/complaints/{complaintId}/supplement`.
     - Admin (`ADMIN`): `GET /api/v1/admin/complaints` (`UC-63`), `GET /api/v1/admin/complaints/{complaintId}` (`UC-64`), `POST .../request-info` (`UC-65 A1`), `POST .../decide` (`UC-65`), `POST .../close` (`SD-10`).
     - Chưa mở API riêng cho Store Owner trong T8.1 (vẫn giữ quyền đọc bằng chứng của chủ cửa hàng qua `ComplaintAttachmentPolicy` từ T7.5).
   - **Q2 (Mở khóa hoàn bổ sung trên `Payment` gốc):** Thay `ux_refund_request_payment` (từ `V16`) thành partial unique index chỉ áp dụng khi `complaint_id IS NULL` (`ux_refund_request_payment_non_complaint`), cho phép khiếu nại tạo nghĩa vụ hoàn bổ sung (vd 30% còn lại của No-show lỗi cửa hàng `UC-65 A2`) lên cùng `Payment` gốc dưới kiểm tra khóa bi quan tổng hoàn ≤ `payment.receivedAmount`.
   - **Q3 (Migration `V22__complaint_workflow_and_resolution.sql`):** Mở rộng `complaint` với `complaint_code`, `within_dispute_window`, `requested_info_note`, `outcome`, `responsibility`, `approved_refund_amount`, `store_adjustment_amount`, `commission_adjustment_amount`, `closed_at`, `request_fingerprint`, `decision_key`, `decision_fingerprint` + index idempotency `(customer_id, idempotency_key)`. Tổng Hibernate entity giữ nguyên `43`; số migration tăng `21` → `22`.
   - **Q4 (Liên thông T6.5 & T7.4):** Quyết định có hoàn tiền (`> 0`) tạo `RefundRequest(PENDING)` (giữ nguyên quy trình D3 qua T6.5) và giữ `Complaint` ở `DECIDED`; khi `ManualRefundService.confirm` hoàn tất nghĩa vụ cuối cùng của khiếu nại (`FULFILLED`), tự động đóng `Complaint` sang `CLOSED`. `NoShowCompensationService.releaseEligible` giữ tiền khi có complaint ở `[OPEN, INVESTIGATING, AWAITING_EVIDENCE, DECIDED]`.
   - **Q5 (Ranh giới T8.1 ↔ T8.3):** T8.1 ghi 1 `FinancialEvent(COMPLAINT_RESOLVED)` và lưu snapshot trách nhiệm + số tiền điều chỉnh (`storeAdjustmentAmount`, `commissionAdjustmentAmount`) trên `Complaint`; T6.5 ghi `LedgerEntry(CUSTOMER_REFUND)` khi chuyển khoản; T8.3 đọc các bản ghi bất biến này để phát sinh bút toán `LedgerEntry(ADJUSTMENT)` gắn bút toán gốc.
2. **Baseline:**
   - `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`: **BUILD SUCCESSFUL** (`295 PASS, 0 FAIL, 1 SKIPPED` có sẵn; `21` migrations `V1`–`V21`; `43` entities; `108` OpenAPI paths / `26` tags).

---

## 2. Thiết kế chi tiết

### 2.1. Migration `V22__complaint_workflow_and_resolution.sql` & Entity `Complaint`

- Bổ sung cột lên bảng `complaint`:
  - `complaint_code varchar(64)` — backfill cho các dòng hiện có (`'CMP-' || upper(replace(left(id::text, 13), '-', ''))`) rồi `SET NOT NULL`, kèm `CREATE UNIQUE INDEX uq_complaint_code ON complaint(complaint_code);`.
  - `within_dispute_window boolean NOT NULL DEFAULT true`
  - `requested_info_note text`
  - `outcome varchar(32) CHECK (outcome IS NULL OR outcome IN ('APPROVED', 'PARTIAL', 'REJECTED'))`
  - `responsibility varchar(32) CHECK (responsibility IS NULL OR responsibility IN ('STORE', 'PLATFORM', 'CUSTOMER', 'SHARED', 'NONE'))`
  - `approved_refund_amount numeric(19,2) CHECK (approved_refund_amount IS NULL OR (approved_refund_amount >= 0 AND approved_refund_amount = trunc(approved_refund_amount)))`
  - `store_adjustment_amount numeric(19,2) CHECK (store_adjustment_amount IS NULL OR store_adjustment_amount = trunc(store_adjustment_amount))`
  - `commission_adjustment_amount numeric(19,2) CHECK (commission_adjustment_amount IS NULL OR commission_adjustment_amount = trunc(commission_adjustment_amount))`
  - `closed_at timestamptz`
  - `request_fingerprint text`
  - `decision_key varchar(255)`
  - `decision_fingerprint text`
  - `CREATE UNIQUE INDEX uq_complaint_customer_idempotency ON complaint(customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL AND source_box_id IS NULL;`
  - `CREATE UNIQUE INDEX uq_complaint_decision_key ON complaint(id, decision_key) WHERE decision_key IS NOT NULL;`
  - `CREATE INDEX idx_complaint_status_created ON complaint(status, created_at DESC);`
- Cập nhật unique index trên `refund_request`:
  - `DROP INDEX IF EXISTS ux_refund_request_payment;`
  - `CREATE UNIQUE INDEX ux_refund_request_payment_non_complaint ON refund_request (payment_id) WHERE payment_id IS NOT NULL AND complaint_id IS NULL;`
- Cập nhật entity [`Complaint`](../../src/main/kotlin/com/foodrescue/backend/complaint/entity/Complaint.kt) và tạo 2 enum [`ComplaintOutcome`](../../src/main/kotlin/com/foodrescue/backend/complaint/ComplaintOutcome.kt), [`ComplaintResponsibility`](../../src/main/kotlin/com/foodrescue/backend/complaint/ComplaintResponsibility.kt). Giữ tổng Hibernate entity là `43`.
- Tạo [`ComplaintCodeGenerator`](../../src/main/kotlin/com/foodrescue/backend/complaint/service/ComplaintCodeGenerator.kt) sinh mã `"CMP-$yyMMdd-XXXXXX"` theo cùng pattern với `OrderCodeGenerator`.

> **Lý do cập nhật tối thiểu 3 file của task trước theo `.agents/CLAUDE.md` §1:**
> 1. [`BoxUnfitProcessor.kt`](../../src/main/kotlin/com/foodrescue/backend/surprisebox/service/BoxUnfitProcessor.kt) (T7.5): cần truyền `complaintCode = complaintCodes.next()` khi tạo `Complaint` hậu giao để khớp `NOT NULL UNIQUE` mới trên `complaint_code`.
> 2. [`NoShowCompensationService.kt`](../../src/main/kotlin/com/foodrescue/backend/pickup/service/NoShowCompensationService.kt) (T7.4): mở rộng điều kiện chặn giải tỏa ở `releaseEligible` thêm trạng thái `'DECIDED'` (đề phòng khoản giữ chưa xử lý xong hoàn tiền).
> 3. [`ManualRefundService.kt`](../../src/main/kotlin/com/foodrescue/backend/payment/service/ManualRefundService.kt) (T6.5): tại `confirm`, nếu `RefundRequest.complaint != null` và mọi nghĩa vụ hoàn của hồ sơ đó đã đạt `FULFILLED`, tự động chuyển `Complaint` từ `DECIDED` → `CLOSED` (Q4).

### 2.2. Luồng Khách hàng (`ComplaintService` & `ComplaintController` — `UC-23`, `BR-43`, `D06`)

- **Thứ tự khóa bi quan:** `CustomerOrder (findForUpdate)` → `NoShowCase (findForUpdate)` → `StoredFile (sorted id ASC, PESSIMISTIC_WRITE)`.
- **`submit(customerId, request, idempotencyKey): ComplaintSubmitResult`:**
  1. Validate `Idempotency-Key` (nếu có: `^[A-Za-z0-9_-]{1,255}$`, else `400 VALIDATION_FAILED`).
  2. Tính `fingerprint = mapper.writeValueAsString(listOf("COMPLAINT_SUBMIT", request.orderId, request.type, request.description!!.trim(), request.attachmentIds.distinct().sorted()))`.
  3. Kiểm tra replay theo `(customerId, idempotencyKey)` (`sourceBox == null`): nếu tìm thấy, so sánh `fingerprint` (sai → `409 IDEMPOTENCY_KEY_REUSED`), khớp trả `ComplaintSubmitResult(customerView(existing), created = false)` (`200 OK` — `UC-23 E2`).
  4. Khóa `CustomerOrder` (`orders.findForUpdate(request.orderId!!)`), kiểm tra `order.customer.id == customerId` (else `404 NOT_FOUND`).
  5. Kiểm tra điều kiện loại khiếu nại và trạng thái đơn:
     - Chặn nếu đơn đang có khiếu nại hoạt động (`status in [OPEN, INVESTIGATING, AWAITING_EVIDENCE, DECIDED]`) cùng `type` (`409 COMPLAINT_ALREADY_OPEN`).
     - Nếu `type == ComplaintType.NO_SHOW`:
       - Phải có `order.status == OrderStatus.NO_SHOW` và tồn tại `NoShowCase` (else `409 NO_SHOW_NOT_FOUND`).
       - Khóa `NoShowCase` (`cases.findForUpdate(noShowCase.id!!)`).
       - Kiểm tra 48h (`BR-43`, `D06`): `val timely = noShowCase.disputeDeadline == null || !now.isAfter(noShowCase.disputeDeadline)`.
       - Nếu `timely && noShowCase.compensationStatus == CompensationStatus.HELD` → chuyển `noShowCase.compensationStatus = CompensationStatus.DISPUTED`.
       - Nếu `!timely` → gán `withinDisputeWindow = false`, giữ nguyên `noShowCase.compensationStatus` (`UC-23 A2`).
     - Nếu `type != ComplaintType.NO_SHOW`:
       - Với `FOOD_SAFETY`, `BOX_VALUE`, `PICKUP`: cho phép khi `order.status in [COMPLETED, CANCELLED, HANDOVER_IN_PROGRESS, NO_SHOW]` (hoặc đã có bàn giao/hộp thực tế; từ chối `409 INVALID_ORDER_STATUS` nếu đơn mới `PENDING_PAYMENT` hoặc `EXPIRED` chưa nhận hàng).
       - Với `PAYMENT`, `OTHER`: cho phép mọi đơn thuộc khách đã có thanh toán/sự cố (từ chối `PENDING_PAYMENT` chưa phát sinh `Payment` nếu loại là `FOOD_SAFETY`/`BOX_VALUE`/`PICKUP`).
  6. Validate & khóa `StoredFile` theo `id ASC` (`uploadedBy.id == customerId && attachments.findAllByFileId(id).isEmpty()`, else `400 VALIDATION_FAILED`).
  7. Tạo `Complaint` với `complaintCode = codes.next()`, `status = ComplaintStatus.OPEN`, `withinDisputeWindow = timely`, `idempotencyKey = key`, `requestFingerprint = fingerprint`, lưu `Attachment(file, complaint = saved)`, ghi `StoreHistoryRecorder` (`"COMPLAINT"`, `saved.id!!`, `"COMPLAINT_SUBMITTED"`). Trả `201 CREATED`.
- **`myList(customerId, status, orderId, page, size)`** (`@Transactional(readOnly = true)`): dùng `PageQuery` + `PageResponse<CustomerComplaintSummary>`.
- **`myDetail(customerId, complaintId)`** (`@Transactional(readOnly = true)`): kiểm tra `complaint.customer.id == customerId` (else `404`), trả `CustomerComplaintDetail` (thông tin hồ sơ, đính kèm, yêu cầu bổ sung, trạng thái hoàn tiền & khoản giữ nếu có).
- **`supplement(customerId, complaintId, request)`** (`@Transactional`):
  - Khóa `CustomerOrder` rồi `Complaint`, kiểm tra `customer.id == customerId` (`404`), `complaint.status == ComplaintStatus.AWAITING_EVIDENCE` (else `409 INVALID_COMPLAINT_STATUS`).
  - Khóa & gắn thêm `attachmentIds` mới của `customerId`, nối nội dung bổ sung vào `complaint.description` (có dấu thời gian UTC), chuyển `complaint.status = ComplaintStatus.INVESTIGATING`, ghi `AuditLog("COMPLAINT_SUPPLEMENTED")`.

### 2.3. Luồng Quản trị viên (`AdminComplaintService` & `AdminComplaintController` — `UC-63`, `UC-64`, `UC-65`, `BR-45`, `SD-10`)

- `@PreAuthorize("hasRole('ADMIN')")`.
- **`list(status, type, storeId, orderId, from, to, page, size)` (`UC-63`):**
  - Truy vấn động an toàn qua JDBC/JPA Criteria/Specification theo bộ lọc tùy chọn, sắp xếp mặc định `createdAt DESC`.
  - Trả `PageResponse<AdminComplaintSummary>` gồm `id`, `complaintCode`, `orderId`, `orderCode`, `storeId`, `storeName`, `customerDisplayName`, `customerEmail`, `type`, `status`, `withinDisputeWindow`, `verifiedPaid`, `heldCompensationAmount`, `approvedRefundAmount`, `outcome`, `responsibility`, `createdAt`, `resolvedAt`, `closedAt`.
- **`detail(complaintId)` (`UC-64`):**
  - Tải toàn bộ thông tin đối chiếu cho `order`:
    - `complaint`: toàn bộ trường trạng thái, mã, `sourceBoxId`, `requestedInfoNote`, kết quả giải quyết, `attachmentFileIds`.
    - `order`: `OrderViews.view(order)` + `OrderMoneyReader.money(order)`.
    - `boxes`: danh sách `BoxAllocation` + thông tin `PhysicalBox` + lịch sử `BoxInspection`.
    - `payments`: từng `Payment` kèm `refundedAmount`, `openObligationAmount`, `refundableAmount = receivedAmount - refundedAmount - openObligationAmount`.
    - `refundRequests`: toàn bộ `RefundRequestView` (qua `RefundViews.view(r, true)`).
    - `handover`: danh sách `PickupSessionView` và `PickupIncidentView`.
    - `noShowCase`: `NoShowCaseSummaryView` (nếu có).
    - `history`: các dòng `audit_log` liên quan (`(entity_type = 'COMPLAINT' AND entity_id = :complaintId) OR (entity_type = 'CUSTOMER_ORDER' AND entity_id = :orderId)`).
- **`requestInfo(adminId, complaintId, request)` (`UC-65 A1`, `SD-10` #17–22):**
  - Khóa `CustomerOrder` → `Complaint`, kiểm tra `complaint.status in [OPEN, INVESTIGATING]` (else `409 INVALID_COMPLAINT_STATUS`).
  - Đặt `complaint.status = ComplaintStatus.AWAITING_EVIDENCE`, `requestedInfoNote = request.note!!.trim()`, `handledBy = admin`.
  - Ghi `AuditLog("COMPLAINT_INFO_REQUESTED")` và trả `AdminComplaintDetail`.
- **`decide(adminId, complaintId, request, idempotencyKey)` (`UC-65` bước 4–7, `A2..A4`, `E1`, `BR-45`, `SD-10` #23–37):**
  - **Thứ tự khóa:** giải `orderId` → `CustomerOrder (findForUpdate)` → `Complaint (findForUpdate)` → `NoShowCase (findForUpdate, nếu có)` → toàn bộ `Payment` thuộc `orderId` (`id ASC`, `findForUpdate`).
  - Validate `Idempotency-Key` & `decisionFingerprint` (`[outcome, responsibility, reason.trim(), normalizedAllocations, storeAdjustmentAmount, commissionAdjustmentAmount]`). Nếu `decisionKey == key`: khác payload → `409 IDEMPOTENCY_KEY_REUSED`, khớp → trả `200 OK` `detail(complaint)` (không lặp lại bất kỳ thay đổi nào).
  - Kiểm tra `complaint.status !in [DECIDED, CLOSED]` (else `409 COMPLAINT_ALREADY_DECIDED` — quyết định đã chốt bất biến).
  - **Validate logic quyết định (`UC-65 E1`):**
    - `outcome == REJECTED`: `refundAllocations` phải rỗng, `storeAdjustmentAmount` & `commissionAdjustmentAmount` bằng `0` (hoặc `null` mặc định `0`), ngược lại `400 VALIDATION_FAILED`.
    - `outcome in [APPROVED, PARTIAL]`:
      - Validate từng `PaymentRefundAllocation(paymentId, amount)`: `amount > 0`, nguyên đồng (`scale <= 0`), không trùng `paymentId` trong danh sách; `payment` phải thuộc `order.id` và `payment.status == PaymentStatus.VERIFIED`.
      - Với mỗi `payment`: tính `refundedSucceeded` (`SUM(refund.amount WHERE SUCCEEDED)`) và `openObligations` (`SUM(coalesce(approvedAmount, requestedAmount) WHERE PENDING/APPROVED)`). Tính `refundable = payment.receivedAmount - refundedSucceeded - openObligations`.
      - Nếu `alloc.amount > refundable` → `409 REFUND_EXCEEDS_REFUNDABLE` (`UC-65 E1`: *"số hoàn vượt khoản còn có thể hoàn"*).
      - Kiểm tra tổng `totalApprovedRefund = refundAllocations.sumOf { it.amount }`: nếu `totalApprovedRefund == 0` với `PARTIAL` (hoặc thiếu điều chỉnh giải thích) yêu cầu rõ ràng, với `APPROVED`/`PARTIAL` hỗ trợ `0` nếu chỉ điều chỉnh trách nhiệm hoặc `> 0` qua `refundAllocations` (nếu `totalApprovedRefund > 0` bắt buộc phân bổ theo `Payment`).
      - Validate `storeAdjustmentAmount` và `commissionAdjustmentAmount` (nếu truyền): phải là số nguyên đồng `VND`.
  - **Xử lý `NoShowCase` (nếu đơn có `NoShowCase`):**
    - Khi `outcome != REJECTED && responsibility in [STORE, SHARED]` (`UC-65 A2`):
      - Nếu trước đó đã có `NO_SHOW_REFUND` chưa `REJECTED`, tổng tất cả nghĩa vụ hoàn đang hiệu lực (`open + succeeded`) + `totalApprovedRefund` phải ≤ `verifiedPaid` (đã được kiểm tra tự động qua `refundable` từng payment!).
      - Đặt `noShowCase.compensationStatus = CompensationStatus.FORFEITED` (hủy khoản bù cửa hàng).
    - Khi `outcome == REJECTED` (`UC-65 A3`):
      - Nếu `noShowCase.compensationStatus == CompensationStatus.DISPUTED`: kiểm tra không còn `Complaint` nào khác trên `orderId` đang ở `[OPEN, INVESTIGATING, AWAITING_EVIDENCE, DECIDED]`:
        - Nếu `noShowCase.notificationReceivedAt != null && noShowCase.disputeDeadline != null && !now.isBefore(noShowCase.disputeDeadline)` → đặt `noShowCase.compensationStatus = CompensationStatus.ELIGIBLE`.
        - Ngược lại → trả về `noShowCase.compensationStatus = CompensationStatus.HELD`.
  - **Tạo nghĩa vụ hoàn T6.5 & Sự kiện tài chính (`SD-10` #26–35):**
    - Nếu `totalApprovedRefund > 0`: tạo `RefundRequest(order = order, complaint = complaint, reason = "COMPLAINT_${outcome.name}: ${reason}", requestedAmount = alloc.amount.setScale(0), status = RefundRequestStatus.PENDING, payment = payment, reasonCode = "COMPLAINT_${outcome.name}")` cho mỗi allocation > 0.
    - Ghi `FinancialEvent(order = order, type = FinancialEventType.COMPLAINT_RESOLVED, complaint = complaint, occurredAt = now, businessKey = "COMPLAINT_RESOLVED:${complaint.id}")`.
    - Cập nhật `Complaint`:
      - `handledBy = admin`, `decision = reason`, `outcome = outcome`, `responsibility = responsibility`, `approvedRefundAmount = totalApprovedRefund.setScale(0)`, `storeAdjustmentAmount = storeAdj.setScale(0)`, `commissionAdjustmentAmount = commAdj.setScale(0)`, `resolvedAt = now`, `decisionKey = key`, `decisionFingerprint = fingerprint`.
      - Nếu `totalApprovedRefund > 0`: `complaint.status = ComplaintStatus.DECIDED`, `closedAt = null`.
      - Ngược lại (`totalApprovedRefund == 0`): `complaint.status = ComplaintStatus.CLOSED`, `closedAt = now`.
    - Ghi `AuditLog("COMPLAINT_DECIDED")` (và `"COMPLAINT_CLOSED"` nếu đóng ngay).
- **`close(adminId, complaintId)` (`SD-10` #30–33):**
  - Khóa `Complaint` (`findForUpdate`), kiểm tra `status == ComplaintStatus.DECIDED` (else `409 INVALID_COMPLAINT_STATUS`).
  - Kiểm tra mọi `RefundRequest` có `complaint.id == complaintId` đều ở trạng thái `FULFILLED` (`else 409 REFUND_PENDING`).
  - Chuyển `complaint.status = ComplaintStatus.CLOSED`, `closedAt = now`, ghi `AuditLog("COMPLAINT_CLOSED")`.
- **Tự động đóng trong `ManualRefundService.confirm` (`Q4`):**
  - Sau khi chuyển `RefundRequest` sang `FULFILLED`, nếu `o.complaint != null`: khóa `Complaint` và kiểm tra không còn `RefundRequest` nào thuộc `complaint.id` khác `FULFILLED`; nếu tất cả đã `FULFILLED` và `complaint.status == DECIDED`, tự động set `complaint.status = CLOSED`, `closedAt = now`, ghi `AuditLog("COMPLAINT_CLOSED")`.
  - Thứ tự khóa bất biến: `ManualRefundService` luôn khóa `RefundRequest -> Complaint`, còn `close`/`decide` không khóa `RefundRequest` sau `Complaint` (chỉ đọc hoặc tạo mới) → tuyệt đối không deadlock.

---

## 3. API Contract (`docs/api/openapi.yaml`)

Thêm **8** đường dẫn mới (nâng từ `108` → `116` paths) và **2** tag mới (`"Complaints"`, `"Admin complaints"`, nâng từ `26` → `28` tags):

| Method / Path | Quyền & Request | Response | Tag |
|---|---|---|---|
| `POST /api/v1/complaints` | `CUSTOMER`; header `Idempotency-Key` (tùy chọn); body `ComplaintSubmitRequest` | `201/200 CustomerComplaintDetail`; `400, 401, 404, 409` | `Complaints` |
| `GET /api/v1/complaints` | `CUSTOMER`; query `status`, `orderId`, `page`, `size` | `200 PageResponse<CustomerComplaintSummary>`; `400, 401` | `Complaints` |
| `GET /api/v1/complaints/{complaintId}` | `CUSTOMER` (chủ hồ sơ) | `200 CustomerComplaintDetail`; `401, 404` | `Complaints` |
| `POST /api/v1/complaints/{complaintId}/supplement` | `CUSTOMER` (chủ hồ sơ, trạng thái `AWAITING_EVIDENCE`); body `ComplaintSupplementRequest` | `200 CustomerComplaintDetail`; `400, 401, 404, 409` | `Complaints` |
| `GET /api/v1/admin/complaints` | `ADMIN`; query `status`, `type`, `storeId`, `orderId`, `from`, `to`, `page`, `size` | `200 PageResponse<AdminComplaintSummary>`; `400, 401, 403` | `Admin complaints` |
| `GET /api/v1/admin/complaints/{complaintId}` | `ADMIN` | `200 AdminComplaintDetail`; `401, 403, 404` | `Admin complaints` |
| `POST /api/v1/admin/complaints/{complaintId}/request-info` | `ADMIN`; body `ComplaintRequestInfoRequest` | `200 AdminComplaintDetail`; `400, 401, 403, 404, 409` | `Admin complaints` |
| `POST /api/v1/admin/complaints/{complaintId}/decide` | `ADMIN`; header `Idempotency-Key` (tùy chọn); body `ComplaintDecisionRequest` | `200 AdminComplaintDetail`; `400, 401, 403, 404, 409` | `Admin complaints` |
| `POST /api/v1/admin/complaints/{complaintId}/close` | `ADMIN` | `200 AdminComplaintDetail`; `401, 403, 404, 409` | `Admin complaints` |

*(Ghi chú: `/api/v1/complaints` và `/api/v1/complaints/{complaintId}` mỗi path có POST+GET hoặc GET riêng nên tổng cộng 8 path mới: 3 dưới `/api/v1/complaints...` và 5 dưới `/api/v1/admin/complaints...` → `108 + 8 = 116` paths.)*

---

## 4. Migration và Database Schema

- File migration mới: `src/main/resources/db/migration/V22__complaint_workflow_and_resolution.sql`
  - Mở rộng `complaint` với `complaint_code` (backfill + `NOT NULL UNIQUE`), `within_dispute_window`, `requested_info_note`, `outcome`, `responsibility`, `approved_refund_amount`, `store_adjustment_amount`, `commission_adjustment_amount`, `closed_at`, `request_fingerprint`, `decision_key`, `decision_fingerprint` + các index `uq_complaint_code`, `uq_complaint_customer_idempotency`, `uq_complaint_decision_key`, `idx_complaint_status_created`.
  - Thay `ux_refund_request_payment` thành `ux_refund_request_payment_non_complaint ON refund_request (payment_id) WHERE payment_id IS NOT NULL AND complaint_id IS NULL`.
- Không sửa bất kỳ file migration nào từ `V1`–`V21`.
- Số lượng Hibernate entity giữ nguyên (`43`); số migration tăng `21` → `22`.

---

## 5. File Layout dự kiến

Đường dẫn Kotlin tương đối dưới `src/main/kotlin/com/foodrescue/backend/`:

| File | Loại | Trách nhiệm |
|---|---|---|
| `src/main/resources/db/migration/V22__complaint_workflow_and_resolution.sql` | NEW | Mở rộng cột workflow/quyết định trên `complaint` + cập nhật partial unique index `refund_request` |
| `complaint/ComplaintOutcome.kt` | NEW | Enum `APPROVED`, `PARTIAL`, `REJECTED` |
| `complaint/ComplaintResponsibility.kt` | NEW | Enum `STORE`, `PLATFORM`, `CUSTOMER`, `SHARED`, `NONE` |
| `complaint/entity/Complaint.kt` | MODIFIED | Map đầy đủ các cột mới từ `V22` |
| `complaint/repository/ComplaintRepository.kt` | MODIFIED | Thêm `findForUpdate`, `existsByComplaintCode`, `findByCustomerIdAndIdempotencyKeyAndSourceBoxIsNull`, `existsActiveDuplicate`, `findOrderId`, v.v. |
| `payment/repository/RefundRequestRepository.kt` | MODIFIED | Thêm `findAllByComplaintIdOrderByCreatedAt`, `openObligationsForPayment(paymentId)` |
| `store/service/StoreHistoryRecorder.kt` | MODIFIED | Thêm `"COMPLAINT"` vào `ENTITY_TYPES` |
| `surprisebox/service/BoxUnfitProcessor.kt` | MODIFIED | Cấp `complaintCode` qua `ComplaintCodeGenerator` khi mở khiếu nại hậu giao T7.5 |
| `pickup/service/NoShowCompensationService.kt` | MODIFIED | Bổ sung `'DECIDED'` vào danh sách chặn giải tỏa tự động ở `releaseEligible` |
| `payment/service/ManualRefundService.kt` | MODIFIED | Tự động đóng `Complaint` (`DECIDED` → `CLOSED`) khi nghĩa vụ hoàn cuối cùng của hồ sơ đạt `FULFILLED` |
| `complaint/dto/ComplaintSubmitRequest.kt` | NEW | DTO gửi khiếu nại (`orderId`, `type`, `description`, `attachmentIds`) |
| `complaint/dto/ComplaintSupplementRequest.kt` | NEW | DTO bổ sung chứng cứ (`description`, `attachmentIds`) |
| `complaint/dto/CustomerComplaintSummary.kt` | NEW | DTO tóm tắt khiếu nại của khách hàng |
| `complaint/dto/CustomerComplaintDetail.kt` | NEW | DTO chi tiết khiếu nại của khách hàng kèm trạng thái hoàn tiền & đính kèm |
| `complaint/dto/AdminComplaintSummary.kt` | NEW | DTO tóm tắt cho Admin (`UC-63`) kèm `heldCompensationAmount` |
| `complaint/dto/AdminComplaintDetail.kt` | NEW | DTO tổng hợp chi tiết cho Admin (`UC-64`: đơn, hộp, kiểm tra, thanh toán, hoàn tiền, giao nhận, No-show, audit) |
| `complaint/dto/ComplaintRequestInfoRequest.kt` | NEW | DTO yêu cầu bổ sung chứng cứ (`note`) |
| `complaint/dto/ComplaintDecisionRequest.kt` | NEW | DTO quyết định giải quyết (`outcome`, `responsibility`, `reason`, `refundAllocations`, `storeAdjustmentAmount`, `commissionAdjustmentAmount`) |
| `complaint/service/ComplaintCodeGenerator.kt` | NEW | Sinh mã tiếp nhận `CMP-yyMMdd-XXXXXX` |
| `complaint/service/ComplaintViews.kt` | NEW | Dựng `CustomerComplaintSummary`/`Detail`, `AdminComplaintSummary`/`Detail` tổng hợp đa nguồn |
| `complaint/service/ComplaintService.kt` | NEW | Nghiệp vụ phía Khách hàng (`submit`, `myList`, `myDetail`, `supplement`, xử lý 48h `D06`) |
| `complaint/service/AdminComplaintService.kt` | NEW | Nghiệp vụ phía Admin (`list`, `detail`, `requestInfo`, `decide`, `close`, khóa bi quan, `FinancialEvent`) |
| `complaint/controller/ComplaintController.kt` | NEW | Endpoints `/api/v1/complaints/**` |
| `complaint/controller/AdminComplaintController.kt` | NEW | Endpoints `/api/v1/admin/complaints/**` |
| `docs/api/openapi.yaml` | MODIFIED | Thêm tags `Complaints`, `Admin complaints` (`26` → `28`) và 8 paths (`108` → `116`) kèm schema/ví dụ/mã lỗi |
| `src/test/kotlin/.../ComplaintTests.kt` | NEW | Integration test TDD cho toàn bộ `AC-01`..`AC-11` |
| `src/test/kotlin/.../AuthMigrationTests.kt` | MODIFIED | Cập nhật kỳ vọng số migration `21` → `22` |
| `src/test/kotlin/.../FoodRescueApplicationTests.kt` | MODIFIED | Cập nhật kỳ vọng số migration `21` → `22` |
| `src/test/kotlin/.../SwaggerUiTests.kt` | MODIFIED | Cập nhật kỳ vọng OpenAPI paths `108` → `116`, tags `26` → `28` |
| `docs/specs/t8-1-complaints/03-implementation-control.txt` | NEW | Báo cáo hoàn thành & kết quả kiểm chứng cuối task |
| `docs/specs/roadmap-3-9.md` | MODIFIED | Đánh dấu `[x] T8.1` khi hoàn tất |
| `.gitignore` | MODIFIED | Thêm `!docs/specs/t8-1-complaints/` khi hoàn tất |

---

## 6. Task Breakdown

- [x] **T8.1-01:** Tạo migration `V22__complaint_workflow_and_resolution.sql`, enum `ComplaintOutcome` / `ComplaintResponsibility`, cập nhật `Complaint.kt`, `ComplaintRepository.kt`, `RefundRequestRepository.kt`, `StoreHistoryRecorder.kt`.
- [x] **T8.1-02 (TDD):** Viết trước bộ integration test `ComplaintTests.kt` bao phủ `AC-01`..`AC-11` (xác nhận đỏ trước khi cài đặt controller/service).
- [x] **T8.1-03:** Cài đặt `ComplaintCodeGenerator`, các DTO, `ComplaintViews`, cập nhật `BoxUnfitProcessor.kt` gán `complaintCode`, và xây dựng `ComplaintService` + `ComplaintController` (`UC-23`, 48h `D06`, supplement).
- [x] **T8.1-04:** Xây dựng `AdminComplaintService` + `AdminComplaintController` (`UC-63`, `UC-64`, `UC-65`, khóa bi quan, giới hạn hoàn theo nguồn thanh toán, xử lý `NoShowCase` `FORFEITED`/`HELD`/`ELIGIBLE`, `FinancialEvent(COMPLAINT_RESOLVED)`).
- [x] **T8.1-05:** Cập nhật `ManualRefundService` (auto-close `Complaint` khi mọi `RefundRequest` `FULFILLED`) và `NoShowCompensationService` (bảo vệ khoản giữ khi có complaint `DECIDED`).
- [x] **T8.1-06:** Cập nhật `docs/api/openapi.yaml` (`116` paths / `28` tags), `AuthMigrationTests.kt`, `FoodRescueApplicationTests.kt`, `SwaggerUiTests.kt`.
- [x] **T8.1-07:** Chạy targeted tests mở rộng, sau đó chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`, đọc trực tiếp `build/reports/tests/test/index.html` và XML từng suite, hoàn tất `03-implementation-control.txt`, đóng `[x] T8.1` trong `roadmap-3-9.md` và cập nhật `.gitignore`.

---

## 7. Truy vết AC → Task → Test

| AC | Mô tả ngắn | Task | Test Case dự kiến (`ComplaintTests` & liên quan) |
|---|---|---|---|
| **AC-01** | Khách gửi khiếu nại + chứng cứ + `complaintCode` + idempotency (`201`/`200`/`409`) | T8.1-01, 03 | `AC-01 customer submits complaint with evidence, code and idempotency replay` |
| **AC-02** | Phản ánh No-show đúng hạn 48h (hoặc `disputeDeadline == null`) giữ `DISPUTED` qua job | T8.1-03, 05 | `AC-02 timely no-show complaint keeps compensation disputed even after 48h job` |
| **AC-03** | Phản ánh No-show quá hạn 48h → ghi nhận hỗ trợ `withinDisputeWindow=false` (D06) | T8.1-03 | `AC-03 late no-show complaint records support request without reopening dispute window` |
| **AC-04** | Admin lọc danh sách (`UC-63`) và xem chi tiết đa chiều (`UC-64`) | T8.1-04 | `AC-04 admin filters complaint list and inspects multi-source detail` |
| **AC-05** | Admin yêu cầu bổ sung (`AWAITING_EVIDENCE`), khách bổ sung quay về `INVESTIGATING` | T8.1-03, 04 | `AC-05 admin requests additional evidence and customer supplements complaint` |
| **AC-06** | Duyệt hoàn toàn phần/một phần đơn `COMPLETED` (gồm hậu giao T7.5) → T6.5 `FULFILLED` → auto `CLOSED` | T8.1-04, 05 | `AC-06 resolved complaint creates refund obligation and auto-closes upon manual refund confirmation` |
| **AC-07** | No-show lỗi cửa hàng (`UC-65 A2`): `FORFEITED` + hoàn nốt phần còn thiếu lên cùng `Payment` gốc | T8.1-01, 04 | `AC-07 store fault no-show resolution forfeits retention and opens extra refund on same payment` |
| **AC-08** | Bác khiếu nại No-show (`UC-65 A3`): `CLOSED` ngay và mở khóa `DISPUTED` → `ELIGIBLE`/`HELD` | T8.1-04 | `AC-08 rejected no-show complaint closes immediately and unlocks compensation status` |
| **AC-09** | Từ chối hoàn vượt nguồn (`UC-65 E1`), quyết định lại (`DECIDED`/`CLOSED`), hoặc `/close` khi còn `PENDING` | T8.1-04 | `AC-09 excessive refund, re-decision and premature close are rejected with 409` |
| **AC-10** | Quyết định đồng thời trên cùng khiếu nại chỉ cho 1 thắng & bảo toàn bất biến tiền | T8.1-04 | `AC-10 concurrent decisions serialize and preserve financial invariants` |
| **AC-11** | Kiểm soát quyền `CUSTOMER` / `ADMIN` / file chứng cứ | T8.1-03, 04 | `AC-11 authorization checks reject unauthorized complaint submission and admin actions` |
| **AC-12** | Flyway `V22`, `openapi.yaml` (`116` paths / `28` tags), toàn bộ suite pass | T8.1-06, 07 | `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests` |

---

## 8. Kế hoạch kiểm thử TDD

- Viết `ComplaintTests.kt` trước khi cài đặt service/controller theo đúng quy trình TDD, xác nhận bước chạy đầu fail đúng do endpoint chưa tồn tại.
- Dùng `Clock` cố định có thể dịch chuyển thời gian (hoặc helper tương đồng `NoShowTests`) để kiểm chứng chính xác ranh giới trước/sau 48h `disputeDeadline` (`AC-02`, `AC-03`, `AC-08`).
- Test đồng thời (`AC-10`) dùng `concurrently(...)` / `CountDownLatch` xác nhận 2 request `/decide` đồng thời chỉ có 1 thành công và 1 bị từ chối `409`.
- Kết thúc task chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain` và đọc trực tiếp `build/reports/tests/test/index.html` cùng XML từng suite trước khi chốt `03-implementation-control.txt`.

---

## 9. Rủi ro và phương án kiểm soát / Rollback

1. **Rủi ro vòng khóa chéo giữa `ManualRefundService.confirm` và `AdminComplaintService.close`/`decide`:**  
   *Kiểm soát:* Quy ước một chiều `RefundRequest -> Complaint` khi khóa ở `ManualRefundService.confirm`; ở `AdminComplaintService.decide` chỉ tạo mới `RefundRequest` (không khóa `RefundRequest` cũ sau khi khóa `Complaint`), còn `close` chỉ đọc danh sách `RefundRequest` dưới khóa `Complaint`.
2. **Rủi ro vượt tổng tiền thanh toán khi hoàn nhiều đợt trên 1 `Payment` (`UC-65 A2`):**  
   *Kiểm soát:* Khóa `Payment` theo `id ASC` và tính chính xác `payment.receivedAmount - SUM(SUCCEEDED refund) - SUM(PENDING/APPROVED refund_request)` trước khi tạo `RefundRequest` mới.
3. **Rollback:**  
   Trước khi đóng task, có thể hoàn tác sạch code tại gói `complaint` cùng `V22` trên DB dev/test mới mà không ảnh hưởng `V1`–`V21`.

## 10. Làm rõ khi triển khai (2026-11-24)

- Khóa Account của khách trước khi khóa Order nếu submit có Idempotency-Key, rồi kiểm tra replay lần nữa. Mục đích: tuần tự hóa cùng khóa theo customer kể cả khi hai payload trỏ tới hai Order khác nhau; tránh unique violation trả 500. Những khóa nghiệp vụ tiếp theo vẫn Order → NoShowCase → StoredFile.
- Test NoShowTests chèn Complaint trực tiếp cần bổ sung complaint_code để đáp ứng NOT NULL của V22; không thay đổi kỳ vọng nghiệp vụ cũ.
- ManualRefundService cần bảo vệ bất biến mới khi cùng Payment có nhiều RefundRequest. Ngoài auto-close, approve phải trừ các nghĩa vụ PENDING/APPROVED khác trước khi duyệt/tái duyệt. Nếu không, có thể giảm approvedAmount của nghĩa vụ A, mở thêm nghĩa vụ B từ phần tiền còn lại, rồi tăng A về requestedAmount khiến tổng nghĩa vụ vượt receivedAmount.
- Để tuần tự hóa approve/confirm với decide, ManualRefundService đọc orderId của RefundRequest rồi khóa CustomerOrder trước RefundRequest; thứ tự tương đối RefundRequest → Complaint giữ nguyên. close chỉ khóa Complaint rồi đọc nghĩa vụ, không khóa RefundRequest. Điều chỉnh tối thiểu trong RefundRequestRepository (findOrderId) và ManualRefundService là cần thiết cho bất biến tiền T8.1, không mở rộng chức năng T6.5.

- Full build đầu: 316 tests, 312 PASS / 3 FAIL / 1 SKIPPED. BoxUnfitViolationSuspendTests setup nhận HTTP 503 `No financial policy in force`; hai ca OrderCancellationTests cũng lỗi setup lấy id đơn. V12 seed effective_from bằng PostgreSQL now(), trong khi fixture Clock chụp Instant.now() của host nên có thể đi trước thời điểm policy có hiệu lực. Không sửa V12 hay FinancialPolicyResolver. Thêm StoreTestFixtures.fixtureNow() = max(host now, seeded effective_from), dùng tại các reset PickupTestClock của ComplaintTests/T7.1/T7.2/T7.3/T7.4/T7.5. Đây là điều chỉnh fixture đồng hồ để kiểm chứng yêu cầu AC-12; không thay đổi/khoan dung bất kỳ assertion nào.
