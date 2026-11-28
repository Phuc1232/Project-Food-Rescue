# Business Definition: T8.5 — Giám sát và Thống kê Quản trị

**Status:** Chờ duyệt Gate A  
**Updated:** 2026-11-28  
**Scope:** T8.5 (`UC-75`, `UC-76`, `UC-79`, `BR-56`: Quản trị viên tra cứu & đối chiếu giá Menu cửa hàng gồm giá niêm yết hiện tại, lịch sử thay đổi `MenuPriceHistory`, tài liệu tham chiếu `PriceReferenceDocument` và giá snapshot đã đóng trong `BoxItem`/`PhysicalBox`; Quản trị viên xem lịch sử hoạt động cửa hàng từ `audit_log` lọc theo cửa hàng, loại đối tượng, mã đối tượng, người thực hiện, hành động và khoảng thời gian UTC kèm tác giả/vai trò/thời điểm/trước-sau; Quản trị viên xem thống kê tài chính cơ bản theo kỳ và cửa hàng hoặc toàn sàn gồm số đơn theo trạng thái, giá trị giao dịch đã xác minh, doanh thu hoàn tất, hoa hồng, hoàn tiền, bồi hoàn No-show, khoản đang giữ và khoản còn phải chi bảo đảm khớp 100% với `ledger_entry` & `settlement`/`store_payout`).  
**Phụ thuộc:** T8.4 (`[x]`) trong [roadmap-3-9.md](../roadmap-3-9.md).

---

## 1. Bối cảnh và mục tiêu

Đến hết **T8.4** ([03-implementation-control.txt](../t8-4-settlement-and-payout/03-implementation-control.txt)), toàn bộ chu trình nghiệp vụ cốt lõi của FoodRescue (từ hồ sơ đối tác, menu, đóng hộp, mở bán, đặt đơn, thanh toán, bàn giao, hủy, no-show, khiếu nại, hoàn tiền, thông báo, sổ cái đến đối soát & chi trả) đã hoàn tất và đồng loạt ghi dấu vết bất biến vào:
- **`audit_log`** (append-only qua trigger `V3 immutable_audit_log`): lưu `store_id`, `actor_id`, `entity_type`, `entity_id`, `action`, `before_state`, `after_state`, `correlation_id`, `occurred_at`.
- **`menu_price_history` & `price_reference_document` & `box_item`:** lưu lịch sử thay đổi giá niêm yết, chứng từ giá tham chiếu và `unit_price_snapshot` tại thời điểm đóng từng `PhysicalBox`.
- **`ledger_entry`, `financial_event`, `customer_order`, `settlement`, `settlement_line`, `store_payout`, `no_show_case`, `complaint`:** lưu đầy đủ sổ cái tiền tệ, trạng thái đơn hàng, khoản đang giữ và lịch sử đối soát/chi trả.

Tuy nhiên, Admin vẫn chưa có công cụ giám sát tổng hợp và đối chiếu tập trung ở **T8.5**:
1. **Kiểm tra giá Menu (`UC-75`):** Admin hiện chỉ xem được danh sách/chi tiết từng món rời rạc qua endpoint cửa hàng (`/stores/{storeId}/menu-items`), chưa có màn hình/API tổng hợp giúp đối chiếu **giá niêm yết + lịch sử thay đổi giá + tài liệu tham chiếu + giá snapshot đã đóng trong các hộp (`BoxItem.unitPriceSnapshot`)**.
2. **Xem lịch sử hoạt động cửa hàng (`UC-76`, `BR-56`):** Endpoint `/stores/{storeId}/history` dành cho Owner chỉ đọc 12 `entityType` cố định và ẩn `actorId`; endpoint `GET /api/v1/admin/stores/{storeId}` chỉ kèm 50 dòng gần nhất không có phân trang hay bộ lọc theo đối tượng/thời gian/người thực hiện, đồng thời chưa bao quát `PARTNER_APPLICATION`, `STORE_PAYOUT_ACCOUNT`, `SETTLEMENT`, `STORE_PAYOUT`, `FINANCIAL_POLICY`.
3. **Xem thống kê tài chính cơ bản (`UC-79`):** Chưa có API cho Admin thống kê theo kỳ / cửa hàng (hoặc toàn nền tảng) về **số đơn theo trạng thái, giá trị giao dịch, doanh thu hoàn tất, hoa hồng, hoàn tiền, khoản đang giữ và khoản phải chi**, đối chiếu khớp trực tiếp với sổ cái `ledger_entry` và tình trạng chi trả `Settlement`/`StorePayout`.

