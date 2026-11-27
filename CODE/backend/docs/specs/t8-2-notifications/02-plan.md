# Technical Plan: T8.2 — Thông báo (Outbox, Worker gửi & API hộp thư người dùng)

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A đã được người dùng duyệt `Q1`–`Q5` (giữ đúng scope Outbox DB + Email `JavaMailSender` + In-App REST API, chưa mở WebSocket/SSE) ngày 2026-11-24.  
**Status:** Hoàn tất triển khai và kiểm chứng (336 PASS / 0 FAIL / 1 SKIPPED)  
**Updated:** 2026-11-24  
**Scope:** T8.2 (`FR hệ thống 21`, `NoShowCase.notification_received_at`, `D06`, `BR-43`: Transactional Outbox trong cùng transaction nghiệp vụ; worker gửi qua port `NotificationSender` kết hợp email `JavaMailSender` + in-app, `FOR UPDATE SKIP LOCKED`, retry giới hạn `PENDING -> SENT / DELIVERED / FAILED`; API thông báo của tôi & đánh dấu đã đọc kích hoạt mốc 48h `NoShowCase`).  
**Phụ thuộc:** T8.1 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Quyết định đã duyệt tại Gate A và Baseline

1. **Các quyết định Gate A đã chốt (`Q1`–`Q5`):**
   - **Q1 (Mở rộng `NotificationType` & cột Outbox trong `V23__notification_outbox_and_types.sql`):**
     - Bổ sung 4 loại `NotificationType`: `ORDER_PLACED`, `ORDER_STATUS`, `PAYMENT`, `STORE_REVIEW` (giữ nguyên 6 loại cũ `ORDER_CONFIRMED`, `ORDER_CANCELLED`, `NO_SHOW`, `REFUND`, `COMPLAINT`, `SETTLEMENT`).
     - Bổ sung cột trên `notification`: `event_code`, `title`, `dedupe_key`, `attempt_count`, `next_attempt_at`, `last_error`, cùng các index `uq_notification_dedupe_key`, `idx_notification_outbox_due`, `idx_notification_recipient_created`. Tổng Hibernate entity giữ nguyên `43`; số migration tăng `22` → `23`.
   - **Q2 (Tích hợp ghi outbox trong cùng transaction nghiệp vụ):**
     - Gọi `NotificationOutboxService.enqueue(...)` (`Propagation.MANDATORY`) ngay trong transaction của các service sự kiện hiện hữu (đặt hàng, thanh toán, hủy/hết hạn, bàn giao, quá hạn nhận, No-show, hoàn tiền, khiếu nại, xét duyệt/khóa cửa hàng, chuẩn bị sẵn hàm `SETTLEMENT` cho T8.4).
   - **Q3 (Port `NotificationSender`, email + in-app, vòng đời `PENDING -> SENT / DELIVERED / FAILED` và retry giới hạn):**
     - Worker tách TX1 (claim bằng `FOR UPDATE SKIP LOCKED` + lease `next_attempt_at`) → gọi `NotificationSender` ngoài DB transaction → TX2 (cập nhật kết quả dưới khóa dòng `findForUpdate`).
     - Adapter mặc định `SmtpAndInAppNotificationSender` gửi email qua `JavaMailSender` sẵn có + kênh in-app qua bảng `notification`, trả `DELIVERED` khi hoàn tất (hỗ trợ cả `SENT` nếu adapter chỉ chuyển hàng đợi và tự nâng lên `DELIVERED` khi người dùng đọc). Retry theo lịch backoff có cấu hình; vượt `maxAttempts` chuyển `FAILED`.
   - **Q4 (Bộ API Thông báo của tôi & kích hoạt `NoShowCase.notification_received_at`):**
     - 5 endpoints dưới `/api/v1/notifications/**` (tag `"Notifications"`): danh sách phân trang/lọc, đếm chưa đọc, chi tiết, đánh dấu đã đọc (`/{id}/read`), đánh dấu đã đọc tất cả (`/read-all`). Lần đọc đầu tiên của thông báo `NO_SHOW` gọi `NoShowCompensationService.recordNotificationReceived(notificationId, now)` trong cùng transaction.
   - **Q5 (Ma trận người nhận Customer vs Store Owner):**
     - Khách hàng nhận vòng đời đơn/thanh toán/hủy/bàn giao/no-show/hoàn tiền/khiếu nại/đăng ký đối tác của mình; Chủ cửa hàng (`store.owner`) nhận đơn đã xác nhận thanh toán, hủy đơn đã xác nhận/yêu cầu hủy, quá hạn nhận `PENDING_NO_SHOW`, bù đắp `ELIGIBLE`, khiếu nại liên quan cửa hàng, xét duyệt/đổi thông tin/khóa-mở cửa hàng và đối soát (`T8.4`).
   - **Phạm vi kênh gửi:** Giữ đúng scope Gate A (Outbox DB + Email + In-App REST API), chưa thêm WebSocket/SSE/FCM; thiết kế qua port `NotificationSender` để mở rộng kênh đẩy sau này không đụng nghiệp vụ.
2. **Baseline:**
   - `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`: **BUILD SUCCESSFUL** (`22` migrations `V1`–`V22`, `43` entities, `116` OpenAPI paths, `28` tags; `315 PASS, 0 FAIL, 1 SKIPPED` có sẵn).

