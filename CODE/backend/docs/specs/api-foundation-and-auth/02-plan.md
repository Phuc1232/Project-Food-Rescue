# Technical Plan: Nền API và xác thực/phân quyền

**Based on:** 01-business-definition.md
**Status:** Done — source đã triển khai và build/test thành công ngày 2026-11-09
**Last updated:** 2026-11-09

## 0. Approved decisions

Người dùng đã yêu cầu triển khai trực tiếp kế hoạch và chấp thuận các mặc định D1–D6. Đã chốt: dùng JWT; JWT nằm dưới common, middleware giữ rate limit và các xử lý request liên quan. Lượt lập kế hoạch trước chỉ tới Gate B; lượt hiện tại đã cho phép sửa source.

Các mặc định dưới đây đã được chấp thuận theo yêu cầu triển khai trực tiếp:

- [x] D1: Email + mật khẩu; xác minh email trước ACTIVE. SMTP từ biến môi trường, mail server local trong dev; không gửi email thật khi chạy test.
- [x] D2: JWT access 15 phút; refresh token ngẫu nhiên 256 bit, rotation một lần; phiên tuyệt đối 7 ngày, nhiều thiết bị. Refresh token không bắt buộc là JWT. Reuse thu hồi phiên đó; client phải serialize refresh, mất response refresh có thể cần đăng nhập lại.
- [x] D3: Reset token 15 phút; verify email token 24 giờ; mật khẩu 12-128 ký tự, Argon2id. Không trim/cắt mật khẩu; định nghĩa và test độ dài Unicode nhất quán.
- [x] D4: Giữ giới hạn login sai 10/15 phút theo định danh và 50/15 phút theo IP; forgot/reset-resend và email-resend 3/15 phút theo định danh và 10/15 phút theo IP. Thêm quota đầu vào theo bảng mục 3.4 để bảo vệ chi phí hashing.
- [x] D5: JWT RS256, private/public key theo môi trường; production không tự tạo key mỗi lần khởi động. Kiểm tra phiên/account từ DB trên mỗi request để thu hồi JWT ngay.
- [x] D6: Bootstrap admin bằng lệnh riêng chủ động, chỉ nâng quyền account email đã xác minh; không credential mặc định hoặc tự chạy khi boot.

## 1. Approach summary

Giữ stack Kotlin/Spring Boot/JPA/PostgreSQL đã có. Dùng Spring Security để xử lý bearer authentication, cùng Nimbus encoder/decoder để ký và xác minh JWT; không tự viết thuật toán crypto. Common chứa các thành phần JWT dùng chung, middleware chịu trách nhiệm các xử lý request xuyên suốt, auth chứa nghiệp vụ đăng ký/phiên/reset. Lưu refresh-token history và trạng thái phiên phía server để rotation, reuse detection và logout có hiệu lực ngay. Các API trả DTO tường minh, dùng chung format lỗi giữa MVC và security filters.

Không dùng JWT hoàn toàn stateless vì yêu cầu khóa tài khoản/thu hồi phiên ngay. Không đặt toàn bộ auth service vào middleware vì các nghiệp vụ account/session không phải filter. Chọn PostgreSQL cho counter rate limit ban đầu để dùng được trên nhiều instance mà không thêm Redis vào hạ tầng MVP; phải đo độ trễ và dọn counter hết hạn.

## 2. Codebase survey results

