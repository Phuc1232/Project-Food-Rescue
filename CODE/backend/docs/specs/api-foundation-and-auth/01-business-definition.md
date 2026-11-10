# Business Definition: Nền API và xác thực/phân quyền

**Status:** Gate A chuyển sang lập kế hoạch theo yêu cầu người dùng ngày 2026-11-09; chờ duyệt kế hoạch tại Gate B. Các thông số mặc định còn lại được nêu rõ để duyệt cùng kế hoạch.
**Updated:** 2026-11-09
**Scope:** Giai đoạn 1 và 2 của lộ trình backend đã được người dùng yêu cầu.

## 1. Bối cảnh và mục tiêu

Backend FoodRescue đã có entity, repository, PostgreSQL và Flyway nhưng chưa có API nền, xác thực hoặc service nghiệp vụ. Mục tiêu là cung cấp API nhất quán và nhận diện người dùng/kiểm tra quyền để các giai đoạn cửa hàng và đơn hàng có thể xây dựng tiếp.

Không coi entity và database constraint là cơ chế xác thực hoặc phân quyền.

## 2. Khảo sát mã hiện tại

- Stack: Kotlin 2.3.21, Spring Boot 4.1.1, JDK 21, Spring Data JPA, PostgreSQL/Flyway.
- `build.gradle.kts` chưa khai báo starter Security, Validation, Actuator hoặc email.
- `account/entity/Account.kt`: loginIdentifier, passwordHash, displayName, status; chưa quy định định danh là email hay số điện thoại.
- `account/PlatformRole.kt`: CUSTOMER và ADMIN. Chủ cửa hàng xác định qua Store.owner; nhân viên qua StaffAssignment/StaffPermission.
- `auth/entity/LoginSession.kt`: account, tokenHash, expiresAt, revokedAt; sẽ tái sử dụng cho phiên JWT qua claim sid; tokenHash hiện tại cần được chuyển đổi rõ nghĩa khi thêm refresh rotation.
- Chưa thấy @RestController, @Service hoặc SecurityFilterChain trong source chính.
- `common/exception`, `common/response`, `config`, `security` đã có package dự phòng.
- Báo cáo test đang có ghi 9 integration tests + 2 unit tests đều thành công. Chưa chạy lại test trong lượt định nghĩa này.
- `application.yml` hiện đã được chỉnh dùng thông tin DB local trực tiếp. Không tự ghi đè chỉnh sửa này; kế hoạch sẽ tách cấu hình local khỏi secret môi trường production.
- Mọi đường dẫn source nêu trên nằm dưới `src/main/kotlin/com/foodrescue/backend/`.

## 3. Người dùng và quyền

- Khách chưa đăng nhập: đăng ký, đăng nhập, yêu cầu khôi phục mật khẩu; xem health tối giản.
- CUSTOMER: xem thông tin chính mình, quản lý phiên, đổi mật khẩu.
- ADMIN: quyền nền tảng; không cho phép tự đăng ký ADMIN từ request công khai.
- Owner: quyền theo quan hệ sở hữu Store, có thể thực hiện nghiệp vụ staff trong cửa hàng mình.
- Staff: quyền từ phân công ACTIVE và permission của cửa hàng cụ thể.
- Một account có thể có nhiều ngữ cảnh quyền; lựa chọn màn hình/vai trò phía client không cấp quyền backend.

## 4. Luồng chính

1. Đăng ký định danh và mật khẩu hợp lệ -> tạo Account cùng quyền CUSTOMER trong một transaction.
2. Đăng nhập -> kiểm tra mật khẩu/trạng thái tài khoản -> tạo LoginSession, cấp JWT access token và refresh token. Refresh hợp lệ sẽ xoay refresh token và cấp access token mới.
3. Gọi API bảo vệ -> xác minh credential, thời hạn, thu hồi và trạng thái tài khoản -> tạo ngữ cảnh người dùng.
4. `/me` trả thông tin tài khoản và ngữ cảnh quyền được phép, không trả entity thô.
5. API có storeId phải kiểm tra quyền tại cửa hàng đó trên server.
6. Đăng xuất -> thu hồi phiên; đổi hoặc đặt lại mật khẩu -> thu hồi mọi phiên và yêu cầu đăng nhập lại.
7. Quên mật khẩu -> phản hồi chung -> gửi bằng chứng khôi phục tới kênh sở hữu đã chọn -> xác minh một lần -> cập nhật mật khẩu.

