# Roadmap backend giai đoạn 3–9

**Cập nhật:** 2026-11-13
**Quy tắc thực hiện:** [CLAUDE.md](../../CLAUDE.md). Mỗi phiên làm **một** task theo thứ tự; mỗi task đi qua
Gate A (`01-business-definition.md`) → Gate B (`02-plan.md`) → code → `03-implementation-control.txt`.

Card dưới đây là **khung phạm vi**, không phải plan chi tiết. AC trong card là tối thiểu; Gate A được bổ sung
nhưng không được bỏ bớt. Nếu khảo sát cho thấy card sai/thiếu, ghi đề xuất sửa card trong Gate A để người dùng duyệt.

## Quyết định chung đã chốt

| # | Quyết định |
|---|---|
| D1 | Một người làm toàn bộ giai đoạn 3–9, tuần tự; test dùng API thật của task trước. |
| D2 | Cổng thanh toán: **payOS** (VietQR, tiền thật về tài khoản ngân hàng). Qua interface `PaymentGateway`; test dùng `FakePaymentGateway`. |
| D3 | payOS không có API hoàn tiền: hoàn tiền do admin chuyển khoản thủ công, rồi xác nhận trên hệ thống kèm mã giao dịch/bằng chứng. |
| D4 | File lưu qua interface `FileStorage`; bản đầu dùng đĩa local (thư mục cấu hình, ngoài source). |
| D5 | Thời gian giữ hộp mặc định 10 phút (cấu hình được). |
| D6 | Mỗi endpoint có trong OpenAPI để thử trên Swagger `/docs`. |

## Tiến độ

| Giai đoạn | Task |
|---|---|
| 0. Baseline | [ ] T0 |
| 3. Cửa hàng và nhân viên | [ ] T3.1 · [ ] T3.2 · [ ] T3.3 · [ ] T3.4 |
| 4. Menu và Surprise Box | [ ] T4.1 · [ ] T4.2 · [ ] T4.3 · [ ] T4.4 |
| 5. Tìm kiếm và đặt hàng | [ ] T5.1 · [ ] T5.2 · [ ] T5.3 |
| 6. Thanh toán và hoàn tiền | [ ] T6.1 · [ ] T6.2 · [ ] T6.3 · [ ] T6.4 · [ ] T6.5 |
| 7. Bàn giao, hủy, no-show | [ ] T7.1 · [ ] T7.2 · [ ] T7.3 · [ ] T7.4 |
| 8. Khiếu nại, thông báo, đối soát | [ ] T8.1 · [ ] T8.2 · [ ] T8.3 · [ ] T8.4 |
| 9. Hoàn thiện và triển khai | [ ] T9.1 · [ ] T9.2 · [ ] T9.3 · [ ] T9.4 · [ ] T9.5 |

Khi xong một task: đổi `[ ]` thành `[x]` ở cả bảng trên và tiêu đề card.

---

## Giai đoạn 0

### [ ] T0 — Baseline và đối chiếu giai đoạn 1–2
- **Phụ thuộc:** không.
- **Mục tiêu:** xác nhận nền hiện tại chạy được trên macOS trước khi xây tiếp.
- **Phạm vi:** chạy `./gradlew build`, ghi số test theo suite; chạy app profile dev, thử Swagger
  register → verify → login → me. Đối chiếu điều kiện hoàn thành giai đoạn 1–2 (API lỗi thống nhất,
  cấu hình dev/test/prod, phân quyền theo cửa hàng). Kiểm tra seed `permission` đã có chưa.
- **Ngoài phạm vi:** sửa code. Chỗ thiếu chỉ ghi lại, đề xuất task bổ sung.
- **Kết quả:** `docs/specs/t0-baseline/03-implementation-control.txt` (không cần Gate A/B), gồm danh sách
  chỗ thiếu và lệnh chạy trên macOS.

---

## Giai đoạn 3 — Cửa hàng và nhân viên

