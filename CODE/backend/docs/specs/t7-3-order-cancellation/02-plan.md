# Technical Plan: T7.3 — Hủy đơn

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A đã được người dùng duyệt `Q1`–`Q5` ngày 2026-11-23.  
**Status:** DONE — triển khai và kiểm chứng hoàn tất ngày 2026-11-24  
**Updated:** 2026-11-24  
**Scope:** T7.3 — Hủy đơn (khách xem trước, hủy ngay / gửi yêu cầu hủy / bổ sung / rút / xem kết quả; chủ cửa hàng hủy lỗi cửa hàng; admin xét duyệt yêu cầu hủy).  
**Phụ thuộc:** T6.5 (`[x]`), T7.2 (`[x]`).

---

## 1. Quyết định đã duyệt và Baseline

1. **Các quyết định Gate A đã chốt (`Q1`–`Q5`):**
   - **Q1:** Giữ `POST /api/v1/orders/{orderId}/cancel` trả `OrderView` (thêm header tùy chọn `Idempotency-Key`) cho hủy ngay (`PENDING_PAYMENT` và `CONFIRMED` trước `pickupStartsAt`), trả `409 CANCELLATION_REQUIRES_REVIEW` nếu từ `pickupStartsAt` trở đi. Bổ sung bộ endpoint đầy đủ cho UC-17/UC-18 (`cancellation-preview`, `cancellation-requests`, `supplement`, `withdraw`, `cancellation`).
   - **Q2:** Chỉ **Store Owner** của cửa hàng sở hữu đơn được gọi `POST /api/v1/stores/{storeId}/orders/{orderId}/cancel` (UC-56).
   - **Q3:** Phân biệt trạng thái `HANDOVER_IN_PROGRESS`: nếu tồn tại `PickupSession` `OPEN`/`RECONCILING` → chặn hủy (`409 HANDOVER_IN_PROGRESS`); nếu không còn phiên mở (đã hủy phiên vì sự cố T7.2) → cho phép Store Owner hủy lỗi cửa hàng hoặc khách gửi yêu cầu hủy chờ admin duyệt.
   - **Q4:** Tạo migration `V19__order_cancellation_requests.sql` cho bảng `order_cancellation_request` và mở rộng `attachment`; cập nhật kỳ vọng số lượng migration (`19`) / entity (`43`) trong `AuthMigrationTests` và `FoodRescueApplicationTests`.
   - **Q5:** Khi phát sinh hoàn tiền (`> 0 VND`), tạo `RefundRequest` (`PENDING`) gắn `Payment` `VERIFIED` qua T6.5; hoàn `0 VND` (đơn chưa trả hoặc admin duyệt 0đ) không tạo `RefundRequest`.
2. **Baseline:**
   - `./gradlew.bat build --console=plain` đã kiểm chứng xanh hoàn toàn (238 PASS, 0 FAIL, 1 SKIPPED có sẵn).

---

## 2. Thiết kế chi tiết

### 2.1. Thành phần dữ liệu và phân quyền file
- `CancellationInitiator`: `CUSTOMER`, `STORE`.
- `CancellationRequestStatus`: `SUBMITTED`, `NEEDS_INFO`, `APPROVED`, `REJECTED`, `WITHDRAWN`.
- `CancellationDecision`: `APPROVE`, `REJECT`, `REQUEST_INFO`.
- `OrderCancellationRequest` (Entity): lưu `order`, `requestedBy`, `initiator`, `status`, `reason`, `storeFault`, `requestedAt`, `idempotencyKey`, `reviewer`, `reviewedAt`, `reviewNote`, `approvedRefundAmount`, `refundRequest`.
- `OrderCancellationAttachmentPolicy` (`AttachmentAccessPolicy`): cho phép đọc file đính kèm `orderCancellationRequest` nếu là khách chủ đơn, chủ cửa hàng của đơn, hoặc `ADMIN`.