## 5. Quy tắc nghiệp vụ

BR-01: Định danh được chuẩn hóa, kiểm tra định dạng và chống trùng ở cả service lẫn unique constraint; hai request đồng thời chỉ tạo một tài khoản.
BR-02: Mật khẩu được băm bằng password encoder chuẩn; không lưu/trả/log mật khẩu hay credential thô.
BR-03: Đăng ký không nhận role ADMIN, account status hoặc quyền cửa hàng do client chỉ định.
BR-04: Sai định danh/mật khẩu trả thông báo chung; tài khoản không ACTIVE không truy cập API bảo vệ.
BR-05: Credential hết hạn, bị thu hồi hoặc không hợp lệ trả 401; đã xác thực nhưng thiếu quyền trả 403. Không tiết lộ nội dung ngoài phạm vi được phép.
BR-06: Thu hồi quyền staff áp dụng cho các request tiếp theo dù phiên đăng nhập chưa hết hạn.
BR-07: Access token là JWT có chữ ký và claim bắt buộc; không lưu JWT thô trong DB. Refresh token và credential xác minh/reset là chuỗi ngẫu nhiên đủ mạnh, DB chỉ lưu hash. JWT phải kiểm tra chữ ký, thuật toán cho phép, issuer, audience, thời hạn và phiên/account đang hợp lệ.
BR-08: Reset token có hạn dùng, một lần; xác nhận reset đồng thời chỉ một request thành công. Yêu cầu mới vô hiệu token reset cũ còn hiệu lực theo account.
BR-09: Đổi mật khẩu yêu cầu mật khẩu hiện tại; reset yêu cầu bằng chứng qua kênh khôi phục. Không chỉ dựa trên accountId/email do client gửi.
BR-10: API quên mật khẩu không tiết lộ tài khoản có tồn tại. Lỗi gửi email không được làm trả token ra response hoặc ghi token vào log.
BR-11: Giới hạn tần suất đăng nhập và khôi phục; trả 429 khi vượt giới hạn. Cơ chế lưu counter phải được nêu rõ trước triển khai nhiều instance.
BR-12: API dùng UTC/ISO-8601 cho thời gian; DTO và identifier code bằng tiếng Anh.
BR-13: Lỗi API có code ổn định, thông điệp, field errors phù hợp và requestId; không trả SQL, stack trace, password hash hay nội dung secret.
BR-14: Phân trang có giới hạn size và whitelist sort; không truyền trực tiếp sort tùy ý vào truy vấn.
BR-15: Health công khai chỉ báo trạng thái; thông tin hạ tầng chi tiết không công khai production. API docs chỉ bật trong dev/test hoặc cần quyền ở môi trường khác.
BR-17: Refresh token dùng một lần, rotation nguyên tử; phát hiện reuse thu hồi toàn bộ phiên chứa token đó. Mỗi thiết bị có phiên độc lập. Logout/đổi/reset mật khẩu phải chặn cả access token còn hạn theo phạm vi thu hồi.
BR-18: Tạo/xác minh JWT và tích hợp xác thực bearer nằm dưới `common/security/jwt`; rate limit, xác định IP, requestId và xử lý security error nằm dưới package mới `middleware`. Auth service vẫn giữ nghiệp vụ account/session; store authorization dùng chung từ middleware và được kiểm tra lại tại service khi ghi dữ liệu.

BR-16: Audit ghi sự kiện đăng ký, đăng xuất, đổi/reset mật khẩu và thay đổi trạng thái phiên với dữ liệu đã loại secret.

## 6. Phạm vi

### Trong phạm vi

- Nền API: DTO convention, validation, exception handler, pagination contract, health/readiness và tài liệu API.
- Auth: register/verify-email/resend-verification/login/refresh/logout/logout-all, `/me`, change-password, forgot/reset-password, quản lý phiên.
- Security: kiểm tra tài khoản/phiên, role nền tảng, quyền store/permission; cấu hình và test 401/403.
- Cách bootstrap admin có kiểm soát: lệnh vận hành chủ động hoặc cấu hình riêng, không endpoint đăng ký ADMIN công khai và không mật khẩu mặc định.
- Migration mới cho credential reset và dữ liệu khác cần thiết; giữ nguyên V1/V2/V3 đã dùng.
- Integration tests dùng PostgreSQL riêng, kiểm tra API bằng công cụ test HTTP/Spring.

