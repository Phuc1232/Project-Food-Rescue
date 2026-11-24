# Business Definition: T7.5 — Hộp không đạt, ngừng bán vi phạm, khóa cửa hàng có đơn tồn

**Status:** Chờ duyệt Gate A  
**Updated:** 2026-11-24  
**Scope:** T7.5 (Nhân viên `BOX_MANAGE`/Chủ cửa hàng đánh dấu hộp không đạt `UC-34`; Quản trị viên ngừng bán hộp/đợt vi phạm `UC-78`; Quản trị viên tạm khóa cửa hàng và rà soát/xử lý đơn tồn & đợt có đơn `UC-77` bước 3–5, `E1`, `E2`, `BR-20`, `BR-55`, `SD-08`).  
**Phụ thuộc:** T7.4 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Bối cảnh và mục tiêu

Sau khi T7.4 (No-show và kiểm tra lại hộp) đã hoàn tất (`[x]`), hệ thống cần khép kín các tình huống rủi ro chất lượng thực phẩm và vi phạm vận hành đối với **hộp cụ thể**, **đợt bán vi phạm**, và **cửa hàng bị tạm khóa khi đang có đơn tồn**:

1. **Đánh dấu hộp không đạt yêu cầu (UC-34, BR-20, SD-08):**
   - Nhân viên cửa hàng (`BOX_MANAGE`) hoặc Chủ cửa hàng phát hiện hộp không còn an toàn / quá hạn / hư hỏng và đánh dấu không đạt kèm lý do và bằng chứng.
   - Hộp chưa giao bị chuyển ngay sang `BLOCKED`, loại khỏi tồn mở bán và chặn bàn giao.
   - Hệ thống khóa từng đơn hàng bị ảnh hưởng, đọc trạng thái mới nhất để tránh xung đột với thao tác bàn giao/hủy/thanh toán đồng thời (`UC-34 E2`), và xử lý phân nhánh theo trạng thái đơn:
     - `PENDING_PAYMENT` → hủy đơn (`CANCELLED`), nhả các hộp an toàn khác của đơn về `AVAILABLE`, theo dõi tiền đến muộn (không khôi phục đơn — `BR-26`).
     - Đã xác nhận chưa giao (`CONFIRMED`, `HANDOVER_IN_PROGRESS`, `PENDING_NO_SHOW`) → hủy phiên bàn giao mở (nếu có), hủy đơn theo lỗi cửa hàng (`T7.3`: hoàn 100% qua `RefundRequest`, không phí, hoa hồng 0).
     - Đã giao (`COMPLETED` / hộp `DELIVERED`) → không giả chuyển lại hộp về kho, không hủy/hoàn lặp lại, mà ghi nhận sự cố sau giao và tạo hồ sơ khiếu nại (`Complaint` trạng thái `OPEN`) chuyển `T8.1`.
2. **Admin ngừng bán hộp hoặc đợt vi phạm (UC-78, BR-54):**
   - Quản trị viên ngừng bán **một hộp vi phạm** hoặc **cả đợt bán vi phạm** sử dụng cùng lõi xử lý đơn/hộp như trên: chặn bán mới, chặn giao hộp không an toàn, xử lý toàn bộ đơn bị ảnh hưởng (`PENDING_PAYMENT`, đã xác nhận chưa giao, hoặc đã giao) và giữ nguyên dấu vết vi phạm/lịch sử hộp.
3. **Admin khóa cửa hàng có đơn tồn (UC-77 bước 3–5, A1, E1, E2; BR-55):**
   - Nâng cấp `POST /api/v1/admin/stores/{storeId}/status` (từ T3.3 mới dừng ở chặn đợt chưa có đơn): khi tạm khóa (`SUSPEND`) cửa hàng đang có giao dịch tồn, admin phải chọn **phương án xử lý đơn tồn** (`UC-77 bước 3, E1`).
   - Ngừng các đợt chưa phát sinh đơn (`BLOCKED`), chuyển các đợt đã có đơn khỏi trạng thái nhận đơn mới (`OPEN`/`DRAFT` → `PAUSED` hoặc `BLOCKED`).
   - Rà soát từng đơn tồn: `PENDING_PAYMENT` hủy và nhả hộp (theo dõi tiền đến muộn); đơn đã xác nhận chỉ được giữ tiếp tục thực hiện nếu **đáp ứng phương án cho phép, hộp còn an toàn, và cửa hàng còn người có quyền bàn giao (`Owner ACTIVE` hoặc Staff `ACTIVE` có `PICKUP_HANDLE` — `UC-77 E2`)**; nếu không đạt hoặc admin chọn hủy tất cả → hủy lỗi cửa hàng & hoàn 100% (`T7.3`).

Nguồn nghiệp vụ trích xuất từ `../../DOCX/BaoCaoFoodRescue.pdf`:
- **Trang 25:** BR-20 (Ngừng bán và ngăn bàn giao).
- **Trang 31:** BR-54 (Quyền xử lý của Administrator), BR-55 (Xử lý đơn hàng đang tồn tại khi khóa cửa hàng), BR-56 (Lịch sử nghiệp vụ).
- **Trang 80–81:** UC-34 (Đánh dấu hộp không đạt yêu cầu — chính, A1, E1, E2).
- **Trang 127–129:** UC-77 (Cập nhật trạng thái cửa hàng — bước 3–5, A1, E1, E2) và UC-78 (Ngừng bán hộp vi phạm — chính, A1, E1, E2).
- **Trang 160 (Hình 8.8):** SD-08 (Sơ đồ tuần tự Xử lý hộp không đạt yêu cầu — bước 01..22).

---

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin dưới `src/main/kotlin/com/foodrescue/backend/`:

