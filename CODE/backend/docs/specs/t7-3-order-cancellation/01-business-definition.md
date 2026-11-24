# Business Definition: T7.3 — Hủy đơn

**Status:** Chờ duyệt Gate A  
**Updated:** 2026-11-23  
**Scope:** T7.3 (Hủy đơn: khách xem trước, hủy ngay / gửi yêu cầu hủy / rút / bổ sung; xem kết quả hủy & tiến độ hoàn; cửa hàng hủy; admin xét duyệt yêu cầu hủy).  
**Phụ thuộc:** T6.5 (`[x]`), T7.2 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Bối cảnh và mục tiêu

Sau khi T6.5 (hoàn tiền thủ công qua admin) và T7.2 (phiên bàn giao và sự cố) đã hoàn thành, hệ thống cần hoàn thiện toàn bộ nghiệp vụ hủy đơn hàng (`PENDING_PAYMENT`, `CONFIRMED`, hoặc `HANDOVER_IN_PROGRESS` đã có sự cố từ T7.2) cho cả ba vai trò: Khách hàng, Chủ cửa hàng và Quản trị viên.

Mục tiêu của T7.3:
1. Cho phép khách hàng xem trước tác động hủy đơn, điều kiện áp dụng theo chính sách đã chốt và số tiền dự kiến được hoàn trước khi xác nhận (UC-17 bước 1–3, BR-38).
2. Cho phép khách hàng hủy ngay và hoàn 100% tiền đã xác minh nếu hủy trước `pickup_starts_at`; hoặc gửi **yêu cầu hủy chờ admin xét duyệt** nếu đã từ `pickup_starts_at` trở đi; hỗ trợ rút yêu cầu tự nguyện và bổ sung thông tin khi admin yêu cầu (UC-17, UC-71 A2, BR-38, SD-06).
3. Cho phép khách hàng xem kết quả hủy đơn và tiến độ hoàn tiền tách biệt với trạng thái đơn (UC-18, UC-17 E2).
4. Cho phép chủ cửa hàng hủy đơn không thể cung cấp hàng / thực phẩm không an toàn (UC-56, BR-39): hoàn 100% tiền đã xác minh, không thu phí hủy/no-show, hoa hồng 0, và không tự mở bán lại hộp lỗi/không an toàn.
5. Cho phép quản trị viên xem danh sách/chi tiết yêu cầu hủy và ra quyết định **chấp thuận** (kèm số tiền hoàn theo chính sách đã lưu và nguồn thu), **từ chối**, hoặc **yêu cầu bổ sung** (UC-71, SD-06).
6. Bảo đảm mọi nghĩa vụ hoàn tiền phát sinh đều đi qua cơ chế T6.5 (`RefundRequest`), không tạo hoàn 0đ, hoàn lỗi không làm khôi phục đơn, và chặn hủy khi đơn đã hoàn tất hoặc đang có phiên bàn giao `OPEN`/`RECONCILING` (BR-31, UC-17 E1).

Nguồn nghiệp vụ trích xuất từ `../../DOCX/BaoCaoFoodRescue.pdf`:
- **Trang 28:** BR-38 (Khách hàng chủ động hủy đơn), BR-39 (Cửa hàng không thể cung cấp hàng).
- **Trang 62–63:** UC-17 (Yêu cầu hủy đơn hàng — luồng chính, A1, A2, A3, E1, E2).
- **Trang 63–64:** UC-18 (Xem kết quả hủy đơn — luồng chính, A1, E1, E2).
- **Trang 104–105:** UC-56 (Hủy đơn do cửa hàng — luồng chính, A1, E1, E2).
- **Trang 121–122:** UC-71 (Xét duyệt yêu cầu hủy đơn — luồng chính, A1, A2, A3, E1, E2).
- **Trang 158 (Hình 8.6):** SD-06 (Sơ đồ tuần tự Hủy đơn hàng — bước 01..31, nhánh xét duyệt, nhánh rút yêu cầu, nhánh đơn vừa thay đổi, ref SD-11 hoàn tiền).

---

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin dưới `src/main/kotlin/com/foodrescue/backend/`:

1. **Hủy đơn hiện có từ T5.3:**
   - `order/controller/OrderController.kt` (`POST /api/v1/orders/{orderId}/cancel`) gọi `OrderCancellationService.cancelUnpaid`: hiện chỉ cho phép hủy đơn `PENDING_PAYMENT` qua `OrderReleaser.release`, trả về `OrderView` (đã được kiểm thử tại `OrderLifecycleTests.kt` và `PickupHandoverTests.kt`).
   - `order/service/OrderReleaser.kt`: giải phóng `BoxAllocation` `HELD` → `RELEASED`, trả `PhysicalBox` `RESERVED` → `AVAILABLE`, cập nhật trạng thái đơn và ghi `StoreHistoryRecorder`. Chưa xử lý allocation `CONFIRMED` hoặc hộp `ALLOCATED` của đơn đã thanh toán.