### 2.2. Logic xem trước và hủy phía khách hàng (`OrderCancellationService`)
1. **`preview(customerId, orderId)` (UC-17 bước 1–3, read-only):**
   - Kiểm tra quyền sở hữu (`order.customer.id == customerId` → không thì 404).
   - Kiểm tra phiên đang mở `sessions.findByOrderIdAndStatusIn(orderId, listOf(OPEN, RECONCILING))`.
   - Đọc `money = orderMoneyReader.money(order)` và `now = clock.instant()`.
   - Phân loại kết quả:
     - `PENDING_PAYMENT`: `cancellableImmediately=true, requiresReview=false, estimatedRefundAmount=0`.
     - `CONFIRMED` & `now.isBefore(order.pickupStartsAt)`: `cancellableImmediately=true, requiresReview=false, estimatedRefundAmount=money.verifiedPaid`.
     - (`CONFIRMED` & `!now.isBefore(order.pickupStartsAt)`) hoặc (`HANDOVER_IN_PROGRESS` & `activeSession == null`): `cancellableImmediately=false, requiresReview=true, estimatedRefundAmount=null`.
     - `COMPLETED`: `cancellableImmediately=false, requiresReview=false, blockedReason="ORDER_ALREADY_COMPLETED"`.
     - `HANDOVER_IN_PROGRESS` & `activeSession != null`: `cancellableImmediately=false, requiresReview=false, blockedReason="HANDOVER_IN_PROGRESS"`.
     - Các trạng thái kết thúc khác (`CANCELLED`, `EXPIRED`, `NO_SHOW`, `PENDING_NO_SHOW`): `blockedReason="INVALID_ORDER_STATUS"`.
2. **`cancelImmediate(customerId, orderId, reason, idempotencyKey)` (`POST /api/v1/orders/{orderId}/cancel`):**
   - Khóa bi quan `orders.findForUpdate(orderId)` và kiểm tra ownership (`404` nếu sai).
   - Nếu `idempotencyKey != null`: validate format (`^[A-Za-z0-9_-]{1,255}$`), tra `cancellations.findByOrderIdAndIdempotencyKey(orderId, key)` → nếu có và đã `APPROVED`, trả về `OrderView` hiện tại (idempotent).
   - Kiểm tra `order.status`:
     - `COMPLETED` → `409 ORDER_ALREADY_COMPLETED` ("Order is already completed; please use the post-delivery complaint flow").
     - `HANDOVER_IN_PROGRESS`: kiểm tra `activeSession != null` → `409 HANDOVER_IN_PROGRESS`; nếu không có phiên mở → `409 CANCELLATION_REQUIRES_REVIEW`.
     - `PENDING_PAYMENT` → gọi `OrderReleaser.release(order, CANCELLED, "CUSTOMER_CANCELLED: ...", customer, "ORDER_CANCELLED_BY_CUSTOMER")` (không tạo `RefundRequest` 0đ), trả `OrderView`.
     - `CONFIRMED`:
       - Nếu `!clock.instant().isBefore(order.pickupStartsAt)` → `409 CANCELLATION_REQUIRES_REVIEW` ("Pickup window has started; submit a cancellation request for admin review").
       - Nếu `clock.instant().isBefore(order.pickupStartsAt)`: gọi helper dùng chung `executeCancellation(...)`:
         - `order.status = CANCELLED`, `statusReason = "CUSTOMER_CANCELLED: ..."`, `statusChangedAt = now`.
         - Giải phóng `BoxAllocation` (`HELD`/`CONFIRMED` → `RELEASED`), trả `PhysicalBox` (`RESERVED`/`ALLOCATED` → `AVAILABLE`, giữ nguyên nếu đang `BLOCKED`).
         - Tạo `RefundRequest` (`PENDING`, `reasonCode = "CUSTOMER_CANCELLED_BEFORE_PICKUP"`) cho `100%` tiền `VERIFIED` (nếu `> 0`).
         - Tạo `OrderCancellationRequest` (`CUSTOMER`, `APPROVED`, `storeFault = false`, `approvedRefundAmount = verifiedPaid`, `idempotencyKey = key`).
         - Ghi `StoreHistoryRecorder`.
         - Trả `OrderView`.
     - Khác (`CANCELLED`, `EXPIRED`, `NO_SHOW`, `PENDING_NO_SHOW`) → `409 INVALID_ORDER_STATUS`.
