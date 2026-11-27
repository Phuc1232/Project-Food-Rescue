# Technical Plan: T8.4 — Đối soát và Chi trả Cửa hàng

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A đã được người dùng duyệt `Q1`–`Q6` ngày 2026-11-27.  
**Status:** Hoàn tất triển khai và kiểm chứng full build ngày 2026-11-27
**Updated:** 2026-11-27  
**Scope:** T8.4 (`UC-42`, `UC-49`, `UC-80..83`, `BR-50..52`, `BR-56`, `D05`, `Hình 7.12 / 8.12`: quản lý & xác minh tài khoản nhận tiền cửa hàng theo phiên bản được mã hóa AES-GCM và che số tài khoản; lập/rebuild bảng đối soát `Settlement(DRAFT)` truy về `LedgerEntry` gốc, tách khoản giữ/tranh chấp/ngoại lệ, hiển thị số dư âm thu hồi không tạo lệnh chuyển âm; kiểm tra bất biến dữ liệu khi chốt `CONFIRMED` khóa dòng qua `V3` trigger và cập nhật `NoShowCase.SETTLED`; xử lý điều chỉnh sau chốt gắn bảng gốc; ghi nhận chi trả thủ công `StorePayout` với chống trùng `transfer_reference`, chống chi vượt, chặn tài khoản mới đổi/chưa xác minh, chứng từ `Attachment(payout)` + `PayoutAttachmentPolicy`; API theo dõi cho Store Owner và lịch sử cho Admin).  
**Phụ thuộc:** T8.3 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Quyết định đã duyệt tại Gate A và Baseline

1. **Các quyết định Gate A đã chốt (`Q1`–`Q6`):**
   - **Q1 (`V25` & Bảo mật `StorePayoutAccount`):** Tạo migration `V25__settlement_and_payout_details.sql` (không sửa `V1`–`V24`, giữ nguyên **43** Hibernate entities): bổ sung `key_id`, `account_number_masked`, `created_by_id`, `verified_by_id`, `verification_note` + partial unique index `uq_store_payout_account_active` (`WHERE effective_until IS NULL`) trên `store_payout_account`; `settlement_code`, `snapshot_fingerprint`, `snapshot_taken_at`, `held_exceptions_snapshot` + partial unique index `uq_settlement_draft_per_store` (`WHERE status = 'DRAFT'`) trên `settlement`; `transferred_at`, `recorded_by_id`, `failure_reason`, `note` trên `store_payout`. Mã hóa `AES/GCM/NoPadding` bằng key ring cấu hình riêng `finance.payout-account.*` (`PAYOUT_ACCOUNT_ACTIVE_KEY_ID`, `PAYOUT_ACCOUNT_KEYS`).
   - **Q2 (Phạm vi gom bút toán & Rebuild `DRAFT`):** Gom toàn bộ bút toán quyền lợi cửa hàng (`STORE_PAYABLE`, `NO_SHOW_COMPENSATION`, `ADJUSTMENT` trỏ về `STORE_PAYABLE`/`NO_SHOW_COMPENSATION`) có `occurred_at < periodEndsAt` chưa nằm trong bất kỳ `settlement_line` nào và đã hết giữ/tranh chấp (để không sót khoản vừa mở khóa 48h hoặc `ADJUSTMENT` từ kỳ trước). Hỗ trợ rebuild lại trên bảng `DRAFT` duy nhất của cửa hàng (`UC-81 A1`).
   - **Q3 (Kiểm tra dữ liệu không đổi khi chốt `CONFIRMED`):** Khi lập `DRAFT` lưu `snapshot_fingerprint` (SHA-256 trên tập dòng đủ điều kiện + tập giữ/ngoại lệ). Khi gọi `/confirm`: khóa `Store` & `Settlement`, tính lại tập ứng viên và kiểm tra không có ngoại lệ chưa xử lý + `snapshot_fingerprint` khớp 100%; nếu lệch trả `409 CONFLICT` (`UC-81 E1`).
   - **Q4 (Tổng âm `totalAmount < 0` & Điều chỉnh sau chốt):** Cho phép chốt `CONFIRMED` bảng có `totalAmount < 0` (`recoveryAmount = -totalAmount`, `payableAmount = 0`), chặn `409` mọi `StorePayout`. Hỗ trợ liên kết `previousSettlement` và hiển thị khoản nợ kỳ trước chưa thu hồi (`carriedOverRecoveryAmount`) khi đối chiếu/tính hạn mức chi thực tế.
   - **Q5 (Quy trình `StorePayout` & Chứng từ):** Hỗ trợ ghi chi trả `VERIFIED` (mặc định, bắt buộc `transferredAt` + `evidenceFileId`) hoặc `PENDING` (`UC-82 A1`) kèm `/verify` và `/fail`. Yêu cầu `payoutAccountId` + `expectedAccountVersion` phải khớp đúng phiên bản hiện hành (`effectiveUntil IS NULL`) đã xác minh (`verifiedAt != null`); `PENDING + VERIFIED` giữ chỗ hạn mức chi; `transferReference` duy nhất (`409`); thêm `PayoutAttachmentPolicy` vào `FileAccessService`.
   - **Q6 (API Endpoints & Che thông tin):** Tách `/api/v1/stores/{storeId}/finance/...` (Owner-only `STORE_PROFILE_MANAGE`, che số tài khoản) và `/api/v1/admin/finance/...` (`ADMIN`, xem thông tin người nhận đầy đủ khi đối chiếu chi trả ở màn hình chi tiết/verify/payout).
2. **Baseline:**
   - `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`: **BUILD SUCCESSFUL** (`24` migrations `V1`–`V24`, `43` Hibernate entities, `126` OpenAPI paths, `31` tags; `59` suites, **348 PASS / 1 SKIPPED / 0 FAIL / 0 ERROR**).

---

## 2. Thiết kế chi tiết

### 2.1. Migration `V25__settlement_and_payout_details.sql`, Entities & Repositories