Nguồn nghiệp vụ trích xuất từ `../../DOCX/BaoCaoFoodRescue.pdf`:
- **Trang 31 (`BR-56` — Lịch sử nghiệp vụ):** Mọi thay đổi quan trọng liên quan đến giá, thành phần hộp, trạng thái đơn, thanh toán, No-show, hoàn tiền và quyết định của Administrator phải có người thực hiện, thời điểm và nội dung thay đổi để phục vụ tra cứu.
- **Trang 125–126 (`UC-75` — Kiểm tra giá Menu):** Quản trị viên chọn cửa hàng, món hoặc phiên bản giá; hiển thị giá niêm yết, lịch sử thay đổi, tài liệu tham chiếu và giá đã lưu trong hộp để đối chiếu; không làm thay đổi dữ liệu.
- **Trang 126–127 (`UC-76` — Xem lịch sử hoạt động cửa hàng):** Quản trị viên chọn cửa hàng, đối tượng, thời gian hoặc người thực hiện; hiển thị thay đổi giá, đóng hộp, đơn, tiền, bàn giao, No-show, hoàn và quyết định quản trị kèm tác giả/thời điểm; không tìm thấy hiển thị rỗng (`A1`), đối tượng không tồn tại từ chối (`E1`).
- **Trang 130 (`UC-79` — Xem thống kê tài chính):** Quản trị viên chọn khoảng thời gian và cửa hàng (tùy chọn); hiển thị số đơn theo trạng thái, giá trị giao dịch, doanh thu hoàn tất, hoa hồng, hoàn tiền, khoản giữ và phải chi; thao tác chỉ đọc (`readOnly`).

---

## 2. Khảo sát mã hiện tại

Đường dẫn dưới `src/main/kotlin/com/foodrescue/backend/` và `src/main/resources/`:

1. **Dữ liệu `audit_log` & `StoreHistoryRecorder`:**
   - Bảng `audit_log` (`V1` #L148-160, bảo vệ bất biến tại `V3` #L7) đã được tất cả các service nghiệp vụ ghi đầy đủ qua `AuditService.record` / `StoreHistoryRecorder.record`:
     - Nhóm cửa hàng & nhân sự: `STORE`, `STORE_CHANGE_REQUEST`, `PARTNER_APPLICATION`, `STAFF_ASSIGNMENT`.
     - Nhóm menu & giá: `MENU_ITEM`, `PRICE_DOCUMENT` (kèm bảng `menu_price_history` append-only).
     - Nhóm hộp & đợt bán: `BOX_TEMPLATE`, `SALE_BATCH`, `PHYSICAL_BOX`.
     - Nhóm đơn hàng, bàn giao, hủy, No-show: `CUSTOMER_ORDER` (bao gồm `ORDER_CREATED`, `ORDER_CONFIRMED`, `ORDER_CANCELLED`, `CANCELLATION_*`, `HANDOVER_STARTED`, `HANDOVER_COMPLETED`, `PICKUP_INCIDENT_RECORDED`, `ORDER_MARKED_PENDING_NO_SHOW`, `NO_SHOW_CONFIRMED`, `NO_SHOW_NOTIFICATION_RECEIVED`, `NO_SHOW_COMPENSATION_ELIGIBLE`).
     - Nhóm tiền tệ, khiếu nại, hoàn & đối soát: `PAYMENT`, `REFUND_REQUEST`, `COMPLAINT`, `STORE_PAYOUT_ACCOUNT`, `SETTLEMENT`, `STORE_PAYOUT`, `FINANCIAL_POLICY`.
   - `AuditLogRepository.kt` hiện chỉ có 2 method cố định (`findTop50ByEntityIdAndEntityTypeIn...` và `findAllByStoreIdAndEntityTypeIn...`).
   - *Lưu ý timestamp:* `BaseEntity.createdAt` dùng `Instant.now()`, trong khi `audit_log.occurred_at`, `ledger_entry.occurred_at`, `customer_order.accepted_at`, `customer_order.completed_at`, `settlement.confirmed_at`, `store_payout.confirmed_at` dùng `Clock` (chủ động kiểm soát được khi test bằng `PickupTestClock` / `Clock`).
   - *Lưu ý index (`V2` #L118-119):* `audit_log` mới có index đơn lẻ `(actor_id)` và `(store_id)`, chưa có composite index theo `(store_id, occurred_at DESC, id)` hoặc `(occurred_at DESC, id)`.

2. **Dữ liệu giá Menu & Box Snapshot (`catalog/`, `surprisebox/`):**
   - `MenuAccess.canRead` / `requireRead` đã cho phép `ADMIN` đọc `MenuItem`, `MenuPriceHistory`, `PriceReferenceDocument`.
   - `BoxItem` (`V1` #L269, `@Immutable` ở `V3`) lưu `menu_item_id`, `quantity`, `item_name_snapshot`, `unit_price_snapshot` gắn với `PhysicalBox` (`packed_at`, `total_listed_value`, `status`, `current_batch_id`).
   - Hiện chưa có query/endpoint Admin tổng hợp đối chiếu `MenuItem` ↔ `MenuPriceHistory` ↔ `PriceReferenceDocument` ↔ các `BoxItem.unitPriceSnapshot` trong hộp thực tế (`UC-75`).

3. **Dữ liệu Thống kê Tài chính (`finance/`, `order/`, `complaint/`, `pickup/`):**
   - `OrderLedgerService`, `StoreFinanceService` (T8.3) và `SettlementQueryService`, `SettlementService` (T8.4) đã chuẩn hóa đầy đủ quy ước kế toán:
     - `PAYMENT_RECEIPT`: tiền thu đã xác minh.
     - `CUSTOMER_REFUND`: tiền hoàn đã xác minh (mang dấu âm).
     - `PLATFORM_COMMISSION` + `ADJUSTMENT(original=PLATFORM_COMMISSION)`: hoa hồng ròng nền tảng.
     - `STORE_PAYABLE` + `ADJUSTMENT(original=STORE_PAYABLE)`: quyền lợi bán hàng ròng cửa hàng.
     - `NO_SHOW_COMPENSATION` + `ADJUSTMENT(original=NO_SHOW_COMPENSATION)`: quyền lợi bù đắp No-show ròng (hoa hồng `0`).
     - Tình trạng giữ (`HELD`/`DISPUTED`): đơn `COMPLETED` có khiếu nại mở (`OPEN`, `INVESTIGATING`, `AWAITING_EVIDENCE`, `DECIDED`) và đơn `NO_SHOW` có `compensationStatus IN ('HELD', 'DISPUTED')` hoặc khiếu nại mở.
     - Tình trạng đối soát & chi trả (`Settlement` + `StorePayout`): tách riêng khoản đã vào bảng chốt (`CONFIRMED`/`PARTIALLY_PAID`/`PAID`), khoản trong `DRAFT`, khoản còn đủ điều kiện chưa chốt (`eligibleUnsettledAmount`), tổng đã chi (`VERIFIED`), đang chờ chi (`PENDING`), còn phải chi trên bảng đã chốt (`remainingConfirmedPayableAmount`), và nợ thu hồi (`recoveryAmount` từ bảng âm chưa cấn trừ).

4. **Baseline kiểm thử xác minh trên workspace (sau T8.4):**
   - Lệnh: `.\gradlew.bat --gradle-user-home C:/Users/WIN-LOR/.gradle build --console=plain` → **BUILD SUCCESSFUL**.
   - Kết quả XML trong `build/test-results/test/TEST-*.xml`: **61** suites, `367` tests = **366 PASS / 1 SKIPPED / 0 FAIL / 0 ERROR** (ca skip có sẵn `SwaggerUiContextPathTests :: hold disposable browser sandbox until stop signal()`).
   - Hiện trạng repo: **25** migrations (`V1`–`V25`), **43** Hibernate entities, **140** OpenAPI paths / **31** tags.

---

## 3. Người dùng và quyền

- **Quản trị viên (`ADMIN` — `@PreAuthorize("hasRole('ADMIN')")`):**
  - Toàn quyền gọi các API giám sát hoạt động cửa hàng (`UC-76`), đối chiếu giá Menu (`UC-75`) và thống kê tài chính quản trị (`UC-79`).
  - Mọi endpoint mới đều chỉ đọc (`@Transactional(readOnly = true)`), không tạo/sửa dữ liệu nghiệp vụ hay trạng thái đơn/tiền.
- **Chủ cửa hàng (`Store Owner`), Nhân viên cửa hàng (`Store Staff`), Khách hàng (`CUSTOMER`):**
  - Bị chặn hoàn toàn mọi endpoint T8.5 với **403 `ACCESS_DENIED`** (`AC` tối thiểu của roadmap card).
- **Chưa đăng nhập / token không hợp lệ:** **401 `UNAUTHORIZED`**.

---

## 4. Luồng chính

### 4.1. Giám sát & Đối chiếu Giá Menu (`UC-75`)
1. Admin chọn `storeId` (bắt buộc tồn tại; không tồn tại trả `404 NOT_FOUND` theo `UC-75 E1`) và bộ lọc tùy chọn `menuItemId` (thuộc `storeId`, nếu không tồn tại hoặc khác store trả `404`), trạng thái món `status`, hoặc phân trang (`page`, `size`).
2. Hệ thống truy vấn trong transaction `readOnly`:
   - Danh sách `MenuItem` thuộc cửa hàng (kèm `listedPrice` hiện tại, danh mục, trạng thái).
   - Toàn bộ lịch sử thay đổi giá `MenuPriceHistory` của từng món (`previousPrice`, `newPrice`, `changedAt`, `changedById`, `changedByDisplayName`, `reason`).
   - Toàn bộ `PriceReferenceDocument` áp dụng cho món đó hoặc toàn menu (`menuItem == null`) kèm `fileId`, `fileName`, `appliesFrom`, `uploadedAt`, `replacedById` (Admin dùng `/api/v1/files/{fileId}/content` sẵn có để tải file chứng từ).
   - Lịch sử giá snapshot đóng hộp từ `BoxItem` (`boxId`, `boxCode`, `packedAt`, `batchId`, `batchSaleDate`, `quantity`, `unitPriceSnapshot`, `totalListedValue` của hộp) — phân biệt các mức giá snapshot từng đóng hộp và đánh dấu cờ đối chiếu `differsFromCurrentListedPrice = (unitPriceSnapshot != currentListedPrice)`.
3. Nếu cửa hàng chưa có món phù hợp bộ lọc → trả danh sách rỗng `200 OK` (`UC-75 A1`).

### 4.2. Xem Lịch sử Hoạt động Cửa hàng (`UC-76`, `BR-56`)
1. Admin gọi truy vấn lịch sử hoạt động (toàn sàn hoặc lọc theo `storeId`):
   - Nếu truyền `storeId` mà không tồn tại cửa hàng → trả `404 NOT_FOUND` (`UC-76 E1`).
   - Validate `from <= to` (nếu truyền cả hai → `400 VALIDATION_FAILED`), validate `sort` theo whitelist (`occurredAt`, `createdAt`), validate `entityType` (nếu truyền) thuộc danh mục đối tượng được giám sát, validate `page`/`size` qua `PageQuery`.
2. Truy vấn `audit_log` theo bộ lọc kết hợp (AND): `storeId`, `entityType`, `entityId`, `actorId`, `action`, `occurredAt >= from`, `occurredAt <= to`.
3. Mỗi phần tử trả về đầy đủ thông tin phục vụ tra cứu (`BR-56`):
   - `id`, `storeId`, `storeName`, `entityType`, `entityId`, `action`, `actorId`, `actorLoginIdentifier`, `actorDisplayName`, `actorRole` (`ADMIN`, `OWNER`, `STAFF`, `CUSTOMER`, `SYSTEM`), `correlationId`, `occurredAt`, `before` (`JsonNode?`), `after` (`JsonNode?`).
4. Không có bản ghi phù hợp → trả trang rỗng (`totalElements = 0`, `content = []`, `UC-76 A1`).

### 4.3. Thống kê Tài chính Quản trị (`UC-79`)
1. Admin chọn bộ lọc tùy chọn `storeId` (null = toàn nền tảng; nếu truyền mà store không tồn tại → `404 NOT_FOUND`) và khoảng thời gian `[from, to]` UTC (validate `from <= to` → `400 VALIDATION_FAILED`).
2. Hệ thống tính toán đồng bộ trực tiếp từ dữ liệu gốc DB (`customer_order`, `ledger_entry`, `settlement`, `settlement_line`, `store_payout`, `no_show_case`, `complaint`), **tất cả giá trị tiền dùng `BigDecimal` nguyên đồng VND (`scale 0`)**:
   - **Nhóm Đơn hàng (`orderCountsByStatus`, lọc theo `accepted_at` trong `[from, to]` nếu có):**
     - `totalOrderCount` và bản đồ `orderCountsByStatus` theo đầy đủ 8 trạng thái `OrderStatus` (`PENDING_PAYMENT`, `CONFIRMED`, `HANDOVER_IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `EXPIRED`, `PENDING_NO_SHOW`, `NO_SHOW`).
   - **Nhóm Sổ cái & Doanh thu (`ledger`, lọc theo `ledger_entry.occurred_at` trong `[from, to]` nếu có):**
     - `grossTransactionValue` (`collectedVerifiedAmount` = `SUM(PAYMENT_RECEIPT)`).
     - `refundedVerifiedAmount` (`ABS(SUM(CUSTOMER_REFUND))`).
     - `netCashFlowAmount` = `collectedVerifiedAmount - refundedVerifiedAmount`.
     - `completedOrderGrossRevenue` = `SUM(customer_order.total_amount)` của các đơn `COMPLETED` (trong kỳ `completed_at`).
     - `grossCommissionAmount`, `commissionAdjustmentAmount`, `netCommissionAmount` (từ `PLATFORM_COMMISSION` và `ADJUSTMENT` gốc `PLATFORM_COMMISSION`).
     - `grossStorePayableAmount`, `storePayableAdjustmentAmount`, `netStorePayableAmount` (từ `STORE_PAYABLE` và `ADJUSTMENT` gốc `STORE_PAYABLE`).
     - `grossNoShowCompensationAmount`, `noShowAdjustmentAmount`, `netNoShowCompensationAmount` (từ `NO_SHOW_COMPENSATION` và `ADJUSTMENT` gốc `NO_SHOW_COMPENSATION`).
     - `totalStoreEntitlementNetAmount` = `netStorePayableAmount + netNoShowCompensationAmount`.
     - `totalLedgerAllocatedAmount` = `netCommissionAmount + totalStoreEntitlementNetAmount`.
     - `ledgerBalanced`: kiểm tra đối chiếu cân bằng ròng (khi toàn bộ sự kiện đã hạch toán và không còn chênh lệch hoàn tiền).
   - **Nhóm Khoản giữ, Đối soát & Phải chi (`settlementAndHold`, trạng thái thực tế của các bút toán cửa hàng thuộc phạm vi lọc):**
     - `heldAmount`: tổng giá trị ròng các bút toán quyền lợi cửa hàng chưa đối soát đang bị giữ (`HELD` / `DISPUTED` / đơn hoàn tất có khiếu nại mở), tách thêm `heldNoShowAmount` và `heldDisputedSalesAmount`.
     - `eligibleUnsettledAmount`: tổng các bút toán quyền lợi cửa hàng đủ điều kiện đối soát nhưng chưa vào bất kỳ bảng chốt nào (hoặc đang nằm ở `DRAFT`).
     - `confirmedSettlementTotalAmount`: tổng quyền lợi của các bảng đối soát đã chốt (`CONFIRMED`, `PARTIALLY_PAID`, `PAID`) thuộc phạm vi `storeId` (và `confirmed_at` trong `[from, to]` nếu lọc kỳ đối soát — xem **Q2** để chốt cách lọc kỳ cho phần `Settlement`/`StorePayout`).
     - `paidOutVerifiedAmount`: tổng `StorePayout(VERIFIED)` đã chi.
     - `pendingPayoutAmount`: tổng `StorePayout(PENDING)` đang chờ xác nhận ngân hàng.
     - `confirmedRemainingPayableAmount`: tổng số còn phải chi trên các bảng `CONFIRMED`/`PARTIALLY_PAID` (sau cấn trừ nợ kỳ trước và đã chi).
     - `totalOutstandingPayableAmount` = `eligibleUnsettledAmount + confirmedRemainingPayableAmount` (tổng nghĩa vụ nền tảng còn phải chuyển cho cửa hàng ngoài phần đang giữ tranh chấp).
   - **Phân rã theo cửa hàng (`byStore`, khi không truyền `storeId` hoặc trả đúng 1 dòng khi truyền `storeId`):** giúp Admin xem nhanh bức tranh từng cửa hàng khớp với số tổng toàn sàn.

---

## 5. Quy tắc nghiệp vụ

- **BR-56 (Lịch sử nghiệp vụ đầy đủ & bất biến):** Mọi thay đổi giá (`menu_price_history` + `MENU_ITEM_*`), đóng hộp (`PHYSICAL_BOX`, `BOX_TEMPLATE`, `SALE_BATCH`), đơn hàng (`CUSTOMER_ORDER`), thanh toán (`PAYMENT`), No-show (`NO_SHOW_*`), hoàn tiền (`REFUND_*`), khiếu nại (`COMPLAINT_*`), tài khoản nhận tiền & đối soát/chi trả (`STORE_PAYOUT_ACCOUNT`, `SETTLEMENT`, `STORE_PAYOUT`) và quyết định quản trị (`STORE`, `STORE_CHANGE_REQUEST`, `PARTNER_APPLICATION`, `FINANCIAL_POLICY`) đều tra cứu được kèm người thực hiện, vai trò, thời điểm UTC và dữ liệu thay đổi (`before`/`after`).
- **Đối chiếu giá Menu (`UC-75`):** Không cho sửa giá hay sửa `box_item.unit_price_snapshot` khi tra cứu; hiển thị chính xác giá snapshot trong từng hộp để phát hiện trường hợp tăng giá niêm yết hoặc chênh lệch tài liệu tham chiếu.
- **Khớp sổ cái (`UC-79`, Roadmap AC):** Toàn bộ số liệu tiền trong thống kê quản trị phải tính từ bảng sự thật (`ledger_entry`, `settlement`, `store_payout`, `customer_order`) theo đơn vị nguyên đồng VND `BigDecimal`, đảm bảo tổng chi tiết theo cửa hàng cộng lại bằng đúng số tổng hợp, và tổng quyền lợi cửa hàng trên sổ cái khớp tổng `đang giữ + chờ đối soát/draft + đã chốt (loại phần điều chỉnh chéo kỳ)`.
- **Chỉ đọc & Bảo mật (`ADMIN` only, `NFR-12/13`):** Mọi API T8.5 đều yêu cầu vai trò `ADMIN` (`403` với Owner/Staff/Customer, `401` khi chưa xác thực), không lộ thông tin nhạy cảm (mật khẩu, token, số tài khoản ngân hàng giải mã, pickup code).

---

## 6. Phạm vi

### Trong phạm vi (In-Scope):
1. Migration `V26` bổ sung index phục vụ lọc/sắp xếp hiệu năng cho `audit_log` (và `customer_order` / `settlement` nếu cần), **giữ nguyên 43 Hibernate entities** và không sửa `V1`–`V25`.
2. Dịch vụ & API **Kiểm tra giá Menu (`UC-75`)** cho Admin (`MenuPriceInspectionService`).
3. Dịch vụ & API **Xem lịch sử hoạt động cửa hàng (`UC-76`, `BR-56`)** cho Admin (`AdminActivityLogService`), lọc đa tiêu chí (`storeId`, `entityType`, `entityId`, `actorId`, `action`, `from`, `to`, phân trang, whitelist sort).
4. Dịch vụ & API **Thống kê tài chính quản trị (`UC-79`)** cho Admin (`AdminFinanceStatsService`), hỗ trợ toàn sàn hoặc theo `storeId` + khoảng thời gian `[from, to]`, đối khớp chặt với `ledger_entry` và `Settlement`/`StorePayout`.
5. Cập nhật `docs/api/openapi.yaml` (schemas, examples, errors `400/401/403/404`), giữ nguyên hoặc thêm tag hợp lý, viết bộ integration test TDD (`AdminMonitoringAndStatsTests.kt`) phủ đầy đủ AC + giữ xanh toàn bộ suite cũ.

### Ngoài phạm vi (Out-of-Scope):
1. Sửa đổi dữ liệu lịch sử hoặc thêm endpoint ghi đè `audit_log` / `menu_price_history` / `ledger_entry`.
2. Xuất báo cáo CSV/Excel/PDF, biểu đồ BI phức tạp, hoặc thay đổi API Owner `/stores/{storeId}/history` đã khóa ở T3.3.
3. Các hạng mục Giai đoạn 9 (**T9.1–T9.5**: E2E load test, Actuator metrics/alerting, OWASP audit, backup/restore, CI/CD).

---

## 7. Acceptance Criteria (Given / When / Then)

| ID | Given / When / Then |
|---|---|
| **AC-01** | **Given** cửa hàng có món ăn kèm nhiều lần đổi giá (`MenuPriceHistory`), tài liệu tham chiếu giá (`PriceReferenceDocument`) và các `PhysicalBox` đã đóng lưu `BoxItem.unitPriceSnapshot` tại các thời điểm khác nhau, **When** Admin gọi API kiểm tra giá Menu (`UC-75`), **Then** trả `200 OK` đầy đủ giá niêm yết hiện tại, lịch sử đổi giá, danh sách tài liệu tham chiếu và giá snapshot trong hộp (cùng cờ phát hiện chênh lệch nếu giá niêm yết hiện tại khác giá đóng hộp), không làm thay đổi dữ liệu. |
| **AC-02** | **Given** cửa hàng phát sinh chuỗi nghiệp vụ xuyên suốt (giá menu, mẫu hộp/đóng hộp, đặt đơn, thanh toán, bàn giao, no-show, hoàn tiền, khiếu nại, tài khoản nhận tiền, đối soát, chi trả, quyết định khóa/mở cửa hàng của Admin), **When** Admin gọi API lịch sử hoạt động (`UC-76`) lọc theo `storeId`, `entityType`, `entityId`, `actorId`, `action`, `from`/`to`, **Then** trả `200 OK` đúng tập bản ghi `audit_log` kèm `actorId`, `actorRole`, `occurredAt`, `before`, `after`, `correlationId`; bộ lọc không khớp trả danh sách rỗng (`totalElements = 0`); dữ liệu nhạy cảm như số tài khoản ngân hàng vẫn được che. |
| **AC-03** | **Given** tập đơn hàng thuộc nhiều trạng thái (`COMPLETED`, `CANCELLED`, `NO_SHOW`, `PENDING_PAYMENT`, `EXPIRED`...) cùng các bút toán `PAYMENT_RECEIPT`, `CUSTOMER_REFUND`, `PLATFORM_COMMISSION`, `STORE_PAYABLE`, `NO_SHOW_COMPENSATION`, `ADJUSTMENT` (bao gồm cả đơn còn bị `HELD`/`DISPUTED` và bảng `Settlement` đã chốt/đã chi một phần/đã chi đủ), **When** Admin gọi API thống kê tài chính (`UC-79`) cho cửa hàng hoặc toàn nền tảng (có hoặc không lọc `from`/`to`), **Then** số đơn theo từng trạng thái, giá trị giao dịch, doanh thu hoàn tất, hoa hồng ròng, hoàn tiền ròng, bù đắp No-show, khoản đang giữ và khoản phải chi khớp chính xác tuyệt đối với `ledger_entry`, `customer_order`, `settlement` và `store_payout` theo nguyên đồng VND. |
| **AC-04** | **Given** hệ thống có nhiều cửa hàng A và B với dữ liệu tài chính độc lập, **When** Admin gọi thống kê toàn sàn (không truyền `storeId`) và thống kê riêng từng cửa hàng (`storeId = A`, `storeId = B`), **Then** tổng số liệu toàn sàn bằng đúng tổng số liệu của A + B, và khi lọc theo khoảng `[from, to]` chỉ tính các giao dịch/sự kiện trong khoảng thời gian đó. |
| **AC-05** | **Given** Admin truyền `storeId` / `menuItemId` không tồn tại (`404 NOT_FOUND`), hoặc truyền `from > to`, `size > 100`, `sort` / `entityType` không hợp lệ (`400 VALIDATION_FAILED` / `INVALID_PAGINATION`), **When** gọi các API T8.5, **Then** hệ thống trả đúng mã lỗi chuẩn `404`/`400` kèm `requestId`. |
| **AC-06** | **Given** người dùng không phải Admin (`Store Owner`, `Store Staff` dù có đủ 6 quyền `V7`, `CUSTOMER`), hoặc request chưa xác thực, **When** gọi bất kỳ endpoint T8.5 nào (`UC-75`, `UC-76`, `UC-79`), **Then** trả `403 ACCESS_DENIED` (hoặc `401 UNAUTHORIZED` khi thiếu token). |
| **AC-07** | **Given** migration mới (nếu có) và cập nhật `docs/api/openapi.yaml`, **When** chạy Flyway, `AuthMigrationTests`, `FoodRescueApplicationTests`, `ApiContractTests`, `SwaggerUiTests` và toàn bộ Gradle test suite, **Then** build xanh hoàn toàn, không làm vỡ bất kỳ test cũ nào (`StoreProfileTests`, `MenuTests`, `CommissionAndRevenueTests`, `SettlementAndPayoutTests`...). |

---

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn nghiệp vụ | Phạm vi đáp ứng | AC |
|---|---|---|
| **UC-75** (Trang 125–126) | Admin tra cứu giá niêm yết, lịch sử thay đổi giá, tài liệu tham chiếu và giá snapshot trong hộp; lọc cửa hàng/món; chỉ đọc; 404 khi không tồn tại | AC-01, AC-05, AC-06 |
| **UC-76** (Trang 126–127) | Admin xem lịch sử hoạt động cửa hàng (giá, hộp, đơn, tiền, bàn giao, No-show, hoàn, đối soát, quyết định quản trị) lọc theo cửa hàng/đối tượng/thời gian/người thực hiện; trạng thái rỗng | AC-02, AC-05, AC-06 |
| **UC-79** (Trang 130) | Admin xem thống kê tài chính cơ bản theo kỳ & cửa hàng/toàn sàn: số đơn theo trạng thái, giá trị giao dịch, doanh thu hoàn tất, hoa hồng, hoàn, giữ, phải chi khớp ledger | AC-03, AC-04, AC-05, AC-06 |
| **BR-56** (Trang 31) | Truy vết đầy đủ người thực hiện, vai trò, thời điểm và nội dung thay đổi cho giá, hộp, đơn, thanh toán, No-show, hoàn, quyết định quản trị | AC-01, AC-02 |
| **CLAUDE.md** §4, 5, 7, 8 | `BigDecimal` VND nguyên đồng, `Clock`, `PageQuery`/`PageResponse`, OpenAPI đầy đủ, không sửa code cũ ngoài phạm vi, TDD | AC-03, AC-06, AC-07 |

---

## 9. Quyết định cần duyệt tại Gate A (`Q1`–`Q4`)

1. **Q1 (Index migration `V26` & giữ nguyên `StoreHistoryRecorder.ENTITY_TYPES` cho Owner):**
   - *Đề xuất (Recommended):*
     - Tạo migration nhẹ **`V26__admin_monitoring_indexes.sql`** chỉ thêm index tra cứu cho bảng sẵn có (`idx_audit_log_store_occurred ON audit_log(store_id, occurred_at DESC, id)`, `idx_audit_log_occurred ON audit_log(occurred_at DESC, id)`, `idx_audit_log_entity ON audit_log(entity_type, entity_id, occurred_at DESC)`, `idx_customer_order_store_accepted ON customer_order(store_id, accepted_at DESC)`), giữ nguyên **43** Hibernate entities và không sửa `V1`–`V25`.
     - Giữ nguyên danh sách `StoreHistoryRecorder.ENTITY_TYPES` dùng cho Owner `/stores/{storeId}/history` (tránh đổi hợp đồng T3.3), còn API Admin T8.5 cho phép xem **tất cả** `entity_type` hợp lệ đã ghi trong `audit_log` (gồm cả `PARTNER_APPLICATION`, `STORE_PAYOUT_ACCOUNT`, `SETTLEMENT`, `STORE_PAYOUT`, `FINANCIAL_POLICY`).
   *(Xác nhận tạo `V26` chỉ thêm index và tách phạm vi xem `audit_log` của Admin khỏi API Owner cũ?)*

2. **Q2 (Quy ước lọc khoảng thời gian `[from, to]` trong Thống kê Tài chính `UC-79`):**
   - Trong `UC-79`, khi Admin truyền `from` / `to` (UTC), các bảng nguồn dùng mốc thời gian khác nhau (`customer_order.accepted_at` / `completed_at`, `ledger_entry.occurred_at`, `settlement.confirmed_at`, `store_payout.transferred_at` / `confirmed_at`).
   - *Đề xuất (Recommended):*
     - **Các số liệu phát sinh trong kỳ (`from`/`to`):**
       - `orderCountsByStatus` & `totalOrderCount`: lọc theo `customer_order.accepted_at` (thời điểm đặt đơn, chuẩn `Clock`) trong `[from, to]`.
       - `completedOrderGrossRevenue` & `completedOrderCount`: các đơn `COMPLETED` có `completed_at` trong `[from, to]`.
       - Toàn bộ các chỉ số sổ cái (`verifiedCollectedAmount`, `verifiedRefundedAmount`, `netCashFlowAmount`, `grossCommissionAmount`, `commissionAdjustmentAmount`, `netCommissionAmount`, `grossStorePayableAmount`, `storePayableAdjustmentAmount`, `netStorePayableAmount`, `grossNoShowCompensationAmount`, `noShowAdjustmentAmount`, `netNoShowCompensationAmount`, `totalLedgerAllocatedAmount`): lọc trực tiếp theo `ledger_entry.occurred_at` trong `[from, to]`, đảm bảo **mỗi con số khớp chính xác 100% với `SUM` trên `ledger_entry` trong kỳ**.
       - Các chỉ số `paidOutVerifiedAmount` / `pendingPayoutAmount` (trong kỳ): lọc theo `coalesce(store_payout.confirmed_at, store_payout.created_at)` (hoặc `occurredAt` tương ứng) trong `[from, to]`.
     - **Các chỉ số trạng thái hiện tại của quyền lợi (`heldAmount`, `eligibleUnsettledAmount`, `confirmedRemainingPayableAmount`, `totalOutstandingPayableAmount`):** được tính tại thời điểm hiện tại (hoặc giới hạn trên các `ledger_entry` thuộc `[from, to]` khi có truyền kỳ — cụ thể: hiển thị trạng thái hiện hành của chính các bút toán thuộc phạm vi `[from, to]` để **`netStorePayableAmount + netNoShowCompensationAmount == heldAmountInScope + eligibleOrDraftUnsettledInScope + confirmedSettledInScope`** luôn khớp toán học!). Đồng thời cung cấp `currentSnapshot` (nếu có truyền `from`/`to`) hoặc tính thống nhất trên tập bút toán thuộc phạm vi lọc để khớp sổ cái tuyệt đối.
   *(Xác nhận quy ước lọc thời gian và công thức khớp 100% với `ledger_entry` như trên?)*

3. **Q3 (Cấu trúc API Kiểm tra giá Menu `UC-75`):**
   - *Đề xuất (Recommended):* Cung cấp `GET /api/v1/admin/stores/{storeId}/menu-price-audit` với query tùy chọn `menuItemId: UUID?`, `status: MenuItemStatus?`, `onlyMismatchedWithPackedBoxes: Boolean?` (chỉ lọc món có hộp đã đóng bị lệch giá so với giá niêm yết hiện tại), `page`, `size`. Mỗi item trả: thông tin món (`listedPrice` hiện tại), `priceHistory` (`MenuPriceHistory`), `documents` (`PriceReferenceDocument` áp dụng cho món & toàn menu), và `packedBoxPriceSummary` (danh sách tóm tắt theo từng mức `unitPriceSnapshot` trong `box_item`, số lượng hộp/món đã đóng, lần đóng gần nhất, và cờ `differsFromCurrentListedPrice`).
   *(Xác nhận API `UC-75` nêu trên?)*

4. **Q4 (Bộ API Endpoints & Tag trong `openapi.yaml`):**
   - *Đề xuất (Recommended):* Thêm **3** endpoints mới (nâng `140` → **`143`** paths, giữ nguyên **`31`** tags hoặc gắn vào `"Admin stores"` và `"Admin finance"` sẵn có):
     1. `GET /api/v1/admin/stores/{storeId}/menu-price-audit` (Tag `"Admin stores"`, `UC-75`)
     2. `GET /api/v1/admin/audit-logs` (Tag `"Admin stores"`, `UC-76` & `BR-56`: lọc tùy chọn `storeId`, `entityType`, `entityId`, `actorId`, `action`, `from`, `to`, `page`, `size`, `sort`)
     3. `GET /api/v1/admin/finance/statistics` (Tag `"Admin finance"`, `UC-79`: lọc tùy chọn `storeId`, `from`, `to`)
   *(Xác nhận bộ 3 API trên và dùng lại 2 tag `"Admin stores"` / `"Admin finance"` hiện có?)*
