# Business Definition: T7.2 — Phiên bàn giao và sự cố

**Status:** Chờ duyệt Gate A  
**Updated:** 2026-11-23  
**Scope:** T7.2 (Phiên bàn giao và sự cố). Task 7.3 phụ thuộc T7.2 nên sẽ được thực hiện ở phiên riêng biệt kế tiếp theo quy định tại CLAUDE.md §1 & §10.  
**Phụ thuộc:** T7.1 đã được đánh dấu `[x]` trong roadmap.  

---

## 1. Bối cảnh và mục tiêu

Sau khi khách hàng xuất trình mã nhận hàng và được nhân viên/chủ cửa hàng xác minh thành công (T7.1 — UC-29), quy trình bàn giao thực tế tại cửa hàng bắt đầu.

Mục tiêu của T7.2:
1. Cho phép nhân viên có quyền (`PICKUP_HANDLE`) hoặc chủ cửa hàng tra cứu danh sách các đơn cần bàn giao tại cửa hàng trong ngày (UC-27).
2. Quản lý vòng đời phiên bàn giao (`pickup_session`): Bắt đầu bàn giao (OPEN, đơn sang `HANDOVER_IN_PROGRESS`) khi thỏa mãn các điều kiện về khung giờ nhận, thanh toán đủ và hộp đạt chất lượng (UC-28, BR-31, BR-34, BR-36).
3. Hoàn tất bàn giao sau khi khách đã nhận đủ toàn bộ số hộp thực tế: Đơn chuyển `COMPLETED`, toàn bộ hộp và phân bổ chuyển `DELIVERED`, ghi nhận sự kiện tài chính `ORDER_COMPLETED` (BR-37). Tuyệt đối không hoàn tất một phần (BR-37).
4. Ghi nhận sự cố bàn giao (`pickup_incident` — UC-30): Khi có sự cố khiến cửa hàng không thể bàn giao (hộp hỏng, thực phẩm không an toàn, thiếu hộp...), ghi nhận lý do, hộp ảnh hưởng, tình trạng khách đã đến hay chưa; hủy phiên đang mở, chặn giao và chuẩn bị chuyển tiếp cho quy trình hủy do lỗi cửa hàng (T7.3) hoặc chuyển admin đối chiếu, không tự kết luận lỗi khách.
5. Bảo vệ tính toàn vẹn: Khi phiên bàn giao đang mở (`OPEN` hoặc `RECONCILING`), hệ thống chặn khách chủ động hủy đơn và chặn job xác nhận no-show (BR-31).

Nguồn nghiệp vụ trích xuất từ tài liệu chính thức `../../DOCX/BaoCaoFoodRescue.pdf`:
- Trang 27: BR-31 (Điều kiện tài chính khi bàn giao), BR-34 (Thời gian nhận hàng), BR-36 (Điều kiện bàn giao).
- Trang 28: BR-37 (Điều kiện hoàn tất đơn).
- Trang 74: UC-27 (Xem đơn cần bàn giao).
- Trang 75–76: UC-28 (Bàn giao Surprise Box).
- Trang 77–78: UC-30 (Ghi nhận sự cố bàn giao).

---

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin dưới `src/main/kotlin/com/foodrescue/backend/`:

1. **Entity & DB Schema hiện có:**
   - `pickup/entity/PickupSession`: Đã có các trường `order`, `actor`, `status` (`PickupStatus`), `startedAt`, `completedAt`, `idempotencyKey`.
     - DB V1: `pickup_session` có ràng buộc check `((status = 'COMPLETED') = (completed_at IS NOT NULL))` và enum check `OPEN, RECONCILING, COMPLETED, CANCELLED`. Unique trên `idempotency_key`.
     - DB V2: Index `uq_order_active_pickup ON pickup_session(order_id) WHERE status IN ('OPEN', 'RECONCILING')` bảo đảm mỗi đơn chỉ có tối đa một phiên bàn giao đang hoạt động.
   - `pickup/entity/PickupIncident`: Đã có `order`, `session` (nullable), `actor`, `reason`, `customerArrived`, `occurredAt`, `resolution`.
   - `order/entity/CustomerOrder`: Trạng thái `OrderStatus` đã có sẵn `CONFIRMED`, `HANDOVER_IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`, `PENDING_NO_SHOW`.
   - `order/entity/BoxAllocation`: Trạng thái `AllocationStatus` có `CONFIRMED`, `DELIVERED`, `RELEASED`.
   - `surprisebox/entity/PhysicalBox`: Trạng thái `BoxStatus` có `ALLOCATED`, `DELIVERED`, `BLOCKED`.
   - `finance/entity/FinancialEvent`: `FinancialEventType` đã có `ORDER_COMPLETED`.
   - `audit/entity/AuditLog`: Đã có bảng và service ghi audit.