- Kotlin 2.3.21, Spring Boot 4.1.1, JDK 21; cấu hình tại build.gradle.kts.
- 38 entity/repository, V1/V2/V3 đã có; source dưới src/main/kotlin/com/foodrescue/backend.
- LoginSession có tokenHash/expiresAt/revokedAt; Account có loginIdentifier/passwordHash/status, chưa có emailVerifiedAt.
- AccountStatus gồm ACTIVE/LOCKED/DISABLED; schema V1 có CHECK cho enum này, nên thêm trạng thái phải có migration mới.
- PlatformRole gồm CUSTOMER/ADMIN; Store.owner và StaffAssignment/StaffPermission cung cấp quyền theo store.
- Không thấy controller/service/security chain trong source hiện tại. Không có API công khai cũ phải giữ tương thích.
- Test hiện tại dùng Testcontainers, reflection fixture cho toàn bộ entity, hardcode số entity/migration; cần cập nhật khi thêm entity và giữ kiểm tra regression có ý nghĩa.
- Báo cáo test có sẵn: 11 test thành công. Chưa chạy lại baseline ở lượt lập kế hoạch; T1 phải thực hiện trước khi sửa source.
- application.yml hiện có cấu hình DB local được người dùng sửa. Chỉ thay đổi theo phạm vi cấu hình được duyệt, không reset DB hoặc âm thầm đổi credential local.
- Kiểm tra runtime dependencies qua Gradle trong T1; không tự chọn phiên bản mới nhất ngoài BOM. OpenAPI dùng file hợp đồng tĩnh và trang docs dev để không phụ thuộc thư viện sinh docs chưa xác minh tương thích.

## 3. Design

### 3.1 Ranh giới package và request flow

- common/security/jwt: JwtProperties, JwtConfiguration, JwtTokenIssuer, SessionJwtAuthenticationConverter. Toàn bộ cấu hình encoder/decoder, claim validators và xác thực bearer -> principal nằm ở đây, không ở middleware.
- common/security: AccountPrincipal, PasswordConfiguration, StoreAuthorizationService, CurrentAccount.
- middleware: SecurityConfiguration, request ID, client IP, rate limit, security error handlers. SecurityConfiguration chỉ nối các bean JWT từ common vào Spring Security chain; không chứa logic tạo/xác minh JWT.
- auth: controller/service cho đăng ký, xác minh email, login, refresh, logout, reset, quản lý phiên.
- account: /me và DTO tài khoản.

Luồng: RequestIdFilter -> rate limit theo IP -> Spring Security BearerTokenAuthenticationFilter (framework, cấu hình qua common JWT) -> kiểm tra session/account -> authorization -> controller -> service transaction -> repository.

Rate limit theo email nằm ở service có dữ liệu DTO đã validate, gọi RateLimitService thuộc middleware; không đọc body nhiều lần trong filter. Error writer dùng chung cho 401/403/429; exception advice cho lỗi MVC. Filter được đăng ký một lần, không vừa servlet auto-registration vừa security chain.

### 3.2 JWT và session lifecycle

- Claims: sub = account UUID; sid = session UUID; jti duy nhất; iss, aud, iat, nbf, exp; không chứa password, email token hoặc dữ liệu nhạy cảm.
- Cho phép RS256 tường minh; kiểm tra signature, issuer, audience, required claims, định dạng UUID, thời gian, clock skew tối đa 30 giây. Không tin URL key hoặc thuật toán do token tùy ý cung cấp.
- Quyền hiện hành lấy từ DB; không lấy quyền store từ JWT cũ. sid phải thuộc sub, chưa thu hồi, chưa hết hạn; account ACTIVE và đã xác minh email.
- JWT không được lưu thô. Refresh token và verify/reset credential được sinh SecureRandom, lưu SHA-256 hash, có expiry/consumedAt. Hash nhanh phù hợp token ngẫu nhiên đủ entropy, không dùng thay password encoder.
- Login tạo phiên + refresh record trong một transaction rồi phát JWT. Access expiry không vượt session expiry.
- Refresh khóa account rồi session/token theo thứ tự nhất quán; đánh dấu token đã dùng, tạo token kế tiếp và JWT. Giữ hash token đã dùng để phát hiện reuse đến hết thời hạn phiên. Reuse phải commit revocation trước khi controller trả 401, tránh throw trong transaction làm rollback revocation.
- Hai refresh cùng token: lần đầu có thể thành công, lần sau bị coi là reuse và thu hồi phiên, kể cả token vừa cấp. Công bố rõ hành vi và test; không retry refresh mù khi mất response.
- Logout thu hồi session; logout-all/change/reset khóa account và thu hồi mọi session. Login/refresh cũng khóa account để không cấp phiên lọt qua revoke-all đồng thời. Logout lặp có thể trả 401 sau lần đầu, nhưng trạng thái DB không thay đổi thêm.
- Không dùng cookie authentication; token qua Authorization header/refresh DTO. CSRF chỉ tắt cho API bearer này; không bật form login/basic auth. CORS theo allowlist cấu hình, không wildcard credentials. Production yêu cầu HTTPS.

