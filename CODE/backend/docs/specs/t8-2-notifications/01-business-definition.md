# Business Definition: T8.2 — Thông báo (Outbox, Worker gửi & API hộp thư người dùng)

**Status:** Chờ duyệt Gate A  
**Updated:** 2026-11-24  
**Scope:** T8.2 (`FR hệ thống 21` tự động phát thông báo các sự kiện quan trọng qua bảng `notification` làm outbox trong cùng transaction nghiệp vụ; worker nền gửi qua port `NotificationSender` kết hợp email trên `JavaMailSender` sẵn có và kênh in-app với cơ chế retry giới hạn, chuyển trạng thái `PENDING -> SENT / DELIVERED / FAILED`, chống gửi trùng; API xem thông báo của tôi & đánh dấu đã đọc, đồng thời ghi nhận căn cứ nhận thông báo `NoShowCase.notification_received_at` cho `T7.4`/`D06`).  
**Phụ thuộc:** T8.1 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Bối cảnh và mục tiêu

Đến hết **T8.1**, toàn bộ vòng đời cốt lõi của nền tảng (đặt đơn, thanh toán/webhook, hủy đơn, bàn giao, No-show, hoàn tiền thủ công, khiếu nại và xét duyệt cửa hàng) đã hoạt động nhất quán với bất biến tiền và tồn kho. Tuy nhiên:
- Mới chỉ có `NoShowService.confirm` (`T7.4`) ghi tạm 1 bản ghi `Notification(NO_SHOW, PENDING)` vào bảng `notification`; các sự kiện quan trọng khác chưa ghi outbox thông báo.
- Chưa có port `NotificationSender` và worker nền xử lý hàng đợi outbox (`PENDING -> SENT / DELIVERED / FAILED`), chưa có cơ chế retry giới hạn khi gửi email lỗi.
- Chưa có API cho người dùng (`CUSTOMER`, `Store Owner/Staff`, `ADMIN`) tra cứu thông báo của chính mình và đánh dấu đã đọc; do đó `NoShowCompensationService.recordNotificationReceived` (`T7.4`) chưa có điểm kích hoạt HTTP thực tế từ hành động đọc thông báo của khách hàng (`đọc thông báo no-show = căn cứ nhận thông báo cho T7.4` theo `D06`).

Mục tiêu của **T8.2**:
1. **Outbox trong cùng transaction nghiệp vụ (Transactional Outbox):** Mọi sự kiện quan trọng được liệt kê trong `FR-21` và thẻ T8.2 ghi bản ghi `Notification(PENDING)` trong **cùng transaction DB** với nghiệp vụ gốc. Nếu nghiệp vụ gốc rollback → không có bản ghi thông báo; nếu gửi email thất bại → nghiệp vụ gốc đã commit vẫn nguyên vẹn.
2. **Worker gửi thông báo bất đồng bộ qua port `NotificationSender`:** Dùng `FOR UPDATE SKIP LOCKED` (an toàn đa instance, không gửi trùng), gửi qua email (`JavaMailSender` sẵn có) + kênh in-app, hỗ trợ retry giới hạn với backoff có thể cấu hình (`PENDING -> SENT/DELIVERED` hoặc hết lượt retry → `FAILED`).
3. **API Thông báo của tôi & liên thông T7.4:** Người dùng đã đăng nhập xem danh sách/chi tiết thông báo thuộc về mình, lọc theo loại/trạng thái/chưa đọc, đánh dấu đã đọc (`single` và `read-all`). Lần đọc đầu tiên của thông báo `NO_SHOW` tự động gọi `NoShowCompensationService.recordNotificationReceived(notificationId, now)` để bắt đầu đếm cửa sổ phản ánh 48h (`dispute_deadline`) trên `NoShowCase`.

Nguồn nghiệp vụ trích xuất từ `../../DOCX/BaoCaoFoodRescue.pdf` và `roadmap-3-9.md`:
- **Trang 18 (`FR hệ thống 21`):** *Tự động gửi thông báo về các sự kiện quan trọng như đặt hàng thành công, thanh toán, hủy đơn, thay đổi trạng thái, quá hạn nhận và kết quả xử lý No-show.*
- **Trang 29 (`BR-43`), Trang 70 (`D06`), Trang 85 (`UC-37 E3`):** Mốc 48h phản ánh No-show bắt đầu từ thời điểm có căn cứ khách hàng nhận thông báo (`notification_received_at`).
- **Roadmap T8.2:** Mở rộng đầy đủ nhóm sự kiện (đặt hàng, thanh toán, hủy, đổi trạng thái, quá hạn nhận, kết quả no-show, hoàn tiền, khiếu nại, xét duyệt cửa hàng, chuẩn bị sẵn cho đối soát T8.4), outbox worker qua `NotificationSender` (email + in-app), retry giới hạn, API thông báo của tôi & đánh dấu đã đọc.

