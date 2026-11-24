# Business Definition: T7.4 — No-show và kiểm tra lại hộp

**Status:** Chờ duyệt Gate A  
**Updated:** 2026-11-24  
**Scope:** T7.4 (Job chuyển đơn quá hạn nhận sang `PENDING_NO_SHOW`; nhân viên/chủ cửa hàng xem đơn quá hạn, xác nhận khách không đến nhận kèm căn cứ, tính giữ/hoàn qua `NoShowAmounts`, tạo `RefundRequest` qua T6.5, giữ khoản bù 48h kể từ khi có căn cứ khách nhận thông báo; kiểm tra lại hộp No-show trước khi chặn hoặc đưa vào đợt/khung giờ mới phù hợp).  
**Phụ thuộc:** T7.3 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Bối cảnh và mục tiêu

Sau khi T7.2 (phiên bàn giao & sự cố) và T7.3 (hủy đơn & hoàn tiền) đã hoàn thành, hệ thống cần xử lý nghiệp vụ cho các đơn hàng đã xác nhận (`CONFIRMED`) nhưng kết thúc khung giờ nhận hàng mà khách chưa hoàn tất nhận hàng:

1. **Chuyển đơn quá hạn nhận (BR-40):** Sau khi qua `pickup_ends_at`, job tự động chuyển các đơn `CONFIRMED` chưa bàn giao (và không có phiên bàn giao `OPEN`/`RECONCILING` theo BR-31) sang `PENDING_NO_SHOW`.
2. **Xem đơn quá hạn nhận (UC-36):** Chủ cửa hàng hoặc nhân viên có quyền `NO_SHOW_CONFIRM` tra cứu danh sách đơn quá hạn / chờ xác minh No-show cùng khung giờ nhận, thông tin hộp, số tiền đã thanh toán/dự kiến giữ-hoàn và lịch sử sự cố bàn giao (`pickup_incident`) nếu có.
3. **Xác nhận khách không đến nhận (UC-37, BR-41, BR-42, BR-43, BR-51):**
   - Chỉ xác nhận khi đã quá `pickup_ends_at`, chưa bàn giao xong, không có phiên bàn giao đang mở, **không** có sự cố `"khách đã đến"` (`customerArrived = true`, nếu có phải xử lý theo UC-30), cửa hàng mở đúng giờ và có đủ hộp đạt cam kết sẵn sàng bàn giao.
   - Chuyển đơn sang `NO_SHOW`, vô hiệu hóa mã nhận hàng, giải phóng `BoxAllocation` sang `RELEASED`, đưa toàn bộ hộp liên quan sang `AWAITING_INSPECTION` (**không** tự động mở bán lại — BR-44).
   - Tính khoản giữ lại và hoàn trả bằng `NoShowAmounts` theo `no_show_retention_rate` đã snapshot trên đơn (`retained + refund = verifiedPaid`, `retained <= verifiedPaid`, hoa hồng bán hàng `= 0`), mở `RefundRequest` (`PENDING`) qua T6.5 nếu `refundAmount > 0`, ghi `FinancialEvent(NO_SHOW_CONFIRMED)` và `AuditLog`.
   - Khoản bù đắp No-show giữ trạng thái `HELD` tối thiểu `disputeWindowHours` (48 giờ) kể từ khi có căn cứ khách nhận thông báo (`notification_received_at`); nếu chưa có căn cứ nhận thông báo thì **chưa tự tính hết hạn** (UC-37 E3, D06).
4. **Kiểm tra lại hộp No-show (UC-35, BR-44):** Chủ cửa hàng hoặc nhân viên có quyền `BOX_MANAGE` kiểm tra lại từng hộp ở trạng thái `AWAITING_INSPECTION`, ghi bản ghi `BoxInspection` bất biến (`PASSED`/`FAILED`). Hộp `FAILED` bị chuyển `BLOCKED`; hộp `PASSED` giữ lịch sử cũ và chỉ chuyển `AVAILABLE` khi được gán vào một `SaleBatch` mới phù hợp (cùng cửa hàng, còn mở bán/chuẩn bị, `pickupEndsAt > now`, `safeUntil >= batch.pickupEndsAt`), ngược lại giữ ngoài lượng bán (`AWAITING_INSPECTION`).

Nguồn nghiệp vụ trích xuất từ `../../DOCX/BaoCaoFoodRescue.pdf`:
- **Trang 28–29:** BR-40 (Xác định đơn quá hạn nhận), BR-41 (Điều kiện xác nhận No-show), BR-42 (Chính sách tài chính khi khách không đến nhận), BR-43 (Thời hạn phản ánh và xử lý khoản tiền No-show), BR-44 (Xử lý hộp không được nhận), BR-51 (trách nhiệm xác minh/bằng chứng No-show).
- **Trang 70:** D06 (Quy tắc phản ánh No-show 48 giờ; chưa có căn cứ nhận thông báo thì chưa tự tính hết hạn).
- **Trang 82–85:** UC-35 (Kiểm tra lại hộp No-show — chính, A1, A2, E1), UC-36 (Xem đơn quá hạn nhận — chính, A1, E1, E2), UC-37 (Xác nhận khách không đến nhận — chính, A1, A2, E1, E2, E3).

---

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin dưới `src/main/kotlin/com/foodrescue/backend/`:

1. **Domain & DB đã tạo sẵn từ V1–V3, V7, V14, V18:**
   - `order/OrderStatus.kt` đã có `PENDING_NO_SHOW` và `NO_SHOW`.
   - `surprisebox/BoxStatus.kt` đã có `AWAITING_INSPECTION`, `AVAILABLE`, `BLOCKED`.
   - `V7__staff_permissions_and_invitations.sql` đã seed hai quyền nhân viên chuẩn cho T7.4:
     - `NO_SHOW_CONFIRM` ("Confirm a customer no-show") — cho UC-36, UC-37.
     - `BOX_MANAGE` ("Inspect boxes, mark boxes unfit, recheck no-show boxes") — cho UC-35.
   - `pickup/entity/NoShowCase.kt` và bảng `no_show_case` (`V1`, `V2`): `order_id` (`UNIQUE`), `verified_by_id`, `verified_at`, `evidence`, `notification_id` (`UNIQUE`), `notification_received_at`, `dispute_deadline`, `retained_amount` (`>= 0`, nguyên đồng), `compensation_status` (`HELD`, `DISPUTED`, `ELIGIBLE`, `FORFEITED`, `SETTLED`), kèm DB CHECK `((notification_received_at IS NULL) = (dispute_deadline IS NULL))` và `dispute_deadline > notification_received_at`.
   - `finance/NoShowAmounts.kt` và `NoShowAmountsTests.kt`: đã cài đặt công thức `retained = (orderTotal * retentionRate).setScale(0, HALF_UP).min(verifiedAmount)`, `refund = verifiedAmount - retained`, `commission = 0`.
   - `finance/entity/FinancialEvent.kt` và `V2`: hỗ trợ `FinancialEventType.NO_SHOW_CONFIRMED` với `no_show_case_id IS NOT NULL` và FK `(no_show_case_id, order_id) REFERENCES no_show_case(id, order_id)`. `V3` bảo vệ `financial_event` và `box_inspection` bằng trigger bất biến (`immutable_*`).
   - `V18`: trigger `protect_order_terms()` cho phép cập nhật `status`, `pickup_code_hash`, `pickup_code_encrypted`, `pickup_code_key_id`, `status_reason`, `status_changed_at`, và constraint `ck_order_pickup_code_storage` yêu cầu khi vô hiệu hóa mã nhận thì phải xóa đồng thời cả 3 cột (`pickup_code_hash`, `pickup_code_encrypted`, `pickup_code_key_id` cùng `NULL`).
   - `notification/entity/Notification.kt` (`V1`): đã có `NotificationType.NO_SHOW` và `NotificationStatus.PENDING`, nhưng service/controller gửi thông báo và đánh dấu đã nhận thuộc **T8.2** (chưa triển khai).
   - `file/entity/Attachment.kt` (`V1`, `V19`): đã có cột `no_show_case_id`, nhưng chưa có `AttachmentAccessPolicy` cho `no_show_case_id`.
2. **Điểm liên thông với T7.2 & T7.3:**
   - `PickupCodeService.usable(o)` hiện kiểm tra `o.status == OrderStatus.CONFIRMED && clock.instant().isBefore(o.pickupEndsAt)`, nên khi quá giờ hoặc sang `PENDING_NO_SHOW`/`NO_SHOW` mã không thể dùng để nhận hàng; tuy nhiên bước xác nhận No-show vẫn xóa sạch 3 cột mã nhận để vô hiệu vĩnh viễn.
   - `PickupIncidentService.recordIncident` (T7.2) hiện mới cho phép ghi sự cố trên đơn `CONFIRMED` hoặc `HANDOVER_IN_PROGRESS`. Nếu job đã chuyển đơn sang `PENDING_NO_SHOW` nhưng nhân viên phát hiện sự cố khách đã đến đúng giờ (UC-37 A1 → thực hiện UC-30), cần xem xét cho phép `PENDING_NO_SHOW` ghi `PickupIncident` (xem Q2).
   - `PaidOrderCancellationExecutor` (T7.3) đã chặn hủy đơn khi đơn ở `PENDING_NO_SHOW` hoặc `NO_SHOW` (`409 INVALID_ORDER_STATUS`).
   - `BoxPackingService`, `BoxQueryService`, `BatchAvailability`: `physical_box` có FK `(current_batch_id, store_id) REFERENCES sale_batch(id, store_id)` (V2) và **không** bị trigger bất biến khóa cập nhật `status`/`current_batch_id`, nên có thể gán hộp `PASSED` sang một `SaleBatch` mới cùng cửa hàng trong khi vẫn giữ nguyên toàn bộ `box_item`, `box_inspection` và `box_allocation` cũ.
3. **Baseline kiểm thử:**
   - `./gradlew.bat build --console=plain`: **BUILD SUCCESSFUL** (toàn bộ 19 migration V1–V19, `43` entity, `101` path OpenAPI / `25` tag đều pass trước T7.4).

---

## 3. Người dùng và quyền

- **Chủ cửa hàng (`Store Owner`):**
  - Tự động có đầy đủ quyền nghiệp vụ tại cửa hàng mình sở hữu (`store.owner.id == actorId`) thông qua `StoreAuthorizationService.requirePermission`.
- **Nhân viên cửa hàng được phân quyền (`Store Staff`):**
  - `NO_SHOW_CONFIRM`: xem danh sách/chi tiết đơn quá hạn nhận (UC-36) và xác nhận khách không đến nhận (UC-37).
  - `BOX_MANAGE`: xem danh sách hộp chờ kiểm tra lại và thực hiện kiểm tra lại hộp No-show (UC-35). (Mở rộng `BoxAccess` cho phép xem chi tiết hộp theo quyền `BOX_MANAGE` như đã có sẵn).
  - Nhân viên không có quyền tương ứng, hoặc thuộc cửa hàng khác: **403 `ACCESS_DENIED`** (hoặc **404 `NOT_FOUND`** nếu tài nguyên không thuộc cửa hàng).
- **Khách hàng (`CUSTOMER`) & `ADMIN`:**
  - Khách hàng **chỉ** có quyền tải file đính kèm (`NoShowAttachmentPolicy`) và xem thông tin No-show trên đơn của chính mình (nếu mở endpoint đọc/chi tiết đơn khách hàng hoặc qua `OrderQueryService`), không được gọi API vận hành cửa hàng (`403 ACCESS_DENIED`).
  - `ADMIN` có quyền đọc file đính kèm `no_show_case` (`NoShowAttachmentPolicy`) để phục vụ xử lý khiếu nại ở giai đoạn sau.
