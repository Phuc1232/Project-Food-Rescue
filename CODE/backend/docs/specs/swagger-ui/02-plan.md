# Technical Plan: Tích hợp Swagger UI cho FoodRescue

**Based on:** yêu cầu ngày 2026-11-10 và `docs/auth-setup.md` hiện tại.
**Status:** Done — source, HTTP/profile/context tests và browser sandbox đã kiểm chứng ngày 2026-11-10.
**Last updated:** 2026-11-10

## 0. Decisions needed from you

Các lựa chọn dưới đây là đề xuất để duyệt cùng kế hoạch; có thể chỉnh trực tiếp trước triển khai:

- [x] D1: Giữ hợp đồng OpenAPI tĩnh hiện có; thêm Swagger UI phục vụ nội bộ từ JAR, không dùng CDN lúc chạy.
- [x] D2: `/docs` chuyển tới `/docs/`; `/docs/` là Swagger UI. Chỉ mở khi có profile dev/test và không có prod; production và default profile đều chặn.
- [x] D3: Nhập access token thủ công qua Authorize; không tự login, không tự refresh, không lưu authorization qua reload.

Người dùng đã duyệt triển khai D1–D3 và toàn bộ task list qua yêu cầu ngày 2026-11-10.

## 1. Mục tiêu và phạm vi

Developer/tester mở `/docs`, xem API theo nhóm, điền request rồi dùng Try it out để gọi backend đang chạy. Sau login, copy `accessToken` vào Authorize và gọi `/api/v1/accounts/me`. Backend tiếp tục xác thực JWT, thu hồi phiên và rate limit như hiện tại.

Trong phạm vi: giao diện Swagger UI, asset CSS/JS, nối OpenAPI hiện có, Bearer authorization, phân nhóm/example giả, cấu hình đường dẫn/profile, kiểm thử và hướng dẫn sử dụng. Không thay đổi nghiệp vụ auth, DB, migration, `.env` loader, RSA/SMTP hoặc `local.properties`. Không mở docs production hoặc tạo frontend verify/reset email.

## 2. Codebase survey results

- Stack: Kotlin 2.3.21, Spring Boot 4.1.1, Spring MVC/Security, JDK 21; Gradle tại `build.gradle.kts`.
- `docs/api/openapi.yaml`: OpenAPI 3.1.0, nội dung JSON hợp lệ dưới định dạng YAML; 15 paths, đã có `bearerAuth` (`http` / `bearer` / `JWT`), public operations dùng security rỗng. Chưa có tags/examples/server URL tường minh.
- `tasks.processResources` đang đưa hợp đồng này vào `api/openapi.yaml` trong JAR. Tiếp tục giữ một bản hợp đồng nguồn.
- `config/ApiDocumentationConfiguration.kt`: controller profile dev/test; `/openapi.yaml` trả resource, `/docs` trả HTML giới thiệu, chưa có Swagger UI.
- `middleware/SecurityConfiguration.kt`: chỉ permit `/docs` và `/openapi.yaml` khi có dev/test; chưa permit asset. Điều kiện hiện tại vẫn mở docs nếu đồng thời bật prod và dev/test; đề xuất sửa riêng điều kiện docs thành prod có ưu tiên tắt.
- `ApiContractTests.kt` kiểm tra OpenAPI trả 200; `ProductionApiContractTests` cùng file kiểm tra docs production 401. `support/AuthTestFixtures.kt` có HTTP client, PostgreSQL container, RSA test và mail giả; tận dụng hạ tầng này.
- Báo cáo XML hiện có ghi 39 tests, 0 failures/errors. Đây là kết quả có sẵn, chưa chạy lại baseline trong lượt planning; T1 phải chạy lại vì workspace có thay đổi sau lần build trước.
- `docs/auth-setup.md` hiện đã hướng dẫn `.env` tự nạp qua Spring Config Data. Giữ nguyên phần này; chỉ bổ sung cách dùng UI.
- Workspace có nhiều thay đổi chưa commit, kể cả `compose.yml`; không reset hoặc gộp thay đổi ngoài phạm vi. `.gitignore` chỉ whitelist thư mục spec auth hiện có; thư mục spec swagger-ui cần whitelist ở T6.

## 3. Approach summary

Dùng bản phân phối Swagger UI qua WebJar `org.webjars:swagger-ui`, resolve bằng một Gradle configuration riêng chỉ phục vụ build. Khi processResources, chép đúng bundle JS/CSS cần dùng vào `api-docs/vendor/` trong output; không đặt WebJar vào runtimeClasspath. Trang HTML và initializer của ứng dụng đặt dưới `src/main/resources/api-docs/`, được controller có điều kiện profile phục vụ.