---

## 2. Thiết kế chi tiết

### 2.1. Migration `V23__notification_outbox_and_types.sql` & Entity `Notification`

- **Migration `V23`:**
  - Cập nhật `CHECK` của `type` trên bảng `notification`:
    ```sql
    ALTER TABLE notification DROP CONSTRAINT notification_type_check;
    ALTER TABLE notification ADD CONSTRAINT notification_type_check
        CHECK (type IN ('ORDER_PLACED', 'ORDER_CONFIRMED', 'ORDER_CANCELLED', 'ORDER_STATUS', 'PAYMENT', 'NO_SHOW', 'REFUND', 'COMPLAINT', 'STORE_REVIEW', 'SETTLEMENT'));
    ```
  - Bổ sung cột mới trên `notification`:
    - `event_code varchar(64)`
    - `title varchar(255)`
    - `dedupe_key varchar(255)`
    - `attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0)`
    - `next_attempt_at timestamptz`
    - `last_error text`
  - Bổ sung index:
    - `CREATE UNIQUE INDEX uq_notification_dedupe_key ON notification (dedupe_key) WHERE dedupe_key IS NOT NULL;`
    - `CREATE INDEX idx_notification_outbox_due ON notification (status, next_attempt_at, created_at, id) WHERE status = 'PENDING';`
    - `CREATE INDEX idx_notification_recipient_created ON notification (recipient_id, created_at DESC, id);`
- **Entity & Enum:**
  - Cập nhật [`NotificationType.kt`](../../src/main/kotlin/com/foodrescue/backend/notification/NotificationType.kt) thêm `ORDER_PLACED`, `ORDER_STATUS`, `PAYMENT`, `STORE_REVIEW`.
  - Cập nhật [`Notification.kt`](../../src/main/kotlin/com/foodrescue/backend/notification/entity/Notification.kt) thêm 6 field mới (nullable hoặc có default ở cuối constructor để tương thích hoàn toàn với `FoodRescueApplicationTests` và các constructor call cũ).
  - Cập nhật [`NotificationRepository.kt`](../../src/main/kotlin/com/foodrescue/backend/notification/repository/NotificationRepository.kt) mở rộng `JpaSpecificationExecutor<Notification>` và các hàm:
    - `findForUpdate(id: UUID): Notification?` (`@Lock(PESSIMISTIC_WRITE)`)
    - `findByDedupeKey(dedupeKey: String): Notification?`
    - `findUnreadIdsForUpdate(recipientId: UUID): List<UUID>` hoặc lock danh sách theo `ORDER BY id ASC`
    - `countByRecipientIdAndReadAtIsNull(recipientId: UUID): Long`
- **Giữ tổng Hibernate entity là `43`**; **số migration tăng `22` → `23`**.

### 2.2. Ghi Outbox đồng bộ transaction (`NotificationOutboxService`) & điểm tích hợp

- Tạo [`NotificationOutboxService.kt`](../../src/main/kotlin/com/foodrescue/backend/notification/service/NotificationOutboxService.kt):
  - `@Transactional(propagation = Propagation.MANDATORY)` đảm bảo **bắt buộc** chạy bên trong transaction nghiệp vụ gốc:
    ```kotlin
    fun enqueue(
        recipient: Account,
        type: NotificationType,
        eventCode: String,
        title: String,
        payload: Map<String, Any?>,
        order: CustomerOrder? = null,
        dedupeKey: String? = null
    ): Notification
    ```
  - Nếu `dedupeKey != null` và `notifications.findByDedupeKey(dedupeKey)` đã tồn tại → trả về bản ghi cũ, không tạo trùng (chống trùng khi replay idempotency hoặc job chạy lại).
  - Tạo `Notification(..., status = NotificationStatus.PENDING, eventCode = eventCode, title = title, dedupeKey = dedupeKey, attemptCount = 0, nextAttemptAt = clock.instant())`.
  - Cung cấp thêm helper `enqueueSettlement(recipient, eventCode, title, payload, order = null, dedupeKey)` cho **T8.4** sử dụng sẵn trong cùng transaction đối soát/chi trả.