2. **Security & Authorization:**
   - `common/security/StoreAuthorizationService.requirePermission(actorId, storeId, "PICKUP_HANDLE")`: Đã được seed từ T3.4 và áp dụng tại T7.1. Owner có toàn quyền đối với cửa hàng mình sở hữu; staff ACTIVE cần permission code `PICKUP_HANDLE`.

3. **Validation & Thời gian:**
   - `surprisebox/service/PickupWindowValidator`: Đã có hàm kiểm tra thời gian nhận hàng.
   - Inject `java.time.Clock` theo chuẩn dự án để hỗ trợ kiểm thử thời gian.

4. **Baseline Build:**
   - `./gradlew build` đã chạy thành công (BUILD SUCCESSFUL, exit code 0).

---

## 3. Người dùng và quyền

- **Tác nhân thực hiện:**
  - Nhân viên cửa hàng (`Store Staff`) có trạng thái `ACTIVE` và được cấp quyền `PICKUP_HANDLE` tại cửa hàng tương ứng.
  - Chủ cửa hàng (`Store Owner`) của cửa hàng đó.
- **Phân quyền và bảo mật:**
  - Khách hàng (`CUSTOMER`) không có quyền truy cập các endpoint bàn giao hoặc xem danh sách bàn giao của cửa hàng (trả 403 `ACCESS_DENIED`).
  - Nhân viên không thuộc cửa hàng, nhân viên bị thu hồi quyền (`REVOKED`), hoặc tài khoản bị khóa (`SUSPENDED`) bị chặn ngay ở tầng kiểm tra quyền (403 `ACCESS_DENIED`).
  - Quản trị viên (`ADMIN`) không tự động có quyền bàn giao đơn tại quầy thay cửa hàng nếu không phải owner/staff của cửa hàng đó.
  - Chưa xác thực (thiếu JWT token hoặc token hết hạn): trả 401 `UNAUTHORIZED`.

---

## 4. Luồng nghiệp vụ chi tiết

### 4.1. Xem danh sách đơn cần bàn giao (UC-27)
1. Nhân viên/Owner chọn cửa hàng (`storeId`), truyền tham số lọc: ngày nhận (`date`, mặc định là ngày hôm nay theo timezone cửa hàng), và trạng thái đơn (tùy chọn: `CONFIRMED`, `HANDOVER_IN_PROGRESS`).
2. Hệ thống kiểm tra quyền `PICKUP_HANDLE` của tác nhân tại `storeId`.
3. Hệ thống truy vấn danh sách đơn hàng thuộc cửa hàng thỏa mãn điều kiện:
   - Trạng thái đơn thuộc diện bàn giao (`CONFIRMED`, `HANDOVER_IN_PROGRESS`).
   - Khung giờ nhận (`pickupStartsAt`, `pickupEndsAt`) giao với ngày được chọn.
4. Trả về danh sách đơn kèm thông tin: mã đơn (`orderCode`), tên/số điện thoại liên hệ khách hàng (nếu có), khung giờ nhận, số lượng hộp, trạng thái tiền (đã thu đủ hay chưa), danh sách các hộp phân bổ kèm trạng thái, và phiên bàn giao hiện tại (nếu đang `OPEN`).
5. Thao tác xem là read-only, không làm thay đổi trạng thái đơn, không sinh phiên mới.

