# Business Definition: T8.1 — Khiếu nại (Tiếp nhận, Bổ sung, Giải quyết & Đóng hồ sơ)

**Status:** Chờ duyệt Gate A  
**Updated:** 2026-11-24  
**Scope:** T8.1 (`UC-23` khách gửi khiếu nại kèm bằng chứng & cấp mã tiếp nhận; xử lý phản ánh No-show đúng hạn/quá hạn 48h `BR-43`, `D06`; `UC-63` Admin xem danh sách khiếu nại; `UC-64` Admin xem chi tiết kèm đơn/hộp/thanh toán/bàn giao/no-show/lịch sử; `UC-65` Admin yêu cầu bổ sung & ra quyết định chấp thuận/một phần/bác kèm trách nhiệm/nguồn hoàn T6.5/`BR-45`/`SD-10`, ghi `FinancialEvent(COMPLAINT_RESOLVED)` và đóng hồ sơ khi mọi nghĩa vụ đã hoàn tất).  
**Phụ thuộc:** T7.5 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Bối cảnh và mục tiêu

Sau khi giai đoạn 7 hoàn tất toàn bộ luồng bàn giao (`T7.1`–`T7.2`), hủy đơn (`T7.3`), No-show (`T7.4`) và đánh dấu hộp không đạt / ngừng bán vi phạm / khóa cửa hàng (`T7.5`), hệ thống đã có sẵn các bản ghi `Complaint` hậu giao (`FOOD_SAFETY`, `OPEN`) từ `T7.5` và các `NoShowCase` đang bị giữ bù đắp 48h (`HELD`) từ `T7.4`. Nhiệm vụ **T8.1** hoàn thiện vòng đời tiếp nhận, đối chiếu, ra quyết định và theo dõi thực thi khiếu nại:

1. **Khách hàng gửi khiếu nại (`UC-23`, `BR-43`, `D06`):**
   - Khách hàng chọn đơn hàng thuộc về mình, chọn loại khiếu nại (`ComplaintType`: `FOOD_SAFETY`, `BOX_VALUE`, `PAYMENT`, `PICKUP`, `NO_SHOW`, `OTHER`), nhập mô tả/yêu cầu hỗ trợ và đính kèm file bằng chứng (`attachmentIds`).
   - Hỗ trợ `Idempotency-Key`: gửi lặp lại do mất mạng (`UC-23 E2`) trả lại mã hồ sơ đã tạo, không tạo trùng hồ sơ hay giữ trùng tiền.
   - **Phản ánh No-show (`BR-43`, `D06`, `UC-23 A2`):**
     - Nếu đơn có `NoShowCase` và đang trong thời hạn phản ánh (`disputeDeadline == null` theo `UC-37 E3` hoặc `now <= disputeDeadline`) và `compensationStatus == HELD` → tiếp nhận khiếu nại đúng hạn, chuyển `NoShowCase.compensationStatus = DISPUTED` để tiếp tục giữ khoản bù cho tới khi giải quyết xong.
     - Nếu phản ánh **quá hạn 48 giờ** (`notificationReceivedAt != null && now > disputeDeadline`) → vẫn ghi nhận hồ sơ như yêu cầu hỗ trợ (`withinDisputeWindow = false`), **không** tự khôi phục quyền phản ánh đúng hạn và **không** làm đổi trạng thái `ELIGIBLE`/`SETTLED` của `NoShowCase`.
2. **Quản trị viên tra cứu danh sách & chi tiết khiếu nại (`UC-63`, `UC-64`):**
   - `UC-63`: Lọc danh sách theo trạng thái, loại vấn đề, cửa hàng, đơn hàng, khoảng thời gian; hiển thị mã hồ sơ, đơn liên quan, người gửi, thời điểm, trạng thái và khoản tiền đang giữ/tranh chấp.
   - `UC-64`: Xem toàn bộ bức tranh đối chiếu của hồ sơ: nội dung & bằng chứng của các bên, ảnh chụp điều kiện đơn (`CustomerOrder`), thông tin hộp & lịch sử kiểm tra (`PhysicalBox`, `BoxInspection`), giao dịch thanh toán & nghĩa vụ/lịch sử hoàn tiền (`Payment`, `RefundRequest`, `Refund`), phiên & sự cố bàn giao (`PickupSession`, `PickupIncident`), hồ sơ No-show (`NoShowCase`), và lịch sử thay đổi (`AuditLog`).