1. **Hộp vật lý và đợt bán (T4.3, T4.4, T7.4):**
   - `surprisebox/entity/PhysicalBox.kt` & `BoxInspection.kt`: `box_inspection` là bảng bất biến (trigger `immutable_box_inspection` ở V3), ghi nhận `PASSED`/`FAILED`, `inspector`, `inspected_at`, `safe_until`, `notes`.
   - `surprisebox/service/BatchAvailability.kt`: tự động loại các hộp `BLOCKED` khỏi `available` (chỉ đếm `AVAILABLE` thỏa `MEETS_TERMS AND NOT_HELD`).
   - `surprisebox/service/BoxRecheckService.kt` (T7.4): chỉ nhận hộp ở `AWAITING_INSPECTION` có lịch sử `NO_SHOW`; hộp bị đánh dấu `BLOCKED` do không đạt ở `UC-34`/`UC-78` sẽ **không** bị tái kiểm tra nhầm qua T7.4.
   - `surprisebox/controller/AdminSaleBatchController.kt` & `SaleBatchLifecycleService.block` (T4.3): `POST /api/v1/admin/sale-batches/{batchId}/block` hiện chỉ set `batch.status = BLOCKED` và `statusReason`, **chưa** khóa các hộp bên trong hay xử lý đơn bị ảnh hưởng của đợt đó. (Test `SaleBatchTests.kt` AC-08 gọi endpoint này với `{ "reason": "..." }` trên đợt có 1 hộp và 0 đơn, kỳ vọng `200 SaleBatchView`).
2. **Đơn hàng, bàn giao và hủy lỗi cửa hàng (T5.3, T6.4, T7.2, T7.3):**
   - `order/service/OrderReleaser.kt`: giải phóng `BoxAllocation(HELD)` → `RELEASED` và **chỉ** đưa hộp từ `RESERVED` → `AVAILABLE`. Nếu hộp lỗi đã được đổi sang `BLOCKED` trước khi gọi `OrderReleaser`, các hộp không lỗi khác của đơn sẽ tự động trở về `AVAILABLE`, còn hộp lỗi giữ `BLOCKED`.
   - `payment/service/PaymentConfirmationService.kt` (T6.4): khi webhook tiền đến muộn sau khi đơn đã chuyển `CANCELLED`, hệ thống **không** mở lại đơn mà tự ghi `Payment(REQUIRES_REVIEW)` và mở `RefundRequest` (`reasonCode = ORDER_CANCELLED`) qua `RefundObligationService`.
   - `pickup/service/PickupSessionService.kt` (T7.2): `completeSession` khóa `CustomerOrder` (`findForUpdate`) → `PhysicalBox` (`findForUpdate`) và yêu cầu `box.status == ALLOCATED`. Nếu luồng đánh dấu hộp không đạt tuân thủ đúng **thứ tự khóa toàn cục `Store -> SaleBatch -> CustomerOrder -> Payment -> PhysicalBox`**, hai luồng chạy đồng thời sẽ **không deadlock** và chỉ ra đúng một kết quả nhất quán (`UC-34 E2`).
   - `order/service/PaidOrderCancellationExecutor.kt` (T7.3): thực thi nguyên tử hủy đơn lỗi cửa hàng + tạo `RefundRequest` 100% tiền `VERIFIED`, hiện yêu cầu `order.status in [PENDING_PAYMENT, CONFIRMED, HANDOVER_IN_PROGRESS]` và chặn nếu có `PickupSession` `OPEN`/`RECONCILING` — trong T7.5 cần tách/hỗ trợ thêm việc hủy `PickupSession` đang mở và xử lý đơn `PENDING_NO_SHOW` nếu hộp thuộc đơn đó bị đánh dấu không đạt / vi phạm.
3. **Khóa cửa hàng (`T3.3`) và Khiếu nại (`T8.1`):**
   - `store/controller/AdminStoreController.kt` & `StoreStatusService.kt`: `POST /api/v1/admin/stores/{storeId}/status` hiện nhận `StoreStatusRequest(action, reason)` và gọi `SaleBatchBlocker.blockBatchesWithoutOrders(storeId)`, bỏ lại các đợt đã có đơn và các đơn `CONFIRMED` tồn đọng (như comment trong `StoreStatusService` và `SaleBatchBlocker` đã ghi chú dành cho T7.5). Test `StoreStatusTests.kt` gọi `SUSPEND` trên cửa hàng chưa có đơn tồn.
   - `common/security/StoreAuthorizationService.kt`: đã cho phép chủ cửa hàng và nhân viên của cửa hàng trạng thái `SUSPENDED` tiếp tục truy cập nếu tài khoản của họ vẫn `ACTIVE` và `emailVerifiedAt != null` (chỉ chặn `PENDING_APPROVAL` và `CLOSED`). Do đó có thể xác định chính xác cửa hàng còn người bàn giao hợp lệ (`Owner ACTIVE` hoặc Staff `ACTIVE` có `PICKUP_HANDLE`) theo `UC-77 E2`.
   - `complaint/entity/Complaint.kt` & `V1`: bảng `complaint` đã có sẵn `ComplaintType.FOOD_SAFETY` và `ComplaintStatus.OPEN`, `store_id`, `order_id`, `customer_id`, `description`.
   - `file/entity/Attachment.kt` & `V1`/`V19`: đã có sẵn cột `box_id` và `complaint_id`, nhưng hiện **chưa** có `AttachmentAccessPolicy` cho `box_id` và `complaint_id`.
4. **Baseline kiểm thử:**
   - `./gradlew.bat build --console=plain`: **BUILD SUCCESSFUL** (`276 PASS, 0 FAIL, 1 SKIPPED` có sẵn; `20` migrations `V1`–`V20`; `43` entities; `106` OpenAPI paths / `26` tags).

---

## 3. Người dùng và quyền

- **Chủ cửa hàng (`Store Owner`) & Nhân viên (`Store Staff` có quyền `BOX_MANAGE`):**
  - Được đánh dấu hộp không đạt (UC-34) đối với hộp thuộc cửa hàng mình quản lý (`StoreAuthorizationService.requirePermission(actorId, storeId, "BOX_MANAGE")`).
  - Nhân viên không có `BOX_MANAGE` (vd chỉ có `BOX_PACK`, `PICKUP_HANDLE`, `ORDER_VIEW`, `NO_SHOW_CONFIRM`) hoặc thuộc cửa hàng khác: **403 `ACCESS_DENIED`**; hộp không thuộc `storeId`: **404 `NOT_FOUND`**.
