# Technical Plan: T8.3 — Hoa hồng, Quyền lợi Cửa hàng & Doanh thu

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A đã được người dùng duyệt `Q1`–`Q5` ngày 2026-11-27.  
**Status:** Chờ duyệt Gate B  
**Updated:** 2026-11-27  
**Scope:** T8.3 (`UC-47`, `UC-48`, `BR-46..52`, `NFR-17..20`: hạch toán sổ cái `PLATFORM_COMMISSION` + `STORE_PAYABLE` cho đơn `COMPLETED`, hạch toán riêng `NO_SHOW_COMPENSATION` hoa hồng `0` và theo dõi khóa 48h/tranh chấp, hạch toán `ADJUSTMENT` gắn `original_entry_id`, đảm bảo cân bằng `Σ phân bổ = tiền thu − tiền hoàn`, idempotency `business_key`, và API Store Owner/Admin).  
**Phụ thuộc:** T8.1 (`[x]`), T8.2 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Quyết định đã duyệt tại Gate A và Baseline

1. **Các quyết định Gate A đã chốt (`Q1`–`Q5`):**
   - **Q1 (Làm tròn `NFR-17` & Khóa chống trùng `business_key` trên `ledger_entry` qua `V24`):**
     - Mọi phép tính theo tỷ lệ dùng `MoneyRounding.applyRate(amount, rate)` (`amount × rate`, `RoundingMode.HALF_UP`, nguyên đồng `VND` scale `0`); với đơn `COMPLETED`, `storePayable = order.totalAmount.subtract(commission)` để bảo đảm `commission + storePayable == order.totalAmount`.
     - Tạo migration `V24__ledger_entry_business_key.sql` (giữ nguyên `43` entities, không sửa `V1`–`V23`): thêm cột `business_key varchar(255)` trên `ledger_entry`, partial unique index `uq_ledger_entry_business_key ON ledger_entry(business_key) WHERE business_key IS NOT NULL` (tránh trigger `V3 immutable_ledger_entry` khi nâng cấp bảng bất biến) và index truy vấn theo cửa hàng `idx_ledger_entry_store_occurred`. Cập nhật `PaymentConfirmationService` truyền `PAYMENT_RECEIPT:{paymentId}` và `ManualRefundService` truyền `CUSTOMER_REFUND:{refundId}`.
   - **Q2 (Hạch toán đồng bộ trong transaction + Job rà soát idempotent):**
     - Ghi bút toán sổ cái ngay trong cùng transaction khi `PickupSessionService.completeSession` (`ORDER_COMPLETED`) và `NoShowService.confirm` (`NO_SHOW_CONFIRMED`).
     - Đồng thời cung cấp `LedgerPostingService.postPendingEvents(batchSize)` (`FOR UPDATE SKIP LOCKED` + khóa `CustomerOrder` + `business_key`) kèm `LedgerPostingJob` (`@Scheduled` cấu hình qua `finance.ledger-job-enabled`, tắt mặc định trong `AuthTestFixtures`) và endpoint Admin `/reconcile`; chạy lại nhiều lần không tạo trùng.
   - **Q3 (`NO_SHOW_COMPENSATION` và cơ chế giữ 48h):**
     - Ghi `LedgerEntry(NO_SHOW_COMPENSATION, +retainedAmount)` (hoa hồng `= 0`) ngay khi `NO_SHOW_CONFIRMED` (nếu `retainedAmount > 0`), giúp tổng phân bổ khớp ngay `collected - refunded`.
     - Trong khi `NoShowCase.compensationStatus IN (HELD, DISPUTED)` (chưa qua 48h hoặc đang tranh chấp), khoản này thuộc nhóm `heldAmount`, chưa đủ điều kiện đối soát `T8.4`. Chỉ khi `NoShowCase.compensationStatus == ELIGIBLE` mới chuyển sang `eligiblePayableAmount` (`UC-48`). Nếu `FORFEITED` (lỗi cửa hàng), sinh `LedgerEntry(ADJUSTMENT, -retainedAmount)` trỏ `original_entry_id` về `NO_SHOW_COMPENSATION`.
   - **Q4 (Thời điểm & quy tắc `ADJUSTMENT` từ khiếu nại/hoàn tiền):**
     - Khi quyết định khiếu nại không phát sinh `RefundRequest` (`approvedRefundAmount == 0`, đóng `CLOSED` ngay tại `AdminComplaintService.decide`): ghi `ADJUSTMENT` ngay trong transaction `decide`.
     - Khi quyết định có hoàn tiền (`approvedRefundAmount > 0`, `Complaint` ở `DECIDED` chờ chuyển khoản T6.5): giữ nguyên ở `heldAmount`, chỉ ghi `ADJUSTMENT` khi `ManualRefundService.confirm` hoàn tất chuyển khoản cuối cùng (đóng `Complaint` sang `CLOSED`, cùng transaction với `LedgerEntry(CUSTOMER_REFUND)`), bảo đảm tại **mọi thời điểm** tổng phân bổ khớp `collected_verified − refunded_verified`.
     - Quy ước xác định giá trị `ADJUSTMENT` trên đơn `COMPLETED`:
       - Nếu Admin chỉ định `storeAdjustmentAmount != 0` hoặc `commissionAdjustmentAmount != 0`: ghi đúng số tiền Admin đã chốt lên `STORE_PAYABLE` / `PLATFORM_COMMISSION`.
       - Nếu Admin để cả hai `= 0` nhưng `approvedRefundAmount > 0`:
         - `responsibility == PLATFORM`: ghi `commAdj = -approvedRefundAmount`, `storeAdj = 0` (nền tảng chịu khoản hoàn, cửa hàng không bị trừ).
         - `responsibility in [STORE, SHARED]`: tự chia theo tỷ lệ hoa hồng snapshot (`commAdj = -MoneyRounding.applyRate(refund, order.commissionRate)`, `storeAdj = -(refund + commAdj)`).
       - Với `NO_SHOW` bị `FORFEITED`: tự động đảo `-retainedAmount` trên `NO_SHOW_COMPENSATION` (và cộng thêm `storeAdjustmentAmount` nếu Admin có nhập riêng).
   - **Q5 (Bộ 5 API & Quyền truy cập):**
     - Store Owner (kiểm tra `StoreAuthorizationService.requirePermission(accountId, storeId, "STORE_PROFILE_MANAGE")` — `Store Staff` bị chặn `403 ACCESS_DENIED`, chỉ `ACTIVE` & `SUSPENDED` có context theo rule T3.2):
       - `GET /api/v1/stores/{storeId}/finance/revenue` (`UC-47`)
       - `GET /api/v1/stores/{storeId}/finance/entitlements` (`UC-48`)
       - `GET /api/v1/stores/{storeId}/finance/orders/{orderId}/ledger`
     - Admin (`@PreAuthorize("hasRole('ADMIN')")`):
       - `GET /api/v1/admin/finance/orders/{orderId}/ledger`
       - `POST /api/v1/admin/finance/ledger/reconcile`