**Điều kiện hoàn thành giai đoạn:** luồng đăng ký đối tác → duyệt → cấp quyền nhân viên chạy được trọn vẹn.

### [ ] T3.1 — Lưu trữ file
- **Phụ thuộc:** T0.
- **Mục tiêu:** upload/tải file an toàn để dùng cho hồ sơ đối tác, ảnh món, bằng chứng khiếu nại/hoàn tiền.
- **Phạm vi:** port `FileStorage` + adapter local disk; `POST /api/v1/files` (multipart) tạo `stored_file`
  (checksum SHA-256, mime, size, uploader); tải file chỉ cho người có quyền với đối tượng gắn file;
  whitelist mime (jpg/png/webp/pdf), giới hạn kích thước cấu hình được; tên lưu là key ngẫu nhiên.
- **Dữ liệu:** `stored_file`, `attachment`.
- **AC tối thiểu:** sai mime/quá cỡ → 400; người lạ tải file của người khác → 403/404; không path traversal;
  file mồ côi không lộ ra API công khai.
- **Test:** upload hợp lệ/không hợp lệ, quyền tải, tên file độc hại.

### [ ] T3.2 — Hồ sơ đối tác và duyệt
- **Phụ thuộc:** T3.1.
- **Mục tiêu:** khách nộp hồ sơ mở cửa hàng; admin duyệt hoặc từ chối.
- **Phạm vi:** nộp hồ sơ (thông tin cửa hàng, vị trí, giờ dự kiến, giấy tờ đính kèm) → tạo `store`
  `PENDING_APPROVAL` + `partner_application` `SUBMITTED`; nộp lại sau khi bị từ chối tăng `revision`;
  admin list/xem/duyệt/từ chối có lý do; duyệt → store `ACTIVE`, người nộp là owner. Audit.
- **Dữ liệu:** `store`, `partner_application`, `attachment`.
- **AC tối thiểu:** không phải admin → 403; duyệt hai lần đồng thời chỉ một lần thành công; hồ sơ đã chốt
  không sửa được; owner không tự duyệt.
- **Test:** luồng nộp → duyệt, nộp → từ chối → nộp lại, đồng thời duyệt.

### [ ] T3.3 — Thông tin cửa hàng và giờ mở cửa
- **Phụ thuộc:** T3.2.
- **Mục tiêu:** owner quản lý thông tin cửa hàng; khách xem cửa hàng công khai.
- **Phạm vi:** owner cập nhật tên/địa chỉ/tọa độ/mô tả; thay toàn bộ lịch `store_opening_hour`
  (nhiều ca/ngày, ca qua nửa đêm); admin tạm ngưng/mở lại cửa hàng; `GET` công khai chỉ trả store `ACTIVE`.
- **Dữ liệu:** `store`, `store_opening_hour`.
- **AC tối thiểu:** ca chồng lấn hoặc giờ sai → 400; owner khác → 403; store bị tạm ngưng không hiện công khai.
- **Test:** validate lịch, quyền, DTO công khai không lộ dữ liệu nội bộ.

### [ ] T3.4 — Cấp và thu hồi quyền nhân viên
- **Phụ thuộc:** T3.2.
- **Mục tiêu:** owner thêm nhân viên bằng email tài khoản đã có và cấp từng quyền.
- **Phạm vi:** seed permission code (ví dụ `MENU_MANAGE`, `BOX_PACK`, `BOX_INSPECT`, `BATCH_MANAGE`,
  `PICKUP_HANDLE`, `ORDER_VIEW`, `COMPLAINT_RESPOND`, `FINANCE_VIEW` — chốt ở Gate A); thêm/thu hồi
  assignment; cấp/bỏ permission; nhân viên xem các cửa hàng mình làm. Audit mọi thay đổi.