---

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin dưới `src/main/kotlin/com/foodrescue/backend/`:

1. **Schema & Entity `Notification` đã tạo sẵn từ `V1`–`V2`:**
   - `notification/entity/Notification.kt`: có `recipient` (`Account`), `order` (`CustomerOrder?`), `type` (`NotificationType`), `payload` (`text`), `status` (`NotificationStatus`), `sentAt`, `deliveredAt`, `readAt`.
   - `notification/NotificationStatus.kt`: `PENDING, SENT, DELIVERED, FAILED`.
   - `notification/NotificationType.kt`: `ORDER_CONFIRMED, ORDER_CANCELLED, NO_SHOW, REFUND, COMPLAINT, SETTLEMENT`.
   - **Hạn chế của `V1` cần khắc phục qua migration mới (`Q1`):**
     - `V1` đang giới hạn `CHECK (type IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED', 'NO_SHOW', 'REFUND', 'COMPLAINT', 'SETTLEMENT'))` — chưa có type riêng cho đặt hàng (`ORDER_PLACED`), đổi trạng thái (`ORDER_STATUS`), thanh toán gặp vấn đề/gán (`PAYMENT`), và xét duyệt/khóa cửa hàng (`STORE_REVIEW`).
     - Bảng `notification` chưa có cột phục vụ outbox bền vững: `event_code`, `title`, `dedupe_key` (unique chống ghi trùng/gửi trùng khi retry/idempotency), `attempt_count`, `next_attempt_at`, `last_error`, và index tra cứu theo hàng đợi `PENDING` / hộp thư người dùng.
   - `notification/repository/NotificationRepository.kt`: mới chỉ là interface rỗng kế thừa `JpaRepository<Notification, UUID>`.
2. **Điểm liên thông No-show (`T7.4`) & `NoShowTests`:**
   - `pickup/service/NoShowService.kt` (dòng 142–147) đã lưu `Notification(order.customer, order, NotificationType.NO_SHOW, jsonPayload, NotificationStatus.PENDING)` và gắn vào `NoShowCase.notification`.
   - `pickup/service/NoShowCompensationService.kt` (dòng 19–30) đã cài đặt `recordNotificationReceived(notificationId, receivedAt)` (idempotent theo `if (case.notificationReceivedAt == null)`), cập nhật `notificationReceivedAt` và `disputeDeadline = receivedAt + order.disputeWindowHours`, ghi `AuditLog("NO_SHOW_NOTIFICATION_RECEIVED")`.
   - `NoShowTests.kt` kiểm chứng: khi xác nhận No-show tạo `NotificationStatus.PENDING` và nếu transaction rollback thì `notification` count = 0. Do đó các trường bổ sung trên `Notification` cần có giá trị mặc định an toàn để giữ tương thích hoàn toàn.
3. **Hạ tầng Mail & Scheduling hiện có:**
   - `build.gradle.kts` đã khai báo `org.springframework.boot:spring-boot-starter-mail`.
   - `application.yml` & `application-prod.yml` đã cấu hình `spring.mail.*` (`JavaMailSender`) và `auth.mail-from` (default `noreply@foodrescue.local`).
   - `auth/mail/AuthMailSender.kt` & `SmtpAuthMailSender.kt` chỉ phục vụ token xác thực (`TokenPurpose`). T8.2 cần port riêng `NotificationSender` trong gói `notification/` sử dụng `JavaMailSender` sẵn có (không sửa `auth/mail`).
   - `middleware/ratelimit/RateLimitCleanupJob.kt` đã bật `@EnableScheduling`; các job nền (`OrderExpiryJob`, `NoShowOverdueJob`, `NoShowCompensationJob`, `PaymentReconciliationJob`) đều dùng `@ConditionalOnProperty` và được tắt mặc định trong `support/AuthTestFixtures.kt` (`TestAuthKeys.configure`) để test gọi logic service trực tiếp với `Clock` cố định.