Lý do: đã có hợp đồng OpenAPI được kiểm soát, yêu cầu chính là bổ sung giao diện tương tác. Swagger hỗ trợ phục vụ UI bằng static HTML/CSS/JS. Springdoc 3.x cũng là phương án hợp lệ cho Boot 4 (tài liệu hiện ghi Boot 4.1.x tương ứng springdoc 3.1.x–3.2.x), nhưng việc thêm scan/generate spec chưa cần thiết trong phạm vi này. Nếu sau này muốn sinh contract từ controller, lập kế hoạch riêng để tránh hai nguồn hợp đồng.

Không dùng CDN để UI hoạt động trong mạng nội bộ và không tải JavaScript bên thứ ba khi đang thao tác với token. Không tự viết giao diện thay Swagger UI. Phiên bản WebJar cụ thể chưa được resolve ở lượt planning: T1 phải xác nhận artifact 5.x hỗ trợ OpenAPI 3.1, pin patch version, kiểm tra license và ghi phiên bản vào Change log; không dùng `latest`, `+` hoặc tự chuyển sang springdoc/CDN khi resolve lỗi.

## 4. Design

### 4.1 Luồng và URL

`GET /docs` → redirect tương đối `docs/` → `GET /docs/` → HTML + asset cùng origin → `GET /openapi.yaml` → Try it out gọi API cùng backend.

| Route | dev/test, không prod | prod / default / prod+dev/test |
|---|---|---|
| GET `/docs` | 302 tới `docs/` | Bị chặn |
| GET `/docs/` | 200 HTML Swagger UI | Bị chặn |
| GET `/docs/assets/swagger-ui.css` | 200 CSS | Bị chặn |
| GET `/docs/assets/swagger-ui-bundle.js` | 200 JavaScript | Bị chặn |
| GET `/docs/assets/swagger-initializer.js` | 200 JavaScript | Bị chặn |
| GET `/openapi.yaml` | 200 contract hiện có | Bị chặn |

Controller docs và Security chain phải dùng cùng điều kiện `(dev OR test) AND NOT prod`. Production: anonymous nhận 401 như policy hiện hành, bearer hợp lệ nhận 403; không trả nội dung UI/spec/asset. Chỉ permit GET/HEAD trên các docs routes cần thiết, không permit rộng `/webjars/**`, `/api/**` hoặc toàn bộ static resources.

Asset được đọc từ thư mục classpath ngoài `static`/`public` để không có đường dẫn tự public khác. Asset controller chỉ nhận tên trong allowlist ba file ở bảng; đường dẫn lạ trả 404 trong dev/test. Bundle/css được lấy từ upstream đã pin qua build; initializer là file riêng do ứng dụng quản lý. Giữ license/notice của asset trong JAR; không phục vụ mặc định trang Petstore hoặc initializer có sẵn của vendor.

Dùng URL tương đối trong HTML và initializer: tại `/docs/`, contract là `../openapi.yaml`; asset là `assets/...`. Bổ sung `servers: [{url: "."}]` vào OpenAPI để API base được giải quyết theo vị trí tài liệu. Kiểm tra cả context path `/foodrescue` để tránh gọi nhầm về root. Không hardcode `localhost:8080`; không sửa trusted proxy hoặc forward-header policy. Proxy có rewrite phức tạp ngoài context path thông thường chưa thuộc phạm vi.

### 4.2 Swagger UI và authorization

Initializer dự kiến:

- `url` trỏ tới hợp đồng tương đối; `dom_id: "#swagger-ui"`, `layout: "BaseLayout"`, preset API chuẩn.
- `filter: true`, `deepLinking: true`, `docExpansion: "list"`, `displayRequestDuration: true`.
- `supportedSubmitMethods: ["get", "post", "delete"]`; giữ `tryItOutEnabled: false` để người dùng chủ động nhấn Try it out rồi Execute.
- `persistAuthorization: false`, `withCredentials: false`, `queryConfigEnabled: false`, `validatorUrl: null`; tắt validator online và không cho đổi spec qua query string.
- Không có interceptor tự lấy token/login/refresh, analytics, hay lưu credential vào localStorage/sessionStorage. Swagger UI vẫn hiển thị request/response token khi người dùng chạy login; docs phải nói rõ reload/đóng tab sau sử dụng.