> **Lý do cập nhật tối thiểu các service của task trước (theo `.agents/CLAUDE.md` §1):**  
> Yêu cầu cốt lõi của T8.2 (`FR-21`, `AC-01`, `AC-02`) là ghi `notification` (outbox) **trong cùng transaction** với các sự kiện đã cài đặt ở T3.2–T8.1 để bảo đảm khi transaction gốc rollback thì không sinh thông báo. Không thay đổi logic nghiệp vụ, chữ ký API hay dữ liệu tiền/tồn kho của các task trước. Danh sách điểm chèn:
> 1. `OrderPlacementService.place` (`T5.2`): `ORDER_PLACED` / `ORDER_CREATED` → `customer` (`dedupeKey = "ORDER_CREATED:${order.id}:CUSTOMER"`).
> 2. `PaymentConfirmationService.confirm` & `.review` (`T6.3`, `T6.4`): `ORDER_CONFIRMED` / `PAYMENT_VERIFIED` → `customer` & `store.owner` (`dedupeKey` theo `payment.id` + vai trò); `PAYMENT` / `PAYMENT_REQUIRES_REVIEW` → `customer` (`dedupeKey = "PAYMENT_REVIEW:${payment.id}:CUSTOMER"`).
> 3. `AdminPaymentService.assign` (`T6.4`): `PAYMENT` / `PAYMENT_ASSIGNED` → `customer` (`dedupeKey = "PAYMENT_ASSIGNED:${payment.id}:CUSTOMER"`).
> 4. `OrderReleaser.release` (`T5.3`, dùng chung cho `OrderExpiryService`, hủy chưa thanh toán `T7.3`, `ReconciliationStore` `T6.4`, `BoxUnfitProcessor` & `StoreStatusService` chưa thanh toán `T7.5`): `ORDER_CANCELLED` (`ORDER_CANCELLED`) hoặc `ORDER_STATUS` (`ORDER_EXPIRED`) → `customer` (`dedupeKey = "${status.name}:${order.id}:CUSTOMER"`).
> 5. `PaidOrderCancellationExecutor.execute` (`T7.3`, dùng chung cho khách hủy có hoàn, cửa hàng hủy, admin duyệt hủy, `BoxUnfitProcessor` & `StoreStatusService` đã thanh toán `T7.5`): `ORDER_CANCELLED` / `ORDER_CANCELLED` → `customer` & `store.owner` (`dedupeKey = "ORDER_CANCELLED:${order.id}:CUSTOMER"` / `:STORE"`).
> 6. `OrderCancellationService.submitRequest` / `.supplement` / `.withdraw` (`T7.3`): `ORDER_STATUS` (`CANCELLATION_REQUESTED`, `CANCELLATION_SUPPLEMENTED`, `CANCELLATION_WITHDRAWN`) → `store.owner`.
> 7. `AdminCancellationService.decide` (`T7.3`): `REJECT` / `REQUEST_INFO` → `ORDER_STATUS` (`CANCELLATION_REJECTED`, `CANCELLATION_INFO_REQUESTED`) → `customer` (`APPROVE` đi qua `PaidOrderCancellationExecutor.execute`).
> 8. `PickupSessionService.startSession` & `.completeSession` (`T7.2`): `ORDER_STATUS` (`HANDOVER_STARTED`, `ORDER_COMPLETED`) → `customer`.
> 9. `PickupIncidentService.recordIncident` (`T7.2`): `ORDER_STATUS` (`HANDOVER_INCIDENT`) → `customer`.
> 10. `NoShowOverdueService.markOverdue` (`T7.4`): `ORDER_STATUS` / `ORDER_PENDING_NO_SHOW` → `customer` & `store.owner` (`dedupeKey = "PENDING_NO_SHOW:${order.id}:CUSTOMER"` / `:STORE"`).
> 11. `NoShowService.confirm` (`T7.4`): chuyển sang gọi `outbox.enqueue(...)` cho `NO_SHOW` / `NO_SHOW_CONFIRMED` → `customer` (`dedupeKey = "NO_SHOW_CONFIRMED:${orderId}"`), giữ nguyên gắn `NoShowCase.notification` và hoàn toàn tương thích `NoShowTests`.
> 12. `NoShowCompensationService.releaseEligible` (`T7.4`): `NO_SHOW` / `NO_SHOW_COMPENSATION_ELIGIBLE` → `store.owner` (`dedupeKey = "NO_SHOW_ELIGIBLE:${case.id}:STORE"`).
> 13. `ManualRefundService.approve` / `.confirm` / `.fail` (`T6.5`, `T8.1`): `REFUND` (`REFUND_APPROVED`, `REFUND_SUCCEEDED` theo `refund.id`, `REFUND_FAILED` theo `refund.id`) → `customer`; khi auto-close `Complaint` cũng phát `COMPLAINT` / `COMPLAINT_CLOSED` → `customer`.
> 14. `ComplaintService.submit` / `.supplement` (`T8.1`) & `BoxUnfitProcessor.process` hậu giao (`T7.5`): `COMPLAINT` (`COMPLAINT_SUBMITTED` → `customer` & `store.owner`; `COMPLAINT_SUPPLEMENTED` → `store.owner`).
> 15. `AdminComplaintService.requestInfo` / `.decide` / `.close` (`T8.1`): `COMPLAINT` (`COMPLAINT_INFO_REQUESTED`, `COMPLAINT_CLOSED` → `customer`; `COMPLAINT_DECIDED` → `customer` & `store.owner`).
> 16. `PartnerApplicationReviewService.decide` (`T3.2`): `STORE_REVIEW` / `PARTNER_APPLICATION_${decision.name}` → `applicant`.
> 17. `StoreChangeReviewService.decide` (`T3.3`): `STORE_REVIEW` / `STORE_CHANGE_${request.status.name}` → `store.owner`.
> 18. `StoreStatusService.change` (`T3.3`, `T7.5`): `STORE_REVIEW` / `STORE_${to.name}` → `store.owner`.
>
> **Cập nhật 1 dòng assertion trong `NoShowTests.kt` dòng 181:**  
> Trong `NoShowTests` (`AC-04 transaction rolls back when financial event insert fails`), dòng 181 trước đây viết `assertEquals(0, count("select count(*) from notification"))` khi chỉ có duy nhất T7.4 ghi bảng `notification`. Khi T8.2 kích hoạt outbox cho bước duyệt cửa hàng (`store()`), đặt đơn (`placeOrder()`) và thanh toán (`webhook`) ở `@BeforeEach setup()`, các thông báo trước khi gọi `confirm()` đã tồn tại hợp lệ. Sửa hẹp dòng này thành kiểm tra `count("select count(*) from notification where type='NO_SHOW'") == 0` (và giữ nguyên tổng số dòng `notification` trước/sau `confirm()` thất bại không đổi) để vẫn chứng minh rollback 100% mà không sai lệch nghiệp vụ.

