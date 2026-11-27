# KTX Hub Backend — Phase 4 API Contract Inventory & Verification Report

- **Authoritative Baseline:** SRS v3.4.1 FINAL | Logical ERD v3.6 FINAL | Conceptual ERD v3.5 FINAL
- **Repository Branch:** `feat/orca-phase3-final` (layered on Phase 3B `8538b70`)
- **Frontend Prototype Reference (Read-only):** `/home/chinh/Projects/dorm-management-prototype` (`feature/backend-api-integration` @ `d87a76d`)
- **Execution Date:** November 27, 2026
- **Stack:** Java 25 | Spring Boot `4.1.2` | PostgreSQL 16/17 + Flyway (`V1__clean_baseline.sql`) | Redis 8 | AWS SDK for Java v2 (`software.amazon.awssdk:s3` BOM `2.59.10`) | OpenAPI `/v3/api-docs` & Scalar `/scalar` (`/docs`)
- **Implementation Mode:** Phase 4 Module 4.5 Billing & Payments (`4-1`..`4-6`; zero schema/entity/`pom.xml` changes in `4-6`; Phase 2 deferred findings `F-01`, `F-04`, `F-06`, `F-07` untouched; stops before Module 4.4 `FR-14` Facility Expenses and Module 4.10 `FR-25`/`FR-27` Notifications)
- **Verification Status (Task `4-6`):**
  - **Compilation:** `./mvnw -DskipTests compile` → `BUILD SUCCESS` (`237` main Java source files)
  - **Full Offline Suite:** `./mvnw test` → **`586`** offline tests passed (`Tests run: 588, Failures: 0, Errors: 0, Skipped: 2` live opt-in smoke tests `OciS3LiveSmokeTest` and `VietQrLiveConfigSmokeTest`)
  - **Live OCI S3 Smoke Test:** `OCI_SMOKE_TEST_ENABLED=true ./mvnw test -Dtest=OciS3LiveSmokeTest` → **`1 / 1`** passed (`Failures: 0, Errors: 0, Skipped: 0`)
  - **Live VietQR Config Smoke Test:** `VIETQR_SMOKE_TEST_ENABLED=true ./mvnw test -Dtest=VietQrLiveConfigSmokeTest` → **`1 / 1`** passed (`Failures: 0, Errors: 0, Skipped: 0`; ZERO secrets logged or printed)
  - **Automated VietQR Verification:** **`PASS`**
  - **Real Banking App Scan Verification:** **`PENDING OWNER CONFIRMATION`** *(owner must personally scan generated VietQR payload on a real banking app without transferring money)*

---

## 1. Executive Summary & Verification Baseline

Phase 4 delivers **Module 4.5 — Billing, Monthly Invoice Batch Issuance, EMVCo/NAPAS VietQR Payload Generation, Payment Proof Upload/Review, and Atomic Payment Confirmation (`FR-15`, `FR-16`)** alongside post-checkout invoice visibility and proof settlement (`FR-26`).

### Sequential Delivery Ledger on `feat/orca-phase3-final`

| Step | Commit | Scope Delivered | Phase Status |
|---|---|---|---|
| **Phase 3B Final** | `8538b70` | Module 4.3 Onboarding, Contracts, Documents, Expiry Scheduler & Phase 3 E2E/OpenAPI/audit docs | `489` offline (`490` total incl. 1 skipped) + `1 / 1` OCI live smoke |
| **Task `4-1`** | `e7aeec5` | `InvoiceErrorCode`, `VietQrProperties` / `VietQrConfig`, `VietQrPayloadServiceImpl`, `DataIntegrityViolationClassifier` invoice/payment mappings, and billing repository queries | Committed baseline |
| **Task `4-2`** | `cf82f76` | `FR-15` idempotent `InvoiceBatchServiceImpl` (`REQUIRES_NEW` + `REPEATABLE_READ`) and day-25 `MonthlyInvoiceIssuanceScheduler` (`0 0 0 25 * *`, `Asia/Ho_Chi_Minh`) | Committed baseline |
| **Task `4-3`** | `6de948b` | `FR-16` payment proof upload (`.jpg`/`.jpeg`/`.png`/`.webp`, <= 5 MB, MIME + magic-byte validation), rejection with `afterCommit()` S3 cleanup, and authorized streaming | Committed baseline |
| **Task `4-4`** | `75cf008` | `FR-16` atomic cash (`CASH`) and VietQR transfer (`VIETQR_TRANSFER`) confirmation with `PESSIMISTIC_WRITE` (`T06`), `PT-{invoiceCode}` receipts, and `StaffProfile` enforcement | Committed baseline |
| **Task `4-5`** | `14dd873` | `AdminInvoiceController` (8 endpoints) + `TenantInvoiceController` (4 endpoints), dynamic overdue filtering, 4-bucket `InvoiceSummaryStatsResponse`, and RBAC/isolation security tests | Committed baseline (`14dd873`) |
| **Task `4-6`** | *HEAD* | Expanded `OpenApiContractVerificationTest` (Phase 2 + Phase 3 + all 12 Phase 4 endpoints; 8 streaming hygiene checks), `BillingPaymentE2ETest`, opt-in `VietQrLiveConfigSmokeTest`, Phase 4 audit inventory & Phase 2 doc sync | **`586`** offline (`588` total incl. 2 skipped) + **`1 / 1`** OCI smoke + **`1 / 1`** VietQR smoke |

