# Business Definition: T7.1 — Mã nhận hàng

**Status:** Gate A đã được người dùng duyệt ngày 2026-11-23 (Q1–Q4); chuyển lập Gate B. Chưa triển khai.
**Updated:** 2026-11-23
**Scope:** T7.1 trong yêu cầu T7.1 và T7.2; thực hiện tuần tự. T7.2 phụ thuộc T7.1 hoàn tất.
**Phụ thuộc:** T6.3 đã được đánh dấu `[x]` trong roadmap.

## 1. Bối cảnh và mục tiêu

Đơn được xác nhận sau khi backend xác minh đủ tiền nhưng hiện chưa có mã để khách chứng minh quyền nhận hàng.
T7.1 cấp mã riêng cho đơn, cho khách xem lại và cho cửa hàng xác minh trước khi bắt đầu phiên bàn giao.
Mã của một đơn liên kết toàn bộ hộp đã phân bổ qua `box_allocation`, không cấp quyền nhận chỉ bằng mã hộp nội bộ.

Nguồn nghiệp vụ: `../../../../../DOCX/BaoCaoFoodRescue.pdf` tính từ thư mục spec này
(đường dẫn từ backend: `../../DOCX/BaoCaoFoodRescue.pdf`). Đã đọc các trang PDF vật lý:

- Trang 21: yêu cầu bảo mật số 14 (NFR-14), hạn chế xác minh mã bất thường.
- Trang 27: BR-35, mã nhận đơn khác mã định danh hộp; BR-31, BR-34, BR-36 là ranh giới với bước bàn giao.
- Trang 67: UC-21 đầy đủ, bao gồm A1/A2 và E1/E2.
- Trang 76–77: UC-29 đầy đủ, bao gồm nhập tay khi không quét được, giới hạn thử, đúng cửa hàng và chưa bàn giao.

Card T7.1 không liệt kê SD riêng; không suy diễn thêm mã SD từ hình hoặc tự đặt nguồn nghiệp vụ.

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin dưới `src/main/kotlin/com/foodrescue/backend/`:

- `payment/service/PaymentConfirmationService.confirm`: dưới khóa đơn, ghi tiền VERIFIED, chuyển đơn CONFIRMED,
  allocation CONFIRMED, hộp ALLOCATED, ghi event/ledger và audit trong cùng transaction. Chưa cấp mã nhận.
- `order/entity/CustomerOrder`: đã có `pickupCodeHash` nullable; DB V1 có unique trên `pickup_code_hash`.
  Không có dữ liệu có thể giải mã để khách xem lại mã.
- `order/repository/CustomerOrderRepository.findForUpdate`: đã có khóa pessimistic cho luồng thay đổi đơn.
- `order/repository/BoxAllocationRepository`: truy được hộp của đơn và trạng thái phân bổ.
- `order/service/OrderQueryService`: đã có kiểm tra đơn thuộc khách/cửa hàng, xem tiền và hộp; quyền xem danh sách
  cửa hàng là ORDER_VIEW. Quyền đó không mặc nhiên cấp quyền xác minh mã nhận.
- `order/service/OrderMoneyReader`: đọc tổng đã xác minh, còn phải trả, đã hoàn và nghĩa vụ hoàn đang mở.
- `common/security/StoreAuthorizationService`: owner có quyền cửa hàng, staff ACTIVE cần permission tương ứng.
- `middleware/ratelimit/RateLimitService` và `RateLimitStore`: counter DB dùng chung giữa instance,
  transaction REQUIRES_NEW, cửa sổ 15 phút; enum hiện chưa có policy xác minh mã nhận.
- `order/OrderStatus`: đã có CONFIRMED, HANDOVER_IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED,
  PENDING_NO_SHOW, NO_SHOW. T7.1 chưa thêm luồng chuyển trạng thái cho T7.2–T7.4.
- V14 cho phép đổi hash mã nhận nhưng bảo vệ snapshot đơn. Migration hiện có đến V17;
  không sửa migration cũ. Nếu thêm bảng lưu mã, dùng migration mới và kiểm tra lại số trước khi lập Gate B.

Baseline lần trước: 214 tests, 212 PASS, 1 FAIL, 1 SKIPPED; lỗi FinancialPolicyTests:68
(kỳ vọng 409, thực tế 201). Người dùng yêu cầu chạy lại vì nghi có xen kẽ khi test.
Baseline chạy lại BUILD SUCCESSFUL trong 6m48s: 214 tests, 213 PASS, 0 FAIL, 1 SKIPPED.
Đã đọc HTML và XML từng suite; FinancialPolicyTests 6 PASS. Một lần chạy xanh không đủ để kết luận nguyên nhân lỗi trước.