### 4.2. Bắt đầu phiên bàn giao (UC-28, BR-31, BR-34, BR-36)
1. Khách hàng xuất trình mã nhận hàng. Nhân viên kiểm tra mã qua UC-29 (T7.1) và kiểm tra tình trạng hộp thực tế tại quầy.
2. Nhân viên gửi yêu cầu bắt đầu bàn giao cho đơn hàng với header `Idempotency-Key` (hoặc body idempotency key).
3. Backend thực hiện trong một Transaction có khóa bi quan (`PESSIMISTIC_WRITE` trên `CustomerOrder`):
   - **Kiểm tra quyền:** Tác nhân có quyền `PICKUP_HANDLE` tại cửa hàng của đơn.
   - **Kiểm tra trạng thái đơn:**
     - Nếu đơn đã ở `HANDOVER_IN_PROGRESS` và đã có phiên `OPEN`:
       - Nếu trùng `idempotencyKey` của phiên hiện tại: trả về phiên hiện tại (Idempotent response).
       - Nếu khác `idempotencyKey`: từ chối với lỗi 409 `SESSION_ALREADY_OPEN` (chỉ cho phép một phiên hoạt động đồng thời trên một đơn).
     - Nếu đơn không ở trạng thái `CONFIRMED`: từ chối với 409 `INVALID_ORDER_STATUS`.
   - **Kiểm tra tài chính (BR-31):** Đơn phải xác minh thu đủ tiền (`remainingAmount == 0`).
   - **Kiểm tra khung giờ (BR-34):** Thời điểm hiện tại (`clock.instant()`) phải nằm trong khung nhận hàng: `pickupStartsAt <= now < pickupEndsAt`. Nếu ngoài khung giờ: từ chối 409 `OUTSIDE_PICKUP_WINDOW` (không tự ý gia hạn).
   - **Kiểm tra điều kiện hộp (BR-36):** Toàn bộ các hộp phân bổ (`BoxAllocation`) của đơn phải ở trạng thái `CONFIRMED`, và các hộp vật lý (`PhysicalBox`) tương ứng phải ở trạng thái `ALLOCATED` (không bị `BLOCKED` hay hỏng). Số lượng hộp phân bổ phải khớp với số lượng mua của đơn.
4. Tạo bản ghi `PickupSession` mới:
   - `order = order`
   - `actor = currentAccount`
   - `status = OPEN`
   - `startedAt = clock.instant()`
   - `idempotencyKey = idempotencyKey`
5. Cập nhật `CustomerOrder.status = HANDOVER_IN_PROGRESS`.
6. Ghi `AuditLog` và trả về chi tiết `PickupSessionView`.

### 4.3. Hoàn tất bàn giao (UC-28, BR-31, BR-36, BR-37)
1. Nhân viên giao tận tay toàn bộ số hộp cho khách hàng và xác nhận hoàn tất trên hệ thống kèm `idempotencyKey`.
2. Backend xử lý trong Transaction có khóa bi quan:
   - **Kiểm tra quyền:** Tác nhân có quyền `PICKUP_HANDLE` tại cửa hàng.
   - **Kiểm tra phiên bàn giao:** Phiên phải tồn tại, thuộc đơn của cửa hàng, và có trạng thái `OPEN`.
     - Nếu phiên đã ở trạng thái `COMPLETED` với cùng `idempotencyKey`: trả về kết quả thành công trước đó (Idempotent).
   - **Kiểm tra đơn hàng:** Đơn phải ở trạng thái `HANDOVER_IN_PROGRESS`.
   - **Kiểm tra hộp (BR-37):** Toàn bộ hộp phân bổ phải sẵn sàng để giao. Tuyệt đối không cho phép hoàn tất một phần (nếu thiếu hộp thực tế, phải dừng lại và ghi nhận sự cố qua UC-30).
3. Cập nhật trạng thái nguyên tử trong cùng transaction:
   - `PickupSession.status = COMPLETED`, `completedAt = clock.instant()`.
   - `CustomerOrder.status = COMPLETED`.
   - Chuyển toàn bộ `BoxAllocation` của đơn sang `DELIVERED`.
   - Chuyển toàn bộ `PhysicalBox` tương ứng sang `DELIVERED`.
   - Ghi nhận `FinancialEvent`: loại `ORDER_COMPLETED`, liên kết với đơn, `businessKey = "order:" + order.id + ":ORDER_COMPLETED"`.
   - Ghi `AuditLog`.