Authorize dùng `bearerAuth` sẵn có: nhập access JWT thuần, không thêm chữ Bearer. Header được UI tạo cho operations có security bearer; các endpoint public vẫn giữ security rỗng. Sau refresh thành công, người dùng thay access token trong Authorize và dùng refresh token mới cho lần sau. Sau logout/reset/change-password, access token cũ bị backend từ chối; UI không tự xóa hay tự cấp token thay thế.

Ghi chú ngắn trên trang: token chỉ giữ trong phiên trang, email verification lấy từ SMTP local, refresh dùng một lần, và quota vẫn áp dụng. Không nhúng credential mẫu thật hoặc request tự chạy khi mở trang. Cấu hình cùng origin không yêu cầu mở rộng CORS.

### 4.3 Chất lượng hợp đồng

Bổ sung tags Registration, Authentication, Sessions, Password, Account, Health; summary rõ ràng cho từng operation. Thêm request examples bằng email miền `.test`, password giả đủ policy và placeholder cho token; không dùng default credential hoặc example token có chữ ký thật. Đánh dấu password input `writeOnly` và `format: password` theo ý nghĩa field, giữ required/security/status/schema hiện có. Token trong login response vẫn phải đọc/copy được. Không thay đổi route hoặc schema nghiệp vụ chỉ để phục vụ UI.

### 4.4 Dữ liệu và quan sát

Không có migration hoặc bảng mới. Giữ requestId và middleware hiện hành. Không thêm log request body, Authorization, URL chứa token hoặc lưu browser trace/HAR có credential vào repository. Bundle tăng kích thước JAR; asset được đóng gói một lần, không cần Internet ở runtime.

## 5. File layout — mỗi trách nhiệm một file

Đường dẫn Kotlin tương đối với `src/main/kotlin/com/foodrescue/backend/`.

| Feature/Rule | File | New/Modified | Trách nhiệm |
|---|---|---|---|
| Build asset | `build.gradle.kts` | Modified | Configuration riêng, pin WebJar, copy bundle/css + notice vào processResources; giữ copy contract sẵn có |
| HTML và contract | `config/ApiDocumentationConfiguration.kt` | Modified | Redirect `/docs`, trả index `/docs/`, giữ `/openapi.yaml`; cùng profile predicate |
| Asset routing | `config/SwaggerUiAssetController.kt` | New | Serve ba asset allowlist với content type đúng; ngoài static/public; profile predicate |
| UI page | `src/main/resources/api-docs/index.html` | New | Container UI, script/css local, ghi chú thao tác |
| License | `src/main/resources/api-docs/LICENSE.swagger-ui.txt` | New | Giữ nguyên Apache-2.0 license upstream, đóng gói cùng notice bundle |
| UI init | `src/main/resources/api-docs/swagger-initializer.js` | New | SwaggerUIBundle options và URL tương đối; không tự động hóa auth |
| Context-path quota | `middleware/ratelimit/RateLimitFilter.kt` | Modified | Bỏ contextPath trước route lookup để quota/size limit vẫn áp dụng đúng; không đổi policy auth |
| Docs access | `middleware/SecurityConfiguration.kt` | Modified | Match docs GET/HEAD + assets; prod có ưu tiên tắt; không thay rule API |
| Contract | `docs/api/openapi.yaml` | Modified | Tags, summaries, examples giả, password metadata, relative server URL |
| HTTP UI tests | `src/test/kotlin/com/foodrescue/backend/SwaggerUiTests.kt` | New | HTML/assets/spec dev/test, missing assets, requestId, đóng gói và route |
| Profile tests | `src/test/kotlin/com/foodrescue/backend/SwaggerUiProfileTests.kt` | New | prod/default/prod+test chặn docs kể cả khi có bearer; UI disabled không ảnh hưởng API |
| Context path tests | `src/test/kotlin/com/foodrescue/backend/SwaggerUiContextPathTests.kt` | New | `/foodrescue/docs/`, relative redirects/spec/server URL đúng |
| Regression contract | `src/test/kotlin/com/foodrescue/backend/ApiContractTests.kt` | Modified | Giữ kiểm tra errors/health/auth; cập nhật docs redirect nếu cần |
| Fixture helper | `src/test/kotlin/com/foodrescue/backend/support/AuthTestFixtures.kt` | Modified nếu cần | Cho phép request path/context path của test mới; không đổi DB/mail isolation |
| Hướng dẫn | `docs/auth-setup.md`, `README.md` | Modified | URL docs, cách Authorize, ví dụ Try it out/refresh/logout, giới hạn profile |
| Version control | `.gitignore` | Modified | Whitelist `docs/specs/swagger-ui/` để giữ plan/report; không bỏ ignore secrets |
| Control report | `docs/specs/swagger-ui/03-implementation-control.txt` | New sau triển khai | File/thay đổi/AC/test, version asset, kết quả kiểm tra browser và giới hạn còn lại |

