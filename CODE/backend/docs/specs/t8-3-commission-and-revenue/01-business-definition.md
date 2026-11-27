# Business Definition: T8.3 — Hoa hồng, Quyền lợi Cửa hàng & Doanh thu

**Status:** Chờ duyệt Gate A  
**Updated:** 2026-11-27  
**Scope:** T8.3 (`UC-47`, `UC-48`, `BR-46..52`, `NFR-17`: hạch toán sổ cái hoa hồng bán hàng `PLATFORM_COMMISSION` và quyền lợi cửa hàng `STORE_PAYABLE` khi đơn `COMPLETED`; hạch toán riêng bù đắp No-show `NO_SHOW_COMPENSATION` với hoa hồng `0` và chỉ cho phép đối soát sau khi hết cửa sổ phản ánh 48h không còn tranh chấp; sinh bút toán `ADJUSTMENT` gắn `original_entry_id` khi hoàn tiền sau bàn giao hoặc quyết định khiếu nại; đảm bảo cân bằng sổ cái `Σ phân bổ = tiền thu − tiền hoàn`, idempotency chống ghi trùng qua `business_key`, quy tắc làm tròn `NFR-17`; cung cấp API cho Chủ cửa hàng xem doanh thu, hoa hồng, khoản được hưởng và khoản đang bị giữ).  
**Phụ thuộc:** T8.1 (`[x]`), T8.2 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Bối cảnh và mục tiêu

Đến hết **T8.2**, các luồng nghiệp vụ tạo phát sinh tiền tệ đã ghi nhận đầy đủ dữ liệu giao dịch và `FinancialEvent` bất biến:
- **Thanh toán (`T6.3`):** `PaymentConfirmationService.confirm` ghi `FinancialEvent(PAYMENT_RECEIVED)` và `LedgerEntry(PAYMENT_RECEIPT, +receivedAmount)` cho các thanh toán `VERIFIED`.
- **Hoàn tiền thủ công (`T6.5`):** `ManualRefundService.confirm` ghi `FinancialEvent(REFUND_SUCCEEDED)` và nếu thanh toán gốc có trạng thái `VERIFIED` thì ghi `LedgerEntry(CUSTOMER_REFUND, -amount)`.
- **Bàn giao hoàn tất (`T7.2`):** `PickupSessionService.completeSession` ghi `FinancialEvent(ORDER_COMPLETED, businessKey="ORDER_COMPLETED:{orderId}")`, nhưng **chưa** phát sinh `LedgerEntry(PLATFORM_COMMISSION)` và `LedgerEntry(STORE_PAYABLE)`.
- **Xác nhận No-show (`T7.4`):** `NoShowService.confirm` tính `NoShowAmounts` (hoa hồng = `0`), tạo `NoShowCase(compensationStatus = HELD)` và `FinancialEvent(NO_SHOW_CONFIRMED, businessKey="NO_SHOW_CONFIRMED:{orderId}")`, nhưng **chưa** ghi `LedgerEntry(NO_SHOW_COMPENSATION)`. `NoShowCompensationService.releaseEligible` mở khóa `HELD -> ELIGIBLE` khi qua `dispute_deadline` và không còn khiếu nại mở.
- **Giải quyết khiếu nại (`T8.1`):** `AdminComplaintService.decide` ghi `FinancialEvent(COMPLAINT_RESOLVED)`, lưu `outcome`, `responsibility`, `approvedRefundAmount`, `storeAdjustmentAmount`, `commissionAdjustmentAmount`, hủy bù đắp No-show (`FORFEITED`) khi lỗi cửa hàng và mở `RefundRequest(PENDING)` (nếu có hoàn tiền), nhưng **chưa** ghi `LedgerEntry(ADJUSTMENT)` (theo quyết định Q5 của T8.1 đã bàn giao sang T8.3).
- **Truy vấn của Chủ cửa hàng:** Chưa có API cho Store Owner xem doanh thu, hoa hồng, quyền lợi bán hàng/No-show và các khoản đang bị giữ (`UC-47`, `UC-48`).

Mục tiêu của **T8.3**:
1. **Hạch toán sổ cái chuẩn xác & thống nhất làm tròn (`BR-47..49`, `BR-51`, `NFR-17`):**
   - Đơn `COMPLETED`: tính hoa hồng trên giá bán thực tế đã snapshot (`order.totalAmount × order.commissionRate`), làm tròn thống nhất nguyên đồng VND (`MoneyRounding.applyRate`, `HALF_UP`), ghi `PLATFORM_COMMISSION` và `STORE_PAYABLE = totalAmount - commission`.
   - Đơn `CANCELLED` / `EXPIRED` / chưa thanh toán: hoa hồng bán hàng `= 0`, không sinh `PLATFORM_COMMISSION` hoặc `STORE_PAYABLE` (`BR-48`).
   - Đơn `NO_SHOW`: hoa hồng `= 0`, hạch toán riêng `NO_SHOW_COMPENSATION` theo `NoShowCase.retainedAmount`; chỉ đủ điều kiện đưa vào đối soát/chi trả (`T8.4`) khi `NoShowCase.compensationStatus == ELIGIBLE` (hết cửa sổ phản ánh 48h và không còn tranh chấp).
2. **Điều chỉnh tài chính truy vết gốc (`BR-45`, `BR-52`):**
   - Khi hoàn tiền sau bàn giao hoặc quyết định xử lý khiếu nại có điều chỉnh quyền lợi/hoa hồng hoặc hủy bù đắp No-show (`FORFEITED`), phát sinh `LedgerEntry(ADJUSTMENT)` trỏ `original_entry_id` về đúng bút toán gốc (`STORE_PAYABLE`, `PLATFORM_COMMISSION`, hoặc `NO_SHOW_COMPENSATION`).
3. **Bất biến cân bằng & Chống ghi trùng (`BR-50`, `NFR-19`, Roadmap AC):**
   - Sổ cái của từng đơn hàng đạt chuẩn đối soát: tổng bút toán phân bổ quyền lợi & điều chỉnh khớp `tiền thu đã xác minh − tiền hoàn đã xác minh`.
   - Đảm bảo idempotency ở cả tầng DB (unique `business_key` trên `ledger_entry`) và tầng service/job (`FOR UPDATE SKIP LOCKED` / khóa dòng đơn), chạy lại job nhiều lần không bao giờ tạo bút toán trùng.