4. Trả về kết quả hoàn tất bàn giao thành công.

### 4.4. Ghi nhận sự cố bàn giao (UC-30, BR-31)
1. Nhân viên phát hiện không thể giao hàng (ví dụ: hộp bị rơi vỡ, thực phẩm có dấu hiệu biến chất, thiếu món cam kết, khách từ chối nhận do chất lượng, cửa hàng không chuẩn bị kịp...).
2. Nhân viên gửi yêu cầu ghi nhận sự cố:
   - `orderId`: ID đơn hàng.
   - `reason`: Lý do chi tiết (bắt buộc, không được để trống).
   - `customerArrived`: Khách hàng đã có mặt tại cửa hàng hay chưa (boolean, bắt buộc).
   - `affectedBoxIds`: Danh sách ID các hộp bị ảnh hưởng (tùy chọn).
   - `evidenceAttachmentIds`: Danh sách file đính kèm chứng minh sự cố (nếu có).
3. Backend kiểm tra dưới khóa bi quan:
   - **Kiểm tra quyền:** Quyền `PICKUP_HANDLE` tại cửa hàng của đơn.
   - **Kiểm tra trạng thái đơn:**
     - Nếu đơn đã `COMPLETED`: từ chối với 409 `ORDER_ALREADY_COMPLETED` (đơn đã giao hoàn tất không ghi sự cố trước giao, hướng dẫn khách khiếu nại theo UC-23/T8.1).
     - Nếu đơn đã `CANCELLED` hoặc `EXPIRED`: từ chối 409 `INVALID_ORDER_STATUS`.
4. Xử lý ghi nhận sự cố:
   - Tạo bản ghi `PickupIncident`:
     - `order = order`
     - `session = currentSession` (nếu đang có phiên OPEN)
     - `actor = currentAccount`
     - `reason = reason`
     - `customerArrived = customerArrived`
     - `occurredAt = clock.instant()`
     - `resolution = resolution`
   - Nếu đang có phiên bàn giao `OPEN`: cập nhật `PickupSession.status = CANCELLED`.
   - Đánh dấu hộp bị ảnh hưởng (nếu có lỗi thực phẩm/hư hỏng): chuyển `PhysicalBox.status = BLOCKED` để không lọt vào tồn kho bán lại.
   - **Phân loại xử lý sự cố:**
     - *Trường hợp lỗi từ cửa hàng/thực phẩm không an toàn:* Hệ thống chặn bàn giao, cập nhật lý do và chuyển hướng để thực hiện quy trình hủy do lỗi cửa hàng (hoàn 100% tiền cho khách, không phạt khách - chuẩn bị tích hợp T7.3).
     - *Trường hợp nguyên nhân chưa rõ ràng hoặc cần đối chiếu:* Chuyển trạng thái sang diện quản trị viên soát xét (`resolution = "PENDING_ADMIN_REVIEW"`), không tự tiện quy lỗi cho khách hàng và không xác nhận No-show (A1 của UC-30).
5. Ghi `AuditLog` và trả về thông tin sự cố đã tiếp nhận.

---

## 5. Quy tắc nghiệp vụ đề xuất (BR-T72-xx)

- **BR-T72-01 — Điều kiện mở phiên bàn giao:**
  Chỉ mở phiên bàn giao (`pickup_session` OPEN) khi đồng thời thỏa mãn:
  1. Đơn ở trạng thái `CONFIRMED`.
  2. Thời điểm hiện tại nằm trong khung nhận hàng của đơn (`pickupStartsAt <= now < pickupEndsAt`).
  3. Đơn đã được xác minh thanh toán đủ 100% số tiền (`remainingAmount == 0`).
  4. Toàn bộ các hộp phân bổ ở trạng thái `CONFIRMED`, hộp vật lý ở trạng thái `ALLOCATED`.

