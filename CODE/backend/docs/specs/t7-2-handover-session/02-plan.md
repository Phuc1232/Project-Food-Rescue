# Technical Plan: T7.2 — Phiên bàn giao và sự cố

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A đã được người dùng phê duyệt Q1–Q4 ngày 2026-11-23.  
**Status:** DONE — triển khai và kiểm chứng hoàn tất ngày 2026-11-23  
**Updated:** 2026-11-23  
**Scope:** T7.2 (Phiên bàn giao và sự cố). Task 7.3 phụ thuộc T7.2 nên sẽ làm ở phiên tiếp theo.  
**Phụ thuộc:** T7.1 đã hoàn thành và đánh dấu `[x]`.  

---

## 1. Quyết định đã duyệt và Baseline

1. **Các quyết định Gate A đã chốt:**
   - **Q1 (Endpoint layout):**
     - `GET /api/v1/stores/{storeId}/pickup-orders`: Xem danh sách đơn cần bàn giao tại cửa hàng.
     - `POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-sessions/start`: Bắt đầu phiên bàn giao (yêu cầu header `Idempotency-Key`).
     - `POST /api/v1/stores/{storeId}/pickup-sessions/{sessionId}/complete`: Hoàn tất phiên bàn giao (yêu cầu header `Idempotency-Key`).
     - `POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-incidents`: Ghi nhận sự cố không thể bàn giao (UC-30).
   - **Q2 (Bằng chứng sự cố):** Tận dụng schema V1 hiện có, lưu thông tin bằng chứng/chi tiết vào trường `resolution` (hoặc JSON formatted string); không thêm migration mới.
   - **Q3 (Chặn hủy & No-show):** Khi mở phiên, đơn sang `HANDOVER_IN_PROGRESS`. T7.2 đảm bảo trạng thái này được chuyển đổi nguyên tử dưới khóa, sẵn sàng cho T7.3 (hủy) và T7.4 (no-show) kiểm tra chặn theo BR-31.
   - **Q4 (Tuần tự):** Hoàn thành trọn vẹn T7.2 trước khi mở phiên cho T7.3.
2. **Baseline kiểm thử:**
   - `./gradlew build --console=plain` xanh hoàn toàn (BUILD SUCCESSFUL, exit code 0).
   - T7.1 đã hoàn thành toàn bộ test (214 tests pass/skip đúng quy chuẩn).

---

## 2. Thiết kế chi tiết

### 2.1. Tra cứu đơn cần bàn giao (UC-27)
- **Controller:** `StorePickupOrderController` mapped tại `/api/v1/stores/{storeId}/pickup-orders`.
- **Phân quyền:** Gọi `StoreAuthorizationService.requirePermission(actorId, storeId, "PICKUP_HANDLE")`. Chủ cửa hàng (Owner) tự động có quyền, Staff ACTIVE cần quyền `PICKUP_HANDLE`.
- **Tham số truy vấn:**
  - `date`: `LocalDate?` (mặc định lấy ngày hiện tại theo timezone cửa hàng).
  - `status`: `OrderStatus?` (chỉ cho phép `CONFIRMED` hoặc `HANDOVER_IN_PROGRESS`).
- **Dữ liệu trả về (`PickupOrderSummaryView`):**
  - Thông tin đơn: `orderId`, `orderCode`, `status`, `customerName`, `quantity`, `pickupStartsAt`, `pickupEndsAt`, `pickupAddressSnapshot`.
  - Thông tin tài chính: `OrderMoneyView` (từ `OrderMoneyReader`).
  - Hộp phân bổ: danh sách `AllocatedBoxView`.
  - Phiên hiện tại (nếu có): `currentSessionId`, `currentSessionStatus`.
- **Nguyên tắc:** Read-only transaction (`@Transactional(readOnly = true)`), không sinh phiên và không sửa dữ liệu.