Vendor assets chỉ sinh trong build output, không chép thủ công các bundle vào source. File mới có header mục đích + AC liên quan. Không sửa báo cáo hash của lần triển khai auth trước; báo cáo Swagger ghi nhận riêng các file được thay đổi tiếp.

## 6. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| SW-01 | Dev/test không prod, mở `/docs` → tới `/docs/`; render đủ 15 paths từ contract hiện có, không Petstore, không lỗi load asset. |
| SW-02 | Từ UI, Execute login hợp lệ → 200; Authorize với access JWT → `/accounts/me` 200 đúng account; thiếu/sai JWT → 401. |
| SW-03 | UI cho refresh/logout và hiển thị response thật; refresh thủ công thay token, reuse → 401; logout → access cũ bị từ chối. Không có tự refresh/retry. |
| SW-04 | Prod/default/prod+dev hoặc prod+test → UI/spec/assets không truy cập được; anonymous 401, bearer hợp lệ 403; không có alias static public. |
| SW-05 | Trong browser sau khi đã tải JAR, không cần tải asset/validator/spec từ domain ngoài; reload mất authorization; không ghi credential vào browser storage của ứng dụng. |
| SW-06 | Chạy context path `/foodrescue`, UI/spec/assets và Try it out đều gọi đúng prefix/port; không gọi root hoặc host hardcode. |
| SW-07 | Tags/examples hỗ trợ thao tác; giữ nguyên routes, security, schemas bắt buộc và HTTP statuses; toàn bộ test auth/persistence hiện có tiếp tục qua. |
| SW-08 | JAR chứa CSS/bundle/initializer/contract và notice cần thiết; phiên bản dependency pin tường minh; có hướng dẫn vận hành và báo cáo `.txt`. |

## 7. Task list

- [x] T1. Xác minh baseline và nguồn asset.
  - Work: chạy baseline trước sửa code; resolve WebJar ở configuration riêng, chọn/pin patch 5.x hỗ trợ OpenAPI 3.1, kiểm tra layout JAR/license và dependency; ghi phiên bản + nguồn vào Change log.
  - Files: `build.gradle.kts`; Change log trong plan.
  - Depends on: duyệt kế hoạch.
  - Verify: `.\gradlew.bat test --console=plain`; dependency report của configuration asset; inspect bundle/css/license trong artifact. Không tự thay kiến trúc khi resolve thất bại.
  - Covers: SW-07,08.
- [x] T2. Đóng gói và phục vụ Swagger UI.
  - Work: copy asset qua processResources, tạo HTML/init, asset controller, redirect và profile predicate thống nhất với Security chain; cấu hình không CDN/online validator/persistent auth. Thêm relative server URL vào contract cùng bước để UI gọi đúng backend.
  - Files: build/config/security/resources trong mục 5; `docs/api/openapi.yaml`; `SwaggerUiTests.kt`.
  - Depends on: T1.
  - Verify: HTTP `/docs` redirect, HTML/spec/CSS/JS 200 + content type, unknown asset 404; `bootJar` chứa đủ resource; `/api/v1/accounts/me` vẫn 401 nếu không credential.
  - Covers: SW-01,02,05,08.
- [x] T3. Hoàn thiện nội dung tương tác API.
  - Work: tags/summaries/examples/password metadata; hướng dẫn copy JWT, thay access sau refresh, logout và verify email qua SMTP local. Không tự phát request khi load trang.
  - Files: `docs/api/openapi.yaml`, `api-docs/index.html`, `docs/auth-setup.md`.
  - Depends on: T2.
  - Verify: parse contract, mọi `$ref` resolve; đầy đủ 15 paths, bearer scheme và security public/protected giữ đúng; render nhóm/schema trong browser.
  - Covers: SW-01,02,03,07.