- **Migration `V25`:**
  ```sql
  -- T8.4: Audit/encryption/snapshot details for payout accounts, settlements and manual payouts.
  ALTER TABLE store_payout_account
      ADD COLUMN key_id varchar(64),
      ADD COLUMN account_number_masked varchar(64),
      ADD COLUMN created_by_id uuid REFERENCES account(id),
      ADD COLUMN verified_by_id uuid REFERENCES account(id),
      ADD COLUMN verification_note text;

  CREATE UNIQUE INDEX uq_store_payout_account_active
      ON store_payout_account (store_id)
      WHERE effective_until IS NULL;

  ALTER TABLE settlement
      ADD COLUMN settlement_code varchar(64),
      ADD COLUMN snapshot_fingerprint varchar(128),
      ADD COLUMN snapshot_taken_at timestamptz,
      ADD COLUMN held_exceptions_snapshot text;

  CREATE UNIQUE INDEX uq_settlement_code
      ON settlement (settlement_code)
      WHERE settlement_code IS NOT NULL;

  CREATE UNIQUE INDEX uq_settlement_draft_per_store
      ON settlement (store_id)
      WHERE status = 'DRAFT';

  ALTER TABLE store_payout
      ADD COLUMN transferred_at timestamptz,
      ADD COLUMN recorded_by_id uuid REFERENCES account(id),
      ADD COLUMN failure_reason text,
      ADD COLUMN note text;

  CREATE INDEX idx_settlement_store_status ON settlement (store_id, status, period_ends_at DESC);
  CREATE INDEX idx_store_payout_settlement_status ON store_payout (settlement_id, status, created_at DESC);
  ```
  *Lưu ý tương thích ngược:* Tất cả cột mới đều nullable trong DDL và có giá trị mặc định `= null` ở cuối constructor Kotlin của entity (`StorePayoutAccount`, `Settlement`, `StorePayout`) để giữ nguyên **43** Hibernate entities và tương thích hoàn toàn với reflection fixture trong [`FoodRescueApplicationTests.kt`](../../src/test/kotlin/com/foodrescue/backend/FoodRescueApplicationTests.kt).

- **Cập nhật Entities:**
  - [`StorePayoutAccount.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/entity/StorePayoutAccount.kt): thêm cuối constructor `keyId: String? = null`, `accountNumberMasked: String? = null`, `createdBy: Account? = null`, `verifiedBy: Account? = null`, `verificationNote: String? = null`.
  - [`Settlement.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/entity/Settlement.kt): thêm cuối constructor `settlementCode: String? = null`, `snapshotFingerprint: String? = null`, `snapshotTakenAt: Instant? = null`, `heldExceptionsSnapshot: String? = null`.
  - [`StorePayout.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/entity/StorePayout.kt): thêm cuối constructor `transferredAt: Instant? = null`, `recordedBy: Account? = null`, `failureReason: String? = null`, `note: String? = null`.
  - [`SettlementLine.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/entity/SettlementLine.kt): giữ nguyên cấu trúc bảng `settlement_line`; trong dịch vụ rebuild bảng `DRAFT`, thực hiện xóa dòng bằng `NamedParameterJdbcTemplate` (`DELETE FROM settlement_line WHERE settlement_id = :id` sau khi khóa `Settlement` ở `DRAFT` — được DB trigger `V3 protect_settlement_line` cho phép) và `entityManager.clear()` / reload trước khi chèn tập dòng mới.

- **Repositories:**
  - [`StorePayoutAccountRepository.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/repository/StorePayoutAccountRepository.kt):
    - `findFirstByStoreIdAndEffectiveUntilIsNull(storeId: UUID): StorePayoutAccount?`
    - `findAllByStoreIdOrderByAccountVersionDesc(storeId: UUID): List<StorePayoutAccount>`
    - `findByIdAndStoreId(id: UUID, storeId: UUID): StorePayoutAccount?`
    - `@Query("select coalesce(max(a.accountVersion), 0) from StorePayoutAccount a where a.store.id = :storeId") fun findMaxVersion(storeId: UUID): Int`
  - [`SettlementRepository.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/repository/SettlementRepository.kt):
    - Giữ `findForUpdate(id: UUID): Settlement?`
    - `findFirstByStoreIdAndStatus(storeId: UUID, status: SettlementStatus): Settlement?`
    - `findByIdAndStoreId(id: UUID, storeId: UUID): Settlement?`
    - `findFirstByStoreIdAndStatusNotOrderByPeriodEndsAtDescCreatedAtDesc(storeId: UUID, status: SettlementStatus): Settlement?`
    - `existsBySettlementCode(code: String): Boolean`
  - [`SettlementLineRepository.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/repository/SettlementLineRepository.kt):
    - `findAllBySettlementIdOrderByCreatedAtAscIdAsc(settlementId: UUID): List<SettlementLine>`
    - `findAllByLedgerEntryIdIn(ledgerEntryIds: Collection<UUID>): List<SettlementLine>`
  - [`StorePayoutRepository.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/repository/StorePayoutRepository.kt):
    - `findAllBySettlementIdOrderByCreatedAtAscIdAsc(settlementId: UUID): List<StorePayout>`
    - `existsByTransferReference(transferReference: String): Boolean`
    - `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from StorePayout p where p.id = :id") fun findForUpdate(id: UUID): StorePayout?`

### 2.2. Mã hóa & Quản lý Phiên bản Tài khoản Nhận tiền (`PayoutAccountProperties`, `PayoutAccountCodec`, `PayoutAccountService`)

- **Cấu hình [`PayoutAccountProperties.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/PayoutAccountProperties.kt) (`@ConfigurationProperties("finance.payout-account")`):**
  - `activeKeyId: String = ""` (`PAYOUT_ACCOUNT_ACTIVE_KEY_ID`)
  - `keys: String = ""` (`PAYOUT_ACCOUNT_KEYS`, format `id1:base64_32bytes,id2:base64_32bytes`, chuẩn 256-bit AES giống [`PickupCodeProperties.kt`](../../src/main/kotlin/com/foodrescue/backend/pickup/PickupCodeProperties.kt)).
  - Thêm vào [`application.yml`](../../src/main/resources/application.yml), [`.env.example`](../../.env.example), và `TestAuthKeys.configure` trong [`AuthTestFixtures.kt`](../../src/test/kotlin/com/foodrescue/backend/support/AuthTestFixtures.kt).
- **[`PayoutAccountCodec.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/service/PayoutAccountCodec.kt):**
  - `encrypt(storeId: UUID, accountVersion: Int, accountNumber: String): String`: `AES/GCM/NoPadding`, IV 12 bytes ngẫu nhiên (`SecureRandom`), AAD = `"$storeId:$accountVersion"`.
  - `decrypt(storeId: UUID, accountVersion: Int, keyId: String, encrypted: String): String`: kiểm tra keyId trong `keyRing`, giải mã với cùng AAD (nếu Admin cần xem số tài khoản để thực hiện chuyển khoản thủ công ở `UC-82`).
  - `mask(accountNumber: String): String`: giữ 4 ký tự cuối, các ký tự trước thay bằng `*` (ví dụ `1234567890` → `******7890`, độ dài tối thiểu 6 ký tự).