- **Tác nhân hệ thống (Scheduled Job):**
  - Quét định kỳ sau `pickup_ends_at` để chuyển `CONFIRMED` → `PENDING_NO_SHOW` bằng `FOR UPDATE SKIP LOCKED`, không xung đột với transaction bàn giao/xác nhận đồng thời.
- **Chưa đăng nhập / token không hợp lệ:** **401 `UNAUTHORIZED`**.

---

## 4. Luồng chính

### 4.1. Job tự động chuyển đơn quá hạn nhận sang `PENDING_NO_SHOW` (BR-40)
1. Job định kỳ (tương tự `OrderExpiryJob` / `OrderExpiryService`, dùng `Clock`) tìm theo lô các `customer_order` thỏa đồng thời:
   - `status = 'CONFIRMED'`
   - `pickup_ends_at <= :now`
   - Không có `pickup_session` đang ở trạng thái `'OPEN'` hoặc `'RECONCILING'` (BR-31).
2. Khóa từng dòng bằng `FOR UPDATE SKIP LOCKED` để hai job chạy song song hoặc transaction bàn giao/hủy đang khóa đơn không xử lý trùng.
3. Trong transaction của từng đơn, kiểm tra lại điều kiện:
   - Chuyển `order.status = PENDING_NO_SHOW`, `statusReason = "PICKUP_WINDOW_ENDED"`, `statusChangedAt = now`.
   - Không giải phóng `BoxAllocation`, không đổi trạng thái `PhysicalBox`, chưa tạo `NoShowCase` hay hoàn tiền (vì còn chờ nhân viên xác minh tại UC-37).
   - Ghi `AuditLog` (`ORDER_MARKED_PENDING_NO_SHOW`).

### 4.2. Xem danh sách đơn quá hạn nhận (UC-36)
1. Chủ cửa hàng / nhân viên có quyền `NO_SHOW_CONFIRM` mở chức năng lọc theo ngày (`date`, mặc định hôm nay theo timezone cửa hàng hoặc tùy chọn bỏ lọc ngày) và trạng thái (`PENDING_NO_SHOW`, hoặc lọc cả `CONFIRMED` đã quá `pickup_ends_at`, `NO_SHOW`).
2. Hệ thống kiểm tra quyền trên `storeId` và thực hiện truy vấn read-only:
   - Trả về thông tin đơn (`orderId`, `orderCode`, `status`, `pickupStartsAt`, `pickupEndsAt`, `quantity`, `customerDisplayName`), các hộp đã phân bổ (`AllocatedBoxView`), thông tin tiền (`OrderMoneyView` kèm `projectedRetainedAmount`, `projectedRefundAmount` tính từ `NoShowAmounts`), trạng thái phiên bàn giao đang mở (nếu có), danh sách `PickupIncident` (nhấn mạnh cờ `customerArrived`), và bản ghi `NoShowCase` (nếu đã xác nhận).
3. Không sửa bất kỳ trạng thái hay tạo giao dịch nào; danh sách rỗng trả `200 []` (UC-36 A1).

### 4.3. Xác nhận khách không đến nhận (UC-37, BR-41..43, BR-51)
1. Chủ cửa hàng / nhân viên (`NO_SHOW_CONFIRM`) chọn đơn và gửi yêu cầu xác nhận kèm `Idempotency-Key` (tùy chọn nhưng khuyến nghị), `storeOpenedOnTime` (Boolean), `boxesReadyAndCompliant` (Boolean), `evidence` (chuỗi căn cứ bắt buộc, không rỗng) và `attachmentIds` (tùy chọn).
2. Hệ thống kiểm tra quyền trên `storeId`, validate `Idempotency-Key` và khóa bi quan dòng `CustomerOrder` (`findForUpdate`):
   - Kiểm tra đơn thuộc `storeId` (nếu không → `404`).
   - **Idempotency (UC-37 E2):** Nếu đơn đã có `NoShowCase`:
     - Nếu `Idempotency-Key` trùng với `idempotencyKey` đã lưu và `requestFingerprint` khớp → trả `200 OK` với kết quả `NoShowCase` hiện có, không tạo bản ghi/khoản giữ/hoàn trùng.
     - Ngược lại → trả `409 NO_SHOW_ALREADY_CONFIRMED` (hoặc `409 IDEMPOTENCY_CONFLICT` nếu trùng key khác payload).
   - **Kiểm tra điều kiện chặn (BR-31, BR-41, UC-37 E1, A1):**
     - `order.status == COMPLETED` → `409 ORDER_ALREADY_COMPLETED`.
     - `order.status in [CANCELLED, EXPIRED]` → `409 INVALID_ORDER_STATUS`.
     - `now < order.pickupEndsAt` → `409 PICKUP_WINDOW_NOT_ENDED` (chưa hết giờ nhận hàng).
     - Tồn tại `PickupSession` `OPEN` hoặc `RECONCILING` → `409 HANDOVER_IN_PROGRESS`.
     - Tồn tại `PickupIncident` trên đơn có `customerArrived == true` → `409 CUSTOMER_ARRIVED_INCIDENT` ("Khách đã đến nhận; phải xử lý theo luồng sự cố bàn giao UC-30, không được xác nhận No-show").
     - Nếu `storeOpenedOnTime == false` hoặc `boxesReadyAndCompliant == false` → `409 NO_SHOW_PRECONDITION_FAILED` (hoặc `400 VALIDATION_FAILED`, hướng dẫn xử lý UC-30 / hủy lỗi cửa hàng T7.3).
     - Trạng thái hợp lệ để xác nhận: `PENDING_NO_SHOW`, hoặc `CONFIRMED` (nếu `now >= pickupEndsAt` mà job chưa kịp chạy), hoặc `HANDOVER_IN_PROGRESS` (đã hủy phiên bởi sự cố T7.2 mà `customerArrived == false`).
