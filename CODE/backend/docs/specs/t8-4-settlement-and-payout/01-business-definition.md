# Business Definition: T8.4 — Đối soát và Chi trả Cửa hàng

**Status:** Chờ duyệt Gate A  
**Updated:** 2026-11-27  
**Scope:** T8.4 (`UC-42`, `UC-49`, `UC-80`, `UC-81`, `UC-82`, `UC-83`, `BR-50..52`, `BR-56`, `D05`, `Hình 7.12`, `Hình 8.12`: Chủ cửa hàng quản lý tài khoản nhận tiền theo phiên bản được mã hóa/che thông tin nhạy cảm và xác minh trước khi dùng; Quản trị viên lập bảng đối soát `Settlement(DRAFT)` theo cửa hàng và kỳ đối soát có truy vết từng dòng `SettlementLine` về `LedgerEntry` gốc, tách riêng khoản đang giữ/tranh chấp/ngoại lệ và thể hiện số dư âm là khoản cần thu hồi không tạo lệnh chuyển âm; xác nhận `DRAFT -> CONFIRMED` với kiểm tra dữ liệu không đổi kể từ lúc lập và khóa cứng dòng qua trigger DB; xử lý điều chỉnh sau khi chốt thành dòng mới gắn kỳ/bảng gốc; ghi nhận chi trả thủ công `StorePayout` với mã tham chiếu duy nhất và chứng từ đính kèm chuyển trạng thái `PARTIALLY_PAID`/`PAID`; Chủ cửa hàng theo dõi đối soát/chi trả và Quản trị viên tra cứu lịch sử đối soát & chi trả).  
**Phụ thuộc:** T8.3 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Bối cảnh và mục tiêu

Đến hết **T8.3** ([03-implementation-control.txt](../t8-3-commission-and-revenue/03-implementation-control.txt)), nền tảng đã hạch toán đầy đủ và idempotent các bút toán sổ cái bất biến (`ledger_entry`) cùng API báo cáo doanh thu/quyền lợi cho Chủ cửa hàng:
- Đơn `COMPLETED` sinh `PLATFORM_COMMISSION` và `STORE_PAYABLE` (làm tròn `MoneyRounding` `HALF_UP`, nguyên đồng `VND`).
- Đơn `NO_SHOW` sinh `NO_SHOW_COMPENSATION` (hoa hồng `0`, nằm trong nhóm giữ `HELD`/`DISPUTED` cho tới khi `NoShowCompensationService` mở khóa `ELIGIBLE` sau cửa sổ phản ánh 48h không còn tranh chấp).
- Khiếu nại/hoàn tiền hậu giao hoặc `FORFEITED` sinh `ADJUSTMENT` trỏ `original_entry_id` về bút toán gốc (`STORE_PAYABLE`, `PLATFORM_COMMISSION`, hoặc `NO_SHOW_COMPENSATION`).
- `V1`–`V3` đã dựng sẵn các bảng `store_payout_account`, `settlement`, `settlement_line` (kèm trigger `protect_settlement_line` khóa mọi thay đổi ngoài `DRAFT` và `UNIQUE (ledger_entry_id)`), `store_payout` (kèm `UNIQUE (transfer_reference)` và `CHECK (amount > 0)`), cùng `attachment.payout_id`; `T8.2` đã dựng sẵn `NotificationOutboxService.enqueueSettlement`.

Tuy nhiên, toàn bộ luồng nghiệp vụ **đối soát và chi trả thực tế** ở **T8.4** vẫn chưa được triển khai:
1. Chưa có API/dịch vụ để Chủ cửa hàng khai báo và cập nhật tài khoản nhận tiền theo phiên bản (`UC-42`), chưa có cơ chế mã hóa/che số tài khoản và chưa có bước Admin xác minh tài khoản nhận tiền (`D05`).
2. Chưa có quy trình lập bảng đối soát `Settlement(DRAFT)` (`UC-80`), tách bạch khoản đủ điều kiện vs khoản đang giữ/tranh chấp/ngoại lệ, xử lý số âm thu hồi và điều chỉnh sau kỳ đã chốt (`BR-52`).
3. Chưa có luồng xác nhận bảng đối soát `DRAFT -> CONFIRMED` (`UC-81`) kiểm tra dữ liệu không đổi kể từ lúc lập và cập nhật trạng thái `SETTLED` cho `NoShowCase` đã vào bảng chốt.
4. Chưa có luồng ghi nhận chi trả thủ công `StorePayout` (`UC-82`) kiểm tra chống chi vượt, chống trùng `transfer_reference`, chặn chi khi tài khoản vừa đổi hoặc chưa xác minh, đính kèm chứng từ `attachment.payout_id` và cập nhật trạng thái `PARTIALLY_PAID` / `PAID`.
5. Chưa có API để Chủ cửa hàng theo dõi bảng đối soát & chi trả (`UC-49`) và Quản trị viên xem lịch sử đối soát/chi trả (`UC-83`).

Nguồn nghiệp vụ trích xuất từ `../../DOCX/BaoCaoFoodRescue.pdf`:
- **Trang 30–31 (`BR-50`, `BR-51`, `BR-52`, `BR-56`):** Đối soát tiền thu/hoàn/hoa hồng/bù đắp/đã chi truy về giao dịch hoặc quyết định gốc; No-show chỉ chuyển khi hết 48h không tranh chấp hoặc tranh chấp thắng; điều chỉnh tài chính gắn giao dịch gốc chống trùng; lưu dấu vết người thực hiện và thời điểm.
- **Trang 90–91 (`UC-42`):** Chủ cửa hàng cập nhật tài khoản nhận tiền theo phiên bản; che thông tin nhạy cảm khi không cần thiết; xác minh thay đổi theo `D05`; không ghi đã chi chỉ vì lưu tài khoản; không sửa thông tin của giao dịch đã chi; thay đổi trong lúc chuẩn bị chi phải đối chiếu lại trước `UC-82`.
- **Trang 97–98 (`UC-49`):** Chủ cửa hàng theo dõi bảng đối soát, chênh lệch/điều chỉnh, trạng thái xác nhận, số đã chi, số còn phải chi và tham chiếu thanh toán trong phạm vi cửa hàng của mình.
- **Trang 131–135 (`UC-80`, `UC-81`, `UC-82`, `UC-83`):** Lập bảng đối soát dự thảo truy ngược đến đơn/giao dịch, tách khoản giữ/tranh chấp/ngoại lệ, xử lý số âm thu hồi theo `D05` không tạo lệnh chuyển âm, dòng điều chỉnh mới gắn kỳ gốc; xác nhận đối soát kiểm tra phiên bản dữ liệu/trùng kỳ/khoản giữ; ghi nhận chi trả thủ công (đủ hoặc một phần theo `D05`), chặn khi tài khoản vừa đổi/chưa xác minh hoặc trùng tham chiếu hoặc vượt giới hạn chi; Admin xem lịch sử đối soát và chi trả.
- **Trang 152 (`Hình 7.12`) & Trang 164 (`Hình 8.12`):** Sơ đồ hoạt động và sơ đồ tuần tự Đối soát và chi trả cho cửa hàng.