### Key Phase 4 Architectural Invariants

1. **Zero Schema, Entity & Dependency Mutations in `4-6`:** `V1__clean_baseline.sql` (20 PostgreSQL tables), all JPA entity classes, and `pom.xml` remain untouched; `ddl-auto: validate` passes on real PostgreSQL Testcontainers.
2. **Per-Contract `REQUIRES_NEW` + `REPEATABLE_READ` Batch Isolation (`InvoiceBatchServiceImpl`):** Each candidate contract runs in its own `REQUIRES_NEW` sub-transaction at `ISOLATION_REPEATABLE_READ` so a concurrent `23505` unique race (`uq_invoices_contract_period` / `invoices_invoice_code_key`) rolls back only that contract, avoids PostgreSQL `25P02` aborted-transaction cascades, and leaves the overall batch idempotent (`eligibleCount`, `issuedCount`, `skippedExistingCount`).
3. **Day-25 Dorm Clock Scheduling (`MonthlyInvoiceIssuanceScheduler` & `InvoiceBatchServiceImpl`):**
   - Scheduled cron `0 0 0 25 * *` (`${app.scheduler.invoice-issuance-cron:0 0 0 25 * *}`) runs in `Asia/Ho_Chi_Minh` (`TimeConfig.DORM_ZONE`) and resolves `nextMonthFirstDay` from injected `Clock`.
   - Manual `POST /api/v1/admin/invoices/issue-batch` rejects non-day-01 periods (`400 INVOICE_400_INVALID_PERIOD`), periods beyond next month (`400 INVOICE_400_FUTURE_PERIOD`), and next-month periods invoked before day 25 (`400 INVOICE_400_ISSUANCE_WINDOW`).
   - Rent resolution reads locked rent strictly from covering `contract_renewals` (latest `sequenceNo DESC`) or `contracts.original_monthly_rent` — never current `room.basePrice`. `LIQUIDATED` and expired contracts are excluded.
4. **Atomic Payment Confirmation & Pessimistic Locking (`T06` in `InvoiceServiceImpl`):**
   - Both `confirmCashPayment` (`DEBT → PAID`, `CASH`) and `confirmTransferPayment` (`PENDING_CONFIRMATION` with non-blank `proofFileKey` → `PAID`, `VIETQR_TRANSFER`) acquire PostgreSQL `PESSIMISTIC_WRITE` (`findByIdForUpdate`) before mutating status and creating `PaymentTransaction` with `receiptCode = "PT-" + invoiceCode`.
   - Cross-method guards prevent `confirmCashPayment` on `PENDING_CONFIRMATION` (`409 INVOICE_409_NOT_DEBT`) and `confirmTransferPayment` on `DEBT` (`409 INVOICE_409_NOT_PENDING`). Double confirmation returns `409 INVOICE_409_ALREADY_PAID`.
   - Confirming staff is derived solely from `@AuthenticationPrincipal UserPrincipal` and requires a `MANAGER` user with a linked `StaffProfile` (`403 INVOICE_403_STAFF_PROFILE`).
5. **Private Proof Storage, Rollback/Post-Commit S3 Cleanup & Synthetic Streaming (`InvoiceServiceImpl`):**
   - Proofs are stored under `invoices/{invoiceId}/proof/{uuid}.{ext}` in private OCI S3 (`InMemoryPrivateObjectStorage` in offline test suites) and never exposed as raw keys or `/uploads/**` paths in API responses (`InvoiceProofResponse.contentUrl` points to `/api/v1/{admin|tenant}/invoices/{id}/proof/content`).
   - `submitProof` registers rollback cleanup (`afterCompletion` on non-commit) for newly uploaded keys and `afterCommit()` deletion for any superseded key; `rejectProof` (`PENDING_CONFIRMATION → DEBT`) clears `proofFileKey`/`proofSubmittedAt` and deletes the old proof object only in `afterCommit()`.
   - Streaming endpoints (`GET`/`HEAD`) return synthetic `Content-Disposition: inline; filename="{invoiceCode}-proof.{ext}"` via `StreamingResponseUtils.okInline` and `ManagedStreamResource` safe close.
6. **Fail-Closed EMVCo / NAPAS VietQR (`VietQrPayloadServiceImpl` & `VietQrProperties`):**
   - Dynamic VietQR payloads (`00=01`, `01=12`, `38` with NAPAS AID `A000000727` + BIN + account + `QRIBFTTA`, `53=704`, `54=amount`, `58=VN`, `62-08=transferContent`, `63=CRC-16/CCITT-FALSE`) are generated **only** when `status == DEBT` and `app.vietqr` config + positive integer VND amount are valid; otherwise `Optional.empty()` (`vietQr: null`).
   - `VietQrProperties.toString()` explicitly omits `accountNumber` and `accountName` (reporting only `bankBin`, `transferPrefix`, and `configured`).
7. **Dynamic Overdue Computation (`FR-15`):** Overdue status is computed dynamically against `today` in `Asia/Ho_Chi_Minh`: `overdue = (status == DEBT && dueOn != null && dueOn.isBefore(today))`; `PENDING_CONFIRMATION` and `PAID` always return `overdue = false`, `overdueDays = 0`.

---