2. **Baseline:**
   - `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`: **BUILD SUCCESSFUL** (`23` migrations `V1`–`V23`, `43` Hibernate entities, `121` OpenAPI paths, `29` tags; `58` suites, **336 PASS / 0 FAIL / 0 ERROR / 1 SKIPPED**).

---

## 2. Thiết kế chi tiết

### 2.1. Migration `V24__ledger_entry_business_key.sql` & Entity/Repository `LedgerEntry`

- **Migration `V24`:**
  ```sql
  -- T8.3: Idempotency business_key on append-only ledger_entry & store query index (keep V3 trigger untouched).
  ALTER TABLE ledger_entry
      ADD COLUMN business_key varchar(255);

  CREATE UNIQUE INDEX uq_ledger_entry_business_key
      ON ledger_entry (business_key)
      WHERE business_key IS NOT NULL;

  CREATE INDEX idx_ledger_entry_store_occurred
      ON ledger_entry (store_id, occurred_at DESC, id);
  ```
  *Lý do không chạy `UPDATE` backfill:* Bảng `ledger_entry` có trigger `V3 immutable_ledger_entry` (`BEFORE UPDATE OR DELETE`). Partial unique index `WHERE business_key IS NOT NULL` không động vào trigger, vẫn bảo đảm 100% chống trùng cho mọi bút toán mới từ T8.3 trở đi và mọi `PAYMENT_RECEIPT`/`CUSTOMER_REFUND` mới.
- **Entity [`LedgerEntry.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/entity/LedgerEntry.kt):**
  - Thêm field cuối constructor:
    ```kotlin
    @field:Column(name = "business_key", nullable = true, length = 255)
    val businessKey: String? = null
    ```
  - Tương thích ngược hoàn toàn với `FoodRescueApplicationTests` reflection (`parameter.type.isMarkedNullable -> null` hoặc truyền explicit trong service).
- **Repository [`LedgerEntryRepository.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/repository/LedgerEntryRepository.kt) & [`FinancialEventRepository.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/repository/FinancialEventRepository.kt):**
  - `LedgerEntryRepository`:
    - `existsByBusinessKey(businessKey: String): Boolean`
    - `findByBusinessKey(businessKey: String): LedgerEntry?`
    - `findAllByEventOrderIdOrderByOccurredAtAscIdAsc(orderId: UUID): List<LedgerEntry>`
    - `findAllByEventOrderIdAndTypeOrderByOccurredAtAscIdAsc(orderId: UUID, type: LedgerEntryType): List<LedgerEntry>`
  - `FinancialEventRepository`:
    - `findByBusinessKey(businessKey: String): FinancialEvent?`
    - `findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId: UUID): List<FinancialEvent>`
    - `findFirstByOrderIdAndType(orderId: UUID, type: FinancialEventType): FinancialEvent?`

### 2.2. Dịch vụ Hạch toán & Job Idempotent (`LedgerPostingService`, `LedgerPostingJob`)

- **Cấu hình [`FinanceLedgerProperties.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/FinanceLedgerProperties.kt) (`@ConfigurationProperties("finance")`) & `application.yml`:**
  - `ledgerJobEnabled: Boolean = true` (`finance.ledger-job-enabled`)
  - `ledgerScanMs: Long = 30000` (`finance.ledger-scan-ms`)
  - `ledgerBatchSize: Int = 100`
  - Thêm `finance.ledger-job-enabled=false` vào `TestAuthKeys.configure` trong [`AuthTestFixtures.kt`](../../src/test/kotlin/com/foodrescue/backend/support/AuthTestFixtures.kt) theo đúng pattern các job khác (`orders.expiry-job-enabled`, `notifications.dispatch-job-enabled`).