3. **`submitRequest(customerId, orderId, request, idempotencyKey)` (`POST .../cancellation-requests`):**
   - Khóa `CustomerOrder` (`findForUpdate`). Kiểm tra ownership.
   - Nếu `idempotencyKey != null`: kiểm tra idempotency (`findByOrderIdAndIdempotencyKey`) → trả kết quả cũ nếu khớp (`200`), hoặc `409 IDEMPOTENCY_KEY_REUSED` nếu không khớp.
   - Kiểm tra trạng thái chặn: `COMPLETED` (`409 ORDER_ALREADY_COMPLETED`), `HANDOVER_IN_PROGRESS` có phiên mở (`409 HANDOVER_IN_PROGRESS`), trạng thái khác không phải `CONFIRMED`/`HANDOVER_IN_PROGRESS` (`409 INVALID_ORDER_STATUS`).
   - Nếu `CONFIRMED` & `now.isBefore(order.pickupStartsAt)`: `409 IMMEDIATE_CANCELLATION_AVAILABLE`.
   - Kiểm tra chưa có yêu cầu hủy đang mở (`SUBMITTED` hoặc `NEEDS_INFO`) → nếu có thì `409 CANCELLATION_ALREADY_REQUESTED`.
   - Validate `attachmentIds`: mỗi file phải tồn tại, thuộc `customerId`, chưa gắn đối tượng nào.
   - Lưu `OrderCancellationRequest(status = SUBMITTED, storeFault = false, ...)` và các `Attachment`. Ghi `StoreHistoryRecorder`.
4. **`supplement(customerId, orderId, requestId, request)`:**
   - Khóa `CustomerOrder`, tải `OrderCancellationRequest` thuộc `orderId`.
   - Kiểm tra `status == NEEDS_INFO` (khác → `409 INVALID_REQUEST_STATUS`).
   - Validate `attachmentIds` mới, nối/cập nhật lý do, chuyển `status = SUBMITTED`, lưu `Attachment`, ghi `StoreHistoryRecorder`.
5. **`withdraw(customerId, orderId, requestId)`:**
   - Khóa `CustomerOrder`, tải `OrderCancellationRequest` thuộc `orderId`.
   - Kiểm tra `status in listOf(SUBMITTED, NEEDS_INFO)` (khác → `409 INVALID_REQUEST_STATUS`).
   - Chuyển `status = WITHDRAWN`, giữ nguyên trạng thái đơn và hộp, ghi `StoreHistoryRecorder`.
6. **`customerResult(customerId, orderId)` (`GET /api/v1/orders/{orderId}/cancellation`):**
   - Read-only; kiểm tra ownership; trả về `OrderCancellationStatusView` gồm trạng thái đơn, danh sách yêu cầu hủy của đơn (mới nhất trước), và danh sách `RefundRequestView` (tiến độ hoàn tách biệt).

### 2.3. Hủy đơn do cửa hàng (`StoreOrderCancellationService`)
- **`cancelByStore(ownerId, storeId, orderId, request, idempotencyKey)`:**
  - Khóa `CustomerOrder` (`findForUpdate`), kiểm tra `order.store.id == storeId` (không thì 404).
  - Kiểm tra quyền **Store Owner**: `order.store.owner.id == ownerId` (nếu không → `403 ACCESS_DENIED`).
  - Kiểm tra `idempotencyKey` (nếu có): trả kết quả cũ nếu trùng key trên đơn.
  - Kiểm tra trạng thái:
    - `COMPLETED` → `409 ORDER_ALREADY_COMPLETED`.
    - `HANDOVER_IN_PROGRESS`: nếu tồn tại `PickupSession` `OPEN`/`RECONCILING` → `409 HANDOVER_IN_PROGRESS`. Nếu không còn phiên mở (đã ghi sự cố T7.2) → **cho phép tiếp tục**.
    - Khác (`CANCELLED`, `EXPIRED`, `NO_SHOW`, `PENDING_NO_SHOW`) → `409 INVALID_ORDER_STATUS`.
  - Validate `unsafeBoxIds` (nếu có): phải thuộc các allocation đang hoạt động của đơn.
  - Đóng yêu cầu hủy đang mở của khách (nếu có ở `SUBMITTED`/`NEEDS_INFO`) thành `APPROVED` (`storeFault = true`, `reviewer = owner`, `reviewNote = "STORE_CANCELLED: ..."`), hoặc tạo mới `OrderCancellationRequest(initiator = STORE, status = APPROVED, storeFault = true, ...)`.
  - Hủy đơn (`CANCELLED`, `statusReason = "STORE_CANCELLED: ..."`), giải phóng allocation → `RELEASED`.
  - Hộp vật lý:
    - Nếu `request.boxesUnsafe == true` (mặc định) hoặc `box.id in unsafeBoxIds`: chuyển `BoxStatus.BLOCKED`.
    - Ngược lại (`boxesUnsafe == false` và không trong `unsafeBoxIds`): chuyển về `BoxStatus.AVAILABLE`.
  - Tạo `RefundRequest` (`PENDING`, `reasonCode = "STORE_FAULT"`) cho `100%` `verifiedPaid` (nếu `> 0`).
  - Ghi `StoreHistoryRecorder`.