- [x] T4. Kiểm chứng profile và context path.
  - Work: tests dev/test, default, prod và mixed prod+dev/test; anonymous và bearer; context path khác root. Bảo đảm assets không truy cập qua `/webjars/**`, `/api-docs/**` hoặc `/static/**`.
  - Files: `SwaggerUiProfileTests.kt`, `SwaggerUiContextPathTests.kt`, `SwaggerUiTests.kt`; fixture helper chỉ khi cần.
  - Depends on: T2,T3.
  - Verify: HTTP integration tests trên PostgreSQL test riêng; profile precedence, content type, URL redirect/relative và API auth vẫn đúng. Không dùng DB ứng dụng.
  - Covers: SW-04,06,07.
- [x] T5. Kiểm tra trực tiếp trong browser.
  - Work: mở UI dev với tài khoản thử; login → Authorize → me → refresh thủ công → thay access → logout → me 401; reload kiểm tra authorization mất; kiểm tra Network/Console và prefix khi có context path.
  - Files: ghi kết quả đã loại credential vào báo cáo `.txt`; sửa file chức năng ở mục 5 nếu phát hiện lỗi.
  - Depends on: T4.
  - Verify: browser render thật và Execute request thật (không chỉ HTTP trả HTML). Dùng DB/mail sandbox và account thử; không gửi email tới người thật. Ghi rõ môi trường đã dùng. Nếu không có browser/sandbox, đánh dấu kiểm chứng này chưa hoàn thành.
  - Covers: SW-01,02,03,05,06.
- [x] T6. Chốt build, hướng dẫn và báo cáo kiểm soát.
  - Work: cập nhật README/runbook, whitelist spec folder, tạo `.txt` theo cùng quy ước báo cáo auth và tick task dựa trên kết quả thật.
  - Files: `README.md`, `docs/auth-setup.md`, `.gitignore`, `03-implementation-control.txt`, plan này.
  - Depends on: T1..T5.
  - Verify: `.\gradlew.bat build --console=plain`, `git diff --check`; kiểm tra JAR và kết quả từng suite; bảo đảm chỉ sửa file trong phạm vi, không lộ secret.
  - Covers: SW-07,08.

## 8. Traceability và test strategy

| AC | Task | Bằng chứng |
|---|---|---|
| SW-01 | T2,T3,T5 | SwaggerUiTests + browser render/Network |
| SW-02 | T2,T3,T5 | Auth regression + browser login/Authorize/me |
| SW-03 | T3,T5 | RefreshTokenConcurrencyTests/AuthFlowTests + browser refresh/logout |
| SW-04 | T4 | SwaggerUiProfileTests, anonymous/bearer và mixed profiles |
| SW-05 | T2,T5 | Init config assertions + browser reload/storage/Network |
| SW-06 | T4,T5 | SwaggerUiContextPathTests + browser request prefix |
| SW-07 | T1,T3,T4,T6 | Baseline/full build, kiểm tra contract và regression |
| SW-08 | T1,T2,T6 | Artifact inspect, pin version/license, report/runbook |

Tận dụng PostgreSQL Testcontainers, RSA test và RecordingAuthMailSender; không thêm test framework browser chỉ cho thay đổi này nếu công cụ browser hiện có dùng được. Các test profile mới nên tái dùng fixture nhưng không vô tình kế thừa active profile test cho ca default/prod; thử rõ predicate khi nhiều profiles cùng bật. Test HTTP kiểm tra resource/profile/auth; browser xác nhận JavaScript thực sự chạy, Authorize và Try it out hoạt động. Không coi HTTP 200 HTML là hoàn tất SW-01/SW-02.

## 9. Rollout và rollback

1. Duyệt D1–D3 và task list; chạy lại baseline và pin asset trước tích hợp.
2. Build, chạy tests trên DB riêng, thử browser dev; không cần migration hoặc sửa secret.
3. Dev mở `/docs` như cũ, tự chuyển sang UI. `/openapi.yaml` giữ URL và nội dung hợp đồng tương thích ngoài metadata bổ sung.
4. Production tiếp tục tắt docs; mixed profiles có prod được thắt chặt thành tắt docs. Ghi thay đổi này trong runbook.
5. Rollback bằng binary trước tích hợp hoặc revert riêng commit Swagger UI: khôi phục HTML `/docs` cũ, Security docs matching và build asset; không rollback schema hay thay `.env`.

## 10. Risks