- **[`LedgerPostingService.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/service/LedgerPostingService.kt):**
  - Hàm nội bộ chèn an toàn theo `businessKey` (`@Transactional(propagation = Propagation.MANDATORY)`):
    ```kotlin
    private fun insertIfAbsent(
        event: FinancialEvent,
        store: Store,
        type: LedgerEntryType,
        amount: BigDecimal,
        currency: String,
        originalEntry: LedgerEntry?,
        occurredAt: Instant,
        businessKey: String
    ): LedgerEntry?
    ```
    Dùng **atomic native `INSERT INTO ledger_entry (...) VALUES (...) ON CONFLICT (business_key) WHERE business_key IS NOT NULL DO NOTHING`** (flush `FinancialEvent` trước nếu cần, sau đó trả lại `true/false` hoặc nạp entity khi cần `originalEntry`). Cơ chế `ON CONFLICT DO NOTHING` (đã chứng minh ổn định ở `NotificationOutboxService` T8.2) ngăn hoàn toàn race condition và không bao giờ làm vỡ transaction cha nếu hai luồng cùng cố ghi cùng `business_key`!
  - **`postOrderCompleted(order: CustomerOrder, completedEvent: FinancialEvent)` (`Propagation.MANDATORY`):**
    - Kiểm tra `order.status == OrderStatus.COMPLETED`.
    - `val commission = MoneyRounding.applyRate(order.totalAmount, order.commissionRate)`
    - `val storePayable = order.totalAmount.subtract(commission).setScale(0)`
    - Ghi `PLATFORM_COMMISSION` (`amount = commission`, `businessKey = "ORDER_COMPLETED:COMMISSION:${order.id}"`).
    - Ghi `STORE_PAYABLE` (`amount = storePayable`, `businessKey = "ORDER_COMPLETED:STORE_PAYABLE:${order.id}"`).
  - **`postNoShowConfirmed(order: CustomerOrder, noShowCase: NoShowCase, noShowEvent: FinancialEvent)` (`Propagation.MANDATORY`):**
    - Nếu `noShowCase.retainedAmount.signum() > 0`:
      - Ghi `NO_SHOW_COMPENSATION` (`amount = noShowCase.retainedAmount.setScale(0)`, `businessKey = "NO_SHOW_CONFIRMED:COMPENSATION:${noShowCase.id}"`).
    - Tuyệt đối không ghi `PLATFORM_COMMISSION` (`BR-48`, `BR-51`).
  - **`postComplaintResolvedIfSettled(complaint: Complaint, resolvedEvent: FinancialEvent, occurredAt: Instant)` (`Propagation.MANDATORY`):**
    - Nếu `complaint.status != ComplaintStatus.CLOSED` hoặc `complaint.outcome == ComplaintOutcome.REJECTED` → bỏ qua (với `DECIDED`, chờ đến khi `ManualRefundService.confirm` hoàn tất chuyển khoản cuối cùng và chuyển sang `CLOSED`).
    - Khóa/đọc `CustomerOrder` (`orders.findForUpdate(complaint.order.id!!)`).
    - **Nhánh 1 — Đơn có `NoShowCase` và `noShowCase.compensationStatus == CompensationStatus.FORFEITED`:**
      - Bảo đảm `postNoShowConfirmed` đã có `NO_SHOW_COMPENSATION` (nếu `retainedAmount > 0`).
      - Tìm `noShowComp = ledger.findByBusinessKey("NO_SHOW_CONFIRMED:COMPENSATION:${noShowCase.id}")`.
      - Nếu `noShowComp != null` → ghi `ADJUSTMENT` (`amount = noShowComp.amount.negate()`, `originalEntry = noShowComp`, `businessKey = "COMPLAINT_RESOLVED:NO_SHOW_FORFEIT:${complaint.id}"`).
      - Nếu `complaint.storeAdjustmentAmount != null && complaint.storeAdjustmentAmount!!.signum() != 0` và chưa xử lý ở phần hoàn bù → ghi thêm nếu có `noShowComp`.
    - **Nhánh 2 — Đơn `COMPLETED` (khiếu nại hậu giao):**
      - Bảo đảm `ORDER_COMPLETED` đã hạch toán `STORE_PAYABLE` (`storeEntry`) và `PLATFORM_COMMISSION` (`commEntry`).
      - Tính cặp điều chỉnh `(storeAdj, commAdj)`:
        - `val explicitStore = complaint.storeAdjustmentAmount?.setScale(0) ?: BigDecimal.ZERO`
        - `val explicitComm = complaint.commissionAdjustmentAmount?.setScale(0) ?: BigDecimal.ZERO`
        - `val refunded = complaint.approvedRefundAmount?.setScale(0) ?: BigDecimal.ZERO`
        - Nếu `explicitStore.signum() != 0 || explicitComm.signum() != 0`: dùng `(explicitStore, explicitComm)`.
        - Ngược lại nếu `refunded.signum() > 0`:
          - `ComplaintResponsibility.PLATFORM` → `(BigDecimal.ZERO, refunded.negate())`.
          - `ComplaintResponsibility.STORE`, `ComplaintResponsibility.SHARED`, `ComplaintResponsibility.CUSTOMER`, `ComplaintResponsibility.NONE`:
            `commAdj = MoneyRounding.applyRate(refunded, order.commissionRate).negate()`
            `storeAdj = refunded.negate().subtract(commAdj)`
        - Nếu `storeAdj.signum() != 0` và `storeEntry != null` → ghi `ADJUSTMENT` (`amount = storeAdj`, `originalEntry = storeEntry`, `businessKey = "COMPLAINT_RESOLVED:STORE_ADJ:${complaint.id}"`).
        - Nếu `commAdj.signum() != 0` và `commEntry != null` → ghi `ADJUSTMENT` (`amount = commAdj`, `originalEntry = commEntry`, `businessKey = "COMPLAINT_RESOLVED:COMM_ADJ:${complaint.id}"`).
  - **`postPendingEvents(batchSize: Int = properties.ledgerBatchSize): LedgerSyncReportView` (`@Transactional`):**
    - Quét tối đa `batchSize` `FinancialEvent` thuộc loại `ORDER_COMPLETED`, `NO_SHOW_CONFIRMED`, `COMPLAINT_RESOLVED` có khả năng chưa hạch toán bằng `SELECT id FROM financial_event ... ORDER BY occurred_at ASC, id ASC LIMIT :n FOR UPDATE SKIP LOCKED`.
    - Với mỗi event: khóa `CustomerOrder (findForUpdate)` theo đúng thứ tự chuẩn, gọi hàm `post...` tương ứng. Trả `LedgerSyncReportView(eventsScanned, entriesCreated, entriesSkippedAlreadyPosted)`.
  - **`LedgerPostingJob.kt` (`@Component`, `@ConditionalOnProperty("finance.ledger-job-enabled", havingValue = "true", matchIfMissing = true)`):** chạy định kỳ gọi `postPendingEvents()`.

> **Lý do cập nhật tối thiểu 4 service đã có (theo `.agents/CLAUDE.md` §1):**
> 1. [`PaymentConfirmationService.kt`](../../src/main/kotlin/com/foodrescue/backend/payment/service/PaymentConfirmationService.kt) (T6.3): truyền `businessKey = "PAYMENT_RECEIPT:${payment.id}"` khi tạo `LedgerEntry(PAYMENT_RECEIPT)` để đồng bộ `V24`.
> 2. [`ManualRefundService.kt`](../../src/main/kotlin/com/foodrescue/backend/payment/service/ManualRefundService.kt) (T6.5 & T8.1): truyền `businessKey = "CUSTOMER_REFUND:${refund.id}"` cho `CUSTOMER_REFUND`, và ngay khi auto-close `Complaint` từ `DECIDED -> CLOSED` gọi `ledgerPosting.postComplaintResolvedIfSettled(...)` trong cùng transaction.
> 3. [`PickupSessionService.kt`](../../src/main/kotlin/com/foodrescue/backend/pickup/service/PickupSessionService.kt) (T7.2): flush `FinancialEvent(ORDER_COMPLETED)` và gọi `ledgerPosting.postOrderCompleted(order, event)`.
> 4. [`NoShowService.kt`](../../src/main/kotlin/com/foodrescue/backend/pickup/service/NoShowService.kt) (T7.4): flush `FinancialEvent(NO_SHOW_CONFIRMED)` và gọi `ledgerPosting.postNoShowConfirmed(order, case, event)`.
> 5. [`AdminComplaintService.kt`](../../src/main/kotlin/com/foodrescue/backend/complaint/service/AdminComplaintService.kt) (T8.1): trong `decide` khi đóng `CLOSED` ngay (`approvedRefundAmount == 0`), gọi `ledgerPosting.postComplaintResolvedIfSettled(c, event, now)`. (Lưu ý: trường hợp `close()` thủ công cũng kiểm tra & gọi `postComplaintResolvedIfSettled` để bảo đảm an toàn.)
> Các thay đổi trên chỉ bổ sung bước ghi sổ cái T8.3 vào cùng transaction, **không** thay đổi logic nghiệp vụ, trạng thái hay chữ ký API của T6–T8.2.

### 2.3. Truy vấn Doanh thu, Quyền lợi & Sổ cái Đơn (`StoreFinanceService` & `OrderLedgerService`)

- **Kiểm tra cân bằng sổ cái 1 đơn hàng ([`OrderLedgerService.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/service/OrderLedgerService.kt)):**
  - Tổng hợp từ `customer_order` + `payment` + `refund` + `ledger_entry` + `no_show_case` + `complaint`:
    - `collectedVerified = SUM(PAYMENT_RECEIPT)`
    - `refundedVerified = ABS(SUM(CUSTOMER_REFUND))`
    - `netCollected = collectedVerified - refundedVerified`
    - `grossCommission`, `commissionAdjustment`, `netCommission`
    - `grossStorePayable`, `storeAdjustment`, `netStorePayable`
    - `grossNoShowCompensation`, `noShowAdjustment`, `netNoShowCompensation`
    - `netAllocated = netCommission + netStorePayable + netNoShowCompensation`
    - `balanced = (netAllocated.compareTo(netCollected) == 0)` (với đơn ở trạng thái cân bằng: `COMPLETED` không có `DECIDED` refund, `CANCELLED` đã hoàn đủ, `NO_SHOW` đã hoàn phần refund hoặc đã `FORFEITED` xong)
    - `entries: List<LedgerEntryView>` (mỗi dòng có `id`, `eventId`, `eventType`, `type`, `amount`, `currency`, `originalEntryId`, `businessKey`, `occurredAt`).
  - Dùng cho cả `GET /api/v1/stores/{storeId}/finance/orders/{orderId}/ledger` (Store Owner kiểm tra `order.store.id == storeId` sau khi `authorizeOwner`) và `GET /api/v1/admin/finance/orders/{orderId}/ledger` (`ADMIN`).
