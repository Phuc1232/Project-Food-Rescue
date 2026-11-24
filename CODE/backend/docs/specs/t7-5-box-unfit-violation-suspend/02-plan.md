# Technical Plan: T7.5 — Hộp không đạt, ngừng bán vi phạm, khóa cửa hàng có đơn tồn

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A đã được người dùng duyệt `Q1`–`Q5` ngày 2026-11-24.  
**Status:** DONE — triển khai và kiểm chứng hoàn tất ngày 2026-11-24  
**Updated:** 2026-11-24  
**Scope:** T7.5 (`UC-34` đánh dấu hộp không đạt, `UC-78` Admin ngừng bán hộp/đợt vi phạm, `UC-77` bước 3–5/`A1`/`E1`/`E2` khóa cửa hàng có đơn tồn & rà đợt có đơn, `BR-20`, `BR-54`, `BR-55`, `SD-08`, `BoxAttachmentPolicy`, `ComplaintAttachmentPolicy`).  
**Phụ thuộc:** T7.4 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Quyết định đã duyệt tại Gate A và Baseline

1. **Các quyết định Gate A đã chốt (`Q1`–`Q5`):**
   - **Q1 (API endpoints):**
     - `POST /api/v1/stores/{storeId}/boxes/{boxId}/unfit` (`BOX_MANAGE` hoặc Store Owner, header `Idempotency-Key` tùy chọn) → `BoxImpactResultView` (`UC-34`).
     - `POST /api/v1/admin/boxes/{boxId}/block` (`ADMIN`, header `Idempotency-Key` tùy chọn) → `BoxImpactResultView` (`UC-78` mức hộp, dùng chung lõi xử lý).
     - Mở rộng `POST /api/v1/admin/sale-batches/{batchId}/block` (`ADMIN`, hiện có từ T4.3) để chặn mọi hộp chưa giao trong đợt (`BLOCKED`), hủy/hoàn đơn tồn và ghi sự cố hậu giao nếu có, giữ nguyên cấu trúc `SaleBatchView` để tương thích hoàn toàn T4.3 `SaleBatchTests`.
   - **Q2 (Hộp đã giao `DELIVERED` / đơn `COMPLETED`):**
     - Giữ `box.status = DELIVERED` (không đưa lại vào tồn kho), ghi `BoxInspection(FAILED)`, tạo `Complaint(store, order, customer, type = FOOD_SAFETY, status = OPEN, sourceBox = box)` chuyển `T8.1`, **không** hủy đơn và **không** tạo `RefundRequest` lần nữa (`UC-34 E1`, `UC-78 A1`, `SD-08` #07–10). Trả `200 BoxImpactResultView` có `complaintId`.
   - **Q3 (Mở rộng `POST /api/v1/admin/stores/{storeId}/status` cho `UC-77`):**
     - Bổ sung `existingOrderHandling: StoreSuspendOrderHandling?` (`CONTINUE_IF_SAFE`, `CANCEL_ALL`) vào `StoreStatusRequest` và `suspensionOrderSummary: StoreSuspensionOrderSummary? = null` vào `AdminStoreDetail`.
     - Khi `SUSPEND` mà cửa hàng có ≥ 1 đơn tồn (`PENDING_PAYMENT`, `CONFIRMED`, `HANDOVER_IN_PROGRESS`, `PENDING_NO_SHOW`) và `existingOrderHandling == null` → từ chối `409 EXISTING_ORDERS_DECISION_REQUIRED` (`UC-77 E1`).
     - Giữ đơn đã xác nhận chỉ khi `CONTINUE_IF_SAFE` + hộp còn an toàn (`!BLOCKED`, `safeUntil > now`, `safeUntil >= pickupEndsAt`) + còn người bàn giao hợp lệ (`Owner ACTIVE verified` hoặc Staff `ACTIVE verified` có `PICKUP_HANDLE` — `UC-77 E2`); còn lại hủy lỗi cửa hàng (`storeFault = true`, hoàn 100% `verifiedPaid`). `REACTIVATE` không mở lại đợt `BLOCKED`/`ENDED` hay hộp `BLOCKED` (`UC-77 A1`).
   - **Q4 (Attachment policies):** Thêm `BoxAttachmentPolicy` và `ComplaintAttachmentPolicy`.
   - **Q5 (Migration `V21`):** Tạo `V21__box_unfit_idempotency_and_complaint_source.sql`, nâng số migration `20 -> 21`, giữ nguyên số entity `43`.
2. **Baseline:**
   - `./gradlew.bat build --console=plain`: **BUILD SUCCESSFUL** (`276 PASS, 0 FAIL, 1 SKIPPED` có sẵn; `20` migrations `V1`–`V20`; `43` entities; `106` OpenAPI paths / `26` tags).

---

## 2. Thiết kế chi tiết

### 2.1. Migration `V21__box_unfit_idempotency_and_complaint_source.sql` & Entities

- Bổ sung vào bảng `physical_box`:
  - `unfit_idempotency_key varchar(255)` (nullable)
  - `unfit_fingerprint text` (nullable)
  - `unfit_result_json text` (nullable — lưu ảnh chụp `BoxImpactResultView` để replay chính xác `200 OK` sau khi các đơn đã đổi trạng thái)
  - `CREATE UNIQUE INDEX uq_physical_box_unfit_idempotency ON physical_box(id, unfit_idempotency_key) WHERE unfit_idempotency_key IS NOT NULL;`
- Bổ sung vào bảng `complaint`:
  - `source_box_id uuid REFERENCES physical_box(id)` (nullable — phân biệt khiếu nại phát sinh từ hộp hậu giao T7.5 với khiếu nại khách hàng mở ở T8.1)
  - `idempotency_key varchar(255)` (nullable)
  - `CREATE INDEX idx_complaint_source_box ON complaint(source_box_id) WHERE source_box_id IS NOT NULL;`
  - `CREATE UNIQUE INDEX uq_complaint_open_source_box ON complaint(source_box_id) WHERE source_box_id IS NOT NULL AND status IN ('OPEN', 'INVESTIGATING', 'AWAITING_EVIDENCE');`
  - `CREATE UNIQUE INDEX uq_complaint_source_box_key ON complaint(source_box_id, idempotency_key) WHERE source_box_id IS NOT NULL AND idempotency_key IS NOT NULL;`
- Cập nhật entity [`PhysicalBox`](../../src/main/kotlin/com/foodrescue/backend/surprisebox/entity/PhysicalBox.kt) và [`Complaint`](../../src/main/kotlin/com/foodrescue/backend/complaint/entity/Complaint.kt) thêm các trường nullable tương ứng (có default `= null`). Không thêm entity mới (giữ nguyên `43` entities, tương thích hoàn toàn cơ chế tự sinh fixture trong `FoodRescueApplicationTests`).

### 2.2. Thứ tự khóa toàn cục và xử lý tranh chấp đồng thời (`UC-34 E2`, `SD-08`)

- **Thứ tự khóa toàn cục bất biến trên toàn hệ thống:**  
  `Store` → `SaleBatch` → `CustomerOrder (order.id ASC)` → `Payment (payment.id ASC)` → `BoxAllocation` → `PhysicalBox (box.id ASC)`
- **Cơ chế chống deadlock & bắt kịp trạng thái mới nhất trong `BoxUnfitService`:**
  - Giao dịch chạy qua `TransactionTemplate` (tối đa 3 lần thử cho trường hợp cực hiếm có `OrderPlacementService` chen vào giữa bước đọc danh sách đơn và khóa `PhysicalBox`):
    1. Đọc khóa `batchId` của `boxId` (`SaleBatch` `FOR SHARE`) và tập `candidateOrderIds` từ `box_allocation` có `status IN ('HELD', 'CONFIRMED', 'DELIVERED')` sắp xếp `order.id ASC`.
    2. Khóa từng `CustomerOrder` trong `candidateOrderIds` theo thứ tự `id ASC` (`orders.findForUpdate`). Nếu đang có `PickupSessionService.completeSession` chạy song song, luồng này tuần tự hóa ngay trên khóa `CustomerOrder`!
    3. Khóa `PhysicalBox` (`boxes.findForUpdate(boxId)`).
    4. Kiểm tra lại dưới khóa toàn bộ `allocations` đang `HELD`/`CONFIRMED`/`DELIVERED` của `boxId`:
       - Nếu phát hiện vừa có thêm `orderId` mới chưa nằm trong `candidateOrderIds` (chỉ có thể xảy ra nếu `OrderPlacementService` vừa hoàn tất đặt đơn cho hộp này ngay trước bước 3): ném ngoại lệ nội bộ nhẹ để rollback giải phóng khóa và lặp lại vòng thử với danh sách `orderIds` đầy đủ (giữ tuyệt đối đúng thứ tự khóa `CustomerOrder -> PhysicalBox`).
       - Ngược lại, gọi `em.refresh(order)` nếu cần và đọc trạng thái mới nhất của `box` và `order` (`SD-08` #05–06) để phân nhánh chuẩn xác:
         - Nếu `PickupSessionService.completeSession` thắng khóa `CustomerOrder` trước → `BoxUnfitService` thấy `box.status == DELIVERED` / `order.status == COMPLETED` → rẽ vào **Nhánh A** (khiếu nại hậu giao), không hủy/hoàn trùng.
         - Nếu `BoxUnfitService` thắng khóa `CustomerOrder` trước → hủy `PickupSession` & chuyển `box = BLOCKED`, `order = CANCELLED` → `completeSession` thức dậy gọi `em.refresh(session)` thấy `CANCELLED` và từ chối `409 INVALID_ORDER_STATUS`.

### 2.3. Lõi xử lý hộp không đạt / vi phạm (`BoxUnfitProcessor` & `BoxUnfitService`)

Dùng chung cho **3 luồng**: `UC-34` (`POST /api/v1/stores/{storeId}/boxes/{boxId}/unfit`), `UC-78` mức hộp (`POST /api/v1/admin/boxes/{boxId}/block`), và `UC-78` mức đợt (`POST /api/v1/admin/sale-batches/{batchId}/block`):

- **Validate đầu vào & Idempotency (mức hộp):**
  - Validate `Idempotency-Key` (nếu có): `^[A-Za-z0-9_-]{1,255}$` (else `400 VALIDATION_FAILED`).
  - Fingerprint: JSON canonical của `[operation ("STORE_BOX_UNFIT" | "ADMIN_BOX_BLOCK"), boxId, reason.trim(), notes?.trim(), attachmentIds.distinct().sorted()]`.
  - Sau khi khóa `PhysicalBox`: nếu `key != null && box.unfitIdempotencyKey == key`:
    - Nếu `box.unfitFingerprint != fingerprint` → `409 IDEMPOTENCY_KEY_REUSED`.
    - Deserialize `box.unfitResultJson` trả `BoxImpactResultView` (`200 OK`) — không tạo trùng `BoxInspection`, `RefundRequest`, `Complaint`.
- **Validate file đính kèm (`attachmentIds`):**
  - Chỉ cho phép file do chính `actorId` upload (`StoredFile` khóa `PESSIMISTIC_WRITE`) và chưa gắn vào bảng `attachment` (`400 VALIDATION_FAILED` nếu không hợp lệ / thuộc người khác / đã gắn).
  - Ràng buộc DB `V19` (`num_nonnulls(...) = 1`) bắt buộc mỗi `Attachment` chỉ trỏ tới **đúng 1** chủ sở hữu: gắn vào `complaint` ở **Nhánh A** và gắn vào `box` ở **Nhánh B**.
- **Nhánh A — Hộp đã giao (`box.status == BoxStatus.DELIVERED` hoặc allocation gắn với đơn `OrderStatus.COMPLETED`):**
  - Nếu đã có `Complaint` với `sourceBox.id == boxId` ở trạng thái mở (`OPEN`, `INVESTIGATING`, `AWAITING_EVIDENCE`) → `409 POST_DELIVERY_INCIDENT_ALREADY_OPEN` (chống tạo trùng khi gọi với key khác/không key).
  - Giữ `box.status = BoxStatus.DELIVERED` (`UC-34 E1`, `UC-78 A1`, `SD-08` #07–10).
  - Ghi bất biến `BoxInspection(box, actor, now, InspectionResult.FAILED, box.safeUntil, inspectionNotes)`.
  - Tạo `Complaint(store = box.store, order = order, customer = order.customer, type = ComplaintType.FOOD_SAFETY, description = ..., status = ComplaintStatus.OPEN, sourceBox = box, idempotencyKey = key)`.
  - Gắn `attachmentIds` vào `Attachment(file, complaint = savedComplaint)`.
  - Ghi `AuditLog` (`BOX_POST_DELIVERY_INCIDENT_RECORDED`) và lưu vết idempotency trên `box` (nếu có `key`).
- **Nhánh B — Hộp chưa giao (`AVAILABLE`, `RESERVED`, `ALLOCATED`, `AWAITING_INSPECTION`):**
  - Nếu `box.status == BoxStatus.BLOCKED` (khi gọi trực tiếp mức hộp và không còn đơn hoạt động nào liên quan) → `409 BOX_ALREADY_BLOCKED`.
  - Đặt `box.status = BoxStatus.BLOCKED`, ghi bất biến `BoxInspection(box, actor, now, InspectionResult.FAILED, box.safeUntil, inspectionNotes)`, gắn `attachmentIds` vào `Attachment(file, box = box)`.
  - Với các đơn chưa giao đang giữ/phân bổ hộp (`allocation.status in [HELD, CONFIRMED]`):
    - **`OrderStatus.PENDING_PAYMENT`:** gọi `OrderReleaser.release(order, OrderStatus.CANCELLED, cancelReason, actor, "ORDER_CANCELLED_BOX_UNFIT")`. Vì `box.status` đã set `BLOCKED`, `OrderReleaser` chỉ trả các hộp an toàn khác của đơn về `AVAILABLE` và giữ hộp lỗi ở `BLOCKED`. Webhook thanh toán đến muộn sau này tiếp tục được `PaymentConfirmationService` (T6.4) xử lý thành `REQUIRES_REVIEW` + `RefundRequest(ORDER_CANCELLED)` (`BR-26`).
    - **`OrderStatus.CONFIRMED`, `OrderStatus.HANDOVER_IN_PROGRESS`, `OrderStatus.PENDING_NO_SHOW`:** gọi `PaidOrderCancellationExecutor.forceCancelStoreFault(...)` (xem mục 2.4):
      1. Nếu đơn đang có `PickupSession` `OPEN` hoặc `RECONCILING` → chuyển `session.status = PickupStatus.CANCELLED`.
      2. Hủy đơn theo lỗi cửa hàng (`OrderStatus.CANCELLED`, `storeFault = true`, xóa `pickupCodeHash/Encrypted/KeyId = null`, giải phóng toàn bộ `BoxAllocation` → `RELEASED`, hộp thuộc `unsafeBoxIds` hoặc đã `BLOCKED` giữ `BLOCKED`, các hộp đạt khác của đơn trả về `AVAILABLE`).
      3. Đóng `OrderCancellationRequest` đang mở (`SUBMITTED`/`NEEDS_INFO`, nếu có) thành `APPROVED` (`storeFault = true`), tạo mới `OrderCancellationRequest(initiator = STORE, status = APPROVED, storeFault = true)` (nếu chưa có bản ghi do lỗi hệ thống/đơn) và tạo `RefundRequest(PENDING, reasonCode = "STORE_FAULT")` cho 100% `verifiedPaid` (nếu `> 0`), hoa hồng `0`.
  - Ghi `AuditLog` (`BOX_MARKED_UNFIT` cho UC-34, `ADMIN_BOX_BLOCKED` cho UC-78) và lưu `unfitIdempotencyKey`, `unfitFingerprint`, `unfitResultJson` (khi `key != null`).

### 2.4. Mở rộng `PaidOrderCancellationExecutor.forceCancelStoreFault(...)`

- Bổ sung phương thức nội bộ trong [`PaidOrderCancellationExecutor`](../../src/main/kotlin/com/foodrescue/backend/order/service/PaidOrderCancellationExecutor.kt) (`@Transactional(propagation = Propagation.MANDATORY)`, caller đã giữ khóa `CustomerOrder`):
  - Chấp nhận `order.status in [CONFIRMED, HANDOVER_IN_PROGRESS, PENDING_NO_SHOW]` (khác với T7.3 `requireCancellable` giữ nguyên cho luồng khách/chủ đơn hủy thông thường để **không** làm đổi hành vi T7.3).
  - Tìm `PickupSession` `OPEN`/`RECONCILING` nếu có → đặt `session.status = PickupStatus.CANCELLED` và `sessions.saveAndFlush(session)`.
  - Vô hiệu hóa mã nhận (`order.pickupCodeHash = null; order.pickupCodeEncrypted = null; order.pickupCodeKeyId = null` — tuân thủ `ck_order_pickup_code_storage` & `V18`).
  - Gọi `execute(order, actor, verifiedPaid, reasonCode = "STORE_FAULT", reason = cancelReason, blockAll = blockAll, unsafeIds = unsafeIds)` để giải phóng/chặn hộp, ghi `RefundRequest(PENDING)` 100% `verifiedPaid` và audit `ORDER_CANCELLED`.
  - Nếu có `OrderCancellationRequest` đang ở trạng thái mở (`SUBMITTED`/`NEEDS_INFO`) trên `order.id`, cập nhật thành `APPROVED` (`storeFault = true`, `approvedRefundAmount = verifiedPaid`, `refundRequest = refund`, `reviewer = actor`, `reviewedAt = now`) và tạo/gắn `OrderCancellationRequest(STORE, APPROVED, storeFault = true)` để đồng nhất báo cáo T7.3.

### 2.5. Mở rộng ngừng bán đợt vi phạm (`SaleBatchLifecycleService.block` — `UC-78`, `BR-54`)

- Mở rộng `SaleBatchBlockRequest`: giữ `@field:NotBlank val reason: String?` và thêm trường tùy chọn `val includeDeliveredBoxesIncident: Boolean = true`.
- Trong `SaleBatchLifecycleService.block(adminId, batchId, request)`:
  1. Khóa `SaleBatch` (`batches.findForUpdate(batchId)`), kiểm tra `batch.status !in [ENDED, BLOCKED]` (else `409 CONFLICT`).
  2. Đặt `batch.status = SaleBatchStatus.BLOCKED`, `batch.statusReason = reason`.
  3. Tìm tất cả `CustomerOrder` liên quan đến các hộp trong đợt hoặc trực tiếp thuộc đợt (`sale_batch_id = batchId`) đang ở `[PENDING_PAYMENT, CONFIRMED, HANDOVER_IN_PROGRESS, PENDING_NO_SHOW, COMPLETED]`, khóa `CustomerOrder` theo `id ASC`, rồi khóa toàn bộ `PhysicalBox` thuộc `currentBatch.id == batchId` (và các hộp thuộc các đơn trên) theo `box.id ASC`.
  4. Chặn toàn bộ hộp chưa giao trong đợt (`status !in [BLOCKED, DELIVERED]`) → `BLOCKED` + ghi `BoxInspection(FAILED)` (bỏ qua hộp đã `BLOCKED` trước đó để không gây lỗi 409 khi chặn cả đợt).
  5. Hủy toàn bộ đơn chưa giao trong đợt qua lõi xử lý (`PENDING_PAYMENT` → `OrderReleaser`; `CONFIRMED`/`HANDOVER_IN_PROGRESS`/`PENDING_NO_SHOW` → `forceCancelStoreFault` với `unsafeBoxIds` là tập hộp trong đợt).
  6. Nếu `request.includeDeliveredBoxesIncident == true`: với mỗi hộp `DELIVERED` thuộc đợt chưa có khiếu nại mở, ghi `BoxInspection(FAILED)` + mở `Complaint(FOOD_SAFETY, OPEN)` chuyển T8.1 (`UC-78 A1`), không hủy/hoàn đơn `COMPLETED`.
  7. Ghi `AuditLog(SALE_BATCH_BLOCKED)` kèm thống kê `blockedBoxes`, `cancelledUnpaidOrders`, `cancelledConfirmedOrders`, `createdComplaints`, và trả về `SaleBatchView` giữ nguyên cấu trúc cũ (tương thích 100% T4.3 `SaleBatchTests`).

### 2.6. Mở rộng khóa cửa hàng có đơn tồn (`StoreStatusService` — `UC-77` bước 3–5, `A1`, `E1`, `E2`; `BR-55`)

- Mở rộng `StoreStatusRequest`:
  - `enum class StoreSuspendOrderHandling { CONTINUE_IF_SAFE, CANCEL_ALL }`
  - `data class StoreStatusRequest(@field:NotNull val action: StoreStatusAction?, @field:NotBlank @field:Size(max = 2000) val reason: String?, val existingOrderHandling: StoreSuspendOrderHandling? = null)`
- Mở rộng `AdminStoreDetail`: thêm `val suspensionOrderSummary: StoreSuspensionOrderSummary? = null` (chỉ có giá trị khi vừa thực thi `SUSPEND`, các truy vấn khác/khi `REACTIVATE` để `null`).
- Luồng `StoreStatusService.change(adminId, storeId, request)`:
  1. Khóa `Store` (`stores.findForUpdate(storeId)`), kiểm tra quyền (`adminId != store.owner.id`, `from -> to` hợp lệ).
  2. **Khi `REACTIVATE` (`UC-77 A1`):** chuyển `store.status = ACTIVE`, `statusReason = null`, ghi `AuditLog(STORE_REACTIVATED)` — không tự động mở lại `SaleBatch(BLOCKED/ENDED)` hay `PhysicalBox(BLOCKED)`.
  3. **Khi `SUSPEND` (`UC-77` bước 3–5, `E1`, `E2`):**
     - Tìm các đơn tồn chưa kết thúc của cửa hàng (`PENDING_PAYMENT`, `CONFIRMED`, `HANDOVER_IN_PROGRESS`, `PENDING_NO_SHOW`).
     - Nếu danh sách đơn tồn **không rỗng** và `request.existingOrderHandling == null` → ném `ApiException(409, "EXISTING_ORDERS_DECISION_REQUIRED", "Store has existing open orders; specify existingOrderHandling (CONTINUE_IF_SAFE or CANCEL_ALL)")` (`UC-77 E1`).
     - Chuyển `store.status = SUSPENDED`, `store.statusReason = reason`, `store.statusChangedAt = now`, `stores.saveAndFlush(store)`.
     - Đánh giá người có quyền bàn giao (`UC-77 E2`):
       `hasAuthorizedHandoverActor = (store.owner.status == AccountStatus.ACTIVE && store.owner.emailVerifiedAt != null) || existsActiveVerifiedStaffWithPermission(storeId, "PICKUP_HANDLE")`
     - Khóa tất cả `SaleBatch` của cửa hàng theo `id ASC`, rồi khóa tất cả đơn tồn theo `order.id ASC` (đúng thứ tự toàn cục `Store -> SaleBatch -> CustomerOrder -> Payment -> PhysicalBox`).
     - Xử lý từng đơn tồn:
       - **`PENDING_PAYMENT`:** hủy `CANCELLED` (`reason = "STORE_SUSPENDED: $reason"`), nhả hộp qua `OrderReleaser`.
       - **`CONFIRMED` / `HANDOVER_IN_PROGRESS` / `PENDING_NO_SHOW`:**
         - Kiểm tra điều kiện giữ đơn an toàn:
           `handling == CONTINUE_IF_SAFE && hasAuthorizedHandoverActor && order.statusReason?.startsWith("HANDOVER_INCIDENT:") != true` AND mọi `PhysicalBox` thuộc `BoxAllocation` active (`HELD`/`CONFIRMED`) thỏa `box.status != BoxStatus.BLOCKED && box.safeUntil.isAfter(now) && !box.safeUntil.isBefore(order.pickupEndsAt)`.
         - Nếu **đạt**: giữ đơn nguyên trạng (`retainedOrderIds`), ghi nhận đợt của đơn thuộc nhóm cần giữ `PAUSED`.
         - Nếu **không đạt** (`CANCEL_ALL`, hết an toàn/quá `safeUntil`, không còn người bàn giao `UC-77 E2`, hoặc kẹt `HANDOVER_INCIDENT`): gọi `forceCancelStoreFault` hủy lỗi cửa hàng + hoàn 100% `verifiedPaid` (các hộp không an toàn/quá hạn `box.safeUntil <= now || box.safeUntil < order.pickupEndsAt` chuyển `BLOCKED`; hộp đạt về `AVAILABLE`, không bán mới được vì cửa hàng đã `SUSPENDED`).
     - Rà soát đợt bán (`BR-55`):
       - Gọi `SaleBatchBlocker.blockBatchesWithoutOrders(storeId)` → `BLOCKED` cho đợt `DRAFT`/`OPEN`/`PAUSED` chưa từng có đơn.
       - Với các đợt còn lại ở `DRAFT`/`OPEN`/`PAUSED` đã có đơn:
         - Nếu đợt còn ≥ 1 đơn tồn được giữ lại (`batch.id in retainedBatchIds`) → chuyển sang `PAUSED` với `statusReason = "Store suspended"` (nếu chưa `PAUSED`), ngừng nhận đơn mới nhưng vẫn giữ đợt phục vụ các đơn còn lại.
         - Nếu đợt không còn đơn tồn nào tiếp tục → chuyển sang `BLOCKED` với `statusReason = "Store suspended"`.
     - Ghi `AuditLog(STORE_SUSPENDED)` kèm thống kê chi tiết (`blockedSaleBatches`, `pausedSaleBatches`, `cancelledUnpaidOrders`, `retainedConfirmedOrders`, `cancelledConfirmedOrders`, `hasAuthorizedHandoverActor`).

> **Lý do cập nhật test T3.3 (`StoreStatusTests.kt`) theo `.agents/CLAUDE.md` §1:**  
> Như ghi chú chuyển giao trong `roadmap-3-9.md` mục T7.5 (*"Lưu ý từ T3.3: API khóa mới chỉ bắt buộc lý do; T7.5 thêm phương án xử lý đơn tồn (UC-77 bước 3, E1) và rà đợt có đơn (T3.3 để nguyên trạng thái)"*), `StoreStatusTests.kt` hiện gọi `setStatus(..., "SUSPEND")` **không** truyền `existingOrderHandling` ngay cả khi vừa tạo đơn `CONFIRMED` (`AC-09` dòng 21 và `AC-14` dòng 93) và kỳ vọng `withOrder` giữ nguyên `OPEN` (`AC-09` dòng 24). Khi T7.5 thực thi đầy đủ `UC-77 E1` và `BR-55`, cần cập nhật 2 test này để truyền `existingOrderHandling = CONTINUE_IF_SAFE` (và kiểm chứng `withOrder` chuyển sang `PAUSED`, còn trường hợp thiếu phương án trả `409 EXISTING_ORDERS_DECISION_REQUIRED`).

### 2.7. Phân quyền file đính kèm (`AttachmentAccessPolicy`)

- **`BoxAttachmentPolicy` (`surprisebox/service/BoxAttachmentPolicy.kt`):**
  - `supports(attachment) = attachment.box != null`
  - `canRead(accountId, attachment)`: `ADMIN`, hoặc trong cửa hàng của hộp (`attachment.box!!.store.id`) là Owner hoặc Staff `ACTIVE` có quyền `BOX_PACK` hoặc `BOX_MANAGE`.
- **`ComplaintAttachmentPolicy` (`complaint/service/ComplaintAttachmentPolicy.kt`):**
  - `supports(attachment) = attachment.complaint != null`
  - `canRead(accountId, attachment)`: khách hàng chủ đơn (`complaint.customer.id == accountId`), chủ cửa hàng (`complaint.store.owner.id == accountId`), hoặc `ADMIN`. (Người upload file ban đầu như nhân viên `BOX_MANAGE` vẫn được đọc qua cơ chế nền của `FileAccessService`).

---

## 3. API Contract (`docs/api/openapi.yaml`)

- Bổ sung **2** đường dẫn mới (nâng tổng số path từ `106` → `108`) và cập nhật **2** đường dẫn hiện có, giữ nguyên `26` tags (`Boxes`, `Sale batches`, `Admin stores`):

| Method / Path | Quyền & Request | Response | Tag |
|---|---|---|---|
| `POST /api/v1/stores/{storeId}/boxes/{boxId}/unfit` | Owner hoặc `BOX_MANAGE`; header `Idempotency-Key` (tùy chọn); body `BoxUnfitRequest(reason, notes, attachmentIds)` | `200 BoxImpactResultView`; `400, 401, 403, 404, 409` | `Boxes` |
| `POST /api/v1/admin/boxes/{boxId}/block` | `ADMIN`; header `Idempotency-Key` (tùy chọn); body `BoxUnfitRequest(reason, notes, attachmentIds)` | `200 BoxImpactResultView`; `400, 401, 403, 404, 409` | `Boxes` |
| `POST /api/v1/admin/sale-batches/{batchId}/block` *(Cập nhật mô tả & request)* | `ADMIN`; body `SaleBatchBlockRequest(reason, includeDeliveredBoxesIncident = true)` | `200 SaleBatchView`; `400, 401, 403, 404, 409` | `Sale batches` |
| `POST /api/v1/admin/stores/{storeId}/status` *(Cập nhật request/response)* | `ADMIN` (khác owner); body `StoreStatusRequest(action, reason, existingOrderHandling?)` | `200 AdminStoreDetail` (có `suspensionOrderSummary`); `400, 401, 403, 404, 409` | `Admin stores` |

---

## 4. Migration và Database Schema

- File migration mới: `src/main/resources/db/migration/V21__box_unfit_idempotency_and_complaint_source.sql`
  - Mở rộng `physical_box`: `unfit_idempotency_key`, `unfit_fingerprint`, `unfit_result_json` + unique index `uq_physical_box_unfit_idempotency`.
  - Mở rộng `complaint`: `source_box_id` FK `physical_box(id)`, `idempotency_key` + partial unique indexes `uq_complaint_open_source_box`, `uq_complaint_source_box_key`.
- Không sửa bất kỳ file migration nào từ `V1`–`V20`.
- Số lượng Hibernate entity giữ nguyên (`43`); số migration tăng `20` → `21`.

---

## 5. File Layout dự kiến

Đường dẫn Kotlin tương đối dưới `src/main/kotlin/com/foodrescue/backend/`:

| File | Loại | Trách nhiệm |
|---|---|---|
| `src/main/resources/db/migration/V21__box_unfit_idempotency_and_complaint_source.sql` | NEW | Thêm cột idempotency/snapshot trên `physical_box` và `source_box_id`/`idempotency_key` + partial unique index trên `complaint` |
| `surprisebox/entity/PhysicalBox.kt` | MODIFIED | Map `unfitIdempotencyKey`, `unfitFingerprint`, `unfitResultJson` |
| `complaint/entity/Complaint.kt` | MODIFIED | Map `sourceBox`, `idempotencyKey` |
| `complaint/repository/ComplaintRepository.kt` | MODIFIED | Thêm `findFirstBySourceBoxIdAndStatusInOrderByCreatedAtDesc`, `findBySourceBoxIdAndIdempotencyKey` |
| `surprisebox/repository/PhysicalBoxRepository.kt` | MODIFIED | Thêm `findAllByCurrentBatchIdOrderById`, `findForUpdateByIds(ids)` |
| `surprisebox/repository/SaleBatchRepository.kt` | MODIFIED | Thêm lock `FOR SHARE` theo id & khóa tất cả batch của store theo `id ASC` |
| `order/repository/CustomerOrderRepository.kt` | MODIFIED | Thêm query tìm đơn tồn theo `storeId` / `batchId` |
| `order/service/PaidOrderCancellationExecutor.kt` | MODIFIED | Bổ sung `forceCancelStoreFault(...)` hủy phiên bàn giao mở, hủy mã nhận, xử lý `PENDING_NO_SHOW` và đóng request mở (nếu có) |
| `surprisebox/dto/BoxUnfitRequest.kt` | NEW | DTO `reason`, `notes`, `attachmentIds` cho UC-34 & UC-78 mức hộp |
| `surprisebox/dto/BoxImpactResultView.kt` | NEW | DTO kết quả `BoxDetail`, `outcome`, danh sách/số đơn hủy, `refundRequestIds`, `complaintId`, `releasedSafeBoxIds` |
| `surprisebox/dto/SaleBatchBlockRequest.kt` | MODIFIED | Thêm `includeDeliveredBoxesIncident: Boolean = true` |
| `store/StoreSuspendOrderHandling.kt` | NEW | Enum `CONTINUE_IF_SAFE`, `CANCEL_ALL` |
| `store/dto/StoreStatusRequest.kt` | MODIFIED | Thêm `existingOrderHandling: StoreSuspendOrderHandling? = null` |
| `store/dto/StoreSuspensionOrderSummary.kt` | NEW | DTO thống kê xử lý đơn tồn & đợt bán khi `SUSPEND` |
| `store/dto/AdminStoreDetail.kt` | MODIFIED | Thêm `suspensionOrderSummary: StoreSuspensionOrderSummary? = null` |
| `surprisebox/service/BoxUnfitProcessor.kt` | NEW | Lõi xử lý hộp chưa giao (`BLOCKED`, hủy/hoàn đơn, nhả hộp khác) & hộp đã giao (`Complaint(FOOD_SAFETY, OPEN)`) dùng chung cho UC-34, UC-78, UC-77 |
| `surprisebox/service/BoxUnfitService.kt` | NEW | Service `markStoreBoxUnfit` (`UC-34`) & `blockBoxByAdmin` (`UC-78`) với vòng khóa toàn cục chống deadlock & idempotency |
| `surprisebox/service/BoxAttachmentPolicy.kt` | NEW | `AttachmentAccessPolicy` cho `attachment.box` |
| `complaint/service/ComplaintAttachmentPolicy.kt` | NEW | `AttachmentAccessPolicy` cho `attachment.complaint` |
| `surprisebox/controller/BoxController.kt` | MODIFIED | Thêm `POST /api/v1/stores/{storeId}/boxes/{boxId}/unfit` |
| `surprisebox/controller/AdminBoxController.kt` | NEW | Thêm `POST /api/v1/admin/boxes/{boxId}/block` |
| `surprisebox/controller/AdminSaleBatchController.kt` | MODIFIED | Truyền đầy đủ `SaleBatchBlockRequest` sang `SaleBatchLifecycleService.block` |
| `surprisebox/service/SaleBatchLifecycleService.kt` | MODIFIED | Mở rộng `block` chặn hộp & xử lý đơn tồn/hậu giao trong đợt qua `BoxUnfitProcessor` |
| `store/service/StoreStatusService.kt` | MODIFIED | Mở rộng `change` kiểm tra đơn tồn (`409`), giữ đơn an toàn có người bàn giao (`PICKUP_HANDLE`/Owner), hủy đơn không đạt & rà đợt `PAUSED`/`BLOCKED` |
| `docs/api/openapi.yaml` | MODIFIED | Thêm 2 endpoint mới (`106` → `108` paths), cập nhật `/admin/sale-batches/{batchId}/block` & `/admin/stores/{storeId}/status`, schemas/examples |
| `src/test/kotlin/.../BoxUnfitViolationSuspendTests.kt` | NEW | Integration test TDD cho toàn bộ `AC-01`..`AC-12` |
| `src/test/kotlin/.../StoreStatusTests.kt` | MODIFIED | Cập nhật `AC-09` & `AC-14` theo yêu cầu bắt buộc `existingOrderHandling` của `UC-77` |
| `src/test/kotlin/.../AuthMigrationTests.kt` | MODIFIED | Cập nhật số migration `20` → `21` |
| `src/test/kotlin/.../FoodRescueApplicationTests.kt` | MODIFIED | Cập nhật số migration `20` → `21` |
| `src/test/kotlin/.../SwaggerUiTests.kt` | MODIFIED | Cập nhật OpenAPI paths `106` → `108` (`tags` giữ nguyên `26`) |
| `docs/specs/t7-5-box-unfit-violation-suspend/03-implementation-control.txt` | NEW | Báo cáo kết quả kiểm chứng cuối task |
| `docs/specs/roadmap-3-9.md` | MODIFIED | Đánh dấu `[x] T7.5` khi hoàn tất |
| `.gitignore` | MODIFIED | Thêm `!docs/specs/t7-5-box-unfit-violation-suspend/` khi hoàn tất |

---

## 6. Task Breakdown

- [x] **T7.5-01:** Tạo migration `V21__box_unfit_idempotency_and_complaint_source.sql` và cập nhật entity `PhysicalBox`, `Complaint` cùng các phương thức truy vấn/khóa trên repository (`PhysicalBoxRepository`, `SaleBatchRepository`, `CustomerOrderRepository`, `ComplaintRepository`).
- [x] **T7.5-02 (TDD):** Viết trước bộ integration test `BoxUnfitViolationSuspendTests.kt` bao phủ `AC-01`..`AC-11` (chạy xác nhận đỏ do chưa có endpoint/behavior mới).
- [x] **T7.5-03:** Mở rộng `PaidOrderCancellationExecutor.forceCancelStoreFault` và xây dựng lõi `BoxUnfitProcessor` + `BoxUnfitService` (UC-34, UC-78 mức hộp, khóa toàn cục chống deadlock, `Idempotency-Key`, nhánh `DELIVERED` → `Complaint(FOOD_SAFETY, OPEN)`).
- [x] **T7.5-04:** Cài đặt `BoxAttachmentPolicy`, `ComplaintAttachmentPolicy`, endpoint `POST .../boxes/{boxId}/unfit` (`BoxController`) và `POST /api/v1/admin/boxes/{boxId}/block` (`AdminBoxController`).
- [x] **T7.5-05:** Mở rộng `SaleBatchLifecycleService.block` & `AdminSaleBatchController` cho `UC-78` mức đợt (`POST /api/v1/admin/sale-batches/{batchId}/block`).
- [x] **T7.5-06:** Mở rộng `StoreStatusService` (`StoreStatusRequest`, `AdminStoreDetail`, `StoreSuspensionOrderSummary`) xử lý đơn tồn & rà soát đợt khi khóa cửa hàng (`UC-77` bước 3–5, `A1`, `E1`, `E2`, `BR-55`).
- [x] **T7.5-07:** Cập nhật `docs/api/openapi.yaml`, `StoreStatusTests.kt` (`AC-09`, `AC-14`), `AuthMigrationTests.kt`, `FoodRescueApplicationTests.kt`, `SwaggerUiTests.kt`.
- [x] **T7.5-08:** Chạy full `./gradlew.bat build --console=plain`, đọc `build/reports/tests/test/index.html` và XML từng suite xác nhận 100% pass, hoàn tất `03-implementation-control.txt`, đánh dấu `[x] T7.5` và `.gitignore`.

---

## 7. Truy vết AC → Task → Test

| AC | Mô tả ngắn | Task | Test Case dự kiến (`BoxUnfitViolationSuspendTests` & liên quan) |
|---|---|---|---|
| **AC-01** | Hộp chưa gắn đơn → `BLOCKED`, `BoxInspection(FAILED)`, loại khỏi `BatchAvailability`, gắn evidence | T7.5-01, 03, 04 | `AC-01 unallocated box marked unfit is blocked with evidence and removed from availability` |
| **AC-02** | Hộp thuộc đơn `PENDING_PAYMENT` → hủy đơn, hộp lỗi `BLOCKED`, hộp khác `AVAILABLE`, tiền đến muộn BR-26 | T7.5-03 | `AC-02 unfit box cancels pending payment order, frees safe siblings and handles late webhook` |
| **AC-03** | Hộp thuộc `CONFIRMED` / `HANDOVER_IN_PROGRESS` / `PENDING_NO_SHOW` → hủy phiên mở, hủy lỗi cửa hàng 100% | T7.5-03 | `AC-03 unfit box cancels confirmed, in-handover and pending-no-show orders with 100 percent refund` |
| **AC-04** | Hộp `DELIVERED` / đơn `COMPLETED` → giữ `DELIVERED`, ghi `BoxInspection(FAILED)`, mở `Complaint(FOOD_SAFETY, OPEN)` | T7.5-01, 03 | `AC-04 delivered box stays delivered and opens post-delivery food safety complaint` |
| **AC-05** | Tranh chấp đồng thời đánh dấu hộp không đạt vs hoàn tất bàn giao `completeSession` | T7.5-03 | `AC-05 concurrent unfit marking and handover completion yield one consistent outcome` |
| **AC-06** | `Idempotency-Key` lặp lại / sai payload trên UC-34 và UC-78 mức hộp | T7.5-01, 03 | `AC-06 idempotent replay and conflicting key rejection for box unfit and admin block` |
| **AC-07** | Admin chặn hộp vi phạm hoặc chặn cả đợt vi phạm `POST /admin/sale-batches/{id}/block` | T7.5-04, 05 | `AC-07 admin block box and block sale batch handle unpaid, confirmed and completed orders` |
| **AC-08** | Khóa cửa hàng thiếu phương án (`409`) khi có đơn tồn vs thành công khi không có đơn tồn | T7.5-06, 07 | `AC-08 suspend requires order handling only when active orders exist` & `StoreStatusTests` |
| **AC-09** | `CONTINUE_IF_SAFE`: giữ đơn `CONFIRMED` an toàn + có người bàn giao, đợt thành `PAUSED`, hủy `PENDING_PAYMENT` | T7.5-06 | `AC-09 suspend CONTINUE_IF_SAFE keeps safe confirmed orders with handover actor and pauses batch` |
| **AC-10** | Hộp quá hạn / thiếu người bàn giao (`UC-77 E2`) / `CANCEL_ALL` → hủy hoàn 100%; `REACTIVATE` không mở lại đợt/hộp lỗi | T7.5-06 | `AC-10 suspend cancels orders when unsafe or missing handover actor and reactivate keeps blocks` |
| **AC-11** | Kiểm soát quyền `BOX_MANAGE`/`ADMIN`/`Owner` và `BoxAttachmentPolicy`/`ComplaintAttachmentPolicy` | T7.5-04, 05, 06 | `AC-11 authorization and attachment access policies reject unauthorized actors` |
| **AC-12** | Flyway `V21`, `openapi.yaml` (`108` paths), toàn bộ test suite | T7.5-07, 08 | `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests` |

---

## 8. Kế hoạch kiểm thử TDD

- Viết `BoxUnfitViolationSuspendTests.kt` trước khi cài đặt controller/service theo quy trình TDD: xác nhận fail ở bước đầu do endpoint/field chưa tồn tại, sau đó cài đặt lần lượt từng phần và chạy targeted tests + toàn bộ Gradle build.
- Mọi kiểm tra thời gian dùng `Clock` tiêm qua fixture (tua `clock` để kiểm chứng `safeUntil <= now` hoặc `safeUntil < pickupEndsAt` trong `AC-10`).
- Test đồng thời (`AC-05`) dùng `concurrently(...)` / `CountDownLatch` kiểm chứng cặp tranh chấp `(unfit, completeSession)` chạy lặp nhiều vòng không deadlock, không ngoại lệ 500 và thỏa bất biến `(CANCELLED + RefundRequest(PENDING) + BLOCKED)` **HOẶC** `(COMPLETED + DELIVERED + Complaint(FOOD_SAFETY, OPEN))`.
- Kết thúc task: chạy `./gradlew.bat build --console=plain` sạch sẽ toàn bộ test suite và đọc trực tiếp `build/reports/tests/test/index.html` cùng XML của từng suite trước khi chốt `03-implementation-control.txt`.

---

## 9. Rủi ro và phương án kiểm soát / Rollback

1. **Rủi ro deadlock khi khóa `BoxUnfitService` vs `PickupSessionService` vs `OrderPlacementService`:**  
   *Kiểm soát:* Tuân thủ nghiêm ngặt thứ tự khóa toàn cục `Store -> SaleBatch -> CustomerOrder (id ASC) -> Payment (id ASC) -> PhysicalBox (id ASC)` kết hợp kiểm tra lại `box_allocation` dưới khóa và retry tối đa 3 lần nếu phát hiện đơn mới phát sinh giữa lúc đọc.
2. **Rủi ro vỡ test hiện hữu T3.3 (`StoreStatusTests.kt`) & T4.3 (`SaleBatchTests.kt`):**  
   *Kiểm soát:* `SaleBatchView` giữ nguyên schema cũ nên `SaleBatchTests.kt` không bị ảnh hưởng; 2 case trong `StoreStatusTests.kt` (`AC-09`, `AC-14`) được nêu rõ để cập nhật khớp với `UC-77 E1` và `BR-55`.
3. **Rollback:**  
   Nếu phát sinh sự cố khi tích hợp trước khi chốt task, có thể thu hồi sạch thay đổi code ở tầng service/controller/test và migration `V21` (chỉ áp dụng cho DB dev/test mới; không đụng vào `V1`–`V20`).

## 10. Nhật ký triển khai
- Baseline BUILD SUCCESSFUL (up-to-date). Gate B được duyệt bằng yêu cầu triển khai của người dùng.

- Test TDD đầu tiên đỏ đúng endpoint chưa tồn tại; migration V21 và compileKotlin thành công.
- Dùng truy vấn SQL scalar lấy tập orderId trước khóa để tránh giữ entity cũ trong persistence context; khóa từng order/box theo thứ tự ổn định bằng repository hiện có.
- Targeted lần đầu 16 tests: 14 PASS, 2 lỗi fixture dùng AccountStatus.SUSPENDED không tồn tại. Đã dùng LOCKED theo enum tài khoản hiện có; cửa hàng vẫn dùng StoreStatus.SUSPENDED.
- Lặp ca tranh chấp unfit/complete ba vòng và bổ sung kiểm tra CONTINUE_IF_SAFE với hộp không an toàn và incident chưa xử lý.

- Targeted mở rộng 52 tests: 50 pass/2 lỗi test chọn hộp không ổn định và đếm inspection không đúng phạm vi. Đã sửa ORDER BY box_id và chỉ đếm inspection FAILED của hộp DELIVERED ở ca bỏ qua complaint cũ; đang kiểm chứng lại.

- Targeted cuối BUILD SUCCESSFUL, 19/19 test T7.5 PASS. Đang chạy full build cuối.

- Full build cuối BUILD SUCCESSFUL trong 10m50s, exit code 0. 296 tests: 295 PASS, 0 FAIL/ERROR, 1 SKIP có sẵn. BoxUnfitViolationSuspendTests 19/19 PASS; đã đọc HTML và XML từng suite. T7.5 đã đóng trong roadmap.