4. **API cho Chủ cửa hàng (`UC-47`, `UC-48`, `BR-46`):**
   - Chỉ **Store Owner** của cửa hàng được xem báo cáo doanh thu, hoa hồng, quyền lợi được hưởng (bán hàng + No-show đủ điều kiện), khoản đang bị giữ (No-show chờ hết 48h/đang tranh chấp, đơn hoàn tất đang có khiếu nại chưa đóng) và sổ cái chi tiết theo đơn.

Nguồn nghiệp vụ trích xuất từ `../../DOCX/BaoCaoFoodRescue.pdf`:
- **Trang 21 (`NFR-17`, `NFR-18`, `NFR-19`, `NFR-20`):** Quy tắc tính và làm tròn tiền tệ thống nhất; bất biến snapshot giá và tỷ lệ hoa hồng; xử lý lặp lại không tạo bút toán trùng; lưu lịch sử điều chỉnh hoa hồng/đối soát.
- **Trang 29–30 (`BR-45`, `BR-46..BR-52`):** Quyền xem tỷ lệ hoa hồng; cơ sở tính hoa hồng trên giá bán thực tế; thời điểm ghi nhận hoa hồng khi đơn hoàn tất; quyền lợi cửa hàng; đối soát thanh toán; hạch toán riêng No-show và điều kiện mở khóa sau 48h không tranh chấp; điều chỉnh tài chính gắn giao dịch gốc.
- **Trang 95–97 (`UC-47`, `UC-48`):** Xem doanh thu và hoa hồng; Xem khoản cửa hàng được nhận (quyền lợi bán hàng, số còn phải chuyển, bù đắp No-show và khoản đang bị giữ).
- **Trang 164 (`Hình 8.12`):** Ngữ cảnh đối soát và chi trả cho cửa hàng (tách rõ ranh giới T8.3 chuẩn bị dữ liệu đủ điều kiện vs T8.4 lập bảng `Settlement` và chi trả `StorePayout`).

---

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin dưới `src/main/kotlin/com/foodrescue/backend/`:

1. **Schema & Entity tài chính hiện có (`V1`, `V2`, `V3`):**
   - `finance/FinancialEventType.kt`: `PAYMENT_RECEIVED`, `ORDER_COMPLETED`, `REFUND_SUCCEEDED`, `NO_SHOW_CONFIRMED`, `COMPLAINT_RESOLVED`, `ADJUSTMENT`.
   - `finance/LedgerEntryType.kt`: `PAYMENT_RECEIPT`, `CUSTOMER_REFUND`, `PLATFORM_COMMISSION`, `STORE_PAYABLE`, `NO_SHOW_COMPENSATION`, `ADJUSTMENT`.
   - `finance/entity/FinancialEvent.kt` (`@Immutable`): có `order`, `type`, `payment`, `refund`, `noShowCase`, `complaint`, `occurredAt`, `businessKey` (`UNIQUE`). Ràng buộc `V2` #L223-230 yêu cầu `num_nonnulls(payment_id, refund_id, no_show_case_id, complaint_id) <= 1` và khớp từng `type` (`ORDER_COMPLETED` và `ADJUSTMENT` không bắt buộc khóa ngoại con).
   - `finance/entity/LedgerEntry.kt` (`@Immutable`): hiện mới có `event`, `store`, `type`, `amount numeric(19,2) CHECK (amount = trunc(amount))`, `currency = 'VND'`, `originalEntry` (`original_entry_id`), `occurredAt`.
   - **Khoảng trống schema cần lưu ý (`Q1`):** Bảng `ledger_entry` (`V1`) hiện **chưa có cột `business_key` (unique)** và **chưa có chỉ báo trạng thái đủ điều kiện đối soát / ghi chú truy vết**, trong khi Trigger `V3` (`immutable_ledger_entry`, `immutable_financial_event`) chặn hoàn toàn `UPDATE`/`DELETE` sau khi insert. Để đảm bảo re-run job hoặc thao tác đồng thời không thể ghi trùng `LedgerEntry`, cần cân nhắc bổ sung cột `business_key` (`UNIQUE`) qua migration `V24`.
   - `finance/service/MoneyRounding.kt` (T4.5): đã chốt `MoneyRounding.applyRate(amount, rate) = amount.multiply(rate).setScale(0, RoundingMode.HALF_UP)`.
   - `finance/NoShowAmounts.kt`: tính `retainedAmount = min(round_HALF_UP(orderTotal * retentionRate), verifiedAmount)`, `refundAmount = verifiedAmount - retainedAmount`, `commissionAmount = 0`.
2. **Các điểm phát sinh `FinancialEvent` hiện hữu:**
   - `payment/service/PaymentConfirmationService.kt` (T6.3): đã ghi `FinancialEvent(PAYMENT_RECEIVED)` + `LedgerEntry(PAYMENT_RECEIPT, +receivedAmount)`.
   - `payment/service/ManualRefundService.kt` (T6.5): đã ghi `FinancialEvent(REFUND_SUCCEEDED)` + nếu `payment.status == VERIFIED` thì ghi `LedgerEntry(CUSTOMER_REFUND, -amount)`.
   - `pickup/service/PickupSessionService.kt` (T7.2): mới ghi `FinancialEvent(ORDER_COMPLETED)` khi hoàn tất bàn giao.
   - `pickup/service/NoShowService.kt` (T7.4): mới ghi `FinancialEvent(NO_SHOW_CONFIRMED)` khi xác nhận No-show.
   - `complaint/service/AdminComplaintService.kt` (T8.1): mới ghi `FinancialEvent(COMPLAINT_RESOLVED)` và lưu `outcome`, `responsibility`, `approvedRefundAmount`, `storeAdjustmentAmount`, `commissionAdjustmentAmount` trên `Complaint`.
   - `finance/repository/FinancialEventRepository.kt` & `LedgerEntryRepository.kt`: đều đang là `JpaRepository` trống, chưa có query nghiệp vụ.