- **Báo cáo Doanh thu & Hoa hồng cho Store Owner (`UC-47` — `StoreFinanceService.revenue`):**
  - Kiểm tra quyền `authorization.requirePermission(accountId, storeId, "STORE_PROFILE_MANAGE")` (chỉ Store Owner; `Store Staff` và người ngoài nhận `403 ACCESS_DENIED`).
  - Validate `from <= to` (nếu có cả hai; ngược lại `400 VALIDATION_FAILED`), filter tùy chọn theo `orderCode` (exact/trim), phân trang `PageQuery` (`sort` whitelist `completedAt`/`occurredAt`).
  - Nguồn dữ liệu: các đơn `COMPLETED` thuộc `storeId` (trong khoảng `completed_at` nếu truyền `from`/`to`), join các bút toán `PLATFORM_COMMISSION`, `STORE_PAYABLE`, và các `ADJUSTMENT` gắn trực tiếp vào chúng.
  - Tính tổng trên toàn tập lọc (`summary: StoreRevenueSummaryView`):
    - `completedOrderCount`, `grossSalesAmount`, `grossCommissionAmount`, `commissionAdjustmentAmount`, `netCommissionAmount`, `storeAdjustmentAmount`, `netStoreRevenueAmount`, `currency = "VND"`.
  - Danh sách phân trang `orders: PageResponse<StoreOrderRevenueItemView>`: từng đơn gồm `orderId`, `orderCode`, `completedAt`, `commissionRateSnapshot`, `actualSellingPrice`, `grossCommissionAmount`, `commissionAdjustmentAmount`, `netCommissionAmount`, `grossStorePayableAmount`, `storeAdjustmentAmount`, `netStoreRevenueAmount`.