### 3.3 API contract dự kiến

Base path /api/v1. Success JSON theo DTO; lỗi {code, message, requestId, timestamp, fieldErrors}. Không nhét HTTP status lỗi vào response 200. Trường credential không được đưa vào toString/log.

| Method/path | Access | Kết quả chính |
|---|---|---|
| POST /auth/register | Public, limited | 201 accountId + PENDING_VERIFICATION; trùng 409; không tự đăng nhập |
| POST /auth/verify-email | Public, limited | 204 sau khi xác minh token hợp lệ |
| POST /auth/resend-verification | Public, limited | 202 phản hồi chung |
| POST /auth/login | Public, limited | 200 accessToken, tokenType, expiresAt, refreshToken, refreshExpiresAt |
| POST /auth/refresh | Public với refresh credential | 200 bộ token mới; invalid/reuse 401 |
| POST /auth/logout | Bearer | 204 thu hồi phiên hiện tại |
| POST /auth/logout-all | Bearer | 204 thu hồi toàn bộ phiên |
| POST /auth/change-password | Bearer + currentPassword | 204, thu hồi mọi phiên |
| POST /auth/forgot-password | Public, limited | 202 phản hồi giống nhau cho email tồn tại/không tồn tại |
| POST /auth/reset-password | Public + reset credential | 204, dùng token một lần, thu hồi phiên |
| GET /auth/sessions | Bearer | Danh sách phiên của mình có phân trang, không token/hash |
| DELETE /auth/sessions/{id} | Bearer | 204; phiên người khác trả 404 |
| GET /accounts/me | Bearer | AccountProfileResponse, role và store context |
| GET /actuator/health/liveness | Public | Chỉ trạng thái process |
| GET /actuator/health/readiness | Public | Trạng thái sẵn sàng, bao gồm DB; không chi tiết |
| GET /docs và /openapi.yaml | Dev/test | Hợp đồng API và cách sử dụng; production tắt |

Không có register-admin public. Pagination zero-based, mặc định page=0,size=20,max=100; sessions chỉ cho sort createdAt/expiresAt. Unknown sort/invalid limit -> 400. Account bị khóa/pending không được cấp token; cùng lỗi AUTHENTICATION_FAILED, không tiết lộ trạng thái qua login.

### 3.4 Rate limits và hạ tầng request

Mọi xử lý nằm tại middleware/ratelimit và middleware/request. Cửa sổ cố định 15 phút, tăng counter nguyên tử bằng SQL UPSERT ở transaction độc lập, không rollback khi auth thất bại. Khóa counter theo policy + HMAC(normalized identifier/IP) + windowStart; không lưu email/raw token làm key. Thời gian cửa sổ dùng DB để nhiều instance thống nhất; test điều khiển window bằng API clock ở unit và fixture SQL ở integration.

| Policy | Giới hạn đề xuất |
|---|---|
| Login failed | 10/15m/identifier và 50/15m/IP |
| Login incoming, trước password hash | 30/15m/identifier và 100/15m/IP |
| Forgot password hoặc resend verification | Mỗi endpoint 3/15m/identifier và 10/15m/IP |
| Register | 10/15m/IP |
| Verify/reset credential submission | 20/15m/IP |
| Refresh | 60/15m/session xác định từ credential và 120/15m/IP |

Quota failed kiểm tra trước, increment sau lỗi xác thực; quota incoming chặn các request đồng thời vượt chi phí cho phép. Quota identifier áp dụng cả địa chỉ không tồn tại để tránh lộ tồn tại. 429 có Retry-After tính đến cuối cửa sổ. Không thay account.status do vượt quota. Giới hạn request size trước parsing; key length cố định; job xóa counter hết hạn theo batch. DB unavailable -> 503, không bỏ qua rate limit ở auth endpoints.