### 2.4. Xét duyệt của Admin (`AdminCancellationService`)
- **`list(status, page, size)`** & **`detail(id)`:** dùng `PageQuery`, đọc snapshot chính sách, `OrderMoneyReader`, danh sách `PickupIncident` của đơn, attachments chứng cứ.
- **`decide(adminId, id, request, idempotencyKey)`:**
  - Tải `orderId` của request → khóa `CustomerOrder` (`findForUpdate(orderId)`) → khóa/refresh `OrderCancellationRequest` (`findForUpdate(id)`) theo đúng thứ tự khóa `CustomerOrder -> OrderCancellationRequest` để tránh deadlock với các luồng bàn giao/hủy của khách và cửa hàng!
  - Nếu `idempotencyKey != null` và request đã quyết định với cùng `idempotencyKey` (lưu trong `audit_log` hoặc nếu trạng thái đã xử lý với cùng key): trả về `AdminCancellationDetail` (idempotent).
  - Kiểm tra `req.status in listOf(SUBMITTED, NEEDS_INFO)` (nếu đã `APPROVED`/`REJECTED`/`WITHDRAWN` → `409 CONFLICT`).
  - Kiểm tra `order.status`:
    - `COMPLETED` → `409 ORDER_ALREADY_COMPLETED` (UC-71 E1).
    - `HANDOVER_IN_PROGRESS` có phiên `OPEN`/`RECONCILING` → `409 HANDOVER_IN_PROGRESS`.
  - Xử lý quyết định:
    - `REJECT`: bắt buộc `reason` (400 nếu trống). `req.status = REJECTED`, ghi `reviewer`, `reviewedAt`, `reviewNote`. Đơn giữ nguyên, không hoàn tiền.
    - `REQUEST_INFO`: bắt buộc `reason`. `req.status = NEEDS_INFO`, ghi `reviewer`, `reviewedAt`, `reviewNote`. Đơn giữ nguyên.
    - `APPROVE`:
      - Validate `refundAmount`: bắt buộc không âm, nguyên đồng (`scale == 0` / không lẻ đồng), `<= verifiedPaid`.
      - Nếu `storeFault == true`: bắt buộc `refundAmount == verifiedPaid` (UC-71 A3: hoàn 100%, không phạt khách).
      - Hủy đơn (`CANCELLED`, `statusReason = if (storeFault) "STORE_FAULT_CANCELLED: ..." else "ADMIN_APPROVED_CANCEL: ..."`).
      - Giải phóng allocation → `RELEASED`. Hộp → `BLOCKED` nếu `storeFault == true` hoặc `releaseBoxesToAvailable == false`; ngược lại → `AVAILABLE`.
      - Nếu `refundAmount > 0`: tạo `RefundRequest` (`PENDING`, `requestedAmount = refundAmount`, `reasonCode = if (storeFault) "STORE_FAULT" else "CUSTOMER_CANCELLED_APPROVED"`). Nếu `refundAmount == 0`: không tạo `RefundRequest`.
      - Cập nhật `req.status = APPROVED`, `approvedRefundAmount = refundAmount`, `refundRequest = refundReq`, `reviewer`, `reviewedAt`, `reviewNote`.
  - Ghi `StoreHistoryRecorder`.