---

## 2. Khảo sát mã hiện tại

Đường dẫn dưới `src/main/kotlin/com/foodrescue/backend/` và `src/main/resources/`:

1. **Schema & Trigger hiện có (`V1`, `V2`, `V3`, `V24`):**
   - `store_payout_account` (`V1` #L562-579): `id`, `store_id`, `bank_code`, `account_number_ciphertext`, `account_holder_name`, `account_version (>0)`, `verified_at`, `effective_from`, `effective_until`, `UNIQUE (store_id, account_version)`, `UNIQUE (id, store_id)`, `CHECK (effective_until IS NULL OR effective_until > effective_from)`.
     - *Khoảng trống:* Chưa có `key_id` (để xoay khóa mã hóa như `PickupCodeCodec`), chưa có `account_number_masked` (để hiển thị nhanh an toàn khi không giải mã), chưa có `created_by_id` / `verified_by_id` / `verification_note`, và chưa có partial unique index bảo đảm tối đa 1 phiên bản hiện hành (`WHERE effective_until IS NULL`) trên mỗi `store_id`.
   - `settlement` (`V1` #L612-629): `id`, `store_id`, `period_starts_at`, `period_ends_at`, `status IN ('DRAFT', 'CONFIRMED', 'PARTIALLY_PAID', 'PAID')`, `total_amount numeric(19,2)`, `confirmed_by_id`, `confirmed_at`, `previous_settlement_id`, `CHECK (period_ends_at > period_starts_at)`, `CHECK (total_amount = trunc(total_amount))`, `UNIQUE (id, store_id)`.
     - *Khoảng trống:* Chưa có mã định danh thân thiện `settlement_code` (hoặc snapshot chi tiết lúc lập `DRAFT`), chưa lưu `snapshot_fingerprint` / `snapshot_taken_at` / `held_snapshot_json` (để `UC-80` lưu danh sách khoản giữ/ngoại lệ và `UC-81` kiểm tra dữ liệu không đổi kể từ lúc lập), chưa có cột hoặc ràng buộc ngăn 2 bảng `DRAFT` đồng thời cho cùng cửa hàng.
   - `settlement_line` (`V1` #L631-641, `V3` #L15-35): `id`, `store_id`, `settlement_id`, `ledger_entry_id UNIQUE`, `amount numeric(19,2)`, FK composite `(settlement_id, store_id)` & `(ledger_entry_id, store_id)`.
     - Trigger `protect_settlement_line` (`V3` #L15-35) cho phép `INSERT`/`UPDATE`/`DELETE` khi và chỉ khi `settlement.status == 'DRAFT'`, chặn cứng (`SQLSTATE 23514`) mọi thay đổi khi đã `CONFIRMED`/`PARTIALLY_PAID`/`PAID`.
     - *Lưu ý entity:* `SettlementLine.kt` hiện gắn `@Immutable` của Hibernate; cần đánh giá khi rebuild bảng `DRAFT` (xóa dòng bằng JPQL/native SQL trong transaction có khóa `Settlement` rồi `flush`/clear persistence context, hoặc bỏ `@Immutable` vì DB trigger `V3` đã bảo vệ).
   - `store_payout` (`V1` #L643-663): `id`, `store_id`, `settlement_id`, `payout_account_id`, `recipient_snapshot text`, `amount (>0)`, `transfer_reference UNIQUE`, `confirmed_by_id`, `confirmed_at`, `status IN ('PENDING', 'VERIFIED', 'FAILED')`, `CHECK (status <> 'VERIFIED' OR (confirmed_at IS NOT NULL AND confirmed_by_id IS NOT NULL))`.
     - *Khoảng trống:* Chưa có cột `transferred_at` (thời điểm chuyển tiền thực tế bên ngoài ứng dụng theo `UC-82` bước 4), `recorded_by_id` (người tạo lệnh nếu ở `PENDING`), `note` / `failure_reason`.
   - `attachment` (`V1` #L678, `Attachment.kt` #L52-54): Đã có sẵn khóa ngoại `payout_id -> store_payout(id)`.
     - *Khoảng trống:* Chưa có implementation `AttachmentAccessPolicy` cho `attachment.payout != null` trong `FileAccessService.kt`, nên hiện tại chỉ người upload (`ADMIN`) mới tải được chứng từ chi trả còn Store Owner chưa tải được.

2. **Dịch vụ & Mã liên quan hiện hữu:**
   - `finance/entity/{StorePayoutAccount,Settlement,SettlementLine,StorePayout}.kt`, `finance/{SettlementStatus,PayoutStatus}.kt` và các repository `StorePayoutAccountRepository`, `SettlementRepository` (đã có `findForUpdate`), `SettlementLineRepository`, `StorePayoutRepository` (hiện đang là interface trống).
   - `finance/service/StoreFinanceService.kt` (T8.3): Lọc `STORE_PAYABLE` và `NO_SHOW_COMPENSATION`, tính trạng thái giữ `HELD`/`DISPUTED` vs `ELIGIBLE` vs `FORFEITED` vs `SETTLED` (đọc `NoShowCase.compensationStatus` gồm cả `CompensationStatus.SETTLED` đã có sẵn trong `CompensationStatus.kt`).
   - `pickup/service/PickupCodeCodec.kt` & `PickupCodeProperties.kt`: Mẫu chuẩn của repo cho mã hóa `AES/GCM/NoPadding` với key ring nhiều phiên bản từ biến môi trường (`activeKeyId` + `keys` 32-byte Base64).
   - `notification/service/NotificationOutboxService.kt` (`enqueueSettlement`, #L33-34): Đã sẵn sàng phát thông báo `NotificationType.SETTLEMENT` qua outbox T8.2.
   - `store/service/StoreHistoryRecorder.kt`: Đã hỗ trợ ghi `audit_log` gắn `store_id` trong transaction nghiệp vụ.
   - `payment/service/ManualRefundService.kt`: Mẫu đối chiếu cho xác nhận chuyển khoản thủ công (`transferReference` duy nhất, `evidenceFileId` gắn `Attachment`, `transferredAt`, khóa bi quan, idempotency/conflict).

3. **Baseline kiểm thử xác minh trên workspace (sau T8.3):**
   - Lệnh: `.\gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain` → **BUILD SUCCESSFUL**.
   - Kết quả từ `build/test-results/test/TEST-*.xml`: **59** suites, `349` tests = **348 PASS / 0 FAIL / 0 ERROR / 1 SKIPPED** (ca skip có sẵn `SwaggerUiContextPathTests :: hold disposable browser sandbox until stop signal()`).
   - Hiện trạng repo: **24** migrations (`V1`–`V24`), **43** Hibernate entities (`StorePayoutAccount`, `Settlement`, `SettlementLine`, `StorePayout` đã nằm trong 43 entity), **126** OpenAPI paths / **31** tags (`SwaggerUiTests.kt`).

---

## 3. Người dùng và quyền

- **Chủ cửa hàng (`Store Owner`):**
  - Xác thực JWT + kiểm tra quyền owner-only qua `StoreAuthorizationService.requirePermission(actorId, storeId, "STORE_PROFILE_MANAGE")` (hoặc kiểm tra sở hữu cửa hàng).
  - Xem lịch sử phiên bản và cập nhật tài khoản nhận tiền của cửa hàng mình (`UC-42`); số tài khoản luôn được che ở chế độ xem thông thường (`****1234`), không được tự xác minh tài khoản nhận tiền, không được tự sửa thông tin của giao dịch đã chi.
  - Xem danh sách & chi tiết bảng đối soát, dòng bút toán, khoản giữ/điều chỉnh, các lần chi trả, chứng từ chi trả (`Attachment`) thuộc cửa hàng mình (`UC-49`).
  - Không được tạo/xác nhận bảng đối soát hay ghi nhận chi trả; thao tác xem chỉ đọc (`readOnly`).
- **Nhân viên cửa hàng (`Store Staff` — kể cả có đủ 6 quyền `V7`), Chủ cửa hàng khác, Khách hàng (`CUSTOMER`):**
  - Toàn bộ endpoint tài khoản nhận tiền, đối soát, chi trả và chứng từ chi trả của cửa hàng khác → **403 `ACCESS_DENIED`**.
- **Quản trị viên (`ADMIN`):**
  - `@PreAuthorize("hasRole('ADMIN')")`.
  - Xem & xác minh tài khoản nhận tiền phiên bản hiện hành của cửa hàng (`D05`, đối chiếu thông tin người nhận khi chi trả).
  - Lập/cập nhật lại bảng đối soát `DRAFT` (`UC-80`), xác nhận bảng `CONFIRMED` (`UC-81`), ghi nhận/xác minh/đánh dấu thất bại khoản chi trả `StorePayout` (`UC-82`), và tra cứu lịch sử đối soát/chi trả toàn hệ thống có bộ lọc (`UC-83`).
- **Chưa đăng nhập / token không hợp lệ:** **401 `UNAUTHORIZED`**.

---

## 4. Luồng chính

### 4.1. Quản lý & Xác minh Tài khoản Nhận tiền theo Phiên bản (`UC-42`, `D05`)
1. **Cập nhật tài khoản nhận tiền (`Store Owner`):**
   - Owner gửi `bankCode`, `accountNumber`, `accountHolderName` cho `storeId`.
   - Hệ thống khóa dòng `Store` (`findForUpdate`), kiểm tra đầu vào bắt buộc (chuẩn hóa, độ dài hợp lệ, không chứa ký tự điều khiển).
   - Mã hóa `accountNumber` bằng `AES/GCM/NoPadding` (AAD gắn `storeId:accountVersion`), tạo chuỗi che `accountNumberMasked` (giữ 4 ký tự cuối, ví dụ `****6789`).
   - Đóng phiên bản hiệu lực cũ (`effectiveUntil = now` nếu có), tạo bản ghi `StorePayoutAccount` mới với `accountVersion = maxVersion + 1`, `effectiveFrom = now`, `effectiveUntil = null`, `verifiedAt = null` (chưa xác minh), lưu người tạo và thời điểm (`BR-56`).
   - Toàn bộ các `StorePayout` đã chi trước đó vẫn trỏ vào `payout_account_id` cũ và `recipient_snapshot` cũ, tuyệt đối không bị sửa theo.
   - Không ghi nhận chi trả hay tạo lệnh chuyển tiền khi cập nhật tài khoản.
2. **Xác minh tài khoản nhận tiền (`ADMIN`, `D05`):**
   - Admin kiểm tra thông tin phiên bản hiện hành (`effectiveUntil IS NULL`), thực hiện xác minh (`verifiedAt = now`, `verifiedBy = admin`, `verificationNote`).
   - Nếu có phiên bản mới được Owner cập nhật sau khi Admin vừa mở kiểm tra (đối chiếu bằng `expectedVersion`), từ chối xác minh nhầm (`409 CONFLICT`) để Admin tải lại phiên bản mới nhất (`UC-42 E2`).
   - Phát thông báo `NotificationOutboxService.enqueueSettlement(...)` báo Owner tài khoản nhận tiền đã được xác minh.

### 4.2. Lập bảng Đối soát Dự thảo `DRAFT` (`UC-80`, `BR-50..52`)
1. **Chọn phạm vi & Khóa đồng bộ:**
   - Admin chọn `storeId` và kỳ đối soát `[periodStartsAt, periodEndsAt)` (ràng buộc `periodEndsAt > periodStartsAt`).
   - Hệ thống khóa `Store` (`findForUpdate`) để tuần tự hóa thao tác lập/xác nhận đối soát trên cùng cửa hàng.
   - Nếu cửa hàng đã có bảng `DRAFT` (trùng kỳ hoặc đang mở — xem `Q2`), hỗ trợ rebuild lại `DRAFT` hiện tại thay vì sinh nhiều bảng `DRAFT` chồng chéo hoặc chặn `409` nếu xung đột kỳ đã chốt.
2. **Phân loại bút toán & Tách khoản giữ / tranh chấp / ngoại lệ:**
   - Quét toàn bộ `LedgerEntry` của `storeId` có `type IN ('STORE_PAYABLE', 'NO_SHOW_COMPENSATION', 'ADJUSTMENT' trỏ vào 'STORE_PAYABLE'/'NO_SHOW_COMPENSATION')`, `occurredAt < periodEndsAt` (gồm cả bút toán trong kỳ và các bút toán `ELIGIBLE` / `ADJUSTMENT` tồn đọng từ kỳ trước chưa vào bảng `Settlement` nào), **loại trừ** các bút toán đã nằm trong `settlement_line` của bảng khác (`UNIQUE (ledger_entry_id)`).
   - Loại ngay các bút toán chưa đủ điều kiện vào nhóm **Khoản đang giữ / Tranh chấp / Ngoại lệ** (tính snapshot để hiển thị đối chiếu, **không** đưa vào `settlement_line`):
     - `NO_SHOW_COMPENSATION` (và các `ADJUSTMENT` liên quan) khi `NoShowCase.compensationStatus IN ('HELD', 'DISPUTED')` hoặc còn khiếu nại mở (`OPEN`, `INVESTIGATING`, `AWAITING_EVIDENCE`, `DECIDED`).
     - `STORE_PAYABLE` (và các `ADJUSTMENT` liên quan) của đơn `COMPLETED` đang có khiếu nại mở (`OPEN`, `INVESTIGATING`, `AWAITING_EVIDENCE`, `DECIDED`).
     - Đơn hàng trong kỳ đã phát sinh `FinancialEvent` nhưng chưa hạch toán đủ `LedgerEntry` hoặc `balanced == false` (ngoại lệ cần rà soát `LedgerPostingService` trước khi chốt — `UC-80 E1`).
   - Các bút toán đủ điều kiện (thuộc `[periodStartsAt, periodEndsAt)` hoặc tồn đọng chưa đối soát trước `periodEndsAt`, không bị giữ/tranh chấp, đơn cân bằng) được đưa vào `SettlementLine(settlement, ledgerEntry, amount)`:
     - Mỗi dòng truy vết rõ `ledgerEntryId`, `orderId`, `orderCode`, `eventType`, `entryType`, `originalEntryId`, và nếu là `ADJUSTMENT` cho một bút toán gốc đã đối soát ở kỳ trước thì liên kết tới bảng đối soát gốc (`originalSettlementId` / `previousSettlement`) theo `UC-80 A2` và `BR-52`.
3. **Tính tổng & Lưu dự thảo:**
   - `totalAmount = SUM(settlement_line.amount)`.
   - Nếu `totalAmount < 0` (`UC-80 E2`): vẫn hiển thị đúng giá trị âm ở `totalAmount`, tách `payableAmount = 0` và `recoveryAmount = totalAmount.abs()` (trạng thái cần thu hồi theo `D05`, **không** tạo lệnh chi âm).
   - Lưu `Settlement(status = DRAFT)`, danh sách `SettlementLine`, `snapshotFingerprint` (hash bất biến của tập `ledgerEntryId:amount`, danh sách giữ/ngoại lệ và điều chỉnh tại thời điểm lập) và `heldExceptionsSnapshot` để `UC-81` kiểm tra.

### 4.3. Xác nhận Đối soát `DRAFT -> CONFIRMED` (`UC-81`, `BR-50..52`)
1. Admin gọi xác nhận bảng đối soát `settlementId`.
2. Hệ thống khóa `Store` rồi khóa `Settlement` (`findForUpdate`):
   - Nếu đã `CONFIRMED` / `PARTIALLY_PAID` / `PAID`: theo `UC-81 E2` trả về trạng thái hiện tại (idempotent, không tạo bảng hay khoản chi thứ hai) hoặc chặn nếu gọi kèm trạng thái không hợp lệ.
   - **Kiểm tra dữ liệu không đổi kể từ lúc lập (`UC-81` bước 4 & `E1`):** Tính lại tập bút toán đủ điều kiện, trạng thái khiếu nại/no-show của các đơn liên quan và trạng thái ngoại lệ trong kỳ; so sánh `fingerprint` với lúc lập `DRAFT`. Nếu bất kỳ dòng nào vừa bị chuyển sang tranh chấp/giữ, vừa phát sinh `ADJUSTMENT` mới, hoặc có thay đổi số liệu → **từ chối chốt (`409 CONFLICT`)**, yêu cầu Admin cập nhật lại bảng dự thảo (`UC-80`).
   - Nếu có ngoại lệ chưa xử lý (`hasUnresolvedExceptions == true` hoặc `balanced == false` trên đơn thuộc bảng) → từ chối chốt (`409 CONFLICT`, `UC-80 E1` / `UC-81 E1`).
3. Chuyển `status = CONFIRMED`, ghi `confirmedBy = admin`, `confirmedAt = now`. DB trigger `protect_settlement_line` (`V3`) tự động bảo vệ mọi `settlement_line` không thể sửa/xóa từ thời điểm này.
4. Với các `NoShowCase` có `NO_SHOW_COMPENSATION` nằm trong `settlement_line` (trừ trường hợp đã `FORFEITED`), cập nhật `compensationStatus = CompensationStatus.SETTLED`.
5. Ghi `AuditLog` (`SETTLEMENT_CONFIRMED`) và `NotificationOutboxService.enqueueSettlement(...)` gửi Owner (nhấn mạnh trạng thái đã xác nhận đối soát, **chưa đồng nghĩa đã chi trả** — `UC-81` bước 6).
6. Nếu sau thời điểm `CONFIRMED` phát sinh khiếu nại/hoàn tiền mới sinh `LedgerEntry(ADJUSTMENT)`: bảng đã chốt giữ nguyên bất biến; `ADJUSTMENT` đó sẽ được lấy vào bảng đối soát kỳ sau với liên kết tới bảng gốc (`UC-81 A2`, `BR-52`).

### 4.4. Ghi nhận Chi trả Thủ công `StorePayout` (`UC-82`, `D05`)
1. **Tiền điều kiện & Kiểm tra tài khoản nhận tiền (`UC-82 E1`):**
   - Hệ thống khóa `Store` rồi khóa `Settlement` (`findForUpdate`).
   - `settlement.status` phải là `CONFIRMED` hoặc `PARTIALLY_PAID`, và `settlement.totalAmount > 0` (`remainingEligibleAmount > 0`).
   - Truy vấn phiên bản tài khoản nhận tiền hiện hành (`effectiveUntil IS NULL`) của cửa hàng:
     - Nếu cửa hàng chưa có tài khoản nhận tiền hoặc `verifiedAt == null` ("tài khoản nhận vừa đổi hoặc chưa xác minh") → **chặn chi trả (`409 CONFLICT`)**.
     - Nếu Admin chỉ định `payoutAccountId` khác phiên bản hiện hành hoặc không khớp `expectedAccountVersion` của phiên bản đang hiệu lực (ví dụ Owner vừa đổi sang phiên bản mới trong lúc Admin chuẩn bị chuyển tiền theo `UC-42 E2` & `UC-82 E1`) → **chặn chi trả (`409 CONFLICT`)**, yêu cầu đối chiếu lại thông tin người nhận.
2. **Kiểm tra giới hạn chi & Chống trùng (`UC-82 E2`):**
   - `amount` phải nguyên đồng `VND` và `> 0`.
   - Tính `alreadyCommitted = SUM(store_payout.amount WHERE settlement_id = :id AND status IN ('PENDING', 'VERIFIED'))`.
   - Nếu `amount > settlement.totalAmount - alreadyCommitted` → **từ chối (`400`/`409`)**, không chi vượt số phải chi. (Chi một phần `< remaining` được phép theo `UC-82 A2` & `D05`).
   - `transferReference` (đã trim, không rỗng) phải duy nhất toàn hệ thống (`store_payout.transfer_reference UNIQUE`) → trùng trả `409 CONFLICT`.
   - Bắt buộc `evidenceFileId` hợp lệ (tồn tại trong `StoredFile` do `ADMIN` tải lên) khi ghi nhận chi trả `VERIFIED`, liên kết vào `Attachment(file, payout = payout)`.
3. **Trạng thái khoản chi & Cập nhật trạng thái bảng đối soát (`UC-82 A1`, `A2`):**
   - Ghi `StorePayout` kèm `recipientSnapshot` (JSON bất biến chụp `accountVersion`, `bankCode`, `accountNumberMasked`, `accountHolderName`, `verifiedAt`).
   - Hỗ trợ ghi nhận trực tiếp `VERIFIED` (mặc định khi Admin đã có mã giao dịch & chứng từ chuyển khoản thành công) hoặc `PENDING` (`UC-82 A1`: lệnh chuyển đang chờ ngân hàng xác nhận, chưa tính vào `paidAmount`, có API `verify` / `fail` để hoàn tất hoặc hủy giữ chỗ).
   - Sau khi `VERIFIED`: tính lại `verifiedPaidAmount = SUM(amount WHERE status = 'VERIFIED')`:
     - Nếu `verifiedPaidAmount == settlement.totalAmount` → `settlement.status = PAID`.
     - Nếu `0 < verifiedPaidAmount < settlement.totalAmount` → `settlement.status = PARTIALLY_PAID`.
   - Ghi `AuditLog` (`PAYOUT_RECORDED` / `PAYOUT_VERIFIED` / `PAYOUT_FAILED`) và phát thông báo `NotificationOutboxService.enqueueSettlement(...)` cho `store.owner` khi `VERIFIED`.

### 4.5. Tra cứu Đối soát và Chi trả (`UC-49`, `UC-83`)
- **Chủ cửa hàng (`UC-49`):** Xem danh sách & chi tiết bảng đối soát của cửa hàng mình (lọc theo kỳ, trạng thái), thấy rõ số tổng quyền lợi, điều chỉnh, khoản đang giữ, khoản thu hồi âm (nếu có), trạng thái xác nhận, tổng đã chi, số còn phải chi, các giao dịch `StorePayout` (`VERIFIED`/`PENDING`/`FAILED`), mã tham chiếu và tải được chứng từ đính kèm (`Attachment`).
- **Quản trị viên (`UC-83`):** Xem lịch sử bảng đối soát và chi trả trên toàn hệ thống, lọc theo `storeId`, khoảng thời gian kỳ/xác nhận, `status`, thấy đầy đủ người xác nhận, dòng đối soát, điều chỉnh liên quan, từng lần chi trả và tham chiếu. Thao tác xem hoàn toàn `readOnly`, không sửa trạng thái.

---

## 5. Quy tắc nghiệp vụ

- **BR-50 (Đối soát & Truy vết):** Mọi dòng `SettlementLine` phải trỏ 1-1 tới `LedgerEntry` (`UNIQUE (ledger_entry_id)`), truy ngược được đến `CustomerOrder` và `FinancialEvent` gốc. Không một bút toán nào được đưa vào hai bảng đối soát khác nhau.
- **BR-51 (Khoản giữ & No-show):** `NO_SHOW_COMPENSATION` chỉ vào `SettlementLine` khi `NoShowCase.compensationStatus == ELIGIBLE` và không có khiếu nại mở; khi đã chốt `CONFIRMED`, `NoShowCase.compensationStatus` chuyển `SETTLED`. Các khoản `HELD`/`DISPUTED` (cả No-show và đơn hoàn tất đang có khiếu nại chưa `CLOSED`) phải tách riêng, tuyệt đối không đưa vào bảng chốt chi.
- **BR-52 (Điều chỉnh sau chốt & Chống ghi trùng):** Bảng đối soát đã `CONFIRMED` bị khóa cứng dòng (`V3 protect_settlement_line`). Mọi `ADJUSTMENT` phát sinh sau khi kỳ gốc đã chốt sẽ đi vào bảng đối soát mới dưới dạng dòng mới gắn về bảng gốc (`previousSettlement` / `originalSettlementId`).
- **D05 (Xác minh tài khoản nhận tiền, Thu hồi số âm & Chi một phần):**
  - Tài khoản nhận tiền lưu theo phiên bản bất biến; chỉ Admin xác minh xong mới được dùng để chi trả; mọi thay đổi phiên bản tài khoản làm mất hiệu lực xác minh cho phiên bản mới và chặn chi trả nếu chưa đối chiếu/xác minh lại (`UC-42 E2`, `UC-82 E1`).
  - Tổng đối soát âm thể hiện là số cần thu hồi (`recoveryAmount`), **tuyệt đối không** sinh `StorePayout` âm (`UC-80 E2`, `CHECK (amount > 0)`).
  - Cho phép chi nhiều lần / chi một phần (`PARTIALLY_PAID` → `PAID`) nhưng tổng các khoản chi hiệu lực (`PENDING` + `VERIFIED`) không bao giờ vượt số phải chi còn lại (`UC-82 A2`, `E2`).
  - Mỗi `transfer_reference` chỉ được dùng một lần (`UNIQUE`, `UC-82 E2`).
- **BR-56 & NFR-12/13 (Bảo mật & Dấu vết):** Số tài khoản ngân hàng được mã hóa AES-GCM khi lưu DB và che khi hiển thị thông thường (`UC-42`). Chỉ Store Owner (không phải Staff) và Admin được truy cập đúng phạm vi quyền (`403 ACCESS_DENIED`). Mọi thao tác cập nhật tài khoản, xác minh, chốt đối soát và chi trả đều dùng `Clock` và ghi `AuditLog`.

---

## 6. Phạm vi

### Trong phạm vi (In-Scope):
1. Migration `V25` bổ sung cột cần thiết trên `store_payout_account`, `settlement`, `store_payout` (mã hóa keyId/masked, verifiedBy/createdBy, snapshot fingerprint/held JSON, transferredAt/recordedBy/note, partial unique index tài khoản hiệu lực), **không** sửa `V1`–`V24` và **giữ nguyên 43 Hibernate entities**.
2. Mã hóa & quản lý phiên bản `StorePayoutAccount` (`UC-42`, `D05`) + Admin xác minh tài khoản nhận tiền.
3. Dịch vụ lập/rebuild `Settlement(DRAFT)`, kiểm tra bất biến dữ liệu và xác nhận `CONFIRMED` (`UC-80`, `UC-81`), cập nhật `NoShowCase.compensationStatus = SETTLED`.
4. Dịch vụ ghi nhận chi trả `StorePayout` (`VERIFIED` / `PENDING` + `verify`/`fail`), kiểm tra tài khoản mới đổi/chưa xác minh, giới hạn chi, trùng tham chiếu, đính kèm chứng từ `Attachment(payout)` + `PayoutAttachmentPolicy` trong `FileAccessService` (`UC-82`).
5. API cho Store Owner (`UC-42`, `UC-49`) và Admin (`UC-80..83`), ghi `AuditLog` qua `StoreHistoryRecorder` và đẩy thông báo `NotificationOutboxService.enqueueSettlement`.
6. Cập nhật `docs/api/openapi.yaml` và viết bộ integration test TDD (`SettlementAndPayoutTests.kt`) phủ đầy đủ toàn bộ AC, đồng thời giữ xanh toàn bộ test suite hiện có.

### Ngoài phạm vi (Out-of-Scope):
1. Giao diện/API xem nhật ký hoạt động tổng hợp toàn sàn (`audit_log` viewer) và thống kê doanh thu/đối soát tổng hợp theo sàn cho Admin (**T8.5**).
2. Chi trả tự động qua cổng ngân hàng / payOS payout (`D9` — `roadmap-3-9.md` #L33).
3. Sửa đổi `V1`–`V24` hay trigger bảo vệ lịch sử `V3`.

---

## 7. Acceptance Criteria (Given / When / Then)

| ID | Given / When / Then |
|---|---|
| **AC-01** | **Given** Store Owner của cửa hàng A, **When** tạo mới hoặc cập nhật tài khoản nhận tiền (`bankCode`, `accountNumber`, `accountHolderName`), **Then** phiên bản cũ được đóng (`effectiveUntil = now`), phiên bản mới (`accountVersion + 1`, `verifiedAt = null`) được tạo với số tài khoản mã hóa AES-GCM trong DB và response chỉ trả số đã che (`****xxxx`); các `StorePayout` cũ không bị thay đổi. |
| **AC-02** | **Given** phiên bản tài khoản nhận tiền mới của cửa hàng A ở trạng thái chưa xác minh, **When** Admin thực hiện xác minh (đúng `expectedVersion`), **Then** `verifiedAt`, `verifiedBy` được lưu, phát thông báo cho Owner; nếu Owner vừa cập nhật phiên bản mới hơn làm lệch `expectedVersion` thì trả `409 CONFLICT`. |
| **AC-03** | **Given** cửa hàng A có bút toán `STORE_PAYABLE` (đơn `COMPLETED` không khiếu nại mở), `NO_SHOW_COMPENSATION` (đã `ELIGIBLE`), cùng 1 đơn `NO_SHOW` đang `HELD`/`DISPUTED` và 1 đơn `COMPLETED` có khiếu nại đang `OPEN`/`DECIDED`, **When** Admin lập bảng đối soát `DRAFT`, **Then** chỉ các bút toán đủ điều kiện vào `settlement_line`, các khoản giữ/tranh chấp bị tách vào danh sách ngoại lệ/khoản giữ (`totalAmount` không cộng tiền đang giữ), và mỗi `settlement_line` truy vết đúng `ledgerEntryId` và `orderId`. |
| **AC-04** | **Given** một `LedgerEntry` đã nằm trong `settlement_line` của bảng đối soát kỳ 1 (`DRAFT` hoặc `CONFIRMED`), **When** Admin lập bảng đối soát kỳ 2 chồng lấn thời gian hoặc chứa lại giao dịch đó, **Then** bút toán đó không bao giờ được đưa vào bảng thứ hai (`UNIQUE (ledger_entry_id)` & logic lọc loại trừ). |
| **AC-05** | **Given** tổng `settlement_line` của bảng đối soát bị âm (ví dụ có `ADJUSTMENT` thu hồi lớn hơn quyền lợi mới trong kỳ), **When** lập/xác nhận bảng đối soát và khi thử ghi chi trả, **Then** bảng hiển thị `totalAmount < 0`, `payableAmount = 0`, `recoveryAmount > 0` (cần thu hồi theo `D05`), không tạo lệnh chi âm và mọi yêu cầu ghi `StorePayout` đều bị từ chối (`409`). |
| **AC-06** | **Given** bảng `DRAFT` đã lập, trong lúc chờ xác nhận thì có đơn trong bảng phát sinh khiếu nại mới (chuyển bị giữ) hoặc phát sinh `ADJUSTMENT` mới trong kỳ, **When** Admin gọi xác nhận bảng `CONFIRMED`, **Then** hệ thống phát hiện dữ liệu thay đổi kể từ lúc lập và từ chối chốt (`409 CONFLICT`), giữ `DRAFT` để Admin cập nhật kiểm tra lại trước khi chốt. |
| **AC-07** | **Given** bảng `DRAFT` hợp lệ dữ liệu không đổi và không còn ngoại lệ chặn chốt, **When** Admin xác nhận `CONFIRMED`, **Then** lưu `confirmedBy`, `confirmedAt`, DB trigger `V3` khóa cứng `settlement_line` (mọi `INSERT`/`UPDATE`/`DELETE` sau đó bị chặn), `NoShowCase` liên quan chuyển `SETTLED`, phát thông báo `SETTLEMENT_CONFIRMED` cho Owner; gọi lại `/confirm` không tạo bảng thứ hai. |
| **AC-08** | **Given** bảng kỳ 1 đã `CONFIRMED`, sau đó phát sinh hoàn tiền/khiếu nại đóng tạo `LedgerEntry(ADJUSTMENT)` cho đơn thuộc kỳ 1, **When** Admin lập bảng đối soát kỳ tiếp theo, **Then** bảng kỳ 1 giữ nguyên bất biến; `ADJUSTMENT` mới xuất hiện dưới dạng `settlement_line` mới ở bảng mới và liên kết truy về bảng đối soát kỳ 1 (`previousSettlement` / `originalSettlementId`). |
| **AC-09** | **Given** bảng `CONFIRMED` với `totalAmount = 100,000` và cửa hàng có tài khoản nhận tiền chưa xác minh hoặc Owner vừa đổi tài khoản (hoặc Admin truyền `payoutAccountId`/`expectedAccountVersion` cũ), **When** Admin ghi nhận chi trả `StorePayout`, **Then** hệ thống từ chối (`409 CONFLICT`) theo `UC-82 E1`. |
| **AC-10** | **Given** bảng `CONFIRMED` với `totalAmount = 100,000` và tài khoản nhận tiền hiện hành đã xác minh, **When** Admin ghi nhận chi trả `40,000` (kèm `transferReference`, `transferredAt`, `evidenceFileId`) rồi chi tiếp `60,000`, **Then** bảng chuyển `CONFIRMED -> PARTIALLY_PAID -> PAID`; nếu gửi trùng `transferReference` hoặc gửi số tiền vượt số còn lại (kể cả trong 2 luồng đồng thời) hoặc vượt sau khi có `PENDING` giữ chỗ → từ chối `409`/`400`. Khoản `PENDING` có thể xác minh thành `VERIFIED` hoặc hủy thành `FAILED` giải phóng hạn mức chi. |
| **AC-11** | **Given** Owner của cửa hàng A và Admin, **When** Owner xem danh sách/chi tiết đối soát & tải chứng từ chi trả của cửa hàng A (`UC-49`) và Admin lọc lịch sử đối soát/chi trả (`UC-83`), **Then** trả `200 OK` đúng số liệu và ảnh/file chứng từ; `Store Staff` (đủ 6 quyền), Owner khác, `CUSTOMER` gọi API cửa hàng A hoặc chứng từ chi trả bị chặn `403 ACCESS_DENIED`; chưa đăng nhập trả `401`. |
| **AC-12** | **Given** migration `V25` và cập nhật `docs/api/openapi.yaml`, **When** chạy Flyway, `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests` và toàn bộ Gradle test suite, **Then** build xanh hoàn toàn, không làm vỡ bất kỳ test cũ nào (`CommissionAndRevenueTests`, `NoShowTests`, `ComplaintTests`, `ManualRefundTests`, `NotificationTests`). |

---

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn nghiệp vụ | Phạm vi đáp ứng | AC |
|---|---|---|
| **UC-42** (Trang 90–91) | Quản lý tài khoản nhận tiền theo phiên bản, che số TK nhạy cảm, xác minh `D05`, không sửa lệnh chi cũ, đổi lúc chuẩn bị chi phải đối chiếu lại | AC-01, AC-02, AC-09 |
| **UC-49** (Trang 97–98) | Store Owner xem đối soát, chênh lệch/điều chỉnh, đã chi, còn phải chi, tham chiếu & chứng từ đúng phạm vi | AC-11 |
| **UC-80** (Trang 131–132) | Admin lập `DRAFT`, truy vết từng dòng, tách giữ/tranh chấp/ngoại lệ, điều chỉnh kỳ sau gắn kỳ gốc, số âm thu hồi không tạo lệnh chuyển âm | AC-03, AC-04, AC-05, AC-08 |
| **UC-81** (Trang 132–133) | Xác nhận `CONFIRMED` khóa dòng, kiểm tra dữ liệu không đổi lúc chốt, idempotent bấm lặp, chưa đồng nghĩa đã chi | AC-06, AC-07 |
| **UC-82** (Trang 133–134) | Chi trả thủ công (đủ/một phần/`PENDING`), chặn tài khoản mới đổi/chưa xác minh, chống trùng tham chiếu & chi vượt | AC-09, AC-10 |
| **UC-83** (Trang 134–135) | Admin xem lịch sử đối soát/chi trả theo cửa hàng, kỳ, trạng thái (chỉ đọc) | AC-11 |
| **BR-50..52** (Trang 30) | Truy vết dòng đối soát về giao dịch gốc; No-show đủ điều kiện mới vào bảng và chuyển `SETTLED`; điều chỉnh sau chốt thành dòng mới gắn gốc | AC-03, AC-04, AC-07, AC-08 |
| **BR-56** (Trang 31) | Dấu vết người thực hiện, thời điểm và nội dung cho thay đổi tài khoản nhận tiền, xác minh, chốt đối soát, chi trả | AC-01, AC-02, AC-07, AC-10 |
| **D05** & **Hình 7.12 / 8.12** | Xác minh tài khoản trước chi, xử lý số âm thu hồi, chi một phần được phép | AC-02, AC-05, AC-09, AC-10 |

---

## 9. Quyết định cần duyệt tại Gate A (`Q1`–`Q6`)

1. **Q1 (Migration `V25` & Bảo mật/Phiên bản `StorePayoutAccount`):**
   - Hiện bảng `store_payout_account`, `settlement`, `store_payout` (`V1`) thiếu các cột audit/mã hóa/snapshot như mô tả ở Mục 2.
   - *Đề xuất (Recommended):* Tạo **1 migration `V25__settlement_and_payout_details.sql`** (không sửa `V1`–`V24`, giữ nguyên **43** Hibernate entities):
     - `store_payout_account`: thêm `key_id varchar(64)`, `account_number_masked varchar(64)`, `created_by_id uuid`, `verified_by_id uuid`, `verification_note text`, và partial unique index `uq_store_payout_account_active ON store_payout_account(store_id) WHERE effective_until IS NULL`. Dùng `AES/GCM/NoPadding` với key ring cấu hình riêng `finance.payout-account.*` (hoặc tái dùng cơ chế cấu hình env tương tự `pickup.code.*` kèm AAD `storeId:accountVersion`); API mặc định chỉ trả `accountNumberMasked` (e.g. `****6789`).
     - `settlement`: thêm `settlement_code varchar(64) UNIQUE`, `snapshot_fingerprint varchar(128)`, `snapshot_taken_at timestamptz`, `held_exceptions_snapshot text`, và partial unique index `uq_settlement_draft_per_store ON settlement(store_id) WHERE status = 'DRAFT'` (tại một thời điểm mỗi cửa hàng có tối đa 1 bảng dự thảo `DRAFT` đang soạn).
     - `store_payout`: thêm `transferred_at timestamptz`, `recorded_by_id uuid`, `failure_reason text`, `note text`.
   *(Xác nhận tạo `V25` với các bổ sung trên và dùng key cấu hình `finance.payout-account.*` tách biệt với `pickup.code.*`?)*

2. **Q2 (Phạm vi gom bút toán vào `DRAFT`, Quy ước kỳ `[periodStartsAt, periodEndsAt)` & Điều chỉnh sau chốt):**
   - **Phương án A (Recommended — Quét toàn bộ bút toán đủ điều kiện `occurred_at < periodEndsAt` chưa đối soát):**
     - Admin truyền `periodStartsAt < periodEndsAt`. Kỳ đầu khuyến nghị khớp mốc đầu-cuối, nhưng để không bao giờ bỏ sót một giao dịch của kỳ trước vừa mở khóa (ví dụ No-show hết 48h ở kỳ sau, khiếu nại mới `CLOSED` sinh `ADJUSTMENT`, hoặc đơn có `occurredAt` thuộc trước `periodStartsAt` nhưng trước đó bị `HELD` chưa vào bảng nào), hệ thống sẽ gom **mọi bút toán cửa hàng (`STORE_PAYABLE`, `NO_SHOW_COMPENSATION`, `ADJUSTMENT` gắn quyền lợi cửa hàng) có `occurred_at < periodEndsAt` chưa nằm trong bất kỳ `settlement_line` nào** và đã hết giữ/tranh chấp (`UC-80` + `UC-81 A2`), đồng thời tách các khoản còn `HELD`/`DISPUTED` (trong hoặc trước `periodEndsAt`) vào danh sách giữ/ngoại lệ để Admin đối chiếu.
     - Nếu `DRAFT` đã tồn tại cho cửa hàng đó (chỉ 1 `DRAFT` theo `Q1`): nếu gọi lập/rebuild lại `DRAFT`, hệ thống cập nhật lại `periodStartsAt`/`periodEndsAt` và thay thế tập `settlement_line` (được `V3` trigger cho phép khi `DRAFT`) thay vì tạo `DRAFT` trùng.
   - **Phương án B (Chỉ lọc cứng `periodStartsAt <= occurred_at < periodEndsAt`):** Các đơn vừa hết 48h giữ hoặc `ADJUSTMENT` gắn bút toán kỳ trước có nguy cơ bị sót nếu kỳ mới đặt `periodStartsAt` sát giờ.
   *(Chọn Phương án A hay Phương án B?)*

3. **Q3 (Cơ chế kiểm tra "dữ liệu không đổi kể từ lúc lập" khi xác nhận `CONFIRMED` — `UC-81`):**
   - *Đề xuất (Recommended):* Khi lập/rebuild `DRAFT`, tính toán một chuỗi `snapshot_fingerprint` (SHA-256 trên tập `(ledgerEntryId, amount)` đủ điều kiện + danh sách `(orderId, holdStatus/unsettledReason)` của các khoản giữ/ngoại lệ trong kỳ tại `periodEndsAt`). Khi Admin gọi `/confirm`: khóa `Store` & `Settlement`, chạy lại truy vấn tính ứng viên đối soát với cùng `periodEndsAt` và kiểm tra:
     1) Không có đơn ngoại lệ `balanced == false` hoặc `FinancialEvent` chưa hạch toán trong phạm vi;
     2) Tập dòng hiện tại đủ điều kiện + tập giữ/ngoại lệ cho ra `fingerprint` **trùng khớp 100%** với `snapshot_fingerprint` của `DRAFT`. Nếu lệch (có khiếu nại mới, có `NoShow` vừa đổi trạng thái, có `ADJUSTMENT`/đơn mới trong kỳ) → trả `409 CONFLICT` yêu cầu rebuild `DRAFT` trước khi `CONFIRMED`.
   *(Xác nhận cơ chế `snapshot_fingerprint` + re-check lúc `/confirm`?)*

4. **Q4 (Xử lý bảng đối soát có tổng âm `total_amount < 0` — `UC-80 E2`, `D05`):**
   - *Đề xuất (Recommended):* Cho phép lập và xác nhận `Settlement` có `totalAmount < 0` để phản ánh công nợ cần thu hồi (`recoveryAmount = -totalAmount`, `payableAmount = 0`), chặn `409` mọi thao tác tạo `StorePayout` trên bảng này. Tuy nhiên, cần chốt cách thu hồi ở kỳ tiếp theo khi cửa hàng có doanh thu dương mới:
     - **Lựa chọn 4A (Recommended — Không tự động giữ lại âm ở bảng đã chốt; theo dõi qua `previousSettlement` + hiển thị `recoveryNeeded`):** Bảng âm kết thúc ở trạng thái `CONFIRMED` (với `totalAmount < 0`), và kỳ kế tiếp có thể tham chiếu `previousSettlementId`; số cần chi thực tế kỳ sau nếu muốn trừ nợ cũ cần một quyết định/bút toán `ADJUSTMENT` hay **cấn trừ số dư âm của kỳ trước chưa thu hồi (`carriedOverDebt`)** vào `totalAmount` kỳ sau? Lưu ý trong `V1`, `settlement.total_amount` khớp với `SUM(settlement_line.amount)` hay có trừ thêm `previous_settlement` âm? Nếu `total_amount = SUM(settlement_line.amount)` thì bảng sau chỉ lấy các `ledger_entry` mới; nếu muốn trừ tự động bảng âm kỳ trước mà vẫn giữ `total_amount` minh bạch thì bảng âm kỳ trước vẫn là `CONFIRMED` và ta chỉ hiển thị `carriedOverRecoveryAmount` để Admin đối chiếu hoặc ghi rõ ở `payout_eligible_amount = max(totalAmount - openRecoveryFromPrevious, 0)` (hoặc đơn giản hơn: nếu để `DRAFT` kỳ trước ở lại cho đến khi bù đủ, hay giữ `CONFIRMED` và tính số thực chi tối đa kỳ sau?).
     - Đề xuất rõ nhất giữ đúng `V1` (`total_amount` bảng nào bằng tổng dòng bảng đó) và an toàn tài chính: **Kỳ có `totalAmount < 0` vẫn `CONFIRMED` (để khóa dòng đối soát kỳ đó, hiển thị `recoveryAmount = -totalAmount`, chặn mọi chi trả trên bảng đó); ở các kỳ sau có liên kết `previousSettlement`, Admin/hệ thống có thể tra cứu `openRecoveryBalance` từ các bảng âm trước chưa bù để cấn trừ vào hạn mức được chi thực tế (`netPayableAfterRecovery`), hoặc lập `DRAFT` mới gộp dòng kỳ tiếp theo nếu chưa chốt.**
   *(Xác nhận giữ `totalAmount` = tổng `settlement_line` và cấn trừ hạn mức chi từ bảng âm `previousSettlement` chưa thu hồi?)*

5. **Q5 (Quy trình ghi nhận chi trả `StorePayout` & Kiểm tra tài khoản nhận tiền — `UC-82`):**
   - *Đề xuất (Recommended):*
     - Hỗ trợ 2 chế độ khi Admin gọi `POST /api/v1/admin/finance/settlements/{settlementId}/payouts`: truyền `status = VERIFIED` (mặc định khi chuyển khoản thành công, bắt buộc `evidenceFileId` & `transferredAt` & `transferReference`) hoặc `status = PENDING` (`UC-82 A1`: đang chờ xác nhận ngân hàng), kèm `POST /api/v1/admin/finance/payouts/{payoutId}/verify` và `/fail`.
     - Chống đổi đích chuyển tiền ngầm (`UC-42 E2`, `UC-82 E1`): request chi trả bắt buộc gửi `payoutAccountId` và `expectedAccountVersion`; chỉ chấp nhận khi đúng phiên bản hiện hành (`effectiveUntil IS NULL`), đã được xác minh (`verifiedAt != null`). (Nếu tài khoản mới cập nhật `effectiveFrom > settlement.confirmedAt`, Admin vẫn phải lấy phiên bản đã xác minh mới nhất và truyền đúng `payoutAccountId` + `expectedAccountVersion` mới thì mới chi được; nếu vẫn dùng ID/version cũ hoặc bản mới chưa verify → trả `409`).
     - Thêm `PayoutAttachmentPolicy` vào `FileAccessService` để `ADMIN` và `payout.store.owner` tải được file chứng từ chi trả.
   *(Xác nhận luồng `StorePayout` với `VERIFIED`/`PENDING` + `verify`/`fail` và ràng buộc `payoutAccountId` + `expectedAccountVersion`?)*

6. **Q6 (Bộ API Endpoints & Quy ước phản hồi chuẩn):**
   - **Store Owner (`/api/v1/stores/{storeId}/finance/...`):**
     - `GET /payout-accounts` (phiên bản hiện hành + lịch sử, luôn che số TK)
     - `PUT /payout-account` (tạo phiên bản mới chưa xác minh)
     - `GET /settlements` (phân trang, lọc `from`, `to`, `status`)
     - `GET /settlements/{settlementId}` (chi tiết bảng, `lines` truy về đơn/bút toán gốc, `heldExceptions`, `payouts`, số còn phải chi)
   - **Admin (`/api/v1/admin/finance/...`):**
     - `GET /stores/{storeId}/payout-accounts` (xem lịch sử & bản hiện hành để xác minh/đối chiếu; cho phép Admin xem số TK đầy đủ khi cần đối chiếu chuyển khoản qua endpoint/trường chuyên biệt hay che mặc định và giải mã có kiểm soát? Đề xuất: endpoint danh sách trả masked, kèm `fullAccountNumber` chỉ ở response chi tiết đối chiếu cho `ADMIN` hoặc lúc verify/payout để tuân thủ `UC-42` bước 2 "che khi không cần thiết" và `UC-82` bước 2 "hiển thị thông tin nhận tiền để đối chiếu chuyển tiền")
     - `POST /stores/{storeId}/payout-accounts/{accountId}/verify`
     - `POST /settlements/draft`
     - `POST /settlements/{settlementId}/confirm`
     - `POST /settlements/{settlementId}/payouts`
     - `POST /payouts/{payoutId}/verify` và `POST /payouts/{payoutId}/fail`
     - `GET /settlements` và `GET /settlements/{settlementId}` (`UC-83`: lọc `storeId`, `from`, `to`, `status`)
   *(Xác nhận cấu trúc API trên và việc Admin xem được thông tin người nhận đầy đủ khi đối chiếu chi trả còn Owner/danh sách chỉ xem số đã che?)*
