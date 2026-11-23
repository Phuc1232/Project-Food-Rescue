# Technical Plan: T7.1 — Mã nhận hàng

**Based on:** [01-business-definition.md](01-business-definition.md), Gate A được người dùng duyệt Q1–Q4 ngày 2026-11-23.
**Status:** DONE — đã triển khai và kiểm chứng; xem 03-implementation-control.txt. Gate B/điều chỉnh được người dùng duyệt ngày 2026-11-23.
**Updated:** 2026-11-23

## 1. Quyết định đã duyệt và baseline

- Mã ngẫu nhiên 16 ký tự, hash để tra cứu và bản mã hóa để khách xem lại.
- Quota 60/account và 300/store mỗi 15 phút, áp dụng mọi lượt xác minh.
- Chỉ CONFIRMED và chưa hết giờ nhận có mã sử dụng được; tra trước giờ bắt đầu được phép.
- Backfill chủ động cho đơn CONFIRMED còn hiệu lực, không sinh mã khi GET.
- Baseline chạy lại: BUILD SUCCESSFUL trong 6m48s; 214 tests = 213 PASS + 1 SKIPPED,
  0 FAIL. Đã đọc HTML/XML; FinancialPolicyTests 6 PASS, PaymentWebhookTests 6 PASS,
  ApiContractTests 4 PASS; test bỏ qua sẵn có thuộc SwaggerUiContextPathTests.
- Chưa kết luận nguyên nhân lỗi ngắt quãng FinancialPolicyTests của lần trước; không sửa ngoài phạm vi.

## 2. Thiết kế

### 2.1. Sinh, lưu và đọc mã