### 2.5. Giải phóng hộp và tạo nghĩa vụ hoàn dùng chung (`PaidOrderCancellationExecutor`)
Để tránh trùng lặp và đảm bảo bất biến tiền/tồn kho:
- Tách một helper `@Component` `PaidOrderCancellationExecutor`:
  - Khóa danh sách `BoxAllocation` (`HELD`/`CONFIRMED`) và khóa `PhysicalBox` theo thứ tự `box.id` tăng dần (chống deadlock).
  - Cập nhật `AllocationStatus.RELEASED` (`releasedAt = now`, `releaseReason`).
  - Cập nhật `PhysicalBox` (`BLOCKED` nếu không an toàn/lỗi cửa hàng hoặc giữ nguyên nếu đang `BLOCKED`; `AVAILABLE` nếu an toàn).
  - Đọc tất cả `Payment` `VERIFIED` của đơn. Tính số tiền hoàn còn khả dụng trên payment (`receivedAmount - alreadyRefundedOrPending`) để đảm bảo tổng `RefundRequest` không vượt tổng tiền đã xác minh (bất biến CLAUDE.md §5).
  - Nếu `refundAmount > 0`: tạo `RefundRequest` (`PENDING`) qua `RefundRequestRepository` (kiểm tra chưa tồn tại `existsByPaymentId` trước khi insert, ném 409 rõ ràng nếu bất thường).

---

## 3. API Contract

Các tag OpenAPI sử dụng: `Orders`, `Stores`, `Admin` (hoặc `Admin cancellations` — giữ nguyên 24 tags hiện có bằng cách dùng tag `Orders`, `Stores`, `Admin refunds` hoặc thêm tag `Admin cancellations` và cập nhật `SwaggerUiTests` nếu cần; đề xuất dùng tag `Orders`, `Stores` và thêm `Admin cancellations` — cần cập nhật `tags` count trong `SwaggerUiTests` hoặc dùng tag `Orders` + `Stores` + `Admin refunds` để giữ 24 tags? Để sạch và rõ nghĩa như `Admin partner applications`, `Admin refunds`, dùng tag mới `Admin cancellations` sẽ nâng `tags` từ `24` lên `25` và `paths` từ `92` lên `101`!).

| Method / Path | Quyền & Request | Response |
|---|---|---|
| `GET /api/v1/orders/{orderId}/cancellation-preview` | Customer chủ đơn | `200 OrderCancellationPreview`; `401, 404` |
| `POST /api/v1/orders/{orderId}/cancel` | Customer chủ đơn; header `Idempotency-Key` (tùy chọn); body `OrderCancelRequest` | `200 OrderView`; `400, 401, 404, 409` |
| `POST /api/v1/orders/{orderId}/cancellation-requests` | Customer chủ đơn; header `Idempotency-Key` (tùy chọn); body `CustomerCancellationSubmitRequest` | `201/200 OrderCancellationView`; `400, 401, 404, 409` |
| `POST /api/v1/orders/{orderId}/cancellation-requests/{requestId}/supplement` | Customer chủ đơn; body `CustomerCancellationSupplementRequest` | `200 OrderCancellationView`; `400, 401, 404, 409` |
| `POST /api/v1/orders/{orderId}/cancellation-requests/{requestId}/withdraw` | Customer chủ đơn | `200 OrderCancellationView`; `401, 404, 409` |
| `GET /api/v1/orders/{orderId}/cancellation` | Customer chủ đơn | `200 OrderCancellationStatusView`; `401, 404` |
| `POST /api/v1/stores/{storeId}/orders/{orderId}/cancel` | Store Owner; header `Idempotency-Key` (tùy chọn); body `StoreOrderCancelRequest` | `200 OrderCancellationView`; `400, 401, 403, 404, 409` |
| `GET /api/v1/admin/cancellation-requests` | ADMIN; query `status`, `page`, `size` | `200 PageResponse<AdminCancellationSummary>`; `400, 401, 403` |
| `GET /api/v1/admin/cancellation-requests/{id}` | ADMIN | `200 AdminCancellationDetail`; `401, 403, 404` |
| `POST /api/v1/admin/cancellation-requests/{id}/decision` | ADMIN; header `Idempotency-Key` (tùy chọn); body `AdminCancellationDecisionRequest` | `200 AdminCancellationDetail`; `400, 401, 403, 404, 409` |

*(Tổng cộng thêm 9 path mới vào `openapi.yaml`, nâng từ 92 → 101 paths, vì `/orders/{orderId}/cancel` đã có sẵn từ T5.3 và sẽ được cập nhật đặc tả).*

