# Roadmap backend giai đoạn 3–9

**Cập nhật:** 2026-11-13 (đối chiếu tài liệu nghiệp vụ final, class diagram, sequence/activity 01·02·03·06·08·10)
**Quy tắc thực hiện:** [CLAUDE.md](../../CLAUDE.md). Mỗi phiên làm **một** task theo thứ tự; mỗi task đi qua
Gate A (`01-business-definition.md`) → Gate B (`02-plan.md`) → code → `03-implementation-control.txt`.

Card dưới đây là **khung phạm vi**, không phải plan chi tiết. AC trong card là tối thiểu; Gate A được bổ sung
nhưng không được bỏ bớt. Gate A phải trích **UC-xx / BR-xx / SD-xx** của tài liệu nghiệp vụ mà task đáp ứng.
Nếu khảo sát cho thấy card sai/thiếu, ghi đề xuất sửa card trong Gate A để người dùng duyệt.

## Tài liệu nguồn (ngoài repo, trên máy người dùng)

Đường dẫn tương đối từ `CODE/backend/`:

| Tài liệu | Đường dẫn |
|---|---|
| Nghiệp vụ (vision, FR, NFR, BR-01..56, UC-01..87) | `../../../Nghiệp Vụ FOOD&RECSUE_Finalpdf.pdf` (bản gốc `.docx` cùng thư mục) |
| Class diagram | `../../../class.png` |
| Sequence SD-01,02,03,06,08,10 | `../../../sequence.zip` |
| Activity 01,02,03,06,08,10 | `../../../sodohoatdong.zip` |
| ERD | `../../../ERD_FOODRESUE.drawio.png` |

Schema thực tế trong `src/main/resources/db/migration` là chuẩn khi tên/field khác class diagram
(class diagram dùng tên tiếng Việt: DonHang = `customer_order`, HopThucTe = `physical_box`, PhanBoHop = `box_allocation`...).
Tài liệu nhắc tới các quyết định D01, D02, D04, D05, D06, D10 nhưng chưa có văn bản; task nào chạm tới phải nêu
lựa chọn đề xuất trong Gate A.

## Quyết định chung đã chốt

| # | Quyết định |
|---|---|
| D1 | Một người làm toàn bộ giai đoạn 3–9, tuần tự; test dùng API thật của task trước. |
| D2 | Cổng thanh toán: **payOS** (VietQR, tiền thật về tài khoản ngân hàng). Qua interface `PaymentGateway`; test dùng `FakePaymentGateway`. |
| D3 | payOS không có API hoàn tiền: hoàn tiền do admin chuyển khoản thủ công rồi xác nhận trên hệ thống kèm mã giao dịch/bằng chứng. **Lệch có chủ đích** so với UC-72 (tài liệu giả định dịch vụ thanh toán tự hoàn); vẫn giữ bất biến "ghi một lần, gắn giao dịch gốc, không báo thành công khi chưa xác minh". |
| D4 | File lưu qua interface `FileStorage`; bản đầu dùng đĩa local (thư mục cấu hình, ngoài source). |
| D5 | Giữ hộp 10 phút (BR-23), cấu hình được. |
| D6 | Mỗi endpoint có trong OpenAPI để thử trên Swagger `/docs`. |
| D7 | Người đóng hộp và người kiểm hộp chỉ cần là owner hoặc staff ACTIVE của cửa hàng có quyền tương ứng; cùng một người được. |
| D8 | MVP: một chủ một cửa hàng (UC-38, UC-67). |
| D9 | Ngoài phạm vi MVP theo tài liệu: đặt cọc (UC-16), thu tiền tại cửa hàng (UC-31), hoàn tiền tại cửa hàng (UC-73), nhiều chi nhánh, chi trả tự động. Field `tyLeDatCoc`/`hinhThucThanhToan` trong class diagram bỏ qua. |
| D10 | Mọi chức năng tìm kiếm/xem Surprise Box yêu cầu khách đã đăng nhập (tiền điều kiện UC-07..12). |

## Tiến độ

| Giai đoạn | Task |
|---|---|
| 0. Baseline | [x] T0 · [x] T2.1 |
| 3. Cửa hàng và nhân viên | [ ] T3.1 · [ ] T3.2 · [ ] T3.3 · [ ] T3.4 · [ ] T3.5 |
| 4. Menu, Surprise Box, chính sách | [ ] T4.1 · [ ] T4.2 · [ ] T4.3 · [ ] T4.4 · [ ] T4.5 |
| 5. Tìm kiếm và đặt hàng | [ ] T5.1 · [ ] T5.2 · [ ] T5.3 |
| 6. Thanh toán và hoàn tiền | [ ] T6.1 · [ ] T6.2 · [ ] T6.3 · [ ] T6.4 · [ ] T6.5 |
| 7. Bàn giao, hủy, no-show | [ ] T7.1 · [ ] T7.2 · [ ] T7.3 · [ ] T7.4 · [ ] T7.5 |
| 8. Khiếu nại, thông báo, đối soát | [ ] T8.1 · [ ] T8.2 · [ ] T8.3 · [ ] T8.4 · [ ] T8.5 |
| 9. Hoàn thiện và triển khai | [ ] T9.1 · [ ] T9.2 · [ ] T9.3 · [ ] T9.4 · [ ] T9.5 |

