# Technical Plan: T4.5 — Chính sách tài chính theo phiên bản

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — Tạo phiên bản: `pg_advisory_xact_lock(hash 'financial_policy')` rồi đọc phiên bản mới nhất → kiểm `basedOnVersion`, hiệu lực → insert
  version+1. Unique `policy_version` là lớp bảo vệ thứ hai.
- P2 — `FinancialPolicyResolver.at(instant)` (JPQL `effectiveFrom <= :t order by effectiveFrom desc`), `current()`; T5.2 gọi khi tạo đơn.
- P3 — `MoneyRounding.applyRate(amount, rate)` = `amount × rate` HALF_UP scale 0 — định nghĩa duy nhất cho T6–T8.
- P4 — Entity giữ `@Immutable`; thêm `reason`, `createdBy` nullable.
- P5 — Hằng số: migration 11→12; Swagger 59→61 paths, 17→18 tags (`Financial policy`); `REFERENCE_TABLES` thêm `financial_policy`;
  `StoreTestFixtures.order()` dùng phiên bản seed thay vì tự chèn.

## 1. API (tag `Financial policy`)

| Method | Path | Quyền | 2xx |
|---|---|---|---|
| GET | `/api/v1/financial-policies/current` | đăng nhập | `FinancialPolicyView` |
| GET | `/api/v1/admin/financial-policies` | ADMIN | `FinancialPolicyView[]` (mới nhất trước, có `state`) |
| POST | `/api/v1/admin/financial-policies` | ADMIN | 201 `FinancialPolicyView` (400, 409) |

## 2. File layout

`resources/db/migration/V12__financial_policy_seed.sql`; `finance/entity/FinancialPolicy.kt` (M); `finance/repository/FinancialPolicyRepository.kt` (M);
`finance/dto/{FinancialPolicyRequest,FinancialPolicyView}.kt`; `finance/service/{FinancialPolicyService,FinancialPolicyResolver,MoneyRounding}.kt`;
`finance/controller/{FinancialPolicyController,AdminFinancialPolicyController}.kt`; openapi; test `FinancialPolicyTests.kt`, `MoneyRoundingTests.kt`.

## 3. Task list

- [x] V12 + code + OpenAPI. - [x] Test AC-01..07 (đồng thời AC-05). - [x] Build, smoke, báo cáo, commit.