---

## 4. Migration và Database Schema

File mới: `src/main/resources/db/migration/V19__order_cancellation_requests.sql`:
- Tạo bảng `order_cancellation_request` với đầy đủ FK, CHECK constraints cho `initiator`, `status`, `approved_refund_amount`.
- Tạo 2 partial unique indexes: `uq_order_open_cancellation` và `uq_order_cancellation_idempotency`.
- Bổ sung `order_cancellation_request_id` vào `attachment` và thay thế constraint `attachment_check` (giữ nguyên toàn bộ 10 cột cha cũ + cột thứ 11).
- Không sửa đổi V1–V18.

---

## 5. File Layout dự kiến

Đường dẫn Kotlin tương đối dưới `src/main/kotlin/com/foodrescue/backend/`:

| File | Loại | Trách nhiệm |
|---|---|---|
| `src/main/resources/db/migration/V19__order_cancellation_requests.sql` | NEW | Bảng `order_cancellation_request`, indexes, cột `attachment` |
| `order/CancellationInitiator.kt` | NEW | Enum `CUSTOMER`, `STORE` |
| `order/CancellationRequestStatus.kt` | NEW | Enum `SUBMITTED`, `NEEDS_INFO`, `APPROVED`, `REJECTED`, `WITHDRAWN` |
| `order/CancellationDecision.kt` | NEW | Enum `APPROVE`, `REJECT`, `REQUEST_INFO` |
| `order/entity/OrderCancellationRequest.kt` | NEW | Entity khớp V19 |
| `order/repository/OrderCancellationRequestRepository.kt` | NEW | Query & pessimistic lock cho `OrderCancellationRequest` |
| `file/entity/Attachment.kt` | MODIFIED | Thêm trường nullable `orderCancellationRequest` |
| `order/service/OrderCancellationAttachmentPolicy.kt` | NEW | `AttachmentAccessPolicy` cho bằng chứng hủy đơn |
| `order/dto/OrderCancellationPreview.kt` | NEW | DTO xem trước tác động hủy |
| `order/dto/CustomerCancellationSubmitRequest.kt` | NEW | DTO gửi yêu cầu hủy cần duyệt |
| `order/dto/CustomerCancellationSupplementRequest.kt` | NEW | DTO bổ sung thông tin khi `NEEDS_INFO` |
| `order/dto/OrderCancellationView.kt` | NEW | DTO thông tin một yêu cầu hủy |
| `order/dto/OrderCancellationStatusView.kt` | NEW | DTO kết quả hủy + tiến độ hoàn tiền (UC-18) |
| `order/dto/StoreOrderCancelRequest.kt` | NEW | DTO hủy đơn phía cửa hàng |
| `order/dto/AdminCancellationSummary.kt` | NEW | DTO tóm tắt yêu cầu hủy cho admin |
| `order/dto/AdminCancellationDetail.kt` | NEW | DTO chi tiết yêu cầu hủy cho admin (kèm policy, money, incidents) |
| `order/dto/AdminCancellationDecisionRequest.kt` | NEW | DTO quyết định xét duyệt admin |
| `order/service/PaidOrderCancellationExecutor.kt` | NEW | Helper nguyên tử giải phóng hộp/allocation và tạo `RefundRequest` |
| `order/service/OrderCancellationViews.kt` | NEW | Mapper DTO cho các luồng hủy |
| `order/service/OrderCancellationService.kt` | MODIFIED | Mở rộng cho preview, hủy ngay đơn `CONFIRMED` trước giờ nhận, gửi/bổ sung/rút/xem kết quả |
| `order/service/StoreOrderCancellationService.kt` | NEW | Nghiệp vụ hủy đơn do chủ cửa hàng (UC-56) |
| `order/service/AdminCancellationService.kt` | NEW | Nghiệp vụ admin xem/xét duyệt yêu cầu hủy (UC-71) |
| `order/controller/OrderController.kt` | MODIFIED | Cập nhật `/cancel` nhận `Idempotency-Key` tùy chọn |
| `order/controller/OrderCancellationController.kt` | NEW | Endpoints preview, requests, supplement, withdraw, cancellation status |
| `order/controller/StoreOrderCancellationController.kt` | NEW | Endpoint `POST /api/v1/stores/{storeId}/orders/{orderId}/cancel` |
| `order/controller/AdminCancellationController.kt` | NEW | Endpoints admin list, detail, decision |
| `pickup/repository/PickupIncidentRepository.kt` | MODIFIED | Thêm `findAllByOrderIdOrderByOccurredAt` cho admin detail |
| `docs/api/openapi.yaml` | MODIFIED | Đặc tả 9 endpoint mới + cập nhật `/cancel` + schemas/examples |
| `src/test/kotlin/.../OrderCancellationTests.kt` | NEW | Integration tests bao phủ AC-01..15 (gồm concurrency & security) |
| `src/test/kotlin/.../AuthMigrationTests.kt` | MODIFIED | Cập nhật số lượng migration (`18` → `19`) |
| `src/test/kotlin/.../FoodRescueApplicationTests.kt` | MODIFIED | Cập nhật số lượng entity (`42` → `43`) và migration (`18` → `19`) |
| `src/test/kotlin/.../SwaggerUiTests.kt` | MODIFIED | Cập nhật số lượng paths (`92` → `100`) và tags (`24` → `25`) |
| `.gitignore` | MODIFIED | Thêm whitelist `!docs/specs/t7-3-order-cancellation/` |