3. **Phân quyền Store Owner (`V7`, `StoreAuthorizationService`):**
   - `V7__staff_permissions_and_invitations.sql` (#L10-L18) cố ý không cấp quyền xem hoa hồng/doanh thu cho nhân viên (`Staff`). Các endpoint chỉ dành cho chủ cửa hàng như `StoreProfileController` đều kiểm tra qua `StoreAuthorizationService.requirePermission(accountId, storeId, "STORE_PROFILE_MANAGE")` (hoặc kiểm tra trực tiếp `store.owner.id == accountId`), đảm bảo Store Staff nhận `403 ACCESS_DENIED`.
4. **Baseline kiểm thử đã chạy xác minh:**
   - Lệnh: `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain` → **BUILD SUCCESSFUL**.
   - Tổng hợp `build/test-results/test/TEST-*.xml`: **58** test suites, `337` tests = **336 PASS / 0 FAIL / 0 ERROR / 1 SKIPPED** (ca skip có sẵn `SwaggerUiContextPathTests`).
   - Hiện trạng repo: **23** migrations (`V1`–`V23`), **43** Hibernate entities, **121** OpenAPI paths / **29** tags.

---

## 3. Người dùng và quyền

- **Chủ cửa hàng (`Store Owner`):**
  - Xác thực qua JWT + kiểm tra sở hữu cửa hàng trên server (`store.owner.id == CurrentAccount.get().accountId` hoặc qua `StoreAuthorizationService` với quyền owner-only `STORE_PROFILE_MANAGE`, cửa hàng chưa duyệt/đã đóng không có quyền thao tác theo rule T3.2, hoặc xem rõ trong `Q5`).
  - Xem doanh thu và hoa hồng (`UC-47`), xem khoản cửa hàng được nhận & khoản đang bị giữ (`UC-48`), xem sổ cái chi tiết theo từng đơn thuộc cửa hàng mình.
  - Không được tự sửa tỷ lệ hoa hồng (`BR-46`), không được sửa/xóa bút toán, không xem được số liệu cửa hàng khác (**403 `ACCESS_DENIED`**).
- **Nhân viên cửa hàng (`Store Staff`) và Khách hàng (`CUSTOMER`):**
  - Mọi truy cập vào API doanh thu/quyền lợi/hoa hồng của cửa hàng → **403 `ACCESS_DENIED`** (`BR-46`, `NFR-12`).
- **Quản trị viên (`ADMIN`):**
  - `@PreAuthorize("hasRole('ADMIN')")`.
  - Xem sổ cái đối chiếu theo đơn (`GET /api/v1/admin/finance/orders/{orderId}/ledger`) và kích hoạt chạy/rà soát hạch toán sổ cái idempotent (`POST /api/v1/admin/finance/ledger/reconcile`).
  - Chủ cửa hàng / nhân viên / khách hàng gọi endpoint Admin → **403 `ACCESS_DENIED`**.
- **Tác nhân hệ thống (Scheduled / Idempotent Ledger Poster):**
  - Chạy đồng bộ trong transaction khi phát sinh sự kiện (hoặc quét định kỳ các `FinancialEvent` chưa hạch toán qua `@Scheduled` với cờ cấu hình và `FOR UPDATE SKIP LOCKED`), luôn dùng `Clock` (không gọi `Instant.now()`).
- **Chưa đăng nhập / token không hợp lệ:** **401 `UNAUTHORIZED`**.

---

## 4. Luồng chính

### 4.1. Mô hình kế toán sổ cái & Quy ước cân bằng đơn hàng (`BR-47..52`, `NFR-17`)

Trong `ledger_entry` (`V1`), mỗi đơn hàng có hai lớp bút toán bất biến cùng đơn vị nguyên đồng `VND`:
1. **Lớp Dòng tiền thực (Cash Flow — đã có từ T6.3 & T6.5):**
   - `PAYMENT_RECEIPT` (`+amount`): mỗi `Payment` trạng thái `VERIFIED` gắn đơn.
   - `CUSTOMER_REFUND` (`-amount`): mỗi `Refund` trạng thái `SUCCEEDED` xuất từ `Payment VERIFIED`.
   - Tổng dòng tiền ròng thực tế trên đơn = `collectedVerified - refundedVerified = SUM(PAYMENT_RECEIPT) + SUM(CUSTOMER_REFUND)`.
2. **Lớp Phân bổ quyền lợi & Điều chỉnh (Allocation / Entitlement — T8.3):**
   - **Đơn `COMPLETED` (`PickupSessionService.completeSession`):**
     - Lấy `actualSellingPrice = order.totalAmount` (snapshot tại lúc đặt, không tính trên `minimumValue` — `BR-47`) và `commissionRate = order.commissionRate`.
     - Tính `commission = MoneyRounding.applyRate(actualSellingPrice, commissionRate)` (`HALF_UP`, nguyên đồng `VND` — `NFR-17`).
     - Tính `storePayable = actualSellingPrice.subtract(commission)` (`BR-49`).
     - Ghi `LedgerEntry(PLATFORM_COMMISSION, +commission)` và `LedgerEntry(STORE_PAYABLE, +storePayable)` gắn `FinancialEvent(ORDER_COMPLETED)`.
     - Nếu đơn này không có hoàn tiền / điều chỉnh: `PLATFORM_COMMISSION + STORE_PAYABLE = totalAmount = collectedVerified - refundedVerified`.
   - **Đơn `CANCELLED` hoặc `EXPIRED`:**
     - `BR-48`: không ghi `PLATFORM_COMMISSION` hay `STORE_PAYABLE` (hoa hồng = `0`, quyền lợi bán hàng = `0`). Sau khi hoàn hết tiền cho khách (`CUSTOMER_REFUND`), `collectedVerified - refundedVerified = 0 = Σ phân bổ`.
   - **Đơn `NO_SHOW` (`NoShowService.confirm`):**
     - `BR-48`, `BR-51`: hoa hồng bán hàng `= 0` (không ghi `PLATFORM_COMMISSION`).
     - Nếu `noShowCase.retainedAmount > 0`: ghi `LedgerEntry(NO_SHOW_COMPENSATION, +retainedAmount)` gắn `FinancialEvent(NO_SHOW_CONFIRMED)`.
     - Khi phần hoàn khách (VD 70%) đã xác nhận chuyển ở T6.5 (`CUSTOMER_REFUND = -refundAmount`), dòng tiền ròng = `collectedVerified - refundedVerified = +retainedAmount = NO_SHOW_COMPENSATION`.
     - **Trạng thái giữ vs đủ điều kiện đối soát:** Khi `noShowCase.compensationStatus IN (HELD, DISPUTED)`, khoản `+retainedAmount` nằm trong nhóm **Khoản đang giữ (`heldAmount`)**, bị chặn chưa đưa vào đối soát `T8.4`. Chỉ khi `NoShowCompensationService.releaseEligible` (sau 48h `dispute_deadline` và không có khiếu nại mở) hoặc `AdminComplaintService.decide(REJECTED)` đưa trạng thái về `ELIGIBLE`, khoản này mới chuyển sang **Đủ điều kiện đối soát (`eligibleAmount`)**.
   - **Điều chỉnh khi Hoàn tiền hậu giao / Giải quyết Khiếu nại (`COMPLAINT_RESOLVED` — `BR-45`, `BR-52`):**
     - Với **đơn `NO_SHOW` bị kết luận lỗi cửa hàng (`NoShowCase.compensationStatus == FORFEITED`)**: nếu trước đó đã ghi `NO_SHOW_COMPENSATION (+retainedAmount)`, sinh `LedgerEntry(ADJUSTMENT, -retainedAmount, originalEntry = noShowCompensationEntry)` gắn `FinancialEvent(COMPLAINT_RESOLVED)`. Sau khi hoàn nốt phần còn lại cho khách (`CUSTOMER_REFUND`), tổng dòng tiền = `0` và tổng phân bổ = `+retained - retained = 0`.
     - Với **đơn `COMPLETED` có quyết định khiếu nại `APPROVED` / `PARTIAL` phát sinh điều chỉnh (`storeAdjustmentAmount`, `commissionAdjustmentAmount`, hoặc hoàn tiền gắn trách nhiệm)**: sinh các bút toán `LedgerEntry(ADJUSTMENT)` có `original_entry_id` trỏ đúng vào `STORE_PAYABLE` (cho điều chỉnh phần cửa hàng) và `PLATFORM_COMMISSION` (cho điều chỉnh hoa hồng), bảo đảm quyền lợi ròng cửa hàng = `STORE_PAYABLE + Σ ADJUSTMENT(STORE_PAYABLE)` và hoa hồng ròng = `PLATFORM_COMMISSION + Σ ADJUSTMENT(PLATFORM_COMMISSION)` (chi tiết thời điểm ghi & xử lý phần nền tảng chịu xem **Q3, Q4**).

### 4.2. Cơ chế đồng bộ & Job rà soát Idempotent (`LedgerPostingService`)

1. Tại thời điểm chuyển trạng thái nghiệp vụ (`ORDER_COMPLETED`, `NO_SHOW_CONFIRMED`, `COMPLAINT_RESOLVED` / `REFUND_SUCCEEDED`), gọi `LedgerPostingService` trong **cùng transaction** để ghi bút toán ngay lập tức (không có độ trễ cho Owner khi vừa bàn giao).
2. Ngoài ra cung cấp `LedgerPostingService.postPendingEvents(batchSize)` (kết hợp job định kỳ tùy chọn & endpoint Admin `/reconcile`):
   - Quét các `FinancialEvent` chưa hoàn tất hạch toán bằng `SELECT ... FOR UPDATE SKIP LOCKED` theo lô, khóa dòng `CustomerOrder` (`findForUpdate`), kiểm tra `business_key` trên `ledger_entry` trước khi chèn.
   - Do mỗi bút toán có `business_key` duy nhất ở DB (ví dụ `"ORDER_COMPLETED:COMMISSION:{orderId}"`, `"ORDER_COMPLETED:STORE_PAYABLE:{orderId}"`, `"NO_SHOW_CONFIRMED:COMPENSATION:{caseId}"`, `"COMPLAINT_RESOLVED:STORE_ADJ:{complaintId}"`, `"COMPLAINT_RESOLVED:COMM_ADJ:{complaintId}"`, `"COMPLAINT_RESOLVED:NO_SHOW_FORFEIT:{complaintId}"`), chạy lại job hoặc xử lý đồng thời nhiều luồng **không bao giờ tạo trùng bút toán**.

### 4.3. Chủ cửa hàng xem doanh thu, hoa hồng & quyền lợi (`UC-47`, `UC-48`)

1. **Xem doanh thu và hoa hồng (`UC-47`):**
   - Chủ cửa hàng chọn khoảng thời gian (`from`, `to` UTC) hoặc lọc theo `orderId`/`orderCode`.
   - Hệ thống kiểm tra quyền Store Owner đối với `storeId` (`401`/`403`/`404`), truy vấn các đơn `COMPLETED` và toàn bộ bút toán `PLATFORM_COMMISSION`, `STORE_PAYABLE`, `ADJUSTMENT` liên quan trong kỳ (chỉ đọc, không sửa trạng thái).
   - Trả tổng hợp kỳ:
     - `completedOrderCount`
     - `grossSalesAmount` (tổng giá bán thực tế `total_amount` các đơn `COMPLETED`)
     - `grossCommissionAmount` (tổng `PLATFORM_COMMISSION`)
     - `commissionAdjustmentAmount` (tổng `ADJUSTMENT` trỏ về `PLATFORM_COMMISSION`)
     - `netCommissionAmount` (`grossCommissionAmount + commissionAdjustmentAmount`)
     - `storeAdjustmentAmount` (tổng `ADJUSTMENT` trỏ về `STORE_PAYABLE`)
     - `netStoreRevenueAmount` (quyền lợi ròng cửa hàng từ bán hàng)
   - Kèm danh sách phân trang từng đơn hoàn tất với tỷ lệ hoa hồng snapshot, giá bán thực tế, hoa hồng gốc, điều chỉnh và số cửa hàng hưởng. Nếu không có dữ liệu → trả tổng `0` và danh sách rỗng (`UC-47 A1`).
2. **Xem khoản cửa hàng được nhận (`UC-48`):**
   - Chủ cửa hàng lọc theo `from`, `to` hoặc trạng thái phân loại (`ALL`, `ELIGIBLE`, `HELD`).
   - Hệ thống tách bạch rõ:
     - `salesPayableEligibleAmount`: tổng `STORE_PAYABLE + ADJUSTMENT(STORE_PAYABLE)` của các đơn `COMPLETED` **không** có khiếu nại đang mở (`OPEN`, `INVESTIGATING`, `AWAITING_EVIDENCE`, `DECIDED`).
     - `noShowCompensationEligibleAmount`: tổng `NO_SHOW_COMPENSATION + ADJUSTMENT(NO_SHOW_COMPENSATION)` của các đơn `NO_SHOW` có `compensationStatus == ELIGIBLE` và không có khiếu nại mở.
     - `totalEligibleToSettleAmount`: `salesPayableEligibleAmount + noShowCompensationEligibleAmount` (khoản đủ điều kiện chuyển đối soát T8.4; lưu ý chưa trừ phần đã chi trả ở T8.4 vì bảng `Settlement` thực hiện ở T8.4).
     - `heldNoShowAmount`: tổng `NO_SHOW_COMPENSATION` của các đơn đang ở `HELD` (chờ qua 48h nhận thông báo) hoặc `DISPUTED` (đang tranh chấp khiếu nại).
     - `heldDisputedSalesAmount`: tổng quyền lợi bán hàng của các đơn `COMPLETED` đang bị tạm giữ vì có khiếu nại chưa `CLOSED`.
     - `totalHeldAmount`: `heldNoShowAmount + heldDisputedSalesAmount`.
   - Kèm danh sách chi tiết từng dòng quyền lợi (gắn `ledgerEntryId`, `orderId`, `orderCode`, `type`, `amount`, `eligibilityStatus` = `ELIGIBLE` | `HELD` | `DISPUTED` | `FORFEITED`, `originalEntryId`, `occurredAt`, `holdReason`).

---

## 5. Quy tắc nghiệp vụ

- **BR-46 (Quyền xem & không tự sửa tỷ lệ):** Chỉ Store Owner của cửa hàng mới được xem doanh thu, hoa hồng và quyền lợi cửa hàng; nhân viên cửa hàng (`Store Staff`) và người dùng khác bị từ chối `403 ACCESS_DENIED`. Chủ cửa hàng không thể sửa tỷ lệ hoa hồng.
- **BR-47 (Cơ sở tính hoa hồng):** `PLATFORM_COMMISSION` tính bằng `actualSellingPrice × snapshotCommissionRate` (`order.totalAmount × order.commissionRate`), **không** tính trên `minimumValue` hay giá niêm yết của hộp.
- **BR-48 (Thời điểm ghi nhận hoa hồng):** Chỉ ghi nhận `PLATFORM_COMMISSION` khi đơn đạt trạng thái `COMPLETED`. Đơn `PENDING_PAYMENT`, `EXPIRED`, `CANCELLED` hoặc `NO_SHOW` có hoa hồng bán hàng `= 0`.
- **BR-49 (Quyền lợi cửa hàng):** Đối với đơn hoàn tất: `quyền lợi cửa hàng = giá bán thực tế − hoa hồng + các điều chỉnh hợp lệ` (trong đó điều chỉnh trừ mang dấu âm).
- **BR-50 (Đối soát thanh toán & Truy vết):** Mọi bút toán phân bổ và điều chỉnh đều truy vết qua `FinancialEvent` về đơn hàng (`order_id`) và chứng từ/quyết định gốc (`payment_id`, `refund_id`, `no_show_case_id`, `complaint_id`). Tổng phân bổ hợp lệ của đơn đã kết thúc khớp `collected_verified − refunded_verified`.
- **BR-51 (Hạch toán riêng No-show & Cửa sổ 48h):** `NO_SHOW_COMPENSATION` hạch toán tách biệt khỏi doanh thu bán hàng, hoa hồng `= 0`. Khoản này chỉ đạt trạng thái đủ điều kiện đối soát khi `NoShowCase.compensationStatus == ELIGIBLE` (hết 48h kể từ lúc nhận thông báo và không còn khiếu nại mở/chưa xử lý). Nếu xác định lỗi cửa hàng (`FORFEITED`), ghi `ADJUSTMENT` đảo ngược `NO_SHOW_COMPENSATION` về `0`.
- **BR-52 (Điều chỉnh tài chính chống trùng):** Mọi hoàn tiền/quyết định tranh chấp làm thay đổi doanh thu hoặc hoa hồng phải ghi `LedgerEntry(ADJUSTMENT)` trỏ `original_entry_id` về bút toán gốc, có `business_key` duy nhất chống ghi trùng.
- **NFR-17 & NFR-18 & NFR-19 (Làm tròn, Snapshot & Idempotency):** Mọi phép nhân tỷ lệ dùng `MoneyRounding.applyRate` (`HALF_UP`, nguyên đồng `VND` scale `0`); luôn đọc `totalAmount` & `commissionRate` đã snapshot trên `CustomerOrder`; chạy lại job hoặc replay sự kiện không tạo trùng `FinancialEvent` hay `LedgerEntry`.

---

## 6. Phạm vi

### Trong phạm vi (In-Scope):
1. Migration `V24` (nếu chốt Q1) bổ sung `business_key` duy nhất trên `ledger_entry` và cập nhật entity `LedgerEntry` (giữ nguyên `43` Hibernate entities, không sửa `V1`–`V23`).
2. Dịch vụ hạch toán `LedgerPostingService` + job idempotent rà soát `FinancialEvent` chưa hạch toán:
   - Ghi `PLATFORM_COMMISSION` & `STORE_PAYABLE` cho `ORDER_COMPLETED`.
   - Ghi `NO_SHOW_COMPENSATION` cho `NO_SHOW_CONFIRMED` (hoa hồng `0`, theo dõi trạng thái giữ `HELD`/`DISPUTED` vs `ELIGIBLE`).
   - Ghi `ADJUSTMENT` trỏ `original_entry_id` cho `COMPLAINT_RESOLVED` / `REFUND_SUCCEEDED` hậu giao / `NO_SHOW FORFEITED`.
   - Đảm bảo kiểm chứng cân bằng `Σ phân bổ = tiền thu − tiền hoàn` và chống ghi trùng `business_key`.
3. Dịch vụ & Controller truy vấn cho Store Owner (`UC-47`, `UC-48`): xem doanh thu & hoa hồng, xem khoản cửa hàng được hưởng & khoản đang bị giữ, xem sổ cái theo đơn.
4. Endpoint Admin xem sổ cái đơn & chạy rà soát/đồng bộ sổ cái idempotent.
5. Cập nhật `docs/api/openapi.yaml` và viết bộ integration test TDD đầy đủ (bao gồm test làm tròn `NFR-17`, cân bằng đơn `COMPLETED`/`CANCELLED`/`NO_SHOW`/hậu khiếu nại, chạy job lặp & đồng thời không trùng, phân quyền Owner vs Staff/Customer/Admin).

### Ngoài phạm vi (Out-of-Scope):
1. Quản lý tài khoản nhận tiền cửa hàng theo phiên bản (`StorePayoutAccount`), lập bảng đối soát định kỳ `Settlement` (`DRAFT -> CONFIRMED -> PARTIALLY_PAID -> PAID`), và ghi nhận chi trả `StorePayout` (**T8.4**).
2. Nhật ký hoạt động tổng hợp và biểu đồ/thống kê quản trị toàn sàn theo kỳ/cửa hàng (**T8.5**).
3. Cho phép Store Owner tạo/sửa `FinancialPolicy`, chỉnh tỷ lệ hoa hồng hoặc sửa/xóa dòng `ledger_entry` (bị chặn bởi `BR-46` & trigger `V3`).

---

## 7. Acceptance Criteria (Given / When / Then)

| ID | Given / When / Then |
|---|---|
| **AC-01** | **Given** đơn hàng có giá bán thực tế `totalAmount` khác `minimumValue` (hoặc có tỷ lệ lẻ tạo phân số `.5` đồng như `33,333 × 0.15 = 4,999.95` hoặc `10,005 × 0.15 = 1,500.75` / `10,003 × 0.15 = 1,500.45`), **When** đơn hoàn tất bàn giao (`COMPLETED`), **Then** ghi `PLATFORM_COMMISSION = MoneyRounding.applyRate(totalAmount, commissionRate)` (`HALF_UP` scale `0`) và `STORE_PAYABLE = totalAmount - commission`; tổng `PLATFORM_COMMISSION + STORE_PAYABLE == totalAmount == collected - refunded`. |
| **AC-02** | **Given** đơn hàng `PENDING_PAYMENT`, `EXPIRED` hoặc `CANCELLED` (kể cả đã thanh toán rồi hủy hoàn 100%), **When** kiểm tra sổ cái và chạy job đối soát, **Then** không phát sinh `PLATFORM_COMMISSION` hay `STORE_PAYABLE` (hoa hồng = `0`), và sau khi hoàn tiền `FULFILLED`, `PAYMENT_RECEIPT + CUSTOMER_REFUND == 0`. |
| **AC-03** | **Given** đơn `NO_SHOW` có `NoShowCase(retainedAmount = 18,000, compensationStatus = HELD)` và `refundAmount = 42,000` đã xác nhận hoàn (`CUSTOMER_REFUND = -42,000`), **When** hạch toán sổ cái, **Then** ghi `NO_SHOW_COMPENSATION (+18,000)`, hoa hồng `= 0`, khớp `collected - refunded (60,000 - 42,000 = 18,000)`; khoản `18,000` hiển thị ở mục **khoản đang giữ (`heldAmount`)** và **chưa** đủ điều kiện đối soát (`eligiblePayableAmount = 0`). |
| **AC-04** | **Given** `NoShowCase` ở AC-03 đã nhận thông báo No-show và qua `disputeDeadline` (48h) không có khiếu nại mở (hoặc khiếu nại `NO_SHOW` bị `REJECTED` và đóng), **When** `NoShowCompensationService.releaseEligible` chuyển sang `ELIGIBLE`, **Then** khoản `18,000` chuyển từ `heldAmount` sang `eligiblePayableAmount` (`noShowCompensationEligibleAmount = 18,000`), không sinh trùng `NO_SHOW_COMPENSATION`. |
| **AC-05** | **Given** khiếu nại `NO_SHOW` được giải quyết lỗi cửa hàng (`FORFEITED`) kèm hoàn nốt `18,000` (`FULFILLED`), **When** hạch toán điều chỉnh hoàn tất, **Then** ghi `LedgerEntry(ADJUSTMENT, -18,000)` có `original_entry_id` trỏ đúng `NO_SHOW_COMPENSATION`, tổng phân bổ ròng = `0` khớp `collected - refunded = 0`, `heldAmount = 0`, `eligiblePayableAmount = 0`. |
| **AC-06** | **Given** đơn `COMPLETED` có khiếu nại hậu giao được duyệt hoàn `PARTIAL`/`APPROVED` (kèm điều chỉnh cửa hàng/hoa hồng hoặc trách nhiệm cửa hàng/chia sẻ) và xác nhận hoàn tiền `FULFILLED`, **When** hạch toán `ADJUSTMENT`, **Then** ghi các `LedgerEntry(ADJUSTMENT)` trỏ `original_entry_id` tới `STORE_PAYABLE` và/hoặc `PLATFORM_COMMISSION`, doanh thu ròng & hoa hồng ròng giảm/tăng tương ứng, tổng phân bổ ròng khớp `collected - refunded` theo quy tắc đã chốt ở Q4. |
| **AC-07** | **Given** đơn `COMPLETED` đang có khiếu nại `OPEN`/`INVESTIGATING`/`AWAITING_EVIDENCE`/`DECIDED`, **When** Store Owner xem khoản được nhận (`UC-48`), **Then** quyền lợi của đơn đó được tính vào `heldAmount` (`heldDisputedSalesAmount`), không nằm trong `eligiblePayableAmount` cho tới khi khiếu nại đóng (`CLOSED`). |
| **AC-08** | **Given** job/service hạch toán chạy lặp nhiều lần liên tiếp hoặc chạy đồng thời từ hai luồng trên cùng tập `FinancialEvent`, **When** thực thi với `business_key` duy nhất và khóa dòng/skip-locked, **Then** số lượng `LedgerEntry` không tăng thêm ở lần chạy sau, không bao giờ phát sinh bút toán trùng. |
| **AC-09** | **Given** Store Owner của cửa hàng A, **When** gọi `UC-47` (xem doanh thu & hoa hồng, lọc kỳ/đơn) và `UC-48` (xem khoản được nhận & khoản đang giữ, lọc kỳ/trạng thái) và xem sổ cái đơn của cửa hàng A, **Then** trả `200 OK` đúng số liệu tổng hợp và danh sách truy vết; lọc kỳ không có đơn trả trạng thái rỗng (`0`, `[]`), thao tác GET không làm thay đổi dữ liệu sổ cái. |
| **AC-10** | **Given** `Store Staff` (dù có đủ toàn bộ 6 quyền nhân viên `V7`), `Store Owner` của cửa hàng B, `CUSTOMER`, hoặc request không có token, **When** gọi API doanh thu/quyền lợi cửa hàng A hoặc API Admin ledger, **Then** trả `401` (chưa đăng nhập) hoặc `403 ACCESS_DENIED` đúng kiểm soát quyền server (`BR-46`). |
| **AC-11** | **Given** migration mới (nếu có) và cập nhật `docs/api/openapi.yaml`, **When** chạy Flyway, `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests` và toàn bộ Gradle test suite, **Then** toàn bộ build xanh, không làm vỡ bất kỳ test cũ nào (`NoShowTests`, `ComplaintTests`, `ManualRefundTests`, `NotificationTests`). |

---

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn nghiệp vụ | Phạm vi đáp ứng | AC |
|---|---|---|
| **BR-46** (Trang 30) | Chỉ Store Owner xem tỷ lệ/doanh thu/hoa hồng của mình; không sửa tỷ lệ; chặn Staff/người ngoài | AC-09, AC-10 |
| **BR-47** (Trang 30) | Hoa hồng tính trên giá bán thực tế snapshot (`totalAmount`), không tính trên `minimumValue` / giá niêm yết | AC-01 |
| **BR-48** (Trang 30) | Chỉ ghi nhận hoa hồng khi đơn `COMPLETED`; đơn chờ thanh toán/hủy/`NO_SHOW` hoa hồng `= 0` | AC-01, AC-02, AC-03 |
| **BR-49** (Trang 30) | Quyền lợi cửa hàng = giá bán thực tế − hoa hồng ± điều chỉnh hợp lệ | AC-01, AC-06 |
| **BR-50** (Trang 30) | Đối soát tiền thu − tiền hoàn với hoa hồng, quyền lợi cửa hàng, bù đắp No-show và khoản giữ; truy vết gốc | AC-01, AC-03, AC-05, AC-06, AC-07 |
| **BR-51** (Trang 30) | `NO_SHOW_COMPENSATION` hạch toán riêng, hoa hồng `0`; chỉ đủ điều kiện đối soát sau 48h không tranh chấp; lỗi cửa hàng thì hủy | AC-03, AC-04, AC-05 |
| **BR-52** (Trang 30) | Hoàn tiền/tranh chấp sinh `ADJUSTMENT` gắn `original_entry_id` tránh ghi nhận trùng | AC-05, AC-06, AC-08 |
| **NFR-17..20** (Trang 21) | Quy tắc làm tròn thống nhất (`HALF_UP` VND nguyên đồng), bất biến snapshot, idempotency, lưu vết | AC-01, AC-06, AC-08 |
| **UC-47** (Trang 95–96) | Chủ cửa hàng xem doanh thu, đơn hoàn tất, hoa hồng snapshot, điều chỉnh, bộ lọc kỳ/đơn, trạng thái rỗng | AC-09, AC-10 |
| **UC-48** (Trang 96–97) | Chủ cửa hàng xem quyền lợi bán hàng, số đủ điều kiện chuyển, bù đắp No-show và khoản đang bị giữ | AC-03, AC-04, AC-07, AC-09, AC-10 |
| **CLAUDE.md** §4, 5, 7, 8 | `Clock`, `BigDecimal` VND, `ledger_entry` append-only, unique `business_key`, `FOR UPDATE SKIP LOCKED`, OpenAPI, TDD | AC-01, AC-08, AC-10, AC-11 |

---

## 9. Quyết định cần duyệt tại Gate A (`Q1`–`Q5`)

1. **Q1 (Quy tắc làm tròn `NFR-17` & Bổ sung `business_key` trên `ledger_entry` qua `V24`):**
   - **Làm tròn (`NFR-17`):** Dùng lại chuẩn `MoneyRounding.applyRate(amount, rate)` đã chốt ở T4.5 (`amount × rate`, `RoundingMode.HALF_UP`, nguyên đồng `VND` scale `0`). `storePayable = order.totalAmount - commission` để không phát sinh chênh lệch làm tròn 1đ giữa hoa hồng và quyền lợi cửa hàng.
   - **Chống ghi trùng `ledger_entry` (`business_key`):** Hiện bảng `ledger_entry` (`V1`) chưa có cột `business_key`.
   - *Đề xuất (Recommended):* Tạo migration `V24__ledger_entry_business_key.sql` (giữ nguyên `43` entities, không sửa `V1`–`V23`): thêm cột `business_key varchar(255)` trên `ledger_entry`, backfill cho các dòng cũ nếu có (`'LEGACY:' || id::text`), `SET NOT NULL` và `UNIQUE (business_key)` (đồng thời cập nhật `PaymentConfirmationService` truyền `PAYMENT_RECEIPT:{paymentId}` và `ManualRefundService` truyền `CUSTOMER_REFUND:{refundId}`).
   *(Xác nhận quy tắc làm tròn `HALF_UP` scale `0` và thêm `business_key` `UNIQUE` trên `ledger_entry` qua `V24`?)*

2. **Q2 (Thời điểm ghi bút toán `PLATFORM_COMMISSION` / `STORE_PAYABLE` / `NO_SHOW_COMPENSATION`):**
   - **Phương án A (Recommended — Đồng bộ + Job bù idempotent):**
     - Ghi ngay trong transaction khi `PickupSessionService.completeSession` (`ORDER_COMPLETED` → `PLATFORM_COMMISSION` + `STORE_PAYABLE`) và `NoShowService.confirm` (`NO_SHOW_CONFIRMED` → `NO_SHOW_COMPENSATION` nếu `retainedAmount > 0`).
     - Song song đó có `LedgerPostingService.postPendingEvents` (dùng `FOR UPDATE SKIP LOCKED` + `business_key` + endpoint/job định kỳ) để rà soát và hạch toán bù các `FinancialEvent` cũ chưa có bút toán; chạy lại nhiều lần không bao giờ tạo trùng.
   - **Phương án B (Chỉ ghi qua Job nền / endpoint Admin):** `PickupSessionService` và `NoShowService` không ghi `LedgerEntry`, chỉ đợi job nền chạy mới có số liệu trên `UC-47`/`UC-48`.
   *(Chọn Phương án A để Store Owner thấy số liệu tức thì và vẫn có job idempotent, hay Phương án B?)*

3. **Q3 (Thời điểm ghi `NO_SHOW_COMPENSATION` và cơ chế khoản giữ 48h):**
   - **Phương án A (Recommended — Ghi khi `NO_SHOW_CONFIRMED`, mở khóa `ELIGIBLE` khi qua 48h không tranh chấp):**
     - Ngay khi `NO_SHOW_CONFIRMED` (với `retainedAmount > 0`): ghi `LedgerEntry(NO_SHOW_COMPENSATION, +retainedAmount)` (hoa hồng `= 0`). Nhờ vậy, ngay sau khi hoàn khách phần không giữ (70%), tổng phân bổ của đơn (`+30%`) khớp ngay `collected - refunded`.
     - Trong khi `NoShowCase.compensationStatus IN (HELD, DISPUTED)`: khoản này hiển thị trong `heldAmount` (`UC-48`) và bị khóa khỏi tập đủ điều kiện đối soát cho `T8.4`.
     - Khi `NoShowCase.compensationStatus == ELIGIBLE`: tự động chuyển sang `eligiblePayableAmount` (`UC-48`).
     - Nếu bị kết luận lỗi cửa hàng (`FORFEITED`): ghi `LedgerEntry(ADJUSTMENT, -retainedAmount, originalEntry = NO_SHOW_COMPENSATION)`.
   - **Phương án B (Chỉ ghi `NO_SHOW_COMPENSATION` khi `NoShowCase` đạt `ELIGIBLE`):** Khi còn `HELD`/`DISPUTED` thì chưa có dòng trong `ledger_entry` (tổng phân bổ tạm lệch `collected - refunded` trong 48h đầu), và trường hợp `FORFEITED` trước 48h sẽ không sinh `NO_SHOW_COMPENSATION` lẫn `ADJUSTMENT`.
   *(Chọn Phương án A hay Phương án B?)*

4. **Q4 (Quy tắc sinh `LedgerEntry(ADJUSTMENT)` khi hoàn tiền/giải quyết khiếu nại & cân bằng `Σ = thu − hoàn`):**
   - Trong T8.1, `AdminComplaintService.decide` lưu `outcome`, `responsibility`, `approvedRefundAmount`, `storeAdjustmentAmount`, `commissionAdjustmentAmount`. Cần chốt rõ 2 điểm để không bị lệch sổ cái:
     1) **Thời điểm ghi `ADJUSTMENT`:**
        - *Đề xuất (Recommended):*
          - Nếu quyết định không có `RefundRequest` (`approvedRefundAmount == 0`, tức đóng `CLOSED` ngay lúc `/decide`): ghi `ADJUSTMENT` ngay trong transaction `/decide`.
          - Nếu quyết định có hoàn tiền (`approvedRefundAmount > 0`, `Complaint` ở `DECIDED` chờ chuyển khoản T6.5): chỉ ghi `ADJUSTMENT` (bao gồm cả `NO_SHOW FORFEITED` lẫn điều chỉnh `STORE_PAYABLE`/`PLATFORM_COMMISSION`) **khi `RefundRequest` được `ManualRefundService.confirm` xác nhận `FULFILLED` (`CLOSED`)** (tức cùng thời điểm `LedgerEntry(CUSTOMER_REFUND, -amount)` thực sự được ghi). Nhờ vậy, ở mọi thời điểm (cả trước và sau khi chuyển khoản T6.5), **`Σ bút toán phân bổ` luôn khớp chính xác `collected_verified − refunded_verified`** (trong lúc chờ chi hoàn ở `DECIDED`, đơn vẫn nằm trong `heldAmount` của cửa hàng)!
     2) **Xử lý chênh lệch khi Admin để `storeAdjustmentAmount = 0` và `commissionAdjustmentAmount = 0` trên một quyết định có hoàn tiền (`approvedRefundAmount > 0`) trên đơn `COMPLETED`:**
        - Với `NO_SHOW FORFEITED`: tự động đảo `-retainedAmount` trên `NO_SHOW_COMPENSATION`.
        - Với đơn `COMPLETED`:
          - Nếu Admin đã chỉ định rõ `storeAdjustmentAmount` và/hoặc `commissionAdjustmentAmount` (khác `0`): ghi đúng số Admin đã chốt (`ADJUSTMENT` trỏ `STORE_PAYABLE` và/hoặc `PLATFORM_COMMISSION`).
          - Nếu cả hai đều `0` nhưng `approvedRefundAmount > 0`:
            - Khi `responsibility == STORE`: mặc định tính hoàn trả chia theo tỷ lệ snapshot (`commAdj = -round(refund × commissionRate)`, `storeAdj = -(refund + commAdj)`) hoặc trừ toàn bộ vào cửa hàng?
            - Khi `responsibility == PLATFORM` (nền tảng tự chịu bồi hoàn cho khách, không trừ cửa hàng): trừ vào `PLATFORM_COMMISSION` (`commAdj = -refund`, cho phép hoa hồng ròng đơn đó âm nếu nền tảng bù thêm) để `Σ phân bổ == collected − refunded`, hay giữ `commAdj = 0` (chấp nhận phần nền tảng bù lỗ không nằm trong `PLATFORM_COMMISSION` và tách riêng chỉ tiêu "nền tảng tài trợ" khi kiểm tra cân bằng)?
   *(Xác nhận đề xuất thời điểm ghi `ADJUSTMENT` và quy ước xử lý cho trường hợp mặc định `0` / trách nhiệm `PLATFORM`?)*