ClientIpResolver mặc định dùng remoteAddress. Chỉ đọc X-Forwarded-For khi immediate peer thuộc trusted proxy CIDR, duyệt chuỗi từ phải sang trái tới hop không tin cậy; chuẩn hóa IPv4/IPv6. Không tin header trực tiếp từ Internet. RequestId do server tạo UUID; không phản chiếu chuỗi bất kỳ của client; response và log cùng ID, dọn MDC trong finally.

### 3.5 Data model và migration

V4__auth_credentials_and_rate_limits.sql (mới, không sửa V1-V3):

- Account: thêm email_verified_at nullable; cập nhật CHECK status thêm PENDING_VERIFICATION. Account cũ không được tự suy ra verified; login của account ACTIVE nhưng chưa verified bị chặn cho đến khi verify email. Không thay role/mật khẩu dữ liệu cũ.
- Giữ LoginSession là record phiên; token_hash cũ trở thành nullable/deprecated và không dùng để xác minh. Thu hồi mọi session cũ chưa revoked trong V4 để không lẫn hai cơ chế; các phiên mới không ghi token_hash. Không xóa lịch sử.
- RefreshToken: id, session_id, token_hash unique, created_at, expires_at, consumed_at, replaced_by_id nullable; một token chưa consumed cho mỗi phiên qua partial unique index; khóa/transaction đảm bảo rotation.
- AccountActionToken: account_id, purpose (VERIFY_EMAIL/RESET_PASSWORD), token_hash unique, expires_at, consumed_at, created_at; tối đa một token hiện hành theo account/purpose (expire/invalidate bản cũ trước khi tạo mới).
- auth_rate_limit: policy/key_hash/window_start composite PK, count, expires_at; truy cập JdbcTemplate, không bắt buộc thêm JPA entity cho counter.
- Index session(account_id,revoked_at,expires_at), refresh token hash/session, action token expiry; CHECK thời hạn, count không âm, FK không cascade xóa audit.
- Không đổi AuditLog append-only. Header/payload audit loại credential. Token cleanup chỉ xóa sau hết cửa sổ phát hiện reuse/expiry, không xóa session audit.

### 3.6 Email, mật khẩu và cấu hình

Argon2id qua Spring PasswordEncoder, định dạng hash có prefix algorithm; đo cost trên máy test và không cắt password dài. Account cũ có hash không hỗ trợ -> hướng dẫn reset, không coi chuỗi đó là plaintext.

Gửi email qua adapter sau khi transaction lưu action token commit. SMTP lỗi giữ account pending/token hiện hành, response public không lộ trạng thái tài khoản; ghi lỗi đã redact và cho resend có rate limit. Không tự giả định gửi mail thành công là verified. Chưa có durable outbox: crash sau commit trước gửi được phục hồi bằng resend; giới hạn này được công bố. Không lưu raw token vào audit hoặc hàng đợi plaintext. Mail dev không chuyển tiếp người thật; sender thật chỉ cấu hình khi triển khai.

Biến cấu hình: JWT_PRIVATE_KEY_LOCATION, JWT_PUBLIC_KEY_LOCATION, JWT_KEY_ID, JWT_ISSUER, JWT_AUDIENCE, JWT_ACCESS_TTL, AUTH_SESSION_TTL, AUTH_RESET_TTL, AUTH_VERIFY_TTL, RATE_LIMIT_KEY_SECRET, TRUSTED_PROXY_CIDRS, MAIL_HOST/PORT/USERNAME/PASSWORD/FROM, AUTH_ACTION_BASE_URL, DB_URL/USER/PASSWORD. Secret production không có fallback; test tạo key trong bộ nhớ. Key rotation giữ key xác minh cũ tới khi access token cũ hết hạn hoặc chủ động revoke toàn bộ phiên; không tạo key ngẫu nhiên mỗi lần restart production.

## 4. File layout - mỗi trách nhiệm một file