---

## 6. Task List và Traceability AC → Task → Test

### Task List:
- [x] **T1:** Tạo migration `V19__order_cancellation_requests.sql`, cập nhật `Attachment`, tạo enums, `OrderCancellationRequest` entity và repositories.
- [x] **T2:** Viết integration tests ban đầu (`OrderCancellationTests.kt`) theo hướng TDD (xác nhận đỏ trước khi viết service/controller).
- [x] **T3:** Tạo đầy đủ các DTOs, `OrderCancellationAttachmentPolicy`, `PaidOrderCancellationExecutor`, `OrderCancellationViews`.
- [x] **T4:** Triển khai `OrderCancellationService`, `StoreOrderCancellationService`, `AdminCancellationService`.
- [x] **T5:** Triển khai các Controllers (`OrderController`, `OrderCancellationController`, `StoreOrderCancellationController`, `AdminCancellationController`).
- [x] **T6:** Cập nhật `docs/api/openapi.yaml`, `AuthMigrationTests.kt`, `FoodRescueApplicationTests.kt`, `SwaggerUiTests.kt`, `.gitignore`.
- [x] **T7:** Chạy targeted tests và full `./gradlew.bat build --console=plain`, đọc `build/reports/tests/test/index.html`, viết `03-implementation-control.txt` và đánh dấu `[x] T7.3` trong `roadmap-3-9.md`.

### Traceability Table:

| AC | Task | Test tối thiểu trong `OrderCancellationTests` / Regression suites |
|---|---|---|
| AC-01, 02 | T3, T4, T5 | `preview` trước và từ `pickupStartsAt` với `Clock` cố định; không đổi DB |
| AC-03 | T4, T5 | Hủy đơn `PENDING_PAYMENT` giữ nguyên tương thích T5.3 (`OrderLifecycleTests` + `OrderCancellationTests`) |
| AC-04 | T3, T4, T5 | Hủy đơn `CONFIRMED` trước `pickupStartsAt`: `CANCELLED`, `RELEASED`, hộp về `AVAILABLE` (hết hạn an toàn không bán lại), tạo `RefundRequest` 100% |
| AC-05, 06, 07 | T1, T3, T4, T5 | Gửi yêu cầu từ `pickupStartsAt` (`SUBMITTED`), không trùng yêu cầu mở; rút yêu cầu (`WITHDRAWN`); admin `REQUEST_INFO` -> khách `supplement` -> `SUBMITTED` |
| AC-08 | T3, T4, T5 | Chủ cửa hàng hủy đơn `CONFIRMED` và đơn `HANDOVER_IN_PROGRESS` đã có `PickupIncident`: hoàn 100%, hộp lỗi chuyển `BLOCKED`, staff thường bị `403` |
| AC-09 | T3, T4, T5 | Admin duyệt `APPROVE` (toàn phần, một phần, `0đ`, `storeFault=true` bắt buộc 100%), `REJECT`, `REQUEST_INFO` |
| AC-10 | T4, T5 | Chặn hủy đơn `COMPLETED` (409 hướng dẫn khiếu nại) và đơn đang có phiên `OPEN` (409 `HANDOVER_IN_PROGRESS`) |
| AC-11 | T4 | Test đồng thời (`CountDownLatch`): hủy đơn vs bắt đầu/hoàn tất phiên bàn giao → đúng 1 kết quả nhất quán |
| AC-12 | T4, T5 | Test gửi lặp với cùng `Idempotency-Key` không tạo yêu cầu/hoàn trùng; hoàn tiền thất bại ở T6.5 giữ nguyên đơn `CANCELLED` |
| AC-13 | T4, T5 | `GET /api/v1/orders/{orderId}/cancellation` trả kết quả hủy + tiến độ hoàn T6.5 tách biệt |
| AC-14 | T3, T5 | Test 401 thiếu token, 404 đơn khách khác, 403 không phải Store Owner / không phải ADMIN, tải file đính kèm đúng phạm vi |
| AC-15 | T1, T6, T7 | `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests` và full build |