## 3. Người dùng và quyền

- Khách đã đăng nhập chỉ xem mã của đơn do chính mình đặt. Đơn người khác hoặc không tồn tại: 404.
- Owner hoặc staff ACTIVE có `PICKUP_HANDLE` tại cửa hàng được chọn được xác minh mã.
- Staff chỉ có ORDER_VIEW, staff bị thu hồi quyền và người có quyền tại cửa hàng khác: 403.
- ADMIN không tự có quyền nhận/bàn giao thay cửa hàng chỉ vì có platform role ADMIN.
- Không đăng nhập: 401. Backend kiểm tra quan hệ và quyền, không tin role/storeId trong payload.

## 4. Luồng chính

### 4.1. Cấp mã khi xác nhận thanh toán

1. Xác minh thanh toán theo T6.3, khóa đơn và kiểm tra điều kiện xác nhận hiện có.
2. Trong chính transaction chuyển đơn sang CONFIRMED, sinh một mã ngẫu nhiên riêng cho đơn,
   lưu hash để tra cứu và bản mã hóa để hiển thị lại. Không có trạng thái xác nhận thành công mà thiếu mã.
3. Gửi lại webhook hoặc xử lý đồng thời không cấp mã thứ hai, không đổi mã đã cấp.
4. Thanh toán thiếu/thừa, sai mã, tiền muộn hoặc đơn không được xác nhận: không cấp mã.

### 4.2. Khách xem lại mã

1. Kiểm tra đăng nhập và đơn thuộc khách.
2. Trả trạng thái đơn, địa điểm và khung nhận theo snapshot, thông tin tiền đã xác minh.
3. Nếu đơn CONFIRMED và chưa hết giờ nhận, trả mã và `usable=true`.
   Trước giờ bắt đầu vẫn xem được mã để chuẩn bị; điều kiện bắt đầu bàn giao thuộc T7.2.
4. Nếu đơn chưa xác nhận, đang bàn giao, đã hủy/hoàn tất/hết hạn/no-show hoặc hết giờ nhận,
   trả trạng thái với `code=null`, `usable=false`; không trình bày mã cũ như mã còn hiệu lực.
5. Xem lại không sinh mã, không thay trạng thái, tồn kho hay tiền. Lỗi tải/giải mã trả lỗi rõ,
   không trả mã cũ hoặc tạo mã thay thế ngầm.

### 4.3. Cửa hàng xác minh mã

1. Nhân viên chọn cửa hàng, quét QR hoặc nhập chuỗi mã; hai cách sử dụng cùng giá trị.
2. Backend kiểm tra quyền PICKUP_HANDLE rồi áp hạn mức theo tài khoản và cửa hàng;
   lượt nhập sai định dạng cũng không được bỏ qua hạn mức.
3. Kiểm tra định dạng, hash và tra cứu đúng cửa hàng. Không dùng orderCode hay boxCode làm fallback.
4. Chỉ trả kết quả hợp lệ cho đơn CONFIRMED, chưa hết giờ nhận. Trước giờ bắt đầu có thể tra cứu,
   kết quả vẫn có khung giờ để T7.2 kiểm tra trước khi cho mở phiên.
5. Trả thông tin đơn, toàn bộ hộp thuộc allocation CONFIRMED và số liệu tiền đã xác minh.
   Không trả hash, ciphertext, mã ngân hàng hoặc thông tin cá nhân không cần thiết.
6. Mã sai/không tồn tại/khác cửa hàng/không còn hiệu lực: cùng lỗi 404 NOT_FOUND, không tiết lộ đơn ngoài quyền.
7. Xác minh thành công chỉ là đọc thông tin, không mở phiên hoặc đánh dấu đã giao.

## 5. Quy tắc nghiệp vụ đề xuất

- **BR-T71-01 — Một mã cho một đơn:** mã sinh từ nguồn ngẫu nhiên mật mã, không suy ra từ mã đơn/hộp.
  Đề xuất 16 ký tự Base32 (80 bit), bỏ ký tự dễ nhầm; nhập không phân biệt chữ hoa/thường,
  bỏ khoảng trắng/gạch ngang dùng để nhóm ký tự. QR chứa đúng mã, không chứa thông tin cá nhân.
- **BR-T71-02 — Xem lại an toàn:** lưu SHA-256 của mã chuẩn hóa trong trường hash hiện có;
  lưu bản mã hóa có xác thực (AES-256-GCM) trực tiếp trên customer_order, kèm định danh phiên bản khóa (điều chỉnh đã duyệt ngày 2026-11-23).
  Khóa lấy từ cấu hình bí mật môi trường, tách khỏi DB; test dùng khóa giả. Không lưu/log mã thô.