- **Báo cáo Khoản được nhận & Khoản đang giữ (`UC-48` — `StoreFinanceService.entitlements`):**
  - Kiểm tra quyền Store Owner `STORE_PROFILE_MANAGE`.
  - Query tất cả đơn của `storeId` có phát sinh quyền lợi (`COMPLETED` hoặc `NO_SHOW` có `retainedAmount > 0`), xác định trạng thái `eligibilityStatus` của từng khoản quyền lợi:
    - Với `COMPLETED`:
      - Nếu tồn tại `Complaint` của đơn có `status IN ('OPEN', 'INVESTIGATING', 'AWAITING_EVIDENCE', 'DECIDED')` → `HELD` (`holdReason = "OPEN_COMPLAINT"`), vào `heldDisputedSalesAmount`.
      - Ngược lại → `ELIGIBLE`, vào `salesPayableEligibleAmount`.
    - Với `NO_SHOW`:
      - `compensationStatus == HELD` → `HELD` (`holdReason = if (notificationReceivedAt == null) "AWAITING_NOTIFICATION_RECEIPT" else "WITHIN_DISPUTE_WINDOW"`), vào `heldNoShowAmount`.
      - `compensationStatus == DISPUTED` → `DISPUTED` (`holdReason = "OPEN_DISPUTE"`), vào `heldNoShowAmount`.
      - `compensationStatus == ELIGIBLE` (và không có complaint mở) → `ELIGIBLE`, vào `noShowCompensationEligibleAmount`.
      - `compensationStatus == SETTLED` → `SETTLED` (hiển thị lịch sử).
      - `compensationStatus == FORFEITED` → `FORFEITED` (net `= 0` sau `ADJUSTMENT`, không tính vào `eligible` hay `held`).
  - Lọc tùy chọn `from`/`to` (theo `occurred_at`), `status` (`ALL`, `ELIGIBLE`, `HELD`, `DISPUTED`, `FORFEITED`).
  - Trả `StoreEntitlementReportView` gồm `summary: StoreEntitlementSummaryView` (`salesPayableEligibleAmount`, `noShowCompensationEligibleAmount`, `totalEligibleToSettleAmount`, `heldNoShowAmount`, `heldDisputedSalesAmount`, `totalHeldAmount`, `forfeitedNoShowAmount`, `currency = "VND"`) + `items: PageResponse<StoreEntitlementItemView>` (truy về `ledgerEntryId`, `orderId`, `orderCode`, `type`, `grossAmount`, `adjustmentAmount`, `netAmount`, `eligibilityStatus`, `holdReason`, `occurredAt`).

---

## 3. API Contract (`docs/api/openapi.yaml`)

Thêm **5** đường dẫn mới (nâng từ `121` → **`126`** paths) và **2** tag mới (`"Store finance"`, `"Admin finance"`, nâng từ `29` → **`31`** tags):