3. **Tính toán tài chính (BR-42):**
   - Đọc `verifiedPaid` (và khóa các dòng `Payment` `VERIFIED` chưa có `RefundRequest` nếu cần tạo hoàn).
   - Tính `amounts = NoShowAmounts.calculate(order.totalAmount, verifiedPaid, order.noShowRetentionRate)`:
     - `retainedAmount = min(roundHalfUp(orderTotal * noShowRetentionRate), verifiedPaid)`
     - `refundAmount = verifiedPaid - retainedAmount`
     - `commissionAmount = 0`
   - Bảo đảm `retainedAmount + refundAmount == verifiedPaid` và `retainedAmount <= verifiedPaid`.
4. **Cập nhật nguyên tử trong 1 transaction:**
   - `order.status = NO_SHOW`, `statusReason = "NO_SHOW_CONFIRMED: <evidence>"`, `statusChangedAt = now`, và xóa `pickupCodeHash = null`, `pickupCodeEncrypted = null`, `pickupCodeKeyId = null`.
   - Khóa toàn bộ `BoxAllocation` (`HELD`/`CONFIRMED`) theo thứ tự `box.id`: chuyển `RELEASED`, `releasedAt = now`, `releaseReason = "NO_SHOW_CONFIRMED"`.
   - Khóa từng `PhysicalBox` tương ứng: chuyển sang `BoxStatus.AWAITING_INSPECTION` (nếu hộp đang `BLOCKED` do sự cố trước đó thì giữ `BLOCKED`, tuyệt đối **không** tự động về `AVAILABLE` — BR-44).
   - Tạo bản ghi `Notification` (`recipient = order.customer`, `order = order`, `type = NotificationType.NO_SHOW`, `status = NotificationStatus.PENDING`, `payload = JSON tóm tắt`) để T8.2 gửi đi. Để `notificationReceivedAt = null` và `disputeDeadline = null` tại thời điểm xác nhận (UC-37 E3, D06).
   - Tạo `NoShowCase` với `compensationStatus = CompensationStatus.HELD`, `retainedAmount = amounts.retainedAmount`, gắn `attachmentIds` qua `attachment.no_show_case_id` (`NoShowAttachmentPolicy`).
   - Nếu `amounts.refundAmount > 0`: tạo `RefundRequest` (`PENDING`, `reasonCode = "NO_SHOW_REFUND"`, `requestedAmount = amounts.refundAmount`) qua cơ chế T6.5 gắn với `Payment` `VERIFIED` còn dư. Nếu `amounts.refundAmount == 0`: **không** tạo `RefundRequest` 0đ.
   - Ghi `FinancialEvent(order, type = NO_SHOW_CONFIRMED, noShowCase = case, occurredAt = now, businessKey = "NO_SHOW_CONFIRMED:${order.id}")`.
   - Ghi `AuditLog` qua `StoreHistoryRecorder`.

### 4.4. Kiểm tra lại hộp No-show (UC-35, BR-44)
1. Chủ cửa hàng / nhân viên (`BOX_MANAGE`) xem danh sách hộp đang `AWAITING_INSPECTION` của cửa hàng (kèm thông tin lần kiểm tra gần nhất và lịch sử đơn No-show/allocation gần nhất mà không xóa lịch sử).
2. Nhân viên chọn hộp cần kiểm tra lại và gửi: `result` (`PASSED` hoặc `FAILED`), `safeUntil`, `notes` (bắt buộc nếu `FAILED`), và tùy chọn `targetBatchId` (đợt bán mới để đưa hộp quay lại mở bán).
3. Hệ thống kiểm tra quyền `BOX_MANAGE` trên `storeId`, khóa `PhysicalBox` (`findForUpdate`):
   - Kiểm tra hộp thuộc `storeId` (nếu không → `404`).
   - Kiểm tra `box.status == BoxStatus.AWAITING_INSPECTION` (UC-35 E1: nếu hộp đang `AVAILABLE`/`RESERVED`/`ALLOCATED`/`DELIVERED`/`BLOCKED` → `409 BOX_NOT_AWAITING_INSPECTION`).
   - Kiểm tra không có `BoxAllocation` đang hoạt động (`HELD`/`CONFIRMED`) trên hộp (UC-35 bước 4 → `409 BOX_ALLOCATED`).
   - Kiểm tra hộp có lịch sử liên quan tới đơn `NO_SHOW` (đảm bảo đúng luồng tái kiểm tra hộp No-show, giữ nguyên mọi `BoxAllocation` cũ).
4. **Xử lý theo kết quả kiểm tra:**
   - **Nhánh `FAILED` (UC-35 A1):**
     - Bắt buộc `notes` không rỗng.
     - Tạo bản ghi `BoxInspection(box, inspector, now, FAILED, safeUntil, notes)`.
     - Chuyển `box.status = BoxStatus.BLOCKED` (chỉ cập nhật `box.safeUntil = safeUntil` nếu `safeUntil.isAfter(box.packedAt)` để tôn trọng CHECK DB `safe_until > packed_at`). Hộp không được đưa bán lại.
   - **Nhánh `PASSED`:**
     - Bắt buộc `safeUntil.isAfter(now)` (còn an toàn tại thời điểm kiểm tra).
     - Tạo bản ghi `BoxInspection(box, inspector, now, PASSED, safeUntil, notes)`.
     - Cập nhật `box.safeUntil = safeUntil`.
     - **Nếu có `targetBatchId` (đưa vào đợt/khung giờ mới phù hợp — BR-44):**
       - Khóa `targetBatch` (`SaleBatchRepository.findForUpdate`), kiểm tra thuộc cùng `storeId` (`404` nếu không), `targetBatch.status in [DRAFT, OPEN, PAUSED]`, `targetBatch.pickupEndsAt.isAfter(now)`, `!safeUntil.isBefore(targetBatch.pickupEndsAt)`, và `box.totalListedValue >= targetBatch.minimumValue`.
       - Nếu không đạt bất kỳ điều kiện nào → `409 TARGET_BATCH_INELIGIBLE` (không mở bán).
       - Nếu đạt → gán `box.currentBatch = targetBatch`, chuyển `box.status = BoxStatus.AVAILABLE` (đạt cam kết trong `BatchAvailability`).
     - **Nếu `targetBatchId == null` (UC-35 A2 — chưa có khung giờ mới phù hợp):**
       - Giữ `box.status = BoxStatus.AWAITING_INSPECTION` (ngoài lượng bán, không tăng tồn bán trên đợt cũ đã hết hạn).