4. **Danh sách service nghiệp vụ sẽ phát sự kiện outbox:**
   - **Đặt hàng:** `OrderPlacementService.place`
   - **Thanh toán:** `PaymentConfirmationService.record` (`CONFIRMED`, `REQUIRES_REVIEW`), `AdminPaymentService.assign`
   - **Hủy đơn & Quá hạn giữ chỗ:** `OrderReleaser.release` (unpaid `EXPIRED` / `CANCELLED`), `PaidOrderCancellationExecutor.execute` (paid `CANCELLED` bởi khách/cửa hàng/admin/box unfit/khóa cửa hàng), `OrderCancellationService.submitRequest` / `supplement` / `withdraw`, `AdminCancellationService.decide` (`REJECT` / `REQUEST_INFO` / `APPROVE`)
   - **Đổi trạng thái bàn giao:** `PickupSessionService.startSession` (`HANDOVER_IN_PROGRESS`), `PickupSessionService.completeSession` (`COMPLETED`), `PickupIncidentService.recordIncident` (`HANDOVER_INCIDENT`)
   - **Quá hạn nhận & No-show:** `NoShowOverdueService.markOverdue` (`PENDING_NO_SHOW`), `NoShowService.confirm` (`NO_SHOW`), `NoShowCompensationService.releaseEligible` (`ELIGIBLE`)
   - **Hoàn tiền:** `ManualRefundService.approve`, `ManualRefundService.confirm`, `ManualRefundService.fail`
   - **Khiếu nại:** `ComplaintService.submit`, `ComplaintService.supplement`, `AdminComplaintService.requestInfo`, `AdminComplaintService.decide`, `AdminComplaintService.close`, `ManualRefundService.confirm` (auto-close), `BoxUnfitProcessor.process` (sự cố hậu giao tạo `Complaint`)
   - **Xét duyệt & trạng thái cửa hàng:** `PartnerApplicationReviewService.decide`, `StoreChangeReviewService.decide`, `StoreStatusService.change`
   - **Đối soát (T8.4):** cung cấp sẵn `NotificationType.SETTLEMENT` và API nội bộ trên outbox service để T8.4 gọi trong cùng transaction.
5. **Baseline kiểm thử:**
   - `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`: **BUILD SUCCESSFUL** (`22` migrations `V1`–`V22`, `43` Hibernate entities, `116` OpenAPI paths / `28` tags; `315 PASS, 0 FAIL, 1 SKIPPED` theo báo cáo T8.1).

---

## 3. Người dùng và quyền

- **Mọi tài khoản đã đăng nhập (`CUSTOMER`, `Store Owner/Staff`, `ADMIN`):**
  - Xem danh sách thông báo gửi cho chính mình (`GET /api/v1/notifications`), số thông báo chưa đọc (`GET /api/v1/notifications/unread-count`), xem chi tiết thông báo của mình (`GET /api/v1/notifications/{notificationId}`), đánh dấu đã đọc (`POST /api/v1/notifications/{notificationId}/read` và `POST /api/v1/notifications/read-all`).
  - **Kiểm soát sở hữu (`IDOR`):** Mọi truy vấn/cập nhật chỉ tác động lên bản ghi có `recipient.id == CurrentAccount.get().id`. Truy cập thông báo của tài khoản khác hoặc không tồn tại → **404 `NOT_FOUND`**.
- **Chưa đăng nhập / token không hợp lệ:** **401 `UNAUTHORIZED`**.
- **Tác nhân hệ thống (Scheduled Outbox Worker):**
  - Chạy nền qua `@Scheduled` (`NotificationDispatchJob`), logic chính đặt trong service nhận `Clock` để test gọi trực tiếp; dùng `FOR UPDATE SKIP LOCKED` + lease claim để nhiều instance không gửi trùng một thông báo.
- **Lưu ý phân quyền cửa hàng cho thông báo cửa hàng:**
  - Người đại diện nhận thông báo của cửa hàng là `store.owner` (chủ cửa hàng; riêng đơn đăng ký đối tác chưa duyệt là `application.applicant`), đúng theo mô hình MVP một chủ một cửa hàng (`D8`) và bảo đảm `Notification.recipient` luôn là một `Account` cụ thể.

---

## 4. Luồng chính

### 4.1. Ghi Outbox đồng thời với sự kiện nghiệp vụ
1. Trong cùng transaction `@Transactional` của nghiệp vụ gốc (ví dụ đặt đơn, xác nhận thanh toán, hủy đơn, quá hạn nhận, xác nhận No-show, duyệt/hoàn tất hoàn tiền, xử lý khiếu nại, duyệt cửa hàng), service nghiệp vụ gọi `NotificationOutboxService.enqueue(...)` (`Propagation.MANDATORY`).
2. `NotificationOutboxService` tạo bản ghi `Notification`:
   - `recipient`: tài khoản nhận (`customer`, `store.owner`, `applicant`).
   - `order`: đơn hàng liên quan (nếu có).
   - `type`: phân loại `NotificationType` và `eventCode` cụ thể (ví dụ `ORDER_CREATED`, `PAYMENT_VERIFIED`, `ORDER_EXPIRED`, `ORDER_PENDING_NO_SHOW`, `NO_SHOW_CONFIRMED`, `REFUND_SUCCEEDED`, `COMPLAINT_INFO_REQUESTED`, `PARTNER_APPLICATION_APPROVED`...).
   - `title` + `payload` (JSON chuẩn hóa các thông số định danh/số tiền/trạng thái không chứa secret).
   - `dedupeKey` (tùy chọn theo khóa tự nhiên của sự kiện, ví dụ `"ORDER_CONFIRMED:${order.id}:${payment.id}"`, `"NO_SHOW_CONFIRMED:${order.id}"`, `"REFUND_SUCCEEDED:${refund.id}"`): nếu đã tồn tại bản ghi cùng `dedupeKey` (do replay idempotency), bỏ qua hoặc trả lại bản ghi cũ mà **không tạo bản ghi trùng**.
   - `status = PENDING`, `attemptCount = 0`, `nextAttemptAt = now`.