- **BR-T71-03 — Nguyên tử và idempotency:** cấp mã cùng transaction xác nhận đơn, khóa dòng và unique DB.
  Duplicate webhook giữ nguyên mã; rollback xác nhận thì rollback mã. Chỉ đọc mã đã được cấp, không sinh trong GET.
- **BR-T71-04 — Phạm vi:** khách chỉ xem đơn mình; xác minh bắt buộc quyền PICKUP_HANDLE và store của đơn.
  Mã hộp hoặc mã đơn thông thường không có giá trị chứng minh quyền nhận.
- **BR-T71-05 — Hiệu lực:** chỉ CONFIRMED và `now < pickupEndsAt` được xem/tra mã hợp lệ.
  Khi mở phiên (T7.2) tiếp tục bằng session; không dùng mã để mở phiên thứ hai.
  Việc vô hiệu theo trạng thái có tác dụng ngay, không chờ job xóa hash.
- **BR-T71-06 — Giới hạn thử:** đề xuất tối đa 60 yêu cầu/15 phút theo account và 300 yêu cầu/15 phút theo store.
  Đếm mọi lượt xác minh của người có quyền, không chỉ lượt sai; counter DB tồn tại sau rollback lỗi nghiệp vụ.
  Vượt hạn mức trả 429 RATE_LIMITED kèm Retry-After. Đổi mã/IP không làm mới quota tài khoản/cửa hàng.
- **BR-T71-07 — Không làm thay bước bàn giao:** kết quả tra mã chỉ cung cấp dữ liệu để kiểm tra,
  không thay trạng thái đơn, hộp, allocation, không phát sinh financial_event/ledger.
- **BR-T71-08 — Thời gian và dữ liệu:** inject Clock, dùng snapshot địa điểm/khung giờ và UTC ISO-8601.
  API trả BigDecimal VND nguyên đồng theo quy ước hiện có.
- **BR-T71-09 — Bảo vệ dữ liệu:** không đưa mã vào URL/query/log/audit; endpoint xác minh nhận mã trong body.
  Response chứa mã khách xem phải có Cache-Control: no-store. Mất khóa hoặc dữ liệu mã hóa hỏng phải báo lỗi chung,
  không cấp mã thay thế. Không trả cấu hình bí mật trong response lỗi.

## 6. Phạm vi

Trong phạm vi: cấp mã gắn với xác nhận thanh toán, lưu để xem lại, khách xem mã,
owner/staff xác minh, hạn mức, OpenAPI và test quyền/trạng thái/đồng thời/rollback.

Ngoài phạm vi: mở/hoàn tất phiên và sự cố (T7.2), hủy (T7.3), job no-show (T7.4),
gửi thông báo (T8.2), tạo ảnh QR ở backend, UI Android, thay đổi cách xác minh tiền hoặc sửa FinancialPolicyTests.
Ứng dụng có thể render QR từ chuỗi mã API trả về; không bắt buộc thêm thư viện tạo ảnh.

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Given đơn chờ đủ điều kiện, When webhook hợp lệ xác nhận, Then đúng một mã gắn với đơn và toàn bộ allocation; mã được lưu cùng transaction với trạng thái CONFIRMED. |
| AC-02 | Given webhook trùng/đồng thời, When gửi lại, Then giữ nguyên mã và chỉ một bản lưu; rollback xác nhận không để lại mã hoặc đơn xác nhận một phần. |
| AC-03 | Given khách sở hữu đơn CONFIRMED chưa hết giờ nhận, When xem mã nhiều lần, Then cùng mã, đúng địa điểm/khung snapshot và tiền đã xác minh; không thay đổi dữ liệu nghiệp vụ. |
| AC-04 | Given khách khác hoặc chưa đăng nhập, When xem mã, Then lần lượt 404 hoặc 401, không lộ mã. |
| AC-05 | Given owner hoặc staff ACTIVE có PICKUP_HANDLE đúng store, When xác minh mã hợp lệ bằng giá trị quét hoặc nhập tay, Then trả đúng đơn, toàn bộ hộp phân bổ, trạng thái tiền; không ghi nhận giao. |
| AC-06 | Given staff thiếu quyền/bị thu hồi/sai store hoặc chưa đăng nhập, When xác minh, Then 403 hoặc 401; không lộ dữ liệu ngoài quyền. |
| AC-07 | Given mã không tồn tại/sai store hoặc boxCode/orderCode nội bộ, When xác minh, Then bị từ chối; không dùng mã hộp thay mã nhận. |
| AC-08 | Given vượt quota account hoặc store, When thử tiếp kể cả đổi mã hoặc mã sai định dạng, Then 429 và Retry-After; lỗi trước đó không rollback counter. |
| AC-09 | Given đơn PENDING_PAYMENT/EXPIRED/CANCELLED/COMPLETED/NO_SHOW/PENDING_NO_SHOW/HANDOVER_IN_PROGRESS, When khách xem hoặc staff tra mã, Then không có mã sử dụng được và không tạo giao dịch. |
| AC-10 | Given Clock tại hoặc sau pickupEndsAt dù job chưa chạy, When xem/tra mã, Then mã không còn hiệu lực; trước pickupStartsAt xem/tra được nhưng chưa ghi nhận bàn giao. |
| AC-11 | Given thanh toán không đủ điều kiện xác nhận, When webhook xử lý, Then không cấp mã; không khôi phục đơn kết thúc để cấp mã. |
| AC-12 | Given dữ liệu mã đã lưu, When đọc DB/response lỗi hoặc ciphertext hỏng, Then không có mã thô/khóa bị lộ; lỗi giải mã không tạo mã mới; response xem mã có no-store. |
| AC-13 | Given khóa mã hóa thiếu/sai ở môi trường yêu cầu khóa, When khởi động, Then cấu hình bị từ chối rõ; test dùng khóa giả và không gọi payOS thật. |
| AC-14 | Given schema và API mới, When chạy migration/build/ApiContractTests, Then DB mới/nâng cấp hoạt động và mọi endpoint có schema, ví dụ, mã lỗi trong OpenAPI. |