### 2.2. Bắt đầu phiên bàn giao (UC-28, BR-31, BR-34, BR-36)
- **Service:** `PickupSessionService.startSession(actorId, storeId, orderId, idempotencyKey)`
- **Xử lý dưới khóa bi quan (`PESSIMISTIC_WRITE` trên CustomerOrder):**
  1. Kiểm tra quyền `PICKUP_HANDLE` tại `storeId`.
  2. Khóa dòng đơn hàng: `val order = orders.findForUpdate(orderId) ?: throw notFound()`.
  3. Kiểm tra đơn thuộc đúng `storeId`.
  4. **Kiểm tra tính Idempotent:**
     - Tìm phiên đang mở của đơn: `pickupSessionRepository.findByOrderIdAndStatusIn(orderId, listOf(PickupStatus.OPEN, PickupStatus.RECONCILING))`.
     - Nếu đã có phiên hoạt động:
       - Nếu trùng `idempotencyKey`: trả về view của phiên hiện tại (Idempotent 200/201).
       - Nếu khác `idempotencyKey`: từ chối với 409 `SESSION_ALREADY_OPEN` (BR-T72-02).
  5. **Kiểm tra trạng thái đơn:** Phải là `CONFIRMED` (nếu khác: 409 `INVALID_ORDER_STATUS`).
  6. **Kiểm tra tài chính (BR-31):** Gọi `OrderMoneyReader.money(order)` kiểm tra `remainingAmount.compareTo(BigDecimal.ZERO) == 0`. Nếu chưa trả đủ: 409 `PAYMENT_INCOMPLETE`.
  7. **Kiểm tra khung giờ (BR-34):** `val now = clock.instant()`. Kiểm tra `now.isBefore(order.pickupStartsAt) || !now.isBefore(order.pickupEndsAt)`. Nếu ngoài khung giờ: 409 `OUTSIDE_PICKUP_WINDOW`.
  8. **Kiểm tra hộp (BR-36):** Toàn bộ allocation của đơn phải là `CONFIRMED`, hộp vật lý phải là `ALLOCATED`. Số lượng phải bằng `order.quantity`.
  9. **Cập nhật dữ liệu nguyên tử:**
     - Tạo `PickupSession(order = order, actor = actor, status = PickupStatus.OPEN, startedAt = now, idempotencyKey = idempotencyKey)`. Lưu vào DB.
     - Cập nhật `order.status = OrderStatus.HANDOVER_IN_PROGRESS`, `order.statusReason = "HANDOVER_STARTED"`, `order.statusChangedAt = now`. Lưu vào DB.
     - Ghi `StoreHistoryRecorder`: action `HANDOVER_STARTED`.
  10. Trả về `PickupSessionView`.

### 2.3. Hoàn tất bàn giao (UC-28, BR-31, BR-36, BR-37)
- **Service:** `PickupSessionService.completeSession(actorId, storeId, sessionId, idempotencyKey)`
- **Xử lý dưới khóa bi quan (`PESSIMISTIC_WRITE` trên CustomerOrder qua session.order):**
  1. Kiểm tra quyền `PICKUP_HANDLE` tại `storeId`.
  2. Tìm phiên: `val session = pickupSessionRepository.findById(sessionId).orElse(null) ?: throw notFound()`.
  3. Khóa đơn: `val order = orders.findForUpdate(session.order.id!!)!!`.
  4. Kiểm tra đơn thuộc đúng `storeId`.
  5. **Kiểm tra tính Idempotent:**
     - Nếu `session.status == PickupStatus.COMPLETED`:
       - Nếu trùng `idempotencyKey` của phiên (hoặc hoàn tất): trả về kết quả thành công (Idempotent 200).
       - Nếu khác: 409 `SESSION_ALREADY_COMPLETED`.
  6. **Kiểm tra điều kiện hợp lệ:**
     - `session.status` phải là `OPEN`.
     - `order.status` phải là `HANDOVER_IN_PROGRESS`.
  7. **Kiểm tra hộp (BR-37 - Hoàn tất toàn bộ, không một phần):**
     - Đọc toàn bộ allocations `CONFIRMED` của đơn. Phải đủ `order.quantity`.
  8. **Cập nhật dữ liệu nguyên tử trong 1 Transaction:**
     - `val now = clock.instant()`.
     - `session.status = PickupStatus.COMPLETED`, `session.completedAt = now`.
     - `order.status = OrderStatus.COMPLETED`, `order.completedAt = now`, `order.statusReason = "HANDOVER_COMPLETED"`, `order.statusChangedAt = now`.
     - Toàn bộ `BoxAllocation` của đơn chuyển `status = AllocationStatus.DELIVERED`.
     - Toàn bộ `PhysicalBox` tương ứng khóa và chuyển `status = BoxStatus.DELIVERED`.
     - Tạo `FinancialEvent`:
       - `order = order`
       - `type = FinancialEventType.ORDER_COMPLETED`
       - `occurredAt = now`
       - `businessKey = "ORDER_COMPLETED:${order.id}"`
       - Lưu qua `FinancialEventRepository`.
     - Ghi `StoreHistoryRecorder`: action `HANDOVER_COMPLETED`.
  9. Trả về `PickupSessionView`.