5. Ghi `AuditLog` (`BOX_RECHECKED`) qua `StoreHistoryRecorder`.

---

## 5. Quy tắc nghiệp vụ đề xuất (BR-T74-xx)

- **BR-T74-01 — Quét đơn quá hạn không chạm phiên đang bàn giao (BR-40, BR-31):**
  Đơn `CONFIRMED` quá `pickup_ends_at` chỉ chuyển `PENDING_NO_SHOW` khi không có `PickupSession` `OPEN`/`RECONCILING`. Mọi quét tự động phải dùng `FOR UPDATE SKIP LOCKED` và `Clock`.
- **BR-T74-02 — Điều kiện xác nhận No-show và chặn khi khách đã đến (BR-41, UC-37 A1/E1):**
  Cấm xác nhận No-show khi chưa hết `pickup_ends_at`, khi đơn đã `COMPLETED`/`CANCELLED`/`EXPIRED`, khi đang có phiên bàn giao `OPEN`/`RECONCILING`, khi cửa hàng không mở đúng giờ/không đủ hộp đạt, hoặc khi đơn có `PickupIncident` với `customerArrived = true`.
- **BR-T74-03 — Tách bạch tiền giữ lại, hoàn trả và hoa hồng (BR-42):**
  Dùng `NoShowAmounts.calculate`: `retainedAmount` nguyên đồng, `<= verifiedPaid`, `retainedAmount + refundAmount == verifiedPaid`, hoa hồng bán hàng `= 0`. Phần `refundAmount > 0` tạo `RefundRequest` (`PENDING`) qua T6.5; `refundAmount == 0` không tạo bản ghi hoàn 0đ.
- **BR-T74-04 — Tạm giữ bù đắp và mốc 48 giờ theo D06 (BR-43, UC-37 E3):**
  Khoản bù đắp khởi tạo ở trạng thái `HELD`. Thời hạn phản ánh (`dispute_deadline = notification_received_at + disputeWindowHours`) **chỉ được tính khi có căn cứ khách nhận thông báo** (`notification_received_at != null`). Khi `notification_received_at == null`, `dispute_deadline` phải giữ `null` và hệ thống không tự chuyển sang hết hạn phản ánh / đủ điều kiện đối soát.
- **BR-T74-05 — Hộp No-show không tự mở bán lại (BR-44, UC-35):**
  Khi xác nhận No-show, hộp đang phân bổ của đơn chuyển `AWAITING_INSPECTION` (không về `AVAILABLE`). Khi kiểm tra lại, hộp `FAILED` chuyển `BLOCKED`; hộp `PASSED` chỉ trở thành `AVAILABLE` khi gắn vào `SaleBatch` mới cùng cửa hàng còn hạn và đạt `safeUntil >= pickupEndsAt` & `totalListedValue >= minimumValue`, luôn giữ nguyên lịch sử `BoxAllocation`/`BoxInspection`/`NoShowCase`.
- **BR-T74-06 — Idempotency và chống xác nhận trùng (UC-37 E2):**
  Thực hiện xác nhận No-show dưới khóa bi quan `CustomerOrder`. Gửi lại yêu cầu cùng `Idempotency-Key` và cùng nội dung trả lại kết quả cũ (`NoShowCase` duy nhất, 1 khoản giữ, 1 `RefundRequest`, 1 `FinancialEvent`); tranh chấp đồng thời với bàn giao hoặc xác nhận khác chỉ cho tối đa một giao dịch thành công.
- **BR-T74-07 — Bảo toàn lịch sử và quyền truy cập (CLAUDE.md §5, §8):**
  Không xóa/sửa các bản ghi lịch sử (`box_inspection`, `financial_event`, `box_allocation`, `audit_log`); kiểm tra quyền theo từng cửa hàng (`NO_SHOW_CONFIRM` vs `BOX_MANAGE` vs `Owner`) ở tầng service dưới khóa giao dịch.

---

## 6. Phạm vi

### Trong phạm vi (In-Scope):
1. Service + Scheduled job quá hạn nhận (`NoShowOverdueService`, `NoShowOverdueJob`) chuyển `CONFIRMED` → `PENDING_NO_SHOW`.
2. API xem danh sách đơn quá hạn nhận (UC-36) cho `NO_SHOW_CONFIRM` / Owner.
3. API xác nhận khách không đến nhận (UC-37) cho `NO_SHOW_CONFIRM` / Owner, tích hợp `NoShowAmounts`, T6.5 `RefundRequest`, `FinancialEvent(NO_SHOW_CONFIRMED)`, `Notification(NO_SHOW, PENDING)`, `NoShowAttachmentPolicy`, `AuditLog`.
4. API kiểm tra lại hộp No-show (UC-35) + danh sách hộp chờ kiểm tra cho `BOX_MANAGE` / Owner, ghi `BoxInspection` bất biến và gán lại `SaleBatch` mới hợp lệ.
5. Cơ chế domain ghi nhận mốc `notification_received_at` (chuẩn bị móc nối cho T8.2) và job/service nâng trạng thái `HELD` → `ELIGIBLE` chỉ khi `notification_received_at != null && now >= dispute_deadline` (tuân thủ D06) — tùy thuộc quyết định Q3.
6. Cập nhật `docs/api/openapi.yaml` và viết bộ integration tests đầy đủ (bao gồm đồng thời bàn giao vs no-show, idempotency, D06, quyền nhân viên `NO_SHOW_CONFIRM` vs `BOX_MANAGE`).