Đường dẫn Kotlin trong bảng tương đối với src/main/kotlin/com/foodrescue/backend; tests tương ứng dưới src/test/kotlin/com/foodrescue/backend. Mỗi file mới có comment mục đích và AC liên quan. Các tên ngăn bởi dấu phẩy là các file riêng.

| Feature | Files | Loại | Trách nhiệm |
|---|---|---|---|
| Lỗi API | common/response/ApiError.kt, FieldViolation.kt; common/exception/ApiException.kt, ApiExceptionHandler.kt | New | Contract lỗi và mapping MVC |
| Phân trang | common/response/PageResponse.kt; common/validation/PageQuery.kt | New | Metadata và validate page/sort |
| Request | middleware/request/RequestIdFilter.kt, ClientIpResolver.kt | New | ID/MDC và trusted proxy |
| Security wiring | middleware/SecurityConfiguration.kt; middleware/error/ApiAuthenticationEntryPoint.kt, ApiAccessDeniedHandler.kt, SecurityErrorWriter.kt | New | Chain, route access, 401/403/429 |
| JWT common | common/security/jwt/JwtProperties.kt, JwtConfiguration.kt, JwtTokenIssuer.kt, SessionJwtAuthenticationConverter.kt | New | Encoder/decoder/claim validation và xác thực phiên |
| Shared security | common/security/AccountPrincipal.kt, CurrentAccount.kt, PasswordConfiguration.kt, StoreAuthorizationService.kt | New | Principal, password encoder, owner/staff checks |
| Rate limit | middleware/ratelimit/RateLimitProperties.kt, RateLimitPolicy.kt, RateLimitFilter.kt, RateLimitService.kt, RateLimitStore.kt, RateLimitCleanupJob.kt | New | Quota IP/identifier, PostgreSQL counter, cleanup |
| Auth config/util | auth/AuthProperties.kt, AuthClockConfiguration.kt, CredentialGenerator.kt, EmailIdentifierNormalizer.kt | New | Thời hạn, Clock, SecureRandom/hash, normalize |
| Model | auth/entity/RefreshToken.kt, AccountActionToken.kt; auth/TokenPurpose.kt | New | Credential lifecycle |
| Existing model | account/entity/Account.kt, account/AccountStatus.kt, auth/entity/LoginSession.kt | Modified | Verified state, migration semantics |
| Repositories | auth/repository/RefreshTokenRepository.kt, AccountActionTokenRepository.kt | New | Query/lock credential |
| Existing repositories | account/repository/AccountRepository.kt, AccountRoleRepository.kt; auth/repository/LoginSessionRepository.kt; store/repository/StoreRepository.kt; staff/repository/StaffAssignmentRepository.kt, StaffPermissionRepository.kt | Modified | Lookups, locks, role/store queries |
| Services | auth/service/RegistrationService.kt, LoginService.kt, SessionService.kt, RefreshTokenService.kt, EmailVerificationService.kt, PasswordRecoveryService.kt, PasswordChangeService.kt | New | Tách các nghiệp vụ và transaction |
| Audit | audit/AuthAuditService.kt | New | Audit redact trong transaction phù hợp |
| Mail | auth/mail/AuthMailSender.kt, SmtpAuthMailSender.kt | New | Port và SMTP adapter |
| Controllers | auth/controller/RegistrationController.kt, LoginController.kt, SessionController.kt, EmailVerificationController.kt, PasswordController.kt; account/controller/AccountController.kt | New | Mapping HTTP, không nghiệp vụ trong controller |
| DTO | auth/dto/RegisterRequest.kt, RegistrationResponse.kt, LoginRequest.kt, TokenPairResponse.kt, RefreshRequest.kt, EmailRequest.kt, ActionTokenRequest.kt, ResetPasswordRequest.kt, ChangePasswordRequest.kt, SessionResponse.kt; account/dto/AccountProfileResponse.kt, StoreAccessResponse.kt | New | Một DTO một file; không serialize entity |
| Admin bootstrap | auth/bootstrap/AdminBootstrapCommand.kt | New | Lệnh riêng opt-in cấp role cho verified account |
| Entrypoint | FoodRescueApplication.kt | Modified | Đóng context sau lệnh bootstrap riêng; API thông thường giữ lifecycle Spring |
| Control report | docs/specs/api-foundation-and-auth/03-implementation-control.txt | New | Bảng file/thay đổi/AC/test, hướng dẫn kiểm soát |
| Build/config | build.gradle.kts; src/main/resources/application.yml, application-dev.yml, application-prod.yml; .env.example | Modified | Dependency security/JOSE/validation/mail/actuator/crypto, cấu hình |
| Migration | src/main/resources/db/migration/V4__auth_credentials_and_rate_limits.sql | New | Expand schema và revoke legacy sessions |
| API docs | docs/api/openapi.yaml, docs/auth-setup.md; config/ApiDocumentationConfiguration.kt; README.md | New/Modified | Hợp đồng tĩnh, chỉ expose dev/test, runbook |
| Tests | ApiContractTests.kt, AuthFlowTests.kt, JwtSecurityTests.kt, RefreshTokenConcurrencyTests.kt, PasswordRecoveryTests.kt, StoreAuthorizationTests.kt, RateLimitTests.kt, AuthMigrationTests.kt, AdminBootstrapTests.kt; support/AuthTestFixtures.kt, RecordingAuthMailSender.kt | New | Test theo AC |
| Regression | FoodRescueApplicationTests.kt; src/test/resources/application-test.yml | Modified/New | Fixture entity mới, test key/config và số migration |

