# Technical Plan: T8.5 — Giám sát và Thống kê Quản trị

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A đã được người dùng duyệt `Q1`–`Q4` ngày 2026-11-28.  
**Status:** Hoàn tất triển khai và kiểm chứng ngày 2026-11-28  
**Updated:** 2026-11-28  
**Scope:** T8.5 (`UC-75`, `UC-76`, `UC-79`, `BR-56`: API quản trị kiểm tra & đối chiếu giá Menu — gồm giá niêm yết, lịch sử `MenuPriceHistory`, chứng từ `PriceReferenceDocument` và giá snapshot `BoxItem.unitPriceSnapshot` trong hộp đã đóng kèm cờ chênh lệch; API quản trị tra cứu `audit_log` hoạt động cửa hàng đa tiêu chí kèm tác giả/vai trò/thời điểm/`before`/`after`/`correlationId` và che dữ liệu nhạy cảm; API quản trị thống kê tài chính cơ bản theo kỳ & cửa hàng hoặc toàn sàn gồm số đơn theo đủ 8 `OrderStatus`, doanh thu hoàn tất, giá trị giao dịch, hoa hồng, hoàn tiền, bồi hoàn No-show, khoản đang giữ và khoản phải chi khớp 100% với `ledger_entry`, `customer_order`, `settlement`, `store_payout`).  
**Phụ thuộc:** T8.4 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Quyết định đã duyệt tại Gate A và Baseline