- **BR-T72-02 — Độc quyền phiên bàn giao (Mutual Exclusion):**
  Một đơn hàng chỉ được phép có **tối đa một** phiên bàn giao hoạt động (`OPEN` hoặc `RECONCILING`) tại một thời điểm (được bảo vệ bởi partial unique index `uq_order_active_pickup`). Nếu nhân viên khác cố mở phiên trên cùng đơn, hệ thống từ chối 409 `SESSION_ALREADY_OPEN`. Nếu gửi lại cùng `idempotencyKey`, hệ thống trả về phiên hiện tại.

- **BR-T72-03 — Hoàn tất trọn vẹn, không hoàn tất một phần (All-or-Nothing Delivery):**
  Một đơn chỉ được chuyển sang `COMPLETED` khi toàn bộ số hộp phân bổ theo đơn được bàn giao thành công (`DELIVERED`). Không hỗ trợ giao thiếu hoặc hoàn tất một phần (BR-37). Nếu thiếu hộp, phải dừng bàn giao và chuyển sang xử lý sự cố (UC-30).

- **BR-T72-04 — Nguyên tử và ghi nhận sự kiện tài chính:**
  Thao tác hoàn tất bàn giao phải cập nhật trạng thái đơn sang `COMPLETED`, hộp và phân bổ sang `DELIVERED`, và ghi nhận `FinancialEvent` loại `ORDER_COMPLETED` (kèm `business_key` idempotent) trong **cùng một Transaction DB**.

- **BR-T72-05 — Khóa thao tác hủy và No-show khi phiên đang mở (BR-31):**
  Khi đơn đang có phiên bàn giao ở trạng thái `OPEN` hoặc `RECONCILING` (`order.status == HANDOVER_IN_PROGRESS`), hệ thống chặn khách chủ động hủy đơn và chặn tác vụ xác nhận No-show. Chỉ cho phép hủy hoặc xác nhận No-show sau khi phiên bàn giao đã đóng/hủy mà việc giao hàng không diễn ra.

- **BR-T72-06 — Ghi nhận sự cố minh bạch (UC-30):**
  Mọi sự cố dẫn đến việc không thể giao hàng phải được lưu vào `pickup_incident` kèm thông tin tác nhân, thời điểm, lý do cụ thể và ghi nhận khách đã đến hay chưa. Nếu có phiên đang mở, phiên đó phải chuyển sang `CANCELLED`. Hộp bị lỗi chất lượng phải chuyển `BLOCKED`. Không tự động kết luận lỗi do khách hàng khi nguyên nhân chưa rõ ràng.

- **BR-T72-07 — Idempotency cho mọi thao tác ghi:**
  Các API bắt đầu bàn giao (`start`), hoàn tất (`complete`), và ghi nhận sự cố (`incident`) đều yêu cầu `Idempotency-Key` để bảo đảm gửi lại do chập chờn mạng không tạo hai phiên, không hoàn tất hai lần, và không nhân đôi sự kiện tài chính.

- **BR-T72-08 — Kiểm soát truy cập và bảo mật:**
  Chỉ nhân viên có quyền `PICKUP_HANDLE` hoặc Store Owner của chính cửa hàng sở hữu đơn mới được thao tác. Mọi truy vấn và cập nhật phải kiểm tra `storeId` ở phía backend, không tin tưởng dữ liệu do client tự gửi.

---

## 6. Phạm vi

### Trong phạm vi (In-Scope):
1. API tra cứu danh sách đơn cần bàn giao của cửa hàng theo ngày và trạng thái (`GET /api/v1/stores/{storeId}/pickup-orders`).
2. API bắt đầu phiên bàn giao (`POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-sessions/start` hoặc `POST /api/v1/stores/{storeId}/pickup-sessions`).
3. API hoàn tất phiên bàn giao (`POST /api/v1/stores/{storeId}/pickup-sessions/{sessionId}/complete`).
4. API ghi nhận sự cố bàn giao (`POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-incidents`).
5. Cập nhật trạng thái đồng bộ: `CustomerOrder` (`HANDOVER_IN_PROGRESS`, `COMPLETED`), `BoxAllocation` (`DELIVERED`), `PhysicalBox` (`DELIVERED`, `BLOCKED`), `PickupSession` (`OPEN`, `COMPLETED`, `CANCELLED`).
6. Ghi nhận `FinancialEvent` loại `ORDER_COMPLETED` và `AuditLog`.
7. OpenAPI spec đầy đủ cho các endpoint mới.
8. Bộ test tích hợp (Integration tests kế thừa `AuthTestFixtures`) bao phủ đầy đủ các AC về quyền, trạng thái, khung giờ nhận, kiểm tra tài chính, tranh chấp đồng thời và idempotency.

