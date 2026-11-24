# Technical Plan: T7.4 — No-show và kiểm tra lại hộp

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A đã được người dùng duyệt `Q1`–`Q5` ngày 2026-11-24.  
**Status:** DONE — triển khai và kiểm chứng hoàn tất ngày 2026-11-24  
**Updated:** 2026-11-24  
**Scope:** T7.4 — Job quá hạn nhận (`PENDING_NO_SHOW`), xem đơn quá hạn (`UC-36`), xác nhận khách không đến nhận (`UC-37`, `BR-41..43`, `BR-51`, `D06`), giải tỏa bù đắp `ELIGIBLE` sau 48h kể từ khi khách nhận thông báo, kiểm tra lại hộp No-show (`UC-35`, `BR-44`).  
**Phụ thuộc:** T7.3 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Quyết định đã duyệt tại Gate A và Baseline

1. **Các quyết định Gate A đã chốt (`Q1`–`Q5`):**
   - **Q1 (API endpoints):** Bộ 5 endpoint cửa hàng dưới `/api/v1/stores/{storeId}/...`:
     - `GET /api/v1/stores/{storeId}/no-shows/overdue` (`UC-36`, quyền `NO_SHOW_CONFIRM`/Owner)
     - `GET /api/v1/stores/{storeId}/no-shows/{caseId}` (chi tiết `NoShowCase`, quyền `NO_SHOW_CONFIRM`/Owner)
     - `POST /api/v1/stores/{storeId}/orders/{orderId}/no-show` (`UC-37`, quyền `NO_SHOW_CONFIRM`/Owner, header `Idempotency-Key` tùy chọn)
     - `GET /api/v1/stores/{storeId}/boxes/awaiting-inspection` (danh sách hộp chờ kiểm tra, quyền `BOX_MANAGE`/Owner)
     - `POST /api/v1/stores/{storeId}/boxes/{boxId}/recheck` (`UC-35`, quyền `BOX_MANAGE`/Owner)
   - **Q2 (Mở rộng `PickupIncidentService` theo `UC-37 A1`):** Cho phép `PickupIncidentService.recordIncident` nhận thêm đơn ở `PENDING_NO_SHOW` (chuyển về `HANDOVER_IN_PROGRESS` với `statusReason = "HANDOVER_INCIDENT: ..."`, khóa khả năng xác nhận No-show khi `customerArrived = true` và cho phép luồng hủy/xử lý T7.3).
   - **Q3 (`notification_received_at` và `ELIGIBLE` theo `D06`):** Tạo `Notification(NO_SHOW, PENDING)` khi xác nhận No-show; giữ `notificationReceivedAt = null`, `disputeDeadline = null`, `compensationStatus = HELD`. Cung cấp `NoShowCompensationService` với `recordNotificationReceived(notificationId, receivedAt)` (chuẩn bị cho T8.2) và job `NoShowCompensationJob` chỉ nâng `HELD` → `ELIGIBLE` khi `notificationReceivedAt != null && disputeDeadline != null && now >= disputeDeadline` (không có khiếu nại mở).
   - **Q4 (Trạng thái hộp `PASSED` khi chưa có đợt mới — `UC-35 A2`):** Ghi `BoxInspection(PASSED)`, cập nhật `safeUntil`, và giữ `box.status = AWAITING_INSPECTION` ngoài lượng bán khi `targetBatchId == null`. Khi có `targetBatchId` đạt chuẩn (`DRAFT`/`OPEN`/`PAUSED`, cùng cửa hàng, `pickupEndsAt > now`, `safeUntil >= pickupEndsAt`, `totalListedValue >= minimumValue`), chuyển `currentBatch = targetBatch` và `status = AVAILABLE`.
   - **Q5 (Migration `V20__no_show_idempotency.sql`):** Bổ sung `idempotency_key`, `request_fingerprint`, `refund_amount` và partial unique index trên `no_show_case`; giữ tổng entity ở `43`, cập nhật kỳ vọng Flyway migration `19 -> 20` trong `AuthMigrationTests` và `FoodRescueApplicationTests`.
2. **Baseline:**
   - `./gradlew.bat build --console=plain` **BUILD SUCCESSFUL** (19 migration, 43 entity, 101 OpenAPI paths, 25 tags).

---

## 2. Thiết kế chi tiết

### 2.1. Migration `V20__no_show_idempotency.sql` & Entity `NoShowCase`
- Thêm vào bảng `no_show_case`:
  - `idempotency_key varchar(255)` (nullable)
  - `request_fingerprint text` (nullable với dữ liệu cũ, được ghi bắt buộc khi tạo mới từ T7.4)
  - `refund_amount numeric(19,2) NOT NULL DEFAULT 0 CHECK (refund_amount >= 0 AND refund_amount = trunc(refund_amount))`
  - `CREATE UNIQUE INDEX uq_no_show_case_idempotency ON no_show_case(order_id, idempotency_key) WHERE idempotency_key IS NOT NULL;`