- **Quản trị viên (`ADMIN`):**
  - Xác thực `@PreAuthorize("hasRole('ADMIN')")`:
    - Ngừng bán hộp vi phạm hoặc đợt vi phạm (`UC-78`).
    - Khóa/mở lại cửa hàng và quyết định phương án đơn tồn (`UC-77`).
  - Tài khoản không phải `ADMIN`: **403 `ACCESS_DENIED`**.
  - Lưu ý giữ nguyên quy tắc từ T3.3: `ADMIN` đồng thời là chủ cửa hàng đó không được tự đổi trạng thái cửa hàng mình (`403 ACCESS_DENIED`).
- **Quyền đọc file đính kèm (`AttachmentAccessPolicy`):**
  - File gắn `box_id`: Chủ cửa hàng, nhân viên có quyền `BOX_PACK` hoặc `BOX_MANAGE`, và `ADMIN`.
  - File gắn `complaint_id`: Khách hàng chủ đơn, chủ cửa hàng của đơn, và `ADMIN`.
- **Chưa đăng nhập / token không hợp lệ:** **401 `UNAUTHORIZED`**.

---

## 4. Luồng chính

### 4.1. Nhân viên / Chủ cửa hàng đánh dấu hộp không đạt (UC-34, BR-20, SD-08)
1. Tác nhân chọn hộp (`boxId`), nhập lý do (`reason`, bắt buộc), mô tả/ghi chú thực tế (`notes`, tùy chọn), danh sách bằng chứng (`attachmentIds`, tùy chọn) và header `Idempotency-Key` (tùy chọn).
2. Hệ thống kiểm tra quyền `BOX_MANAGE` trên `storeId`, validate `Idempotency-Key` và `attachmentIds`.
3. Xác định phạm vi khóa theo **thứ tự khóa toàn cục** tránh deadlock (`Store` → `SaleBatch FOR SHARE` → các `CustomerOrder` liên quan `findForUpdate` theo `order.id` tăng dần → `PhysicalBox findForUpdate`):
   - Kiểm tra idempotency: nếu yêu cầu trùng `Idempotency-Key` đã xử lý thành công với cùng payload, trả lại kết quả cũ mà không tạo `BoxInspection`/`RefundRequest`/`Complaint` trùng.
   - Đọc trạng thái mới nhất của hộp và mọi `BoxAllocation` (`HELD`, `CONFIRMED`, `DELIVERED`) trên hộp (bảo đảm bắt kịp trường hợp bàn giao vừa hoàn tất song song `UC-34 E2`).