## 5. Task list

- [x] T1. Xác minh baseline và dependency.
  - Work: chạy test hiện có; resolve security/JOSE/validation/mail/actuator/crypto từ BOM, đọc API thực tế. Ghi lỗi có sẵn, chưa thay business behavior.
  - Files: build.gradle.kts, test config; docs/auth-setup.md.
  - Depends on: Gate B. Verify: gradlew.bat test, dependencyInsight; Covers: AC-18.
- [x] T2. Thêm API contract và middleware request cơ bản.
  - Work: DTO lỗi, validation, pagination, requestId, health, docs static.
  - Files: common response/exception/validation, middleware/request, docs/API config theo mục 4.
  - Depends on: T1. Verify: ApiContractTests; Covers: AC-01..04, AC-26.
- [x] T3. Migrate credential và phiên.
  - Work: V4, entity/repository mới, nullable legacy tokenHash, verified account, fixture mới.
  - Files: model/repository/migration/test fixture mục 4.
  - Depends on: T1. Verify: AuthMigrationTests trên DB trống và từ V3 có dữ liệu; regression. Covers: AC-18, nền AC-21..24.
- [x] T4. Xây rate limit middleware.
  - Work: IP resolver, quota atomic, độc lập transaction, cleanup, 429 và Retry-After.
  - Files: middleware/ratelimit, ClientIpResolver, SecurityErrorWriter.
  - Depends on: T2,T3. Verify: RateLimitTests với cạnh tranh, 2 service instances, proxy spoof, boundary window. Covers: AC-15,25,26.
- [x] T5. Xây đăng ký và xác minh email.
  - Work: Argon2id, normalize email, tạo account/role/token atomic, mail adapter sau commit, verify/resend, audit.
  - Files: Registration/EmailVerification service/controller/DTO, password config, mail và audit.
  - Depends on: T3,T4. Verify: AuthFlowTests, mail fake, duplicate/concurrent registration. Covers: AC-05,06,16,17,19,23,24.
- [x] T6. Xây JWT common và login.
  - Work: encoder/decoder, validators, session/account check, security chain dùng component common, login và /me.
  - Files: common/security/jwt, common principal, middleware wiring/errors, LoginService/Controller, AccountController.
  - Depends on: T5. Verify: JwtSecurityTests + AuthFlowTests; forged/expired/wrong audience/issuer/alg/missing sid and locked account. Covers: AC-07,08,11,20,26.