- Cập nhật entity [`NoShowCase`](../../src/main/kotlin/com/foodrescue/backend/pickup/entity/NoShowCase.kt) khớp 3 cột mới (`idempotencyKey`, `requestFingerprint`, `refundAmount`). Không tạo thêm entity mới (vẫn 43 entity).

### 2.2. Job quá hạn nhận (`NoShowOverdueService` & `NoShowOverdueJob`)
- **Scheduled config:** Thêm vào `OrderProperties` (hoặc `@Value` / `@Scheduled` tương tự `OrderExpiryJob`):
  - `orders.no-show-job-enabled` (default `true`)
  - `orders.no-show-scan-ms` (default `30000`)
- **`NoShowOverdueService.markOverdue(now: Instant, batchSize: Int = 100): Int` (`@Transactional`):**
  1. Truy vấn native với `NamedParameterJdbcTemplate`:
     ```sql
     SELECT o.id FROM customer_order o
     WHERE o.status = 'CONFIRMED'
       AND o.pickup_ends_at <= :now
       AND NOT EXISTS (
           SELECT 1 FROM pickup_session s
           WHERE s.order_id = o.id AND s.status IN ('OPEN', 'RECONCILING')
       )
     ORDER BY o.pickup_ends_at
     LIMIT :n
     FOR UPDATE SKIP LOCKED
     ```
  2. Với mỗi `id`, đọc lại `CustomerOrder` (`orders.findById`), kiểm tra bảo vệ `order.status == OrderStatus.CONFIRMED && !order.pickupEndsAt.isAfter(now)` và không có phiên `OPEN`/`RECONCILING`:
     - Đặt `order.status = OrderStatus.PENDING_NO_SHOW`, `order.statusReason = "PICKUP_WINDOW_ENDED"`, `order.statusChangedAt = now`.
     - Ghi `StoreHistoryRecorder.record(null, order.store, "CUSTOMER_ORDER", id, "ORDER_MARKED_PENDING_NO_SHOW", mapOf("status" to "CONFIRMED"), mapOf("status" to "PENDING_NO_SHOW"))`.
  3. Trả về số dòng đã claim (`ids.size`) để `NoShowOverdueJob` rút hết backlog theo lô `BATCH = 100`.

### 2.3. Tra cứu đơn quá hạn (`NoShowService.overdueOrders` & `NoShowService.detail`)
- **`overdueOrders(actorId, storeId, date, status)` (`@Transactional(readOnly = true)` — `UC-36`):**
  - `authorization.requirePermission(actorId, storeId, "NO_SHOW_CONFIRM")`.
  - Kiểm tra `store` tồn tại (`404 NOT_FOUND` nếu không).
  - Tập trạng thái hợp lệ: `ALLOWED_OVERDUE = setOf(CONFIRMED, HANDOVER_IN_PROGRESS, PENDING_NO_SHOW, NO_SHOW)`. Nếu truyền `status` ngoài tập này → `400 VALIDATION_FAILED`.
  - Lọc các đơn thuộc `storeId` có `pickupEndsAt <= clock.instant()` (nếu có `date`, lọc theo `[date 00:00, date+1 00:00)` tại `ZoneId.of(store.timezone)` giống `PickupOrderService`), sắp xếp theo `pickupEndsAt ASC, id ASC`.
  - Map từng đơn sang `OverdueOrderView`:
    - `orderId`, `orderCode`, `status`, `customerDisplayName`, `quantity`, `pickupStartsAt`, `pickupEndsAt`
    - `boxes`: `List<AllocatedBoxView>` từ `BoxAllocationRepository.findAllByOrderIdOrderByReservedAt`
    - `money`: `OrderMoneyView` từ `OrderMoneyReader`
    - `projectedAmounts`: `NoShowAmountsView(retainedAmount, refundAmount, storeCompensationAmount, commissionAmount = 0)` tính bằng `NoShowAmounts.calculate(order.totalAmount, money.verifiedPaid, order.noShowRetentionRate)`
    - `activeSessionId`, `activeSessionStatus`: từ `PickupSessionRepository` (`OPEN`/`RECONCILING`)
    - `incidents`: `List<PickupIncidentView>` từ `PickupIncidentRepository` + cờ tiện ích `customerArrivedIncident = incidents.any { it.customerArrived }`
    - `noShowCase`: `NoShowCaseSummaryView?` nếu đơn đã có `NoShowCase`.
- **`detail(actorId, storeId, caseId)` (`@Transactional(readOnly = true)`):**
  - `authorization.requirePermission(actorId, storeId, "NO_SHOW_CONFIRM")`.
  - Tải `NoShowCase` thuộc `storeId` (`404 NOT_FOUND` nếu không có hoặc thuộc cửa hàng khác).
  - Trả về `NoShowCaseDetailView`: thông tin `NoShowCase`, `amounts`, `notificationId`/`Status`, `attachmentFileIds`, `refundRequests` (`List<RefundRequestView>` qua `RefundViews`), `boxes`, `incidents`.