3. **Yêu cầu bổ sung thông tin (`UC-65` bước 3 / `A1`, `SD-10` #17–22):**
   - Admin có thể yêu cầu khách bổ sung chứng cứ (`OPEN`/`INVESTIGATING` → `AWAITING_EVIDENCE`), tiếp tục giữ khoản tiền đang tranh chấp.
   - Khách hàng gửi bổ sung mô tả/tệp đính kèm đưa hồ sơ sang `INVESTIGATING` để Admin xem xét tiếp.
4. **Ra quyết định có căn cứ & Theo dõi đóng hồ sơ (`UC-65` bước 4–7, `A2..A4`, `E1..E2`, `BR-45`, `SD-10` #23–37):**
   - Admin chọn kết quả: **Chấp thuận (`APPROVED`)**, **Chấp thuận một phần (`PARTIAL`)**, hoặc **Bác (`REJECTED`)** kèm lý do/căn cứ, xác định trách nhiệm (`STORE`, `PLATFORM`, `CUSTOMER`, `SHARED`, `NONE`) và phân bổ số tiền hoàn theo từng giao dịch thanh toán (`Payment`) cụ thể (bắt buộc không vượt số tiền còn có thể hoàn trên từng giao dịch — `UC-65 E1`).
   - Quyết định đã chốt **không thể bị sửa lại** (`SD-10` "Hồ sơ chưa có quyết định xử lý hợp lệ").
   - **Nếu No-show là lỗi cửa hàng (`UC-65 A2`):** Hủy khoản bù (`NoShowCase.compensationStatus = FORFEITED`), mở nghĩa vụ hoàn cho khách phần tiền đã xác minh còn lại (tới đủ 100% `verifiedPaid`), cửa hàng không được bù đắp.
   - **Nếu bác khiếu nại No-show (`UC-65 A3`):** Mở khóa trạng thái `DISPUTED` trên `NoShowCase` (về `ELIGIBLE` nếu đã qua `disputeDeadline` & hết tranh chấp, hoặc về `HELD` nếu chưa nhận thông báo / chưa hết 48h, hoa hồng vẫn `= 0`).
   - Ghi nhận 1 sự kiện bất biến `FinancialEvent(COMPLAINT_RESOLVED)`.
   - Nếu có tiền hoàn được duyệt → tạo `RefundRequest(PENDING)` gắn `complaint_id` chuyển cho luồng chi hoàn thủ công T6.5 (D3); khiếu nại giữ trạng thái `DECIDED` cho đến khi mọi nghĩa vụ hoàn của hồ sơ đạt `FULFILLED` mới chuyển `CLOSED` (`UC-65` bước 7, `E2`, `SD-10` #30–35).

Nguồn nghiệp vụ trích xuất từ `../../DOCX/BaoCaoFoodRescue.pdf`:
- **Trang 29:** `BR-43` (Thời hạn phản ánh 48h và giữ/hủy khoản bù No-show), `BR-45` (Hoàn tiền sau bàn giao gắn giao dịch gốc, điều chỉnh đối soát và hoa hồng).
- **Trang 69–70:** `UC-23` (Gửi khiếu nại — chính, `A1` sau bàn giao, `A2` quá 48h theo `D06`, `E1` thiếu nội dung/file, `E2` gửi lặp mất mạng).
- **Trang 85:** `UC-37 A2`, `E3` & `D06` (Phản ánh No-show và căn cứ nhận thông báo).
- **Trang 112–115:** `UC-63` (Xem danh sách khiếu nại), `UC-64` (Xem chi tiết khiếu nại), `UC-65` (Giải quyết khiếu nại — chính, `A1..A4`, `E1..E2`).
- **Trang 162 (`Hình 8.10`):** `SD-10` (Sơ đồ tuần tự Tiếp nhận và giải quyết khiếu nại — bước `01`..`37`).

---

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin dưới `src/main/kotlin/com/foodrescue/backend/`:

1. **Entity & Migration hiện có liên quan khiếu nại:**
   - `complaint/entity/Complaint.kt` (`V1`, `V2`, `V21`): đã có `store`, `order`, `customer`, `type` (`FOOD_SAFETY`, `BOX_VALUE`, `PAYMENT`, `PICKUP`, `NO_SHOW`, `OTHER`), `description`, `status` (`OPEN`, `INVESTIGATING`, `AWAITING_EVIDENCE`, `DECIDED`, `CLOSED`), `handledBy`, `decision`, `resolvedAt`, cùng hai cột T7.5 vừa thêm `sourceBox` và `idempotencyKey`.
   - `complaint/repository/ComplaintRepository.kt`: mới có 2 hàm phục vụ T7.5 (`findFirstBySourceBoxIdAndStatusInOrderByCreatedAtDesc`, `findBySourceBoxIdAndIdempotencyKey`).
   - `complaint/service/ComplaintAttachmentPolicy.kt` (T7.5): đã cho phép `complaint.customer`, `complaint.store.owner`, `ADMIN` (và người upload qua `FileAccessService`) đọc file đính kèm `attachment.complaint`.
   - `V2` line 210, 217, 222: đã có `UNIQUE (id, order_id)` trên `complaint`, composite FK `(complaint_id, order_id)` trên `refund_request` và `financial_event`, và check trên `financial_event`: `(type = 'COMPLAINT_RESOLVED' AND complaint_id IS NOT NULL)`.
2. **Điểm giao thoa cần xử lý kỹ:**
   - **`V16` `ux_refund_request_payment` (QUAN TRỌNG):** Migration `V16` tạo `CREATE UNIQUE INDEX ux_refund_request_payment ON refund_request (payment_id) WHERE payment_id IS NOT NULL;`. Điều này hiện **chặn** việc tạo nghĩa vụ hoàn thứ hai lên cùng một `Payment` — ví dụ khi T7.4 đã mở `NO_SHOW_REFUND` (70% tiền) trên payment đó và tới T8.1 Admin kết luận `No-show là lỗi cửa hàng (UC-65 A2)` cần hoàn nốt 30% còn lại gắn vào đúng `Payment` gốc! Xem câu hỏi **Q2**.
   - **T7.4 `NoShowCompensationService` (`releaseEligible`):** Hiện kiểm tra `n.compensation_status = 'HELD' AND NOT EXISTS (SELECT 1 FROM complaint c WHERE c.order_id = n.order_id AND c.status IN ('OPEN', 'INVESTIGATING', 'AWAITING_EVIDENCE'))`. Khi khách gửi khiếu nại No-show đúng hạn, cần chuyển `n.compensationStatus = CompensationStatus.DISPUTED` để bảo vệ khoản giữ kể cả khi complaint sang trạng thái `DECIDED`.
   - **T6.5 `ManualRefundService` (`confirm`):** Khi Admin xác nhận đã chuyển khoản hoàn tiền thủ công (`FULFILLED`), chưa có logic kiểm tra đóng `Complaint` từ `DECIDED` → `CLOSED` khi tất cả nghĩa vụ hoàn gắn `complaint_id` đã `FULFILLED`.
   - **T8.2 & T8.3 ranh giới:** Bảng `notification` và `ledger_entry` (`ADJUSTMENT`) đã có sẵn trong schema `V1`. Worker gửi thông báo thuộc **T8.2** (T8.1 chỉ có thể tạo/không tạo record outbox nếu cần, hoặc để T8.2 tích hợp), và job tính `PLATFORM_COMMISSION` / `STORE_PAYABLE` / `NO_SHOW_COMPENSATION` / `ADJUSTMENT` thuộc **T8.3** (xem **Q5**).
3. **Baseline kiểm thử:**
   - `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`: **BUILD SUCCESSFUL** (`295 PASS, 0 FAIL, 1 SKIPPED` có sẵn; `21` migrations `V1`–`V21`; `43` entities; `108` OpenAPI paths / `26` tags).

---

## 3. Người dùng và quyền

- **Khách hàng (`CUSTOMER`):**
  - Gửi khiếu nại mới (`POST /api/v1/complaints`), xem danh sách & chi tiết khiếu nại của chính mình, bổ sung chứng cứ khi hồ sơ ở trạng thái `AWAITING_EVIDENCE`.
  - Chỉ được gửi và thao tác trên đơn hàng do chính mình đặt (`order.customer.id == accountId`); đơn của người khác → **404 `NOT_FOUND`** (không lộ dữ liệu đơn của người khác).
- **Quản trị viên (`ADMIN`):**
  - Xác thực `@PreAuthorize("hasRole('ADMIN')")` ở controller + kiểm tra vai trò ở server.
  - Xem danh sách khiếu nại toàn hệ thống (`UC-63`), xem chi tiết tổng hợp (`UC-64`), yêu cầu bổ sung (`AWAITING_EVIDENCE`), ra quyết định (`UC-65`) và đóng hồ sơ.
  - Chủ cửa hàng / nhân viên / khách hàng gọi API Admin → **403 `ACCESS_DENIED`**.
- **Chủ cửa hàng (`Store Owner`):**
  - Có quyền đọc chứng cứ đính kèm của khiếu nại thuộc cửa hàng mình qua `ComplaintAttachmentPolicy` (`T7.5`), và theo dõi danh sách/chi tiết khiếu nại liên quan đến cửa hàng mình ở mức chỉ đọc (nếu mở endpoint ở **Q1**). Không được tự duyệt/bác/quyết định khiếu nại.
- **Chưa đăng nhập / token không hợp lệ:** **401 `UNAUTHORIZED`**.

---

## 4. Luồng chính

### 4.1. Khách hàng gửi khiếu nại (`UC-23`, `BR-43`, `D06`, `SD-10` #01–10)
1. Khách hàng gửi `orderId`, `type` (`ComplaintType`), `description` (bắt buộc), `attachmentIds` (tùy chọn) và header `Idempotency-Key` (tùy chọn).
2. Hệ thống validate `Idempotency-Key`, kiểm tra đơn tồn tại và thuộc về `customerId` (`order.customer.id == customerId`, else `404`).
3. **Kiểm tra trùng lặp idempotency (`UC-23 E2`):** Nếu đã tồn tại `Complaint` của khách hàng đó với cùng `idempotencyKey`:
   - Khớp `requestFingerprint` → trả `200 OK` hồ sơ cũ (kèm mã tiếp nhận cũ), không tạo trùng bản ghi hay tác động thêm tới `NoShowCase`.
   - Khác `requestFingerprint` → `409 IDEMPOTENCY_KEY_REUSED`.
4. **Kiểm tra trạng thái hợp lệ của đơn & thời hạn:**
   - Đơn `PENDING_PAYMENT` hoặc `EXPIRED` chưa phát sinh thanh toán `VERIFIED` (trừ khi `type == PAYMENT` có giao dịch `Payment` trên đơn như tiền muộn/trùng): nếu không hợp lệ theo loại khiếu nại → từ chối `409 INVALID_ORDER_STATUS`.
   - Không cho phép mở 2 khiếu nại đang hoạt động (`OPEN`, `INVESTIGATING`, `AWAITING_EVIDENCE`, `DECIDED`) cùng `(orderId, type)` (trừ replay idempotent) → `409 COMPLAINT_ALREADY_OPEN`.
   - Với `type == NO_SHOW`: yêu cầu đơn phải ở trạng thái `NO_SHOW` (có `NoShowCase`) (else `409 NO_SHOW_NOT_FOUND`). Kiểm tra cửa sổ 48h (`BR-43`, `D06`, `UC-23 A2`):
     - Nếu `noShowCase.notificationReceivedAt != null && now.isAfter(noShowCase.disputeDeadline)`: gán `withinDisputeWindow = false` (ghi nhận như yêu cầu hỗ trợ quá hạn theo D06, **không** chuyển `ELIGIBLE`/`SETTLED` về `DISPUTED`).
     - Ngược lại (`disputeDeadline == null` hoặc `!now.isAfter(disputeDeadline)`): gán `withinDisputeWindow = true`; nếu `noShowCase.compensationStatus == CompensationStatus.HELD`, chuyển sang `CompensationStatus.DISPUTED` (tiếp tục giữ khoản bù — `BR-43`).
5. Validate & khóa `attachmentIds` (thuộc `customerId` upload và chưa gắn đối tượng khác), tạo `Complaint` trạng thái `OPEN`, cấp `complaintCode` (VD `CMP-...`), gắn `Attachment(file, complaint = complaint)`, ghi `AuditLog` (`COMPLAINT_SUBMITTED`).

### 4.2. Admin xem danh sách & chi tiết đối chiếu (`UC-63`, `UC-64`, `SD-10` #11–16)
1. **Danh sách (`UC-63`):** Lọc theo `status`, `type`, `storeId`, `orderId`, `from`, `to` (theo `createdAt`), trả `PageResponse<AdminComplaintSummary>` gồm `complaintCode`, `orderId`, `orderCode`, `storeId`, `storeName`, `customerDisplayName`, `type`, `status`, `withinDisputeWindow`, `verifiedPaid`, `heldCompensationAmount` (từ `NoShowCase` nếu đang giữ/tranh chấp), `approvedRefundAmount`, `createdAt`.
2. **Chi tiết (`UC-64`):** Trả `AdminComplaintDetail` tổng hợp đầy đủ dữ liệu đối chiếu:
   - Thông tin khiếu nại, `complaintCode`, `sourceBoxId`, bằng chứng khách/bên liên quan (`attachmentFileIds`), yêu cầu bổ sung & lịch sử bổ sung.
   - Snapshot đơn hàng (`OrderView`: giá, chính sách `noShowRetentionRate`, `disputeWindowHours`, `pickupStartsAt`, `pickupEndsAt`, `statusReason`).
   - Danh sách hộp thực tế (`BoxAllocation` + `PhysicalBox` + `BoxInspection` lịch sử kiểm tra).
   - Giao dịch tiền & hoàn tiền (`OrderMoneyView`, từng `Payment` kèm số tiền còn có thể hoàn `refundableAmount`, từng `RefundRequest` & `Refund` attempt).
   - Phiên bàn giao & sự cố (`PickupSession`, `PickupIncident`) và hồ sơ `NoShowCase` (nếu có).
   - Lịch sử thay đổi trên đơn & khiếu nại (`AuditLog`).

### 4.3. Yêu cầu bổ sung thông tin (`UC-65` bước 3 / `A1`, `SD-10` #17–22)
1. Admin gọi yêu cầu bổ sung (bắt buộc `note` nội dung cần làm rõ) khi hồ sơ ở `OPEN` hoặc `INVESTIGATING`.
2. Hồ sơ chuyển `AWAITING_EVIDENCE`, lưu `requestedInfoNote`, `handledBy = admin`, ghi `AuditLog(COMPLAINT_INFO_REQUESTED)`. Khoản bù tranh chấp (nếu có) giữ nguyên trạng thái `DISPUTED`.
3. Khách hàng gọi endpoint bổ sung (`description`/`note` + `attachmentIds` mới) khi hồ sơ ở `AWAITING_EVIDENCE`: gắn thêm chứng cứ, nối nội dung bổ sung và chuyển hồ sơ về `INVESTIGATING`, ghi `AuditLog(COMPLAINT_SUPPLEMENTED)`.

### 4.4. Ra quyết định giải quyết & Đóng hồ sơ (`UC-65` bước 4–7, `A2..A4`, `E1..E2`, `SD-10` #23–37)
1. Admin gửi quyết định kèm `Idempotency-Key` (tùy chọn), `outcome` (`APPROVED` | `PARTIAL` | `REJECTED`), `responsibility` (`STORE` | `PLATFORM` | `CUSTOMER` | `SHARED` | `NONE`), `reason` (căn cứ bắt buộc), `refundAllocations: List<PaymentRefundAllocation>` (từng `paymentId` + `amount` nguyên đồng), và thông số điều chỉnh cửa hàng/hoa hồng (nếu có).
2. Khóa `CustomerOrder` (`findForUpdate`) → `Complaint` (`findForUpdate`) → `NoShowCase` (nếu có) → các `Payment` liên quan (sorted `id ASC`).
3. **Kiểm tra hợp lệ (`SD-10` #23–25, `UC-65 E1`):**
   - Nếu trùng `Idempotency-Key` với quyết định đã lưu: khớp fingerprint → trả `200 OK`; khác → `409 IDEMPOTENCY_KEY_REUSED`.
   - Nếu `complaint.status in [DECIDED, CLOSED]` → từ chối `409 COMPLAINT_ALREADY_DECIDED` (quyết định đã chốt không sửa).
   - Kiểm tra `outcome`:
     - `REJECTED` → tổng hoàn bắt buộc `= 0`.
     - `APPROVED` / `PARTIAL` với `refundAllocations`: mỗi dòng phải thuộc `orderId`, `payment.status == VERIFIED`, `amount > 0` nguyên đồng, và **`amount <= payment.receivedAmount - refundedSucceeded(payment) - openRefundObligations(payment)`**; nếu vượt → `409 REFUND_EXCEEDS_REFUNDABLE` (`UC-65 E1`).
4. **Xử lý nhánh No-show (`UC-65 A2`, `A3`):**
   - Nếu đơn có `NoShowCase`:
     - Khi `outcome != REJECTED && responsibility in [STORE, SHARED]` (`UC-65 A2` — No-show là lỗi cửa hàng hoặc chia sẻ lỗi làm hủy No-show): đặt `noShowCase.compensationStatus = CompensationStatus.FORFEITED` (cửa hàng không được bù). Tổng tiền hoàn qua `RefundRequest` mở mới + các nghĩa vụ hoàn trước đó (`NO_SHOW_REFUND`) phải ≤ `verifiedPaid` (với `APPROVED` toàn phần lỗi cửa hàng: cho phép hoàn đủ phần còn lại lên tới 100% `verifiedPaid`).
     - Khi `outcome == REJECTED` (`UC-65 A3`): nếu `noShowCase.compensationStatus == DISPUTED` và không còn khiếu nại mở khác trên đơn, cập nhật về `ELIGIBLE` (nếu `notificationReceivedAt != null && disputeDeadline != null && !now.isBefore(disputeDeadline)`) hoặc về `HELD` (nếu chưa đủ điều kiện quá hạn 48h).
5. **Ghi kết quả, tạo nghĩa vụ hoàn T6.5 & sự kiện tài chính (`SD-10` #26–35):**
   - Tạo `RefundRequest(order, complaint = complaint, payment = payment, requestedAmount = alloc.amount, status = RefundRequestStatus.PENDING, reasonCode = ...)` cho mỗi dòng hoàn > 0.
   - Tạo 1 `FinancialEvent(order = order, type = FinancialEventType.COMPLAINT_RESOLVED, complaint = complaint, occurredAt = now, businessKey = "COMPLAINT_RESOLVED:${complaint.id}")`.
   - Cập nhật `Complaint`:
     - Nếu có ít nhất 1 `RefundRequest` được tạo (cần T6.5 duyệt & chuyển tiền ngoài hệ thống): `complaint.status = ComplaintStatus.DECIDED`, `resolvedAt = now`, `closedAt = null`.
     - Nếu không có nghĩa vụ hoàn cần thực thi (`REJECTED` hoặc tổng hoàn duyệt `= 0`): `complaint.status = ComplaintStatus.CLOSED`, `resolvedAt = now`, `closedAt = now`.
   - Ghi `AuditLog(COMPLAINT_DECIDED)` / `COMPLAINT_CLOSED`.
6. **Hoàn tất thực thi & Đóng hồ sơ (`UC-65 E2`, `SD-10` #30–35):**
   - Khi `ManualRefundService.confirm` (T6.5) xác nhận chuyển tiền `FULFILLED` cho một `RefundRequest` có `complaint != null`: tự động kiểm tra nếu toàn bộ `RefundRequest` của `complaint` đều đã `FULFILLED` và `complaint.status == DECIDED` → cập nhật `complaint.status = ComplaintStatus.CLOSED`, `closedAt = now`, ghi `AuditLog(COMPLAINT_CLOSED)`. Nếu có lần chuyển khoản thất bại (`ManualRefundService.fail`), `RefundRequest` vẫn mở nên `Complaint` tiếp tục giữ ở `DECIDED` (`UC-65 E2`). Đồng thời cung cấp endpoint `POST /api/v1/admin/complaints/{complaintId}/close` để Admin đóng/xác nhận hoàn tất (chỉ cho phép khi mọi nghĩa vụ hoàn của hồ sơ đã `FULFILLED`, else `409 REFUND_PENDING`).

---

## 5. Quy tắc nghiệp vụ đề xuất (BR-T81-xx)

- **BR-T81-01 — Cấp mã tiếp nhận & Idempotency gửi khiếu nại (`UC-23 E2`):**
  Mỗi hồ sơ khiếu nại có mã duy nhất (`complaintCode`); gửi lặp cùng `Idempotency-Key` và payload trả lại hồ sơ cũ, sai payload trả `409 IDEMPOTENCY_KEY_REUSED`.
- **BR-T81-02 — Cửa sổ 48h phản ánh No-show & Bảo vệ khoản bù (`BR-43`, `D06`, `UC-23 A2`):**
  Chỉ khiếu nại No-show gửi trước hoặc bằng `disputeDeadline` (hoặc khi `disputeDeadline == null` theo `UC-37 E3`) mới được tính là đúng hạn và chuyển `NoShowCase` sang `DISPUTED`. Khiếu nại quá hạn vẫn được tiếp nhận như yêu cầu hỗ trợ (`withinDisputeWindow = false`) nhưng không tự khôi phục quyền phản ánh đúng hạn.
- **BR-T81-03 — Bất biến quyết định & Chặn chi trả vượt nguồn thanh toán (`UC-65 E1`):**
  Mỗi khiếu nại chỉ có duy nhất một quyết định chốt (`DECIDED`/`CLOSED` không thể sửa); tổng hoàn tiền từ các nghĩa vụ hoàn thuộc mọi quy trình trên một `Payment` không bao giờ vượt `payment.receivedAmount`.
- **BR-T81-04 — Đóng hồ sơ chỉ khi mọi nghĩa vụ đã hoàn tất (`UC-65` bước 7, `E2`, `SD-10` #32–35):**
  Khiếu nại phát sinh hoàn tiền giữ trạng thái `DECIDED` cho đến khi mọi `RefundRequest` liên kết đều `FULFILLED` mới chuyển sang `CLOSED`.
- **BR-T81-05 — Bảo toàn chứng cứ và truy vết tài chính (`BR-45`):**
  Mọi quyết định tạo đúng 1 `FinancialEvent(COMPLAINT_RESOLVED, businessKey = "COMPLAINT_RESOLVED:{id}")`, lưu đầy đủ trách nhiệm & số tiền điều chỉnh làm căn cứ cho đối soát và điều chỉnh sổ cái T8.3.

---

## 6. Phạm vi

### Trong phạm vi (In-Scope):
1. Khách hàng gửi, xem, bổ sung chứng cứ khiếu nại (`UC-23`, `BR-43`, `D06`).
2. Admin tra cứu danh sách (`UC-63`), xem chi tiết đối chiếu đa nguồn (`UC-64`), yêu cầu bổ sung (`UC-65 A1`), ra quyết định (`UC-65`) và đóng hồ sơ (`SD-10`).
3. Liên kết hoàn tiền thủ công T6.5 (`ManualRefundService.confirm` tự động đóng `Complaint` khi mọi `RefundRequest` của hồ sơ đã `FULFILLED`; bỏ giới hạn chặn nhiều obligation trên 1 payment khi obligation gắn `complaint_id`).
4. Cập nhật `NoShowCase.compensationStatus` (`DISPUTED`, `FORFEITED`, khôi phục `HELD`/`ELIGIBLE` khi bác) và chặn `NoShowCompensationJob` giải tỏa đơn đang `DECIDED`.
5. (Nếu chốt ở Q1) Endpoint đọc danh sách/chi tiết khiếu nại cho Store Owner.
6. Cập nhật `docs/api/openapi.yaml` và viết bộ integration test TDD đầy đủ.

### Ngoài phạm vi (Out-of-Scope):
1. Worker gửi email/in-app notification & đánh dấu đã nhận thông báo (`T8.2` — trừ việc các cột/enum thông báo được giữ tương thích).
2. Sinh bút toán `PLATFORM_COMMISSION`, `STORE_PAYABLE`, `NO_SHOW_COMPENSATION` và các bút toán `LedgerEntry(ADJUSTMENT)` (`T8.3` — T8.1 chuẩn bị sẵn `FinancialEvent(COMPLAINT_RESOLVED)` và dữ liệu trách nhiệm/điều chỉnh đã chốt trên `Complaint`).
3. Bảng đối soát định kỳ & chi trả cửa hàng (`T8.4`).

---

## 7. Acceptance Criteria (Given / When / Then)

| ID | Given / When / Then |
|---|---|
| **AC-01** | **Given** khách hàng sở hữu đơn hợp lệ và bằng chứng tự upload, **When** gửi khiếu nại (`POST /api/v1/complaints`) kèm `Idempotency-Key`, **Then** trả `201` kèm `complaintCode`, trạng thái `OPEN`, gắn `attachment.complaint`; gửi lặp lại cùng key & payload trả `200` với cùng `id` và `complaintCode`; đổi payload cùng key trả `409 IDEMPOTENCY_KEY_REUSED`. |
| **AC-02** | **Given** đơn `NO_SHOW` có `notificationReceivedAt` và `disputeDeadline = t + 48h`, **When** khách gửi khiếu nại `NO_SHOW` lúc `<= disputeDeadline` (hoặc khi `disputeDeadline == null`), **Then** `withinDisputeWindow = true`, `NoShowCase.compensationStatus = DISPUTED`, job `NoShowCompensationService.releaseEligible` dù tua Clock qua `+72h` vẫn **không** giải tỏa sang `ELIGIBLE`. |
| **AC-03** | **Given** đơn `NO_SHOW` đã quá `disputeDeadline` (`now > disputeDeadline`), **When** khách gửi khiếu nại `NO_SHOW`, **Then** vẫn tạo `Complaint(OPEN)` với `withinDisputeWindow = false` (yêu cầu hỗ trợ theo D06), không tự phục hồi quyền phản ánh đúng hạn và không đảo ngược trạng thái `ELIGIBLE`/`SETTLED` của `NoShowCase`. |
| **AC-04** | **Given** `ADMIN`, **When** gọi xem danh sách khiếu nại (`UC-63`) với các bộ lọc `status`/`type`/`storeId`/`orderId`/`from`/`to` và gọi xem chi tiết (`UC-64`), **Then** danh sách hiển thị đúng trạng thái và khoản đang giữ, chi tiết trả đủ snapshot đơn, hộp, kiểm tra hộp, thanh toán/hoàn tiền, phiên/sự cố bàn giao, `NoShowCase`, chứng cứ và lịch sử `AuditLog`. |
| **AC-05** | **Given** khiếu nại đang `OPEN` hoặc `INVESTIGATING`, **When** `ADMIN` yêu cầu bổ sung (`AWAITING_EVIDENCE`), **Then** lưu lý do yêu cầu, giữ `NoShowCase.DISPUTED`; khi khách gọi `/supplement` kèm file mới, file được gắn và trạng thái quay về `INVESTIGATING`. |
| **AC-06** | **Given** khiếu nại trên đơn `COMPLETED` (gồm cả hồ sơ `FOOD_SAFETY` do T7.5 tạo sau giao) có `Payment VERIFIED`, **When** `ADMIN` quyết định `APPROVED` hoặc `PARTIAL` kèm số tiền hoàn hợp lệ trên `Payment` gốc và trách nhiệm (`STORE`/`PLATFORM`), **Then** ghi `FinancialEvent(COMPLAINT_RESOLVED)`, tạo `RefundRequest(PENDING, complaint_id)` qua T6.5, giữ hồ sơ ở `DECIDED`; sau khi `ADMIN` xác nhận chuyển tiền hoàn (`ManualRefundService.confirm`), hồ sơ tự chuyển `CLOSED`. |
| **AC-07** | **Given** khiếu nại `NO_SHOW` đúng hạn mà trước đó T7.4 đã giữ 30% và mở `NO_SHOW_REFUND` 70%, **When** `ADMIN` quyết định `APPROVED` (`responsibility = STORE`, No-show là lỗi cửa hàng) kèm hoàn nốt 30% còn lại trên cùng `Payment` gốc, **Then** `NoShowCase.compensationStatus = FORFEITED`, mở thành công `RefundRequest(PENDING)` thứ hai cho phần 30% gắn `complaint_id`, tổng nghĩa vụ hoàn = 100% `verifiedPaid`. |
| **AC-08** | **Given** khiếu nại `NO_SHOW` đang `DISPUTED`, **When** `ADMIN` quyết định `REJECTED`, **Then** ghi `FinancialEvent(COMPLAINT_RESOLVED)`, đóng hồ sơ `CLOSED` ngay (`0` hoàn tiền mới), mở khóa `NoShowCase` (thành `ELIGIBLE` nếu đã qua `disputeDeadline` hoặc `HELD` nếu chưa hết 48h). |
| **AC-09** | **Given** quyết định hoàn vượt số tiền còn có thể hoàn trên `Payment` (`UC-65 E1`), HOẶC khiếu nại đã ở `DECIDED`/`CLOSED`, HOẶC gọi đóng hồ sơ khi `RefundRequest` chưa `FULFILLED`, **When** `ADMIN` gọi `/decide` hoặc `/close`, **Then** từ chối `409` và không thay đổi dữ liệu tiền hay quyết định cũ. |
| **AC-10** | **Given** hai request quyết định đồng thời trên cùng khiếu nại hoặc cùng `Order`, **When** tranh chấp song song, **Then** khóa bi quan bảo đảm chỉ một quyết định hợp lệ được ghi, người còn lại nhận `409` (hoặc idempotent `200` nếu cùng key), tổng hoàn không bao giờ vượt `verifiedPaid`. |
| **AC-11** | **Given** người dùng sai quyền (khách xem/bổ sung khiếu nại hoặc đơn của người khác; non-ADMIN gọi `/api/v1/admin/complaints/**`; đính kèm file của người khác), **When** gọi API, **Then** trả `401`/`403`/`404`/`400` đúng kiểm soát quyền server. |
| **AC-12** | **Given** migration mới và cập nhật `docs/api/openapi.yaml`, **When** chạy Flyway, `ApiContractTests`, `SwaggerUiTests` và toàn bộ Gradle test suite, **Then** toàn bộ build xanh sạch sẽ. |

---

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn nghiệp vụ | Phạm vi đáp ứng | AC |
|---|---|---|
| **UC-23 chính, A1, A2, E1, E2** (Trang 69–70) | Khách gửi khiếu nại theo đơn & loại, hậu bàn giao, quá 48h D06, validate file/nội dung, idempotency mạng | AC-01, 02, 03, 11 |
| **BR-43 & D06** (Trang 29, 70, 85) | Giữ khoản bù No-show khi khiếu nại đúng hạn 48h; lỗi cửa hàng thì hủy bù hoàn 100%; quá hạn ghi nhận hỗ trợ | AC-02, 03, 07, 08 |
| **UC-63** (Trang 112–113) | Admin tra cứu & lọc danh sách khiếu nại, hiển thị tiền giữ/tranh chấp | AC-04, 11 |
| **UC-64** (Trang 113–114) | Admin xem chi tiết khiếu nại đa chiều (đơn, hộp, kiểm tra, tiền, giao nhận, No-show, lịch sử) | AC-04 |
| **UC-65 chính, A1..A4, E1, E2 & BR-45** (Trang 29, 114–115) | Bổ sung thông tin, quyết định chấp thuận/một phần/bác, nguồn thanh toán, giới hạn hoàn, bất biến quyết định, đóng khi xong | AC-05, 06, 07, 08, 09, 10 |
| **SD-10** (Trang 162, Hình 8.10) | Chuỗi bước 01–37 tiếp nhận → bổ sung → khóa kiểm tra → quyết định → theo dõi T6.5 → đóng | AC-01, 05, 06, 09, 10 |
| **CLAUDE.md** §4, 5, 7, 8 | `Clock`, `BigDecimal` VND, khóa bi quan, idempotency, OpenAPI, TDD integration tests | AC-01, 10, 11, 12 |

---

## 9. Quyết định cần duyệt tại Gate A (`Q1`–`Q5`)

1. **Q1 (Thiết kế bộ API endpoints cho T8.1):**
   - *Đề xuất (Recommended):*
     - **Khách hàng (`CUSTOMER`):**
       - `POST /api/v1/complaints` (tạo khiếu nại, header `Idempotency-Key` tùy chọn → `201`/`200 CustomerComplaintDetail`)
       - `GET /api/v1/complaints` (danh sách khiếu nại của tôi, phân trang & lọc `status`, `orderId`)
       - `GET /api/v1/complaints/{complaintId}` (chi tiết khiếu nại của tôi kèm trạng thái hoàn tiền & bằng chứng)
       - `POST /api/v1/complaints/{complaintId}/supplement` (bổ sung khi `AWAITING_EVIDENCE` → `INVESTIGATING`)
     - **Quản trị viên (`ADMIN`):**
       - `GET /api/v1/admin/complaints` (`UC-63`: lọc `status`, `type`, `storeId`, `orderId`, `from`, `to`)
       - `GET /api/v1/admin/complaints/{complaintId}` (`UC-64`: chi tiết tổng hợp đơn/hộp/thanh toán/bàn giao/no-show/lịch sử)
       - `POST /api/v1/admin/complaints/{complaintId}/request-info` (`UC-65 A1`: chuyển `AWAITING_EVIDENCE`)
       - `POST /api/v1/admin/complaints/{complaintId}/decide` (`UC-65`, header `Idempotency-Key` tùy chọn: `APPROVED` / `PARTIAL` / `REJECTED`)
       - `POST /api/v1/admin/complaints/{complaintId}/close` (đóng thủ công khi `DECIDED` và mọi `RefundRequest` đã `FULFILLED`)
     - **Câu hỏi phụ trong Q1:** Có cần thêm `GET /api/v1/stores/{storeId}/complaints` (chỉ đọc cho Store Owner / nhân viên được ủy quyền) trong T8.1 không, hay giữ riêng Customer + Admin theo đúng actor của `UC-23`, `UC-63..65` (mặc định chưa thêm endpoint Store nếu không yêu cầu)?
   *(Xác nhận thiết kế bộ endpoint này?)*

2. **Q2 (Cho phép tạo nghĩa vụ hoàn bổ sung từ Khiếu nại lên `Payment` gốc — nới lỏng `V16 ux_refund_request_payment`):**
   - Hiện tại `V16` có `ux_refund_request_payment ON refund_request(payment_id) WHERE payment_id IS NOT NULL`, chặn tạo `RefundRequest` thứ hai trên cùng `Payment` dù tiền còn lại chưa hoàn (ví dụ trường hợp `UC-65 A2`: No-show đã hoàn 70% qua T7.4, nay Admin xử lỗi cửa hàng cần hoàn nốt 30% còn lại).
   - *Đề xuất (Recommended):* Migration `V22` thay index trên thành `CREATE UNIQUE INDEX ux_refund_request_payment_non_complaint ON refund_request (payment_id) WHERE payment_id IS NOT NULL AND complaint_id IS NULL;`. Các luồng T6.4/T7.3/T7.4 (`complaint_id IS NULL`) giữ nguyên chống trùng 100%, còn hoàn tiền từ `Complaint` (`complaint_id IS NOT NULL`) được tạo lên đúng `Payment` gốc dưới khóa bi quan bảo đảm tổng tiền hoàn ≤ `payment.receivedAmount`.
   *(Xác nhận hướng điều chỉnh index `V16` này?)*

3. **Q3 (Quy tắc mở rộng bảng `complaint` & mã tiếp nhận `complaintCode` — Migration `V22`):**
   - *Đề xuất (Recommended):* Tạo migration nhỏ `V22__complaint_workflow_and_resolution.sql` giữ nguyên **43 entity** (không thêm bảng mới):
     - Thêm trên `complaint`: `complaint_code` (`UNIQUE`), `within_dispute_window` (`boolean NOT NULL DEFAULT true`), `requested_info_note`, `outcome` (`APPROVED`/`PARTIAL`/`REJECTED`), `responsibility` (`STORE`/`PLATFORM`/`CUSTOMER`/`SHARED`/`NONE`), `approved_refund_amount`, `store_adjustment_amount`, `commission_adjustment_amount`, `closed_at`, `request_fingerprint`, `decision_key`, `decision_fingerprint`.
     - Sửa unique index idempotency thành `(customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL` (vẫn giữ tương thích `uq_complaint_source_box_key` của T7.5).
     - Thay `ux_refund_request_payment` theo **Q2**.
     - Cập nhật số migration `21 -> 22` trong `AuthMigrationTests` và `FoodRescueApplicationTests`.
   *(Xác nhận cấu trúc mở rộng trong migration `V22`?)*

4. **Q4 (Tạo `RefundRequest(PENDING)` từ quyết định khiếu nại và cơ chế tự động đóng hồ sơ khi T6.5 hoàn tất):**
   - *Đề xuất (Recommended):*
     - Khi Admin duyệt hoàn tiền trong `/decide`, hệ thống tạo `RefundRequest(status = PENDING)` (đúng nguyên tắc D3 giống T6.4/T7.3/T7.4/T7.5 để Admin vào T6.5 duyệt & xác nhận chuyển khoản kèm biên lai) và đặt `Complaint.status = DECIDED`.
     - Khi `ManualRefundService.confirm` (T6.5) xác nhận `FULFILLED` cho `RefundRequest` có `complaint != null` và không còn `RefundRequest` nào của `complaint` chưa `FULFILLED` → tự động chuyển `Complaint.status = CLOSED` (`closedAt = now`, ghi `AuditLog`). (Vẫn giữ `POST /close` dự phòng trong trường hợp kiểm tra đóng).
     - Đồng thời bổ sung điều kiện `AND c.status IN ('OPEN', 'INVESTIGATING', 'AWAITING_EVIDENCE', 'DECIDED')` trong `NoShowCompensationService.releaseEligible` để bảo vệ khoản giữ nếu khiếu nại đang chờ chi hoàn ở `DECIDED`.
   *(Xác nhận cơ chế liên thông T6.5 ↔ T8.1 này?)*

5. **Q5 (Ranh giới `FinancialEvent(COMPLAINT_RESOLVED)` và bút toán `LedgerEntry(ADJUSTMENT)` giữa T8.1 & T8.3):**
   - `LedgerEntry(PLATFORM_COMMISSION, STORE_PAYABLE, NO_SHOW_COMPENSATION)` và cơ chế đối soát điều chỉnh thuộc **T8.3**.
   - *Đề xuất (Recommended):* Tại thời điểm Admin quyết định (`decide`), T8.1 ghi `FinancialEvent(COMPLAINT_RESOLVED, complaint = complaint)` và lưu snapshot `responsibility`, `approvedRefundAmount`, `storeAdjustmentAmount`, `commissionAdjustmentAmount` ngay trên `Complaint`; dòng `LedgerEntry(CUSTOMER_REFUND)` thực tế vẫn ghi khi `ManualRefundService.confirm` (T6.5), còn việc sinh bút toán `LedgerEntry(ADJUSTMENT)` điều chỉnh doanh thu/hoa hồng gắn bút toán gốc (`original_entry_id`) thực hiện trong **T8.3** từ dữ liệu bất biến của T8.1.
   *(Xác nhận ranh giới tài chính T8.1 ↔ T8.3 này?)*

---

## 10. Ràng buộc cho Gate B

- Không viết code sản phẩm hay migration `V22` trước khi Gate A và Gate B được người dùng duyệt.
- Không sửa các migration `V1`–`V21` đã chạy.
- Tuân thủ thứ tự khóa `CustomerOrder -> Complaint -> NoShowCase -> Payment (id ASC) -> StoredFile`.
- Không sửa làm hỏng bất kỳ test hiện hữu nào (`NoShowTests`, `BoxUnfitViolationSuspendTests`, `ManualRefundTests`, `OrderCancellationTests`, `PaymentReconciliationTests`).
- Cập nhật đầy đủ `docs/api/openapi.yaml` để vượt qua `ApiContractTests` và `SwaggerUiTests`.