### Ngoài phạm vi (Out-of-Scope):
1. Gửi thực tế thông báo đẩy/email/outbox worker và API đánh dấu đã đọc thông báo của khách hàng (T8.2).
2. Luồng khiếu nại No-show của khách hàng & admin giải quyết tranh chấp (`DISPUTED` / `FORFEITED` — T8.1).
3. Sinh `LedgerEntry` đối soát/chi trả khoản bù đắp No-show cho cửa hàng (`SETTLED` — T8.3, T8.4).
4. Đánh dấu hộp không đạt ngoài quầy không gắn No-show (UC-34) và khóa cửa hàng có đơn tồn (T7.5).

---

## 7. Acceptance Criteria (Given / When / Then)

| ID | Given / When / Then |
|---|---|
| **AC-01** | **Given** đơn `CONFIRMED` đã qua `pickup_ends_at` và không có `PickupSession` `OPEN`/`RECONCILING`, **When** job quá hạn nhận chạy theo `Clock`, **Then** đơn chuyển sang `PENDING_NO_SHOW`, giữ nguyên allocation/hộp, không chạy trùng khi hai luồng quét cùng lúc. |
| **AC-02** | **Given** đơn `CONFIRMED` hoặc `HANDOVER_IN_PROGRESS` đang có `PickupSession` `OPEN`/`RECONCILING` dù đã qua `pickup_ends_at`, **When** job quét hoặc nhân viên gọi xác nhận No-show, **Then** job bỏ qua đơn đó và API xác nhận trả `409 HANDOVER_IN_PROGRESS`. |
| **AC-03** | **Given** chủ cửa hàng hoặc nhân viên có quyền `NO_SHOW_CONFIRM`, **When** gọi API xem đơn quá hạn nhận (UC-36), **Then** trả danh sách đơn quá hạn kèm khung giờ, hộp, tiền, sự cố và `NoShowCase` (nếu có) mà không thay đổi DB; kết quả rỗng trả `200 []`. |
| **AC-04** | **Given** đơn ở `PENDING_NO_SHOW` (hoặc `CONFIRMED` đã qua `pickup_ends_at`), không có sự cố `customerArrived=true`, không có phiên bàn giao mở, **When** `NO_SHOW_CONFIRM`/Owner xác nhận No-show kèm `storeOpenedOnTime=true`, `boxesReadyAndCompliant=true` và `evidence`, **Then** tạo 1 `NoShowCase` (`HELD`, `retained = NoShowAmounts.retainedAmount`), đơn → `NO_SHOW`, mã nhận bị xóa vô hiệu, allocation → `RELEASED`, hộp → `AWAITING_INSPECTION` (không về `AVAILABLE`), tạo `FinancialEvent(NO_SHOW_CONFIRMED)` và `Notification(NO_SHOW, PENDING)`. |
| **AC-05** | **Given** đơn No-show có `verifiedPaid > retainedAmount` (vd: 100.000đ, giữ 30%), **When** xác nhận No-show, **Then** `retainedAmount = 30.000`, `refundAmount = 70.000` (`retained + refund = verifiedPaid`), hoa hồng `= 0`, tạo đúng 1 `RefundRequest` (`PENDING`, `70.000`, `NO_SHOW_REFUND`); nếu `refundAmount == 0` thì không tạo `RefundRequest` 0đ. |
| **AC-06** | **Given** đơn chưa hết `pickup_ends_at`, hoặc đã `COMPLETED`/`CANCELLED`/`EXPIRED`, hoặc có `PickupIncident` với `customerArrived=true`, hoặc `storeOpenedOnTime=false`/`boxesReadyAndCompliant=false`, **When** xác nhận No-show, **Then** bị từ chối `409` nhất quán, không tạo `NoShowCase` hay đổi trạng thái hộp/tiền. |
| **AC-07** | **Given** xác nhận No-show lặp lại với cùng `Idempotency-Key` và cùng nội dung, hoặc xử lý đồng thời bàn giao vs xác nhận No-show trên cùng đơn, **When** gửi lặp hoặc chạy song song, **Then** gửi lặp trả lại đúng kết quả cũ với 1 khoản giữ & 1 `RefundRequest`; chạy song song chỉ có tối đa 1 kết quả thắng. |
| **AC-08** | **Given** `NoShowCase` mới tạo chưa có `notification_received_at` (`null`), **When** kiểm tra/quét hết hạn phản ánh 48h theo `Clock`, **Then** `dispute_deadline` vẫn là `null` và `compensation_status` giữ `HELD` (không tự chuyển `ELIGIBLE` theo D06); khi có `notification_received_at` và `Clock >= notification_received_at + 48h`, chuyển `ELIGIBLE`. |
| **AC-09** | **Given** nhân viên có quyền `BOX_MANAGE`/Owner kiểm tra lại hộp đang `AWAITING_INSPECTION`, **When** ghi kết quả `FAILED` kèm ghi chú, **Then** lưu `BoxInspection(FAILED)` bất biến và chuyển hộp sang `BLOCKED`. |
| **AC-10** | **Given** nhân viên có quyền `BOX_MANAGE`/Owner kiểm tra lại hộp đang `AWAITING_INSPECTION` với `PASSED`, **When** không truyền `targetBatchId` hoặc `targetBatchId` không đủ điều kiện (`EXPIRED`/`CLOSED`, `safeUntil < pickupEndsAt`, dưới `minimumValue`), **Then** nếu không truyền batch thì hộp giữ `AWAITING_INSPECTION` (không tự tăng lượng bán); nếu batch không đạt thì bị từ chối `409`. |
| **AC-11** | **Given** hộp `AWAITING_INSPECTION` kiểm tra lại `PASSED` kèm `targetBatchId` hợp lệ cùng cửa hàng (`DRAFT`/`OPEN`/`PAUSED`, `pickupEndsAt > now`, `safeUntil >= pickupEndsAt`, `totalListedValue >= minimumValue`), **When** xác nhận kiểm tra lại, **Then** lưu `BoxInspection(PASSED)`, gán `currentBatch = targetBatch`, chuyển hộp sang `AVAILABLE` (đếm vào `BatchAvailability`) mà vẫn giữ nguyên `box_allocation`/`no_show_case` cũ. |
| **AC-12** | **Given** hộp không ở trạng thái `AWAITING_INSPECTION` hoặc đang có allocation hoạt động, **When** gọi kiểm tra lại hộp No-show, **Then** từ chối `409 BOX_NOT_AWAITING_INSPECTION` / `BOX_ALLOCATED`. |
| **AC-13** | **Given** nhân viên chỉ có `BOX_MANAGE` gọi xác nhận No-show, nhân viên chỉ có `NO_SHOW_CONFIRM` gọi kiểm tra lại hộp, khách hàng/chủ cửa hàng khác gọi API cửa hàng, hoặc chưa đăng nhập, **When** gọi endpoint tương ứng, **Then** trả `403`/`404`/`401` đúng phạm vi quyền; tài liệu `Attachment` gắn `no_show_case_id` tuân thủ `NoShowAttachmentPolicy`. |
| **AC-14** | **Given** các endpoint mới (và migration nếu được duyệt tại Q5), **When** chạy Flyway, `ApiContractTests`, `SwaggerUiTests` và toàn bộ Gradle test suite, **Then** toàn bộ build pass và `docs/api/openapi.yaml` đầy đủ schema/ví dụ/mã lỗi. |