### 2.4. Ghi nhận sự cố bàn giao (UC-30, BR-31)
- **Service:** `PickupIncidentService.recordIncident(actorId, storeId, orderId, request)`
- **Xử lý dưới khóa bi quan:**
  1. Kiểm tra quyền `PICKUP_HANDLE` tại `storeId`.
  2. Khóa dòng đơn hàng: `val order = orders.findForUpdate(orderId) ?: throw notFound()`.
  3. Kiểm tra đơn thuộc `storeId`.
  4. Kiểm tra trạng thái đơn:
     - Nếu `order.status == OrderStatus.COMPLETED`: từ chối với 409 `ORDER_ALREADY_COMPLETED`.
     - Nếu `order.status in listOf(CANCELLED, EXPIRED)`: từ chối 409 `INVALID_ORDER_STATUS`.
  5. Tìm phiên đang mở (nếu có): `val activeSession = pickupSessionRepository.findByOrderIdAndStatusIn(orderId, listOf(PickupStatus.OPEN, PickupStatus.RECONCILING))`
  6. Nếu có phiên đang mở:
     - Chuyển `activeSession.status = PickupStatus.CANCELLED`.
     - `order.statusReason = "HANDOVER_INCIDENT: " + request.reason`.
  7. Xử lý các hộp bị ảnh hưởng (nếu có `affectedBoxIds`):
     - Khóa từng hộp vật lý và chuyển sang `BoxStatus.BLOCKED`.
  8. Lưu bản ghi `PickupIncident`:
     - `order = order`
     - `session = activeSession`
     - `actor = actor`
     - `reason = request.reason`
     - `customerArrived = request.customerArrived`
     - `occurredAt = clock.instant()`
     - `resolution = request.resolution ?: "PENDING_REVIEW"`
     - Lưu qua `PickupIncidentRepository`.
  9. Ghi `StoreHistoryRecorder`: action `PICKUP_INCIDENT_RECORDED`.
  10. Trả về `PickupIncidentView`.

---

## 3. API Contract

Tag trong OpenAPI: `Pickup`

| Method / Path | Header / Body | Response | Ý nghĩa |
|---|---|---|---|
| `GET /api/v1/stores/{storeId}/pickup-orders` | Query: `date` (LocalDate?), `status` (OrderStatus?) | `200 List<PickupOrderSummaryView>`<br>`401, 403, 404` | Danh sách đơn cần bàn giao |
| `POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-sessions/start` | Header: `Idempotency-Key` (bắt buộc) | `201 PickupSessionView`<br>`400, 401, 403, 404, 409` | Mở phiên bàn giao |
| `POST /api/v1/stores/{storeId}/pickup-sessions/{sessionId}/complete` | Header: `Idempotency-Key` (bắt buộc) | `200 PickupSessionView`<br>`400, 401, 403, 404, 409` | Hoàn tất bàn giao |
| `POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-incidents` | Body: `PickupIncidentCreateRequest`<br>(`reason`, `customerArrived`, `affectedBoxIds?`, `resolution?`) | `201 PickupIncidentView`<br>`400, 401, 403, 404, 409` | Ghi nhận sự cố |