Khi xong một task: đổi `[ ]` thành `[x]` ở cả bảng trên và tiêu đề card.

---

## Giai đoạn 0 — Nền

### [x] T0 — Baseline và đối chiếu giai đoạn 1–2
- **Phụ thuộc:** không.
- **Mục tiêu:** xác nhận nền hiện tại chạy được trên macOS trước khi xây tiếp.
- **Phạm vi:** chạy `./gradlew build`, ghi số test theo suite; chạy app profile dev, thử Swagger
  register → verify → login → me. Đối chiếu UC-01..05 và điều kiện hoàn thành giai đoạn 1–2. Kiểm tra seed
  `permission`, `food_category`, `financial_policy` đã có chưa.
- **Ngoài phạm vi:** sửa code. Chỗ thiếu chỉ ghi lại, đề xuất task bổ sung.
- **Kết quả:** `docs/specs/t0-baseline/03-implementation-control.txt` (không cần Gate A/B).

### [x] T2.1 — Cập nhật hồ sơ cá nhân
- **Phụ thuộc:** T0. **Tài liệu:** UC-05, UC-06.
- **Phạm vi:** `PATCH /api/v1/accounts/me` chỉ cho trường được phép (Gate A chốt: chỉ tên hiển thị, không thêm số điện thoại);
  kiểm tra `@Version` tránh ghi đè.
- **Kết quả:** `docs/specs/t2-1-profile-update/` (Gate A/B, `03-implementation-control.txt`).
- **AC tối thiểu:** gửi kèm role/store/email/password → 400 hoặc bị bỏ qua có báo, không nâng quyền (UC-06 E2);
  dữ liệu sai → 400 kèm field.

---

## Giai đoạn 3 — Cửa hàng và nhân viên

**Điều kiện hoàn thành giai đoạn:** luồng đăng ký đối tác → duyệt → cấp quyền nhân viên chạy được trọn vẹn.

### [ ] T3.1 — Lưu trữ file
- **Phụ thuộc:** T0.
- **Mục tiêu:** upload/tải file an toàn cho hồ sơ đối tác, tài liệu giá, ảnh hộp, bằng chứng khiếu nại/hoàn/chi trả.
- **Phạm vi:** port `FileStorage` + adapter local disk; `POST /api/v1/files` (multipart) tạo `stored_file`
  (checksum SHA-256, mime, size, uploader); tải file chỉ cho người có quyền với đối tượng gắn file;
  whitelist mime (jpg/png/webp/pdf), giới hạn kích thước cấu hình được; tên lưu là key ngẫu nhiên.
- **Dữ liệu:** `stored_file`, `attachment`.
- **AC tối thiểu:** sai mime/quá cỡ → 400; người lạ tải file của người khác → 403/404; không path traversal;
  file chưa gắn đối tượng chỉ người upload thấy.

### [ ] T3.2 — Đăng ký đối tác và xét duyệt
- **Phụ thuộc:** T3.1. **Tài liệu:** UC-38, UC-39, UC-66, UC-67, BR-02, SD-01.
- **Phạm vi:** người dùng đã đăng nhập nộp hồ sơ (thông tin cửa hàng, địa chỉ, tọa độ, giờ hoạt động, giấy tờ theo
  danh mục D04) → `store` `PENDING_APPROVAL` + `partner_application` `SUBMITTED`. Đã có hồ sơ chờ → trả hồ sơ đó,
  không tạo trùng. Admin xem danh sách/chi tiết và ra **một trong ba** kết quả: yêu cầu bổ sung (lưu nội dung cần
  làm rõ), từ chối (bắt buộc lý do), chấp thuận. Nộp lại sau bổ sung/từ chối tăng `revision`, giữ lịch sử.
  Chấp thuận chỉ khi hồ sơ còn chờ **và** người nộp chưa sở hữu cửa hàng được duyệt → store `ACTIVE`, người nộp
  thành owner. Người nộp xem trạng thái và lịch sử xét duyệt. Audit.
- **Dữ liệu:** `store`, `partner_application`, `attachment`. Cần migration thêm trạng thái "cần bổ sung"
  (`ApplicationStatus` hiện chỉ có SUBMITTED/APPROVED/REJECTED) và chặn một chủ nhiều cửa hàng ở DB.