3. Nếu transaction nghiệp vụ gốc gặp lỗi và rollback → bản ghi `Notification` cũng bị rollback theo (không bao giờ phát thông báo "ma" cho sự kiện không thành công).

### 4.2. Worker gửi thông báo & retry giới hạn (`NotificationDispatchService`)
1. Worker chạy theo chu kỳ cấu hình (`notifications.dispatch-scan-ms`, mặc định `10000` ms) và có thể gọi trực tiếp trong test với `Clock`:
2. **Pha 1 — Claim lô (TX1 ngắn):**
   - Dùng `SELECT id FROM notification WHERE status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= :now) ORDER BY created_at ASC, id ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED`.
   - Đánh dấu tạm thời `nextAttemptAt = now + leaseSeconds` (ví dụ `60s`) và lưu lại trong TX1 để đảm bảo:
     - Hai instance/worker chạy song song **không bao giờ claim trùng** cùng một `notificationId`.
     - **Không giữ khóa DB** trong lúc gọi SMTP ra ngoài (tuân thủ nguyên tắc cô lập I/O ngoài trong `.agents/CLAUDE.md` §5).
3. **Pha 2 — Gửi qua port `NotificationSender` (ngoài transaction DB):**
   - `NotificationSender.send(command)` thực hiện gửi thông báo qua kênh email (qua `JavaMailSender` tới `recipient.loginIdentifier`, chỉ gửi khi có cấu hình/kích hoạt mail hợp lệ; kênh in-app chính là bản ghi đã tồn tại trong hộp thư của người dùng).
   - Nếu `NotificationSender` ném ngoại lệ hoặc trả về lỗi → bắt ngoại lệ an toàn, chuyển sang Pha 3 ghi kết quả lỗi (không làm crash worker hay ảnh hưởng dữ liệu nghiệp vụ gốc).
4. **Pha 3 — Cập nhật kết quả (TX2 ngắn):**
   - Khóa bản ghi `Notification` bằng `findForUpdate`: chỉ xử lý nếu vẫn `status == PENDING`.
   - **Nếu gửi thành công:**
     - Cập nhật `attemptCount = attemptCount + 1`, `lastError = null`, `sentAt = sentAt ?: now`, và chuyển trạng thái theo kết quả từ `NotificationSender` (`DELIVERED` kèm `deliveredAt = deliveredAt ?: now` khi cả in-app + email hoàn tất, hoặc `SENT` nếu chỉ xác nhận đã chuyển đi). Xem câu hỏi **Q3**.
   - **Nếu gửi thất bại (`error`):**
     - Tăng `newAttempts = attemptCount + 1`, ghi `lastError` (cắt gọn an toàn, không chứa thông tin nhạy cảm).
     - Nếu `newAttempts < maxAttempts`: giữ `status = PENDING`, đặt `nextAttemptAt = now + retryDelay(newAttempts)`.
     - Nếu `newAttempts >= maxAttempts`: đặt `status = FAILED`, `nextAttemptAt = null`.

### 4.3. Người dùng xem hộp thư & đánh dấu đã đọc (`NoShowCase.notification_received_at`)
1. Người dùng gọi `GET /api/v1/notifications` (lọc `type`, `status`, `unreadOnly`, phân trang `PageQuery`) hoặc `GET /api/v1/notifications/unread-count` để xem thông báo của mình.
2. Người dùng gọi `GET /api/v1/notifications/{notificationId}` để xem chi tiết nội dung + `payload`.
3. Người dùng gọi `POST /api/v1/notifications/{notificationId}/read` (hoặc `POST /api/v1/notifications/read-all`):
   - Kiểm tra `recipient.id == currentUserId` (else `404`).
   - Nếu `readAt == null`: đặt `readAt = now`; nếu trạng thái hiện tại là `PENDING` hoặc `SENT`, cập nhật `sentAt = sentAt ?: now`, `deliveredAt = deliveredAt ?: now`, `status = DELIVERED`.
   - **Liên thông T7.4 / D06:** Nếu `notification.type == NotificationType.NO_SHOW`, gọi `noShowCompensationService.recordNotificationReceived(notification.id!!, now)` trong cùng transaction. Lần đọc đầu tiên sẽ cố định `NoShowCase.notificationReceivedAt = now` và `disputeDeadline = now + order.disputeWindowHours`; các lần đọc lại hoặc chạy `read-all` lần sau giữ nguyên mốc nhận đầu tiên (`idempotent`).

---

## 5. Quy tắc nghiệp vụ