### Chi tiết DTOs:
- `PickupOrderSummaryView`:
  - `orderId`: UUID
  - `orderCode`: String
  - `status`: String
  - `customerName`: String?
  - `quantity`: Int
  - `pickupStartsAt`: Instant
  - `pickupEndsAt`: Instant
  - `pickupAddress`: String
  - `boxes`: List<AllocatedBoxView>
  - `money`: OrderMoneyView
  - `activeSessionId`: UUID?
- `PickupSessionView`:
  - `id`: UUID
  - `orderId`: UUID
  - `orderCode`: String
  - `actorId`: UUID
  - `status`: String (`OPEN`, `COMPLETED`, `CANCELLED`)
  - `startedAt`: Instant
  - `completedAt`: Instant?
  - `idempotencyKey`: String
- `PickupIncidentCreateRequest`:
  - `reason`: String (NotBlank, max 1000)
  - `customerArrived`: Boolean (NotNull)
  - `affectedBoxIds`: List<UUID>?
  - `resolution`: String?
- `PickupIncidentView`:
  - `id`: UUID
  - `orderId`: UUID
  - `sessionId`: UUID?
  - `actorId`: UUID
  - `reason`: String
  - `customerArrived`: Boolean
  - `occurredAt`: Instant
  - `resolution`: String?

---

## 4. Migration và Database Schema

- Bảng `pickup_session` và `pickup_incident` đã có trong schema từ `V1__create_entities.sql`.
- Index bảo vệ một phiên hoạt động duy nhất trên một đơn: `uq_order_active_pickup ON pickup_session(order_id) WHERE status IN ('OPEN', 'RECONCILING')` đã có trong `V2__relationships_and_indexes.sql`.
- Ràng buộc check `((status = 'COMPLETED') = (completed_at IS NOT NULL))` đã có trong `V1`.
- Cột `completed_at` trên `customer_order` đã có trong `V1` và nằm trong danh sách whitelist cho phép update của trigger `protect_order_terms` (V18).
- **Kết luận:** **Không cần thêm Flyway migration mới**. Tận dụng tối đa schema hiện hữu.

---

## 5. File Layout và Trách nhiệm

Đường dẫn tương đối dưới `src/main/kotlin/com/foodrescue/backend/`:

| File | Loại | Trách nhiệm |
|---|---|---|
| `pickup/dto/PickupOrderSummaryView.kt` | NEW | DTO trả về danh sách đơn cần bàn giao |
| `pickup/dto/PickupSessionView.kt` | NEW | DTO thông tin phiên bàn giao |
| `pickup/dto/PickupIncidentCreateRequest.kt` | NEW | DTO yêu cầu ghi sự cố |
| `pickup/dto/PickupIncidentView.kt` | NEW | DTO trả về thông tin sự cố |
| `pickup/repository/PickupSessionRepository.kt` | MODIFIED | Thêm query tìm phiên theo đơn và trạng thái, tìm theo idempotencyKey |
| `pickup/service/PickupSessionService.kt` | NEW | Nghiệp vụ mở phiên, hoàn tất phiên, kiểm tra tiền, khung giờ, hộp, idempotency, ghi event tài chính |
| `pickup/service/PickupIncidentService.kt` | NEW | Nghiệp vụ ghi sự cố, hủy phiên mở, chặn hộp hỏng, phân loại hướng giải quyết |
| `pickup/controller/StorePickupOrderController.kt` | NEW | API xem danh sách đơn cần bàn giao |
| `pickup/controller/StorePickupSessionController.kt` | NEW | API start và complete phiên bàn giao |
| `pickup/controller/StorePickupIncidentController.kt` | NEW | API ghi nhận sự cố bàn giao |
| `docs/api/openapi.yaml` | MODIFIED | Thêm 4 endpoint mới, schema request/response, mã lỗi 400, 401, 403, 404, 409 |
| `src/test/kotlin/.../PickupHandoverTests.kt` | NEW | Integration tests HTTP bao phủ AC-01 đến AC-14 |

---

## 6. Task List & Traceability AC → Task → Test