| Method / Path | Quyền & Tham số | Response | Tag |
|---|---|---|---|
| `GET /api/v1/stores/{storeId}/finance/revenue` | Store Owner (`STORE_PROFILE_MANAGE`); query `from`, `to`, `orderCode`, `page`, `size` | `200 StoreRevenueReportView`; `400`, `401`, `403` | `Store finance` |
| `GET /api/v1/stores/{storeId}/finance/entitlements` | Store Owner (`STORE_PROFILE_MANAGE`); query `from`, `to`, `status`, `page`, `size` | `200 StoreEntitlementReportView`; `400`, `401`, `403` | `Store finance` |
| `GET /api/v1/stores/{storeId}/finance/orders/{orderId}/ledger` | Store Owner (`STORE_PROFILE_MANAGE`, đúng cửa hàng) | `200 OrderLedgerView`; `400`, `401`, `403`, `404` | `Store finance` |
| `GET /api/v1/admin/finance/orders/{orderId}/ledger` | `ADMIN` | `200 OrderLedgerView`; `400`, `401`, `403`, `404` | `Admin finance` |
| `POST /api/v1/admin/finance/ledger/reconcile` | `ADMIN` | `200 LedgerSyncReportView`; `401`, `403` | `Admin finance` |

---

## 4. Migration và Database Schema

- File migration mới: `src/main/resources/db/migration/V24__ledger_entry_business_key.sql`
  - Thêm cột `business_key varchar(255)` lên `ledger_entry`.
  - Tạo partial unique index `uq_ledger_entry_business_key ON ledger_entry (business_key) WHERE business_key IS NOT NULL`.
  - Tạo index `idx_ledger_entry_store_occurred ON ledger_entry (store_id, occurred_at DESC, id)`.
- Không sửa bất kỳ file migration nào từ `V1`–`V23`.
- Tổng số Hibernate entity giữ nguyên (`43`); tổng số migration tăng `23` → `24`.

---

## 5. File Layout dự kiến

Đường dẫn Kotlin tương đối dưới `src/main/kotlin/com/foodrescue/backend/`:

| File | Loại | Trách nhiệm |
|---|---|---|
| `src/main/resources/db/migration/V24__ledger_entry_business_key.sql` | NEW | Thêm `business_key` unique & index truy vấn cửa hàng trên `ledger_entry` |
| `src/main/resources/application.yml` | MODIFIED | Khai báo cấu hình `finance.ledger-*` |
| `finance/FinanceLedgerProperties.kt` | NEW | `@ConfigurationProperties("finance")` cho job hạch toán sổ cái |
| `finance/entity/LedgerEntry.kt` | MODIFIED | Map cột `business_key` |
| `finance/repository/LedgerEntryRepository.kt` | MODIFIED | Truy vấn sổ cái theo `businessKey` và `orderId` |
| `finance/repository/FinancialEventRepository.kt` | MODIFIED | Truy vấn `FinancialEvent` theo `orderId`, `type`, `businessKey` |
| `finance/dto/LedgerEntryView.kt` | NEW | DTO dòng bút toán sổ cái |
| `finance/dto/OrderLedgerView.kt` | NEW | DTO sổ cái & kiểm chứng cân bằng theo đơn (`collected - refunded`) |
| `finance/dto/LedgerSyncReportView.kt` | NEW | DTO kết quả chạy job/endpoint đồng bộ sổ cái idempotent |
| `finance/dto/StoreRevenueReportView.kt` | NEW | DTO tổng hợp & chi tiết doanh thu, hoa hồng (`UC-47`) |
| `finance/dto/StoreEntitlementReportView.kt` | NEW | DTO tổng hợp & chi tiết quyền lợi bán hàng, No-show, khoản giữ (`UC-48`) |
| `finance/service/LedgerPostingService.kt` | NEW | Logic hạch toán `ORDER_COMPLETED`, `NO_SHOW_CONFIRMED`, `COMPLAINT_RESOLVED` + `postPendingEvents` (`ON CONFLICT DO NOTHING` + `FOR UPDATE SKIP LOCKED`) |
| `finance/service/LedgerPostingJob.kt` | NEW | `@Scheduled` job nền với `@ConditionalOnProperty("finance.ledger-job-enabled")` |
| `finance/service/OrderLedgerService.kt` | NEW | Tổng hợp & đối chiếu cân bằng sổ cái đơn hàng cho Owner & Admin |
| `finance/service/StoreFinanceService.kt` | NEW | Truy vấn `UC-47` (`revenue`) và `UC-48` (`entitlements`) cho Store Owner |
| `finance/controller/StoreFinanceController.kt` | NEW | Endpoints `/api/v1/stores/{storeId}/finance/**` |
| `finance/controller/AdminFinanceLedgerController.kt` | NEW | Endpoints `/api/v1/admin/finance/**` |
| `payment/service/PaymentConfirmationService.kt` | MODIFIED | Truyền `businessKey = "PAYMENT_RECEIPT:${payment.id}"` |
| `payment/service/ManualRefundService.kt` | MODIFIED | Truyền `businessKey = "CUSTOMER_REFUND:${refund.id}"` và gọi `postComplaintResolvedIfSettled` khi auto-close complaint |
| `pickup/service/PickupSessionService.kt` | MODIFIED | Gọi `postOrderCompleted` trong cùng transaction hoàn tất bàn giao |
| `pickup/service/NoShowService.kt` | MODIFIED | Gọi `postNoShowConfirmed` trong cùng transaction xác nhận No-show |
| `complaint/service/AdminComplaintService.kt` | MODIFIED | Gọi `postComplaintResolvedIfSettled` khi đóng/quyết định khiếu nại |
| `docs/api/openapi.yaml` | MODIFIED | Thêm 2 tags (`29` → `31`) và 5 paths (`121` → `126`) |
| `src/test/kotlin/.../support/AuthTestFixtures.kt` | MODIFIED | Tắt `finance.ledger-job-enabled=false` trong test |
| `src/test/kotlin/.../CommissionAndRevenueTests.kt` | NEW | Bộ integration test TDD bao phủ `AC-01`..`AC-10` |
| `src/test/kotlin/.../AuthMigrationTests.kt`, `FoodRescueApplicationTests.kt`, `SwaggerUiTests.kt` | MODIFIED | Cập nhật kỳ vọng `23` → `24` migrations, `121` → `126` paths, `29` → `31` tags |
| `docs/specs/t8-3-commission-and-revenue/03-implementation-control.txt` | NEW | Báo cáo kiểm chứng cuối task |
| `docs/specs/roadmap-3-9.md`, `.gitignore` | MODIFIED | Đóng `[x] T8.3` và whitelist `!docs/specs/t8-3-commission-and-revenue/` |