### 2.4. Xác nhận No-show (`NoShowService.confirm` — `UC-37`, `BR-41..43`, `BR-51`)
- Request `NoShowConfirmRequest`:
  - `@field:NotNull val storeOpenedOnTime: Boolean?`
  - `@field:NotNull val boxesReadyAndCompliant: Boolean?`
  - `@field:NotBlank @field:Size(max = 2000) val evidence: String?`
  - `@field:Size(max = 10) val attachmentIds: List<UUID> = emptyList()`
- Luồng xử lý trong `@Transactional`:
  1. `authorization.requirePermission(actorId, storeId, "NO_SHOW_CONFIRM")`.
  2. Validate header `Idempotency-Key` (nếu có): regex `^[A-Za-z0-9_-]{1,255}$` (`400 VALIDATION_FAILED` nếu sai).
  3. Tính `fingerprint = mapper.writeValueAsString(listOf("NO_SHOW_CONFIRM", r.storeOpenedOnTime, r.boxesReadyAndCompliant, r.evidence!!.trim(), r.attachmentIds.distinct().sorted()))`.
  4. Khóa bi quan `CustomerOrder` (`orders.findForUpdate(orderId)`) và kiểm tra `order.store.id == storeId` (else `404 NOT_FOUND`).
  5. **Idempotency (`UC-37 E2`):**
     - Kiểm tra `noShowCases.findByOrderId(orderId)`:
       - Nếu đã tồn tại:
         - Nếu `key != null && existing.idempotencyKey == key`:
           - Nếu `existing.requestFingerprint != fingerprint` → `409 IDEMPOTENCY_KEY_REUSED`.
           - Trả về `NoShowConfirmResult(detailView(existing), created = false)` (`200 OK`).
         - Ngược lại → `409 NO_SHOW_ALREADY_CONFIRMED`.
  6. **Kiểm tra điều kiện chặn (`UC-37 E1/A1`, `BR-31`, `BR-41`):**
     - `val now = clock.instant()`
     - `order.status == OrderStatus.COMPLETED` → `409 ORDER_ALREADY_COMPLETED`
     - `order.status in listOf(OrderStatus.CANCELLED, OrderStatus.EXPIRED)` → `409 INVALID_ORDER_STATUS`
     - `now.isBefore(order.pickupEndsAt)` → `409 PICKUP_WINDOW_NOT_ENDED`
     - Tồn tại `PickupSession` `OPEN`/`RECONCILING` → `409 HANDOVER_IN_PROGRESS`
     - Tồn tại `PickupIncident` trên đơn có `customerArrived == true` → `409 CUSTOMER_ARRIVED_INCIDENT` ("Customer arrived at pickup; resolve via pickup incident (UC-30), cannot confirm no-show")
     - `r.storeOpenedOnTime != true || r.boxesReadyAndCompliant != true` → `409 NO_SHOW_PRECONDITION_FAILED` ("Store must have opened on time with compliant boxes ready")
     - `order.status !in listOf(OrderStatus.PENDING_NO_SHOW, OrderStatus.CONFIRMED, OrderStatus.HANDOVER_IN_PROGRESS)` → `409 INVALID_ORDER_STATUS`
  7. **Tính tiền và khóa nguồn thanh toán (`BR-42`):**
     - `val orderMoney = money.money(order)` (`verifiedPaid`)
     - `val amounts = NoShowAmounts.calculate(order.totalAmount, orderMoney.verifiedPaid, order.noShowRetentionRate)`
     - Nếu `amounts.refundAmount.signum() > 0`:
       - Kiểm tra `amounts.refundAmount <= orderMoney.verifiedPaid - orderMoney.refunded - orderMoney.refundInProgress` (nếu không → `409 REFUND_AMOUNT_CONFLICT`).
       - Khóa các `Payment` `VERIFIED` theo `id` tăng dần (`payments.findForUpdate(p.id!!)`), lọc các payment chưa có `RefundRequest` (`!refundRequests.existsByPaymentId(p.id!!)`), xác nhận tổng đủ `amounts.refundAmount` (else `409 REFUND_ALREADY_REQUESTED`).
  8. **Validate file đính kèm (nếu có):**
     - Khóa từng `StoredFile` theo `id` tăng dần (`PESSIMISTIC_WRITE`), kiểm tra `file.uploadedBy.id == actorId` và `attachments.findAllByFileId(id).isEmpty()` (`400 VALIDATION_FAILED` nếu sai/đã gắn).
  9. **Cập nhật nguyên tử:**
     - Khóa `BoxAllocation` (`HELD`/`CONFIRMED`) và `PhysicalBox` theo `box.id` tăng dần:
       - `allocation.status = AllocationStatus.RELEASED`, `releasedAt = now`, `releaseReason = "NO_SHOW_CONFIRMED"`
       - `if (box.status != BoxStatus.BLOCKED) box.status = BoxStatus.AWAITING_INSPECTION` (BR-44, không bao giờ tự về `AVAILABLE`).
     - Cập nhật `CustomerOrder`:
       - `order.status = OrderStatus.NO_SHOW`
       - `order.statusReason = "NO_SHOW_CONFIRMED: ${r.evidence.trim()}"`
       - `order.statusChangedAt = now`
       - Vô hiệu hóa mã nhận hàng (`pickupCodeHash = null; pickupCodeEncrypted = null; pickupCodeKeyId = null` — tuân thủ `ck_order_pickup_code_storage` & `V18`).
     - Tạo `Notification`:
       - `Notification(recipient = order.customer, order = order, type = NotificationType.NO_SHOW, payload = JSON, status = NotificationStatus.PENDING)` (giữ `sentAt/deliveredAt/readAt = null` cho T8.2).
     - Tạo `NoShowCase`:
       - `order`, `verifiedBy = actor`, `verifiedAt = now`, `evidence = r.evidence.trim()`, `notification = notif`, `notificationReceivedAt = null`, `disputeDeadline = null`, `retainedAmount = amounts.retainedAmount`, `refundAmount = amounts.refundAmount`, `compensationStatus = CompensationStatus.HELD`, `idempotencyKey = key`, `requestFingerprint = fingerprint`.
     - Lưu các `Attachment(file, noShowCase = savedCase)` (nếu có).
     - Nếu `amounts.refundAmount.signum() > 0`: tạo `RefundRequest` (`PENDING`, `reasonCode = "NO_SHOW_REFUND"`, `payment = ...`) phân bổ qua các verified `Payment` khả dụng (tương tự `PaidOrderCancellationExecutor`). Nếu `refundAmount == 0`: **không** tạo `RefundRequest`.
     - Lưu `FinancialEvent(order = order, type = FinancialEventType.NO_SHOW_CONFIRMED, noShowCase = savedCase, occurredAt = now, businessKey = "NO_SHOW_CONFIRMED:${order.id}")`.
     - Ghi `StoreHistoryRecorder.record(actor, order.store, "CUSTOMER_ORDER", order.id!!, "NO_SHOW_CONFIRMED", ...)`.
     - Trả về `NoShowConfirmResult(detailView(savedCase), created = true)` (`201 CREATED`).