- **Dữ liệu:** `staff_assignment`, `staff_permission`, `permission`.
- **AC tối thiểu:** thu hồi có hiệu lực ngay request kế tiếp; staff không tự cấp quyền; không thêm owner làm staff
  của chính cửa hàng; cấp trùng không tạo bản ghi trùng.
- **Test:** cấp → staff gọi được; thu hồi → 403; owner cửa hàng khác → 403.

---

## Giai đoạn 4 — Menu và Surprise Box

**Điều kiện hoàn thành giai đoạn:** chỉ hộp hợp lệ được tính vào tồn khả dụng; thay đổi giá không làm sai hộp cũ.

### [ ] T4.1 — Danh mục và menu
- **Phụ thuộc:** T3.4.
- **Mục tiêu:** cửa hàng quản lý món với giá niêm yết có lịch sử.
- **Phạm vi:** seed `food_category`; CRUD `menu_item` (quyền `MENU_MANAGE`), ngừng bán thay vì xóa;
  đổi giá bắt buộc lý do và ghi `menu_price_history` trong cùng transaction; ảnh món qua `attachment`.
- **AC tối thiểu:** giá ≤ 0 hoặc lẻ đồng → 400; lịch sử giá không sửa/xóa được; món của store khác → 403.
- **Test:** đổi giá ghi lịch sử, đổi giá đồng thời (`@Version`) không mất cập nhật.

### [ ] T4.2 — Mẫu hộp
- **Phụ thuộc:** T4.1.
- **Mục tiêu:** định nghĩa loại Surprise Box của cửa hàng.
- **Phạm vi:** CRUD `box_template` (giá bán mặc định, giá trị tối thiểu mặc định, cảnh báo an toàn, ảnh bìa);
  bật/tắt; giá bán phải < giá trị tối thiểu (quy tắc chốt ở Gate A).
- **AC tối thiểu:** template đã dùng cho đợt bán không bị xóa cứng; sửa template không đổi đợt bán đã tạo.
- **Test:** validate giá, quyền, snapshot sang đợt bán (kết hợp T4.3).

### [ ] T4.3 — Đợt bán
- **Phụ thuộc:** T4.2.
- **Mục tiêu:** mở bán hộp theo ngày với khung giờ nhận.
- **Phạm vi:** tạo `sale_batch` từ template (snapshot giá, giá trị tối thiểu, cảnh báo); `order_deadline` ≤
  `pickup_starts_at` < `pickup_ends_at` và nằm trong giờ mở cửa; chuyển trạng thái DRAFT → OPEN ↔ PAUSED → ENDED;
  admin có thể BLOCKED. Quyền `BATCH_MANAGE`.
- **AC tối thiểu:** chuyển trạng thái sai → 409; mở bán khi chưa có hộp hợp lệ → được nhưng tồn = 0
  (hoặc chặn — chốt ở Gate A); store không ACTIVE không mở bán được.
- **Test:** máy trạng thái, validate thời gian theo `timezone` của store.

### [ ] T4.4 — Đóng hộp thực tế và kiểm tra
- **Phụ thuộc:** T4.3.
- **Mục tiêu:** chỉ hộp thật, đủ giá trị và an toàn mới bán được.
- **Phạm vi:** nhân viên (`BOX_PACK`) đóng `physical_box` với `box_item` (snapshot tên/giá/cảnh báo/hạn dùng),
  tính `total_listed_value`, `safe_until` = hạn sớm nhất; trạng thái `AWAITING_INSPECTION`;
  người kiểm (`BOX_INSPECT`) ghi `box_inspection` PASSED → `AVAILABLE`,
  FAILED → `BLOCKED`; API tồn khả dụng của đợt bán.
  **Đã chốt:** người đóng và người kiểm chỉ cần là owner hoặc staff ACTIVE của chính cửa hàng đó có quyền tương ứng;
  cùng một người được vừa đóng vừa kiểm.
- **AC tối thiểu:** tổng giá trị < `minimum_value` → không được PASSED; hộp hết hạn an toàn không tính tồn;
  đổi giá menu sau khi đóng không đổi giá trị hộp.