- **[`PayoutAccountService.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/service/PayoutAccountService.kt):**
  - **Store Owner (`STORE_PROFILE_MANAGE`):**
    - `listForOwner(actorId, storeId)`: trả `PayoutAccountHistoryView(activeAccount, versions)` trong đó **chỉ có `accountNumberMasked`**, tuyệt đối không giải mã/trả số tài khoản đầy đủ.
    - `upsertForOwner(actorId, storeId, request)`:
      - Validate `bankCode` (2..50 ký tự chữ/số/gạch ngang, upper-trimmed), `accountNumber` (6..34 ký tự số/chữ hợp lệ không khoảng trắng thừa), `accountHolderName` (2..255 ký tự, trim).
      - Khóa `Store` (`stores.findForUpdate(storeId)`).
      - Đóng bản hiện hành nếu có (`active.effectiveUntil = now`, `saveAndFlush`) để nhường partial unique index `WHERE effective_until IS NULL`.
      - Tạo phiên bản mới `accountVersion = maxVersion + 1`, `verifiedAt = null`, `verifiedBy = null`, `effectiveFrom = now`, `keyId = codec.activeKeyId`, `accountNumberMasked = codec.mask(normalizedNumber)`, `accountNumberCiphertext = codec.encrypt(...)`, `createdBy = actorAccount`.
      - Ghi `StoreHistoryRecorder.record(actorAccount, store, "STORE_PAYOUT_ACCOUNT", saved.id!!, "PAYOUT_ACCOUNT_UPDATED", beforeMasked, afterMasked)` (che số tài khoản trong audit log, không lưu plaintext số tài khoản vào `audit_log`).
  - **Admin (`ADMIN`):**
    - `listForAdmin(storeId)`: trả danh sách lịch sử phiên bản ở dạng **che số tài khoản** (`accountNumberMasked` — tuân thủ `UC-42` bước 2 "che thông tin nhạy cảm khi không cần thiết").
    - `revealActiveForAdminPayout(storeId)`: endpoint/chi tiết riêng cho Admin đối chiếu chuyển khoản (`UC-82` bước 2) trả bản hiện hành gồm `accountNumber` đã giải mã kèm `accountVersion`, `verifiedAt`, ghi `AuditLog` (`PAYOUT_ACCOUNT_REVEALED`) để truy vết mọi lần đọc số tài khoản đầy đủ.
    - `verify(adminId, storeId, accountId, request)`:
      - Khóa `Store` (`findForUpdate`). Tìm `account = accountsRepo.findByIdAndStoreId(accountId, storeId)` (`404` nếu sai store).
      - Kiểm tra `account.effectiveUntil == null` (nếu không phải phiên bản hiện hành → `409 CONFLICT` `"Payout account version is superseded"`).
      - Kiểm tra `request.expectedVersion?.let { if (it != account.accountVersion) throw conflict(...) }` (`UC-42 E2`: đồng thời vừa có thay đổi mới).
      - Nếu đã `verifiedAt != null` → idempotent trả view hiện tại (không ghi trùng).
      - Ghi `account.verifiedAt = now`, `account.verifiedBy = adminAccount`, `account.verificationNote = request.note?.trim()`.
      - Ghi `StoreHistoryRecorder.record(...)` (`PAYOUT_ACCOUNT_VERIFIED`) và `NotificationOutboxService.enqueueSettlement(store.owner, "PAYOUT_ACCOUNT_VERIFIED", ...)`.

### 2.3. Lập, Rebuild & Xác nhận Đối soát (`SettlementService`)

- **Phân loại & Tính ứng viên đối soát (dùng chung cho `createOrRebuildDraft` và re-check tại `confirm`):**
  1. Khóa `Store` (`stores.findForUpdate(storeId)`).
  2. Kiểm tra `periodEndsAt > periodStartsAt` (`400 VALIDATION_FAILED`).
  3. Tìm mọi `LedgerEntry` của `storeId` có `occurred_at < periodEndsAt`, `type IN ('STORE_PAYABLE', 'NO_SHOW_COMPENSATION', 'ADJUSTMENT')`, **chưa có trong `settlement_line`** (chú ý khi đang rebuild bảng `DRAFT` hiện tại `existingDraftId`, loại trừ các `settlement_line` của chính `existingDraftId` khỏi điều kiện "đã thuộc bảng khác" để có thể đánh giá lại toàn bộ!).
  4. Lọc riêng `ADJUSTMENT`: chỉ giữ `ADJUSTMENT` có `originalEntry.type IN ('STORE_PAYABLE', 'NO_SHOW_COMPENSATION')` (bỏ qua `COMMISSION_ADJ` thuộc nền tảng).
  5. Với từng `orderId` liên quan tới các bút toán ứng viên (cùng các đơn có `FinancialEvent(occurredAt < periodEndsAt)` trong kỳ của cửa hàng):
     - Kiểm tra đơn có đang có `Complaint` ở trạng thái mở (`OPEN`, `INVESTIGATING`, `AWAITING_EVIDENCE`, `DECIDED`) hay không.
     - Nếu là `NO_SHOW`: kiểm tra `NoShowCase.compensationStatus`:
       - `HELD` / `DISPUTED` (hoặc có khiếu nại mở) → tách vào **`heldItems`** (`NO_SHOW_HELD` / `OPEN_DISPUTE`), **không** vào `settlement_line`.
       - `ELIGIBLE` (không khiếu nại mở) → đưa `NO_SHOW_COMPENSATION` (và mọi `ADJUSTMENT` gắn nó) vào **`eligibleLines`**.
       - `FORFEITED` và khiếu nại đã `CLOSED`: cả `NO_SHOW_COMPENSATION` (nếu chưa chốt kỳ trước) + `ADJUSTMENT(NO_SHOW_FORFEIT)` (nếu chưa chốt) đưa vào **`eligibleLines`** (net `= 0` để khép sổ, hoặc nếu gốc đã chốt kỳ trước thì chỉ `ADJUSTMENT` âm đi vào kỳ này làm khoản thu hồi/cấn trừ!).
     - Nếu là `COMPLETED`:
       - Nếu có khiếu nại mở (`OPEN`, `INVESTIGATING`, `AWAITING_EVIDENCE`, `DECIDED`) → toàn bộ bút toán chưa chốt của đơn đó (`STORE_PAYABLE` / `ADJUSTMENT`) tách vào **`heldItems`** (`OPEN_COMPLAINT`), **không** vào `settlement_line`.
       - Nếu không có khiếu nại mở → kiểm tra đơn có cân bằng sổ cái (`OrderLedgerService.view(orderId).balanced`) không:
         - Nếu chưa có `STORE_PAYABLE` dù có `ORDER_COMPLETED`, hoặc `!balanced` (lệch sổ cái chưa hạch toán đủ) → đưa vào **`unresolvedExceptions`** (`LEDGER_IMBALANCE` / `MISSING_LEDGER_ENTRY`), không chốt được bảng nếu còn ngoại lệ này (`UC-80 E1` / `UC-81 E1`).
         - Nếu hợp lệ → đưa vào **`eligibleLines`**.
  6. Tìm các đơn có `FinancialEvent` thuộc `type IN ('ORDER_COMPLETED', 'NO_SHOW_CONFIRMED', 'COMPLAINT_RESOLVED')` với `occurredAt < periodEndsAt` nhưng chưa có `LedgerEntry` tương ứng (trừ các complaint `REJECTED`/`DECIDED` chờ hoàn tiền) → bổ sung vào **`unresolvedExceptions`**.
  7. Tạo `snapshotFingerprint = SHA-256` từ chuỗi chuẩn hóa (deterministic, sắp xếp theo `id`) của:
     - `eligibleLines`: `ledgerEntryId:amount`
     - `heldItems`: `orderId:ledgerEntryId:reason:amount`
     - `unresolvedExceptions`: `orderId:code:detail`
- **Tạo / Rebuild `DRAFT` (`createOrRebuildDraft(adminId, request)`):**
  - Kiểm tra không cho phép tạo mới nếu kỳ mới chồng lấn hoặc `periodEndsAt` sớm hơn bảng đã chốt gần nhất nếu đang có `DRAFT` xung đột; nếu cửa hàng đang có 1 `Settlement(DRAFT)` (`findFirstByStoreIdAndStatus`), rebuild trực tiếp trên `DRAFT` đó (xóa các `settlement_line` cũ bằng `jdbc.update("DELETE FROM settlement_line WHERE settlement_id = :id", ...)` rồi chèn lại tập `eligibleLines` mới); nếu chưa có, sinh `settlementCode = "STL-..."` duy nhất và tìm `previousSettlement` là bảng đã chốt (`CONFIRMED`/`PARTIALLY_PAID`/`PAID`) gần nhất của `storeId` (hoặc bảng gốc chứa `originalEntry` của các `ADJUSTMENT` nằm trong bảng này).
  - Tính `totalAmount = eligibleLines.sumOf { it.amount }.setScale(0)`.
  - Lưu `snapshotFingerprint`, `snapshotTakenAt = clock.instant()`, `heldExceptionsSnapshot` (JSON của `heldItems` + `unresolvedExceptions`).
  - Ghi `StoreHistoryRecorder.record(...)` (`SETTLEMENT_DRAFT_SAVED`).
- **Xác nhận `DRAFT -> CONFIRMED` (`confirm(adminId, settlementId)`):**
  - Khóa `Store` rồi khóa `Settlement` (`findForUpdate`).
  - Nếu `settlement.status IN (CONFIRMED, PARTIALLY_PAID, PAID)`: trả ngay `SettlementDetailView` hiện tại (`UC-81 E2`, idempotent).
  - Chạy lại bước tính ứng viên với cùng `periodEndsAt` (bỏ qua dòng của chính `settlement.id`):
    - Nếu `unresolvedExceptions.isNotEmpty()` → throw `ApiException(409, "CONFLICT", "Settlement has unresolved exceptions and cannot be confirmed")` (`UC-80 E1`).
    - Nếu `currentFingerprint != settlement.snapshotFingerprint` → throw `ApiException(409, "CONFLICT", "Settlement underlying data changed since draft creation; rebuild draft before confirming")` (`UC-81 E1`).
  - Cập nhật `status = SettlementStatus.CONFIRMED`, `confirmedBy = admin`, `confirmedAt = clock.instant()`, `saveAndFlush`.
  - Với mọi `LedgerEntry(NO_SHOW_COMPENSATION)` nằm trong `settlement_line` có `NoShowCase.compensationStatus == CompensationStatus.ELIGIBLE`, cập nhật `compensationStatus = CompensationStatus.SETTLED`.
  - Ghi `StoreHistoryRecorder.record(...)` (`SETTLEMENT_CONFIRMED`) và `NotificationOutboxService.enqueueSettlement(store.owner, "SETTLEMENT_CONFIRMED", ...)` (nội dung nêu rõ đã xác nhận đối soát, chưa đồng nghĩa đã chi trả).

### 2.4. Ghi nhận Chi trả Thủ công `StorePayout` (`StorePayoutService` & `PayoutAttachmentPolicy`)

- **`recordPayout(adminId, settlementId, request)`:**
  - Khóa `Store (findForUpdate)` → `Settlement (findForUpdate)`.
  - Validate `settlement.status IN (CONFIRMED, PARTIALLY_PAID)` (nếu `DRAFT` hoặc `PAID` → `409 CONFLICT`).
  - Validate `settlement.totalAmount > BigDecimal.ZERO` (nếu `<= 0` → `409 CONFLICT` `"Negative or zero settlement cannot be paid out; recovery required"` — `UC-80 E2`).
  - Tính công nợ thu hồi kỳ trước chưa bù từ chuỗi `previousSettlement` (nếu `request.applyRecoveryOffset` hoặc theo rule `Q4`: tính `effectivePayableCap = max(0, settlement.totalAmount - openRecoveryFromPrevious)` hoặc báo rõ nếu cố chi vượt `payableAmount`; chi tiết cụ thể trong code đảm bảo: `amount <= remainingPayableAmount`).
  - Kiểm tra tài khoản nhận tiền (`UC-42 E2`, `UC-82 E1`):
    - Lấy `activeAccount = accountsRepo.findFirstByStoreIdAndEffectiveUntilIsNull(storeId)` (`409` nếu chưa có).
    - Kiểm tra `activeAccount.verifiedAt != null` (`409` nếu chưa xác minh).
    - Bắt buộc `request.payoutAccountId == activeAccount.id && request.expectedAccountVersion == activeAccount.accountVersion` (nếu Admin gửi phiên bản cũ hoặc vừa có phiên bản mới thay thế → `409 CONFLICT` `"Payout account changed or unverified; verify recipient before payout"`).
  - Kiểm tra số tiền & tham chiếu (`UC-82 E2`):
    - `amount` nguyên đồng `VND` (`> 0`).
    - Tính `committed = payouts.filter { it.status == PENDING || it.status == VERIFIED }.sumOf { it.amount }`.
    - `remainingCap = payableCap - committed`. Nếu `amount > remainingCap` → `409 CONFLICT` `"Payout amount exceeds remaining eligible amount"`.
    - `transferRef = request.transferReference.trim()` phải không rỗng và `!payoutRepo.existsByTransferReference(transferRef)` (`409 CONFLICT` nếu trùng).
    - Nếu `request.status == VERIFIED` (mặc định): bắt buộc `transferredAt != null` và `evidenceFileId != null` (`StoredFile` tồn tại và `uploadedBy.id == adminId`, gắn `Attachment(file, payout = savedPayout)`). Nếu `request.status == PENDING` (`UC-82 A1`): cho phép `evidenceFileId` tùy chọn lúc khởi tạo, bắt buộc khi `/verify`.
  - Tạo `recipientSnapshot` JSON (`accountVersion`, `bankCode`, `accountNumberMasked`, `accountHolderName`, `verifiedAt`) và lưu `StorePayout`.
  - Gọi `recalculateSettlementStatus(settlement)`:
    - `verifiedPaid = payouts.filter { it.status == VERIFIED }.sumOf { it.amount }`.
    - Nếu `verifiedPaid >= payableCap && payableCap > 0` → `SettlementStatus.PAID`.
    - Else if `verifiedPaid > BigDecimal.ZERO` → `SettlementStatus.PARTIALLY_PAID`.
    - Else giữ `SettlementStatus.CONFIRMED`.
  - Ghi `AuditLog` (`PAYOUT_RECORDED` / `PAYOUT_VERIFIED`); nếu `status == VERIFIED`, gọi `NotificationOutboxService.enqueueSettlement(store.owner, "STORE_PAYOUT_VERIFIED", ...)`.
- **`verifyPayout(adminId, payoutId, request)` & `failPayout(adminId, payoutId, reason)`:**
  - Khóa `Store` → `Settlement` → `StorePayout (findForUpdate)`: chỉ áp dụng khi `payout.status == PENDING` (`409` nếu khác).
  - `verifyPayout`: kiểm tra lại tài khoản `payout.payoutAccount` vẫn là bản hiệu lực (`effectiveUntil == null`, `verifiedAt != null`; nếu vừa đổi → `409` chặn chi!), kiểm tra `evidenceFileId` (nếu chưa có từ lúc `PENDING` thì bắt buộc truyền vào lúc `/verify`), chuyển `VERIFIED`, gán `confirmedBy`, `confirmedAt`, cập nhật trạng thái `Settlement` (`PARTIALLY_PAID`/`PAID`), gửi `NotificationOutboxService.enqueueSettlement`.
  - `failPayout`: yêu cầu `reason` khác rỗng, chuyển `FAILED`, giải phóng phần tiền đã giữ chỗ để Admin chi lại sau khi kiểm tra ngân hàng (`UC-82 A1`).
- **[`PayoutAttachmentPolicy.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/service/PayoutAttachmentPolicy.kt):**
  - Implements `AttachmentAccessPolicy`: `supports(attachment) = attachment.payout != null`; `canRead(accountId, attachment)` cho phép `PlatformRole.ADMIN` hoặc `attachment.payout!!.store.owner.id == accountId` (chặn `Store Staff`, khách hàng, chủ cửa hàng khác → `403 ACCESS_DENIED`).