### 2.3. Port `NotificationSender`, Cấu hình & Worker Outbox (`NotificationDispatchService` / `Job`)

- **Cấu hình [`NotificationProperties.kt`](../../src/main/kotlin/com/foodrescue/backend/notification/NotificationProperties.kt) (`@ConfigurationProperties("notifications")`):**
  - `dispatchJobEnabled: Boolean = true` (`notifications.dispatch-job-enabled`)
  - `dispatchScanMs: Long = 10000` (`notifications.dispatch-scan-ms`)
  - `batchSize: Int = 50`
  - `leaseSeconds: Long = 60`
  - `maxAttempts: Int = 3`
  - `retryDelaysSeconds: List<Long> = listOf(60L, 300L, 900L)`
  - `mailFrom: String = ""` (mặc định fallback sang `auth.mailFrom`)
  - `emailEnabled: Boolean = true`
  - Cập nhật `src/main/resources/application.yml` thêm khối `notifications:` với giá trị mặc định an toàn.
  - Cập nhật `src/test/kotlin/.../support/AuthTestFixtures.kt` (`TestAuthKeys.configure`): đặt `notifications.dispatch-job-enabled=false` (cùng mẫu với `orders.expiry-job-enabled=false`, `orders.no-show-job-enabled=false`) để test điều khiển thời điểm dispatch bằng `Clock`.
- **Port [`NotificationSender.kt`](../../src/main/kotlin/com/foodrescue/backend/notification/port/NotificationSender.kt):**
  - `NotificationDispatchCommand(notificationId, recipientId, recipientEmail, recipientDisplayName, orderId, type, eventCode, title, payloadJson, dedupeKey)`
  - `NotificationDeliveryOutcome { DELIVERED, SENT }`
  - `NotificationDeliveryResult(outcome: NotificationDeliveryOutcome, detail: String? = null)`
  - `interface NotificationSender { fun send(command: NotificationDispatchCommand): NotificationDeliveryResult }`
- **Production Adapter [`SmtpAndInAppNotificationSender.kt`](../../src/main/kotlin/com/foodrescue/backend/notification/port/SmtpAndInAppNotificationSender.kt):**
  - Thực hiện kênh email bằng `JavaMailSender` (với `SimpleMailMessage`, `from = properties.mailFrom.ifBlank { authProperties.mailFrom }`, `to = command.recipientEmail`, `subject = "[FoodRescue] ${command.title}"`, nội dung tóm tắt sạch không chứa token/secret/stacktrace) khi `properties.emailEnabled == true`.
  - Kênh in-app hoạt động qua bản ghi `Notification` đã lưu sẵn trong hộp thư của người dùng.
  - Trả về `NotificationDeliveryResult(NotificationDeliveryOutcome.DELIVERED)` khi gửi email + in-app thành công.
- **Test Double [`RecordingNotificationSender.kt`](../../src/test/kotlin/.../support/RecordingNotificationSender.kt):**
  - `@TestConfiguration` `@Bean @Primary` import trong `AuthTestFixtures` (`MailTestConfiguration` hoặc `NotificationTestConfiguration`), tự reset ở `@BeforeEach clearDatabase()`.
  - Hỗ trợ `fail = true`, `failForIds`, `failRemainingTimes`, hoặc cấu hình `outcome = SENT / DELIVERED` để test đầy đủ retry, backoff, chuyển `FAILED`, `SENT -> DELIVERED` khi đọc và test đồng thời.
- **Worker `NotificationDispatchStore` + `NotificationDispatchService` + `NotificationDispatchJob`:**
  - Tách TX bằng `NotificationDispatchStore` (mẫu `ReconciliationStore` của T6.4):
    1. `claimDue(now: Instant, batchSize: Int): List<NotificationDispatchCommand>` (`@Transactional`):
       - Native query với `FOR UPDATE SKIP LOCKED`:
         ```sql
         SELECT id FROM notification
         WHERE status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
         ORDER BY created_at ASC, id ASC
         LIMIT :n
         FOR UPDATE SKIP LOCKED
         ```
       - Cập nhật `nextAttemptAt = now.plusSeconds(properties.leaseSeconds)` trước khi trả danh sách command (cô lập giao dịch DB; không giữ lock khi gọi `NotificationSender`).
    2. `NotificationDispatchService.dispatchDue(now: Instant, batchSize: Int = properties.batchSize): NotificationDispatchReport`:
       - Gọi `store.claimDue(now, batchSize)` → với từng item gọi `sender.send(cmd)` ngoài transaction DB → bắt `Throwable`/`RuntimeException`, gọi `store.markSuccess(...)` hoặc `store.markFailure(...)`.
    3. `markSuccess(id, now, result)` (`@Transactional`):
       - Khóa dòng `notifications.findForUpdate(id)`, nếu `status != PENDING` thì bỏ qua.
       - Tăng `attemptCount += 1`, `lastError = null`, `sentAt = sentAt ?: now`.
       - Nếu `result.outcome == DELIVERED` → `status = DELIVERED`, `deliveredAt = deliveredAt ?: now`, `nextAttemptAt = null`; nếu `SENT` → `status = SENT`, `nextAttemptAt = null`.
    4. `markFailure(id, now, message)` (`@Transactional`):
       - Khóa dòng `notifications.findForUpdate(id)`, nếu `status != PENDING` thì bỏ qua.
       - Tăng `attemptCount += 1`, ghi `lastError = message.take(500)`.
       - Nếu `attemptCount >= properties.maxAttempts` → `status = FAILED`, `nextAttemptAt = null`.
       - Ngược lại → giữ `status = PENDING`, `nextAttemptAt = now.plusSeconds(retryDelayForAttempt(attemptCount))`.
  - `NotificationDispatchJob` (`@Component`, `@ConditionalOnProperty(name = ["notifications.dispatch-job-enabled"], havingValue = "true", matchIfMissing = true)`):
    - `@Scheduled(fixedDelayString = "\${notifications.dispatch-scan-ms:10000}", initialDelayString = "\${notifications.dispatch-scan-ms:10000}")`: vòng `while` rút hết backlog với `clock.instant()`.