- **Test:** tính giá trị, kiểm tra hợp lệ/không hợp lệ, tồn khả dụng chỉ đếm hộp AVAILABLE còn hạn.

---

## Giai đoạn 5 — Tìm kiếm và đặt hàng

**Điều kiện hoàn thành giai đoạn:** không bán trùng; giữ đủ số hộp trong một transaction; hết hạn xử lý đúng.

### [ ] T5.1 — Tìm kiếm đợt bán công khai
- **Phụ thuộc:** T4.4.
- **Mục tiêu:** khách tìm hộp gần mình còn bán.
- **Phạm vi:** `GET` công khai đợt bán OPEN, còn trước deadline, tồn > 0; lọc theo khoảng cách
  (lat/lng + bán kính), ngày, khoảng giá, danh mục; sắp xếp whitelist; DTO công khai (không lộ id nhân viên,
  dữ liệu nội bộ); chi tiết đợt bán kèm cảnh báo an toàn và thông tin cửa hàng.
- **AC tối thiểu:** đợt PAUSED/hết tồn/quá deadline không hiện; phân trang có giới hạn.
- **Test:** lọc khoảng cách, lọc giá, dữ liệu ẩn.

### [ ] T5.2 — Tạo đơn và giữ hộp
- **Phụ thuộc:** T5.1.
- **Mục tiêu:** đặt N hộp, giữ trong 10 phút chờ thanh toán.
- **Phạm vi:** `POST` tạo `customer_order` `PENDING_PAYMENT` với snapshot (giá, giá trị tối thiểu, cảnh báo,
  địa chỉ, khung giờ, `financial_policy` hiện hành và các tỷ lệ); khóa và chọn N hộp AVAILABLE
  (`FOR UPDATE SKIP LOCKED`) → `box_allocation` HELD, hộp `RESERVED`; khách phải chấp nhận điều khoản;
  header `Idempotency-Key`. Seed `financial_policy` mặc định nếu chưa có.
- **AC tối thiểu:** không đủ hộp → 409, không giữ một phần; hai khách tranh hộp cuối chỉ một người được;
  gửi lại cùng Idempotency-Key trả đúng đơn cũ.
- **Test:** đồng thời nhiều khách tranh tồn, idempotency, snapshot không đổi khi giá/chính sách đổi.

### [ ] T5.3 — Hết hạn giữ hộp và đơn của khách
- **Phụ thuộc:** T5.2.
- **Mục tiêu:** trả hộp về khi khách không thanh toán; khách xem đơn của mình.
- **Phạm vi:** job quét đơn `PENDING_PAYMENT` quá `hold_expires_at` → `EXPIRED`, allocation `RELEASED`,
  hộp về `AVAILABLE` (nếu còn hạn an toàn); khách hủy đơn chưa thanh toán; list/chi tiết đơn của khách;
  cửa hàng xem đơn của store (`ORDER_VIEW`).
- **AC tối thiểu:** job chạy trùng/hai instance không nhả hộp hai lần; đơn đã CONFIRMED không bị hết hạn;
  khách không xem đơn người khác.
- **Test:** `Clock` cố định, job đồng thời, quyền xem đơn.
- **Lưu ý tích hợp:** T6.3 sẽ phải xử lý tranh chấp giữa "job hết hạn" và "webhook tiền đến" trên cùng đơn —
  thiết kế khóa đơn ở đây để T6 dùng lại.

---

## Giai đoạn 6 — Thanh toán và hoàn tiền nền tảng

**Điều kiện hoàn thành giai đoạn:** đơn chỉ xác nhận khi đủ tiền hợp lệ; không ghi tiền hoặc hoàn tiền trùng.