## 8. Truy vết nguồn nghiệp vụ → AC

| Nguồn | Phạm vi đáp ứng | AC |
|---|---|---|
| UC-21 chính, A1/A2, E1/E2 | Khách xem mã, địa điểm/khung và trạng thái tiền; giới hạn quyền; không sửa dữ liệu khi xem | AC-03,04,09,10,12 |
| UC-29 chính 1–4, A1 | Quét/nhập, đúng cửa hàng, trả đơn/hộp/tiền, chưa bàn giao | AC-05,06,07,10 |
| UC-29 E1/E2 | Từ chối mã sai/khác cửa hàng/hết quyền, chống thử bất thường | AC-06,07,08,09 |
| BR-35 | Đơn xác nhận có mã riêng, khác mã hộp nội bộ | AC-01,02,07,11 |
| NFR-14 | Hạn chế xác minh bất thường | AC-08 |
| CLAUDE.md §4,5,6,8; bảo mật NFR-12/13 | Nguyên tử, không lộ bí mật, kiểm quyền server, OpenAPI/test | AC-02,04,06,12,13,14 |

Không có SD được card T7.1 yêu cầu. BR-31/34/36 dùng làm ranh giới, kiểm tra bắt đầu giao được triển khai ở T7.2.

## 9. Quyết định cần duyệt tại Gate A

- [x] Q1: Duyệt mã 16 ký tự ngẫu nhiên, lưu hash và bản mã hóa để xem lại, theo BR-T71-01/02.
- [x] Q2: Duyệt quota 60/account và 300/store trong cửa sổ 15 phút, đếm mọi lượt; 429 kèm Retry-After.
- [x] Q3: Duyệt hiệu lực CONFIRMED đến trước pickupEndsAt; tra trước giờ bắt đầu được phép nhưng không có quyền giao sớm.
- [x] Q4: Đơn CONFIRMED có trước rollout chưa có mã: duyệt tác vụ backfill có kiểm soát,
  khóa từng đơn và chỉ cấp cho đơn còn hiệu lực, không đổi tiền/tồn, chạy trước khi bật API;
  không cấp ngầm trong thao tác xem. Chi tiết vận hành và rollback đưa vào Gate B.

## 10. Ràng buộc cho Gate B

- Card yêu cầu sinh mã đúng lúc CONFIRMED nên cần điểm tích hợp tối thiểu ở PaymentConfirmationService (T6.3).
  Plan phải nêu file và lý do; không đổi quy tắc xác nhận tiền hiện có.
- Nếu thêm enum rate limit ở middleware, plan phải liệt kê rõ thay đổi task khác và lý do theo CLAUDE.md §1;
  không sửa thuật toán rate limit hoặc auth ngoài yêu cầu này.
- Lưu ciphertext/keyId trên customer_order; V18 cập nhật whitelist của hàm bảo vệ snapshot, không sửa V14 (điều chỉnh người dùng đã duyệt).
- Không code trước khi Gate A và Gate B được duyệt. Không thực hiện thao tác git theo hướng dẫn hiện tại.