---

## 6. Task Breakdown

- [ ] **T8.3-01:** Tạo migration `V24__ledger_entry_business_key.sql`, cập nhật `LedgerEntry`, `LedgerEntryRepository`, `FinancialEventRepository`, `FinanceLedgerProperties`, `application.yml`, `AuthTestFixtures`.
- [ ] **T8.3-02 (TDD):** Viết trước bộ integration test `CommissionAndRevenueTests.kt` bao phủ `AC-01`..`AC-10` và chạy xác nhận TDD RED (endpoint `404` / chưa có bút toán `PLATFORM_COMMISSION`, `STORE_PAYABLE`, `NO_SHOW_COMPENSATION`, `ADJUSTMENT`).
- [ ] **T8.3-03:** Cài đặt `LedgerPostingService`, `LedgerPostingJob`, DTOs (`LedgerEntryView`, `OrderLedgerView`, `LedgerSyncReportView`, `StoreRevenueReportView`, `StoreEntitlementReportView`), `OrderLedgerService`, `StoreFinanceService`, `StoreFinanceController`, `AdminFinanceLedgerController`.
- [ ] **T8.3-04:** Tích hợp `LedgerPostingService` & `businessKey` vào `PaymentConfirmationService`, `ManualRefundService`, `PickupSessionService`, `NoShowService`, `AdminComplaintService`.
- [ ] **T8.3-05:** Cập nhật `docs/api/openapi.yaml` (`126` paths / `31` tags) và kỳ vọng tại `AuthMigrationTests`, `FoodRescueApplicationTests`, `SwaggerUiTests`.
- [ ] **T8.3-06:** Chạy targeted tests (`CommissionAndRevenueTests`, `NoShowTests`, `ComplaintTests`, `ManualRefundTests`, `PaymentWebhookTests`, `SwaggerUiTests`, `ApiContractTests`, `FoodRescueApplicationTests`), sau đó chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`.
- [ ] **T8.3-07:** Đọc trực tiếp `build/reports/tests/test/index.html` và XML các suite, viết `03-implementation-control.txt`, cập nhật `[x] T8.3` trong `roadmap-3-9.md` và `.gitignore`.

---

## 7. Truy vết AC → Task → Test

| AC | Mô tả ngắn | Task | Test Case dự kiến (`CommissionAndRevenueTests` & liên quan) |
|---|---|---|---|
| **AC-01** | `COMPLETED`: hoa hồng theo giá bán thực tế `totalAmount` + làm tròn `HALF_UP` `NFR-17`, `STORE_PAYABLE = total - comm`, cân bằng đơn | T8.3-01, 03, 04 | `AC-01 completed order posts commission on actual totalAmount with HALF_UP rounding and balances` |
| **AC-02** | `PENDING_PAYMENT` / `EXPIRED` / `CANCELLED`: hoa hồng `= 0`, sau hoàn đủ tổng cân bằng `= 0` | T8.3-03 | `AC-02 pending expired and cancelled orders accrue zero commission and reconcile to zero after refund` |
| **AC-03** | `NO_SHOW` (`HELD`): `NO_SHOW_COMPENSATION` riêng, hoa hồng `= 0`, tính vào `heldAmount` | T8.3-03, 04 | `AC-03 no-show posts separate compensation with zero commission held before 48h expiry` |
| **AC-04** | `NO_SHOW` qua 48h `ELIGIBLE` (hoặc bác khiếu nại): chuyển `eligiblePayableAmount`, không ghi trùng | T8.3-03 | `AC-04 no-show past 48h dispute window or rejected complaint unlocks eligible compensation` |
| **AC-05** | `NO_SHOW` `FORFEITED` + hoàn 100%: `ADJUSTMENT` đảo `NO_SHOW_COMPENSATION`, tổng `= 0` | T8.3-03, 04 | `AC-05 forfeited no-show records adjustment linked to original entry and balances to zero` |
| **AC-06** | `COMPLETED` + khiếu nại/hoàn hậu giao (`STORE`/`SHARED`/`PLATFORM`/explicit): `ADJUSTMENT` trỏ `original_entry_id`, cân bằng sau `FULFILLED` | T8.3-03, 04 | `AC-06 post-delivery complaint refund records adjustments linked to store and commission entries after fulfillment` |
| **AC-07** | Đơn `COMPLETED` đang có khiếu nại mở/`DECIDED` bị giữ vào `heldAmount`, đóng `CLOSED` mới đủ điều kiện | T8.3-03 | `AC-07 completed order with open or decided complaint stays in held amount until closed` |
| **AC-08** | Re-run job/endpoint & chạy song song 2 luồng không ghi trùng `LedgerEntry` (`business_key` + `SKIP LOCKED`) | T8.3-01, 03 | `AC-08 repeated and concurrent ledger reconciliation jobs never create duplicate entries` |
| **AC-09** | `UC-47` & `UC-48` & ledger đơn: lọc kỳ/đơn/trạng thái, trạng thái rỗng, chỉ đọc | T8.3-03 | `AC-09 store owner revenue entitlements filters empty state and order ledger balance` |
| **AC-10** | `401` chưa login, `403` `Store Staff` (dù đủ 6 quyền `V7`), Owner khác, `CUSTOMER` gọi API Store/Admin | T8.3-03 | `AC-10 authorization denies staff other owners customers and unauthenticated callers` |
| **AC-11** | Flyway `V24`, OpenAPI `126` paths / `31` tags, không vỡ test cũ, full build xanh | T8.3-05..07 | `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests`, `NoShowTests`, `ComplaintTests`, `ManualRefundTests` |

---

## 8. Kế hoạch kiểm thử TDD

- Viết `CommissionAndRevenueTests.kt` trước khi cài đặt `LedgerPostingService` / controller, chạy xác nhận TDD RED (thiếu endpoint `/finance/...` → `404` và chưa có `PLATFORM_COMMISSION`/`STORE_PAYABLE`/`NO_SHOW_COMPENSATION`/`ADJUSTMENT`).
- Dùng `Clock` cố định (pattern `PickupTestClock` hoặc điều khiển thời gian qua service) để test chính xác chuyển tiếp 48h `NoShowCase` và bộ lọc `from`/`to` UTC.
- Test làm tròn `NFR-17` với cả giá trị `.45`, `.50`, `.75` (ví dụ `10,005 × 0.15 = 1,500.75 → 1,501`; `10,003 × 0.15 = 1,500.45 → 1,500`; `10,010 × 0.05 = 500.50 → 501` với `HALF_UP`) và xác minh `totalAmount != minimumValue`.
- Test đồng thời 2 luồng gọi `POST /api/v1/admin/finance/ledger/reconcile` (hoặc `postPendingEvents`) bằng `CountDownLatch` để chứng minh không tạo trùng `LedgerEntry`.
- Kết thúc task chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain` và đọc trực tiếp `build/reports/tests/test/index.html` cùng XML các suite.