### [ ] T6.1 — Port thanh toán và adapter payOS
- **Phụ thuộc:** T0 (có thể làm sớm, nhưng giữ thứ tự).
- **Mục tiêu:** tách tích hợp payOS khỏi nghiệp vụ.
- **Phạm vi:** interface `PaymentGateway` (tạo link/QR, tra cứu trạng thái, hủy link, xác minh webhook);
  `PayOsPaymentGateway` theo tài liệu payOS hiện hành (kiểm tra lại docs chính thức trong Gate A: chữ ký
  HMAC-SHA256, `orderCode` kiểu số); `FakePaymentGateway` cho test; cấu hình `payos.*` từ env, timeout;
  cập nhật `.env.example`.
- **AC tối thiểu:** sai checksum → từ chối; lỗi mạng/timeout → lỗi có kiểu rõ ràng, không lộ key;
  không có key → app dev vẫn chạy được với gateway giả hoặc báo cấu hình thiếu rõ ràng (chốt ở Gate A).
- **Test:** unit test checksum với vector mẫu từ docs payOS; adapter test với HTTP server giả (không gọi payOS thật).

### [ ] T6.2 — Tạo yêu cầu thanh toán
- **Phụ thuộc:** T5.3, T6.1.
- **Mục tiêu:** khách nhận link/QR để trả tiền đơn.
- **Phạm vi:** `POST /orders/{id}/payment-requests`: chỉ đơn của mình, `PENDING_PAYMENT`, còn hạn giữ;
  ánh xạ đơn → `orderCode` số duy nhất (migration nếu cần); `expected_amount` = `total_amount`;
  link hết hạn ≤ `hold_expires_at`; idempotent (gọi lại trả request còn hiệu lực); ghi request trước,
  gọi payOS ngoài transaction, cập nhật `payment_url`/`READY` hoặc `FAILED`.
- **AC tối thiểu:** đơn người khác → 403/404; đơn đã CONFIRMED/EXPIRED → 409; payOS lỗi không để đơn ở trạng thái mơ hồ.
- **Test:** idempotency, đồng thời hai request tạo link, gateway giả lỗi.

### [ ] T6.3 — Webhook xác nhận thanh toán
- **Phụ thuộc:** T6.2.
- **Mục tiêu:** ghi nhận tiền vào và xác nhận đơn đúng một lần.
- **Phạm vi:** endpoint webhook công khai (không JWT, có rate limit hợp lý); xác minh checksum; tìm request theo
  `orderCode`; ghi `payment` unique theo (`provider`, `provider_transaction_id`); khóa đơn; nếu đủ tiền và còn hạn
  → đơn `CONFIRMED`, allocation `CONFIRMED`, hộp `ALLOCATED`, `financial_event` PAYMENT_RECEIVED +
  `ledger_entry` PAYMENT_RECEIPT; trả mã phản hồi payOS mong đợi.
- **AC tối thiểu:** webhook trùng → không ghi lần hai; checksum sai → không đổi gì; webhook và job hết hạn
  cùng lúc → kết quả nhất quán (đã xác nhận thì không hết hạn, đã hết hạn thì sang T6.4).
- **Test:** đồng thời webhook trùng, đồng thời webhook vs job hết hạn, checksum sai.
- **Thử thật:** hướng dẫn người dùng cấu hình webhook URL (ngrok/cloudflared) và tự chuyển số tiền nhỏ.

### [ ] T6.4 — Tiền đến muộn, sai số tiền và đối chiếu
- **Phụ thuộc:** T6.3.
- **Mục tiêu:** không mất tiền khi tình huống không chuẩn.
- **Phạm vi:** tiền đến khi đơn đã EXPIRED/CANCELLED, thiếu hoặc thừa tiền → `payment` `REQUIRES_REVIEW`,
  không xác nhận đơn (trừ khi Gate A chốt cho phép xác nhận lại nếu còn hộp), tạo nghĩa vụ hoàn tiền
  (`refund_request`); job đối chiếu tra cứu payOS cho request `READY` quá hạn (phòng mất webhook);
  API admin tra cứu giao dịch theo đơn/mã giao dịch.