- **BR-T82-01 (Transactional consistency):** Bản ghi `Notification` bắt buộc được tạo trong cùng transaction DB với sự kiện nghiệp vụ (`Propagation.MANDATORY` tại outbox helper). Rollback nghiệp vụ đồng nghĩa không tồn tại thông báo.
- **BR-T82-02 (Isolation from delivery failures):** Lỗi SMTP / `NotificationSender` không bao giờ làm rollback hoặc hỏng giao dịch đặt đơn, thanh toán, hủy đơn, No-show, hoàn tiền, khiếu nại hay duyệt cửa hàng.
- **BR-T82-03 (Idempotency & No duplicate delivery):**
  - Chống tạo trùng outbox bằng `dedupe_key` (`UNIQUE WHERE dedupe_key IS NOT NULL`) khi một thao tác nghiệp vụ được replay idempotent.
  - Chống gửi trùng tại worker bằng `FOR UPDATE SKIP LOCKED` + lease claim (`nextAttemptAt`) + kiểm tra trạng thái `PENDING` dưới khóa dòng khi ghi nhận kết quả.
- **BR-T82-04 (Bounded retry & state machine):** Chỉ các thông báo ở trạng thái `PENDING` mới được worker gửi lại tối đa `maxAttempts` lần theo lịch backoff; vượt quá `maxAttempts` chuyển hẳn sang `FAILED` và ngừng retry tự động. Thông báo đã `SENT`/`DELIVERED`/`FAILED` không bị worker gửi lại.
- **BR-T82-05 (No-show receipt & 48h window — `BR-43`, `D06`, `UC-37 E3`):** Chỉ hành động đánh dấu đã đọc thông báo `NO_SHOW` lần đầu của khách hàng mới ghi nhận `NoShowCase.notificationReceivedAt` và mở đồng hồ 48h (`disputeDeadline`), không tự gán lúc tạo `NoShowCase` hay khi email mới gửi đi (`PENDING`/`SENT`) nếu khách chưa đọc/nhận.
- **BR-T82-06 (Ownership & Privacy):** Mọi endpoint `/api/v1/notifications/**` chỉ trả dữ liệu thuộc `recipient.id == accountId`; nội dung lỗi/payload/email không lộ JWT, mật khẩu, checksum hay khóa bí mật.

---

## 6. Phạm vi

### Trong phạm vi (In-Scope):
1. Migration `V23__notification_outbox_and_types.sql` (mở rộng `NotificationType`, bổ sung cột outbox/retry/dedupe trên bảng `notification`), giữ nguyên `43` Hibernate entity.
2. Port `NotificationSender`, adapter email + in-app (`JavaMailSender`) và bản giả test `RecordingNotificationSender`.
3. `NotificationOutboxService` + `NotificationDispatchService` + `NotificationDispatchJob` (`@Scheduled` với cờ bật/tắt).
4. Tích hợp ghi outbox đồng bộ transaction vào các sự kiện: đặt hàng, thanh toán, hủy/hết hạn, bàn giao/đổi trạng thái, quá hạn nhận (`PENDING_NO_SHOW`), No-show (`NO_SHOW` + `ELIGIBLE`), hoàn tiền (`T6.5`), khiếu nại (`T7.5` + `T8.1`), xét duyệt/đổi thông tin/khóa-mở cửa hàng (`T3.2`, `T3.3`, `T7.5`), và hàm sẵn cho đối soát (`T8.4`).
5. API người dùng: danh sách thông báo của tôi, đếm chưa đọc, chi tiết, đánh dấu đã đọc (`read`, `read-all`) kèm kích hoạt `NoShowCompensationService.recordNotificationReceived` cho thông báo `NO_SHOW`.
6. Cập nhật `docs/api/openapi.yaml` và bộ test TDD đầy đủ (bao gồm test rollback, lỗi gửi email + retry giới hạn + `FAILED`, chống gửi trùng đồng thời, liên thông đọc thông báo No-show → `dispute_deadline` → 48h `ELIGIBLE`).

### Ngoài phạm vi (Out-of-Scope):
1. Push notification FCM/APNs tới thiết bị di động (kênh in-app hoạt động qua bảng `notification` + API hộp thư; email gửi qua `JavaMailSender`; cấu trúc port `NotificationSender` cho phép mở rộng kênh push sau này).
2. Nghiệp vụ tính hoa hồng/bút toán `T8.3` và chốt bảng đối soát/chi trả `T8.4` (`T8.2` chỉ chuẩn bị sẵn `NotificationType.SETTLEMENT` và method trên outbox).
3. Cho phép admin/user gửi thông báo quảng cáo tùy ý hoặc xóa cứng thông báo (chỉ hỗ trợ thông báo sự kiện hệ thống).

---

## 7. Acceptance Criteria (Given / When / Then)