1. **Các quyết định Gate A đã chốt (`Q1`–`Q4`):**
   - **Q1 (`V26` index-only & tách phạm vi `audit_log`):** Tạo migration nhẹ `V26__admin_monitoring_indexes.sql` chỉ bổ sung composite index tra cứu trên `audit_log`, `customer_order`, `ledger_entry` (**giữ nguyên 43 Hibernate entities**, không sửa `V1`–`V25`). Giữ nguyên [`StoreHistoryRecorder.ENTITY_TYPES`](../../src/main/kotlin/com/foodrescue/backend/store/service/StoreHistoryRecorder.kt#L24) cho Owner `/stores/{storeId}/history`; API Admin mới cho phép lọc trên toàn bộ các `entity_type` nghiệp vụ được ghi vào `audit_log`.
   - **Q2 (Quy ước lọc `[from, to]` UTC & đối khớp `ledger_entry`):**
     - `orderCountsByStatus` (đủ 8 `OrderStatus`): lọc theo `customer_order.accepted_at` trong `[from, to]`.
     - `completedOrderGrossRevenue` & `completedOrderCount`: các đơn `COMPLETED` có `customer_order.completed_at` trong `[from, to]`.
     - Toàn bộ chỉ số sổ cái (`verifiedCollectedAmount`, `verifiedRefundedAmount`, `netCashFlowAmount`, `grossCommissionAmount`, `commissionAdjustmentAmount`, `netCommissionAmount`, `grossStorePayableAmount`, `storePayableAdjustmentAmount`, `netStorePayableAmount`, `grossNoShowCompensationAmount`, `noShowAdjustmentAmount`, `netNoShowCompensationAmount`, `totalStoreEntitlementNetAmount`, `totalLedgerAllocatedAmount`): lọc theo `ledger_entry.occurred_at` trong `[from, to]`.
     - Phân rã trạng thái quyền lợi của đúng tập bút toán trong phạm vi lọc (`heldNoShowAmount`, `heldDisputedSalesAmount`, `heldAmount`, `draftUnconfirmedAmount`, `eligibleUnassignedAmount`, `eligibleUnsettledAmount`, `confirmedSettledLedgerAmount`) bảo đảm cân đối toán học `totalStoreEntitlementNetAmount == heldAmount + eligibleUnsettledAmount + confirmedSettledLedgerAmount`.
     - Tình trạng bảng đối soát & chi trả (`confirmedSettlementCount`, `confirmedSettlementTotalAmount`, `confirmedSettlementPayableAmount`, `confirmedSettlementRecoveryAmount`, `paidOutVerifiedAmount`, `pendingPayoutAmount`, `confirmedRemainingPayableAmount`, `outstandingRecoveryDebtAmount`, `totalEstimatedPayableAmount`) tính khớp 100% với `settlement` + `store_payout` theo chuỗi công nợ `SettlementQueryService.carriedRecovery`.
   - **Q3 (API Kiểm tra giá Menu `UC-75`):** `GET /api/v1/admin/stores/{storeId}/menu-price-audit` với query tùy chọn `menuItemId`, `status`, `onlyMismatchedWithPackedBoxes`, `page`, `size`; trả mỗi món gồm thông tin hiện hành, `priceHistory`, `documents` (gồm cả tài liệu toàn menu) và `packedBoxPriceSummary` (gom theo `unitPriceSnapshot`, số hộp, tổng số lượng món, `firstPackedAt`, `lastPackedAt`, cờ `differsFromCurrentListedPrice`, độ lệch `priceDeltaFromCurrentListedPrice`).
   - **Q4 (3 Endpoints & OpenAPI tags):** Nâng `140` → **`143`** paths, giữ nguyên **`31`** tags (gắn vào `"Admin stores"` và `"Admin finance"` sẵn có).
2. **Baseline:**
   - `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`: **BUILD SUCCESSFUL** (`25` migrations `V1`–`V25`, `43` Hibernate entities, `140` OpenAPI paths, `31` tags; `61` suites, **366 PASS / 1 SKIPPED / 0 FAIL / 0 ERROR**).

---

## 2. Thiết kế chi tiết

### 2.1. Migration `V26__admin_monitoring_indexes.sql`

- Tạo mới `src/main/resources/db/migration/V26__admin_monitoring_indexes.sql`:
  ```sql
  -- T8.5: Composite indexes for admin audit log queries and time-window financial statistics.
  CREATE INDEX idx_audit_log_store_occurred ON audit_log (store_id, occurred_at DESC, id);
  CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at DESC, id);
  CREATE INDEX idx_audit_log_entity_occurred ON audit_log (entity_type, entity_id, occurred_at DESC);
  CREATE INDEX idx_customer_order_store_accepted ON customer_order (store_id, accepted_at DESC);
  CREATE INDEX idx_ledger_entry_store_occurred ON ledger_entry (store_id, occurred_at DESC);
  ```
- Không thêm/sửa `@Entity` nào → **43** Hibernate entities không đổi, `V1`–`V25` và mọi trigger `V3` giữ nguyên tuyệt đối.

### 2.2. Kiểm tra & Đối chiếu giá Menu (`UC-75`)

- **Service:** [`MenuPriceInspectionService.kt`](../../src/main/kotlin/com/foodrescue/backend/catalog/service/MenuPriceInspectionService.kt) (`@Transactional(readOnly = true)`, `@PreAuthorize("hasRole('ADMIN')")`)
- **Controller:** [`AdminMenuAuditController.kt`](../../src/main/kotlin/com/foodrescue/backend/catalog/controller/AdminMenuAuditController.kt) (`@RequestMapping("/api/v1/admin/stores/{storeId}/menu-price-audit")`, `@PreAuthorize("hasRole('ADMIN')")`)
- **DTOs (mỗi class một file trong `catalog/dto/`):**
  - `PackedBoxPriceSummaryView`: `unitPriceSnapshot: BigDecimal`, `boxCount: Long`, `totalQuantity: Long`, `firstPackedAt: Instant`, `lastPackedAt: Instant`, `differsFromCurrentListedPrice: Boolean`, `priceDeltaFromCurrentListedPrice: BigDecimal` (`listedPrice - unitPriceSnapshot`, scale `0`).
  - `MenuPriceAuditHistoryItem`: `previousPrice: BigDecimal`, `newPrice: BigDecimal`, `changedAt: Instant`, `changedById: UUID`, `changedByDisplayName: String`, `reason: String`.
  - `MenuItemPriceAuditView`: `item: MenuItemView`, `priceHistory: List<MenuPriceAuditHistoryItem>`, `documents: List<PriceDocumentView>`, `packedBoxPriceSummary: List<PackedBoxPriceSummaryView>`, `hasPackedPriceMismatch: Boolean`.
  - `StoreMenuPriceAuditView`: `storeId: UUID`, `storeName: String`, `storeStatus: String`, `items: PageResponse<MenuItemPriceAuditView>`.
- **Luồng xử lý:**
  1. Kiểm tra `Store` tồn tại (`404 NOT_FOUND` nếu không có).
  2. Validate `PageQuery().pageable(page, size, "createdAt")` (`400 INVALID_PAGINATION`).
  3. Nếu `menuItemId != null`: tìm `MenuItem` thuộc `storeId` (`404 NOT_FOUND` nếu không tồn tại hoặc thuộc cửa hàng khác). Nếu `menuItemId == null`: lấy toàn bộ `MenuItem` của `storeId` sắp xếp theo tên (`findAllByStoreIdOrderByName`), lọc theo `status` nếu có.
  4. Truy vấn batch cho danh sách món đã lọc:
     - `MenuPriceHistory` từ `MenuPriceHistoryRepository` (`changedAt DESC, createdAt DESC`).
     - `PriceReferenceDocument` từ `PriceReferenceDocumentRepository` (gồm tài liệu riêng món và tài liệu toàn menu `menu_item_id IS NULL` của cửa hàng).
     - Thống kê `box_item` kết hợp `physical_box` qua `NamedParameterJdbcTemplate`:
       ```sql
       SELECT bi.menu_item_id, bi.unit_price_snapshot,
              count(DISTINCT bi.box_id) AS box_count,
              coalesce(sum(bi.quantity), 0) AS total_quantity,
              min(pb.packed_at) AS first_packed_at,
              max(pb.packed_at) AS last_packed_at
       FROM box_item bi
       JOIN physical_box pb ON pb.id = bi.box_id
       WHERE bi.store_id = :storeId AND bi.menu_item_id IN (:itemIds)
       GROUP BY bi.menu_item_id, bi.unit_price_snapshot
       ORDER BY max(pb.packed_at) DESC, bi.unit_price_snapshot ASC
       ```
  5. Gắn cờ `differsFromCurrentListedPrice = (unitPriceSnapshot.compareTo(item.listedPrice) != 0)` và `hasPackedPriceMismatch = packedBoxPriceSummary.any { it.differsFromCurrentListedPrice }`. Nếu `onlyMismatchedWithPackedBoxes == true`, lọc giữ chỉ các món có `hasPackedPriceMismatch == true` trước khi cắt trang `PageResponse`.
  6. Trạng thái rỗng (`UC-75 A1`): khi cửa hàng chưa có món phù hợp bộ lọc, trả `200 OK` với `items.content = []`, `items.totalElements = 0`.

### 2.3. Xem Lịch sử Hoạt động Cửa hàng (`UC-76`, `BR-56`)

- **Service:** [`AdminActivityLogService.kt`](../../src/main/kotlin/com/foodrescue/backend/audit/service/AdminActivityLogService.kt) (`@Transactional(readOnly = true)`, `@PreAuthorize("hasRole('ADMIN')")`)
- **Controller:** [`AdminAuditLogController.kt`](../../src/main/kotlin/com/foodrescue/backend/audit/controller/AdminAuditLogController.kt) (`@RequestMapping("/api/v1/admin/audit-logs")`, `@PreAuthorize("hasRole('ADMIN')")`)
- **DTO (`audit/dto/`):**
  - `AdminAuditLogItem`: `id: UUID`, `storeId: UUID?`, `storeName: String?`, `entityType: String`, `entityId: UUID`, `action: String`, `actorId: UUID?`, `actorLoginIdentifier: String?`, `actorDisplayName: String?`, `actorRole: String`, `correlationId: String?`, `occurredAt: Instant`, `before: JsonNode?`, `after: JsonNode?`.
- **Bộ lọc & Validate:**
  - `storeId: UUID?`: nếu truyền và `!stores.existsById(storeId)` → `404 NOT_FOUND` (`UC-76 E1`).
  - `entityType: String?`: chuẩn hóa trim/upper; kiểm tra thuộc tập `MONITORED_ENTITY_TYPES` (`STORE`, `STORE_CHANGE_REQUEST`, `PARTNER_APPLICATION`, `STAFF_ASSIGNMENT`, `MENU_ITEM`, `PRICE_DOCUMENT`, `BOX_TEMPLATE`, `SALE_BATCH`, `PHYSICAL_BOX`, `CUSTOMER_ORDER`, `PAYMENT`, `REFUND_REQUEST`, `COMPLAINT`, `STORE_PAYOUT_ACCOUNT`, `SETTLEMENT`, `STORE_PAYOUT`, `FINANCIAL_POLICY`, `ACCOUNT`, `AUTH`); ngoài danh mục → `400 VALIDATION_FAILED`.
  - `entityId: UUID?`, `actorId: UUID?`, `action: String?` (trim, so sánh không phân biệt hoa thường bằng `upper(action)`), `from: Instant?`, `to: Instant?` (`from > to` → `400 VALIDATION_FAILED`).
  - Phân trang: `PageQuery().pageable(page, size, "createdAt")` (`400 INVALID_PAGINATION`).
  - Whitelist `sort`: `"occurredAt"` → `"a.occurred_at"`, `"createdAt"` → `"a.created_at"` (`400 INVALID_PAGINATION` nếu khác).
- **Xác định `actorRole` chính xác (batch load cho trang kết quả, tránh N+1):**
  - `actorId == null` → `"SYSTEM"`.
  - `actorId` thuộc chủ sở hữu của bản ghi (`store?.owner?.id == actorId`) → `"OWNER"`.
  - `actorId` có role `PlatformRole.ADMIN` → `"ADMIN"`.
  - `storeId != null` và tồn tại `staff_assignment(store_id = storeId, account_id = actorId)` → `"STAFF"`.
  - Còn lại → `"CUSTOMER"`.
- **Bảo vệ dữ liệu nhạy cảm:** `PayoutAccountService` chỉ ghi `accountNumberMasked` vào `audit_log`; `before`/`after` parse từ JSON (`ObjectMapper.readTree`) trả `JsonNode?`.

### 2.4. Thống kê Tài chính Quản trị (`UC-79`)

- **Controller:** [`AdminFinanceStatsController.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/controller/AdminFinanceStatsController.kt) (`@RequestMapping("/api/v1/admin/finance/statistics")`, `@PreAuthorize("hasRole('ADMIN')")`)
- **Service:** [`AdminFinanceStatsService.kt`](../../src/main/kotlin/com/foodrescue/backend/finance/service/AdminFinanceStatsService.kt) (`@Transactional(readOnly = true)`, `@PreAuthorize("hasRole('ADMIN')")`)
- **DTOs (mỗi class một file trong `finance/dto/`):**
  - `AdminFinanceLedgerStatsView`:
    - `verifiedCollectedAmount`, `verifiedRefundedAmount`, `netCashFlowAmount`
    - `completedOrderCount: Long`, `completedOrderGrossRevenue: BigDecimal`
    - `grossCommissionAmount`, `commissionAdjustmentAmount`, `netCommissionAmount`
    - `grossStorePayableAmount`, `storePayableAdjustmentAmount`, `netStorePayableAmount`
    - `grossNoShowCompensationAmount`, `noShowAdjustmentAmount`, `netNoShowCompensationAmount`
    - `totalStoreEntitlementNetAmount`, `totalLedgerAllocatedAmount`
  - `AdminFinanceSettlementStatsView`:
    - *Phân rã 100% tập bút toán quyền lợi cửa hàng trong phạm vi lọc (`ledger_entry`):*
      - `heldNoShowAmount`, `heldDisputedSalesAmount`, `heldAmount` (`heldNoShowAmount + heldDisputedSalesAmount`)
      - `draftUnconfirmedAmount`, `eligibleUnassignedAmount`, `eligibleUnsettledAmount` (`draftUnconfirmedAmount + eligibleUnassignedAmount`)
      - `confirmedSettledLedgerAmount`
      - (Bất biến: `heldAmount + eligibleUnsettledAmount + confirmedSettledLedgerAmount == totalStoreEntitlementNetAmount`)
    - *Tổng hợp bảng đối soát & chi trả (`settlement` + `store_payout`):*
      - `confirmedSettlementCount: Long`, `confirmedSettlementTotalAmount`, `confirmedSettlementPayableAmount`, `confirmedSettlementRecoveryAmount`
      - `paidOutVerifiedAmount`, `pendingPayoutAmount`
      - `confirmedRemainingPayableAmount`, `outstandingRecoveryDebtAmount`, `totalEstimatedPayableAmount` (`eligibleUnsettledAmount + confirmedRemainingPayableAmount`)
  - `AdminStoreFinanceStatsItem`: `storeId: UUID`, `storeName: String`, `storeStatus: String`, `totalOrderCount: Long`, `orderCountsByStatus: Map<String, Long>`, `ledger: AdminFinanceLedgerStatsView`, `settlementAndHold: AdminFinanceSettlementStatsView`.
  - `AdminFinanceStatisticsView`: `scopeStoreId: UUID?`, `from: Instant?`, `to: Instant?`, `totalOrderCount: Long`, `orderCountsByStatus: Map<String, Long>`, `ledger: AdminFinanceLedgerStatsView`, `settlementAndHold: AdminFinanceSettlementStatsView`, `byStore: List<AdminStoreFinanceStatsItem>`.
- **Quy tắc tính toán & Cộng gộp (`byStore` → toàn sàn):**
  - Toàn bộ số tiền dùng `BigDecimal.setScale(0)`.
  - Nếu `storeId != null`: kiểm tra tồn tại (`404 NOT_FOUND`), chỉ tính trên `storeId` và trả `byStore` đúng 1 phần tử cho cửa hàng đó.
  - Nếu `storeId == null`: nạp các cửa hàng có phát sinh dữ liệu trong hệ thống/kỳ (hoặc toàn bộ `Store` sắp xếp theo tên/id để mọi bảng cộng tổng `byStore` luôn khớp chính xác tuyệt đối với số tổng toàn sàn).
  - *Trạng thái giữ tranh chấp trên từng `ledger_entry` (`STORE_PAYABLE`, `NO_SHOW_COMPENSATION`, hoặc `ADJUSTMENT` gắn tới 2 loại trên):* dùng chung đúng rule với `SettlementService` & `StoreFinanceService`:
    - `CONFIRMED_SETTLED`: có `settlement_line` thuộc `settlement` trạng thái `CONFIRMED`, `PARTIALLY_PAID`, hoặc `PAID`.
    - `DRAFT_UNCONFIRMED`: có `settlement_line` thuộc `settlement` trạng thái `DRAFT`.
    - `HELD` / `DISPUTED`: chưa vào `settlement_line`, và đơn hàng đang có `complaint.status IN ('OPEN', 'INVESTIGATING', 'AWAITING_EVIDENCE', 'DECIDED')` hoặc (`order.status = 'NO_SHOW'` AND `no_show_case.compensation_status IN ('HELD', 'DISPUTED')`).
    - `ELIGIBLE_UNASSIGNED`: phần còn lại (chưa vào `settlement_line` và không bị giữ/tranh chấp).
  - *Tính `confirmedRemainingPayableAmount` & `outstandingRecoveryDebtAmount` theo từng store:* tái sử dụng trực tiếp công thức `SettlementQueryService.carriedRecovery` / `payableCap` trên chuỗi các `Settlement(CONFIRMED/PARTIALLY_PAID/PAID)` của từng store theo thứ tự thời gian (`period_ends_at ASC, created_at ASC, id ASC`), trừ tổng `StorePayout(VERIFIED)` + `StorePayout(PENDING)` gắn với từng bảng đó.

---

## 3. API Contract (`docs/api/openapi.yaml`)

Thêm **3** đường dẫn mới (nâng `140` → **`143`** paths; giữ nguyên **`31`** tags):

| Method / Path | Quyền & Tham số | Response | Tag |
|---|---|---|---|
| `GET /api/v1/admin/stores/{storeId}/menu-price-audit` | `ADMIN`; query `menuItemId?`, `status?`, `onlyMismatchedWithPackedBoxes?`, `page`, `size` | `200 StoreMenuPriceAuditView`; `400`, `401`, `403`, `404` | `Admin stores` |
| `GET /api/v1/admin/audit-logs` | `ADMIN`; query `storeId?`, `entityType?`, `entityId?`, `actorId?`, `action?`, `from?`, `to?`, `page`, `size`, `sort` | `200 PageResponse<AdminAuditLogItem>`; `400`, `401`, `403`, `404` | `Admin stores` |
| `GET /api/v1/admin/finance/statistics` | `ADMIN`; query `storeId?`, `from?`, `to?` | `200 AdminFinanceStatisticsView`; `400`, `401`, `403`, `404` | `Admin finance` |

Mọi schema (`StoreMenuPriceAuditView`, `MenuItemPriceAuditView`, `MenuPriceAuditHistoryItem`, `PackedBoxPriceSummaryView`, `AdminAuditLogItem`, `PageResponseAdminAuditLogItem`, `AdminFinanceStatisticsView`, `AdminStoreFinanceStatsItem`, `AdminFinanceLedgerStatsView`, `AdminFinanceSettlementStatsView`) đều có định nghĩa tường minh và ví dụ thực tế trong `docs/api/openapi.yaml`.

---

## 4. Migration và Database Schema

- Thêm mới 1 migration: `src/main/resources/db/migration/V26__admin_monitoring_indexes.sql`.
- Không sửa `V1`–`V25`, không sửa trigger `V3`.
- Giữ nguyên **43** Hibernate entities; số migration tăng `25` → **`26`**.

---

## 5. File Layout dự kiến

Đường dẫn Kotlin tương đối dưới `src/main/kotlin/com/foodrescue/backend/`:

| File | Loại | Trách nhiệm |
|---|---|---|
| `src/main/resources/db/migration/V26__admin_monitoring_indexes.sql` | NEW | Composite indexes cho `audit_log`, `customer_order`, `ledger_entry` |
| `catalog/dto/PackedBoxPriceSummaryView.kt` | NEW | Tóm tắt mức giá snapshot đóng hộp + cờ lệch giá |
| `catalog/dto/MenuPriceAuditHistoryItem.kt` | NEW | Phiên bản lịch sử thay đổi giá kèm tên người sửa |
| `catalog/dto/MenuItemPriceAuditView.kt` | NEW | Chi tiết đối chiếu giá 1 món (`listedPrice`, history, docs, packed summaries) |
| `catalog/dto/StoreMenuPriceAuditView.kt` | NEW | Kết quả phân trang kiểm tra giá Menu theo cửa hàng (`UC-75`) |
| `catalog/service/MenuPriceInspectionService.kt` | NEW | Truy vấn read-only đối chiếu giá Menu & giá đóng hộp (`UC-75`) |
| `catalog/controller/AdminMenuAuditController.kt` | NEW | `GET /api/v1/admin/stores/{storeId}/menu-price-audit` (`ADMIN` only) |
| `audit/dto/AdminAuditLogItem.kt` | NEW | Một dòng lịch sử hoạt động đầy đủ cho Admin (`UC-76`, `BR-56`) |
| `audit/service/AdminActivityLogService.kt` | NEW | Truy vấn read-only đa tiêu chí trên `audit_log` kèm batch resolve vai trò |
| `audit/controller/AdminAuditLogController.kt` | NEW | `GET /api/v1/admin/audit-logs` (`ADMIN` only) |
| `finance/dto/AdminFinanceLedgerStatsView.kt` | NEW | Nhóm chỉ số doanh thu, thu/hoàn, hoa hồng & quyền lợi từ `ledger_entry` |
| `finance/dto/AdminFinanceSettlementStatsView.kt` | NEW | Nhóm phân rã giữ/chưa chốt/đã chốt và số liệu `Settlement`/`StorePayout` |
| `finance/dto/AdminStoreFinanceStatsItem.kt` | NEW | Thống kê chi tiết cho từng cửa hàng |
| `finance/dto/AdminFinanceStatisticsView.kt` | NEW | Thống kê tài chính quản trị tổng hợp + `byStore` (`UC-79`) |
| `finance/service/AdminFinanceStatsService.kt` | NEW | Tính toán thống kê tài chính read-only khớp 100% sổ cái & đối soát |
| `finance/controller/AdminFinanceStatsController.kt` | NEW | `GET /api/v1/admin/finance/statistics` (`ADMIN` only) |
| `docs/api/openapi.yaml` | MODIFIED | Bổ sung 3 paths (`140` → `143`), schemas & examples (giữ `31` tags) |
| `src/test/kotlin/.../AdminMonitoringAndStatsTests.kt` | NEW | Bộ integration test TDD phủ toàn bộ `AC-01`..`AC-07` |
| `src/test/kotlin/.../{AuthMigrationTests,FoodRescueApplicationTests,SwaggerUiTests}.kt` | MODIFIED | Cập nhật kỳ vọng `25` → `26` migrations và `140` → `143` OpenAPI paths |
| `docs/specs/t8-5-admin-monitoring-and-stats/03-implementation-control.txt` | NEW | Báo cáo kiểm chứng cuối task & kịch bản bấm thử Swagger |
| `docs/specs/roadmap-3-9.md`, `.gitignore` | MODIFIED | Đánh dấu `[x] T8.5` và whitelist `!docs/specs/t8-5-admin-monitoring-and-stats/` |

---

## 6. Task Breakdown

- [x] **T8.5-01:** Tạo migration `V26__admin_monitoring_indexes.sql`.
- [x] **T8.5-02 (TDD):** Viết trước bộ integration test `AdminMonitoringAndStatsTests.kt` phủ `AC-01`..`AC-07`, chạy test xác nhận TDD RED (3 endpoints mới chưa tồn tại / `404`).
- [x] **T8.5-03:** Tạo DTOs + `MenuPriceInspectionService` + `AdminMenuAuditController` (`UC-75`).
- [x] **T8.5-04:** Tạo `AdminAuditLogItem` + `AdminActivityLogService` + `AdminAuditLogController` (`UC-76`, `BR-56`).
- [x] **T8.5-05:** Tạo DTOs + `AdminFinanceStatsService` + `AdminFinanceStatsController` (`UC-79`).
- [x] **T8.5-06:** Cập nhật `docs/api/openapi.yaml` (`140` → `143` paths, `31` tags), cập nhật assertions trong `AuthMigrationTests.kt`, `FoodRescueApplicationTests.kt`, `SwaggerUiTests.kt`.
- [x] **T8.5-07:** Chạy targeted tests (`AdminMonitoringAndStatsTests`, `MenuTests`, `BoxPackingTests`, `StoreProfileTests`, `CommissionAndRevenueTests`, `SettlementAndPayoutTests`, `ApiContractTests`, `SwaggerUiTests`, `AuthMigrationTests`, `FoodRescueApplicationTests`), sau đó chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain`.
- [x] **T8.5-08:** Đọc trực tiếp `build/reports/tests/test/index.html` và XML các suite, viết `03-implementation-control.txt`, đánh dấu `[x] T8.5` trong `roadmap-3-9.md` và whitelist thư mục spec trong `.gitignore`.

---

## 7. Truy vết AC → Task → Test

| AC | Mô tả ngắn | Task | Test Case dự kiến (`AdminMonitoringAndStatsTests` & liên quan) |
|---|---|---|---|
| **AC-01** | `UC-75`: Đối chiếu giá niêm yết hiện tại, `MenuPriceHistory`, `PriceReferenceDocument` (món + toàn menu), `BoxItem.unitPriceSnapshot` trước/sau đổi giá kèm cờ chênh lệch, chỉ đọc | T8.5-01..03, 06 | `AC-01 menu price audit returns listed price history documents and packed box snapshots with mismatch flag` |
| **AC-02** | `UC-76` & `BR-56`: Lọc `audit_log` theo `storeId`, `entityType`, `entityId`, `actorId`, `action`, `from`/`to`; đầy đủ `actorRole`, `occurredAt`, `before`, `after`, `correlationId`; empty state; không lộ STK ngân hàng | T8.5-01, 02, 04, 06 | `AC-02 audit log query filters across modules shows roles masks bank account and handles empty state` |
| **AC-03** | `UC-79`: Thống kê đủ 8 `OrderStatus`, giao dịch, doanh thu hoàn tất, hoa hồng, hoàn tiền, No-show, khoản giữ `HELD`/`DISPUTED`, đã đối soát/đã chi/còn phải chi khớp 100% với `ledger_entry` & `settlement`/`store_payout` | T8.5-01, 02, 05, 06 | `AC-03 admin finance statistics reconcile order counts ledger entries holds settlements and payouts` |
| **AC-04** | Multi-store & khoảng thời gian `[from, to]`: tổng toàn sàn bằng tổng `A + B`, lọc đúng cửa sổ UTC theo `Clock` | T8.5-02, 05, 06 | `AC-04 multi-store totals match sum of stores and respect UTC time range` |
| **AC-05** | Validation & lỗi chuẩn: `storeId` / `menuItemId` không tồn tại (`404`), `from > to` / `entityType` sai / `size > 100` / `sort` sai (`400`) | T8.5-02..05 | `AC-05 invalid filters missing store or menu item and bad pagination return standard 400 and 404` |
| **AC-06** | Bảo mật vai trò: `Store Owner`, `Store Staff` (dù đủ 6 quyền), `CUSTOMER` → `403 ACCESS_DENIED`; chưa đăng nhập → `401 UNAUTHORIZED` cho cả 3 API | T8.5-02..06 | `AC-06 non-admin roles receive 403 and unauthenticated requests receive 401 across all endpoints` |
| **AC-07** | Flyway `V26`, OpenAPI `143` paths / `31` tags, full Gradle build xanh không vỡ test cũ | T8.5-06..08 | `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests`, full build |

---

## 8. Kế hoạch kiểm thử TDD

- Viết trước `AdminMonitoringAndStatsTests.kt` kế thừa `StoreTestFixtures` / `AuthTestFixtures` với `PickupTestClock` điều khiển chính xác các mốc thời gian (`accepted_at`, `completed_at`, `changed_at`, `packed_at`, `occurred_at`, `confirmed_at`, `transferred_at`).
- Chạy `AdminMonitoringAndStatsTests` trước khi tạo controller/service để ghi nhận **TDD RED** thực tế.
- Kiểm chứng số liệu `UC-79` không chỉ kiểm tra tĩnh mà dựng kịch bản vòng đời thực hoàn chỉnh qua HTTP API: đơn `COMPLETED`, đơn `COMPLETED` có khiếu nại mở (`HELD`), đơn `NO_SHOW` đang `HELD`, đơn `NO_SHOW` `ELIGIBLE` + đã chốt `Settlement` + đã chi một phần (`PARTIALLY_PAID`), đơn `CANCELLED` + hoàn tiền, đơn `EXPIRED`, đơn `PENDING_PAYMENT`; kiểm tra đồng thời đẳng thức `totalStoreEntitlementNetAmount == heldAmount + eligibleUnsettledAmount + confirmedSettledLedgerAmount` và `totalPlatform == storeA + storeB`.
- Sau khi hoàn tất code & `openapi.yaml`, chạy full `./gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain` và đọc trực tiếp `build/reports/tests/test/index.html` + `build/test-results/test/TEST-*.xml`.

---

## 9. Rủi ro và phương án kiểm soát / Rollback

1. **Rủi ro N+1 query khi dựng trang `audit_log` hoặc danh sách món `UC-75`:**  
   *Kiểm soát:* `MenuPriceInspectionService` gom tổng `box_item` bằng một query `GROUP BY`, chỉ tải lịch sử & chứng từ cho các món thuộc cửa hàng; `AdminActivityLogService` batch-load `account_role` và `staff_assignment` theo tập `actorId` của trang hiện tại thay vì query từng dòng.
2. **Rủi ro sai lệch thời gian giữa `BaseEntity.createdAt` (`Instant.now()`) và `Clock` test (`PickupTestClock`):**  
   *Kiểm soát:* Mọi bộ lọc thời gian và tính thống kê trong kỳ sử dụng chuẩn `occurred_at` (`audit_log`, `ledger_entry`), `accepted_at` / `completed_at` (`customer_order`), `confirmed_at` / `transferred_at` (`settlement`, `store_payout`), `packed_at` (`physical_box`), `changed_at` (`menu_price_history`) — đều được điều khiển bởi `Clock`.
3. **Rủi ro khác biệt phép chia/làm tròn hoặc chuỗi công nợ `Settlement` giữa T8.4 và T8.5:**  
   *Kiểm soát:* Toàn bộ số tiền dùng `BigDecimal` nguyên đồng (`scale 0`); phần tính `carriedRecovery` và `payableCap` gọi/dùng lại logic chuẩn của `SettlementQueryService`, đảm bảo số thống kê ở `UC-79` khớp tuyệt đối với màn hình chi tiết đối soát T8.4.
4. **Rollback:** Toàn bộ thay đổi chỉ nằm ở 3 package `catalog/`, `audit/`, `finance/` (chỉ đọc), migration index `V26`, `openapi.yaml` và test mới; có thể gỡ sạch mà không tác động bất kỳ dữ liệu hay invariant của `V1`–`V25`.


## Ghi nhận triển khai
- V24 đã tạo idx_ledger_entry_store_occurred(store_id, occurred_at DESC, id); V26 tái sử dụng index này, không tạo trùng index cùng tên hoặc sửa migration cũ. V26 thêm 4 index còn lại theo plan.
- Ba API dùng transaction read-only REPEATABLE_READ để toàn bộ chỉ số trong một response nhất quán khi nghiệp vụ khác ghi đồng thời.
- Menu history/documents/snapshot được batch bằng SQL JOIN/GROUP BY, không sửa repository/logic Owner hiện hành. Audit batch roles/assignment đúng precedence OWNER -> ADMIN -> STAFF; dữ liệu JSON có lớp che bank number và credential dự phòng.
- Thời gian header theo confirmed_at; payout VERIFIED theo transferred_at (legacy fallback confirmed_at), PENDING theo transferred_at hoặc created_at được StorePayoutService ghi bằng Clock. Remaining cap trừ toàn bộ cam kết trên header được chọn; chuỗi recovery giữ đầy đủ các kỳ trước ngoài cửa sổ để không tính sai cấn nợ.

- JSON audit được đọc bằng ObjectReader USE_BIG_DECIMAL_FOR_FLOATS cho cả parse và tạo tree, tránh mất chính xác số tiền lớn trong quá trình che dữ liệu; test có giá trị 9007199254740993.00.

- Kết quả cuối: full BUILD SUCCESSFUL (12m24s), 62 suites / 376 tests: 375 PASS, 0 FAIL, 1 SKIPPED có sẵn; T8.5 đạt 9/9 PASS. Đã đọc HTML/XML và ghi chi tiết tại 03-implementation-control.txt. OpenAPI 143 paths / 31 tags; V26 / 43 entities.