### Ngoài phạm vi (Out-of-Scope):
1. Luồng khách tự hủy đơn hoặc yêu cầu hủy đơn (thuộc T7.3).
2. Tác vụ quét đơn hết hạn nhận hàng để chuyển `PENDING_NO_SHOW` và quy trình xác nhận No-show (thuộc T7.4).
3. Đánh dấu hộp không đạt ngoài quầy bàn giao và ngừng bán vi phạm (thuộc T7.5).
4. Tính toán hoa hồng chi tiết và đối soát doanh thu (thuộc T8.3 & T8.4).
5. Gửi thông báo đẩy/email khi hoàn tất đơn hoặc phát sinh sự cố (thuộc T8.2).
6. UI mobile hoặc giao diện web.

---

## 7. Acceptance Criteria (Given / When / Then)

| ID | Given / When / Then |
|---|---|
| **AC-01** | **Given** nhân viên có quyền `PICKUP_HANDLE` hoặc Store Owner, **When** tra cứu đơn cần bàn giao tại `storeId` theo ngày và trạng thái, **Then** hệ thống trả về danh sách các đơn `CONFIRMED` / `HANDOVER_IN_PROGRESS` kèm thông tin hộp phân bổ, tiền đã xác minh và khung giờ nhận mà không làm thay đổi trạng thái đơn. |
| **AC-02** | **Given** nhân viên không có quyền `PICKUP_HANDLE`, nhân viên cửa hàng khác, hoặc khách hàng, **When** tra cứu đơn bàn giao hoặc thao tác phiên, **Then** hệ thống từ chối với 403 `ACCESS_DENIED`. |
| **AC-03** | **Given** đơn `CONFIRMED` đã thu đủ 100% tiền và thời gian hiện tại nằm trong khung giờ nhận (`pickupStartsAt <= now < pickupEndsAt`), **When** nhân viên có quyền gửi yêu cầu bắt đầu bàn giao với `Idempotency-Key`, **Then** hệ thống tạo `PickupSession` trạng thái `OPEN`, cập nhật đơn sang `HANDOVER_IN_PROGRESS`, và trả về thông tin phiên. |
| **AC-04** | **Given** thời gian hiện tại trước `pickupStartsAt` hoặc sau `pickupEndsAt`, **When** gửi yêu cầu bắt đầu bàn giao, **Then** hệ thống từ chối với 409 `OUTSIDE_PICKUP_WINDOW`, không mở phiên và không đổi trạng thái đơn. |
| **AC-05** | **Given** đơn chưa xác minh đủ tiền thanh toán (`remainingAmount > 0`), **When** gửi yêu cầu bắt đầu bàn giao, **Then** hệ thống từ chối với 409 `PAYMENT_INCOMPLETE`. |
| **AC-06** | **Given** đơn không ở trạng thái `CONFIRMED` (ví dụ `CANCELLED`, `EXPIRED`, `COMPLETED`), **When** gửi yêu cầu bắt đầu bàn giao, **Then** hệ thống từ chối với 409 `INVALID_ORDER_STATUS`. |
| **AC-07** | **Given** hai nhân viên gửi yêu cầu bắt đầu bàn giao đồng thời trên cùng một đơn với hai `idempotencyKey` khác nhau, **When** cả hai request được xử lý, **Then** chỉ đúng một phiên `OPEN` được tạo thành công, request còn lại bị từ chối 409 `SESSION_ALREADY_OPEN` (nhờ partial unique index DB). |
| **AC-08** | **Given** yêu cầu bắt đầu bàn giao đã thành công, **When** gửi lại yêu cầu với cùng `Idempotency-Key`, **Then** hệ thống trả về đúng thông tin phiên hiện tại mà không tạo bản ghi mới (Idempotent). |
| **AC-09** | **Given** phiên bàn giao đang `OPEN` và đơn đang `HANDOVER_IN_PROGRESS`, **When** nhân viên gửi yêu cầu hoàn tất bàn giao với `Idempotency-Key`, **Then** phiên chuyển `COMPLETED` (có `completedAt`), đơn chuyển `COMPLETED`, toàn bộ hộp phân bổ chuyển `DELIVERED`, toàn bộ hộp vật lý chuyển `DELIVERED`, và ghi nhận đúng một `FinancialEvent` loại `ORDER_COMPLETED`. |
| **AC-10** | **Given** phiên đã `COMPLETED`, **When** gửi lại yêu cầu hoàn tất với cùng `Idempotency-Key`, **Then** hệ thống trả về kết quả thành công mà không ghi đúp event tài chính hay sửa đổi dữ liệu lần hai. |
| **AC-11** | **Given** nhân viên phát hiện không thể giao đủ số hộp hoặc thực phẩm hỏng, **When** gửi yêu cầu ghi nhận sự cố (`pickup_incident`) nêu rõ lý do và tình trạng khách đã đến hay chưa, **Then** hệ thống lưu `PickupIncident`, hủy phiên bàn giao đang mở (`CANCELLED`), chặn giao hộp lỗi (`BLOCKED`), và không chuyển đơn sang `COMPLETED`. |
| **AC-12** | **Given** đơn đã hoàn tất (`COMPLETED`), **When** gửi yêu cầu ghi nhận sự cố bàn giao, **Then** hệ thống từ chối với 409 `ORDER_ALREADY_COMPLETED` và hướng dẫn xử lý khiếu nại sau giao. |
| **AC-13** | **Given** đơn đang có phiên bàn giao `OPEN` (`HANDOVER_IN_PROGRESS`), **When** có yêu cầu hủy đơn hoặc tác vụ no-show cố gắng can thiệp, **Then** hệ thống chặn lại theo quy tắc BR-31 cho đến khi phiên kết thúc. |
| **AC-14** | **Given** toàn bộ các endpoint mới, **When** kiểm tra tài liệu và hợp đồng API, **Then** mọi endpoint đều có đặc tả trong `docs/api/openapi.yaml` (bao gồm schema request/response, mã lỗi, ví dụ) và vượt qua `ApiContractTests`. |