### 2.5. Phân quyền file chứng cứ (`NoShowAttachmentPolicy`)
- `NoShowAttachmentPolicy` implements `AttachmentAccessPolicy`:
  - `supports(attachment) = attachment.noShowCase != null`
  - `canRead(accountId, attachment)`:
    - Khách hàng của đơn (`order.customer.id == accountId`), HOẶC
    - `ADMIN` (`roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)`), HOẶC
    - Chủ cửa hàng / nhân viên có quyền `NO_SHOW_CONFIRM` của cửa hàng sở hữu đơn (`authorization.contexts(accountId).any { it.storeId == order.store.id && (it.owner || "NO_SHOW_CONFIRM" in it.permissions) }`).

### 2.6. Thời hạn 48h theo D06 (`NoShowCompensationService` & `NoShowCompensationJob`)
- **`recordNotificationReceived(notificationId: UUID, receivedAt: Instant): NoShowCase?` (`@Transactional`):**
  - Tìm và khóa `NoShowCase` theo `notificationId` (`findByNotificationId` → `findForUpdate`). Nếu không thấy trả `null`.
  - Nếu `notificationReceivedAt == null`: cập nhật `notificationReceivedAt = receivedAt` và `disputeDeadline = receivedAt.plus(order.disputeWindowHours.toLong(), ChronoUnit.HOURS)` (mặc định 48h, bảo đảm `disputeDeadline > notificationReceivedAt`), ghi `StoreHistoryRecorder` (`NO_SHOW_NOTIFICATION_RECEIVED`).
- **`releaseEligible(now: Instant, batchSize: Int = 100): Int` (`@Transactional`):**
  - Native query `FOR UPDATE SKIP LOCKED`:
    ```sql
    SELECT n.id FROM no_show_case n
    WHERE n.compensation_status = 'HELD'
      AND n.notification_received_at IS NOT NULL
      AND n.dispute_deadline IS NOT NULL
      AND n.dispute_deadline <= :now
      AND NOT EXISTS (
          SELECT 1 FROM complaint c
          WHERE c.order_id = n.order_id AND c.status IN ('OPEN', 'INVESTIGATING', 'AWAITING_EVIDENCE')
      )
    ORDER BY n.dispute_deadline
    LIMIT :n
    FOR UPDATE SKIP LOCKED
    ```
  - Chuyển `compensationStatus = CompensationStatus.ELIGIBLE` và ghi `StoreHistoryRecorder` (`NO_SHOW_COMPENSATION_ELIGIBLE`).
  - Các case chưa có `notification_received_at` giữ nguyên `dispute_deadline = null` và `compensation_status = HELD` (`D06`, `UC-37 E3`).
- `NoShowCompensationJob`: chạy định kỳ theo `orders.no-show-compensation-job-enabled` (default `true`) và `orders.no-show-compensation-scan-ms` (default `60000`).