---

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn nghiệp vụ | Phạm vi đáp ứng | AC |
|---|---|---|
| **BR-40** (Trang 28) | Job sau `pickup_ends_at` chuyển `CONFIRMED` chưa giao sang `PENDING_NO_SHOW` | AC-01, AC-02 |
| **UC-36 chính, A1, E1, E2** (Trang 83–84) | Tra cứu đơn quá hạn nhận theo quyền `NO_SHOW_CONFIRM`/Owner, read-only, trạng thái rỗng | AC-03, AC-13 |
| **UC-37 chính, A1, E1–E3; BR-41, BR-51** (Trang 28, 84–85) | Điều kiện xác nhận (mở đúng giờ, đủ hộp, hết giờ, không có phiên mở, không có sự cố khách đã đến), idempotency, căn cứ & đính kèm | AC-02, AC-04, AC-06, AC-07, AC-13 |
| **BR-42** (Trang 28–29) | Tính giữ/hoàn qua `NoShowAmounts`, giữ `<= verifiedPaid`, hoàn phần vượt qua T6.5, hoa hồng 0 | AC-04, AC-05 |
| **BR-43, D06, UC-37 E3** (Trang 29, 70, 85) | Giữ bù đắp `HELD` 48h từ lúc khách nhận thông báo; chưa có căn cứ nhận thì chưa tự tính hết hạn | AC-08 |
| **UC-35 chính, A1, A2, E1; BR-44** (Trang 29, 82–83) | Hộp No-show vào `AWAITING_INSPECTION`, không tự bán lại; tái kiểm tra `FAILED` → `BLOCKED`, `PASSED` chỉ mở bán khi có đợt mới đạt chuẩn, giữ lịch sử | AC-04, AC-09, AC-10, AC-11, AC-12 |
| **CLAUDE.md** §4, 5, 7, 8 | `Clock`, `BigDecimal`, khóa dòng bi quan, `FOR UPDATE SKIP LOCKED`, `Idempotency-Key`, OpenAPI, full test | AC-01, 07, 13, 14 |

---

## 9. Quyết định cần duyệt tại Gate A

1. **Q1 (Thiết kế bộ API cửa hàng cho T7.4):**
   - *Đề xuất (Recommended):* Đặt dưới prefix chuẩn `/api/v1/stores/{storeId}/...`:
     - `GET /api/v1/stores/{storeId}/no-shows/overdue` (UC-36: lọc `date`, `status` — quyền `NO_SHOW_CONFIRM` hoặc Owner).
     - `GET /api/v1/stores/{storeId}/no-shows/{caseId}` (xem chi tiết 1 `NoShowCase` kèm số giữ/hoàn, `RefundRequest`, hộp, đính kèm — quyền `NO_SHOW_CONFIRM` hoặc Owner).
     - `POST /api/v1/stores/{storeId}/orders/{orderId}/no-show` (UC-37: xác nhận No-show, hỗ trợ header `Idempotency-Key` — quyền `NO_SHOW_CONFIRM` hoặc Owner).
     - `GET /api/v1/stores/{storeId}/boxes/awaiting-inspection` (danh sách hộp đang `AWAITING_INSPECTION` của cửa hàng — quyền `BOX_MANAGE` hoặc Owner).
     - `POST /api/v1/stores/{storeId}/boxes/{boxId}/recheck` (UC-35: kiểm tra lại hộp No-show, truyền `result`, `safeUntil`, `notes`, tùy chọn `targetBatchId` — quyền `BOX_MANAGE` hoặc Owner).
   *(Xác nhận cấu trúc endpoint này hay muốn chỉnh tên đường dẫn nào?)*

2. **Q2 (Cho phép ghi sự cố UC-30 trên đơn `PENDING_NO_SHOW` theo UC-37 A1):**
   Hiện `PickupIncidentService.recordIncident` (T7.2) chỉ chấp nhận đơn `CONFIRMED` hoặc `HANDOVER_IN_PROGRESS`. Nếu job đã chuyển đơn sang `PENDING_NO_SHOW`, nhưng nhân viên kiểm tra thực tế thấy **khách có đến đúng giờ** và cửa hàng không phục vụ được (UC-37 A1 → *"thực hiện UC-30, không xác nhận No-show"*), hiện tại sẽ bị chặn `409 INVALID_ORDER_STATUS`.
   - *Đề xuất (Recommended):* Mở rộng `PickupIncidentService.recordIncident` cho phép ghi sự cố `customerArrived = true` trên đơn `PENDING_NO_SHOW` (đưa đơn sang `HANDOVER_IN_PROGRESS` với `statusReason = "HANDOVER_INCIDENT: ..."` như T7.2 để chặn No-show và để chủ cửa hàng/admin xử lý hủy lỗi cửa hàng theo T7.3).
   *(Xác nhận mở rộng này hay giữ nguyên `PickupIncidentService` như T7.2?)*