### Chưa thuộc phạm vi

- UI Android, Google/social login, MFA, nghiệp vụ duyệt cửa hàng và quản lý nhân viên thực tế.
- Menu/đóng hộp/đơn/thanh toán/đối soát.
- Không thêm endpoint kinh doanh giả chỉ để chứng minh phân quyền; kiểm tra store authorization bằng integration tests trên component và dữ liệu fixture.

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Request có lỗi validation -> HTTP 400 với code và field errors ổn định, không stack trace. |
| AC-02 | JSON lỗi, UUID sai kiểu, tham số thiếu -> lỗi API có cấu trúc; lỗi bất ngờ -> HTTP 500 chung và requestId. |
| AC-03 | Pagination vượt giới hạn hoặc sort không được phép -> từ chối rõ; giá trị mặc định được công bố. |
| AC-04 | Health sẵn sàng khi các thành phần cần thiết hoạt động; production không lộ cấu hình và API docs mặc định. |
| AC-05 | Đăng ký hợp lệ -> 201, Account + CUSTOMER; request trùng hoặc cạnh tranh không tạo account trùng. |
| AC-06 | Mật khẩu DB không bằng plaintext; response/log không chứa mật khẩu, token hash hoặc thông tin SQL. |
| AC-07 | Đăng nhập hợp lệ -> credential + expiresAt; sai mật khẩu/không tồn tại -> phản hồi chung; tài khoản bị khóa bị chặn. |
| AC-08 | Token hợp lệ gọi /me -> dữ liệu đúng account; không token/sai/hết hạn/thu hồi -> 401. |
| AC-09 | Logout thu hồi phiên hiện tại; logout-all thu hồi toàn bộ phiên; gửi logout lặp không tạo thay đổi sai. |
| AC-10 | CUSTOMER gọi chức năng ADMIN hoặc staff truy cập store khác -> 403; owner và staff có quyền đúng được cho phép. |
| AC-11 | Thu hồi staff assignment hoặc khóa account -> request tiếp theo bị chặn dù credential chưa hết hạn. |
| AC-12 | Đổi mật khẩu yêu cầu mật khẩu cũ; thành công thu hồi các phiên, mật khẩu cũ không đăng nhập được. |
| AC-13 | Forgot password cho email tồn tại/không tồn tại -> cùng phản hồi công khai; SMTP test nhận được thông điệp phù hợp, API không trả reset token. |
| AC-14 | Reset token hợp lệ dùng một lần; hết hạn/đã dùng/gửi lại đồng thời bị từ chối; reset thành công thu hồi phiên. |
| AC-15 | Vượt rate limit cấu hình -> 429; test không phụ thuộc đồng hồ thật; không log credential. |
| AC-16 | Client tự gửi role ADMIN/owner/staff không nhận được quyền tương ứng; bootstrap admin không tự chạy bằng credential mặc định. |
| AC-17 | DB/mailer lỗi -> rollback hoặc giữ trạng thái xử lý rõ; không lưu một nửa Account/role, không báo reset thành công khi chưa thực hiện. |
| AC-18 | Test persistence hiện có tiếp tục qua; migration mới áp dụng từ schema V3 và DB mới; không dùng database Compose thật trong test. |
| AC-19 | Audit các thao tác auth thành công đúng actor/thời gian, không chứa secret; security error từ filter có cùng hợp đồng lỗi API. |

## 8. Các quyết định chuyển tới Gate B

- [x] Q2 - Người dùng chọn JWT thay cho opaque access token. Thiết kế đề xuất: access JWT 15 phút; refresh token ngẫu nhiên dùng một lần, rotation; phiên tuyệt đối 7 ngày, không kéo dài vô hạn. Nhiều thiết bị được phép. Các TTL là giá trị cấu hình đề xuất để duyệt cùng Gate B.
- [x] Q4 - Người dùng yêu cầu `middleware` cho rate limit và các thành phần request liên quan; đã sửa rõ JWT authentication nằm trong `common/security/jwt`, không đặt trong middleware.
- [ ] Q1 - Giữ đề xuất email + mật khẩu, xác minh email trước ACTIVE; người dùng yêu cầu tới Gate B, chưa xác nhận riêng lựa chọn này. Đưa vào mục quyết định của 02-plan.md, không mô tả là đã duyệt.
- [ ] Q3 - Giữ đề xuất SMTP cho xác minh/reset; dev dùng mail server thử nghiệm, test dùng adapter giả. Reset 15 phút; xác minh email 24 giờ. Đưa vào mục quyết định Gate B.