---

## 7. Test Strategy, Rollout, Rollback & Rủi ro

- **Test Strategy:** Kế thừa `StoreTestFixtures` + `@Import(PickupClockConfiguration::class)` + `@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)` (giải phóng pool sau class để tránh cạn connection PostgreSQL Testcontainers, giống `PickupCodeTests` và `PickupHandoverTests`). Dùng `FakePaymentGateway` và `CountDownLatch` cho concurrency.
- **Rollout & Rollback:**
  - Flyway tự động chạy `V19__order_cancellation_requests.sql` khi khởi động app mới.
  - Rollback giữ nguyên bảng `order_cancellation_request` và dữ liệu đã ghi; không chạy down migration.
- **Rủi ro & giảm thiểu:**
  - *Deadlock khóa nhiều bảng:* Quy chuẩn thứ tự khóa cố định `CustomerOrder` → `OrderCancellationRequest` → `PickupSession` → `BoxAllocation` → `PhysicalBox` (sắp xếp theo `box.id` tăng dần).
  - *Chiếm dụng file đính kèm:* Kiểm tra `file.uploadedBy.id == customerId` và `attachments.findAllByFileId(fileId).isEmpty()` trước khi gắn vào `OrderCancellationRequest`.
  - *Tạo hoàn vượt số đã xác minh:* Kiểm tra `verifiedPaid - alreadyRefundedOrPending` và tận dụng unique index `ux_refund_request_payment`.

## 8. Ghi chú triển khai
- 2026-11-23: người dùng yêu cầu thực hiện kế hoạch, Gate B được duyệt.
- Đếm đủ 9 path mới theo bảng endpoint: tổng paths = 101 (92 + 9), tags = 25.
- V19 lưu request_fingerprint và decision_key/decision_fingerprint để kiểm tra payload khi replay và tách khóa gửi yêu cầu khỏi khóa xét duyệt; không dùng audit làm nơi tra idempotency.
- Hủy chưa trả tiền cũng lưu yêu cầu APPROVED với 0đ để replay khi có key; không tạo RefundRequest 0đ.
- Store hủy khi có yêu cầu khách đang mở: đóng yêu cầu đó và lưu thêm quyết định STORE để giữ nguyên khóa gửi của khách cùng khóa hủy của owner.

- Baseline trước triển khai: BUILD SUCCESSFUL (up-to-date, kết quả trước 239 test: 238 pass/1 skip). Test TDD preview đỏ đúng 404 trước service/controller; V19 và Hibernate validate khởi tạo thành công.

- 2026-11-24: targeted mở rộng 45 tests, 44 pass/1 fail. Ca hai admin cùng key trả 200/503 do request được nạp vào persistence context trước khi chờ khóa order. Sửa bằng query scalar findOrderId trước, sau đó khóa order và tải/khóa request hiện tại.

- Kiểm chứng cuối ngày 2026-11-24: BUILD SUCCESSFUL trong 8m22s; 259 tests, 258 PASS, 0 FAIL/ERROR, 1 SKIP có sẵn. OrderCancellationTests 20/20 PASS; đã đọc báo cáo HTML và XML từng suite.