- **AC tối thiểu:** mọi đồng nhận được đều nằm ở một trong: đã dùng cho đơn, chờ hoàn, đã hoàn;
  đối chiếu chạy lại không tạo payment trùng.
- **Test:** từng tình huống tiền muộn/thiếu/thừa, job đối chiếu với gateway giả.

### [ ] T6.5 — Thực thi hoàn tiền thủ công có kiểm soát
- **Phụ thuộc:** T6.4.
- **Mục tiêu:** hoàn tiền đúng số, đúng người, không trùng.
- **Phạm vi:** máy trạng thái `refund_request` (PENDING → APPROVED/REJECTED → FULFILLED) và `refund`
  (PENDING → PROCESSING → SUCCEEDED/FAILED); admin duyệt số tiền; admin xác nhận đã chuyển khoản kèm mã giao dịch
  ngân hàng + file bằng chứng → `refund` SUCCEEDED, `financial_event` REFUND_SUCCEEDED, `ledger_entry` CUSTOMER_REFUND;
  khách xem trạng thái hoàn tiền. Đây là cơ chế dùng chung cho T7.3, T7.4, T8.1.
- **AC tối thiểu:** tổng hoàn ≤ tổng tiền đã xác minh của đơn; xác nhận hai lần đồng thời chỉ ghi một lần;
  mã giao dịch ngân hàng không dùng lại cho hoàn khác.
- **Test:** vượt hạn mức, đồng thời xác nhận, ledger đúng số.

---

## Giai đoạn 7 — Bàn giao, hủy đơn và no-show

**Điều kiện hoàn thành giai đoạn:** bàn giao/hủy/no-show không tạo kết quả mâu thuẫn; phát sinh nghĩa vụ hoàn đúng.

### [ ] T7.1 — Mã nhận hàng
- **Phụ thuộc:** T6.3.
- **Mục tiêu:** khách có mã để nhận hộp; cửa hàng xác minh mã.
- **Phạm vi:** sinh mã khi đơn CONFIRMED (DB chỉ lưu `pickup_code_hash`); khách lấy mã (hiển thị lại được →
  chốt ở Gate A cách lưu: mã hóa hai chiều hay cấp lại mã mới); nhân viên tra đơn bằng mã.
- **AC tối thiểu:** mã sai nhiều lần bị rate limit; mã chỉ dùng cho đúng cửa hàng; không lộ hash.
- **Test:** sinh/xác minh, brute-force bị chặn, cửa hàng khác không tra được.

### [ ] T7.2 — Phiên bàn giao và sự cố
- **Phụ thuộc:** T7.1.
- **Mục tiêu:** bàn giao hộp có ghi nhận, kể cả khi có vấn đề.
- **Phạm vi:** nhân viên (`PICKUP_HANDLE`) mở `pickup_session` (idempotency key) bằng mã, đơn
  `HANDOVER_IN_PROGRESS`; hoàn tất → đơn `COMPLETED`, allocation/hộp `DELIVERED`, `financial_event`
  ORDER_COMPLETED; ghi `pickup_incident` (hộp hỏng, khách đến muộn...) và cách xử lý; ngoài khung giờ nhận → chặn
  hoặc cần lý do (chốt ở Gate A).
- **AC tối thiểu:** hai nhân viên mở phiên cùng đơn → một phiên; hoàn tất lặp không ghi trùng event.
- **Test:** đồng thời mở phiên, luồng đầy đủ, sự cố.

### [ ] T7.3 — Hủy đơn theo chính sách
- **Phụ thuộc:** T6.5, T7.2.
- **Mục tiêu:** hủy đơn đã thanh toán với số tiền hoàn đúng điều khoản đã snapshot.
- **Phạm vi:** khách hủy (trước mốc cho phép trong `cancellation_terms_snapshot`); cửa hàng hủy (hoàn 100%,
  có lý do, audit); đơn `CANCELLED`, allocation `RELEASED`, hộp quay lại `AWAITING_INSPECTION` để tái kiểm
  trước khi bán lại; tạo `refund_request` theo số tính được (dùng T6.5).