| ID | Given / When / Then |
|---|---|
| **AC-01** | **Given** các sự kiện đặt hàng (`ORDER_PLACED`), xác nhận thanh toán/không khớp (`ORDER_CONFIRMED`, `PAYMENT`), hủy đơn/hết hạn giữ chỗ (`ORDER_CANCELLED`, `ORDER_STATUS`), bắt đầu/hoàn tất bàn giao (`ORDER_STATUS`), **When** giao dịch thành công, **Then** tạo bản ghi `Notification(PENDING)` tương ứng trong cùng transaction (khách hàng và chủ cửa hàng khi phù hợp); nếu transaction rollback thì không có bản ghi `Notification` nào được lưu. |
| **AC-02** | **Given** sự kiện quá hạn nhận (`PENDING_NO_SHOW`), xác nhận No-show (`NO_SHOW`), mở khóa bù đắp (`ELIGIBLE`), hoàn tiền (`approve`/`confirm`/`fail`), khiếu nại (`submit`/`request-info`/`supplement`/`decide`/`close`/hậu giao `T7.5`), và xét duyệt/khóa-mở cửa hàng (`PartnerApplication`, `StoreChangeRequest`, `StoreStatus`), **When** thao tác nghiệp vụ thành công, **Then** tạo đúng thông báo `PENDING` cho đúng người nhận (`customer`, `store.owner`, `applicant`) trong cùng transaction; replay idempotency không tạo trùng thông báo. |
| **AC-03** | **Given** các thông báo đang `PENDING` đến hạn gửi, **When** `NotificationDispatchService` chạy với `Clock` và `NotificationSender` thành công, **Then** thông báo chuyển sang `DELIVERED` (hoặc `SENT` theo **Q3**), ghi nhận `sentAt`/`deliveredAt`, và `NotificationSender` nhận đúng nội dung email/in-app đúng **một** lần. |
| **AC-04** | **Given** `NotificationSender` gặp lỗi khi gửi (exception / SMTP down), **When** nghiệp vụ gốc chạy và worker dispatch thực thi qua các mốc thời gian retry theo `Clock`, **Then** nghiệp vụ gốc vẫn thành công (không rollback/500), thông báo giữ `PENDING` với `attemptCount` tăng và `nextAttemptAt` theo backoff, và khi vượt quá `maxAttempts` chuyển sang `FAILED` và không được retry tiếp. |
| **AC-05** | **Given** hai luồng worker `NotificationDispatchService` chạy đồng thời trên cùng tập `Notification(PENDING)` (kèm `FOR UPDATE SKIP LOCKED` + lease), **When** cả hai cùng quét và gửi, **Then** mỗi thông báo chỉ được `NotificationSender` gửi đúng **một** lần duy nhất, không gửi trùng. |
| **AC-06** | **Given** người dùng đã đăng nhập (`CUSTOMER`, Owner/Staff, `ADMIN`), **When** gọi `GET /api/v1/notifications` (lọc `type`, `status`, `unreadOnly`), `GET /api/v1/notifications/unread-count`, `GET /api/v1/notifications/{id}`, **Then** chỉ thấy thông báo của chính mình (của người khác trả `404`), chưa đăng nhập trả `401`. |
| **AC-07** | **Given** khách hàng có `NoShowCase` chưa có `notificationReceivedAt` và có thông báo `NO_SHOW` chưa đọc, **When** khách hàng gọi `POST /api/v1/notifications/{id}/read` (hoặc `read-all`), **Then** thông báo ghi `readAt = now`, chuyển `DELIVERED`, đồng thời `NoShowCase.notificationReceivedAt = now` và `disputeDeadline = now + disputeWindowHours` (48h); đọc lại nhiều lần giữ nguyên mốc đầu tiên và sau 48h `NoShowCompensationService.releaseEligible` chuyển khoản giữ sang `ELIGIBLE`. |
| **AC-08** | **Given** migration `V23`, cập nhật `Notification`, các service liên quan và `docs/api/openapi.yaml`, **When** chạy Flyway, `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests` và toàn bộ Gradle test suite, **Then** toàn bộ build xanh, không làm vỡ test của các task trước (bao gồm `NoShowTests`). |

---

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn nghiệp vụ | Phạm vi đáp ứng | AC |
|---|---|---|
| **FR hệ thống 21** (Trang 18) | Tự động phát thông báo cho đặt hàng, thanh toán, hủy đơn, đổi trạng thái, quá hạn nhận, kết quả No-show (và mở rộng T8.2: hoàn tiền, khiếu nại, xét duyệt cửa hàng, sẵn sàng T8.4) | AC-01, AC-02 |
| **Roadmap T8.2** | Outbox trong transaction, `NotificationSender` (email + in-app), retry giới hạn `PENDING -> SENT/DELIVERED/FAILED`, API thông báo của tôi & đọc thông báo, rollback/không hỏng nghiệp vụ/không gửi trùng | AC-01..07 |
| **BR-43, D06, UC-37 E3** (Trang 29, 70, 85) | Đọc thông báo No-show (`read`) = căn cứ nhận thông báo (`notification_received_at`) khởi động 48h `dispute_deadline` | AC-07 |
| **CLAUDE.md** §1, 4, 5, 7, 8 | Port + adapter + test double, `Clock`, `FOR UPDATE SKIP LOCKED`, cô lập I/O ngoài transaction, OpenAPI, TDD | AC-03, 04, 05, 06, 08 |