### 2.5. Truy vấn Đối soát & Lịch sử (`UC-49`, `UC-83`) & Tích hợp Audit

- `SettlementQueryService`:
  - `listForOwner(actorId, storeId, from, to, status, page, size, sort)`: kiểm tra `authorization.requirePermission(actorId, storeId, "STORE_PROFILE_MANAGE")` (`403` với Staff/người ngoài).
  - `detailForOwner(actorId, storeId, settlementId)`: kiểm tra quyền owner-only và `settlement.store.id == storeId`. Trả `SettlementDetailView`: thông tin bảng, `lines` (kèm `orderId`, `orderCode`, `entryType`, `amount`, `originalEntryId`, `originalSettlementId`), `heldExceptions`, `payouts` (gồm `payoutAccountVersion`, `recipientSnapshot`, `amount`, `transferReference`, `status`, `transferredAt`, `confirmedAt`, `evidenceFileIds`), `verifiedPaidAmount`, `pendingPayoutAmount`, `remainingPayableAmount`, `recoveryAmount`.
  - `listForAdmin(storeId, from, to, status, page, size, sort)` & `detailForAdmin(settlementId)` (`UC-83`): `@PreAuthorize("hasRole('ADMIN')")`, hỗ trợ lọc đa tiêu chí UTC, `PageQuery` + whitelist sort (`periodEndsAt`, `confirmedAt`, `createdAt`), chỉ đọc (`@Transactional(readOnly = true)`).