## 2. Complete Phase 4 API Contract Inventory

All Phase 4 JSON endpoints wrap responses in standard `ResponseFactory` envelopes (`SingleResponse<T>`, `SingleResponse<PageResponse<T>>`, `ErrorResponse`, `DetailedErrorResponse`), while binary proof streaming endpoints return `ResponseEntity<Resource>`.

### 2.1. Admin Billing & Payments (`AdminInvoiceController`)

All routes under `/api/v1/admin/invoices` require `ROLE_MANAGER` (`@PreAuthorize("hasRole('MANAGER')")`).

| HTTP Method | Route | Request DTO / Query / Path Params | Success Status & Envelope Payload | Error Codes |
|---|---|---|---|---|
| `GET` | `/api/v1/admin/invoices` | Query:<br>• `Pageable` (default sort: `billingPeriod DESC, issuedAt DESC`)<br>• `search` (optional `String`: matches `invoiceCode`, tenant `fullName`, `idCardNumber`, `email`, `roomCodeSnapshot`)<br>• `status` (optional `InvoiceStatus`: `DEBT`, `PENDING_CONFIRMATION`, `PAID`)<br>• `billingPeriod` (optional ISO `LocalDate`: must be day `01`)<br>• `overdueOnly` (optional `Boolean`: filters `status == DEBT && dueOn < today`) | `200 OK` → `SingleResponse<PageResponse<InvoiceSummaryResponse>>` (`id`, `invoiceCode`, `contractId`, `contractCode`, `tenantId`, `tenantCode`, `tenantName`, `tenantPhone`, `tenantIdCardNumber`, `tenantEmail`, `roomNumber`, `bedCode`, `billingPeriod`, `amount`, `dueOn`, `status`, `overdue`, `overdueDays`, `proofAvailable`, `proofSubmittedAt`, `payment` [`PaymentTransactionResponse`], `issuedAt`) | `INVOICE_400_INVALID_PERIOD` (400)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` | `/api/v1/admin/invoices/summary` | Query:<br>• `billingPeriod` (optional ISO `LocalDate`: must be day `01`; omitted aggregates across all periods) | `200 OK` → `SingleResponse<InvoiceSummaryStatsResponse>` (`billingPeriod`, `paidAmount`, `paidCount`, `unpaidInTermAmount`, `unpaidInTermCount`, `overdueAmount`, `overdueCount`, `pendingConfirmationAmount`, `pendingConfirmationCount`, `totalExpectedAmount`, `totalInvoiceCount`) | `INVOICE_400_INVALID_PERIOD` (400)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` | `/api/v1/admin/invoices/{id}` | Path: `id` (`UUID`) | `200 OK` → `SingleResponse<InvoiceDetailResponse>` (`InvoiceSummaryResponse` fields plus `proof` [`InvoiceProofResponse(available, submittedAt, contentUrl="/api/v1/admin/invoices/{id}/proof/content")`] and `vietQr` [`VietQrInfoResponse` when `DEBT` and configured, else `null`]) | `INVOICE_404` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `POST` | `/api/v1/admin/invoices/issue-batch` | Optional JSON `IssueInvoiceBatchRequest`:<br>• `billingPeriod` (optional `LocalDate`: defaults to next month day `01` if `today.dayOfMonth >= 25`, else current month day `01`) | `200 OK` → `SingleResponse<InvoiceBatchIssuanceResponse>` (`billingPeriod`, `eligibleContractCount`, `issuedCount`, `skippedExistingCount`, `issuedInvoices: List<InvoiceSummaryResponse>`) | `INVOICE_400_INVALID_PERIOD` (400)<br>`INVOICE_400_FUTURE_PERIOD` (400)<br>`INVOICE_400_ISSUANCE_WINDOW` (400)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` / `HEAD` | `/api/v1/admin/invoices/{id}/proof/content` | Path: `id` (`UUID`) *(no `httpMethod` OpenAPI parameter exposed)* | `200 OK` → Binary `Resource` stream (`Content-Type`: `image/jpeg` \| `image/png` \| `image/webp`, `Content-Length`, `Content-Disposition: inline; filename="{invoiceCode}-proof.{ext}"`; `HEAD` closes `ManagedStreamResource` unconsumed) | `INVOICE_404` (404)<br>`INVOICE_404_PROOF` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `PATCH` | `/api/v1/admin/invoices/{id}/confirm-cash` | Path: `id` (`UUID`)<br>Manager identity resolved from JWT `@AuthenticationPrincipal` (`StaffProfile` required) | `200 OK` → `SingleResponse<InvoiceDetailResponse>` (`status: PAID`, `vietQr: null`, `payment` with `paymentMethod: CASH`, `receiptCode: "PT-" + invoiceCode`, `confirmedByStaffId`, `confirmedByStaffName`, `confirmedAt`) | `INVOICE_403_STAFF_PROFILE` (403)<br>`INVOICE_404` (404)<br>`INVOICE_409_ALREADY_PAID` (409)<br>`INVOICE_409_NOT_DEBT` (409)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `PATCH` | `/api/v1/admin/invoices/{id}/confirm-transfer` | Path: `id` (`UUID`)<br>Manager identity resolved from JWT `@AuthenticationPrincipal` (`StaffProfile` required) | `200 OK` → `SingleResponse<InvoiceDetailResponse>` (`status: PAID`, `vietQr: null`, `proof` preserved for audit, `payment` with `paymentMethod: VIETQR_TRANSFER`, `receiptCode: "PT-" + invoiceCode`) | `INVOICE_403_STAFF_PROFILE` (403)<br>`INVOICE_404` (404)<br>`INVOICE_409_ALREADY_PAID` (409)<br>`INVOICE_409_NOT_PENDING` (409)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `PATCH` | `/api/v1/admin/invoices/{id}/reject-proof` | Path: `id` (`UUID`) | `200 OK` → `SingleResponse<InvoiceDetailResponse>` (`status: DEBT`, `proofAvailable: false`, `proof: null`, `proofSubmittedAt: null`, `vietQr` re-enabled, old S3 proof deleted `afterCommit()`) | `INVOICE_404` (404)<br>`INVOICE_409_ALREADY_PAID` (409)<br>`INVOICE_409_NOT_PENDING` (409)<br>`WEB_401` (401)<br>`WEB_403` (403) |

---

### 2.2. Tenant Billing, VietQR & Proof Submission (`TenantInvoiceController`)

All routes under `/api/v1/tenant/invoices` require `ROLE_TENANT` (`@PreAuthorize("hasRole('TENANT')")`) and support both `ACTIVE` and `CHECKED_OUT` owning tenants (`FR-26`). Cross-tenant access fails closed with `404 INVOICE_404`.

| HTTP Method | Route | Request Params / Multipart | Success Status & Envelope Payload | Error Codes |
|---|---|---|---|---|
| `GET` | `/api/v1/tenant/invoices` | Query: `status` (optional `InvoiceStatus`: `DEBT`, `PENDING_CONFIRMATION`, `PAID`) | `200 OK` → `SingleResponse<List<InvoiceDetailResponse>>` ordered by `billingPeriod DESC, issuedAt DESC` (`proof.contentUrl = "/api/v1/tenant/invoices/{id}/proof/content"`; `vietQr` populated only when `DEBT`) | `TENANT_404` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` | `/api/v1/tenant/invoices/{id}` | Path: `id` (`UUID`) | `200 OK` → `SingleResponse<InvoiceDetailResponse>` for owning tenant (`vietQr` populated only when `DEBT`; cross-tenant returns `404 INVOICE_404`) | `TENANT_404` (404)<br>`INVOICE_404` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `POST` | `/api/v1/tenant/invoices/{id}/proof` | Path: `id` (`UUID`)<br>Multipart `form-data`: `file` (`MultipartFile`: `.jpg`, `.jpeg`, `.png`, `.webp`; `> 0` bytes and `<= 5 MB`; declared MIME + magic bytes validated) | `200 OK` → `SingleResponse<InvoiceDetailResponse>` (`status: PENDING_CONFIRMATION`, `vietQr: null`, `proofAvailable: true`, `proof.contentUrl = "/api/v1/tenant/invoices/{id}/proof/content"`) | `INVOICE_400_PROOF_REQUIRED` (400)<br>`INVOICE_400_PROOF_SIZE` (400)<br>`INVOICE_400_PROOF_FORMAT` (400)<br>`INVOICE_404` (404)<br>`INVOICE_409_ALREADY_PAID` (409)<br>`INVOICE_409_NOT_DEBT` (409)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` / `HEAD` | `/api/v1/tenant/invoices/{id}/proof/content` | Path: `id` (`UUID`) *(no `httpMethod` OpenAPI parameter exposed; owning `ACTIVE` or `CHECKED_OUT` tenant)* | `200 OK` → Binary `Resource` stream (`Content-Type`, `Content-Length`, `Content-Disposition: inline; filename="{invoiceCode}-proof.{ext}"`; cross-tenant returns `404 INVOICE_404`) | `INVOICE_404` (404)<br>`INVOICE_404_PROOF` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |

---

## 3. Domain Error Code Matrix (`InvoiceErrorCode`) & Constraint Mappings

### 3.1. Complete `InvoiceErrorCode` Enum (13 Entries)

Verified directly from `src/main/java/com/uit/dormmanagement/exceptions/error/InvoiceErrorCode.java`:

| # | Enum Constant | `errorCode` | HTTP Status | Backend Message | Trigger Condition |
|---|---|---|---|---|---|
| 1 | `INVOICE_NOT_FOUND` | `INVOICE_404` | `404 NOT_FOUND` | Không tìm thấy hóa đơn tương ứng | Missing `invoiceId`, or tenant reads/submits/streams an invoice owned by another tenant (`404` isolation) |
| 2 | `INVOICE_PROOF_NOT_FOUND` | `INVOICE_404_PROOF` | `404 NOT_FOUND` | Không tìm thấy ảnh minh chứng thanh toán của hóa đơn | Invoice has blank/null `proofFileKey`, or proof object does not exist in private storage |
| 3 | `INVALID_BILLING_PERIOD` | `INVOICE_400_INVALID_PERIOD` | `400 BAD_REQUEST` | Kỳ hóa đơn không hợp lệ (phải là ngày 01 của tháng) | `billingPeriod` query/body date has `dayOfMonth != 1` |
| 4 | `FUTURE_BILLING_PERIOD_NOT_ALLOWED` | `INVOICE_400_FUTURE_PERIOD` | `400 BAD_REQUEST` | Không thể phát hành hóa đơn cho kỳ tương lai xa | `issue-batch` requested for a `billingPeriod` strictly after next month's day `01` |
| 5 | `ISSUANCE_WINDOW_NOT_OPEN` | `INVOICE_400_ISSUANCE_WINDOW` | `400 BAD_REQUEST` | Kỳ hóa đơn tháng kế tiếp chỉ được phát hành từ ngày 25 hằng tháng | `issue-batch` requested for next month's day `01` when `today.dayOfMonth < 25` (`Asia/Ho_Chi_Minh`) |
| 6 | `PROOF_FILE_REQUIRED` | `INVOICE_400_PROOF_REQUIRED` | `400 BAD_REQUEST` | Vui lòng đính kèm ảnh minh chứng chuyển khoản | Missing, null, empty, or zero-byte `file` part on proof upload |
| 7 | `PROOF_FILE_SIZE_EXCEEDED` | `INVOICE_400_PROOF_SIZE` | `400 BAD_REQUEST` | Kích thước ảnh minh chứng vượt quá giới hạn tối đa 5 MB | Uploaded proof exceeds `5 * 1024 * 1024` bytes |
| 8 | `UNSUPPORTED_PROOF_FORMAT` | `INVOICE_400_PROOF_FORMAT` | `400 BAD_REQUEST` | Ảnh minh chứng phải có định dạng JPG, PNG hoặc WEBP hợp lệ | Missing filename/extension, disallowed extension, mismatched declared MIME type, or magic-byte mismatch |
| 9 | `MANAGER_STAFF_PROFILE_REQUIRED` | `INVOICE_403_STAFF_PROFILE` | `403 FORBIDDEN` | Tài khoản quản lý chưa được liên kết hồ sơ nhân sự để xác nhận thanh toán | Confirming user has null ID, no `StaffProfile`, or staff user role `!= MANAGER` |
| 10 | `INVOICE_ALREADY_PAID` | `INVOICE_409_ALREADY_PAID` | `409 CONFLICT` | Hóa đơn đã được xác nhận thanh toán trước đó | Proof upload, proof rejection, or payment confirmation attempted on an already `PAID` invoice or existing `PaymentTransaction` |
| 11 | `INVOICE_NOT_DEBT` | `INVOICE_409_NOT_DEBT` | `409 CONFLICT` | Thao tác chỉ áp dụng cho hóa đơn đang ở trạng thái Nợ | `submitProof` or `confirmCashPayment` attempted on an invoice whose status `!= DEBT` (e.g., `PENDING_CONFIRMATION`) |
| 12 | `INVOICE_NOT_PENDING_CONFIRMATION` | `INVOICE_409_NOT_PENDING` | `409 CONFLICT` | Thao tác chỉ áp dụng cho hóa đơn đang ở trạng thái Chờ xác nhận | `rejectProof` or `confirmTransferPayment` attempted when status `!= PENDING_CONFIRMATION` (or `confirmTransferPayment` when `proofFileKey` is blank) |
| 13 | `DUPLICATE_INVOICE_PERIOD` | `INVOICE_409_DUPLICATE_PERIOD` | `409 CONFLICT` | Hóa đơn cho hợp đồng và kỳ này đã tồn tại | Concurrent duplicate invoice insertion hitting `uq_invoices_contract_period` or `invoices_invoice_code_key` |

**Cross-Domain Error Codes Surfaced on Phase 4 Endpoints:**
- `TenantErrorCode`: `TENANT_404` (404 — missing `Tenant` profile on tenant list/detail reads).
- `WebErrorCode`: `WEB_400_VAL` (400 — malformed UUID/date/enum request params), `WEB_401` (401 — unauthenticated), `WEB_403` (403 — role guard).
- `SystemErrorCode`: `SYS_500` (500 — multipart IO error), `SYS_502` / `EXTERNAL_SERVICE_ERROR` (OCI S3 storage failure).

### 3.2. PostgreSQL Unique Constraint Mappings (`DataIntegrityViolationClassifier`)

Verified from `src/main/java/com/uit/dormmanagement/exceptions/DataIntegrityViolationClassifier.java` (lines 53–56) and `DataIntegrityClassificationPostgresTest`:

| PostgreSQL Constraint / Index | Table | Mapped `AppErrorCode` | HTTP Status |
|---|---|---|---|
| `uq_invoices_contract_period` | `invoices (contract_id, billing_period)` | `InvoiceErrorCode.DUPLICATE_INVOICE_PERIOD` (`INVOICE_409_DUPLICATE_PERIOD`) | `409 CONFLICT` |
| `invoices_invoice_code_key` | `invoices (invoice_code)` | `InvoiceErrorCode.DUPLICATE_INVOICE_PERIOD` (`INVOICE_409_DUPLICATE_PERIOD`) | `409 CONFLICT` |
| `payment_transactions_invoice_id_key` | `payment_transactions (invoice_id)` | `InvoiceErrorCode.INVOICE_ALREADY_PAID` (`INVOICE_409_ALREADY_PAID`) | `409 CONFLICT` |
| `payment_transactions_receipt_code_key` | `payment_transactions (receipt_code)` | `InvoiceErrorCode.INVOICE_ALREADY_PAID` (`INVOICE_409_ALREADY_PAID`) | `409 CONFLICT` |

---

## 4. Frontend Prototype Compatibility & Discrepancy Disposition Matrix

Inspected read-only at `/home/chinh/Projects/dorm-management-prototype` on branch `feature/backend-api-integration` (`d87a76d`). **Note:** The prototype currently runs against local mock state (`src/services/mock/mockDormService.ts`, `mockStore.ts`) while `src/services/api/apiDormService.ts` still contains placeholder throws for billing endpoints (`getInvoices` targeting `/invoices` at L94–L97; `issueInvoices`, `submitInvoiceProof`, `rejectInvoiceProof`, `confirmInvoiceProof`, `confirmPayment` throwing `"API ... chưa được thống nhất."` at L116–L121). The Backend enforces **SRS v3.4.1 FINAL** strictly; the Frontend adapter layer must reconcile the 8 discrepancies below when integrating:

| ID | Discrepancy Area | Prototype Behavior (`d87a76d`) | Backend SRS v3.4.1 Contract | Disposition & Required FE Adapter Action |
|---|---|---|---|---|
| **D-01** | **Routes, RBAC Split & Envelopes** | `apiDormService.getInvoices()` calls `GET /invoices` and expects flat `Invoice[]`; mutation methods throw unimplemented (`apiDormService.ts` L94–L97, L116–L121) | Role-scoped endpoints under `/api/v1/admin/invoices/**` (`MANAGER`, paginated `PageResponse<InvoiceSummaryResponse>`) and `/api/v1/tenant/invoices/**` (`TENANT`, `List<InvoiceDetailResponse>`) wrapped in `SingleResponse<T>` | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Route Admin and Tenant calls separately, pass pagination/filter params, and unwrap `res.data` (`res.data.items` for Admin pages). |
| **D-02** | **Invoice Primary UUID vs Display Code** | `Invoice.id` (`types/invoice.ts` L18, `InvoicesPage.tsx` L235–L241) serves simultaneously as React key, route/mutation ID, and displayed `Mã HĐ` | Backend separates immutable UUID `id` (used in `/invoices/{id}` routes) from human-readable `invoiceCode` (`INV-YYYYMM-{contractCode}`) | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Use `id` for API path params/React keys and render `invoiceCode` (`PT-...` from `payment.receiptCode` when paid) in UI tables/drawers. |
| **D-03** | **Status & Payment Method Enums** | Prototype uses Vietnamese strings `"Nợ" \| "Chờ xác nhận" \| "Đã thu"` (Admin), `"Chưa thanh toán" \| "Chờ xác nhận" \| "Đã thanh toán"` (Tenant), and `"Tiền mặt" \| "Trực tuyến"` (`types/invoice.ts` L3, L9, L39; `TenantPortalPage.tsx` L94–L99) | Canonical enums: `InvoiceStatus` (`DEBT`, `PENDING_CONFIRMATION`, `PAID`) and `PaymentMethod` (`CASH`, `VIETQR_TRANSFER`) | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Map canonical API enums to Vietnamese display badges/labels in adapter mappers and query params. |
| **D-04** | **Admin Summary KPI Cards & `PENDING_CONFIRMATION` Bucket** | `InvoicesPage.tsx` (L174–L204) computes 3 client-side KPI cards (`Đã thu`, `Nợ trong hạn`, `Nợ quá hạn`) over in-memory rows and omits a `Chờ xác nhận` KPI bucket | `GET /api/v1/admin/invoices/summary` computes all **4** authoritative buckets server-side across the full dataset (`paid*`, `unpaidInTerm*`, `overdue*`, `pendingConfirmation*`, `totalExpectedAmount`, `totalInvoiceCount`) | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Replace client-side array reductions (which break under server pagination) with `GET /api/v1/admin/invoices/summary`, adding or surfacing the `pendingConfirmation` bucket. |
| **D-05** | **Payment Proof Upload, Format Support & Private Streaming** | `PaymentProofPanel.tsx` (L93–L106) restricts picker to `JPG`/`PNG`, `TenantPortalPage.tsx` (L123–L142) encodes files to base64 `FileReader` `dataUrl`, and `InvoicesPage.tsx` (L346–L368) renders `<img src={selected.proof.dataUrl}>` and raw `fileName` | `POST /api/v1/tenant/invoices/{id}/proof` accepts `multipart/form-data` (`file`) for `JPG`/`JPEG`/`PNG`/`WEBP` (`<= 5 MB`, magic-byte validated), stores privately in OCI S3, omits raw `fileName`/`storageFileKey`, and serves bytes via Bearer-authenticated `proof.contentUrl` (`/content`, synthetic `{invoiceCode}-proof.{ext}`) | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Post `FormData("file")`, allow `image/webp` in picker (`accept="image/jpeg,image/png,image/webp"`), and fetch `proof.contentUrl` with `Authorization: Bearer <token>` into a `Blob` URL for Admin drawer preview/download. |
| **D-06** | **VietQR Payload Generation & Safety** | `PaymentProofPanel.tsx` (L6–L35, L60–L90) & `QrCode.tsx` generate client-side dummy QR payloads (`BIN 000000`, account `0000000000`, memo `DEMO {id}`) | Backend `VietQrPayloadServiceImpl` builds authoritative EMVCo/NAPAS VietQR (`vietQr` containing `bankBin`, `bankName`, `accountNumber`, `accountName`, `amount`, `transferContent`, `qrPayload`) strictly on `DEBT` invoices (`null` when `PENDING_CONFIRMATION`/`PAID` or unconfigured) | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Remove client-side dummy generator in API mode; render `<QrCode value={invoice.vietQr.qrPayload} />` and display `invoice.vietQr` bank/memo fields when `vietQr != null`, falling back cleanly when `null`. |
| **D-07** | **Manager Attribution on Cash & Transfer Confirm** | `InvoicesPage.tsx` (L72–L73, L394–L395) requires client-side `managerStaffId` and passes it into `onConfirmPayment` / `onConfirmInvoiceProof` | `PATCH /confirm-cash` and `PATCH /confirm-transfer` take empty bodies and derive manager `StaffProfile` securely from JWT `@AuthenticationPrincipal` (`403 INVOICE_403_STAFF_PROFILE` if missing) | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Do not pass `managerStaffId` from frontend state; send authenticated `PATCH` requests and display returned `payment.confirmedByStaffName`. |
| **D-08** | **All-Inclusive Rent Model & Day-25 Batch Trigger** | `types/invoice.ts` (L34–L46) still carries legacy `electric`/`water`/`service` sub-fields on `TenantInvoice` (hardcoded zero/included in `TenantInvoicesPage.tsx` L173–L200), and `InvoicesPage.tsx` (L147–L170) hides the batch button behind `env.USE_MOCK` | `BR-03` all-inclusive billing: single locked `amount` from contract/renewal; `POST /api/v1/admin/invoices/issue-batch` available to `MANAGER` with optional `billingPeriod` and day-25 window enforcement (`400 INVOICE_400_ISSUANCE_WINDOW`) | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Drop unused utility sub-fields from API tenant invoice DTOs and enable the Admin batch button against `POST /api/v1/admin/invoices/issue-batch` (surfacing backend day-25/future-period errors via toast). |

---

## 5. Verification Suites, VietQR Status & Preserved Deferred Risk Ledger

### 5.1. Phase 4 & Billing Verification Suites (`Task 4-6`)

Executed after final Task `4-6` edits on `feat/orca-phase3-final`:
- **Full Offline Suite (`./mvnw test`):** `Tests run: 588, Failures: 0, Errors: 0, Skipped: 2` (**`586`** offline tests passed; the 2 skipped tests are opt-in live smoke tests `OciS3LiveSmokeTest` and `VietQrLiveConfigSmokeTest`).

| Test Suite | Count | Scope Verified |
|---|---|---|
| `OpenApiContractVerificationTest` | `1` | Live `GET /v3/api-docs` verification across all Phase 2, Phase 3, and all **12 Phase 4** Billing & Payments endpoints (`8` Admin + `4` Tenant), absence of `PUT /api/v1/admin/vehicles/{id}`, and absence of `httpMethod` OpenAPI parameter across all **8** streaming endpoints |
| `BillingPaymentE2ETest` | `1` | Full PostgreSQL + Redis Testcontainers + `InMemoryPrivateObjectStorage` E2E billing lifecycle (no class `@Transactional`): Day-01 onboarding → dynamic overdue detection (`2` days) & cash confirmation (`PT-...`) → day-25 scheduler batch & idempotent retry → VietQR EMVCo CRC-16, PNG proof upload, blocked cash (`409 INVOICE_409_NOT_DEBT`), proof rejection & `afterCommit()` S3 cleanup → JPEG resubmission, Admin streaming, transfer confirmation (`VIETQR_TRANSFER`) & locked-rent renewal (`1.8M` isolated from `2.0M` room price increase) → post-checkout debt preservation, `LIQUIDATED` exclusion from batch, returning-tenant `409 CONTRACT_409_UNSETTLED_DEBT` across `DEBT` & `PENDING_CONFIRMATION`, WEBP proof upload/streaming, transfer confirmation, clean Day-01 re-onboarding at `2.0M`, and 4-bucket summary reconciliation (`6.8M` across `4` invoices) |
| `InvoiceSecurityIntegrationTest` | `4` | RBAC `401`/`403` matrix across all 12 endpoints (+ 2 `HEAD`), `ACTIVE` & `CHECKED_OUT` tenant isolation (`404 INVOICE_404`), `GET`/`HEAD` proof streaming, EMVCo CRC-16 hygiene, raw S3 key & `/uploads/` non-leakage, dynamic overdue & 4-bucket summary endpoints |
| `InvoiceControllerTest` | `11` | MockMvc routing, pageable/filter binding, `issue-batch`, `GET`/`HEAD` `ManagedStreamResource` closing, and `400`/`403`/`404`/`409` exception mapping |
| `InvoiceServiceImplTest` | `24` | Unit verification of admin listing/summary/detail, tenant listing/detail isolation, proof validation (size, MIME, JPG/PNG/WEBP magic bytes), rollback/`afterCommit()` S3 cleanup, cash/transfer confirmation guards, and `StaffProfile` enforcement |
| `InvoicePaymentPostgresTest` | `4` | Real PostgreSQL `PESSIMISTIC_WRITE` (`FOR UPDATE`) concurrent double-confirmation race serialization (1 winner, 1 `409 INVOICE_409_ALREADY_PAID`), cross-method guards, proof audit retention, and staff guard |
| `InvoiceProofPostgresTest` | `3` | Real PostgreSQL `ck_invoices_proof_consistency` constraint enforcement, checked-out tenant JPG upload/streaming, rollback S3 cleanup, `afterCommit()` S3 deletion on reject, and WEBP resubmission |
| `InvoiceBatchServiceImplTest` & `InvoiceBatchPostgresTest` | `7` + `3` | Locked rent resolution across multi-renewal/original/liquidated/expired contracts, day-25 window checks, real PostgreSQL `REQUIRES_NEW` `23505` race isolation (zero `25P02`), dual concurrent batch safety, and day-25 cron registration |
| `MonthlyInvoiceIssuanceSchedulerTest` | `3` | `Asia/Ho_Chi_Minh` midnight boundary (`17:00Z`), cron expression `0 0 0 25 * *`, read-only outer method, and PII-free logging |
| `InvoiceIssuanceServiceImplTest` & `InvoiceIssuancePostgresTest` | `10` + `3` | `hasUnsettledInvoices`, idempotent `issueMonthlyInvoiceIfAbsent`, day-25+ `T05` renewal catch-up boundaries, and real PostgreSQL `uq_invoices_contract_period` enforcement |
| `InvoiceRepositoryPostgresTest` | `6` | Real PostgreSQL JPQL filter queries (`search`, `status`, `billingPeriod`, `overdueOnly`), `findAllForSummary`, and `findByIdForUpdate` |
| `VietQrPayloadServiceImplTest` & `VietQrConfigTest` | `24` + `2` | EMVCo/NAPAS TLV grammar, CRC-16/CCITT-FALSE `123456789 -> 29B1` vector, 14 NAPAS BIN mappings + fallback, Vietnamese diacritic sanitization, TLV truncation, fail-closed guards, and `VietQrProperties.toString()` + log redaction |
| `DomainErrorCodeTest`, `DataIntegrityClassificationPostgresTest` & `AppExceptionHandlerDataIntegrityTest` | `5` + `1` + `26` | Complete error code prefix/status checks (`InvoiceErrorCode` 13 entries) and real PostgreSQL SQLSTATE `23505` vs `23514`/`23502`/`23503` classification |
| `OciS3LiveSmokeTest` | `1` *(live)* | `OCI_SMOKE_TEST_ENABLED=true ./mvnw test -Dtest=OciS3LiveSmokeTest` → **`1 / 1`** passed (live OCI private bucket round-trip `putObject` → `getObject` → `deleteObject`) |
| `VietQrLiveConfigSmokeTest` | `1` *(live)* | `VIETQR_SMOKE_TEST_ENABLED=true ./mvnw test -Dtest=VietQrLiveConfigSmokeTest` → **`1 / 1`** passed (live `.env` VietQR config EMVCo TLV & CRC-16 check + redaction check, ZERO secrets printed) |

### 5.2. Explicit VietQR Verification Status

- **Automated Verification (`VietQrPayloadServiceImplTest`, `VietQrConfigTest`, `InvoiceSecurityIntegrationTest`, `BillingPaymentE2ETest`, `VietQrLiveConfigSmokeTest`):** **`PASS`**
  - Strictly verifies EMVCo/NAPAS TLV framing (`00=01`, `01=12`, `38` with `A000000727` + `QRIBFTTA`, `53=704`, `54=amount`, `58=VN`, `62-08=transferContent`, `63=CRC-16/CCITT-FALSE`), live `.env` binding, `VietQrProperties.toString()` secret redaction, and zero raw account leakage in logs or tests.
- **Real Banking App Scan Verification:** **`PENDING OWNER CONFIRMATION`**
  - No QR PNG files or real `.env` bank values were committed or printed, and no real bank transfer was executed. The repository owner must personally verify scan readability on a real banking app in their local environment (without completing a transfer).

### 5.3. Preserved Phase 2 Deferred Findings Ledger (Untouched)

The following Phase 2 review findings remain explicitly **`DEFERRED / ACCEPTED RISK`** per owner decision and were **NOT** modified in Phase 3A, Phase 3B, or Phase 4 (`4-1`..`4-6`):
- **`F-01` (`DEFERRED / ACCEPTED RISK`):** Old-email password recovery challenge race vs. Manager email correction (`PasswordRecoveryServiceImpl`).
- **`F-04` (`DEFERRED / ACCEPTED RISK`):** Non-atomic Redis login failure counter/TTL/lock (`LoginAttemptServiceImpl`).
- **`F-06` (`DEFERRED / ACCEPTED RISK`):** Non-atomic missing Redis session generation initialization (`UserTokenInvalidationServiceImpl`).
- **`F-07` (`DEFERRED / ACCEPTED RISK`):** Undelivered OTP challenge retained in Redis after email delivery failure (`PasswordRecoveryServiceImpl`).

### 5.4. Branch & Delivery Status Notice

All Phase 2 Hardening, Phase 3A, Phase 3B, and Phase 4 (`4-1`..`4-6`) work is committed **locally only** on branch `feat/orca-phase3-final`. No merge, push, or deployment has been performed. Scope stops cleanly at Module 4.5 (`FR-15`, `FR-16`); Module 4.4 (`FR-14` Facility Expenses) and Module 4.10 (`FR-25`/`FR-27` Notifications) remain unimplemented pending owner direction.