- [x] T7. Xây refresh rotation và quản lý phiên.
  - Work: refresh, reuse detection, revoke, logout/logout-all/session listing và revoke-by-id.
  - Files: RefreshTokenService, SessionService và controller/DTO tương ứng.
  - Depends on: T6. Verify: RefreshTokenConcurrencyTests; logout vs refresh/login; commit revocation sau 401. Covers: AC-09,21,22,19.
- [x] T8. Xây đổi/khôi phục mật khẩu.
  - Work: current password check, forgot/reset token, mail sau commit, reset one-time và revoke-all.
  - Files: PasswordChangeService/PasswordRecoveryService, PasswordController, DTO/mail/audit.
  - Depends on: T7. Verify: PasswordRecoveryTests với SMTP failure, expired/consumed/concurrent reset, enumeration-safe responses. Covers: AC-12..15,17,19,24.
- [x] T9. Hoàn thiện authorization và admin bootstrap.
  - Work: store owner/staff permissions, role platform, bootstrap opt-in không default secret.
  - Files: StoreAuthorizationService, role/store/staff repositories, AdminBootstrapCommand.
  - Depends on: T6. Verify: StoreAuthorizationTests/AdminBootstrapTests; staff revoked/account locked kiểm tra request kế tiếp; không endpoint fake business. Covers: AC-10,11,16.
- [x] T10. Kiểm thử xuyên suốt và runbook.
  - Work: hoàn thiện OpenAPI, secret/config examples, test coverage theo AC, cập nhật README và hướng dẫn cấp/revoke key/session.
  - Files: docs và tests trong mục 4.
  - Depends on: T2..T9. Verify: gradlew.bat build, contract check, HTTP register -> verify -> login -> me -> refresh -> logout -> 401; đọc reports không chỉ exit code. Covers: tất cả AC.

## 6. Traceability

| AC | Task | Test |
|---|---|---|
| 01,02,03,04 | T2,T10 | ApiContractTests |
| 05,06,07,08 | T5,T6 | AuthFlowTests, JwtSecurityTests |
| 09,21,22 | T7 | RefreshTokenConcurrencyTests, AuthFlowTests |
| 10,11,16 | T6,T9 | StoreAuthorizationTests, AdminBootstrapTests |
| 12,13,14,17,24 | T5,T8 | PasswordRecoveryTests, AuthFlowTests |
| 15,25,26 | T2,T4,T6 | RateLimitTests, ApiContractTests, JwtSecurityTests |
| 18 | T1,T3,T10 | AuthMigrationTests, FoodRescueApplicationTests |
| 19 | T5..T8 | AuthFlowTests, PasswordRecoveryTests (audit/no secrets) |
| 20 | T6 | JwtSecurityTests |
| 23 | T5 | AuthFlowTests |

## 7. Test strategy

Unit test cho validators, quota window, pagination, normalization và JWT claims bằng Clock cố định. Integration test với PostgreSQL Testcontainers cho locking, uniqueness, migration V3->V4 và multi-instance rate limit. HTTP tests chạy trên random port để kiểm tra cả SecurityFilterChain và MVC, không chỉ gọi controller trực tiếp. Email test dùng RecordingAuthMailSender có mô phỏng lỗi; không dùng hòm thư thật. Credential sample tạo riêng cho tests và không nằm trong production configuration.

Không coi việc cộng test-count là tiêu chí thành công: phải kiểm tra từng AC, transaction rollback/commit, forbidden fields và truy cập sai store. Chạy regression persistence để đảm bảo các bảng order/payment hiện tại vẫn dùng được.

## 8. Rollout & rollback

1. Chạy baseline, migration trên bản sao/DB test trước; backup database đích trước triển khai thật.
2. Cấu hình keys, mail, rate-limit secret, trusted proxies và health. Dev key dùng công cụ sinh key local không commit; test key tự sinh.
3. Triển khai migration V4 với app mới; legacy session bị thu hồi, account cũ chưa verified phải verify trước login. Đây là thay đổi cần thông báo người dùng khi đã có dữ liệu thật.
4. Smoke test môi trường staging với email thử nghiệm; bootstrap admin chủ động sau verify, không tự nâng account có sẵn.
5. Rollback binary giữ V4 mở rộng, không drop bảng hoặc sửa flyway history. Nếu binary cũ chưa hiểu trạng thái mới/null legacy tokenHash thì không rollback mù: giữ maintenance, dùng bản sửa tiến hoặc phục hồi backup theo runbook đã thử. Không hứa rollback dữ liệu không mất mát sau khi đã có traffic.