---

## 9. Quyết định cần duyệt tại Gate A (`Q1`–`Q5`)

1. **Q1 (Mở rộng `NotificationType` và cột Outbox trên bảng `notification` qua Migration `V23`):**
   Hiện bảng `notification` (`V1`) mới có 6 `type` (`ORDER_CONFIRMED, ORDER_CANCELLED, NO_SHOW, REFUND, COMPLAINT, SETTLEMENT`) và chưa có cột `title`, `event_code`, `dedupe_key`, `attempt_count`, `next_attempt_at`, `last_error`.
   - *Đề xuất (Recommended):* Tạo migration `V23__notification_outbox_and_types.sql` (giữ nguyên `V1`–`V22`, giữ nguyên `43` Hibernate entities):
     - Mở rộng `NotificationType` và DB `CHECK` thêm `ORDER_PLACED`, `ORDER_STATUS`, `PAYMENT`, `STORE_REVIEW` (giữ nguyên 6 giá trị cũ để tương thích ngược).
     - Thêm lên bảng `notification`: `event_code varchar(64)`, `title varchar(255)`, `dedupe_key varchar(255)`, `attempt_count int NOT NULL DEFAULT 0 CHECK (attempt_count >= 0)`, `next_attempt_at timestamptz`, `last_error text`.
     - Thêm index `uq_notification_dedupe_key ON notification(dedupe_key) WHERE dedupe_key IS NOT NULL`, `idx_notification_outbox_due ON notification(status, next_attempt_at, created_at) WHERE status = 'PENDING'`, `idx_notification_recipient_created ON notification(recipient_id, created_at DESC)`.
   - *Phương án thay thế:* Không thêm giá trị enum mới (gói tất cả vào 6 enum cũ + phân biệt trong JSON `payload`), vẫn cần `V23` để lưu `attempt_count`, `next_attempt_at`, `last_error`, `dedupe_key`.
   *(Xác nhận đề xuất mở rộng `NotificationType` + cột outbox trong `V23`?)*

2. **Q2 (Phạm vi chỉnh sửa các service task trước để gắn outbox trong cùng transaction — `.agents/CLAUDE.md` §1):**
   Để outbox ghi trong **cùng transaction** với mọi sự kiện trong thẻ T8.2 (đặt hàng, thanh toán, hủy, đổi trạng thái, quá hạn nhận, No-show, hoàn tiền, khiếu nại, xét duyệt/khóa cửa hàng), cần gọi `NotificationOutboxService.enqueue(...)` tại các service nghiệp vụ đã có của T3.2, T3.3, T5.2, T5.3, T6.3, T6.4, T6.5, T7.2, T7.3, T7.4, T7.5, T8.1 (hoặc qua Spring `ApplicationEvent` đồng bộ `@TransactionalEventListener(phase = BEFORE_COMMIT)` / gọi trực tiếp `NotificationOutboxService`).
   - *Đề xuất (Recommended):* Phát sự kiện nhẹ hoặc gọi trực tiếp `NotificationOutboxService` (`Propagation.MANDATORY` trong transaction) tại đúng điểm chuyển trạng thái của từng service liên quan (chi tiết danh sách file sẽ ghi rõ trong `02-plan.md`), bảo đảm không thay đổi bất kỳ rule nghiệp vụ hay chữ ký API cũ nào của T3–T8.1. (Dùng `NotificationOutboxService` trực tiếp rõ ràng hơn về trace & kiểm tra compile-time so với event ngầm.)
   *(Xác nhận cho phép tích hợp gọi outbox vào các service sự kiện hiện hữu?)*

3. **Q3 (Cơ chế chuyển trạng thái `PENDING -> SENT / DELIVERED / FAILED` qua `NotificationSender`):**
   - *Đề xuất (Recommended):*
     - Khi worker gọi `NotificationSender.send(command)` thành công (email gửi qua `JavaMailSender` + kênh in-app khả dụng trong hộp thư DB): chuyển trực tiếp `PENDING -> DELIVERED` (ghi cả `sentAt = now` và `deliveredAt = now`), hoặc nếu `NotificationSender` báo chỉ mới chuyển hàng đợi ngoài chưa xác nhận tới thiết bị thì chuyển `SENT` (`sentAt = now`) và khi người dùng đọc/truy cập in-app sẽ hoàn tất `DELIVERED` (`deliveredAt = deliveredAt ?: now`). (Với adapter mặc định `EmailAndInAppNotificationSender` gửi SMTP đồng bộ + in-app đã sẵn sàng trong DB, trả `DELIVERED` khi email gửi thành công — hoặc nếu tách rõ email `SENT` và in-app `DELIVERED`, đều được mô hình hóa rõ qua enum trả về của `NotificationSender`!)
     - Với thất bại: retry tối đa `maxAttempts = 3` (cấu hình được qua `notifications.max-attempts`, backoff mặc định `60s`, `300s`), quá ngưỡng chuyển `FAILED` và ngừng retry.
   *(Bạn muốn adapter mặc định trả `DELIVERED` ngay khi SMTP + in-app gửi thành công, hay trả `SENT` rồi chỉ lên `DELIVERED` khi người dùng đọc/nhận trên in-app?)*