- Ghi `AuditLog` qua `StoreHistoryRecorder.record(...)` cho các action: `PAYOUT_ACCOUNT_UPDATED`, `PAYOUT_ACCOUNT_VERIFIED`, `PAYOUT_ACCOUNT_REVEALED`, `SETTLEMENT_DRAFT_SAVED`, `SETTLEMENT_CONFIRMED`, `PAYOUT_RECORDED`, `PAYOUT_VERIFIED`, `PAYOUT_FAILED`. (Không sửa/mở rộng API xem nhật ký tổng hợp của `T8.5`).

---

## 3. API Contract (`docs/api/openapi.yaml`)

Thêm **14** đường dẫn mới (nâng từ `126` → **`140`** paths; giữ nguyên **`31`** tags bằng cách gán vào `"Store finance"` và `"Admin finance"`):

| Method / Path | Quyền & Tham số | Response | Tag |
|---|---|---|---|
| `GET /api/v1/stores/{storeId}/finance/payout-accounts` | Store Owner (`STORE_PROFILE_MANAGE`) | `200 PayoutAccountHistoryView`; `401`, `403`, `404` | `Store finance` |
| `PUT /api/v1/stores/{storeId}/finance/payout-account` | Store Owner (`STORE_PROFILE_MANAGE`); body `UpsertPayoutAccountRequest` | `200 StorePayoutAccountView`; `400`, `401`, `403`, `404` | `Store finance` |
| `GET /api/v1/stores/{storeId}/finance/settlements` | Store Owner (`STORE_PROFILE_MANAGE`); query `from`, `to`, `status`, `page`, `size`, `sort` | `200 PageResponse<SettlementSummaryView>`; `400`, `401`, `403` | `Store finance` |
| `GET /api/v1/stores/{storeId}/finance/settlements/{settlementId}` | Store Owner (`STORE_PROFILE_MANAGE`) | `200 SettlementDetailView`; `401`, `403`, `404` | `Store finance` |
| `GET /api/v1/admin/finance/stores/{storeId}/payout-accounts` | `ADMIN` | `200 PayoutAccountHistoryView`; `401`, `403`, `404` | `Admin finance` |
| `GET /api/v1/admin/finance/stores/{storeId}/payout-accounts/active-for-payout` | `ADMIN` (xem thông tin người nhận đầy đủ để đối chiếu chi trả) | `200 AdminPayoutRecipientView`; `401`, `403`, `404`, `409` | `Admin finance` |
| `POST /api/v1/admin/finance/stores/{storeId}/payout-accounts/{accountId}/verify` | `ADMIN`; body `VerifyPayoutAccountRequest` | `200 StorePayoutAccountView`; `400`, `401`, `403`, `404`, `409` | `Admin finance` |
| `POST /api/v1/admin/finance/settlements/draft` | `ADMIN`; body `CreateSettlementDraftRequest` | `200 SettlementDetailView`; `400`, `401`, `403`, `404`, `409` | `Admin finance` |
| `POST /api/v1/admin/finance/settlements/{settlementId}/confirm` | `ADMIN` | `200 SettlementDetailView`; `401`, `403`, `404`, `409` | `Admin finance` |
| `POST /api/v1/admin/finance/settlements/{settlementId}/payouts` | `ADMIN`; body `CreateStorePayoutRequest` | `201 StorePayoutView`; `400`, `401`, `403`, `404`, `409` | `Admin finance` |
| `POST /api/v1/admin/finance/payouts/{payoutId}/verify` | `ADMIN`; body `VerifyStorePayoutRequest` | `200 StorePayoutView`; `400`, `401`, `403`, `404`, `409` | `Admin finance` |
| `POST /api/v1/admin/finance/payouts/{payoutId}/fail` | `ADMIN`; body `FailStorePayoutRequest` | `200 StorePayoutView`; `400`, `401`, `403`, `404`, `409` | `Admin finance` |
| `GET /api/v1/admin/finance/settlements` & `GET /api/v1/admin/finance/settlements/{settlementId}` | `ADMIN`; query `storeId`, `from`, `to`, `status`, `page`, `size`, `sort` | `200 PageResponse<SettlementSummaryView>` / `SettlementDetailView`; `400`, `401`, `403`, `404` | `Admin finance` |