4. **Nhánh A — Hộp đã giao cho khách (`box.status == DELIVERED` hoặc đơn đã `COMPLETED`):**
   - Không chuyển `box.status` về lại kho (`BLOCKED` hay `AVAILABLE` trong kho); giữ `box.status = DELIVERED` (`UC-34 E1`, `SD-08` #07–10).
   - Ghi nhận bản ghi kiểm tra bất biến `BoxInspection(box, actor, now, FAILED, safeUntil = box.safeUntil, notes)` làm dấu vết kiểm tra/vi phạm.
   - Mở/bổ sung hồ sơ sự cố sau giao `Complaint(store, order, customer = order.customer, type = FOOD_SAFETY, description, status = OPEN)` để `T8.1` tiếp nhận giải quyết (nếu gắn file chứng cứ thì liên kết qua `complaint_id` / `box_id`).
   - **Không** hủy đơn và **không** tạo `RefundRequest` lần nữa (chờ quy trình xử lý khiếu nại sau giao `T8.1`).
   - Ghi `AuditLog` (`BOX_POST_DELIVERY_INCIDENT_RECORDED`).
5. **Nhánh B — Hộp chưa giao (`AVAILABLE`, `RESERVED`, `ALLOCATED`, `AWAITING_INSPECTION` hoặc chưa gắn đơn):**
   - Nếu hộp đã `BLOCKED` trước đó mà không còn đơn ảnh hưởng chưa xử lý → trả `409 BOX_ALREADY_BLOCKED` (hoặc idempotent nếu trùng `Idempotency-Key`).
   - Đánh dấu `box.status = BoxStatus.BLOCKED` (loại khỏi `BatchAvailability`, ngăn `PickupSessionService` hoàn tất bàn giao) và ghi `BoxInspection(box, actor, now, FAILED, safeUntil = box.safeUntil, notes)`. Gắn `attachmentIds` vào `box_id`.
   - Nếu hộp chưa gắn đơn (`held`/`confirmed` rỗng — `UC-34 A1`): chỉ ghi lịch sử và trả kết quả (0 đơn bị hủy, 0 hoàn tiền).
   - Nếu hộp gắn với đơn bị ảnh hưởng (khóa `order` từ bước 3):
     - **`PENDING_PAYMENT` (`SD-08` #15–16):** hủy đơn `CANCELLED` (`statusReason = "BOX_UNFIT: <reason>"`), gọi `OrderReleaser` (giải phóng `BoxAllocation(HELD)` → `RELEASED`, hộp không đạt **giữ `BLOCKED`**, các hộp khác an toàn của đơn trở về `AVAILABLE`). Tiền đến muộn sau khi hủy sẽ được `PaymentConfirmationService` (T6.4) giữ ở `REQUIRES_REVIEW` + mở `RefundRequest(ORDER_CANCELLED)` mà không khôi phục đơn.
     - **`CONFIRMED`, `HANDOVER_IN_PROGRESS`, `PENDING_NO_SHOW` (`SD-08` ref `SD-06`):**
       - Nếu đơn đang có `PickupSession` `OPEN` hoặc `RECONCILING` (đang dở bàn giao mà chưa hoàn tất `COMPLETED`): chuyển phiên sang `PickupStatus.CANCELLED` để chặn hoàn tất bàn giao.
       - Thực thi hủy đơn lỗi cửa hàng (`OrderStatus.CANCELLED`, `storeFault = true`): giải phóng toàn bộ `BoxAllocation` → `RELEASED`, hộp vi phạm giữ `BLOCKED`, **các hộp khác không vi phạm** của đơn chuyển về `AVAILABLE`, tạo `OrderCancellationRequest(initiator = STORE, status = APPROVED, storeFault = true)` và tạo `RefundRequest(PENDING, reasonCode = "STORE_FAULT")` cho 100% tiền `VERIFIED` (nếu `> 0`), hoa hồng 0.
     - **Đơn vừa chuyển sang `COMPLETED` ngay trước khi khóa (`UC-34 E2`):** chuyển sang xử lý như **Nhánh A** (ghi `Complaint(FOOD_SAFETY, OPEN)` sau giao, không hủy/hoàn trùng).
   - Ghi `AuditLog` (`BOX_MARKED_UNFIT`).

### 4.2. Admin ngừng bán hộp hoặc đợt vi phạm (UC-78, BR-54)
1. **Ngừng bán hộp vi phạm (`POST /api/v1/admin/boxes/{boxId}/block`):**
   - Admin gửi `reason` (bắt buộc), `notes`, `attachmentIds`, và `Idempotency-Key` (tùy chọn).
   - Dùng chung lõi xử lý `UnfitBoxProcessor` với UC-34 (chỉ khác actor kiểm tra `ADMIN` thay vì `BOX_MANAGE`, và action audit `ADMIN_BOX_BLOCKED`).
2. **Ngừng bán đợt bán vi phạm (`POST /api/v1/admin/sale-batches/{batchId}/block`):**
   - Admin gửi `reason` (bắt buộc) — giữ tương thích schema cũ từ T4.3 và mở rộng tùy chọn cờ `includeDeliveredBoxesIncident: Boolean = true` (hoặc mặc định xử lý các hộp chưa giao + rà các đơn liên quan).
   - Khóa `SaleBatch` → đặt `batch.status = SaleBatchStatus.BLOCKED`, `statusReason = reason.trim()` (chặn vĩnh viễn mở bán/nhận đơn mới).
   - Khóa toàn bộ đơn đang tồn trong đợt (`PENDING_PAYMENT`, `CONFIRMED`, `HANDOVER_IN_PROGRESS`, `PENDING_NO_SHOW`, `COMPLETED` có hộp chưa hoàn) và toàn bộ `PhysicalBox` thuộc đợt theo đúng thứ tự khóa toàn cục:
     - Với mọi hộp chưa giao (`AVAILABLE`, `RESERVED`, `ALLOCATED`, `AWAITING_INSPECTION`) thuộc đợt: chuyển `BLOCKED` + ghi `BoxInspection(FAILED)`.
     - Với từng đơn chưa giao thuộc đợt:
       - `PENDING_PAYMENT` → hủy `CANCELLED`, nhả/chặn hộp, theo dõi tiền đến muộn (`BR-26`).
       - `CONFIRMED` / `HANDOVER_IN_PROGRESS` / `PENDING_NO_SHOW` → hủy phiên mở (nếu có), hủy lỗi cửa hàng (`CANCELLED`, `storeFault = true`, tạo `OrderCancellationRequest(APPROVED, storeFault = true)` + `RefundRequest(PENDING, STORE_FAULT)` 100% `verifiedPaid`).
     - Với đơn `COMPLETED` thuộc đợt (nếu đợt vi phạm ATTP được đánh dấu hậu kiểm — `UC-78 A1`): giữ nguyên trạng thái đơn & hộp `DELIVERED` (không hủy/hoàn lại), ghi nhận `Complaint(FOOD_SAFETY, OPEN)` chuyển T8.1 nếu admin đánh dấu có nguy cơ an toàn thực phẩm cho hộp đã giao (hoặc theo flag/reason).
   - Ghi `AuditLog` (`SALE_BATCH_BLOCKED`, tổng hợp số hộp chặn, đơn hủy, khiếu nại mở).

### 4.3. Admin khóa cửa hàng và rà soát đơn tồn (UC-77 bước 3–5, A1, E1, E2; BR-55)
1. Admin gọi `POST /api/v1/admin/stores/{storeId}/status` với `action` (`SUSPEND` / `REACTIVATE`), `reason` (bắt buộc) và `existingOrderHandling` (`CONTINUE_IF_SAFE` | `CANCEL_ALL`, tùy chọn khi không có đơn tồn nhưng **bắt buộc** khi cửa hàng đang có đơn tồn chưa kết thúc).
2. **Khi `SUSPEND`:**
   - Khóa `Store` (`findForUpdate`) và kiểm tra trạng thái `ACTIVE` (khác → `409`), admin không phải owner cửa hàng (`403`).
   - Đếm/tải các đơn tồn chưa kết thúc của cửa hàng (`PENDING_PAYMENT`, `CONFIRMED`, `HANDOVER_IN_PROGRESS`, `PENDING_NO_SHOW`):
     - Nếu tồn tại ít nhất 1 đơn tồn mà `existingOrderHandling == null` → từ chối (`409 EXISTING_ORDERS_DECISION_REQUIRED` hoặc `400 VALIDATION_FAILED`, tuân thủ **UC-77 E1**: *"thiếu căn cứ hoặc phương án cho đơn tồn tại: yêu cầu hoàn thiện quyết định"*).
   - Chuyển `store.status = SUSPENDED`, `statusReason = reason`, `statusChangedAt = now`.
   - **Rà soát đợt bán (`BR-55`):**
     - Các đợt `DRAFT`/`OPEN`/`PAUSED` chưa có bất kỳ đơn nào → `BLOCKED` (`SaleBatchBlocker`).
     - Các đợt `DRAFT`/`OPEN` đã có đơn → chuyển `PAUSED` (`statusReason = "Store suspended"`) để chặn nhận đơn mới nhưng vẫn giữ đợt cho phép xử lý đơn tồn (nếu mọi đơn trên đợt đều bị hủy hoặc không còn đơn hoạt động thì có thể giữ `PAUSED`/`BLOCKED` không mở bán lại).
   - **Xử lý từng đơn tồn (khóa đơn theo thứ tự `order.id` tăng dần):**
     - **Đơn `PENDING_PAYMENT`:** hủy `CANCELLED` (`statusReason = "STORE_SUSPENDED: <reason>"`), nhả hộp về `AVAILABLE` (hộp nào `BLOCKED` giữ `BLOCKED`), theo dõi tiền đến muộn (`BR-26`).
     - **Đơn đã xác nhận (`CONFIRMED`, `HANDOVER_IN_PROGRESS`, `PENDING_NO_SHOW`):**
       - Kiểm tra khả năng bàn giao của cửa hàng (`UC-77 E2`): `hasAuthorizedHandoverActor = (store.owner.status == ACTIVE && store.owner.emailVerifiedAt != null) || existsActiveStaffWithPermission(storeId, "PICKUP_HANDLE")`.
       - Kiểm tra điều kiện an toàn của hộp thuộc đơn: mọi hộp đang giữ/phân bổ cho đơn không ở trạng thái `BLOCKED`, `box.safeUntil >= order.pickupEndsAt`, và `box.safeUntil > now`.
       - Nếu `existingOrderHandling == CONTINUE_IF_SAFE` **và** cả hai điều kiện (hộp an toàn + có người bàn giao) đều thỏa (trừ trường hợp đơn đang bị kẹt sự cố `HANDOVER_INCIDENT` không giao được): **giữ đơn tiếp tục thực hiện**, ghi nhận vào danh sách `retainedOrderIds`.
       - Ngược lại (`CANCEL_ALL`, hoặc hộp không còn an toàn/quá hạn, hoặc không còn người được quyền bàn giao — `UC-77 E2`): hủy phiên bàn giao mở (nếu có), hủy đơn theo lỗi cửa hàng (`CANCELLED`, `storeFault = true`, hoàn 100% `verifiedPaid` qua `RefundRequest(PENDING)`). Hộp thuộc đơn: nếu hộp mất an toàn/quá hạn thì `BLOCKED`, nếu hộp còn đạt thì `AVAILABLE` (nhưng cửa hàng đang `SUSPENDED` nên không bán mới được).
   - Ghi `AuditLog` (`STORE_SUSPENDED` kèm thống kê `blockedSaleBatches`, `pausedSaleBatches`, `cancelledUnpaidOrders`, `retainedConfirmedOrders`, `cancelledConfirmedOrders`).
3. **Khi `REACTIVATE` (`UC-77 A1`):**
   - Khôi phục `store.status = ACTIVE`, tuyệt đối không tự động mở lại các `SaleBatch` đã `BLOCKED`/`ENDED`/hết hạn hoặc `PhysicalBox` đã `BLOCKED`.

---

## 5. Quy tắc nghiệp vụ đề xuất (BR-T75-xx)

- **BR-T75-01 — Hộp không đạt phải bị loại khỏi tồn bán và chặn bàn giao ngay (BR-20, UC-34):**
  Hộp chưa giao bị đánh dấu không đạt (`UC-34`) hoặc bị Admin chặn (`UC-78`) phải chuyển `BLOCKED` và ghi `BoxInspection(FAILED)` bất biến; không thể bán mới hay bàn giao thành công.
- **BR-T75-02 — Phân biệt trạng thái mới nhất của từng đơn dưới khóa bi quan (SD-08, UC-34 E2):**
  Mọi thao tác xử lý hộp/đợt/cửa hàng có đơn tồn phải tuân theo thứ tự khóa toàn cục `Store -> SaleBatch -> CustomerOrder -> Payment -> PhysicalBox` và đọc trạng thái mới nhất của đơn sau khi lấy khóa, bảo đảm bàn giao và đánh dấu lỗi chạy đồng thời không tạo hai kết quả mâu thuẫn.
- **BR-T75-03 — Hộp đã giao chỉ ghi sự cố sau giao & chuyển T8.1 (UC-34 E1, UC-78 A1):**
  Nếu hộp đã ở trạng thái `DELIVERED` / đơn đã `COMPLETED`, tuyệt đối không đưa hộp lại thành tồn kho, không hủy đơn và không tạo nghĩa vụ hoàn trùng lặp; chỉ ghi `BoxInspection(FAILED)` + tạo `Complaint(FOOD_SAFETY, OPEN)` chuyển T8.1.
- **BR-T75-04 — Hủy lỗi cửa hàng và hoàn 100% cho đơn chưa giao (BR-20, BR-55):**
  Đơn `PENDING_PAYMENT` bị hủy và nhả các hộp an toàn khác về `AVAILABLE` (tiền đến muộn giữ theo `BR-26` ở T6.4); đơn đã xác nhận chưa giao bị hủy theo lỗi cửa hàng (hoàn 100% `verifiedPaid` qua `RefundRequest`, hoa hồng 0).
- **BR-T75-05 — Khóa cửa hàng bắt buộc phương án đơn tồn và rà điều kiện an toàn + người bàn giao (UC-77 E1, E2; BR-55):**
  Tạm khóa cửa hàng đang có đơn tồn mà thiếu phương án xử lý phải bị từ chối. Chỉ giữ đơn đã xác nhận khi admin chọn tiếp tục, hộp còn an toàn (`!BLOCKED`, `safeUntil > now`, `safeUntil >= pickupEndsAt`) **và** còn người được quyền bàn giao (`Owner ACTIVE` hoặc Staff `ACTIVE` có `PICKUP_HANDLE`). Khi mở lại cửa hàng không tự mở đợt hết hạn hay hộp `BLOCKED`.
- **BR-T75-06 — Bảo toàn lịch sử hộp/đơn và Idempotency:**
  Không bao giờ xóa `BoxAllocation`, `BoxInspection`, `CustomerOrder`, hay `Complaint`. Yêu cầu đánh dấu hộp không đạt/chặn vi phạm lặp lại với cùng `Idempotency-Key` trả kết quả cũ mà không tạo trùng `BoxInspection`/`RefundRequest`/`Complaint`.

---

## 6. Phạm vi

### Trong phạm vi (In-Scope):
1. Luồng đánh dấu hộp không đạt (`UC-34`, `BR-20`, `SD-08`) cho `BOX_MANAGE` / Store Owner.
2. Luồng Admin ngừng bán hộp vi phạm và mở rộng ngừng bán đợt vi phạm (`UC-78`, `BR-54`) dùng chung lõi xử lý hộp/đơn.
3. Mở rộng Admin khóa/mở lại cửa hàng (`POST /api/v1/admin/stores/{storeId}/status`, `UC-77` bước 3–5, `A1`, `E1`, `E2`, `BR-55`) để rà soát đợt có đơn và xử lý toàn bộ đơn tồn (`PENDING_PAYMENT` & đơn đã xác nhận).
4. Tạo hồ sơ `Complaint(FOOD_SAFETY, OPEN)` cho trường hợp hộp đã giao (`COMPLETED`/`DELIVERED`) để chuyển tiếp sang `T8.1`.
5. `BoxAttachmentPolicy` và `ComplaintAttachmentPolicy` cho cột `attachment.box_id` và `attachment.complaint_id`.
6. Cập nhật `docs/api/openapi.yaml` và viết bộ integration test đầy đủ (bao gồm đồng thời đánh dấu hộp không đạt vs hoàn tất bàn giao, khóa cửa hàng thiếu/đủ người bàn giao, hoàn tiền 100%, tiền đến muộn sau hủy `PENDING_PAYMENT`).

### Ngoài phạm vi (Out-of-Scope):
1. Quy trình tiếp nhận khiếu nại từ khách hàng, yêu cầu bổ sung, xét duyệt và hoàn tiền sau giao trên `Complaint` (`T8.1`).
2. Gửi thông báo outbox/push/email cho khách hàng và cửa hàng (`T8.2`).
3. Bút toán đối soát/chi trả/điều chỉnh (`ADJUSTMENT`) của T8.3 & T8.4.
4. Giao diện Android/Web.

---

## 7. Acceptance Criteria (Given / When / Then)

| ID | Given / When / Then |
|---|---|
| **AC-01** | **Given** hộp chưa gắn đơn (`AVAILABLE`/`AWAITING_INSPECTION`), **When** staff `BOX_MANAGE` hoặc Store Owner đánh dấu hộp không đạt kèm lý do + bằng chứng (`attachmentIds`), **Then** lưu `BoxInspection(FAILED)`, hộp chuyển `BLOCKED` (loại khỏi `BatchAvailability`), không tạo `RefundRequest`, giữ nguyên lịch sử. |
| **AC-02** | **Given** hộp đang gắn đơn `PENDING_PAYMENT` (cùng 1 hoặc nhiều hộp khác trong đơn), **When** đánh dấu hộp không đạt (`UC-34`), **Then** hộp đó thành `BLOCKED`, đơn chuyển `CANCELLED`, allocation `RELEASED`, các hộp khác của đơn trả về `AVAILABLE`; nếu webhook tiền về sau đó (BR-26) thì giữ `REQUIRES_REVIEW` + mở hoàn tiền mà không khôi phục đơn. |
| **AC-03** | **Given** hộp đang gắn đơn `CONFIRMED` / `HANDOVER_IN_PROGRESS` / `PENDING_NO_SHOW` (đã thu tiền `VERIFIED`), **When** đánh dấu hộp không đạt, **Then** hủy phiên bàn giao mở (nếu có), hộp lỗi `BLOCKED`, hộp không lỗi khác của đơn về `AVAILABLE`, đơn chuyển `CANCELLED` (`storeFault = true`), tạo `OrderCancellationRequest(APPROVED, storeFault = true)` và `RefundRequest(PENDING)` bằng 100% tiền `VERIFIED`. |
| **AC-04** | **Given** hộp đã ở trạng thái `DELIVERED` / đơn `COMPLETED`, **When** đánh dấu hộp không đạt (`UC-34 E1`) hoặc Admin chặn hộp vi phạm (`UC-78 A1`), **Then** hộp giữ `DELIVERED` (không biến lại thành tồn kho), ghi `BoxInspection(FAILED)`, tạo `Complaint(FOOD_SAFETY, OPEN)` cho `T8.1`, **không** hủy đơn và **không** tạo `RefundRequest` lần nữa. |
| **AC-05** | **Given** nhân viên đánh dấu hộp không đạt (`UC-34`) và nhân viên bàn giao hoàn tất phiên (`completeSession`) chạy **đồng thời** trên cùng đơn, **When** hai giao dịch tranh chấp song song, **Then** khóa tuần tự chỉ cho ra đúng một kết quả nhất quán (hủy đơn + chặn bàn giao hoàn tất, HOẶC bàn giao hoàn tất + ghi nhận sự cố sau giao `Complaint`), tuyệt đối không có trạng thái nửa vời hay deadlock. |
| **AC-06** | **Given** gửi lại yêu cầu đánh dấu hộp không đạt hoặc chặn hộp vi phạm với cùng `Idempotency-Key` và cùng nội dung, **When** gọi lặp lại, **Then** trả lại kết quả cũ, không tạo `BoxInspection`, `RefundRequest` hay `Complaint` thứ hai. |
| **AC-07** | **Given** `ADMIN` ngừng bán hộp vi phạm hoặc ngừng bán đợt bán vi phạm (`UC-78`), **When** gọi API chặn hộp hoặc `POST /api/v1/admin/sale-batches/{batchId}/block`, **Then** đợt/hộp chuyển `BLOCKED`, các hộp chưa giao trong đợt bị `BLOCKED`, đơn `PENDING_PAYMENT` hủy + nhả chỗ, đơn đã xác nhận chưa giao hủy hoàn 100%, đơn `COMPLETED` ghi khiếu nại nếu có vi phạm an toàn hậu giao. |
| **AC-08** | **Given** cửa hàng đang có đơn tồn (`PENDING_PAYMENT` hoặc `CONFIRMED`), **When** `ADMIN` gọi `SUSPEND` mà thiếu phương án xử lý đơn tồn (`UC-77 E1`), **Then** bị từ chối nhất quán và yêu cầu chọn phương án; nếu cửa hàng không có đơn tồn, vẫn `SUSPEND` thành công như `T3.3`. |
| **AC-09** | **Given** cửa hàng bị `SUSPEND` với phương án `CONTINUE_IF_SAFE`, **When** đơn `CONFIRMED` có toàn bộ hộp còn an toàn (`!BLOCKED`, `safeUntil >= pickupEndsAt`, `safeUntil > now`) **và** còn người bàn giao hợp lệ (`Owner ACTIVE` hoặc Staff `ACTIVE` có `PICKUP_HANDLE`), **Then** đơn `CONFIRMED` được giữ lại tiếp tục bàn giao, đợt có đơn ngừng nhận đơn mới (`PAUSED`), đợt chưa có đơn bị `BLOCKED`, đơn `PENDING_PAYMENT` bị hủy. |
| **AC-10** | **Given** cửa hàng bị `SUSPEND` với đơn `CONFIRMED` mà hộp không còn an toàn/quá hạn, HOẶC cửa hàng không còn người có quyền bàn giao (`Owner` bị khóa & không có Staff `PICKUP_HANDLE` — `UC-77 E2`), HOẶC Admin chọn `CANCEL_ALL`, **Then** mọi đơn xác nhận không đạt điều kiện bị hủy lỗi cửa hàng và tạo `RefundRequest` hoàn 100%; khi `REACTIVATE` (`UC-77 A1`) không tự mở lại đợt hết hạn/bị khóa hay hộp `BLOCKED`. |
| **AC-11** | **Given** các người dùng sai quyền (`BOX_PACK`/`PICKUP_HANDLE`/`NO_SHOW_CONFIRM`/customer gọi UC-34; non-ADMIN gọi UC-77/UC-78; admin là chủ chính cửa hàng gọi khóa cửa hàng; người ngoài tải file chứng cứ hộp/khiếu nại), **When** gọi API tương ứng, **Then** trả `403`/`404`/`401` đúng phạm vi kiểm soát quyền server. |
| **AC-12** | **Given** các endpoint mới/cập nhật (và migration nếu được duyệt tại Q5), **When** chạy Flyway, `ApiContractTests`, `SwaggerUiTests` và toàn bộ Gradle test suite, **Then** build xanh hoàn toàn và `docs/api/openapi.yaml` đầy đủ schema/ví dụ/mã lỗi. |

---

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn nghiệp vụ | Phạm vi đáp ứng | AC |
|---|---|---|
| **UC-34 chính, A1, E1, E2** (Trang 80–81) | Đánh dấu hộp không đạt, hộp chưa gắn đơn, hộp đã giao chuyển khiếu nại, xử lý tranh chấp đồng thời với bàn giao | AC-01, 02, 03, 04, 05, 06 |
| **SD-08** (Trang 160, Hình 8.8) | Trình tự khóa kiểm tra trạng thái mới nhất của từng đơn, phân nhánh đã giao / chờ thanh toán / đã xác nhận / kết thúc | AC-02, 03, 04, 05 |
| **BR-20** (Trang 25) | Hộp quá hạn/không an toàn ngừng bán, chặn giao, đơn liên quan xử lý hủy/hoàn | AC-01, 02, 03, 07 |
| **UC-78 chính, A1, E1, E2; BR-54** (Trang 31, 128–129) | Admin ngừng bán hộp hoặc đợt vi phạm cùng luồng UC-34, xử lý đơn chưa giao và hậu giao, tiền đến muộn BR-26 | AC-02, 06, 07 |
| **UC-77 bước 3–5, A1, E1, E2; BR-55** (Trang 31, 127–128) | Khóa cửa hàng bắt buộc phương án đơn tồn, ngừng đợt chưa có đơn/tạm dừng đợt có đơn, giữ đơn an toàn & có người bàn giao, không tự mở đợt/hộp lỗi khi mở khóa | AC-08, 09, 10 |
| **CLAUDE.md** §4, 5, 7, 8 | Thứ tự khóa tránh deadlock, `Clock`, `BigDecimal`, `Idempotency-Key`, OpenAPI, test quyền và đồng thời | AC-05, 06, 11, 12 |

---

## 9. Quyết định cần duyệt tại Gate A

1. **Q1 (Thiết kế API cho `UC-34` và `UC-78`):**
   - *Đề xuất (Recommended):*
     - `POST /api/v1/stores/{storeId}/boxes/{boxId}/unfit` (`BOX_MANAGE` hoặc Store Owner, header `Idempotency-Key` tùy chọn): đánh dấu hộp không đạt (`UC-34`), trả về `BoxImpactResultView` (gồm `BoxDetail`, số đơn `PENDING_PAYMENT` đã hủy, số đơn đã xác nhận đã hủy/hoàn, số hồ sơ `Complaint` hậu giao đã mở).
     - `POST /api/v1/admin/boxes/{boxId}/block` (`ADMIN`, header `Idempotency-Key` tùy chọn): ngừng bán hộp vi phạm (`UC-78` mức hộp), dùng chung lõi xử lý và trả `BoxImpactResultView`.
     - Mở rộng `POST /api/v1/admin/sale-batches/{batchId}/block` (`ADMIN`, hiện có từ T4.3): khi chặn đợt bán, đồng thời chuyển mọi hộp chưa giao trong đợt sang `BLOCKED` và xử lý các đơn tồn thuộc đợt theo cùng lõi `UC-34`/`UC-78`. Giữ cấu trúc các trường của `SaleBatchView` (có thể bổ sung field tổng hợp tùy chọn hoặc trả `SaleBatchView` kèm ghi đầy đủ chi tiết vào `audit_log` để **không** làm vỡ bất kỳ test nào của T4.3 `SaleBatchTests`).
   *(Xác nhận thiết kế 3 endpoint này?)*

2. **Q2 (Xử lý đơn `COMPLETED` / hộp `DELIVERED` khi đánh dấu lỗi hậu kiểm):**
   Theo `UC-34 E1`, `UC-78 A1`, `SD-08` #07–10 và roadmap T7.5: hộp đã giao không thể thu hồi về kho, không hủy/hoàn tiền ngay mà chuyển khiếu nại `T8.1`.
   - *Đề xuất (Recommended):* Khi hộp có `status == DELIVERED` (hoặc đơn vừa chuyển `COMPLETED` đồng thời):
     - Giữ `box.status = DELIVERED`, ghi `BoxInspection(FAILED)` làm dấu vết kiểm tra.
     - Tạo mới 1 `Complaint(store, order, customer = order.customer, type = FOOD_SAFETY, status = OPEN, description = ...)` kèm gắn `attachmentIds` (nếu có) để `T8.1` xử lý tiếp.
     - Trả `200 BoxImpactResultView` (có `complaintId` vừa tạo) chứ không báo lỗi 409, đúng với nhánh `alt [Hộp đã được giao cho khách]` trong `SD-08`.
   *(Xác nhận hướng xử lý này?)*

3. **Q3 (Mở rộng `POST /api/v1/admin/stores/{storeId}/status` cho `UC-77` bước 3–5, `E1`, `E2`):**
   - *Đề xuất (Recommended):*
     - Thêm trường tùy chọn `existingOrderHandling: StoreSuspendOrderHandling?` (`CONTINUE_IF_SAFE`, `CANCEL_ALL`) vào `StoreStatusRequest` (và giữ tùy chọn tương thích ngược hoàn toàn khi cửa hàng chưa có đơn tồn như trong `StoreStatusTests` T3.3).
     - Nếu `action == SUSPEND` và cửa hàng đang có ít nhất 1 đơn tồn (`PENDING_PAYMENT`, `CONFIRMED`, `HANDOVER_IN_PROGRESS`, `PENDING_NO_SHOW`) mà `existingOrderHandling == null` → trả `409 EXISTING_ORDERS_DECISION_REQUIRED` (`UC-77 E1`).
     - Với đơn đã xác nhận khi chọn `CONTINUE_IF_SAFE`: kiểm tra điều kiện an toàn của hộp (`status != BLOCKED && safeUntil > now && safeUntil >= pickupEndsAt`) **và** cửa hàng có người được quyền bàn giao (`owner ACTIVE đã verify email` HOẶC có ít nhất 1 `StaffAssignment ACTIVE` với `account ACTIVE đã verify email` và có permission `PICKUP_HANDLE`). Thiếu bất kỳ điều kiện nào → tự động hủy lỗi cửa hàng & hoàn 100% (`UC-77 E2`).
     - Đợt bán chưa có đơn → `BLOCKED`; đợt `DRAFT`/`OPEN` có đơn → `PAUSED` nếu còn đơn tiếp tục thực hiện, hoặc `BLOCKED` nếu mọi đơn trên đợt đều bị hủy.
     - Mở rộng `AdminStoreDetail` thêm field `suspensionOrderSummary` (tùy chọn, nullable khi đọc hoặc mặc định null) để trả kết quả rà soát đơn tồn mà không làm gãy T3.3 tests.
   *(Xác nhận thiết kế mở rộng này cho `POST /api/v1/admin/stores/{storeId}/status`?)*

4. **Q4 (Phân quyền file đính kèm `attachment.box_id` và `attachment.complaint_id`):**
   - *Đề xuất (Recommended):* Bổ sung `BoxAttachmentPolicy` (cho phép owner, staff `BOX_PACK`/`BOX_MANAGE`, và `ADMIN`) và `ComplaintAttachmentPolicy` (cho phép customer chủ đơn, store owner, và `ADMIN`) để file chứng cứ gắn vào hộp/khiếu nại trong `UC-34`/`UC-78` truy cập được an toàn qua `GET /api/v1/files/{id}/content`.
   *(Xác nhận bổ sung 2 policy này?)*

5. **Q5 (Có cần Migration `V21` để chống trùng & idempotency cho hộp không đạt / khiếu nại hậu giao hay không?):**
   Khi đánh dấu hộp không đạt kèm `Idempotency-Key` (nhất là trường hợp hộp đã `DELIVERED` chỉ tạo `Complaint`, hoặc hộp đã bị `BLOCKED`), cần lưu vết `idempotency_key` + `request_fingerprint` để trả lại đúng `BoxImpactResultView` cũ nếu client gửi lại do mất mạng mà không ghi thêm `BoxInspection` hoặc `Complaint` thứ hai.
   - *Đề xuất (Recommended):* Tạo migration nhỏ `V21__box_unfit_idempotency_and_complaint_source.sql`:
     - Thêm cột `unfit_idempotency_key varchar(255)` + `unfit_fingerprint text` (hoặc một bảng/2 cột `source_box_id uuid REFERENCES physical_box(id)` + `idempotency_key varchar(255)` trên `complaint` / `physical_box` — cụ thể ở Gate B sẽ chốt sạch sẽ), kèm unique index để bảo đảm không bao giờ tạo 2 `Complaint` cho cùng 1 lần đánh dấu hộp.
     - Cập nhật số migration `20 -> 21` trong `AuthMigrationTests` và `FoodRescueApplicationTests`.
   - *Phương án thay thế:* Không thêm migration V21; dựa vào trạng thái `BoxStatus.BLOCKED` (với hộp chưa giao) và kiểm tra tồn tại `Complaint(type = FOOD_SAFETY)` theo `orderId` (với hộp đã giao), nhưng như vậy hộp `BLOCKED` rồi sẽ trả 409 thay vì replay idempotent khi gửi lại cùng `Idempotency-Key`.
   *(Xác nhận tạo migration `V21` hay chọn phương án không thêm migration?)*

---

## 10. Ràng buộc cho Gate B

- Không viết code sản phẩm hay migration V21 trước khi Gate A và Gate B được người dùng duyệt.
- Không sửa các migration V1–V20 đã chạy.
- Tuân thủ tuyệt đối thứ tự khóa `Store -> SaleBatch -> CustomerOrder -> Payment -> BoxAllocation / PhysicalBox` để chống deadlock với `OrderPlacementService`, `PickupSessionService`, `OrderCancellationService` và `NoShowService`.
- Không sửa làm hỏng bất kỳ test hiện hữu nào của T3.3 (`StoreStatusTests`), T4.3 (`SaleBatchTests`), T4.4 (`BoxPackingTests`), T7.2 (`PickupHandoverTests`), T7.3 (`OrderCancellationTests`), T7.4 (`NoShowTests`).
- Cập nhật đầy đủ `docs/api/openapi.yaml` để vượt qua `ApiContractTests` và `SwaggerUiTests`.