---

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn nghiệp vụ | Tóm tắt quy định | AC đáp ứng |
|---|---|---|
| **UC-27** (Trang 74) | Xem đơn cần bàn giao theo ngày và trạng thái, đúng phạm vi quyền, read-only | AC-01, AC-02 |
| **UC-28** (Trang 75–76) | Bàn giao Surprise Box: kiểm tra điều kiện, mở phiên, giao toàn bộ, hoàn tất một lần | AC-03, AC-04, AC-05, AC-06, AC-07, AC-08, AC-09, AC-10 |
| **UC-30** (Trang 77–78) | Ghi nhận sự cố không thể bàn giao, lưu lý do, khách đến hay chưa, chặn giao, chuyển tiếp xử lý | AC-11, AC-12 |
| **BR-31** (Trang 27) | Điều kiện tài chính khi bàn giao; mở phiên trước khi giao thực tế; chặn hủy và no-show khi phiên đang mở | AC-03, AC-05, AC-09, AC-13 |
| **BR-34** (Trang 27) | Khách nhận hàng đúng địa điểm và khung giờ đã xác nhận; không tự gia hạn ngoài khung giờ | AC-04 |
| **BR-36** (Trang 27) | Điều kiện bàn giao: đơn hợp lệ, hộp đúng phân bổ, đủ điều kiện, thanh toán hoàn tất | AC-03, AC-05, AC-06, AC-09 |
| **BR-37** (Trang 28) | Điều kiện hoàn tất: toàn bộ hộp giao thành công, không hỗ trợ hoàn tất một phần | AC-09, AC-11 |
| **CLAUDE.md** §4, 5, 8 | Idempotency, giao dịch nguyên tử tiền/tồn kho, kiểm soát quyền server, OpenAPI và test suite | AC-07, AC-08, AC-09, AC-10, AC-14 |