*(Ghi chú: Bảng trên liệt kê 14 dòng trong đó dòng cuối gồm 2 path `GET /settlements` và `GET /settlements/{settlementId}`; tổng cộng chính xác **14** đường dẫn mới, nâng `126` → **`140`** paths. Mọi DTO đều có schema và example đầy đủ trong `openapi.yaml`.)*

---

## 4. Migration và Database Schema

- Thêm mới 1 migration: `src/main/resources/db/migration/V25__settlement_and_payout_details.sql`.
- Tuyệt đối không sửa `V1`–`V24` và các trigger `V3` (`protect_settlement_line`, `immutable_ledger_entry`, `immutable_financial_event`).
- Giữ nguyên **43** Hibernate entities; tổng số migration tăng `24` → **`25`**.

---

## 5. File Layout dự kiến

Đường dẫn Kotlin tương đối dưới `src/main/kotlin/com/foodrescue/backend/`:

| File | Loại | Trách nhiệm |
|---|---|---|
| `src/main/resources/db/migration/V25__settlement_and_payout_details.sql` | NEW | Cột audit/mã hóa/snapshot và partial unique index cho T8.4 |
| `src/main/resources/application.yml` | MODIFIED | Khai báo cấu hình `finance.payout-account.*` |
| `.env.example` | MODIFIED | Khai báo biến môi trường rỗng `PAYOUT_ACCOUNT_ACTIVE_KEY_ID`, `PAYOUT_ACCOUNT_KEYS` |
| `finance/PayoutAccountProperties.kt` | NEW | `@ConfigurationProperties("finance.payout-account")` |
| `finance/entity/{StorePayoutAccount,Settlement,StorePayout}.kt` | MODIFIED | Map cột mới từ `V25` |
| `finance/repository/{StorePayoutAccount,Settlement,SettlementLine,StorePayout}Repository.kt` | MODIFIED | Các truy vấn khóa dòng và lọc nghiệp vụ |
| `finance/dto/PayoutAccountDtos.kt` | NEW | Request/Response cho `StorePayoutAccount` (`Upsert`, `Verify`, `View`, `HistoryView`, `AdminRecipientView`) |
| `finance/dto/SettlementDtos.kt` | NEW | Request/Response cho `Settlement` (`CreateDraft`, `SummaryView`, `DetailView`, `LineView`, `HeldExceptionView`) |
| `finance/dto/StorePayoutDtos.kt` | NEW | Request/Response cho `StorePayout` (`Create`, `Verify`, `Fail`, `View`) |
| `finance/service/PayoutAccountCodec.kt` | NEW | Mã hóa AES-GCM & che số tài khoản nhận tiền |
| `finance/service/PayoutAccountService.kt` | NEW | Quản lý phiên bản và xác minh `StorePayoutAccount` (`UC-42`, `D05`) |
| `finance/service/SettlementService.kt` | NEW | Lập/rebuild `DRAFT`, kiểm tra bất biến dữ liệu, xác nhận `CONFIRMED`, truy vấn `UC-49`/`UC-83` |
| `finance/service/SettlementQueryService.kt` | NEW | Truy vấn Owner/Admin, DTO và tính công nợ/hạn mức chi qua chuỗi kỳ |
| `finance/service/SettlementErrors.kt` | NEW | Lỗi API nội bộ thống nhất cho workflow T8.4 |
| `file/service/FileAccessService.kt` | MODIFIED | Tích hợp payout policy, trả 403 theo AC-11 cho người không có quyền đọc chứng từ chi trả |
| `finance/service/StorePayoutService.kt` | NEW | Ghi nhận/xác minh/hủy chi trả thủ công `StorePayout` (`UC-82`) |
| `finance/service/PayoutAttachmentPolicy.kt` | NEW | Quyền tải chứng từ `attachment.payout` cho `ADMIN` & `Store Owner` |
| `finance/controller/StoreFinanceController.kt` | MODIFIED | Thêm endpoint tài khoản nhận tiền & đối soát cho Store Owner |
| `finance/controller/AdminSettlementController.kt` | NEW | Endpoints `/api/v1/admin/finance/**` cho đối soát, tài khoản nhận tiền & chi trả |
| `docs/api/openapi.yaml` | MODIFIED | Thêm 14 paths (`126` → `140`) và schemas/examples |
| `src/test/kotlin/.../support/AuthTestFixtures.kt` | MODIFIED | Cấu hình `finance.payout-account.*` giả lập trong test |
| `src/test/kotlin/.../SettlementAndPayoutTests.kt` | NEW | Bộ integration test TDD phủ toàn bộ `AC-01`..`AC-11` |
| `src/test/kotlin/.../{AdminBootstrapTests,AuthMigrationTests,FoodRescueApplicationTests,SwaggerUiTests}.kt` | MODIFIED | Cập nhật kỳ vọng `24` → `25` migrations và `126` → `140` OpenAPI paths |
| `docs/specs/t8-4-settlement-and-payout/03-implementation-control.txt` | NEW | Báo cáo kiểm chứng cuối task |
| `docs/specs/roadmap-3-9.md`, `.gitignore` | MODIFIED | Đánh dấu `[x] T8.4` và whitelist `!docs/specs/t8-4-settlement-and-payout/` |