### 2.7. Kiểm tra lại hộp No-show (`BoxRecheckService` — `UC-35`, `BR-44`)
- **`listAwaitingInspection(actorId, storeId)` (`@Transactional(readOnly = true)`):**
  - `authorization.requirePermission(actorId, storeId, "BOX_MANAGE")`.
  - Trả về danh sách `AwaitingInspectionBoxView` cho các `PhysicalBox` có `store.id == storeId && status == AWAITING_INSPECTION`, gồm `BoxView`, lần kiểm tra gần nhất (`BoxInspectionView?`), `sourceOrderId`, `sourceOrderCode`, `noShowCaseId`, `noShowVerifiedAt`.
- **`recheck(actorId, storeId, boxId, request)` (`@Transactional`):**
  - `authorization.requirePermission(actorId, storeId, "BOX_MANAGE")`.
  - Khóa `PhysicalBox` (`boxes.findForUpdate(boxId)`), kiểm tra `box.store.id == storeId` (else `404 NOT_FOUND`).
  - Kiểm tra `box.status == BoxStatus.AWAITING_INSPECTION` (else `409 BOX_NOT_AWAITING_INSPECTION` — `UC-35 E1`).
  - Kiểm tra không có `BoxAllocation` đang hoạt động (`HELD`/`CONFIRMED`) trên `boxId` (`else 409 BOX_ALLOCATED`).
  - Kiểm tra hộp có ít nhất một `BoxAllocation` `RELEASED` gắn với đơn `NO_SHOW` (`else 409 BOX_NOT_FROM_NO_SHOW`).
  - `val now = clock.instant()`, `val notes = request.notes?.trim().orEmpty()`, `val safeUntil = request.safeUntil!!`.
  - **Nếu `request.result == InspectionResult.FAILED` (`UC-35 A1`):**
    - Nếu `notes.isEmpty()` → `400 VALIDATION_FAILED` ("Notes are required when inspection fails").
    - Lưu bất biến `BoxInspection(box, inspector, now, FAILED, safeUntil, notes)`.
    - `box.status = BoxStatus.BLOCKED`; cập nhật `box.safeUntil = safeUntil` nếu `safeUntil.isAfter(box.packedAt)` (tôn trọng DB CHECK `safe_until > packed_at`).
  - **Nếu `request.result == InspectionResult.PASSED` (`UC-35 chính / A2`):**
    - Nếu `!safeUntil.isAfter(now)` → `400 VALIDATION_FAILED` ("safeUntil must be in the future when inspection passes").
    - Lưu bất biến `BoxInspection(box, inspector, now, PASSED, safeUntil, notes.ifEmpty { "Rechecked and passed" })`.
    - `box.safeUntil = safeUntil`.
    - Nếu `request.targetBatchId != null`:
      - Khóa `targetBatch = batches.findForUpdate(request.targetBatchId)` thuộc `storeId` (else `404 NOT_FOUND`).
      - Kiểm tra điều kiện đợt mới (`BR-44`): `targetBatch.status in setOf(DRAFT, OPEN, PAUSED)`, `targetBatch.pickupEndsAt.isAfter(now)`, `!safeUntil.isBefore(targetBatch.pickupEndsAt)`, `box.totalListedValue >= targetBatch.minimumValue`. Nếu vi phạm bất kỳ điều kiện nào → `ApiException(409, "TARGET_BATCH_INELIGIBLE", "...")`.
      - Gán `box.currentBatch = targetBatch`, `box.status = BoxStatus.AVAILABLE`.
    - Nếu `request.targetBatchId == null` (`UC-35 A2`): giữ `box.status = BoxStatus.AWAITING_INSPECTION` (ngoài lượng bán cho đến khi gắn vào đợt phù hợp).
  - Ghi `StoreHistoryRecorder.record(inspector, box.store, "PHYSICAL_BOX", box.id!!, "BOX_RECHECKED", ...)` và trả `BoxViews.detail(box)`.

### 2.8. Mở rộng `PickupIncidentService` (`Q2`)
- Cho phép `order.status in listOf(CONFIRMED, HANDOVER_IN_PROGRESS, PENDING_NO_SHOW)`.
- Khi ghi sự cố trên `PENDING_NO_SHOW`, cập nhật `order.status = HANDOVER_IN_PROGRESS` với `statusReason = "HANDOVER_INCIDENT: ${request.reason}"` để thống nhất hoàn toàn với luồng hủy T7.3 và chặn No-show khi `customerArrived == true`.

---

## 3. API Contract

Các tag OpenAPI sử dụng: thêm tag mới `No-show` (nâng từ `25` → `26` tags) cho 3 endpoint No-show và dùng tag `Boxes` hiện có cho 2 endpoint kiểm tra lại hộp (nâng tổng số path từ `101` → `106`):