### Task List:
- [x] **T1:** Tạo các DTO mới (`PickupOrderSummaryView`, `PickupSessionView`, `PickupIncidentCreateRequest`, `PickupIncidentView`).
- [x] **T2:** Mở rộng `PickupSessionRepository` với các phương thức truy vấn cần thiết.
- [x] **T3:** Triển khai `PickupSessionService` (nguyên tử dưới khóa, kiểm tra khung giờ qua Clock, tiền qua `OrderMoneyReader`, hộp qua `PhysicalBoxRepository`, cập nhật đơn/hộp/allocation/session, ghi `FinancialEvent` `ORDER_COMPLETED`).
- [x] **T4:** Triển khai `PickupIncidentService` (lưu sự cố, hủy session OPEN, chặn hộp lỗi, cập nhật reason trên order).
- [x] **T5:** Triển khai các Controller (`StorePickupOrderController`, `StorePickupSessionController`, `StorePickupIncidentController`) với phân quyền `PICKUP_HANDLE`.
- [x] **T6:** Cập nhật tài liệu API `docs/api/openapi.yaml`.
- [x] **T7:** Viết bộ integration test toàn diện `PickupHandoverTests.kt` và kiểm chứng.

### Traceability Table:

| AC | Mô tả tóm tắt | Task | Test tương ứng trong `PickupHandoverTests` |
|---|---|---|---|
| **AC-01** | Xem đơn bàn giao: lọc theo ngày, trạng thái, trả đủ tiền và hộp | T1, T2, T5 | `AC-01 list is read only with money boxes date status and active session` |
| **AC-02** | Phân quyền: sai cửa hàng, không có `PICKUP_HANDLE`, khách hàng → 403 | T5 | `AC-02 all endpoints enforce authentication and pickup permission` |
| **AC-03** | Mở phiên thành công: đơn `CONFIRMED`, đủ tiền, trong khung giờ → phiên `OPEN`, đơn `HANDOVER_IN_PROGRESS` | T1, T3, T5 | `AC-03,08,09,10 start complete and replay update all records exactly once` |
| **AC-04** | Ngoài khung giờ nhận (trước startsAt hoặc sau endsAt) → 409 `OUTSIDE_PICKUP_WINDOW` | T3 | `AC-04 pickup window is inclusive at start exclusive at end` |
| **AC-05** | Chưa thanh toán đủ tiền (`remainingAmount > 0`) → 409 `PAYMENT_INCOMPLETE` | T3 | `AC-05 insufficient verified payment blocks start` |
| **AC-06** | Đơn không ở `CONFIRMED` (CANCELLED, EXPIRED...) → 409 `INVALID_ORDER_STATUS` | T3 | `AC-06 invalid order states and invalid keys are rejected` |
| **AC-07** | Tranh chấp mở phiên đồng thời: 2 request khác idempotency key → 1 thành công, 1 bị 409 | T3 | `AC-07 concurrent starts serialize and concurrent completions replay` |
| **AC-08** | Idempotency mở phiên: gửi lại cùng key → trả phiên hiện tại, không tạo bản ghi mới | T3 | `AC-03,08,09,10 start complete and replay update all records exactly once` |
| **AC-09** | Hoàn tất phiên: phiên `COMPLETED`, đơn `COMPLETED`, hộp & allocation `DELIVERED`, tạo `FinancialEvent` `ORDER_COMPLETED` | T3 | `AC-03,08,09,10 start complete and replay update all records exactly once` |
| **AC-10** | Idempotency hoàn tất: gửi lại cùng key → trả thành công, không ghi trùng event | T3 | `AC-03,08,09,10 start complete and replay update all records exactly once` |
| **AC-11** | Ghi sự cố: lưu `PickupIncident`, hủy phiên OPEN, chuyển hộp sang `BLOCKED`, không hoàn tất đơn | T1, T4, T5 | `AC-11 incident cancels session blocks affected box and prevents completion` |
| **AC-12** | Đơn đã `COMPLETED` mà ghi sự cố bàn giao → 409 `ORDER_ALREADY_COMPLETED` | T4 | `AC-12 completed orders use the complaint flow` |
| **AC-13** | Đơn đang `HANDOVER_IN_PROGRESS` bảo vệ tính toàn vẹn (chuẩn bị cho T7.3/T7.4) | T3, T4 | `AC-13 current customer cancellation cannot change an open handover` |
| **AC-14** | OpenAPI contract đầy đủ và `ApiContractTests` pass | T6 | `ApiContractTests` |