Thông số đề xuất: mật khẩu 12-128 ký tự không cắt ngầm; Argon2id; login thất bại 10/15 phút theo định danh và 50/15 phút theo IP, reset/resend 3/15 phút theo định danh và 10/15 phút theo IP. Không khóa tài khoản vĩnh viễn do rate limit. Counter định danh áp dụng cả email không tồn tại. middleware chỉ tin forwarded IP từ proxy được cấu hình.

### Tiêu chí bổ sung cho JWT và email

| ID | Given / When / Then |
|---|---|
| AC-20 | JWT sai signature/algorithm/issuer/audience/claim bắt buộc hoặc thời gian -> 401; không lấy quyền từ payload chưa xác minh. |
| AC-21 | Refresh hợp lệ -> JWT mới và refresh mới; token cũ không dùng lại. Reuse thu hồi phiên và JWT còn hạn; hành vi commit thu hồi không bị rollback khi trả 401. |
| AC-22 | Hai refresh đồng thời dùng cùng token -> tối đa một lần rotation; áp dụng chính sách reuse nghiêm ngặt, client phải serialize refresh; không mất dấu vết reuse. |
| AC-23 | Account mới PENDING_VERIFICATION không đăng nhập; verify đúng hạn một lần -> ACTIVE. Verify không kích hoạt lại LOCKED/DISABLED. |
| AC-24 | Resend vô hiệu token cũ, bị rate limit; SMTP lỗi không lộ token và có thể yêu cầu gửi lại; payload email không làm đổi mật khẩu trước xác nhận. |
| AC-25 | Hai backend dùng chung DB có cùng quota; spoof forwarded headers không vượt IP limit; vượt quota trả 429 + Retry-After qua middleware. |
| AC-26 | RequestId có cả ở lỗi security/rate limit; MDC được dọn sau request; không log Authorization, refresh/reset token hoặc password. |

## 9. Ràng buộc và kiểm chứng

- Chỉ thêm dependency cần thiết, kiểm tra tương thích với Spring Boot đang dùng trong bước lập kế hoạch kỹ thuật.
- Không sửa/xóa dữ liệu DB đang chạy; test sử dụng Testcontainers.
- Không thay đổi migration cũ đã áp dụng; rollout bằng migration tăng phiên bản.
- Không tự nâng role tài khoản có sẵn hoặc tự gửi email.
- Tách mỗi trách nhiệm thành file riêng theo skill. Phase 2 sẽ ghi đường dẫn file, task checkbox, mapping AC -> task -> test, rollout/rollback và rủi ro.

## 10. Glossary

- Authentication: xác định request thuộc account nào.
- Authorization: kiểm tra account có được thực hiện thao tác trên tài nguyên cụ thể không.
- Access JWT: token có chữ ký, thời hạn ngắn, chứa account ID và session ID; không chứa secret.
- Refresh token: chuỗi ngẫu nhiên dùng một lần để cấp access JWT mới; lưu hash và lịch sử rotation trong DB.
- Role: quyền nền tảng; store permission: quyền nghiệp vụ theo cửa hàng.
- Gate A: xác nhận nghiệp vụ và các lựa chọn còn thiếu.
- Gate B: duyệt file kế hoạch kỹ thuật trước khi code.

## 11. Change log

- 2026-11-09: Theo yêu cầu người dùng, chuyển access token sang JWT, đặt JWT trong common/security/jwt và các thành phần request còn lại vào middleware và chuyển sang chuẩn bị Gate B. Bổ sung AC-20 đến AC-26; các giá trị TTL và lựa chọn email/SMTP vẫn là đề xuất để duyệt.
- 2026-11-09: Người dùng điều chỉnh vị trí JWT từ middleware sang common; kế hoạch dùng common/security/jwt cho toàn bộ tạo/xác minh và tích hợp bearer authentication.