| Risk | Likelihood | Impact | Mitigation / early detection |
|---|---|---|---|
| WebJar version/layout hoặc license thiếu | Trung bình | Build fail/UI trắng | Resolve/pin/inspect ở T1; chỉ copy asset đã kiểm tra; không chọn version động |
| Security chặn JS hoặc vô tình public docs production | Trung bình | UI không chạy hoặc lộ docs | Predicate thống nhất; test dev/prod/default/mixed và alias resources |
| Token bị giữ qua reload hoặc đưa ra domain ngoài | Trung bình | Lộ credential thử nghiệm | Local assets, no validator, persistAuthorization false; browser storage/Network check |
| Sai base URL khi đổi port/context path | Trung bình | Try it out gọi nhầm backend | Relative URL + servers '.', test prefix và browser Execute |
| Quota/reuse làm thao tác thử bị 429/401 | Cao khi click lặp | Khó hiểu kết quả | Ghi hướng dẫn quota/serialize refresh; hiển thị response backend; không auto retry |
| Contract tĩnh lệch source khi API thay đổi sau này | Trung bình | UI gửi request sai | Một nguồn contract; cập nhật hợp đồng và tests trong cùng thay đổi API |

Không có thao tác dữ liệu trong planning; khi triển khai, Execute là request thật và có thể tạo account/thu hồi phiên trên môi trường đang mở. Dùng dữ liệu thử trong dev/sandbox.

## 11. Out of scope và open questions

Sinh OpenAPI tự động bằng annotations/springdoc, OAuth/OIDC UI, auto-login/auto-refresh, lưu token lâu dài, bật docs production, endpoint nghiệp vụ mới và sửa nghiệp vụ auth không thuộc phạm vi. Chưa chốt patch version WebJar; T1 chịu trách nhiệm xác nhận/pin, không cần người dùng tự chọn phiên bản thư viện.

## 12. References

- [Swagger UI installation](https://swagger.io/docs/open-source-tools/swagger-ui/usage/installation/): static distribution và bundle.
- [Swagger UI configuration](https://swagger.io/docs/open-source-tools/swagger-ui/usage/configuration/): url, supportedSubmitMethods, persistAuthorization, validatorUrl, queryConfigEnabled.
- [OpenAPI 3.1 Server Object](https://spec.openapis.org/oas/v3.1.0.html#server-object): URL server tương đối.
- [Springdoc compatibility matrix](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot): phương án thay thế hỗ trợ Boot 4; chưa chọn cho triển khai này.

## 13. Change log

- 2026-11-10, người dùng: yêu cầu đề xuất planning tích hợp Swagger UI dựa trên auth-setup.md.
- 2026-11-10, assistant: khảo sát source/config/tests hiện tại và tài liệu chính thức; đề xuất 6 task, static contract + UI assets trong JAR, dev/test có prod override, Bearer thủ công. Chỉ tạo tài liệu kế hoạch; chưa thêm dependency hoặc sửa source.

- 2026-11-10, implementation: người dùng duyệt triển khai. Xác minh Maven Central org.webjars:swagger-ui:5.34.1 (không dependency transitives), bundle/css + bundle LICENSE notices; POM khai báo Apache-2.0. Bổ sung file license vào layout theo trách nhiệm license đã duyệt. Browser host CUA không kết nối; sẽ thử browser sandbox độc lập cho T5, không dùng DB ứng dụng.

- 2026-11-10, T1: baseline 39/39 PASS; Gradle configuration swaggerUiAssets chỉ resolve org.webjars:swagger-ui:5.34.1. License Apache-2.0 upstream và JS bundle notices được giữ. T2: phát hiện Gradle eachFile path ghi đè into khi inspect output; sửa path đích tường minh và sẽ chạy clean build để loại output cũ.

- 2026-11-10, T4 review: requestURI chứa servlet context nên rate-limit filter cũ bỏ qua IP/size checks dưới /foodrescue. Bổ sung sửa path lookup vào layout/T4 để đáp ứng yêu cầu quota vẫn áp dụng khi Try it out dưới prefix; không đổi mức quota hoặc nghiệp vụ auth. Thêm regression HTTP dưới context path.

- 2026-11-10, T3–T5: browser sandbox PASS (Chrome 156.0.8078.81): render đủ 15 operations, login/Authorize/me/refresh/logout/reuse/reload, 0 external requests, storage rỗng sau reload, prefix /foodrescue đúng. Context/size/quota tests và ca giữ sandbox riêng đều PASS; sandbox đã dừng và container đóng.

- 2026-11-10, completion: build PASS; 49 tests PASS, 1 opt-in sandbox test skipped trong build thường. Sandbox opt-in chạy riêng PASS; Chrome headless 156.0.8078.81: 15 operations, login/Authorize/me/refresh/logout/reuse/reload, context path đúng, zero external requests. JAR có đủ license/notices/assets/spec, không runtime WebJar.