---

## 9. Rủi ro và phương án kiểm soát / Rollback

1. **Rủi ro trigger `V3 immutable_ledger_entry` từ chối migration `V24`:**  
   *Kiểm soát:* `V24` chỉ `ALTER TABLE ledger_entry ADD COLUMN business_key varchar(255)` và tạo partial unique index `WHERE business_key IS NOT NULL`, tuyệt đối không chạy `UPDATE` trên `ledger_entry`.
2. **Rủi ro vòng khóa chéo (deadlock) giữa các luồng cập nhật trạng thái và hạch toán:**  
   *Kiểm soát:* Tuân thủ thứ tự khóa một chiều `CustomerOrder (findForUpdate) -> Complaint / NoShowCase -> Payment -> RefundRequest`; `LedgerPostingService` chỉ thực hiện `INSERT ... ON CONFLICT DO NOTHING` vào `ledger_entry` và được gọi sau khi khóa `CustomerOrder`.
3. **Rủi ro lệch cân bằng khi xử lý lại các đơn/sự kiện sinh trước T8.3 (hoặc gọi `reconcile` nhiều lần):**  
   *Kiểm soát:* `postPendingEvents()` khóa `FinancialEvent` với `FOR UPDATE SKIP LOCKED`, khóa `CustomerOrder`, chạy lại logic idempotent theo `business_key` và bỏ qua các `Complaint` chưa `CLOSED`.
4. **Rủi ro sửa nhầm hành vi các test cũ (`NoShowTests`, `ManualRefundTests`, `ComplaintTests`, `PaymentWebhookTests`):**  
   *Kiểm soát:* Không thay đổi rule/response API cũ; `PaymentWebhookTests` kiểm tra `ledger_entry` trước khi bàn giao/no-show, `ManualRefundTests` kiểm tra theo `type='CUSTOMER_REFUND'`, `NoShowTests` không giả định `ledger_entry = 0`. Sẽ chạy regression toàn bộ các suite này trước khi đóng task.
5. **Rollback:**  
   Trước khi đóng task, có thể hoàn tác sạch gói controller/service/DTO mới dưới `finance/`, các lời gọi `LedgerPostingService` ở 5 service tích hợp và `V24` trên DB mới mà không ảnh hưởng dữ liệu/bất biến của `V1`–`V23`.