| Method / Path | Quyền & Request | Response | Tag |
|---|---|---|---|
| `GET /api/v1/stores/{storeId}/no-shows/overdue` | Owner hoặc `NO_SHOW_CONFIRM`; query `date` (`YYYY-MM-DD`, tùy chọn), `status` (`CONFIRMED`, `HANDOVER_IN_PROGRESS`, `PENDING_NO_SHOW`, `NO_SHOW`, tùy chọn) | `200 List<OverdueOrderView>`; `400, 401, 403, 404` | `No-show` |
| `GET /api/v1/stores/{storeId}/no-shows/{caseId}` | Owner hoặc `NO_SHOW_CONFIRM` | `200 NoShowCaseDetailView`; `401, 403, 404` | `No-show` |
| `POST /api/v1/stores/{storeId}/orders/{orderId}/no-show` | Owner hoặc `NO_SHOW_CONFIRM`; header `Idempotency-Key` (tùy chọn); body `NoShowConfirmRequest` | `201/200 NoShowCaseDetailView`; `400, 401, 403, 404, 409` | `No-show` |
| `GET /api/v1/stores/{storeId}/boxes/awaiting-inspection` | Owner hoặc `BOX_MANAGE` | `200 List<AwaitingInspectionBoxView>`; `401, 403, 404` | `Boxes` |
| `POST /api/v1/stores/{storeId}/boxes/{boxId}/recheck` | Owner hoặc `BOX_MANAGE`; body `BoxRecheckRequest` | `200 BoxDetail`; `400, 401, 403, 404, 409` | `Boxes` |

---

## 4. Migration và Database Schema

- File mới: `src/main/resources/db/migration/V20__no_show_idempotency.sql`
  - Thêm `idempotency_key`, `request_fingerprint`, `refund_amount` lên `no_show_case` cùng `CHECK` nguyên đồng và partial unique index `uq_no_show_case_idempotency`.
- Không sửa đổi bất kỳ migration nào từ `V1`–`V19`.
- Số lượng Hibernate entity không đổi (`43`), số migration thành công tăng `19` → `20`.

---

## 5. File Layout dự kiến

Đường dẫn Kotlin tương đối dưới `src/main/kotlin/com/foodrescue/backend/`:

| File | Loại | Trách nhiệm |
|---|---|---|
| `src/main/resources/db/migration/V20__no_show_idempotency.sql` | NEW | Thêm `idempotency_key`, `request_fingerprint`, `refund_amount` + unique index trên `no_show_case` |
| `pickup/entity/NoShowCase.kt` | MODIFIED | Map thêm 3 cột từ `V20` |
| `pickup/repository/NoShowCaseRepository.kt` | MODIFIED | Thêm `findByOrderId`, `findByNotificationId`, `findForUpdate`, `findAllByOrderStoreIdOrderByVerifiedAtDesc` |
| `order/repository/BoxAllocationRepository.kt` | MODIFIED | Thêm query kiểm tra allocation đang active hoặc có đơn `NO_SHOW` trên `boxId` |
| `surprisebox/repository/PhysicalBoxRepository.kt` | MODIFIED | Thêm query tìm hộp theo `storeId` và `status` |
| `order/OrderProperties.kt` | MODIFIED | Bổ sung cấu hình `noShowScanMs`, `noShowJobEnabled`, `noShowCompensationScanMs`, `noShowCompensationJobEnabled` (hoặc giữ qua `@Scheduled` properties đồng bộ với `OrderExpiryJob`) |
| `pickup/dto/NoShowConfirmRequest.kt` | NEW | DTO xác nhận No-show (`storeOpenedOnTime`, `boxesReadyAndCompliant`, `evidence`, `attachmentIds`) |
| `pickup/dto/OverdueOrderView.kt` | NEW | DTO danh sách đơn quá hạn (`UC-36`) kèm dự tính `NoShowAmountsView`, hộp, sự cố |
| `pickup/dto/NoShowCaseDetailView.kt` | NEW | DTO chi tiết `NoShowCase` (tiền giữ/hoàn, đính kèm, `RefundRequest`, hộp, sự cố) |
| `pickup/dto/NoShowAmountsView.kt` | NEW | DTO `retainedAmount`, `refundAmount`, `storeCompensationAmount`, `commissionAmount` |
| `surprisebox/dto/BoxRecheckRequest.kt` | NEW | DTO kiểm tra lại hộp No-show (`result`, `safeUntil`, `notes`, `targetBatchId`) |
| `surprisebox/dto/AwaitingInspectionBoxView.kt` | NEW | DTO hộp chờ kiểm tra lại kèm thông tin đơn No-show & lần kiểm tra cuối |
| `pickup/service/NoShowOverdueService.kt` | NEW | Logic quét quá hạn nhận (`CONFIRMED` → `PENDING_NO_SHOW`) dùng `FOR UPDATE SKIP LOCKED` |
| `pickup/service/NoShowOverdueJob.kt` | NEW | `@Scheduled` job chạy `NoShowOverdueService` |
| `pickup/service/NoShowService.kt` | NEW | `overdueOrders`, `detail`, `confirm` (idempotency, locking, T6.5 `RefundRequest`, `FinancialEvent`, `Notification`) |
| `pickup/service/NoShowCompensationService.kt` | NEW | `recordNotificationReceived` (cho T8.2) & `releaseEligible` (sau 48h `D06`) |
| `pickup/service/NoShowCompensationJob.kt` | NEW | `@Scheduled` job chạy `NoShowCompensationService.releaseEligible` |
| `pickup/service/NoShowAttachmentPolicy.kt` | NEW | `AttachmentAccessPolicy` cho chứng cứ `no_show_case_id` |
| `pickup/service/PickupIncidentService.kt` | MODIFIED | Mở rộng cho phép ghi sự cố UC-30 trên đơn `PENDING_NO_SHOW` (`Q2`) |
| `surprisebox/service/BoxRecheckService.kt` | NEW | `listAwaitingInspection` và `recheck` hộp No-show (`UC-35`, `BR-44`) |
| `pickup/controller/StoreNoShowController.kt` | NEW | Endpoints `GET .../no-shows/overdue`, `GET .../no-shows/{caseId}`, `POST .../orders/{orderId}/no-show` |
| `surprisebox/controller/BoxController.kt` | MODIFIED | Bổ sung `GET .../boxes/awaiting-inspection` và `POST .../boxes/{boxId}/recheck` |
| `docs/api/openapi.yaml` | MODIFIED | Thêm tag `No-show`, 5 endpoints mới + schemas/examples/errors |
| `src/test/kotlin/.../NoShowTests.kt` | NEW | Integration tests theo TDD bao phủ AC-01..14 (job, đồng thời, idempotency, D06, `UC-35` gán đợt mới, quyền) |
| `src/test/kotlin/.../AuthMigrationTests.kt` | MODIFIED | Cập nhật kỳ vọng Flyway migration (`19` → `20`) |
| `src/test/kotlin/.../FoodRescueApplicationTests.kt` | MODIFIED | Cập nhật kỳ vọng Flyway migration (`19` → `20`) |
| `src/test/kotlin/.../SwaggerUiTests.kt` | MODIFIED | Cập nhật OpenAPI paths (`101` → `106`) và tags (`25` → `26`) |
| `docs/specs/t7-4-no-show/03-implementation-control.txt` | NEW | Báo cáo hoàn thành & kết quả kiểm chứng cuối task |
| `docs/specs/roadmap-3-9.md` | MODIFIED | Đánh dấu `[x] T7.4` khi hoàn thành |
| `.gitignore` | MODIFIED | Thêm `!docs/specs/t7-4-no-show/` khi hoàn thành |