- **AC tối thiểu:** không hủy đơn đã COMPLETED/đang bàn giao; hủy và bàn giao đồng thời → một kết quả.
- **Test:** từng loại hủy, số tiền hoàn, đồng thời hủy vs bàn giao.

### [ ] T7.4 — No-show
- **Phụ thuộc:** T7.3.
- **Mục tiêu:** xử lý khách không đến nhận một cách công bằng.
- **Phạm vi:** job sau `pickup_ends_at` chuyển đơn CONFIRMED → `PENDING_NO_SHOW`; cửa hàng xác minh kèm bằng
  chứng → `no_show_case`, đơn `NO_SHOW`; tính tiền bằng `NoShowAmounts` (giữ lại theo `no_show_retention_rate`,
  hoàn phần còn lại qua T6.5); khoản bù cho cửa hàng `HELD` đến hết `dispute_deadline`; hộp sang tái kiểm.
  Có sự cố đã ghi (khách đã đến) → không được kết luận no-show.
- **AC tối thiểu:** số giữ + số hoàn = số đã trả; no-show và bàn giao muộn đồng thời → một kết quả.
- **Test:** tính tiền (mở rộng `NoShowAmountsTests`), job, tranh chấp trạng thái.

---

## Giai đoạn 8 — Khiếu nại, thông báo và đối soát

**Điều kiện hoàn thành giai đoạn:** truy vết được từng khoản; không giải tỏa hoặc chi tiền chưa đủ điều kiện.

### [ ] T8.1 — Khiếu nại
- **Phụ thuộc:** T7.4.
- **Mục tiêu:** khách khiếu nại trong cửa sổ tranh chấp; admin phân xử.
- **Phạm vi:** khách tạo `complaint` (loại, mô tả, ảnh bằng chứng) cho đơn COMPLETED/NO_SHOW trong
  `dispute_window_hours`; cửa hàng phản hồi (`COMPLAINT_RESPOND`); admin chuyển trạng thái, yêu cầu thêm bằng chứng,
  ra quyết định → có thể tạo `refund_request` (T6.5); khiếu nại mở giữ khoản bù/khoản phải trả liên quan ở trạng
  thái `DISPUTED`; `financial_event` COMPLAINT_RESOLVED.
- **AC tối thiểu:** quá hạn → 409; mỗi đơn tối đa một khiếu nại mở; quyết định đã chốt không sửa.
- **Test:** luồng đầy đủ, hết hạn, giữ tiền khi tranh chấp.

### [ ] T8.2 — Thông báo
- **Phụ thuộc:** T8.1.
- **Mục tiêu:** người dùng biết các sự kiện quan trọng; theo dõi được đã gửi hay chưa.
- **Phạm vi:** ghi `notification` (outbox) trong cùng transaction với sự kiện (xác nhận đơn, hủy, no-show, hoàn tiền,
  khiếu nại, đối soát); worker gửi qua port `NotificationSender` (bản đầu: email qua mail sender sẵn có + lưu
  in-app), retry có giới hạn, trạng thái PENDING → SENT/FAILED; API danh sách thông báo của tôi, đánh dấu đã đọc.
- **AC tối thiểu:** rollback sự kiện thì không có thông báo; gửi lỗi không làm hỏng nghiệp vụ; không gửi trùng.
- **Test:** outbox + rollback, retry, quyền xem.