- **AC tối thiểu:** không phải admin → 403; duyệt hai lần đồng thời chỉ một thành công, lần sau thấy kết quả đã có
  (SD-01 #32); đã sở hữu cửa hàng → không nộp/không duyệt được; hồ sơ thiếu → 400; được duyệt chưa đồng nghĩa mở bán.
- **Test:** nộp → duyệt; nộp → bổ sung → nộp lại → duyệt; nộp → từ chối → nộp lại; đồng thời duyệt.

### [ ] T3.3 — Thông tin cửa hàng, giờ mở cửa, khóa/mở cửa hàng
- **Phụ thuộc:** T3.2. **Tài liệu:** UC-40, UC-41, UC-77 (phần trạng thái), BR-55.
- **Phạm vi:** owner xem/cập nhật tên, mô tả, địa chỉ, tọa độ, lịch `store_opening_hour` (nhiều ca/ngày, ca qua
  nửa đêm); lưu lịch sử thay đổi; thay đổi thuộc diện phải xét duyệt (D04) chuyển thành yêu cầu xem xét, chưa
  áp dụng; không áp ngược nơi/giờ nhận cho đơn đã xác nhận (đơn dùng snapshot). Admin tạm khóa/kích hoạt lại
  cửa hàng có lý do: khóa → chặn mở bán/nhận đơn mới, ngừng các đợt chưa phát sinh đơn. (Xử lý đơn đã xác nhận
  khi khóa: T7.5.) Owner không tự kích hoạt cửa hàng bị khóa.
- **AC tối thiểu:** ca chồng lấn/giờ sai → 400; owner khác → 403; store bị khóa không hiện cho khách và không mở bán.

### [ ] T3.4 — Quản lý nhân viên và phân quyền
- **Phụ thuộc:** T3.2. **Tài liệu:** UC-43..46, BR-03, SD-02.
- **Phạm vi:** seed permission code nghiệp vụ staff (đề xuất: `MENU_VIEW`, `BOX_PACK`, `BOX_MANAGE`, `PICKUP_HANDLE`,
  `ORDER_VIEW`, `NO_SHOW_CONFIRM` — chốt ở Gate A). **Không tồn tại** quyền sửa giá, giá bán, hoa hồng, chính sách
  cho staff. Owner xem danh sách nhân viên; tạo nhân viên: tìm theo email, có tài khoản → liên kết; chưa có → tạo
  tài khoản và gửi link thiết lập mật khẩu qua cơ chế reset sẵn có (D01, chốt ở Gate A); đổi bộ quyền; thu hồi
  quyền có lý do. Audit mọi thay đổi. Staff xem các cửa hàng mình làm.
- **Dữ liệu:** `staff_assignment`, `staff_permission`, `permission`, `account`.
- **AC tối thiểu:** thu hồi có hiệu lực ngay request kế tiếp (UC-46); cấp quyền owner/admin/giá → từ chối;
  không tạo tài khoản trùng; không thêm owner làm staff cửa hàng mình; thu hồi không khóa tài khoản toàn nền tảng.

### [ ] T3.5 — Quản trị tài khoản người dùng
- **Phụ thuộc:** T3.4. **Tài liệu:** UC-68, UC-69, UC-70.
- **Phạm vi:** admin tìm tài khoản theo email/vai trò/trạng thái (phân trang), xem chi tiết (vai trò, cửa hàng liên
  kết, trạng thái, lịch sử) không lộ mật khẩu; khóa/mở khóa có lý do → khóa có hiệu lực request kế tiếp (đã có cơ chế
  kiểm tra ở phiên). Khôi phục không tự kích hoạt cửa hàng hay cấp thêm vai trò. Khóa owner đang có đơn tồn đọng:
  cảnh báo phương án (D10), không xóa dữ liệu.
- **AC tối thiểu:** không phải admin → 403; admin không tự khóa chính mình; audit đủ người/thời điểm/lý do.

---

## Giai đoạn 4 — Menu, Surprise Box và chính sách tài chính

**Điều kiện hoàn thành giai đoạn:** chỉ hộp hợp lệ được tính vào tồn khả dụng; thay đổi giá không làm sai hộp cũ.

### [ ] T4.1 — Menu nội bộ và lịch sử giá
- **Phụ thuộc:** T3.4. **Tài liệu:** UC-24, UC-50..53, UC-75 (phần xem), BR-04..08.
- **Phạm vi:** seed `food_category`; owner tạo/sửa `menu_item` (tên, nhóm, giá niêm yết, thành phần, cảnh báo);
  đổi giá bắt buộc lý do, ghi `menu_price_history` cùng transaction; ngừng/mở lại kinh doanh có lý do (mở lại kiểm
  tra đủ dữ liệu); tài liệu tham chiếu giá (UC-53) qua `attachment`, có phiên bản. Staff có quyền và owner xem menu;
  admin xem menu + lịch sử giá + tài liệu của mọi cửa hàng. Customer không xem được menu (BR-05).
- **AC tối thiểu:** staff sửa giá → 403; giá ≤ 0 hoặc lẻ đồng → 400; sửa đồng thời → 409 (không ghi đè im lặng,
  UC-51 E2); lịch sử giá không sửa/xóa được.

### [ ] T4.2 — Mẫu Surprise Box
- **Phụ thuộc:** T4.1. **Tài liệu:** UC-57, UC-58, BR-09.
- **Phạm vi:** owner tạo/sửa/ngừng `box_template` (tên, nhóm thực phẩm, mô tả dự kiến, cảnh báo an toàn, ảnh bìa,
  giá bán và giá trị tối thiểu **mặc định** gợi ý cho đợt bán); không công khai món thực tế; không xóa cứng mẫu đã dùng.
- **AC tối thiểu:** thiếu cảnh báo bắt buộc → 400; sửa mẫu không đổi đợt/đơn đã tạo; tạo mẫu không sinh tồn kho.

### [ ] T4.3 — Đợt mở bán
- **Phụ thuộc:** T4.2. **Tài liệu:** UC-59..62, BR-10, BR-15, BR-16, BR-18, BR-21, SD-03.
- **Phạm vi:** owner tạo `sale_batch` DRAFT từ mẫu (ngày bán, giá bán cố định, giá trị tối thiểu, hạn đặt, khung
  nhận; snapshot cảnh báo); kiểm tra `order_deadline` ≤ `pickup_starts_at` < `pickup_ends_at`, nằm trong giờ hoạt động
  theo `timezone` cửa hàng. Sửa đợt (UC-60) chỉ trong phạm vi không ảnh hưởng đơn đã xác nhận/đang giữ. Vận hành:
  công khai (OPEN) chỉ khi cửa hàng ACTIVE, còn hạn đặt **và có ít nhất một hộp đạt yêu cầu** (SD-03 #19: chưa có
  hộp → giữ DRAFT, báo cần đóng hộp); tạm dừng/tiếp tục (tiếp tục kiểm tra lại như công khai)/kết thúc; admin BLOCKED.
  API số lượng hộp theo trạng thái (UC-62: đã đóng, đủ điều kiện, đang giữ, đã phân bổ, đã giao, bị chặn, khả dụng).
- **AC tối thiểu:** chuyển trạng thái sai → 409; store bị khóa → không mở; số khả dụng luôn tính từ hộp thực tế,
  không có trường "số lượng khai báo".

### [ ] T4.4 — Đóng gói hộp thực tế
- **Phụ thuộc:** T4.3. **Tài liệu:** UC-25, UC-26, UC-32, UC-33, BR-11..14, BR-17, BR-18, D7.
- **Phạm vi:** staff (`BOX_PACK`) hoặc owner mở phiếu đóng cho một đợt: phiếu trống hoặc **điền nhanh từ hộp đã đóng
  của cùng cửa hàng** (chỉ copy món + số lượng); chỉnh theo thực tế, nhập hạn dùng và cách bảo quản; backend lấy giá
  và trạng thái món **hiện hành**, tính `total_listed_value` = Σ giá × số lượng, `safe_until` = hạn sớm nhất; người đóng
  xác nhận đã kiểm tra thực phẩm → ghi `box_inspection` PASSED → hộp `AVAILABLE` (hoặc FAILED → không vào lượng bán).
  Cấp `box_code` duy nhất. Idempotency key cho thao tác xác nhận. Xem danh sách hộp và chi tiết hộp (owner/staff/admin).
- **AC tối thiểu:** giá trị < `minimum_value` của đợt → không đạt; `safe_until` < `pickup_ends_at` → không đạt (BR-18);
  món ngừng kinh doanh → bị từ chối, yêu cầu chỉnh (UC-26 A1/E1); gửi lại cùng key → trả hộp cũ, không nhân đôi tồn;
  đổi giá menu sau đó không đổi giá trị hộp.

### [ ] T4.5 — Chính sách tài chính theo phiên bản
- **Phụ thuộc:** T3.5. **Tài liệu:** UC-84..87, BR-33, BR-46, NFR-28.
- **Phạm vi:** admin xem phiên bản đang áp dụng + lịch sử; tạo phiên bản mới của `financial_policy` (tỷ lệ hoa hồng,
  tỷ lệ giữ lại no-show, giờ phản ánh = 48, điều khoản hủy, điều khoản hoàn) với thời điểm hiệu lực (có thể tương lai)
  và lý do; không sửa phiên bản cũ (trigger V3). Owner xem tỷ lệ đang áp dụng. Seed một phiên bản mặc định.
  API "chính sách hiện hành tại thời điểm t" cho T5.2.
- **AC tối thiểu:** tỷ lệ ngoài [0,1] → 400; hai phiên bản cùng hiệu lực chồng nhau → 409; tạo đồng thời → một thắng;
  đơn đã tạo giữ chính sách cũ.

---

## Giai đoạn 5 — Tìm kiếm và đặt hàng

**Điều kiện hoàn thành giai đoạn:** không bán trùng; giữ đủ số hộp trong một transaction; hết hạn xử lý đúng.

### [ ] T5.1 — Tìm kiếm Surprise Box và cửa hàng
- **Phụ thuộc:** T4.4. **Tài liệu:** UC-07..12, BR-05, BR-19, D10.
- **Phạm vi:** (khách đã đăng nhập) tìm đợt OPEN, còn hạn đặt, tồn > 0, thuộc cửa hàng ACTIVE theo vị trí + bán kính
  hoặc khu vực; lọc nhóm thực phẩm, khoảng giá, khoảng giờ nhận; tìm cửa hàng theo tên; danh sách cửa hàng cho bản đồ
  (tọa độ + có hộp đang bán hay không); chi tiết đợt: nhóm, mô tả, giá, giá trị tối thiểu, số khả dụng, hạn đặt,
  địa chỉ, khung nhận, cảnh báo an toàn. Không lộ danh sách món thực tế.
- **AC tối thiểu:** đợt PAUSED/hết hàng/quá hạn không hiện; khoảng giá/thời gian sai → 400; phân trang có giới hạn.

### [ ] T5.2 — Tạo đơn và giữ hộp
- **Phụ thuộc:** T5.1, T4.5. **Tài liệu:** UC-13, BR-21..23, BR-27, BR-29, BR-33.
- **Phạm vi:** preview (số lượng, tổng tiền, giá trị cam kết, cảnh báo, địa chỉ, khung nhận, chính sách hủy/hoàn/
  no-show); khách xác nhận → kiểm tra lại, tạo `customer_order` `PENDING_PAYMENT` với snapshot đầy đủ + chính sách
  hiện hành; khóa và chọn N hộp AVAILABLE (`FOR UPDATE SKIP LOCKED`) → `box_allocation` HELD, hộp `RESERVED`,
  `hold_expires_at` = now + 10 phút; header `Idempotency-Key`.
- **AC tối thiểu:** không đủ hộp → 409, không giữ một phần; hai khách tranh hộp cuối chỉ một người được; gửi lại cùng
  key trả đúng đơn cũ; snapshot không đổi khi giá/chính sách đổi.

### [ ] T5.3 — Hết hạn giữ hộp và theo dõi đơn
- **Phụ thuộc:** T5.2. **Tài liệu:** UC-17 A2, UC-19..22, UC-54, UC-55, BR-24.
- **Phạm vi:** job quét đơn `PENDING_PAYMENT` quá hạn → `EXPIRED`, allocation `RELEASED`, hộp về `AVAILABLE` chỉ nếu
  còn đủ điều kiện bán; khách hủy đơn chưa thanh toán (không tạo hoàn 0đ); khách xem danh sách/lịch sử/chi tiết đơn,
  các khoản tiền của đơn (khung, số liệu điền dần ở giai đoạn 6–7); cửa hàng xem đơn của mình (`ORDER_VIEW`).
- **AC tối thiểu:** job chạy trùng/hai instance không nhả hộp hai lần; khách không xem đơn người khác.
- **Lưu ý tích hợp:** T6.3 xử lý tranh chấp "job hết hạn" vs "webhook tiền đến" trên cùng đơn — thiết kế khóa đơn ở đây.

---

## Giai đoạn 6 — Thanh toán và hoàn tiền nền tảng

**Điều kiện hoàn thành giai đoạn:** đơn chỉ xác nhận khi đủ tiền hợp lệ; không ghi tiền hoặc hoàn tiền trùng.

### [ ] T6.1 — Port thanh toán và adapter payOS
- **Phụ thuộc:** T0.
- **Phạm vi:** interface `PaymentGateway` (tạo link/QR, tra cứu, hủy link, xác minh webhook); `PayOsPaymentGateway`
  theo tài liệu payOS hiện hành (Gate A đối chiếu docs chính thức: chữ ký, `orderCode` kiểu số); `FakePaymentGateway`;
  cấu hình `payos.*` từ env, timeout; cập nhật `.env.example`.
- **AC tối thiểu:** sai checksum → từ chối; lỗi mạng/timeout → lỗi có kiểu rõ ràng, không lộ key.
- **Test:** checksum với vector mẫu từ docs payOS; adapter test với HTTP server giả.

### [ ] T6.2 — Tạo yêu cầu thanh toán
- **Phụ thuộc:** T5.3, T6.1. **Tài liệu:** UC-14 (bước 1–3), UC-15, BR-28, BR-29.
- **Phạm vi:** khách lấy/tạo QR/link cho đơn của mình, `PENDING_PAYMENT`, còn hạn giữ; ánh xạ đơn → `orderCode` số duy
  nhất; số tiền = `total_amount`; hạn link ≤ `hold_expires_at`; còn yêu cầu hợp lệ → trả lại cùng QR (UC-15 A1);
  ghi request trước, gọi payOS ngoài transaction; lỗi → tra cứu theo mã đơn trước khi tạo lại (UC-15 E1).
- **AC tối thiểu:** đơn người khác → 403/404; đơn đã xác nhận/hết hạn → 409; hai request đồng thời → một link.

### [ ] T6.3 — Webhook xác nhận thanh toán
- **Phụ thuộc:** T6.2. **Tài liệu:** UC-14 (bước 5–8), BR-25, BR-30, BR-32.
- **Phạm vi:** endpoint webhook công khai; xác minh checksum trước; tìm request theo `orderCode`; ghi `payment` unique
  theo (`provider`, `provider_transaction_id`); khóa đơn; đủ tiền + còn hạn → đơn `CONFIRMED`, allocation `CONFIRMED`,
  hộp `ALLOCATED`, `financial_event` PAYMENT_RECEIVED + `ledger_entry` PAYMENT_RECEIPT; trạng thái thanh toán độc lập
  trạng thái đơn. Khách quay lại app chỉ thấy trạng thái đã xác minh (không coi "đã quay lại" là đã trả).
- **AC tối thiểu:** webhook trùng → không ghi lần hai; checksum sai → không đổi gì; webhook và job hết hạn đồng thời →
  kết quả nhất quán.
- **Thử thật:** hướng dẫn người dùng cấu hình webhook URL (ngrok/cloudflared) và tự chuyển số tiền nhỏ.

### [ ] T6.4 — Tiền đến muộn, sai số tiền và đối chiếu
- **Phụ thuộc:** T6.3. **Tài liệu:** UC-13 E3, UC-14 E1/E2/E4, BR-26, BR-29, BR-30.
- **Phạm vi:** tiền đến khi đơn đã EXPIRED/CANCELLED → ghi nhận, **không khôi phục đơn hay phân bổ lại hộp** (BR-26),
  tạo nghĩa vụ hoàn; thiếu/thừa/không khớp mã → `payment` `REQUIRES_REVIEW`, đơn chưa xác nhận, đưa vào đối chiếu;
  giao dịch thất bại xác định → giải phóng hộp đủ điều kiện; job đối chiếu tra cứu payOS cho request còn treo;
  API admin tra cứu giao dịch theo đơn/mã giao dịch.
- **AC tối thiểu:** mọi đồng nhận được đều ở một trong: đã dùng cho đơn, chờ hoàn, đã hoàn; đối chiếu chạy lại không
  tạo payment trùng.

### [ ] T6.5 — Hoàn tiền có kiểm soát (thủ công qua admin)
- **Phụ thuộc:** T6.4. **Tài liệu:** UC-72, UC-74, BR-45, BR-52, D3.
- **Phạm vi:** nghĩa vụ hoàn (`refund_request`) gắn đơn + giao dịch gốc + lý do; admin xem danh sách, số đã thu/đã hoàn/
  đang xử lý; xác nhận đã chuyển khoản kèm mã giao dịch ngân hàng + bằng chứng → `refund` SUCCEEDED, `financial_event`
  REFUND_SUCCEEDED, `ledger_entry` CUSTOMER_REFUND; ghi thất bại → nghĩa vụ còn nguyên; hoàn một phần đúng số được quyết
  định; khách xem trạng thái hoàn. Cơ chế dùng chung cho T7.3, T7.4, T8.1.
- **AC tối thiểu:** tổng hoàn ≤ tổng đã xác minh; xác nhận hai lần đồng thời chỉ ghi một; mã giao dịch ngân hàng không
  dùng lại; không báo khách đã nhận tiền khi admin chưa xác nhận.

---

## Giai đoạn 7 — Bàn giao, hủy đơn và no-show

**Điều kiện hoàn thành giai đoạn:** bàn giao/hủy/no-show không tạo kết quả mâu thuẫn; phát sinh nghĩa vụ hoàn đúng.

### [ ] T7.1 — Mã nhận hàng
- **Phụ thuộc:** T6.3. **Tài liệu:** UC-21, UC-29, BR-35, NFR-14.
- **Phạm vi:** sinh mã khi đơn CONFIRMED (liên kết các hộp của đơn); khách xem mã + địa điểm + khung giờ (cách lưu để
  hiển thị lại: chốt ở Gate A); đơn hủy/hoàn tất/no-show → mã không còn hiệu lực; nhân viên quét/nhập mã → trả đơn,
  hộp phân bổ, trạng thái tiền.
- **AC tối thiểu:** sai nhiều lần bị rate limit; mã chỉ dùng ở đúng cửa hàng; biết `box_code` nội bộ không phải quyền nhận.

### [ ] T7.2 — Phiên bàn giao và sự cố
- **Phụ thuộc:** T7.1. **Tài liệu:** UC-27, UC-28, UC-30, BR-31, BR-34, BR-36, BR-37.
- **Phạm vi:** staff (`PICKUP_HANDLE`)/owner xem đơn cần bàn giao; bắt đầu bàn giao (idempotency key) khi đơn đã xác
  nhận đủ tiền, **trong khung giờ nhận**, đủ hộp đạt yêu cầu → `pickup_session` OPEN, đơn `HANDOVER_IN_PROGRESS`;
  hoàn tất sau khi giao thực tế → đơn `COMPLETED`, hộp/allocation `DELIVERED`, `financial_event` ORDER_COMPLETED;
  không hoàn tất một phần. Ghi `pickup_incident` (lý do, hộp ảnh hưởng, khách đã đến chưa, bằng chứng); lỗi cửa hàng/
  hộp không an toàn → chặn giao và chuyển hủy lỗi cửa hàng (T7.3); nguyên nhân chưa rõ → chuyển admin, không tự kết
  luận lỗi khách. Phiên đang mở chặn hủy và no-show (BR-31).
- **AC tối thiểu:** ngoài khung giờ → từ chối, không tự gia hạn; hai nhân viên mở phiên cùng đơn → một phiên; gửi lại
  hoàn tất cùng key → không ghi trùng.

### [ ] T7.3 — Hủy đơn
- **Phụ thuộc:** T6.5, T7.2. **Tài liệu:** UC-17, UC-18, UC-56, UC-71, BR-38, BR-39, SD-06.
- **Phạm vi:** khách xem trước tác động hủy + số dự kiến được hoàn, nhập lý do, xác nhận hoặc rút yêu cầu.
  Trước `pickup_starts_at` → hủy ngay, hoàn 100% tiền đã thu. Từ `pickup_starts_at` → tạo **yêu cầu hủy chờ admin xét
  duyệt** (UC-71: chấp thuận/từ chối/yêu cầu bổ sung, số hoàn theo chính sách đã lưu). Cửa hàng hủy (UC-56) → hoàn 100%,
  không phí. Khi hủy: allocation `RELEASED`, hộp chỉ về bán nếu còn đủ điều kiện (lỗi cửa hàng/không an toàn → không
  tự mở bán lại); tạo nghĩa vụ hoàn qua T6.5; hoàn lỗi không làm khôi phục đơn. Khách xem kết quả hủy và tiến độ hoàn.
- **Dữ liệu:** cần bảng yêu cầu hủy (chưa có trong schema) — đề xuất migration ở Gate A.
- **AC tối thiểu:** đơn đã hoàn tất/đang bàn giao → không hủy, hướng dẫn khiếu nại; hủy và bàn giao đồng thời → một kết
  quả; gửi lại → không tạo yêu cầu/hoàn trùng.

### [ ] T7.4 — No-show và kiểm tra lại hộp
- **Phụ thuộc:** T7.3. **Tài liệu:** UC-35..37, BR-40..44, BR-51.
- **Phạm vi:** job sau `pickup_ends_at` chuyển đơn CONFIRMED chưa giao → `PENDING_NO_SHOW`; staff (`NO_SHOW_CONFIRM`)/owner
  xác nhận khách không đến kèm căn cứ (cửa hàng mở đúng giờ, có đủ hộp đạt) → `no_show_case`, đơn `NO_SHOW`, vô hiệu mã
  nhận; tính bằng `NoShowAmounts` (giữ lại ≤ tiền đã xác minh, hoàn phần còn lại qua T6.5, hoa hồng 0); khoản bù
  `HELD` đến 48h kể từ khi có căn cứ khách nhận thông báo (chưa có căn cứ → chưa tính hết hạn, D06); có sự cố "khách
  đã đến" → không được xác nhận no-show, chuyển UC-30. Kiểm tra lại hộp no-show (UC-35): ghi kết quả; đạt chỉ được
  đưa vào đợt/khung giờ mới phù hợp; không đạt → chặn.
- **AC tối thiểu:** giữ + hoàn = đã trả; xác nhận lặp → một kết quả, một khoản giữ, một nghĩa vụ hoàn; chưa hết giờ/đã
  hoàn tất → từ chối.

### [ ] T7.5 — Hộp không đạt, ngừng bán vi phạm, khóa cửa hàng có đơn tồn
- **Phụ thuộc:** T7.4. **Tài liệu:** UC-34, UC-78, UC-77 (phần đơn tồn), BR-20, BR-55, SD-08.
- **Phạm vi:** staff (`BOX_MANAGE`)/owner đánh dấu hộp không đạt kèm lý do + bằng chứng → `BLOCKED`, loại khỏi tồn,
  chặn giao; với từng đơn bị ảnh hưởng (khóa đơn, đọc trạng thái mới nhất): chờ thanh toán → hủy đơn, nhả các hộp khác,
  theo dõi tiền đến muộn; đã xác nhận chưa giao → hủy lỗi cửa hàng (T7.3); đã giao → ghi sự cố sau giao, chuyển khiếu
  nại (T8.1), không hủy/hoàn lại lần nữa. Admin ngừng bán hộp/đợt vi phạm (UC-78) dùng cùng luồng. Admin khóa cửa hàng
  (T3.3) → rà đơn đã xác nhận: tiếp tục nếu còn an toàn và có người bàn giao, nếu không → hủy/hoàn.
- **AC tối thiểu:** đánh dấu và bàn giao đồng thời → không ra hai kết quả mâu thuẫn; không xóa lịch sử hộp.

---

## Giai đoạn 8 — Khiếu nại, thông báo và đối soát

**Điều kiện hoàn thành giai đoạn:** truy vết được từng khoản; không giải tỏa hoặc chi tiền chưa đủ điều kiện.

### [ ] T8.1 — Khiếu nại
- **Phụ thuộc:** T7.5. **Tài liệu:** UC-23, UC-63..65, BR-43, BR-45, SD-10.
- **Phạm vi:** khách gửi khiếu nại theo loại (`ComplaintType`) kèm bằng chứng, cấp mã tiếp nhận; phản ánh no-show đúng
  48h → tiếp tục giữ khoản bù; quá hạn → ghi nhận như yêu cầu hỗ trợ (D06); sự cố sau giao từ T7.5 cũng tạo hồ sơ.
  Admin xem danh sách/chi tiết (đơn, hộp, thanh toán, giao nhận, no-show, lịch sử); yêu cầu bổ sung (giữ tiền tranh chấp);
  quyết định chấp thuận/một phần/bác kèm căn cứ, trách nhiệm, số tiền theo nguồn → tạo nghĩa vụ hoàn (T6.5) và điều chỉnh
  (no-show lỗi cửa hàng → hủy khoản giữ, hoàn 100%, cửa hàng không được bù); đóng hồ sơ chỉ khi mọi việc đã thực hiện.
  `financial_event` COMPLAINT_RESOLVED.
- **AC tối thiểu:** gửi lại do mất mạng → trả mã cũ; số hoàn vượt khoản còn hoàn được → từ chối; quyết định đã chốt không sửa.

### [ ] T8.2 — Thông báo
- **Phụ thuộc:** T8.1. **Tài liệu:** FR hệ thống 21, NoShowCase.notification_received_at.
- **Phạm vi:** ghi `notification` (outbox) trong cùng transaction với sự kiện (đặt hàng, thanh toán, hủy, đổi trạng thái,
  quá hạn nhận, kết quả no-show, hoàn tiền, khiếu nại, xét duyệt cửa hàng, đối soát); worker gửi qua port
  `NotificationSender` (email qua mail sender sẵn có + in-app), retry giới hạn, PENDING → SENT/DELIVERED/FAILED; API thông
  báo của tôi, đánh dấu đã đọc (đọc thông báo no-show = căn cứ nhận thông báo cho T7.4).
- **AC tối thiểu:** rollback sự kiện → không có thông báo; gửi lỗi không làm hỏng nghiệp vụ; không gửi trùng.

### [ ] T8.3 — Hoa hồng, quyền lợi cửa hàng, doanh thu
- **Phụ thuộc:** T8.1. **Tài liệu:** UC-47, UC-48, BR-47..52.
- **Phạm vi:** đơn COMPLETED → ghi PLATFORM_COMMISSION (= giá bán thực tế × tỷ lệ snapshot) + STORE_PAYABLE; no-show →
  NO_SHOW_COMPENSATION riêng, hoa hồng 0; chỉ đủ điều kiện đối soát khi qua cửa sổ phản ánh và không còn tranh chấp; hoàn
  tiền/quyết định khiếu nại → bút toán ADJUSTMENT gắn bút toán gốc. Owner xem doanh thu, hoa hồng, khoản được hưởng, khoản
  đang giữ.
- **AC tối thiểu:** Σ bút toán của đơn khớp tiền thu − tiền hoàn; job chạy lại không ghi trùng (`business_key`); làm tròn
  thống nhất (NFR-17, chốt ở Gate A).

### [ ] T8.4 — Đối soát và chi trả
- **Phụ thuộc:** T8.3. **Tài liệu:** UC-42, UC-49, UC-80..83, BR-50..52, D05.
- **Phạm vi:** owner quản lý tài khoản nhận tiền theo phiên bản (che thông tin nhạy cảm; xác minh trước khi dùng);
  admin lập bảng đối soát DRAFT theo cửa hàng + kỳ, mỗi dòng truy về bút toán gốc, tách khoản giữ/tranh chấp/ngoại lệ,
  số âm hiển thị là khoản cần thu hồi (không tạo lệnh chuyển âm); xác nhận → CONFIRMED (trigger khóa line), kiểm tra
  dữ liệu không đổi kể từ lúc lập; điều chỉnh sau khi chốt → dòng mới gắn kỳ gốc; ghi chi trả thủ công (số tiền, thời điểm,
  mã chuyển, bằng chứng) → PARTIALLY_PAID/PAID. Owner xem đối soát/chi trả; admin xem lịch sử.
- **AC tối thiểu:** một bút toán chỉ thuộc một bảng; không chi vượt; mã chuyển trùng → từ chối; tài khoản nhận vừa đổi
  hoặc chưa xác minh → chặn chi; khoản tranh chấp không vào bảng.

### [ ] T8.5 — Giám sát và thống kê quản trị
- **Phụ thuộc:** T8.4. **Tài liệu:** UC-75, UC-76, UC-79, BR-56.
- **Phạm vi:** admin xem lịch sử hoạt động cửa hàng (giá, đóng hộp, đơn, tiền, bàn giao, no-show, hoàn, quyết định quản
  trị — từ `audit_log`/event), lọc theo đối tượng/thời gian/người; thống kê tài chính cơ bản theo kỳ/cửa hàng (số đơn
  theo trạng thái, giá trị giao dịch, doanh thu hoàn tất, hoa hồng, hoàn, khoản giữ, phải chi).
- **AC tối thiểu:** số liệu thống kê khớp ledger; không phải admin → 403.

---

## Giai đoạn 9 — Hoàn thiện và triển khai

**Điều kiện hoàn thành giai đoạn:** chạy được các kịch bản MVP hoàn chỉnh và phục hồi được khi có sự cố.

### [ ] T9.1 — Test xuyên suốt và tải đồng thời
- **Phụ thuộc:** T8.5.
- **Phạm vi:** test HTTP kịch bản MVP: đăng ký đối tác → duyệt → nhân viên → menu → đợt → đóng hộp → mở bán → đặt → trả
  tiền → nhận → đối soát → chi trả; nhánh hủy, no-show, khiếu nại, tiền muộn, hộp lỗi. Tải ≥ 100 người dùng đồng thời
  (NFR-4) tranh hộp, webhook dồn dập; đo p95 so với NFR-1/2. Kịch bản bấm tay trên Swagger.

### [ ] T9.2 — Giám sát
- **Phụ thuộc:** T9.1.
- **Phạm vi:** Actuator metrics, log có cấu trúc kèm requestId, chỉ số nghiệp vụ (thanh toán treo, payment REQUIRES_REVIEW,
  hoàn chờ, job lỗi); không lộ thông tin nhạy cảm.

### [ ] T9.3 — Rà soát bảo mật
- **Phụ thuộc:** T9.1.
- **Phạm vi:** OWASP API Top 10 (IDOR mọi endpoint có id, mass assignment, rate limit, upload, webhook), CVE dependency,
  header bảo mật, CORS cho app Android, NFR-9..15; sửa lỗi kèm test.

### [ ] T9.4 — Backup và phục hồi
- **Phụ thuộc:** T9.1.
- **Phạm vi:** backup hằng ngày (NFR-8) `pg_dump` + thư mục file; runbook restore; thử khôi phục vào DB trống và smoke test.

### [ ] T9.5 — CI/CD và staging
- **Phụ thuộc:** T9.2, T9.3, T9.4.
- **Phạm vi:** GitHub Actions chạy `./gradlew build` trên PR; Dockerfile; compose staging (app + db + volume file) có
  HTTPS; secret qua biến môi trường; hướng dẫn deploy và cấu hình webhook payOS trỏ về staging.