---

## 7. Test Strategy, Rollout & Rủi ro

### Test Strategy:
- Kế thừa `AuthTestFixtures` sử dụng Testcontainers PostgreSQL và web test client với cổng ngẫu nhiên.
- Sử dụng `FakePaymentGateway` và dữ liệu seed hợp lệ từ các task trước.
- Giả lập `Clock` cố định để kiểm tra chính xác các biên thời gian:
  - `now < pickupStartsAt`: phải bị từ chối 409.
  - `pickupStartsAt <= now < pickupEndsAt`: thành công.
  - `now >= pickupEndsAt`: phải bị từ chối 409.
- Kiểm thử tranh chấp đồng thời bằng `CountDownLatch` trên hai thread độc lập.
- Kiểm tra tính bất biến của `FinancialEvent` và `CustomerOrder` terms snapshot.

### Rollout / Rollback:
- Không có thay đổi schema DB nên việc triển khai không yêu cầu migration downtime.
- Rollback an toàn bằng cách redeploy phiên bản mã nguồn trước đó mà không ảnh hưởng tới dữ liệu cũ.

### Rủi ro và biện pháp giảm thiểu:
- **Rủi ro:** Tranh chấp giữa nhân viên thao tác bàn giao tại quầy và job quét No-show hoặc khách bấm hủy đơn.
  - *Biện pháp:* Khóa bi quan `PESSIMISTIC_WRITE` trên dòng `CustomerOrder` trước mọi thao tác chuyển trạng thái, chuyển ngay sang `HANDOVER_IN_PROGRESS` khi mở phiên để các luồng khác thấy ngay trạng thái đang xử lý.
- **Rủi ro:** Giao thiếu hộp (hoàn tất một phần).
  - *Biện pháp:* Backend kiểm tra nghiêm ngặt toàn bộ số lượng allocation và hộp vật lý. Thiếu dù chỉ 1 hộp cũng từ chối hoàn tất và bắt buộc ghi nhận sự cố.

## 8. Ghi chú triển khai (2026-11-23)
- Dùng cùng Idempotency-Key khi start và complete một phiên; khóa khác bị 409. Schema hiện có chỉ lưu một khóa trên phiên.
- Bổ sung PickupOrderService cho truy vấn read-only và CustomerOrderRepository.findPickupOrders để lọc khung giờ giao với ngày theo timezone cửa hàng.
- Khóa hộp theo UUID ổn định ở start/complete; complete refresh session sau khi khóa order để đọc trạng thái mới nhất khi có tranh chấp.
- affectedBoxIds chỉ chấp nhận hộp phân bổ CONFIRMED của đúng đơn; sự cố chặn mở lại phiên qua statusReason, chờ quy trình xử lý T7.3.
- AC-13 kiểm tra endpoint hủy đơn chưa trả tiền hiện có từ chối HANDOVER_IN_PROGRESS; job no-show và hủy đơn đã trả tiền thuộc T7.3/T7.4.

- OrderMoneyView hiện có dùng tên remainingToPay; triển khai kiểm tra remainingToPay == 0 theo OrderMoneyReader, tương ứng remainingAmount trong mô tả kế hoạch.

- SwaggerUiTests kiểm tra cố định tổng path: cập nhật 88 → 92 tương ứng đúng 4 endpoint T7.2. Full build đầu: 239 test, 236 pass, 1 skip, 2 fail chỉ do kỳ vọng số path cũ (hai profile). Không đổi hành vi Swagger.

- Kiểm chứng cuối: full build BUILD SUCCESSFUL trong 8m39s; 239 tests: 238 PASS, 0 FAIL/ERROR, 1 SKIP có sẵn. PickupHandoverTests 14/14 PASS; đã đọc HTML và XML báo cáo.