### [ ] T8.3 — Hoa hồng và khoản phải trả cửa hàng
- **Phụ thuộc:** T8.1.
- **Mục tiêu:** tính đúng phần nền tảng và phần cửa hàng cho từng đơn.
- **Phạm vi:** khi đơn COMPLETED (hoặc no-show đủ điều kiện) và đã qua cửa sổ tranh chấp không có khiếu nại mở →
  ghi `ledger_entry` PLATFORM_COMMISSION + STORE_PAYABLE (hoặc NO_SHOW_COMPENSATION) theo tỷ lệ snapshot của đơn;
  job xét đủ điều kiện; báo cáo số dư theo cửa hàng (`FINANCE_VIEW`).
- **AC tối thiểu:** tổng các bút toán của một đơn khớp tiền thu − tiền hoàn; chạy job lại không ghi trùng
  (`business_key`); làm tròn nhất quán (quy tắc chốt ở Gate A).
- **Test:** tính tiền nhiều tình huống, idempotency job.

### [ ] T8.4 — Đối soát và chi trả
- **Phụ thuộc:** T8.3.
- **Mục tiêu:** chi tiền cho cửa hàng theo kỳ, có bằng chứng.
- **Phạm vi:** cửa hàng khai `store_payout_account` (admin xác minh); admin tạo `settlement` kỳ (DRAFT) gom
  `settlement_line` từ ledger đủ điều kiện chưa đối soát; xác nhận → CONFIRMED (trigger khóa line);
  ghi `store_payout` với mã chuyển khoản + bằng chứng → PARTIALLY_PAID/PAID; cửa hàng xem bảng đối soát.
- **AC tối thiểu:** một ledger entry chỉ thuộc một settlement; không chi quá tổng settlement; khoản đang tranh chấp
  không vào settlement.
- **Test:** tạo kỳ, đồng thời tạo hai kỳ chồng nhau, chi trả từng phần.

---

## Giai đoạn 9 — Hoàn thiện và triển khai

**Điều kiện hoàn thành giai đoạn:** chạy được các kịch bản MVP hoàn chỉnh và phục hồi được khi có sự cố.

### [ ] T9.1 — Test xuyên suốt và tải đồng thời
- **Phụ thuộc:** T8.4.
- **Phạm vi:** test HTTP các kịch bản MVP: đăng ký đối tác → duyệt → menu → đóng hộp → đặt → trả tiền → nhận →
  đối soát → chi trả; các nhánh hủy, no-show, khiếu nại, tiền muộn. Test tải đồng thời (nhiều khách tranh
  hộp, webhook dồn dập) đo không bán trùng/không ghi trùng. Kịch bản bấm tay trên Swagger.

### [ ] T9.2 — Giám sát
- **Phụ thuộc:** T9.1.
- **Phạm vi:** Actuator metrics (Micrometer/Prometheus format), log có cấu trúc kèm requestId, chỉ số nghiệp vụ
  (thanh toán treo, payment REQUIRES_REVIEW, refund chờ, job lỗi), endpoint admin xem tình trạng; không lộ thông tin
  nhạy cảm ra công khai.

### [ ] T9.3 — Rà soát bảo mật
- **Phụ thuộc:** T9.1.
- **Phạm vi:** rà soát theo OWASP API Top 10 (IDOR trên mọi endpoint có id, mass assignment, rate limit,
  upload, webhook), kiểm tra dependency có CVE, header bảo mật, CORS cho app Android; sửa lỗi tìm thấy kèm test.

### [ ] T9.4 — Backup và phục hồi
- **Phụ thuộc:** T9.1.
- **Phạm vi:** script `pg_dump`/restore + thư mục file upload; runbook; thử khôi phục vào DB trống và chạy
  smoke test; ghi RPO/RTO mục tiêu.

### [ ] T9.5 — CI/CD và staging
- **Phụ thuộc:** T9.2, T9.3, T9.4.
- **Phạm vi:** GitHub Actions chạy `./gradlew build` (có Docker cho Testcontainers) trên PR; Dockerfile backend;
  compose staging (app + db + volume file); quản lý secret qua biến môi trường của nền tảng; hướng dẫn deploy và
  cấu hình webhook payOS trỏ về staging.