---

## 6. Task Breakdown

- [x] **T8.4-01:** Tạo migration `V25__settlement_and_payout_details.sql`, cập nhật 3 entity (`StorePayoutAccount`, `Settlement`, `StorePayout`), `PayoutAccountProperties`, `application.yml`, `.env.example`, `AuthTestFixtures.kt` và repositories.
- [x] **T8.4-02 (TDD):** Viết trước bộ integration test `SettlementAndPayoutTests.kt` bao phủ `AC-01`..`AC-11` (bao gồm concurrency lock test cho `/confirm` & `/payouts`) và chạy xác nhận TDD RED.
- [x] **T8.4-03:** Cài đặt `PayoutAccountCodec`, `PayoutAccountService`, DTOs (`PayoutAccountDtos`, `SettlementDtos`, `StorePayoutDtos`).
- [x] **T8.4-04:** Cài đặt `SettlementService` (lập/rebuild `DRAFT`, tách giữ/tranh chấp/ngoại lệ, snapshot fingerprint, xử lý số âm thu hồi, xác nhận `CONFIRMED` + `NoShowCase.SETTLED`, truy vấn `UC-49`/`UC-83`).
- [x] **T8.4-05:** Cài đặt `StorePayoutService` (chi trả `VERIFIED`/`PENDING`, `/verify`, `/fail`, kiểm tra đổi/chưa xác minh tài khoản, giới hạn chi, trùng tham chiếu) và `PayoutAttachmentPolicy`.
- [x] **T8.4-06:** Cập nhật `StoreFinanceController`, tạo `AdminSettlementController`, cập nhật `docs/api/openapi.yaml` (`140` paths / `31` tags) và kỳ vọng trong `AuthMigrationTests`, `FoodRescueApplicationTests`, `SwaggerUiTests`.
- [x] **T8.4-07:** Chạy targeted tests (`SettlementAndPayoutTests`, `CommissionAndRevenueTests`, `NoShowTests`, `ComplaintTests`, `ManualRefundTests`, `FileUploadTests`, `SwaggerUiTests`, `ApiContractTests`, `FoodRescueApplicationTests`), sau đó chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`.
- [x] **T8.4-08:** Đọc trực tiếp `build/reports/tests/test/index.html` và XML các suite, viết `03-implementation-control.txt`, cập nhật `[x] T8.4` trong `roadmap-3-9.md` và `.gitignore`.

---

## 7. Truy vết AC → Task → Test

| AC | Mô tả ngắn | Task | Test Case dự kiến (`SettlementAndPayoutTests` & liên quan) |
|---|---|---|---|
| **AC-01** | Owner tạo/cập nhật `StorePayoutAccount` version mới, mã hóa DB, masked response, không đổi payout cũ | T8.4-01, 03, 06 | `AC-01 payout account versioning encrypts masks and preserves past payout snapshots` |
| **AC-02** | Admin verify đúng `expectedVersion`, thông báo Owner; sai version → `409` | T8.4-03, 06 | `AC-02 admin verification checks version notifies owner and rejects stale version` |
| **AC-03** | Lập `DRAFT`: chỉ bút toán đủ điều kiện vào line; tách `HELD`/`DISPUTED`/khiếu nại mở; truy vết gốc | T8.4-04, 06 | `AC-03 draft settlement isolates held or disputed items and traces lines to orders` |
| **AC-04** | Một `LedgerEntry` chỉ thuộc tối đa một bảng đối soát kể cả khi kỳ chồng lấn | T8.4-04 | `AC-04 overlapping settlement period never double-includes ledger entries` |
| **AC-05** | Tổng đối soát âm hiển thị `recoveryAmount`, không lệnh chuyển âm, chi trả bị chặn `409` | T8.4-04, 05 | `AC-05 negative settlement marks recovery needed and blocks payouts` |
| **AC-06** | Dữ liệu thay đổi sau khi lập `DRAFT` (khiếu nại mới / adjustment mới) → `/confirm` từ chối `409` | T8.4-04 | `AC-06 confirm rejects settlement when underlying entries or holds changed since draft` |
| **AC-07** | `/confirm` chốt `CONFIRMED`, DB trigger khóa line, `NoShowCase -> SETTLED`, thông báo Owner, gọi lặp idempotent | T8.4-04, 06 | `AC-07 confirm locks lines updates no-show settled notifies owner and is idempotent` |
| **AC-08** | Điều chỉnh `ADJUSTMENT` phát sinh sau khi chốt kỳ 1 đi vào dòng mới kỳ 2 liên kết kỳ gốc | T8.4-04 | `AC-08 post-confirmation adjustment enters next settlement linked to original period` |
| **AC-09** | Tài khoản nhận tiền chưa xác minh hoặc vừa đổi → chặn ghi nhận chi trả `409` | T8.4-05 | `AC-09 payout blocked when payout account is unverified or changed` |
| **AC-10** | Chi một phần (`PARTIALLY_PAID -> PAID`), `PENDING -> VERIFIED/FAILED`, chống trùng `transferReference`, chống chi vượt song song | T8.4-05, 06 | `AC-10 partial pending verified failed payouts enforce limits reference uniqueness and concurrency` |
| **AC-11** | `UC-49` Owner & `UC-83` Admin tra cứu + tải chứng từ `Attachment`; Staff 6 quyền/khác store/Customer → `403`, unauth → `401` | T8.4-04, 05, 06 | `AC-11 owner and admin queries attachment policy and role authorization` |
| **AC-12** | Flyway `V25`, OpenAPI `140` paths / `31` tags, full Gradle build xanh không vỡ test cũ | T8.4-06..08 | `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests`, `CommissionAndRevenueTests` |

---

## 8. Kế hoạch kiểm thử TDD

- Viết `SettlementAndPayoutTests.kt` trước khi cài đặt các service/controller mới; chạy test để xác nhận TDD RED (endpoint chưa tồn tại `404` / `store_payout_account` chưa xử lý).
- Dùng `PickupTestClock` / `Clock` kiểm soát mốc `periodStartsAt`, `periodEndsAt`, `effectiveFrom`, `confirmedAt`, `transferredAt`.
- Kiểm tra đồng thời (`CountDownLatch`) khi 2 request cùng ghi `StorePayout` vượt hạn mức còn lại hoặc trùng `transferReference`, bảo đảm chỉ 1 request thành công (`201`) và request còn lại nhận `409`.
- Kiểm chứng chứng từ chi trả qua endpoint tải file thực (`GET /api/v1/files/{fileId}/download` hoặc endpoint tải file của `FileController`): Owner cửa hàng & Admin tải được (`200`), Staff & Owner cửa hàng khác bị `403`.
- Chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain` và đọc trực tiếp `build/reports/tests/test/index.html` cùng XML các suite.