---

## 9. Quyết định cần duyệt tại Gate A

1. **Q1 (Format & Endpoint design):**  
   Đề xuất thiết kế các endpoint sau dưới `/api/v1/stores/{storeId}`:
   - `GET /api/v1/stores/{storeId}/pickup-orders`: Xem danh sách đơn cần bàn giao (tham số `date`, `status`).
   - `POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-sessions/start`: Bắt đầu phiên bàn giao (yêu cầu header `Idempotency-Key`).
   - `POST /api/v1/stores/{storeId}/pickup-sessions/{sessionId}/complete`: Hoàn tất phiên bàn giao (yêu cầu header `Idempotency-Key`).
   - `POST /api/v1/stores/{storeId}/orders/{orderId}/pickup-incidents`: Ghi nhận sự cố không thể bàn giao.  
   *Người dùng có đồng ý với thiết kế URL và phân tách resource này không?*

2. **Q2 (Bằng chứng đính kèm trong PickupIncident):**  
   Bảng `pickup_incident` trong schema DB V1 chưa có cột khóa ngoại trỏ tới `attachment`. Đề xuất tại T7.2:
   - Cho phép đính kèm danh sách `attachmentIds` (UUID) trong request payload của sự cố và lưu mã tham chiếu vào trường `resolution` hoặc bổ sung một migration mới (ví dụ V19) thêm cột hoặc liên kết `attachment`.
   - *Đề xuất ưu tiên:* Tạm thời lưu danh sách ID ảnh bằng chứng vào trường `resolution` dạng text/json có cấu trúc, hoặc chỉ cần lưu `reason` và `customerArrived` theo đúng schema V1 để tránh tạo migration không cần thiết nếu chưa bắt buộc. Người dùng duyệt phương án nào?

3. **Q3 (Chặn hủy và No-show theo BR-31):**  
   Khi đơn đang ở `HANDOVER_IN_PROGRESS` (có phiên `OPEN`), API hủy đơn (sẽ làm ở T7.3) và Job No-show (sẽ làm ở T7.4) sẽ kiểm tra trạng thái đơn: nếu `HANDOVER_IN_PROGRESS` thì chặn xử lý. Để chuẩn bị trước cho T7.3 và T7.4, T7.2 sẽ đảm bảo trạng thái `HANDOVER_IN_PROGRESS` được chuyển đổi chuẩn xác ngay khi mở phiên và hoàn tất/hủy phiên.  
   *Người dùng có đồng ý không?*

4. **Q4 (Quy định thực hiện task tuần tự):**  
   Người dùng yêu cầu "triển khai task 7.2 và 7.3". Tuy nhiên, theo `CLAUDE.md` §1 ("Mỗi phiên chỉ làm đúng task do người dùng chỉ định") và §10 ("Cấm làm nhiều task trong một phiên"), đồng thời Task 7.3 có điều kiện tiên quyết là Task 7.2 phải hoàn thành (`[x]`). Do đó, phiên này chúng ta tập trung hoàn thành trọn vẹn Task 7.2 qua Gate A → Gate B → Code → Verify → Đóng task 7.2; sau đó mới chuyển sang mở phiên cho Task 7.3.  
   *Người dùng xác nhận quy trình này?*

---

## 10. Ràng buộc cho Gate B

- Không viết code sản phẩm trước khi Gate A và Gate B được phê duyệt.
- `PickupSession` và `PickupIncident` phải tận dụng schema và index sẵn có từ V1/V2; nếu cần migration mới phải cân nhắc kỹ và giải trình trong Gate B.
- Thao tác chuyển trạng thái đơn, hộp, allocation và ghi nhận `FinancialEvent` phải gói gọn trong một `@Transactional` duy nhất.
- Tất cả các endpoint phải có mô tả đầy đủ trong `docs/api/openapi.yaml`.
- Integration tests phải kiểm thử đầy đủ các nhánh lỗi, ngoài khung giờ nhận hàng, lỗi thiếu tiền, tranh chấp mở phiên đồng thời, và idempotency.