2. **Tài chính và hoàn tiền (T6.4, T6.5):**
   - `order/service/OrderMoneyReader.kt`: đọc `verifiedPaid`, `remainingToPay`, `refunded`, `refundInProgress` của đơn.
   - `payment/service/RefundObligationService.kt`: tạo `RefundRequest` (`PENDING`) gắn với `payment_id` (unique index `ux_refund_request_payment` trong V16 — mỗi `Payment` tối đa 1 `RefundRequest`).
   - `payment/service/ManualRefundService.kt` và `payment/controller/AdminRefundController.kt`: admin duyệt (`approve`), xác nhận chuyển khoản (`confirm`) hoặc ghi thất bại (`fail`). `OrderRefundController.kt` (`GET /api/v1/orders/{orderId}/refunds`) cho khách xem tiến độ hoàn tiền.
   - Ràng buộc DB bảng `refund_request` (V1): `requested_amount > 0` (chặn hoàn 0đ ở DB), `approved_amount <= requested_amount`, `status IN ('PENDING', 'APPROVED', 'REJECTED', 'FULFILLED')`.
3. **Bàn giao và sự cố (T7.2):**
   - `pickup/service/PickupSessionService.kt` và `pickup/service/PickupIncidentService.kt`:
     - Khi mở phiên (`startSession`), `order.status` thành `HANDOVER_IN_PROGRESS` và có `PickupSession` trạng thái `OPEN`.
     - Khi ghi sự cố (`recordIncident`), phiên `OPEN`/`RECONCILING` chuyển `CANCELLED`, hộp ảnh hưởng chuyển `BLOCKED`, nhưng `order.status` được **giữ ở `HANDOVER_IN_PROGRESS`** và `order.statusReason` bắt đầu bằng `"HANDOVER_INCIDENT: ..."` (đã ghi chú trong `t7-2-handover-session/03-implementation-control.txt` mục 5 để T7.3 phân biệt với phiên đang mở).
4. **Dữ liệu và kiểm thử:**
   - Chưa có bảng yêu cầu hủy đơn trong V1–V18 (như card T7.3 đã chỉ ra).
   - `attachment` trong V1/V6/V9 dùng `CHECK (num_nonnulls(...) = 1)` để gắn với đúng một đối tượng cha.
   - `AuthMigrationTests.kt` và `FoodRescueApplicationTests.kt` hiện assert cố định số lượng migration (`18`) và số lượng entity (`42`).
   - `SwaggerUiTests.kt` hiện assert cố định số đường dẫn OpenAPI (`92`).
   - Baseline `./gradlew.bat build --console=plain`: **BUILD SUCCESSFUL** (UP-TO-DATE sau lần chạy toàn bộ 238 PASS / 1 SKIPPED của T7.2).

---

## 3. Người dùng và quyền

- **Khách hàng (`CUSTOMER`):**
  - Chỉ xem trước tác động hủy, hủy/gửi yêu cầu hủy, bổ sung thông tin, rút yêu cầu và xem kết quả hủy/hoàn cho **đơn do chính mình đặt**.
  - Đơn của người khác hoặc không tồn tại: **404 `NOT_FOUND`** (không lộ thông tin đơn người khác).
- **Chủ cửa hàng (`Store Owner`):**
  - Thực hiện hủy đơn do cửa hàng (UC-56) cho đơn thuộc cửa hàng mình sở hữu (`store.owner.id == actorId`).
  - Khách hàng, nhân viên cửa hàng (hoặc chủ cửa hàng khác), hoặc ADMIN: **403 `ACCESS_DENIED`** (xem Q2 về việc có cấp thêm cho staff nào không).
  - Kiểm tra lại quyền ở tầng service dưới khóa giao dịch.
- **Quản trị viên (`ADMIN`):**
  - Xác thực role `ADMIN` qua `@PreAuthorize("hasRole('ADMIN')")` cho việc xem danh sách/chi tiết yêu cầu hủy và xét duyệt (chấp thuận / từ chối / yêu cầu bổ sung — UC-71).
  - Không phải ADMIN: **403 `ACCESS_DENIED`**.
- **Chưa đăng nhập / token không hợp lệ:** **401 `UNAUTHORIZED`**.

---

## 4. Luồng chính

### 4.1. Khách xem trước tác động hủy đơn (UC-17 bước 1–3)
1. Khách chọn đơn cần xem tác động hủy (`orderId`).
2. Hệ thống kiểm tra quyền sở hữu đơn và đọc trạng thái đơn, phiên bàn giao hiện hành, chính sách đã lưu (`cancellationTermsSnapshot`, `refundTermsSnapshot`, `noShowRetentionRate`), tiền đã xác minh (`verifiedPaid`), và thời điểm hiện tại (`clock.instant()`).
3. Hệ thống tính toán và trả về kết quả xem trước (read-only, không đổi trạng thái/dữ liệu):
   - Đơn `PENDING_PAYMENT`: cho phép hủy ngay, hoàn dự kiến = `0 VND` (không tạo giao dịch hoàn).
   - Đơn `CONFIRMED` và `now < pickupStartsAt`: cho phép hủy ngay, hoàn dự kiến = 100% `verifiedPaid` (BR-38).
   - Đơn `CONFIRMED` và `now >= pickupStartsAt` (hoặc `HANDOVER_IN_PROGRESS` đã hủy phiên vì sự cố T7.2): cần **gửi yêu cầu hủy để admin xét duyệt** (`requiresReview = true`), không hứa hoàn 100% (`estimatedRefundAmount = null`, kèm `cancellationTerms` và `refundTerms` đã chốt lúc đặt — UC-17 A1).
   - Đơn `HANDOVER_IN_PROGRESS` đang có phiên `OPEN`/`RECONCILING`: không thể hủy (BR-31).
   - Đơn `COMPLETED`: không thể hủy, hướng dẫn khiếu nại theo UC-23 (UC-17 E1).
   - Đơn `CANCELLED`, `EXPIRED`, `NO_SHOW`, `PENDING_NO_SHOW`: không thể hủy.