4. **Q4 (Thiết kế bộ API Thông báo của tôi & hành động đánh dấu đã đọc):**
   - *Đề xuất (Recommended):* Tag `"Notifications"`, tất cả yêu cầu JWT hợp lệ (`CUSTOMER`, Owner/Staff, `ADMIN` chỉ truy cập thông báo của chính mình):
     - `GET /api/v1/notifications` — query `type` (`NotificationType?`), `status` (`NotificationStatus?`), `unreadOnly` (`Boolean?`), `page`, `size` → `PageResponse<NotificationView>`.
     - `GET /api/v1/notifications/unread-count` → `UnreadNotificationCountView(unreadCount)`.
     - `GET /api/v1/notifications/{notificationId}` → `NotificationView` (read-only, không tự đổi `readAt`).
     - `POST /api/v1/notifications/{notificationId}/read` → đánh dấu đã đọc (`readAt`), kích hoạt `NoShowCompensationService.recordNotificationReceived` nếu là `NO_SHOW`, trả `NotificationView`.
     - `POST /api/v1/notifications/read-all` → đánh dấu đã đọc toàn bộ thông báo chưa đọc của tài khoản hiện tại (gọi `recordNotificationReceived` cho mọi `NO_SHOW` vừa đọc), trả `NotificationReadAllView(updatedCount)`.
   *(Xác nhận cấu trúc 5 endpoint này?)*

5. **Q5 (Ranh giới sự kiện `ORDER_CONFIRMED` / `ORDER_CANCELLED` / `PENDING_NO_SHOW` / `COMPLAINT` gửi cho Store Owner vs Customer):**
   - *Đề xuất (Recommended):*
     - **Customer (`order.customer`):** nhận thông báo đặt giữ chỗ (`ORDER_PLACED`), thanh toán xác nhận (`ORDER_CONFIRMED`) & thanh toán cần review (`PAYMENT`), hủy đơn/hết hạn (`ORDER_CANCELLED`/`ORDER_STATUS`), bắt đầu/hoàn tất bàn giao & sự cố (`ORDER_STATUS`), quá hạn nhận (`ORDER_STATUS`), kết quả No-show (`NO_SHOW`), các bước hoàn tiền (`REFUND`), các bước khiếu nại (`COMPLAINT`), và xét duyệt đối tác nếu chính mình nộp (`STORE_REVIEW`).
     - **Store Owner (`store.owner`):** nhận thông báo khi đơn đã thanh toán mới (`ORDER_CONFIRMED`), đơn đã thanh toán bị hủy hoặc khách yêu cầu hủy (`ORDER_CANCELLED`/`ORDER_STATUS`), đơn quá hạn nhận cần xác minh (`ORDER_STATUS` `PENDING_NO_SHOW`), khoản bù No-show đủ điều kiện (`NO_SHOW` `ELIGIBLE`), khiếu nại mới/được quyết định liên quan cửa hàng (`COMPLAINT`), kết quả xét duyệt đơn đăng ký/thay đổi/tạm khóa-mở lại cửa hàng (`STORE_REVIEW`), và đối soát (`SETTLEMENT` cho T8.4).
     - Không gửi thông báo rác cho cửa hàng khi khách mới tạo đơn chưa trả tiền (`PENDING_PAYMENT`) hoặc tự hết hạn trước khi trả tiền (`EXPIRED`).
   *(Xác nhận ma trận người nhận Customer vs Store Owner này?)*

---

## 10. Ràng buộc cho Gate B

- Không viết code sản phẩm hay migration `V23` trước khi Gate A và Gate B được người dùng duyệt.
- Không sửa các migration `V1`–`V22` đã chạy.
- Mọi thao tác worker quét outbox chạy với `FOR UPDATE SKIP LOCKED`, giải phóng khóa trước khi gọi I/O gửi email (`NotificationSender`), dùng `Clock` (không gọi `Instant.now()` trực tiếp).
- Không gửi email SMTP thật trong bất kỳ test tự động nào; dùng test double (`RecordingNotificationSender`) trong test.
- Cập nhật đầy đủ `docs/api/openapi.yaml` và bảo đảm mọi test cũ (`NoShowTests`, `ComplaintTests`, `ManualRefundTests`, `OrderCancellationTests`, `SwaggerUiTests`, `ApiContractTests`, `FoodRescueApplicationTests`, `AuthMigrationTests`) đều pass.