3. **Q3 (Phạm vi T7.4 với `notification_received_at` và chuyển trạng thái `ELIGIBLE` sau 48h — D06):**
   T8.2 (gửi thông báo & ghi nhận khách đã nhận/đọc thông báo) chưa triển khai trong sprint hiện tại.
   - *Đề xuất (Recommended):*
     - Khi xác nhận No-show (T7.4), tạo bản ghi `Notification(NO_SHOW, PENDING)` gắn `noShowCase.notification`, và **giữ** `notificationReceivedAt = null`, `disputeDeadline = null`, `compensationStatus = HELD` (tuân thủ UC-37 E3 và D06).
     - Cài đặt sẵn `NoShowCompensationService` (và job định kỳ `NoShowCompensationJob` với cờ cấu hình):
       - Phương thức domain `markNotificationReceived(notificationId, receivedAt)` (để T8.2 gọi khi có bằng chứng nhận/đọc thông báo): cập nhật `notificationReceivedAt = receivedAt` và `disputeDeadline = receivedAt + order.disputeWindowHours` (48h).
       - Hàm/job nâng `HELD` → `ELIGIBLE`: chỉ xử lý các `NoShowCase` có `notificationReceivedAt IS NOT NULL AND disputeDeadline IS NOT NULL AND disputeDeadline <= :now` (bảo đảm trường hợp `notificationReceivedAt == null` **không bao giờ** tự hết hạn).
   *(Xác nhận giữ mốc nhận thông báo là `null` đến T8.2 và cài đặt logic chuyển `ELIGIBLE` sẵn ở T7.4, hay muốn để toàn bộ phần `ELIGIBLE` sang T8.3?)*

4. **Q4 (Trạng thái hộp `PASSED` khi chưa có `targetBatchId` ở UC-35 A2):**
   Theo UC-35 A2: *"chưa có khung giờ phù hợp: giữ hộp ngoài lượng bán dù kết quả kiểm tra đạt"*. Nếu đổi hộp về `AVAILABLE` trên đợt cũ đã hết hạn có thể gây hiểu nhầm trạng thái dù `BatchAvailability` có lọc `safe_until >= pickup_ends_at`.
   - *Đề xuất (Recommended):*
     - Nếu `result == PASSED` và `targetBatchId == null`: ghi `BoxInspection(PASSED)`, cập nhật `box.safeUntil`, và **giữ nguyên `box.status = AWAITING_INSPECTION`** (chờ chọn đợt phù hợp). Khi cửa hàng tạo/mở `SaleBatch` mới, nhân viên có thể gọi lại `POST /boxes/{boxId}/recheck` (kèm `targetBatchId`) để gán đợt và chuyển `AVAILABLE`.
     - Nếu `result == PASSED` và có `targetBatchId` đạt chuẩn (`DRAFT`/`OPEN`/`PAUSED`, cùng cửa hàng, `pickupEndsAt > now`, `safeUntil >= pickupEndsAt`, `totalListedValue >= minimumValue`): chuyển `currentBatch = targetBatch` và `status = AVAILABLE`.
   *(Xác nhận cách xử lý trạng thái `AWAITING_INSPECTION` cho UC-35 A2?)*

5. **Q5 (Có cần Migration `V20__no_show_idempotency.sql` cho `no_show_case` hay không):**
   Bảng `no_show_case` ở V1 đã có đầy đủ cột nghiệp vụ cốt lõi, nhưng chưa có `idempotency_key`, `request_fingerprint` và `refund_amount` để phục vụ phát lại chuẩn `Idempotency-Key` (UC-37 E2) giống `V19` (`order_cancellation_request`).
   - *Đề xuất (Recommended):* Tạo migration nhỏ `V20__no_show_idempotency.sql` chỉ `ALTER TABLE no_show_case` thêm:
     - `idempotency_key varchar(255)` + unique index `(store/order)` hoặc `(order_id, idempotency_key) WHERE idempotency_key IS NOT NULL`
     - `request_fingerprint text`
     - `refund_amount numeric(19,2) NOT NULL DEFAULT 0 CHECK (refund_amount >= 0 AND refund_amount = trunc(refund_amount))`
     *(và cập nhật `flyway_schema_history` count `19 -> 20` trong `FoodRescueApplicationTests` / `AuthMigrationTests`).*
   - *Phương án thay thế:* Không thêm migration V20; dùng `UNIQUE(order_id)` sẵn có và tính lại `refundAmount` qua `NoShowAmounts` (nhưng khi không lưu `idempotency_key` + `request_fingerprint`, khó phân biệt "gửi lại cùng yêu cầu" với "yêu cầu khác gửi sau khi đã No-show").
   *(Xác nhận thêm `V20` hay không cần migration?)*

---

## 10. Ràng buộc cho Gate B

- Không viết code sản phẩm hay migration V20 trước khi Gate A và Gate B được người dùng duyệt.
- Không sửa các migration V1–V19 đã chạy.
- Mọi thao tác xác nhận No-show và kiểm tra lại hộp chạy trong `@Transactional` với khóa bi quan (`CustomerOrder`, `BoxAllocation`, `PhysicalBox`, `SaleBatch`, `Payment`), dùng `Clock` (không gọi `Instant.now()` trực tiếp trong nghiệp vụ).
- Mọi tính toán tiền dùng `NoShowAmounts` / `BigDecimal` nguyên đồng (scale 0), tạo hoàn qua T6.5 `RefundRequest` (`PENDING`), không tạo hoàn 0đ.
- Cập nhật đầy đủ `docs/api/openapi.yaml` để vượt qua `ApiContractTests` và `SwaggerUiTests`.