---

## 9. Rủi ro và phương án kiểm soát / Rollback

1. **Rủi ro trigger `V3 protect_settlement_line` khi rebuild bảng `DRAFT`:**  
   *Kiểm soát:* Chỉ xóa/chèn lại `settlement_line` khi đã khóa `Settlement` ở trạng thái `DRAFT`, thực thi `DELETE FROM settlement_line WHERE settlement_id = :id` qua JDBC và flush/clear Hibernate session trước khi chèn tập dòng mới; không sửa bất kỳ dòng nào của bảng đã `CONFIRMED`.
2. **Rủi ro khóa chéo (deadlock) giữa `Order`/`Complaint` và `Settlement`/`StorePayout`:**  
   *Kiểm soát:* Các luồng đối soát/chi trả/tài khoản nhận tiền luôn khóa `Store (findForUpdate)` đầu tiên trước khi khóa `Settlement` / `StorePayout`, tuần tự hóa mọi thay đổi trên cùng cửa hàng.
3. **Rủi ro rò rỉ số tài khoản ngân hàng qua log hoặc response:**  
   *Kiểm soát:* Mã hóa `AES/GCM/NoPadding` khi lưu DB; `Store Owner` và danh sách chỉ nhận `accountNumberMasked`; `audit_log` chỉ ghi bản che; chỉ endpoint đối chiếu chi trả chuyên biệt của `ADMIN` mới giải mã và ghi nhận dấu vết truy cập.
4. **Rủi ro thiếu biến môi trường `PAYOUT_ACCOUNT_KEYS` ở môi trường test:**  
   *Kiểm soát:* Cấu hình trong `TestAuthKeys.configure` (`AuthTestFixtures.kt`), khai báo mẫu rỗng trong `.env.example` và `application.yml`.
5. **Rollback:** Có thể hoàn tác sạch các file mới dưới `finance/`, `V25`, cấu hình và test mới mà không ảnh hưởng dữ liệu hoặc bất biến `V1`–`V24`.


## Ghi nhận triển khai

- Test-first AC-01 chạy RED trước service/controller: endpoint PUT payout-account trả 404, được kiểm chứng trong XML (BUILD FAILED, 1 test/1 failure, 2m20s).
- FileAccessService được tích hợp policy chi trả và trả 403 cho người không có quyền trên chứng từ payout theo AC-11; đây là phần tích hợp policy nêu ở Q5, không thay đổi quyền của các loại chứng từ khác.
- Lookup store_id/settlement_id bằng scalar SQL trước khi khóa Store để không giữ entity cũ trong persistence context khi request đồng thời chờ khóa.
- Tính fingerprint sau khi khóa các đơn hiện có của cửa hàng bằng NOWAIT (đơn đang được cập nhật trả 409 để thử lại, tránh đảo khóa với job ledger nhiều đơn); đọc ledger/event sau khóa để tránh tập ứng viên cũ khi refund/complaint đang thực hiện. Đơn tạo đồng thời sau điểm chụp sẽ được gom ở lần/kỳ tiếp theo theo quy tắc carry-forward.
- Công nợ qua chuỗi previousSettlement: kỳ âm tăng nợ, mỗi kỳ dương đã xác nhận hấp thụ nợ một lần theo thứ tự thời gian; cap chi thực tế trừ phần nợ còn lại, kể cả khi kỳ dương bù hết nợ có payableAmount=0.
- Mỗi DTO ở một file riêng theo quy ước repository. SettlementQueryService tách truy vấn/DTO và tính cap khỏi workflow ghi.

- AdminBootstrapTests: chuyển tiếp hai thuộc tính key ring payout giả sang context bootstrap không có web server, tương tự pickup keys đang có; không nới validation cấu hình production.

- Theo đúng §2.3, kiểm tra LEDGER_IMBALANCE áp dụng cho COMPLETED; no-show ELIGIBLE được đối soát theo trạng thái bù/hết tranh chấp, không bị phụ thuộc vào tiến độ nghĩa vụ hoàn tiền thủ công. Có test riêng cho no-show ELIGIBLE vẫn còn RefundRequest PENDING.

- Kiểm chứng cuối: full build 16m55s, 61 suite, 366 PASS / 1 SKIPPED có sẵn / 0 FAIL / 0 ERROR. T8.4 workflow 17/17, codec 1/1. Đã đọc HTML/XML và đóng roadmap sau kiểm chứng.