### 2.4. API Thông báo của tôi (`NotificationService` & `NotificationController`)

- **`NotificationService`:**
  - `myList(accountId: UUID, type: NotificationType?, status: NotificationStatus?, unreadOnly: Boolean?, page: Int, size: Int): PageResponse<NotificationView>` (`@Transactional(readOnly = true)`): dùng `Specification` + `PageQuery().pageable(page, size, "createdAt")`.
  - `unreadCount(accountId: UUID): UnreadNotificationCountView` (`@Transactional(readOnly = true)`).
  - `myDetail(accountId: UUID, notificationId: UUID): NotificationView` (`@Transactional(readOnly = true)`): kiểm tra `n.recipient.id == accountId` (else `404 NOT_FOUND`).
  - `markRead(accountId: UUID, notificationId: UUID): NotificationView` (`@Transactional`):
    - Khóa `notifications.findForUpdate(notificationId)` + kiểm tra `n.recipient.id == accountId` (else `404 NOT_FOUND`).
    - `val now = clock.instant()`
    - Nếu `n.readAt == null`:
      - `n.readAt = now`
      - Nếu `n.status == NotificationStatus.PENDING || n.status == NotificationStatus.SENT`: `n.sentAt = n.sentAt ?: now`, `n.deliveredAt = n.deliveredAt ?: now`, `n.status = NotificationStatus.DELIVERED`.
      - Nếu `n.type == NotificationType.NO_SHOW`: gọi `noShowCompensationService.recordNotificationReceived(n.id!!, now)` trong cùng transaction (`NoShowCase` được khóa & cập nhật `notificationReceivedAt` + `disputeDeadline` lần đầu tiên, idempotent).
    - Trả `NotificationView` (kèm `payload` đã parse JSON hoặc `JsonNode`/object và các timestamp).
  - `markAllRead(accountId: UUID): NotificationReadAllView` (`@Transactional`):
    - Truy vấn & khóa theo thứ tự `id ASC` toàn bộ thông báo chưa đọc của `accountId` (`readAt IS NULL`).
    - Áp dụng cùng quy tắc cập nhật `readAt`, `DELIVERED` và gọi `noShowCompensationService.recordNotificationReceived` cho mọi thông báo `NO_SHOW` trong danh sách.
    - Trả số lượng bản ghi vừa được đánh dấu đã đọc (`updatedCount`).
- **Thứ tự khóa bi quan (tránh deadlock):**
  - `NotificationService.markRead/markAllRead`: `Notification (ORDER BY id ASC)` → `NoShowCase (findForUpdate)`.
  - `NoShowCompensationService.releaseEligible`: chỉ khóa `NoShowCase` (`FOR UPDATE SKIP LOCKED`), chỉ đọc `Complaint`, không khóa `Notification`.
  - `NotificationDispatchStore`: chỉ khóa `Notification` ở pha TX1 và TX2, không chạm `NoShowCase`.
  → Tuyệt đối đơn hướng, không thể tạo chu trình deadlock.

---

## 3. API Contract (`docs/api/openapi.yaml`)

Thêm **5** đường dẫn mới (nâng từ `116` → **`121`** paths) và **1** tag mới (`"Notifications"`, nâng từ `28` → **`29`** tags):

| Method / Path | Quyền & Tham số | Response | Tag |
|---|---|---|---|
| `GET /api/v1/notifications` | Đã đăng nhập; query `type`, `status`, `unreadOnly`, `page`, `size` | `200 PageResponse<NotificationView>`; `400`, `401` | `Notifications` |
| `GET /api/v1/notifications/unread-count` | Đã đăng nhập | `200 UnreadNotificationCountView`; `401` | `Notifications` |
| `GET /api/v1/notifications/{notificationId}` | Đã đăng nhập (chủ thông báo) | `200 NotificationView`; `400`, `401`, `404` | `Notifications` |
| `POST /api/v1/notifications/{notificationId}/read` | Đã đăng nhập (chủ thông báo) | `200 NotificationView`; `400`, `401`, `404` | `Notifications` |
| `POST /api/v1/notifications/read-all` | Đã đăng nhập | `200 NotificationReadAllView`; `401` | `Notifications` |