## 9. Risks

| Rủi ro | Khả năng / tác động | Kiểm soát |
|---|---|---|
| JWT logout nhưng vẫn truy cập được | Trung bình / cao | sid + DB check mỗi request, không cache quyền kéo dài |
| Refresh race/reuse rollback | Trung bình / cao | Lock order account->session->token, commit revoke rồi trả lỗi; concurrency tests |
| Spam login/mail hoặc spoof IP | Cao / cao | Pre-hash quota, failure quota, trusted proxy parser, DB counter atomic |
| Token/secret lọt log | Trung bình / cao | Redaction, không data-class toString chứa credentials, log capture tests |
| SMTP lỗi hoặc crash sau commit | Trung bình / vừa | Resend có quota; account pending, response không lộ tồn tại; công bố chưa có outbox |
| Entity mới làm test fixture cũ hỏng | Cao / vừa | T3 cập nhật fixture và migration assertions, giữ regression |
| Migration ảnh hưởng tài khoản cũ | Trung bình / cao | Không tự verify; thử V3 có dữ liệu; không sửa V1-V3 |
| DB lookup/rate limit tăng tải | Trung bình / vừa | Index, batch cleanup, đo latency, nâng sang cache/distributed limiter sau khi có số liệu |
| Dependency không tương thích Boot hiện tại | Trung bình / vừa | BOM, compile spike T1; OpenAPI static để không thêm generator chưa kiểm chứng |

## 10. Out of scope / later

Android UI, OAuth/social login, MFA, Redis, durable mail outbox, dịch vụ nghiệp vụ cửa hàng/đơn/thanh toán và endpoint CRUD toàn bộ entity. Không thay thế các policy transaction nghiệp vụ bằng filter chung.

## 11. References & open questions

Các quyết định D1–D6 được triển khai theo yêu cầu trực tiếp của người dùng ngày 2026-11-09.

Cơ sở API thư viện: [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) mô tả hỗ trợ bearer JWT, decoder và validators; [Password storage](https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html) mô tả encoder và định dạng hash. Cấu hình cụ thể sẽ được đối chiếu dependency thực đã resolve trong T1.

## 12. Change log

- 2026-11-09, người dùng: chọn JWT và chuyển tới Gate B; yêu cầu middleware cho rate limit/các xử lý request.
- 2026-11-09, người dùng: sửa vị trí JWT sang common. Kế hoạch đặt toàn bộ tạo/xác minh/authentication JWT ở common/security/jwt; middleware chỉ wiring và xử lý request không phải JWT.
- 2026-11-09, assistant: tạo kế hoạch 10 task; ghi rõ các mặc định chưa duyệt, migration, API, file layout, test và rollback. Chưa thay source.

- 2026-11-09, implementation: baseline 11 tests PASS; dependency compile PASS. Bổ sung entrypoint vào layout để lệnh bootstrap tự kết thúc, báo cáo .txt theo yêu cầu; kiểm thử mở rộng đang chạy. Store authorization dùng JDBC projection để lấy context/quyền hiện hành, giữ repository nghiệp vụ sẵn có.

- 2026-11-09, completion: build PASS, 39 tests PASS (xem 03-implementation-control.txt). OpenAPI 15 paths, references hợp lệ. JOSE 7.1.1 được chọn qua Spring Boot 4.1.1 BOM; Argon2 dùng Bouncy Castle 1.84. JWT key rotation chọn phương án thu hồi toàn bộ phiên trước thay key theo runbook; không triển khai keyring nhiều key. Docs build input được whitelist trong .gitignore backend.