- Package `pickup`: `PickupCodeCodec` dùng SecureRandom sinh 16 ký tự Base32 độc lập (80 bit entropy)
  với alphabet `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (không I/L/O/U).
- Chuẩn hóa: uppercase bằng Locale.ROOT, bỏ dấu gạch ngang ASCII và khoảng trắng dùng để nhóm mã;
  chỉ nhận đúng 16 ký tự thuộc alphabet. Giới hạn chiều dài đầu vào 128 ký tự trước chuẩn hóa.
- Hash SHA-256 của mã chuẩn hóa (hex 64 ký tự) ghi vào `customer_order.pickup_code_hash` hiện có.
- Mã hóa bằng JDK Cipher AES/GCM/NoPadding: khóa 32 byte, IV ngẫu nhiên 12 byte, tag 128 bit;
  UUID đơn làm associated data để chống tráo ciphertext giữa đơn. Ciphertext gồm IV + dữ liệu + tag,
  lưu Base64, cùng keyId; không lưu mã thô hoặc tạo thuật toán mã hóa riêng.
- `PickupCodeProperties`: activeKeyId và key ring đọc từ env `PICKUP_CODE_ACTIVE_KEY_ID`,
  `PICKUP_CODE_KEYS` (danh sách `keyId:base64Key` phân cách dấu phẩy). Kiểm tra id trùng,
  khóa sai Base64/không đúng 32 byte/activeKeyId không tồn tại; báo cấu hình lỗi mà không in giá trị.
  Không có khóa mặc định dùng được; test fixture cung cấp khóa giả.
- Đọc bằng keyId đã lưu. Giữ khóa cũ khi đổi active key, mã cũ tiếp tục đọc được.
  Không xoay mã theo lần xem. Hỏng dữ liệu/thiếu khóa đọc trả lỗi 503 SERVICE_UNAVAILABLE chung.

### 2.2. Cấp mã trong transaction xác nhận

- `PickupCodeIssuer.issue(order)` yêu cầu transaction hiện tại (MANDATORY), gọi sau khi đơn được chuyển
  CONFIRMED và allocation đã chuyển CONFIRMED trong `PaymentConfirmationService.confirm`.
- Giữ khóa đơn hiện có; bộ ba hash/ciphertext/keyId lưu trực tiếp trên đơn. Đã có mã thì không cấp lại.
  Hash collision được DB chặn và rollback cả transaction; webhook gửi lại có thể thử lại,
  không nuốt lỗi constraint trong transaction đã hỏng.
- `orders.saveAndFlush` sau khi gắn hash; lưu bản mã hóa cùng transaction với payment/event/ledger.
  Lỗi integrity tại bước ghi mã được chuyển thành ApiException 503 trong PaymentConfirmationService,
  tránh catch tổng quát của webhook cũ hiểu nhầm thành DUPLICATE; transaction vẫn rollback toàn bộ.
- Không gọi provider hoặc file storage trong transaction mới này.
- Nếu chỉ có hash mà không có ciphertext (dữ liệu cũ bất thường), không tự đổi mã; báo cần xử lý vận hành.

### 2.3. Xem và xác minh

- `PickupCodeService.mine(customerId, orderId)` read-only: kiểm tra ownership, đọc snapshot và tiền;
  nếu trạng thái/thời gian không hợp lệ trả code null, usable false. Chỉ giải mã sau kiểm quyền.
- `PickupCodeService.verify(actorId, storeId, input)` kiểm quyền PICKUP_HANDLE rồi quota trước khi
  validate định dạng, lookup hash với storeId và đối chiếu trạng thái mới nhất.
- Quota chạy và commit trước transaction đọc (TransactionTemplate), tránh giữ connection khi counter dùng REQUIRES_NEW.
- Dùng transaction read-only cho kết quả đơn/hộp/tiền; thành công không khóa khả năng hủy/bàn giao sau đó.
  T7.2 phải kiểm tra lại các điều kiện dưới khóa khi mở phiên, không tin kết quả tra trước đó.
- Trả đầy đủ các allocation CONFIRMED; dữ liệu hộp giữ nguyên trạng thái thực tế để bước tiếp theo kiểm tra.
  Không trả mã thực tế của món/menu hoặc thông tin khách không cần cho xác minh.
- Response xem mã dùng `Cache-Control: no-store`. Xác minh nhận body, không nhận mã trong URL/query.

### 2.4. Rate limit

- Thêm `PICKUP_VERIFY_ACCOUNT(60)` và `PICKUP_VERIFY_STORE(300)` vào enum hiện có.
- Dùng `RateLimitService.check` với khóa accountId và storeId riêng; counter REQUIRES_NEW hiện có
  đảm bảo mã sai/404/400 không rollback lượt thử. Cả hai policy dùng chung cửa sổ DB 15 phút hiện tại.
- Kiểm quyền trước counter để người ngoài cửa hàng không tiêu hao quota của cửa hàng.
- Lượt vượt account quota có thể dừng trước store counter; đổi account không vượt được store quota.
- Không sửa auth filter, thuật toán HMAC, logic IP hay quota endpoint cũ.

## 3. API contract

Hai endpoint mới, tag `Pickup`; dùng Bearer auth và lỗi API hiện có:

| Method/path | Quyền và request | Response |
|---|---|---|
| GET `/api/v1/orders/{orderId}/pickup-code` | Khách sở hữu đơn | 200 PickupCodeView; 401, 404, 503 |
| POST `/api/v1/stores/{storeId}/pickup-codes/verify` | Owner hoặc staff PICKUP_HANDLE; body `{ "code": "0123456789ABCDEF" }` | 200 PickupVerificationView; 400, 401, 403, 404, 429 + Retry-After, 503 |

`PickupCodeView`: orderId, orderCode, orderStatus, code nullable, usable, pickupAddress,
pickupStartsAt, pickupEndsAt, money (OrderMoneyView). Chưa thanh toán/đơn kết thúc vẫn 200
với trạng thái, code null và usable false để đáp ứng UC-21 A1/A2.

`PickupVerificationView`: orderId, orderCode, orderStatus, storeId, quantity, pickupAddress,
pickupStartsAt, pickupEndsAt, boxes (AllocatedBoxView), money (OrderMoneyView).
Không trả ciphertext/hash/keyId hoặc mã thô cho staff.

Mã lỗi: 400 VALIDATION_FAILED (định dạng/độ dài); 401 theo auth hiện có; 403 ACCESS_DENIED;
404 NOT_FOUND chung cho mã không hợp lệ/khác store/hết hiệu lực; 429 RATE_LIMITED;
503 SERVICE_UNAVAILABLE. Input thiếu/null/không phải string trả 400; quota áp dụng cho mã chuỗi sai định dạng,
còn JSON lỗi chưa thể giải mã vẫn theo body limit/security hiện có.

OpenAPI có schemas, ví dụ mã hợp lệ theo alphabet, response vô hiệu, các lỗi nêu trên và header no-store/Retry-After.

## 4. Migration và backfill

- Đề xuất `V18__pickup_code_storage.sql` (hiện local tối đa V17; kiểm lại trước triển khai).
- Thêm customer_order.pickup_code_encrypted text và pickup_code_key_id varchar(64), nullable cho đơn chưa cấp mã.
  Giữ pickup_code_hash unique hiện có. Thêm CHECK ciphertext và keyId cùng có hoặc cùng null; ciphertext có thì hash phải có.
- V18 thay thế hàm protect_order_terms, bổ sung hai cột mới vào whitelist; không sửa V1–V17.
  Thêm hai field mutable vào CustomerOrder; không tạo entity/repository/bảng pickup_code.
- `PickupCodeBackfillCommand` chạy khi bật cờ `pickup.code.backfill-enabled=true` chủ động, mặc định false.
  Duyệt theo batch nhỏ (100) ở `PickupCodeBackfillService`, mỗi đơn dùng transaction riêng và khóa đơn;
  kiểm lại CONFIRMED, chưa hết giờ và chưa có hash/ciphertext rồi mới cấp. Chạy lặp không đổi mã.
- Nếu tồn tại hash nhưng không có bản mã hóa: dừng với thông báo vận hành an toàn,
  không tự thay mã. Log chỉ số lượng/id kỹ thuật, không mã hoặc khóa.
- Tác vụ dùng cùng issuer và Clock; không update snapshot/financial_event/ledger hoặc allocation.
  Không backfill bằng SQL ngẫu nhiên trong migration vì khóa mã hóa nằm ngoài DB.

## 5. File layout và lý do thay đổi task khác

Đường dẫn tương đối Kotlin dưới package gốc; NEW/MODIFIED dự kiến:

| File | Loại | Trách nhiệm/lý do |
|---|---|---|
| `pickup/PickupCodeProperties.kt` | NEW | Cấu hình và xác thực khóa |
| `pickup/service/PickupCodeCodec.kt` | NEW | Sinh/chuẩn hóa/hash/mã hóa/giải mã |
| `order/entity/CustomerOrder.kt` | MODIFIED | Hai cột ciphertext/keyId gắn trực tiếp với đơn |
| `pickup/service/PickupCodeIssuer.kt` | NEW | Cấp đúng một mã trong transaction |
| `pickup/service/PickupCodeService.kt` | NEW | Ownership, tra mã, quyền, quota và hiệu lực |
| `pickup/service/PickupCodeBackfillService.kt` | NEW | Cấp bù dưới khóa, từng transaction |
| `pickup/bootstrap/PickupCodeBackfillCommand.kt` | NEW | Opt-in vận hành, mặc định tắt |
| `pickup/dto/{PickupCodeView,PickupVerificationView,PickupCodeVerifyRequest}.kt` | NEW | Một DTO/file |
| `pickup/controller/{OrderPickupCodeController,StorePickupCodeController}.kt` | NEW | HTTP mapping, CurrentAccount và no-store |
| `payment/service/PaymentConfirmationService.kt` | MODIFIED | Điểm duy nhất chuyển CONFIRMED: tích hợp issuer để đáp ứng card T7.1; không đổi xác minh tiền |
| `order/repository/CustomerOrderRepository.kt` | MODIFIED | Lookup hash + store và chọn ứng viên backfill; giữ khóa cũ |
| `middleware/ratelimit/RateLimitPolicy.kt` | MODIFIED | Hai policy mới để đáp ứng NFR-14; chỉ bổ sung enum, không đổi cơ chế auth |
| `src/main/resources/application.yml`, `.env.example` | MODIFIED | Cấu hình khóa bằng env, ví dụ giá trị rỗng và backfill mặc định false |
| `src/main/resources/db/migration/V18__pickup_code_storage.sql` | NEW | Hai cột và CHECK; thay hàm bảo vệ snapshot trong migration mới |
| `docs/api/openapi.yaml` | MODIFIED | Hai endpoint, DTOs, ví dụ và lỗi |
| `src/test/kotlin/.../PickupCodeTests.kt` | NEW | HTTP thật cho AC-01..11,14; test đồng thời |
| `src/test/kotlin/.../PickupCodeCodecTests.kt` | NEW | Crypto, hỏng ciphertext, key validation cho AC-12/13 |
| `src/test/kotlin/.../PickupCodeTests.kt` (backfill tests) | NEW | Đơn cũ, lặp/đồng thời và không đổi trạng thái |
| `src/test/kotlin/.../support/AuthTestFixtures.kt`, `AdminBootstrapTests.kt` | MODIFIED | Khóa giả cho mọi Spring test profile và context bootstrap riêng, không dùng secret thật |
| `src/test/kotlin/.../{AuthMigrationTests,FoodRescueApplicationTests,SwaggerUiTests}.kt` và test Swagger có assert số lượng | MODIFIED nếu có | Cập nhật số migration/entity/path/schema thực tế, giữ nguyên ý nghĩa kiểm tra |
| Thư mục spec T7.1, `.gitignore` | MODIFIED | Traceability, báo cáo; whitelist đã có |

Không đổi StoreAuthorizationService, SecurityConfiguration, auth endpoints hoặc code T4.5.
Mọi thay đổi file task khác nêu trên được duyệt trong yêu cầu điều chỉnh kế hoạch và triển khai ngày 2026-11-23.

## 6. Task list và truy vết test

- [x] P0 — Chạy lại baseline, đọc HTML/XML, Gate A Q1–Q4 đã duyệt.
- [x] P1 — Viết test crypto/config và HTTP cấp/đọc/tra mã trước code; xác nhận đỏ do thiếu chức năng.
- [x] P2 — Migration/entity, properties/codec/issuer; tích hợp xác nhận và các test nguyên tử/đồng thời.
- [x] P3 — DTO/controllers/service, kiểm quyền/quota, no-store, hết hiệu lực theo Clock.
- [x] P4 — Test trước rồi triển khai backfill opt-in, idempotency và khóa từng đơn.
- [x] P5 — OpenAPI, hằng số regression cần cập nhật, cấu hình env và hướng dẫn vận hành.
- [x] P6 — Chạy test mục tiêu, sau đó full build; đọc HTML/XML theo suite, viết báo cáo và Swagger walkthrough.
- [x] P7 — Chỉ khi mọi AC đạt, cập nhật roadmap T7.1; không thao tác git. Sau đó mới khảo sát Gate A T7.2.

| AC | Task | Test tối thiểu |
|---|---|---|
| 01,02 | P1,P2 | Webhook qua FakePaymentGateway: cấp một mã; gửi lại/đồng thời giữ mã; lỗi cấp mã rollback payment/order/box/event |
| 03,04 | P1,P3 | GET cùng khách nhiều lần không đổi DB; người khác 404, thiếu token 401; snapshot và tiền đúng |
| 05,06,07 | P1,P3 | Owner/staff đúng quyền; thiếu/thu hồi/sai store 403; mã store khác/boxCode/orderCode bị từ chối |
| 08 | P1,P3 | 60 account/300 store; nhiều actor; lỗi mã sai không rollback counter; 429 có Retry-After |
| 09,10 | P1,P3 | Từng trạng thái không hiệu lực; Clock trước/bằng/sau pickupEndsAt; tra trước pickupStartsAt không giao |
| 11 | P1,P2 | Thiếu/thừa/sai mã/tiền muộn không cấp mã |
| 12,13 | P1,P2,P3 | Round trip, IV khác nhau, AAD sai/ciphertext sửa không giải mã; thiếu/khóa sai bị từ chối, no-store, không mã thô trong DB |
| 14 | P2,P4,P5,P6 | DB mới/nâng V17→V18; backfill còn hiệu lực/lặp/đồng thời; ApiContractTests/Swagger vẫn đạt |

Integration tests kế thừa AuthTestFixtures qua fixture chung hiện có; PostgreSQL Testcontainers và HTTP random port,
không dùng DB Compose. Dùng fake gateway và khóa giả; Clock cố định trong test biên thời gian. Context Clock riêng T7.1 được đóng bằng AFTER_CLASS DirtiesContext
để giải phóng pool trước các suite profile khác, tránh cạn connection PostgreSQL Testcontainers.
Không chạy song song hai lệnh Gradle test. Test đồng thời nghiệp vụ chỉ tạo các request đã kiểm soát bằng latch.
Không tắt, skip hoặc xóa test để build xanh; nếu lỗi baseline cũ tái diễn thì ghi nhận và xử lý theo quy định.

## 7. Rollout, rollback và rủi ro

1. Chuẩn bị key ring 32 byte ngẫu nhiên mỗi khóa trong secret manager/env; không ghi khóa thật vào tài liệu hoặc repo.
2. Chặn ingress nghiệp vụ trong cửa sổ triển khai, dừng phiên bản app cũ để không tạo CONFIRMED thiếu mã.
3. Áp V18, khởi động phiên bản mới với khóa hợp lệ và cờ backfill chủ động; chờ backfill hoàn tất,
   kiểm số đơn CONFIRMED còn hiệu lực thiếu hash/ciphertext bằng 0 trước khi bật ingress.
4. Tắt cờ backfill cho các lần chạy bình thường. Kiểm Swagger: khách đặt/trả qua fake dev → xem mã →
   owner/staff tra đúng mã; kiểm mã sai/store sai/đơn kết thúc/quota, không chuyển khoản thật bằng agent.
5. Rollback app: giữ các cột V18 và secret, không down migration/xóa dữ liệu. Nếu quay về app cũ,
   phải tắt API mã nhận và backfill các đơn xác nhận trong khoảng rollback trước khi bật lại phiên bản mới.

Rủi ro: mất khóa làm mất khả năng xem lại (giữ backup khóa); quota cửa hàng có thể cần điều chỉnh theo thực tế;
GET/verify có thể bị dữ liệu thay đổi ngay sau response nên T7.2 bắt buộc kiểm lại dưới khóa;
startup hiện yêu cầu thêm secret và test fixture phải cấp đủ cho mọi profile; rollout nhiều instance phải đồng bộ
key ring và hoàn tất backfill trước nhận traffic. Không thay dữ liệu tài chính để khắc phục thiếu mã.


## 8. Xử lý lỗi kiểm chứng ngoài T7.1 — ĐÃ DUYỆT NGÀY 2026-11-23

Full build chạy lại tái hiện FinancialPolicyTests:68 (409 kỳ vọng nhưng nhận 201).
Người dùng đã duyệt sửa FinancialPolicyService và bổ sung test T4.5 theo phương án dưới đây.

Khảo sát: FinancialPolicyService.create so sánh effectiveFrom của request trực tiếp với effectiveFrom
đọc lại từ PostgreSQL. Instant nhận đến nanosecond, PostgreSQL timestamptz lưu độ phân giải microsecond
(nguồn chính thức: https://www.postgresql.org/docs/16/datatype-datetime.html).
Đã xác nhận bằng regression cố định chạy riêng: trước sửa, cùng timestamp tạo thêm phiên bản (201 thay vì 409);
không cần chạy xen kẽ với suite khác để tái hiện.

Phương án cụ thể đề nghị duyệt nếu mở rộng phạm vi:
- Thêm regression HTTP T4.5 dùng thời điểm tương lai có phần nano cố định 123456100, gửi cùng timestamp
  hai lần theo version mới nhất: lần đầu 201, lần sau phải 409. Kiểm DB chỉ có một phiên bản mới.
  Chạy test đỏ để xác nhận nguyên nhân trước khi sửa.
- Trong FinancialPolicyService.create, chuẩn hóa now và effectiveFrom bằng truncatedTo(ChronoUnit.MICROS)
  trước kiểm tra thời gian và lưu. Dữ liệu sau chuẩn hóa trùng timestamp đã lưu phải bị từ chối.
- Không sửa migration/lịch sử chính sách, không xóa/skip test cũ, không đổi tỷ lệ tiền hay quy tắc version.
  Giữ nguyên toàn bộ kiểm tra hồi tố/chồng lấn/đồng thời; bổ sung test cố định chống tái diễn.
- Chạy FinancialPolicyTests mục tiêu rồi full build. Ghi riêng các file sửa ngoài T7.1 trong báo cáo.