*(Ghi chú: toàn bộ schema `NotificationView`, `UnreadNotificationCountView`, `NotificationReadAllView`, enum `NotificationType` mở rộng và ví dụ/mã lỗi sẽ khai báo đầy đủ trong `docs/api/openapi.yaml`.)*

---

## 4. Migration và Database Schema

- File migration mới: `src/main/resources/db/migration/V23__notification_outbox_and_types.sql`
  - Mở rộng `notification_type_check` thêm `'ORDER_PLACED'`, `'ORDER_STATUS'`, `'PAYMENT'`, `'STORE_REVIEW'`.
  - Thêm cột `event_code`, `title`, `dedupe_key`, `attempt_count`, `next_attempt_at`, `last_error` cùng index `uq_notification_dedupe_key`, `idx_notification_outbox_due`, `idx_notification_recipient_created`.
- Không sửa bất kỳ file migration nào từ `V1`–`V22`.
- Số lượng Hibernate entity giữ nguyên (`43`); số migration tăng `22` → `23`.

---

## 5. File Layout dự kiến

Đường dẫn Kotlin tương đối dưới `src/main/kotlin/com/foodrescue/backend/`:

| File | Loại | Trách nhiệm |
|---|---|---|
| `src/main/resources/db/migration/V23__notification_outbox_and_types.sql` | NEW | Mở rộng type & cột outbox/retry/dedupe cho bảng `notification` |
| `src/main/resources/application.yml` | MODIFIED | Khai báo mặc định nhóm cấu hình `notifications.*` |
| `notification/NotificationType.kt` | MODIFIED | Thêm `ORDER_PLACED`, `ORDER_STATUS`, `PAYMENT`, `STORE_REVIEW` |
| `notification/NotificationProperties.kt` | NEW | `@ConfigurationProperties("notifications")` |
| `notification/entity/Notification.kt` | MODIFIED | Map 6 cột mới từ `V23` với default tương thích |
| `notification/repository/NotificationRepository.kt` | MODIFIED | Thêm `JpaSpecificationExecutor`, `findForUpdate`, `findByDedupeKey`, unread queries |
| `notification/port/NotificationSender.kt` | NEW | Port gửi thông báo & model command/outcome |
| `notification/port/SmtpAndInAppNotificationSender.kt` | NEW | Adapter gửi email qua `JavaMailSender` + kênh in-app |
| `notification/dto/NotificationView.kt` | NEW | DTO hiển thị thông báo của người dùng |
| `notification/dto/UnreadNotificationCountView.kt` | NEW | DTO đếm thông báo chưa đọc |
| `notification/dto/NotificationReadAllView.kt` | NEW | DTO kết quả đánh dấu đã đọc tất cả |
| `notification/dto/NotificationDispatchReport.kt` | NEW | DTO báo cáo một lượt dispatch outbox |
| `notification/service/NotificationOutboxService.kt` | NEW | Helper `Propagation.MANDATORY` ghi outbox trong cùng transaction + `dedupeKey` + hook T8.4 |
| `notification/service/NotificationDispatchStore.kt` | NEW | Claim `FOR UPDATE SKIP LOCKED` + cập nhật kết quả/retry backoff/FAILED |
| `notification/service/NotificationDispatchService.kt` | NEW | Điều phối gửi ngoài transaction qua `NotificationSender` |
| `notification/service/NotificationDispatchJob.kt` | NEW | `@Scheduled` job nền với `@ConditionalOnProperty` |
| `notification/service/NotificationViews.kt` | NEW | Map `Notification` → `NotificationView` |
| `notification/service/NotificationService.kt` | NEW | API hộp thư của tôi (`myList`, `unreadCount`, `myDetail`, `markRead`, `markAllRead` + T7.4 hook) |
| `notification/controller/NotificationController.kt` | NEW | Endpoints `/api/v1/notifications/**` |
| `order/service/OrderPlacementService.kt`, `OrderReleaser.kt`, `PaidOrderCancellationExecutor.kt`, `OrderCancellationService.kt`, `AdminCancellationService.kt` | MODIFIED | Gọi `NotificationOutboxService` trong transaction đặt/thanh toán/hủy/hết hạn |
| `payment/service/PaymentConfirmationService.kt`, `AdminPaymentService.kt`, `ManualRefundService.kt` | MODIFIED | Gọi `NotificationOutboxService` trong transaction thanh toán/gán/hoàn tiền/auto-close |
| `pickup/service/PickupSessionService.kt`, `PickupIncidentService.kt`, `NoShowOverdueService.kt`, `NoShowService.kt`, `NoShowCompensationService.kt` | MODIFIED | Gọi `NotificationOutboxService` cho bàn giao/quá hạn/no-show/giải tỏa `ELIGIBLE` |
| `complaint/service/ComplaintService.kt`, `AdminComplaintService.kt`, `surprisebox/service/BoxUnfitProcessor.kt` | MODIFIED | Gọi `NotificationOutboxService` cho toàn bộ luồng khiếu nại |
| `store/service/PartnerApplicationReviewService.kt`, `StoreChangeReviewService.kt`, `StoreStatusService.kt` | MODIFIED | Gọi `NotificationOutboxService` cho xét duyệt đối tác, đổi thông tin & khóa/mở cửa hàng |
| `docs/api/openapi.yaml` | MODIFIED | Thêm tag `Notifications` (`28` → `29`) và 5 paths (`116` → `121`) |
| `src/test/kotlin/.../support/RecordingNotificationSender.kt` | NEW | Test double giả lập gửi/lỗi `NotificationSender` |
| `src/test/kotlin/.../support/AuthTestFixtures.kt` | MODIFIED | Import `RecordingNotificationSender`, tắt `notifications.dispatch-job-enabled` và reset ở `@BeforeEach` |
| `src/test/kotlin/.../NotificationTests.kt` | NEW | Bộ integration test TDD bao phủ `AC-01`..`AC-08` |
| `src/test/kotlin/.../NoShowTests.kt` | MODIFIED | Thu hẹp `notification` assertion dòng 181 theo `type='NO_SHOW'` + bảo toàn count sau rollback |
| `src/test/kotlin/.../AuthMigrationTests.kt`, `FoodRescueApplicationTests.kt`, `SwaggerUiTests.kt` | MODIFIED | Cập nhật baseline `22` → `23` migrations, `116` → `121` paths, `28` → `29` tags |
| `docs/specs/t8-2-notifications/03-implementation-control.txt` | NEW | Báo cáo kiểm chứng cuối task |
| `docs/specs/roadmap-3-9.md`, `.gitignore` | MODIFIED | Đóng `[x] T8.2` và whitelist `!docs/specs/t8-2-notifications/` |

