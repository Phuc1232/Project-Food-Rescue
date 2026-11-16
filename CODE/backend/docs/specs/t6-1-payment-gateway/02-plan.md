# Technical Plan: T6.1 — Port thanh toán và adapter payOS

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — `java.net.http.HttpClient` (không thêm thư viện), Jackson 3 `ObjectMapper` có sẵn.
- P2 — `PayOsSignature` thuần (không Spring) để test vector; so sánh `MessageDigest.isEqual`.
- P3 — Bean chọn bằng `@ConditionalOnProperty(payment.gateway)`; `PayOsProperties` kiểm đủ khóa khi khởi tạo adapter thật.
- P4 — Test adapter dùng `com.sun.net.httpserver.HttpServer` trên cổng ngẫu nhiên (không gọi payOS thật, CLAUDE.md §6).
- P5 — Không migration, không endpoint.

## 1. File layout

`payment/gateway/{PaymentGateway,PaymentGatewayException,PaymentLinkRequest,PaymentLink,PaymentLinkInfo,PaymentLinkStatus,WebhookPayment,
InvalidSignatureException,PayOsSignature,PayOsProperties,PayOsPaymentGateway,FakePaymentGateway,PaymentGatewayConfiguration}.kt`;
`application.yml`, `application-dev.yml`, `application-prod.yml`, `.env.example`; test `PayOsSignatureTests.kt`, `PayOsPaymentGatewayTests.kt`, `FakePaymentGatewayTests.kt`.

## 2. Task list

- [x] Port + chữ ký + adapter + fake + cấu hình. - [x] Test AC-01..05. - [x] Build, báo cáo, commit.