---

## 6. Kế hoạch kiểm thử TDD (`NoShowTests.kt`)

Viết test trước (TDD), chạy toàn bộ Gradle build + kiểm tra `build/reports/tests/test/index.html` và XML suite:
1. **Job quá hạn (`AC-01`, `AC-02`):**
   - Đơn `CONFIRMED` trước `pickupEndsAt` không bị chuyển; qua `pickupEndsAt` chuyển `PENDING_NO_SHOW`; đơn có `PickupSession` `OPEN`/`RECONCILING` bị job bỏ qua; gọi đồng thời `markOverdue` không lỗi/trùng.
2. **Xem đơn quá hạn `UC-36` (`AC-03`, `AC-13`):**
   - Owner và staff `NO_SHOW_CONFIRM` xem được danh sách kèm `projectedAmounts`, `boxes`, `incidents`; lọc `date`/`status`, rỗng trả `200 []`, không sửa DB; staff chỉ có `BOX_MANAGE` hoặc cửa hàng khác nhận `403`.
3. **Xác nhận No-show `UC-37` (`AC-04`, `AC-05`, `AC-06`, `AC-07`, `AC-13`):**
   - Xác nhận hợp lệ kèm attachment → `NoShowCase(HELD)`, đơn `NO_SHOW`, vô hiệu mã nhận (`PickupCodeService.mine` trả `usable=false`), allocation `RELEASED`, hộp `AWAITING_INSPECTION`, `Notification(NO_SHOW, PENDING)`, `FinancialEvent(NO_SHOW_CONFIRMED)`.
   - Kiểm tra công thức `NoShowAmounts`: `retained + refund = verifiedPaid`, hoa hồng `0`, tạo đúng 1 `RefundRequest` (`PENDING`) nếu `refundAmount > 0` và `0` bản ghi nếu `refundAmount == 0` (vd retention 100%).
   - Chặn `409`: chưa qua `pickupEndsAt`, đơn `COMPLETED`/`CANCELLED`/`EXPIRED`, có phiên `OPEN`/`RECONCILING`, có `PickupIncident(customerArrived = true)` (bao gồm ghi incident UC-30 ngay trên đơn đang `PENDING_NO_SHOW`), `storeOpenedOnTime=false` hoặc `boxesReadyAndCompliant=false`.
   - Idempotency (`Idempotency-Key` lặp lại trả `200`, sai payload trả `409 IDEMPOTENCY_KEY_REUSED`, key khác trả `409 NO_SHOW_ALREADY_CONFIRMED`) và concurrency (xác nhận No-show chạy song song với nhau hoặc với hủy/bàn giao chỉ cho 1 thắng).
   - File đính kèm `NoShowAttachmentPolicy`: chủ đơn, owner/staff `NO_SHOW_CONFIRM`, `ADMIN` tải được; người khác `403`.