5. **Q5 (Thiết kế bộ API cho T8.3):**
   - *Đề xuất (Recommended):*
     - **Store Owner (`GET`, owner-only của `storeId` — chặn `Store Staff` 403):**
       - `GET /api/v1/stores/{storeId}/finance/revenue` (`UC-47` — query `from`, `to`, `orderCode`, `page`, `size` → `StoreRevenueReportView`)
       - `GET /api/v1/stores/{storeId}/finance/entitlements` (`UC-48` — query `from`, `to`, `status` [`ALL`\|`ELIGIBLE`\|`HELD`\|`DISPUTED`], `page`, `size` → `StoreEntitlementReportView`)
       - `GET /api/v1/stores/{storeId}/finance/orders/{orderId}/ledger` (chi tiết bút toán & kiểm chứng cân bằng `collected - refunded` của 1 đơn thuộc cửa hàng)
     - **Admin (`ADMIN`):**
       - `GET /api/v1/admin/finance/orders/{orderId}/ledger` (chi tiết bút toán & cân bằng `collected - refunded` của 1 đơn)
       - `POST /api/v1/admin/finance/ledger/reconcile` (chạy đồng bộ idempotent các `FinancialEvent` chưa hạch toán → `LedgerSyncReportView`)
   - *Lưu ý trạng thái cửa hàng:* Store Owner của cửa hàng `ACTIVE` hoặc `SUSPENDED` đều xem được doanh thu/quyền lợi của mình (chỉ cửa hàng `PENDING_APPROVAL` hoặc `CLOSED` theo rule T3.2 mới không có context — hoặc vẫn cho Owner của cửa hàng `CLOSED` xem lịch sử tiền của mình nếu cần?).
   *(Xác nhận cấu trúc 5 endpoint và quyền xem khi cửa hàng `SUSPENDED`/`CLOSED`?)*

---

## 10. Ràng buộc cho Gate B

- Không viết code sản phẩm hay migration `V24` trước khi Gate A và Gate B được người dùng duyệt.
- Không sửa các migration `V1`–`V23` đã chạy.
- Không chỉnh sửa `ledger_entry` hoặc `financial_event` đã ghi (tuân thủ trigger `V3`); mọi sửa sai chỉ thực hiện bằng `LedgerEntry(ADJUSTMENT)` trỏ `original_entry_id`.
- Không triển khai nghiệp vụ `T8.4` (`StorePayoutAccount`, `Settlement`, `StorePayout`).
- Cập nhật đầy đủ `docs/api/openapi.yaml` và bảo đảm mọi test hiện hữu (`NoShowTests`, `ComplaintTests`, `ManualRefundTests`, `NotificationTests`, `SwaggerUiTests`, `ApiContractTests`, `FoodRescueApplicationTests`, `AuthMigrationTests`) đều xanh.