### 4.2. Khách hủy đơn hoặc gửi yêu cầu hủy (UC-17 chính, A1–A3, E1–E2; SD-06)
1. Khách nhập lý do hủy (bắt buộc) và gửi yêu cầu kèm tùy chọn `Idempotency-Key` (và danh sách `attachmentIds` bằng chứng nếu cần khi gửi yêu cầu xét duyệt).
2. Hệ thống khóa bi quan dòng `CustomerOrder` (`findForUpdate`):
   - **Idempotency:** Nếu đơn có `Idempotency-Key` đã xử lý trước đó, trả lại kết quả cũ mà không tạo yêu cầu hủy hoặc nghĩa vụ hoàn trùng.
   - **Kiểm tra chặn (BR-31, UC-17 E1):**
     - `COMPLETED` → 409 `ORDER_ALREADY_COMPLETED` kèm thông điệp hướng dẫn khiếu nại.
     - `HANDOVER_IN_PROGRESS` và tồn tại `PickupSession` `OPEN`/`RECONCILING` → 409 `HANDOVER_IN_PROGRESS` (chặn hủy khi đang bàn giao).
     - `CANCELLED`, `EXPIRED`, `NO_SHOW`, `PENDING_NO_SHOW` → 409 `INVALID_ORDER_STATUS`.
   - **Nhánh A — Đơn `PENDING_PAYMENT` (UC-17 A2):**
     - Hủy ngay qua `OrderReleaser`: đơn → `CANCELLED`, allocation `HELD` → `RELEASED`, hộp `RESERVED` → `AVAILABLE`, không tạo `RefundRequest`.
   - **Nhánh B — Đơn `CONFIRMED` và `now < pickupStartsAt` (BR-38):**
     - Hủy ngay nguyên tử trong 1 transaction:
       - Đơn → `CANCELLED`, `statusReason = "CUSTOMER_CANCELLED: <reason>"`, `statusChangedAt = now`.
       - Allocation `CONFIRMED` → `RELEASED` (`releasedAt = now`, `releaseReason = "CUSTOMER_CANCELLED"`).
       - Khóa từng `PhysicalBox`: chuyển từ `ALLOCATED` → `AVAILABLE` (chỉ mở bán lại thực tế khi đáp ứng `BatchAvailability`: còn `safeUntil >= pickupEndsAt` và `safeUntil > now`).
       - Tạo bản ghi `OrderCancellationRequest` ở trạng thái `APPROVED` (tự động theo BR-38), `storeFault = false`, `approvedRefundAmount = verifiedPaid`.
       - Nếu `verifiedPaid > 0`: tạo nghĩa vụ hoàn `RefundRequest` (`PENDING`) cho 100% khoản đã xác minh qua T6.5 (`reasonCode = "CUSTOMER_CANCELLED_BEFORE_PICKUP"`).
       - Ghi `AuditLog` qua `StoreHistoryRecorder`.
   - **Nhánh C — Đơn `CONFIRMED` và `now >= pickupStartsAt` (hoặc `HANDOVER_IN_PROGRESS` không có phiên mở do sự cố T7.2) (UC-17 A1, SD-06 #8–9):**
     - Không hủy đơn ngay; đơn giữ nguyên trạng thái hiện tại.
     - Kiểm tra chưa có yêu cầu hủy đang mở (`SUBMITTED` hoặc `NEEDS_INFO`) trên đơn (đảm bảo bởi partial unique index).
     - Tạo `OrderCancellationRequest` trạng thái `SUBMITTED`, gắn bằng chứng (nếu có) qua `Attachment`.
     - Ghi `AuditLog`.
3. **Khách bổ sung thông tin (UC-71 A2) hoặc rút yêu cầu (SD-06 #22–25):**
   - Khi `OrderCancellationRequest` đang `NEEDS_INFO`, khách có thể gửi bổ sung lý do/tài liệu (`attachmentIds`) → trạng thái chuyển lại `SUBMITTED`.
   - Khi `OrderCancellationRequest` đang `SUBMITTED` hoặc `NEEDS_INFO`, khách có thể **rút yêu cầu** → chuyển `WITHDRAWN`, giữ nguyên trạng thái đơn và không tạo hoàn tiền.

### 4.3. Khách xem kết quả hủy và tiến độ hoàn (UC-18)
1. Khách gọi API xem kết quả hủy đơn (`GET /api/v1/orders/{orderId}/cancellation`).
2. Hệ thống kiểm tra quyền sở hữu đơn và truy vấn dữ liệu ở chế độ read-only:
   - Trạng thái đơn hiện tại, trạng thái và lịch sử/quyết định của `OrderCancellationRequest` (nếu có), lý do, thời điểm, số tiền được duyệt hoàn.
   - Tiến độ hoàn tiền tách biệt từ `RefundRequest` và `Refund` (T6.5): `PENDING`, `APPROVED`, `FULFILLED` (hoặc chưa phát sinh hoàn nếu 0đ / bị từ chối).

### 4.4. Cửa hàng hủy đơn do không thể cung cấp (UC-56, BR-39)
1. Chủ cửa hàng chọn đơn chưa bàn giao thuộc cửa hàng và gửi lý do không thể cung cấp, chỉ định hộp lỗi/không an toàn (hoặc cờ toàn bộ hộp của đơn không an toàn), kèm `Idempotency-Key`.
2. Hệ thống kiểm tra quyền chủ sở hữu cửa hàng và khóa `CustomerOrder` (`findForUpdate`):
   - Kiểm tra idempotency theo key.
   - Nếu `COMPLETED` → 409 `ORDER_ALREADY_COMPLETED` (UC-56 E1).
   - Nếu `HANDOVER_IN_PROGRESS` và có phiên `OPEN`/`RECONCILING` → 409 `HANDOVER_IN_PROGRESS` (phải đóng phiên hoặc ghi sự cố UC-30 trước).
   - Nếu `HANDOVER_IN_PROGRESS` và **không** có phiên `OPEN`/`RECONCILING` (đã ghi sự cố T7.2) → **cho phép** tiếp tục hủy lỗi cửa hàng.
   - Nếu `CANCELLED`, `EXPIRED`, `NO_SHOW`, `PENDING_NO_SHOW` → 409 `INVALID_ORDER_STATUS`.
3. Xử lý nguyên tử trong 1 transaction:
   - Đơn → `CANCELLED`, `statusReason = "STORE_CANCELLED: <reason>"`, `statusChangedAt = now`.
   - Nếu có yêu cầu hủy đang mở của khách (`SUBMITTED`/`NEEDS_INFO`) → chuyển `APPROVED` theo lỗi cửa hàng (`storeFault = true`). Ngược lại tạo bản ghi `OrderCancellationRequest` (`initiator = STORE`, `status = APPROVED`, `storeFault = true`, `approvedRefundAmount = verifiedPaid`).
   - Allocation `HELD` hoặc `CONFIRMED` → `RELEASED` (`releaseReason = "STORE_CANCELLED"`).
   - Hộp vật lý:
     - Với hộp bị đánh dấu không an toàn / lỗi cửa hàng (mặc định toàn bộ hộp của đơn khi `boxesUnsafe = true`, hoặc theo `unsafeBoxIds`): chuyển sang `BoxStatus.BLOCKED` (UC-56 A1: **không tự mở bán lại**).
     - Với hộp an toàn còn lại (nếu `boxesUnsafe = false`): chuyển về `BoxStatus.AVAILABLE` (chỉ bán lại được khi còn đáp ứng `BatchAvailability`).
   - Nếu có tiền đã xác minh (`verifiedPaid > 0`): tạo nghĩa vụ hoàn 100% (`RefundRequest` `PENDING`, `reasonCode = "STORE_FAULT"`), không thu phí hủy/no-show, hoa hồng 0 (BR-39).
   - Ghi `AuditLog`.

### 4.5. Quản trị viên xét duyệt yêu cầu hủy đơn (UC-71, SD-06)
1. Admin xem danh sách yêu cầu hủy lọc theo trạng thái (`SUBMITTED`, `NEEDS_INFO`, `APPROVED`, `REJECTED`, `WITHDRAWN`) và xem chi tiết một yêu cầu (hiển thị chính sách đã lưu của đơn, tiền theo nguồn thu, trạng thái bàn giao và lịch sử sự cố `pickup_incident` nếu có).
2. Admin gửi quyết định kèm lý do/ghi chú:
   - Khóa `OrderCancellationRequest` rồi khóa `CustomerOrder` (`findForUpdate`) để chống xét duyệt đồng thời hoặc tranh chấp với bàn giao/no-show.
   - Nếu yêu cầu không còn ở trạng thái chờ (`SUBMITTED` hoặc `NEEDS_INFO`) → 409 `CONFLICT`.
   - Nếu đơn đã `COMPLETED` (UC-71 E1) → 409 `ORDER_ALREADY_COMPLETED` (chuyển sang khiếu nại sau giao).
   - Nếu đơn đang có phiên bàn giao `OPEN`/`RECONCILING` → 409 `HANDOVER_IN_PROGRESS`.
   - **Quyết định `APPROVE`:**
     - Admin chỉ định `storeFault` (`Boolean`) và `refundAmount` (`BigDecimal`, nguyên đồng, `0 <= refundAmount <= verifiedPaid`).
     - Nếu `storeFault == true` (UC-71 A3): bắt buộc `refundAmount == verifiedPaid` (hoàn 100%, không phạt khách).
     - Hủy đơn (`OrderStatus.CANCELLED`), giải phóng allocation → `RELEASED`.
     - Nếu `storeFault == true` hoặc `releaseBoxesToAvailable == false`: hộp → `BLOCKED`; ngược lại → `AVAILABLE`.
     - Nếu `refundAmount > 0`: tạo `RefundRequest` (`PENDING`, `requestedAmount = refundAmount`, gắn `payment` xác minh của đơn, `reasonCode = if (storeFault) "STORE_FAULT" else "CUSTOMER_CANCELLED_APPROVED"`). Nếu `refundAmount == 0`: không tạo `RefundRequest`.
     - Cập nhật yêu cầu hủy sang `APPROVED`, lưu `reviewer`, `reviewedAt`, `reviewNote`, `approvedRefundAmount`, `refundRequestId`.
   - **Quyết định `REJECT` (UC-71 A1):**
     - Bắt buộc `reason`. Yêu cầu → `REJECTED`, đơn giữ nguyên trạng thái (`CONFIRMED` hoặc `HANDOVER_IN_PROGRESS`), không nhả hộp, không tạo hoàn.
   - **Quyết định `REQUEST_INFO` (UC-71 A2):**
     - Bắt buộc `reason`. Yêu cầu → `NEEDS_INFO`, đơn giữ nguyên, chưa kết luận hoàn.
   - Ghi `AuditLog` cho mọi quyết định.

---

## 5. Quy tắc nghiệp vụ đề xuất (BR-T73-xx)

- **BR-T73-01 — Phân chia ngưỡng thời gian hủy của khách (BR-38):**
  Khách hủy trước `pickupStartsAt` được hủy ngay và hoàn 100% tiền đã xác minh. Từ `pickupStartsAt` trở đi, khách chỉ được tạo yêu cầu hủy chờ admin xét duyệt theo chính sách đã lưu; không mặc nhiên hoàn toàn bộ.
- **BR-T73-02 — Lỗi cửa hàng hoàn 100% và chặn tự mở bán hộp lỗi (BR-39, UC-56 A1):**
  Cửa hàng hủy đơn hoặc admin kết luận `storeFault = true` phải hoàn 100% tiền đã xác minh, không thu phí hủy/no-show, hoa hồng 0. Hộp thuộc đơn lỗi/không an toàn chuyển sang `BLOCKED`, tuyệt đối không tự động trả về `AVAILABLE`.
- **BR-T73-03 — Chặn hủy đơn đã hoàn tất hoặc đang bàn giao (BR-31, UC-17 E1, SD-06):**
  Đơn `COMPLETED` không bao giờ bị hủy (hướng dẫn khiếu nại sau giao). Đơn `HANDOVER_IN_PROGRESS` có phiên `OPEN`/`RECONCILING` chặn mọi thao tác hủy. Khi có tranh chấp đồng thời giữa hủy đơn và bắt đầu/hoàn tất bàn giao trên cùng đơn, khóa bi quan `CustomerOrder` bảo đảm chỉ một kết quả thắng.
- **BR-T73-04 — Phân biệt đơn có sự cố T7.2:**
  Đơn `HANDOVER_IN_PROGRESS` nhưng không còn phiên `OPEN`/`RECONCILING` (đã ghi `PickupIncident` hủy phiên ở T7.2) cho phép cửa hàng hủy lỗi cửa hàng hoặc admin xử lý hủy/hoàn qua UC-71.
- **BR-T73-05 — Tách biệt trạng thái hủy và tiến độ hoàn tiền (UC-17 E2, UC-71 E2):**
  Mọi nghĩa vụ hoàn tiền (> 0 VND) tạo `RefundRequest` (`PENDING`) qua cơ chế T6.5 gắn với giao dịch `Payment` đã xác minh của đơn. Thất bại hoặc chậm trễ khi chuyển khoản hoàn không khôi phục đơn hay hủy bỏ quyết định hủy.
- **BR-T73-06 — Không tạo hoàn 0đ (UC-17 A2):**
  Đơn `PENDING_PAYMENT` chưa thu tiền hoặc quyết định admin duyệt hoàn `0 VND` không tạo `RefundRequest`.
- **BR-T73-07 — Mỗi đơn tối đa một yêu cầu hủy đang mở và Idempotency:**
  Partial unique index trên `order_cancellation_request` bảo đảm một đơn chỉ có tối đa 1 yêu cầu ở `SUBMITTED` hoặc `NEEDS_INFO`. Gửi lại yêu cầu với cùng `Idempotency-Key` trả về kết quả cũ, không tạo yêu cầu/nghĩa vụ hoàn trùng.
- **BR-T73-08 — Toàn vẹn chính sách snapshot và tiền VND nguyên đồng:**
  Số tiền hoàn là `BigDecimal` nguyên đồng (scale 0), không vượt `verifiedPaid - alreadyRefundedOrPending` (≤ tổng tiền đã xác minh của đơn). Đánh giá điều kiện dùng chính sách đã snapshot trên `CustomerOrder`.
- **BR-T73-09 — Thời gian và kiểm soát quyền:**
  Dùng `Clock` được inject để kiểm tra mốc `pickupStartsAt`. Kiểm tra quyền sở hữu/store/admin tại service dưới khóa giao dịch; không lộ dữ liệu đơn người khác hoặc chi tiết nhạy cảm.

---

## 6. Phạm vi

### Trong phạm vi (In-Scope):
1. Migration `V19__order_cancellation_requests.sql`: bảng `order_cancellation_request`, chỉ mục duy nhất yêu cầu đang mở, cột liên kết trên `attachment`.
2. Entity `OrderCancellationRequest` + enum trạng thái/người khởi tạo + `AttachmentAccessPolicy` cho chứng cứ hủy đơn.
3. API khách hàng: xem trước tác động hủy, hủy ngay / gửi yêu cầu hủy, bổ sung thông tin, rút yêu cầu, xem kết quả hủy (tích hợp tiến độ hoàn từ T6.5).
4. API chủ cửa hàng: hủy đơn do cửa hàng (UC-56).
5. API admin: danh sách, chi tiết, xét duyệt yêu cầu hủy (`APPROVE`, `REJECT`, `REQUEST_INFO` — UC-71).
6. Tích hợp giải phóng `BoxAllocation`, xử lý `PhysicalBox` (`AVAILABLE` vs `BLOCKED`), tạo `RefundRequest` qua T6.5, và ghi `AuditLog`.
7. Cập nhật `docs/api/openapi.yaml` và viết bộ integration test đầy đủ cho mọi AC (bao gồm test đồng thời hủy vs bàn giao, trước/sau `pickupStartsAt`, idempotency, quyền).

### Ngoài phạm vi (Out-of-Scope):
1. Job quét hết hạn nhận và quy trình xác nhận No-show (T7.4).
2. Đánh dấu hộp không đạt ngoài quầy và xử lý đơn tồn khi khóa cửa hàng (T7.5).
3. Quy trình khiếu nại sau giao (T8.1) và gửi thông báo outbox/email/push (T8.2).
4. Bút toán `ADJUSTMENT` doanh thu/hoa hồng khi hoàn tiền hoặc đối soát (T8.3, T8.4).
5. Giao diện Android/Web.

---

## 7. Acceptance Criteria (Given / When / Then)

| ID | Given / When / Then |
|---|---|
| **AC-01** | **Given** khách sở hữu đơn `CONFIRMED` trước `pickupStartsAt`, **When** xem trước tác động hủy, **Then** trả `cancellableImmediately=true`, `estimatedRefundAmount=verifiedPaid`, kèm điều khoản snapshot mà không đổi DB. |
| **AC-02** | **Given** khách sở hữu đơn `CONFIRMED` tại hoặc sau `pickupStartsAt`, **When** xem trước tác động hủy, **Then** trả `cancellableImmediately=false`, `requiresReview=true`, `estimatedRefundAmount=null` và điều khoản hủy/hoàn đã chốt. |
| **AC-03** | **Given** khách hủy đơn `PENDING_PAYMENT`, **When** xác nhận hủy, **Then** đơn sang `CANCELLED`, allocation `RELEASED`, hộp về `AVAILABLE`, không tạo `RefundRequest` 0đ (giữ tương thích T5.3). |
| **AC-04** | **Given** khách hủy đơn `CONFIRMED` khi `Clock < pickupStartsAt`, **When** xác nhận hủy, **Then** đơn sang `CANCELLED`, allocation `RELEASED`, hộp về `AVAILABLE` (chỉ khả dụng nếu còn `safeUntil` hợp lệ), và tạo 1 `RefundRequest` `PENDING` bằng 100% `verifiedPaid`. |
| **AC-05** | **Given** khách gửi yêu cầu hủy đơn `CONFIRMED` khi `Clock >= pickupStartsAt`, **When** xác nhận gửi, **Then** đơn giữ nguyên `CONFIRMED`, tạo `OrderCancellationRequest` `SUBMITTED`; gửi trùng khi đang mở không tạo bản ghi thứ hai. |
| **AC-06** | **Given** yêu cầu hủy của khách đang ở `SUBMITTED` hoặc `NEEDS_INFO`, **When** khách rút yêu cầu, **Then** yêu cầu chuyển `WITHDRAWN`, đơn giữ nguyên `CONFIRMED`, không tạo hoàn tiền. |
| **AC-07** | **Given** yêu cầu hủy đang ở `NEEDS_INFO`, **When** khách gửi bổ sung lý do/chứng cứ hợp lệ, **Then** yêu cầu quay lại `SUBMITTED` và lưu chứng cứ gắn đúng quyền tải file. |
| **AC-08** | **Given** chủ cửa hàng có đơn `CONFIRMED` hoặc `HANDOVER_IN_PROGRESS` đã ghi sự cố T7.2 (không có phiên mở), **When** thực hiện hủy đơn do cửa hàng (UC-56), **Then** đơn `CANCELLED`, allocation `RELEASED`, hộp lỗi/không an toàn chuyển `BLOCKED` (không tự mở bán lại), tạo `RefundRequest` 100% `verifiedPaid`. |
| **AC-09** | **Given** admin xét duyệt yêu cầu hủy `SUBMITTED` (UC-71), **When** chọn `APPROVE` với số tiền hoàn hợp lệ (`0 <= refundAmount <= verifiedPaid`), `REJECT`, hoặc `REQUEST_INFO`, **Then** trạng thái yêu cầu, trạng thái đơn, hộp và `RefundRequest` cập nhật nhất quán; `storeFault=true` bắt buộc hoàn 100%; `REJECT` giữ nguyên đơn và không tạo hoàn. |
| **AC-10** | **Given** đơn `COMPLETED` hoặc `HANDOVER_IN_PROGRESS` có phiên `OPEN`/`RECONCILING`, **When** khách, cửa hàng hoặc admin cố hủy/duyệt hủy, **Then** bị từ chối `409` (`ORDER_ALREADY_COMPLETED` hướng dẫn khiếu nại hoặc `HANDOVER_IN_PROGRESS`). |
| **AC-11** | **Given** yêu cầu hủy của khách/cửa hàng và thao tác bắt đầu/hoàn tất bàn giao chạy đồng thời trên cùng đơn, **When** hai request xử lý song song, **Then** chỉ có đúng một kết quả nhất quán (hủy thắng và chặn bàn giao, hoặc bàn giao mở/hoàn tất và chặn hủy). |
| **AC-12** | **Given** gửi lại yêu cầu hủy hoặc xét duyệt với cùng `Idempotency-Key` hoặc gọi lặp trên yêu cầu đã xử lý, **When** request lặp lại, **Then** không tạo yêu cầu hủy thứ hai hay `RefundRequest` trùng; hoàn tiền thất bại ở T6.5 không làm khôi phục đơn đã hủy. |
| **AC-13** | **Given** khách sở hữu đơn đã hủy hoặc có yêu cầu hủy, **When** gọi API xem kết quả hủy (UC-18), **Then** trả đầy đủ trạng thái yêu cầu hủy, quyết định, lý do và tiến độ hoàn tiền hiện hành; không đổi dữ liệu. |
| **AC-14** | **Given** người dùng chưa đăng nhập, khách khác, nhân viên/owner cửa hàng khác hoặc người không phải ADMIN, **When** gọi các endpoint tương ứng, **Then** trả `401`, `404` (đơn khách khác) hoặc `403` theo đúng phân quyền server. |
| **AC-15** | **Given** migration `V19` và các endpoint mới, **When** chạy Flyway migration, `ApiContractTests`, `SwaggerUiTests` và full build, **Then** DB nâng cấp an toàn, mọi endpoint có schema/ví dụ/mã lỗi trong `docs/api/openapi.yaml` và toàn bộ test pass. |

---

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn nghiệp vụ | Phạm vi đáp ứng | AC |
|---|---|---|
| **UC-17 chính, A1–A3, E1–E2** (Trang 62–63) | Xem trước tác động, hủy trước giờ nhận, gửi yêu cầu sau giờ nhận, hủy đơn chưa trả không tạo hoàn 0đ, rút yêu cầu, chặn đơn đã giao, hoàn lỗi không khôi phục đơn | AC-01, 02, 03, 04, 05, 06, 10, 12 |
| **UC-18 chính, A1, E1–E2** (Trang 63–64) | Khách xem kết quả hủy và tiến độ hoàn tách biệt, đúng phạm vi quyền, read-only | AC-13, 14 |
| **UC-56 chính, A1, E1–E2** (Trang 104–105) | Chủ cửa hàng hủy đơn không cung cấp được hàng, hoàn 100%, không tự mở bán lại hộp không an toàn, chặn nếu đã giao xong | AC-08, 10, 11, 12 |
| **UC-71 chính, A1–A3, E1–E2** (Trang 121–122) | Admin xem/duyệt/bác/yêu cầu bổ sung; hoàn theo chính sách & nguồn thu; lỗi cửa hàng hoàn 100%; khách bổ sung | AC-07, 09, 10, 12, 14 |
| **BR-38** (Trang 28) | Khách hủy trước giờ nhận hoàn 100%; từ giờ nhận xử lý theo chính sách | AC-01, 02, 04, 05 |
| **BR-39** (Trang 28) | Cửa hàng không cung cấp được hàng hoàn 100%, không phí hủy/no-show | AC-08, 09 |
| **SD-06** (Trang 158, Hình 8.6) | Tuần tự kiểm tra quyền/trạng thái, nhánh admin xét duyệt, nhánh khách xác nhận/rút, khóa đồng thời với bàn giao, chuyển hoàn tiền | AC-05, 06, 09, 10, 11, 12, 13 |
| **CLAUDE.md** §4, 5, 7, 8 | Migration mới V19, `Clock`, `BigDecimal`, khóa dòng, `Idempotency-Key`, OpenAPI, test quyền và đồng thời | AC-11, 12, 14, 15 |

---

## 9. Quyết định cần duyệt tại Gate A

1. **Q1 (Tương thích `POST /api/v1/orders/{orderId}/cancel` và thiết kế API khách hàng):**
   Hiện `POST /api/v1/orders/{orderId}/cancel` (T5.3) nhận `{ "reason": "..." }` và trả `200 OrderView` (chỉ cho `PENDING_PAYMENT`, trả 409 nếu hủy lại).
   - *Phương án đề xuất (Recommended):*
     - Giữ `POST /api/v1/orders/{orderId}/cancel` chấp nhận thêm header tùy chọn `Idempotency-Key`: xử lý **hủy ngay** cho cả `PENDING_PAYMENT` và `CONFIRMED` trước `pickupStartsAt`, trả về `OrderView` (tương thích hoàn toàn với `OrderLifecycleTests` và `PickupHandoverTests`). Nếu gửi vào thời điểm `>= pickupStartsAt`, trả `409 CANCELLATION_REQUIRES_REVIEW` hướng dẫn dùng API gửi yêu cầu hủy.
     - Bổ sung bộ endpoint đầy đủ cho UC-17/UC-18:
       - `GET /api/v1/orders/{orderId}/cancellation-preview` (xem trước tác động hủy)
       - `POST /api/v1/orders/{orderId}/cancellation-requests` (gửi yêu cầu hủy cần xét duyệt từ `pickupStartsAt`, hỗ trợ `Idempotency-Key` và `attachmentIds`)
       - `POST /api/v1/orders/{orderId}/cancellation-requests/{requestId}/supplement` (bổ sung thông tin khi `NEEDS_INFO`)
       - `POST /api/v1/orders/{orderId}/cancellation-requests/{requestId}/withdraw` (rút yêu cầu)
       - `GET /api/v1/orders/{orderId}/cancellation` (UC-18: xem kết quả hủy & tiến độ hoàn)
     *(Hoặc bạn muốn gộp cả hai trường hợp ngay/chờ duyệt vào chung `POST /api/v1/orders/{orderId}/cancel` và đổi schema trả về? Lưu ý nếu đổi kiểu trả về cần chỉnh lại test T5.3 hoặc giữ top-level `status`/`statusReason`.)*

2. **Q2 (Chủ thể thực hiện hủy đơn do cửa hàng — UC-56):**
   Tài liệu UC-56 (trang 104) ghi tác nhân là **Chủ cửa hàng** (`Store Owner`), và danh sách permission đã seed từ T3.4 không có quyền hủy đơn cho nhân viên.
   - *Đề xuất:* Chỉ **Store Owner** của cửa hàng được gọi `POST /api/v1/stores/{storeId}/orders/{orderId}/cancel` (nhân viên thường trả 403). *(Xác nhận chỉ Store Owner hay cho phép thêm một permission cụ thể như `BOX_MANAGE`?)*

3. **Q3 (Xử lý đơn đang `HANDOVER_IN_PROGRESS` có sự cố từ T7.2):**
   Theo thiết kế T7.2 (`03-implementation-control.txt`), khi có `PickupIncident`, `PickupSession` bị `CANCELLED` nhưng `order.status` giữ `HANDOVER_IN_PROGRESS` với `statusReason` bắt đầu bằng `HANDOVER_INCIDENT:`.
   - *Đề xuất:* Phân biệt bằng việc kiểm tra sự tồn tại của `PickupSession` có trạng thái `OPEN`/`RECONCILING`:
     - Nếu **có** phiên `OPEN`/`RECONCILING`: chặn mọi thao tác hủy (409 `HANDOVER_IN_PROGRESS`, BR-31).
     - Nếu **không có** phiên mở (đã hủy phiên vì sự cố T7.2): cho phép **Store Owner** hủy lỗi cửa hàng theo UC-56 (hoàn 100%, hộp chuyển/giữ `BLOCKED`), hoặc cho phép khách gửi yêu cầu hủy chờ admin duyệt theo UC-71.

4. **Q4 (Schema Migration `V19__order_cancellation_requests.sql`):**
   - Tạo bảng `order_cancellation_request` (liên kết `customer_order`, `account`, `refund_request`) kèm partial unique index `uq_order_open_cancellation` cho `status IN ('SUBMITTED', 'NEEDS_INFO')` và `uq_order_cancellation_idempotency` cho `(order_id, idempotency_key) WHERE idempotency_key IS NOT NULL`.
   - Thêm cột `order_cancellation_request_id` vào `attachment` và cập nhật `attachment_check`.
   - Cập nhật số lượng migration/entity kỳ vọng (`19` và `43`) trong `AuthMigrationTests.kt` và `FoodRescueApplicationTests.kt` ở Gate B.

5. **Q5 (Cách tạo `RefundRequest` khi hủy đơn nhiều/một khoản thanh toán):**
   `ux_refund_request_payment` (V16) quy định tối đa 1 `RefundRequest` gắn mỗi `payment_id`. Một đơn `CONFIRMED` thông thường có đúng 1 `Payment` ở trạng thái `VERIFIED`.
   - *Đề xuất:* Khi hủy đơn có phát sinh hoàn tiền (`amount > 0`), tìm `Payment` `VERIFIED` của đơn để tạo `RefundRequest` (`PENDING`, `requestedAmount = amount`).
   - *Trường hợp đặc biệt:* Nếu tại thời điểm hủy đơn đã có `RefundRequest` cho `payment` đó (ví dụ trong tình huống dữ liệu bất thường), không tạo trùng mà kiểm tra/báo lỗi nhất quán; còn nếu `amount == 0` thì không tạo `RefundRequest` theo đúng CHECK DB `requested_amount > 0`.

---

## 10. Ràng buộc cho Gate B

- Không viết code sản phẩm hay migration V19 trước khi Gate A và Gate B được người dùng duyệt.
- Không sửa các migration V1–V18 đã chạy.
- Mọi thao tác chuyển trạng thái đơn, giải phóng allocation, cập nhật trạng thái hộp, lưu yêu cầu hủy và tạo nghĩa vụ hoàn phải nằm trong một `@Transactional` dưới khóa bi quan `CustomerOrder`.
- Tái sử dụng `ManualRefundService` / `RefundRequest` của T6.5, không tự tạo luồng chuyển tiền hoàn riêng.
- Mọi endpoint mới phải được mô tả đầy đủ trong `docs/api/openapi.yaml` (tag `Orders` / `Stores` / `Admin`) và vượt qua `ApiContractTests` + `SwaggerUiTests`.