4. **D06 / 48h `notification_received_at` (`AC-08`):**
   - Khi `notificationReceivedAt == null`, dù `Clock` tua +72h, `releaseEligible` giữ nguyên `HELD` và `disputeDeadline == null`.
   - Sau khi `recordNotificationReceived(notifId, t)`, `disputeDeadline = t + 48h`; tại `t + 47h59m` vẫn `HELD`; tại `t + 48h` chuyển `ELIGIBLE` (trừ khi có `Complaint` đang mở).
5. **Kiểm tra lại hộp `UC-35` (`AC-09`, `AC-10`, `AC-11`, `AC-12`, `AC-13`):**
   - `FAILED` (bắt buộc notes) → `BoxInspection(FAILED)`, hộp `BLOCKED`.
   - `PASSED` không `targetBatchId` → `BoxInspection(PASSED)`, hộp giữ `AWAITING_INSPECTION`, `BatchAvailability.available == 0`.
   - `PASSED` với `targetBatchId` không đạt (`CLOSED`, quá `safeUntil`, dưới `minimumValue`, khác store) → `409`/`404`.
   - `PASSED` với `targetBatchId` đạt chuẩn cùng store → `BoxInspection(PASSED)`, `currentBatch = targetBatch`, `status = AVAILABLE`, `BatchAvailability.available` tăng +1, vẫn giữ nguyên lịch sử `BoxAllocation`/`BoxInspection`/`NoShowCase`.
   - Chặn hộp không ở `AWAITING_INSPECTION`, hoặc nhân viên chỉ có `NO_SHOW_CONFIRM` gọi `/recheck` (`403`).
6. **Contract & Migrations (`AC-14`):**
   - `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests` pass toàn bộ với `V20`, `106` paths và `26` tags.

---

## 7. Checklist an toàn & kiểm soát chất lượng

- Tiêm `Clock` cho mọi kiểm tra thời gian và bản ghi timestamp (tuyệt đối không dùng `Instant.now()` trực tiếp trong nghiệp vụ).
- Khóa bi quan đúng thứ tự `CustomerOrder` → `Payment` (tăng dần) → `BoxAllocation` / `PhysicalBox` (theo `box.id` tăng dần) / `SaleBatch` để tránh deadlock.
- Không trả lỗi stack trace hay dữ liệu đơn/hộp ngoài cửa hàng.
- Chỉ viết code sản phẩm và migration sau khi **Gate B** được người dùng duyệt.

## 8. Nhật ký triển khai
- Baseline BUILD SUCCESSFUL, up-to-date. Giữ các thay đổi fixture/evidence có sẵn của người dùng.
- Bổ sung hai property tắt job No-show trong TestAuthKeys để kiểm thử Clock trực tiếp, tương tự expiry job.

- Test TDD ban đầu đỏ đúng endpoint chưa tồn tại; compileKotlin sau triển khai thành công.
- Bổ sung NoShowCaseSummaryView.kt để tách DTO, các job dùng @Scheduled properties trực tiếp như OrderExpiryJob.
- Recheck khóa targetBatch trước PhysicalBox để cùng thứ tự với packing/placement, tránh đảo khóa batch/box.
- SaleBatchStatus hiện có ENDED (tương ứng đợt đã đóng trong test kế hoạch), không có CLOSED.
- File chứng cứ ngoài phạm vi trả 404 theo FileAccessService hiện có (test kế hoạch ghi 403); dùng chính sách nền hiện hữu.

- Targeted mở rộng BUILD SUCCESSFUL, 37/37 PASS (NoShowTests 18/18). PostgreSQL timestamp được truncate xuống micro giây để không quét sớm ở biên nanosecond; lỗi persistence được flush qua repository để ánh xạ 409.

## 9. Checklist thực hiện
- [x] Migration V20, entity và repository.
- [x] Test TDD trước code, bổ sung kiểm thử nghiệp vụ AC-01..13.
- [x] DTO, overdue job, confirm service, attachment policy, compensation receipt/job.
- [x] Recheck service, controller và tích hợp incident PENDING_NO_SHOW.
- [x] OpenAPI, kỳ vọng migration/entity/Swagger, cấu hình test tắt job, whitelist spec.
- [x] Kiểm thử targeted mở rộng.
- [x] Full build, đọc báo cáo và đóng T7.4 trong roadmap.

- Full build cuối BUILD SUCCESSFUL trong 9m56s, exit code 0. 277 tests: 276 PASS, 0 FAIL/ERROR, 1 SKIP có sẵn. NoShowTests 18/18 PASS; đã đọc HTML và XML từng suite.