---

## 6. Task Breakdown

- [x] **T8.2-01:** Tạo migration `V23__notification_outbox_and_types.sql`, cập nhật `NotificationType`, `Notification`, `NotificationRepository`, `NotificationProperties`, `application.yml`.
- [x] **T8.2-02 (TDD):** Tạo `RecordingNotificationSender`, cập nhật `AuthTestFixtures`, viết trước bộ integration test `NotificationTests.kt` bao phủ `AC-01`..`AC-07` (xác nhận bước chạy đầu fail do thiếu service/endpoint).
- [x] **T8.2-03:** Cài đặt `NotificationSender`, `SmtpAndInAppNotificationSender`, `NotificationOutboxService`, `NotificationDispatchStore`, `NotificationDispatchService`, `NotificationDispatchJob`, DTOs, `NotificationViews`, `NotificationService`, `NotificationController`.
- [x] **T8.2-04:** Tích hợp `NotificationOutboxService` vào các service sự kiện (`order`, `payment`, `pickup`, `complaint`, `surprisebox`, `store`) và cập nhật kiểm tra rollback ở `NoShowTests.kt:181`.
- [x] **T8.2-05:** Cập nhật `docs/api/openapi.yaml` (`121` paths / `29` tags) và kỳ vọng ở `AuthMigrationTests`, `FoodRescueApplicationTests`, `SwaggerUiTests`.
- [x] **T8.2-06:** Chạy targeted tests (`NotificationTests`, `NoShowTests`, `ComplaintTests`, `ManualRefundTests`, `OrderCancellationTests`, `SwaggerUiTests`, `ApiContractTests`, `FoodRescueApplicationTests`), sau đó chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`.
- [x] **T8.2-07:** Đọc trực tiếp `build/reports/tests/test/index.html` và XML từng suite, hoàn tất `03-implementation-control.txt`, đánh dấu `[x] T8.2` trong `roadmap-3-9.md` và cập nhật `.gitignore`.

---

## 7. Truy vết AC → Task → Test

| AC | Mô tả ngắn | Task | Test Case dự kiến (`NotificationTests` & liên quan) |
|---|---|---|---|
| **AC-01** | Outbox trong transaction đặt hàng, thanh toán, hủy, hết hạn, bàn giao; rollback không lưu | T8.2-01, 03, 04 | `AC-01 order payment cancellation expiry handover enqueue outbox and rollback cleanly` |
| **AC-02** | Outbox quá hạn nhận, no-show, eligible, hoàn tiền, khiếu nại, duyệt/khóa cửa hàng + chống trùng `dedupeKey` | T8.2-01, 03, 04 | `AC-02 noshow refund complaint store lifecycle enqueue without duplicates on replay` |
| **AC-03** | Worker gửi qua `NotificationSender` (`PENDING -> DELIVERED`/`SENT`) | T8.2-03 | `AC-03 dispatch worker delivers pending notifications via NotificationSender` |
| **AC-04** | Gửi lỗi không làm hỏng nghiệp vụ; retry backoff theo `Clock`; quá `maxAttempts` → `FAILED` | T8.2-03 | `AC-04 sender failure keeps business intact retries with backoff then marks FAILED` |
| **AC-05** | Hai luồng worker song song không gửi trùng cùng thông báo (`SKIP LOCKED` + lease) | T8.2-03 | `AC-05 concurrent dispatch workers deliver each notification exactly once` |
| **AC-06** | API hộp thư của tôi: list lọc/phân trang, unread-count, detail, IDOR `404`/`401` | T8.2-03 | `AC-06 inbox endpoints filter paginate enforce ownership and return 401/404` |
| **AC-07** | Đọc thông báo No-show (`read` / `read-all`) mở đồng hồ 48h `NoShowCase` → `ELIGIBLE` | T8.2-03, 04 | `AC-07 reading no-show notification starts 48h dispute window and unlocks ELIGIBLE` |
| **AC-08** | Flyway `V23`, OpenAPI `121` paths / `29` tags, không vỡ test cũ, full build xanh | T8.2-05..07 | `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests`, `NoShowTests` |

---

## 8. Kế hoạch kiểm thử TDD

- Viết `NotificationTests.kt` trước khi cài đặt controller/service outbox/worker, xác nhận đỏ ở endpoint `/api/v1/notifications/**` (`404`) và chưa có bản ghi outbox.
- Dùng `Clock` cố định (kế thừa pattern `PickupTestClock` hoặc `MutableClock`) để test chính xác lịch retry backoff `nextAttemptAt`, hết lease, và mốc `notificationReceivedAt + 48h` kích hoạt `NoShowCompensationService.releaseEligible`.
- Dùng `RecordingNotificationSender` để kiểm chứng payload/số lần gọi chính xác ở các test thành công, test lỗi SMTP (`fail = true`), test `SENT -> DELIVERED` khi đọc, và test 2 luồng `dispatchDue` song song qua `CountDownLatch`.
- Kết thúc task chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain` và đọc trực tiếp `build/reports/tests/test/index.html` cùng XML các suite.

---

## 9. Rủi ro và phương án kiểm soát / Rollback

1. **Rủi ro giữ kết nối/khóa DB khi gọi `JavaMailSender` (`.agents/CLAUDE.md` §5):**  
   *Kiểm soát:* Tách pha `claimDue` (TX1 ngắn với `FOR UPDATE SKIP LOCKED` + `leaseSeconds`), gọi `NotificationSender` **ngoài transaction DB**, rồi ghi kết quả trong pha `markSuccess`/`markFailure` (TX2 ngắn).
2. **Rủi ro vòng khóa chéo giữa `NotificationService.markRead` và `NoShowCompensationService`:**  
   *Kiểm soát:* `markRead`/`markAllRead` khóa một chiều `Notification (id ASC) -> NoShowCase`; `releaseEligible` chỉ khóa `NoShowCase` và `NoShowService.confirm` chỉ insert mới, không có khóa ngược.
3. **Rủi ro phá vỡ test cũ của T7.4 (`NoShowTests.kt:181`):**  
   *Kiểm soát:* Đã phát hiện trước ở bước khảo sát và nêu rõ lý do thu hẹp assertion tại `NoShowTests.kt:181` về `where type='NO_SHOW'` (+ tổng count trước/sau rollback không đổi).
4. **Rollback:**  
   Trước khi đóng task, có thể hoàn tác sạch các thay đổi tại gói `notification/`, các lời gọi `outbox.enqueue` đã thêm và `V23` trên DB mới mà không ảnh hưởng dữ liệu/bất biến của `V1`–`V22`.

## 10. Làm rõ khi triển khai (2026-11-24)
- TDD đỏ: 11/11 tests fail vì thiếu endpoint/worker/outbox. Sau cài đặt, 11/11 PASS trước khi mở rộng các ca kiểm chứng lease và tích hợp.
- Enqueue dedupe dùng INSERT ON CONFLICT DO NOTHING với partial unique index, thay vì chỉ find-then-save; tránh race cùng khóa làm rollback transaction nghiệp vụ. Flush entity cha trước native INSERT, không thêm entity/migration ngoài V23.
- Worker claim từng thông báo ngay trước khi gửi trong giới hạn batchSize; tránh 50 email chờ tuần tự làm hết lease các phần tử cuối. Giữ mô hình TX1 → I/O ngoài TX → TX2. Propagation.NOT_SUPPORTED bảo đảm gửi ngoài TX ngay cả khi caller có transaction.
- Command mang claimUntil (nội bộ) để TX2 không ghi đè kết quả của claim mới sau khi lease cũ hết hạn. lastError chỉ lưu lỗi tổng quát, không lưu message SMTP có thể chứa dữ liệu nhạy cảm.
- Giới hạn SMTP: lease + dedupe chống claim trùng trong điều kiện xử lý trong lease. SMTP không có primitive idempotency nên crash sau khi SMTP nhận mail nhưng trước TX2, hoặc gửi kéo dài quá lease, vẫn có thể gửi lại. Không tuyên bố exactly-once qua crash; dedupeKey được chuyển qua port để adapter tương lai có thể dùng provider idempotency.
- Không thay đổi phạm vi kênh gửi, tiền/tồn kho, schema/API của task trước; chỉ thêm lời gọi outbox trong các transaction đã duyệt.

- Targeted mở rộng phát hiện raw EntityManager.flush() trong outbox đẩy lỗi constraint của nghiệp vụ ra HTTP 500. Đổi sang NotificationRepository.flush() để giữ exception translation / HTTP 409 của các API hiện hữu; không đổi test hủy đơn cũ. Test constraint outbox dùng NOT VALID để bỏ qua các bản ghi setup và chỉ chặn insert mới.

- Targeted cuối: 101 PASS / 0 FAIL / 0 ERROR / 0 SKIPPED (5m24s), gồm NotificationTests 19 và SmtpNotificationSenderTests 2. Full build: BUILD SUCCESSFUL in 11m17s, 336 PASS / 0 FAIL / 0 ERROR / 1 SKIPPED có sẵn.
