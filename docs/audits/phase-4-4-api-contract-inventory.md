# KTX Hub Backend — Module 4.4 (FR-14) API Contract Inventory & Verification Report

- **Authoritative Baseline:** SRS v3.4.1 FINAL | Logical ERD v3.6 FINAL | Conceptual ERD v3.5 FINAL
- **Repository Branch:** `feat/orca-phase3-final` (layered on Phase 4 `58ac071`)
- **Frontend Prototype Reference (Read-only):** `/home/chinh/Projects/dorm-management-prototype` (`feature/backend-api-integration` @ `d87a76d`)
- **Execution Date:** November 27, 2026
- **Stack:** Java 25 | Spring Boot `4.1.2` | PostgreSQL 16/17 + Flyway (`V1__clean_baseline.sql`) | Redis 8 | AWS SDK for Java v2 (`software.amazon.awssdk:s3` BOM `2.59.10`) | Apache POI `5.4.0` (`poi-ooxml`) | OpenPDF `2.0.3` + `NotoSans-Regular.ttf` | OpenAPI `/v3/api-docs` & Scalar `/scalar` (`/docs`)
- **Implementation Mode:** Phase 4 Module 4.4 Facility Operating Expenses & Estimated Net Profit Reports (`4.4-1`..`4.4-4`; zero `src/main/**`, `V1__clean_baseline.sql`, JPA entity, or `pom.xml` changes in `4.4-4`; Phase 2 deferred findings `F-01`, `F-04`, `F-06`, `F-07` untouched; Phase 5 Module 4.10 Notifications not started)
- **Verification Status (Task `4.4-4`):**
  - **Compilation:** `./mvnw -DskipTests compile` → `BUILD SUCCESS` (`256` main Java source files)
  - **Scoped Spotless Check:** `./mvnw spotless:check -DspotlessFiles=".*(OpenApiContractVerificationTest|FacilityExpenseFinancialE2ETest)\.java"` → `BUILD SUCCESS`
  - **Full Offline Suite:** `./mvnw test` → **`626`** offline tests passed (`Tests run: 628, Failures: 0, Errors: 0, Skipped: 2` opt-in live smoke tests `OciS3LiveSmokeTest` and `VietQrLiveConfigSmokeTest`)
  - **Live OCI S3 Smoke Test:** `OCI_SMOKE_TEST_ENABLED=true ./mvnw test -Dtest=OciS3LiveSmokeTest` → **`1 / 1`** passed (`Failures: 0, Errors: 0, Skipped: 0`)
  - **Live VietQR Config Smoke Test:** `VIETQR_SMOKE_TEST_ENABLED=true ./mvnw test -Dtest=VietQrLiveConfigSmokeTest` → **`1 / 1`** passed (`Failures: 0, Errors: 0, Skipped: 0`; ZERO secrets logged or printed)
  - **Live `curl` Verification Script:** [module-4-4-fr14-verification.sh](curl/module-4-4-fr14-verification.sh) → **`SCRIPTED & READY`** (`bash -n docs/audits/curl/module-4-4-fr14-verification.sh` passed; ready to execute against a live running server with Manager credentials)

---

## 1. Executive Summary & Verification Baseline

Module 4.4 delivers **FR-14 — Facility Operating Expense Management, Private Receipt Upload & Streaming, Monthly Estimated Net Profit Reporting, and Vietnamese XLSX / PDF Export** across `AdminFacilityExpenseController` (`8` endpoints) and `AdminFinancialReportController` (`2` endpoints).

### Sequential Delivery Ledger on `feat/orca-phase3-final`

| Step | Commit | Scope Delivered | Verification Status |
|---|---|---|---|
| **Phase 4 Final** | `58ac071` | Module 4.5 Billing & Payments (`FR-15`, `FR-16`, `FR-26`), `BillingPaymentE2ETest`, `VietQrLiveConfigSmokeTest`, and Phase 4 audit docs | `586` offline (`588` total incl. 2 skipped) + `1 / 1` OCI smoke + `1 / 1` VietQR smoke |
| **Task `4.4-1`** | `003c6e5` | `ExpenseErrorCode` (10 entries), `CreateFacilityExpenseRequest` / `UpdateFacilityExpenseRequest`, `FacilityExpenseResponse` / receipt DTOs, `DataIntegrityViolationClassifier` (`uq_facility_expenses_salary_month`), `FacilityExpenseRepository`, `InvoiceRepository` & `MaintenanceTicketRepository` report queries, `FacilityExpenseServiceImpl` | Committed baseline |
| **Task `4.4-2`** | `0499216` | `pom.xml` (`poi-ooxml` `5.4.0`, `openpdf` `2.0.3`), `NotoSans-Regular.ttf` + `NotoSans-LICENSE.txt`, `FinancialReportServiceImpl`, `FinancialReportExportServiceImpl` (4-sheet XLSX with formula-injection neutralization + OpenPDF Vietnamese PDF), and report DTOs | Committed baseline |
| **Task `4.4-3`** | `1d79787` | `AdminFacilityExpenseController` (8 endpoints) + `AdminFinancialReportController` (2 endpoints), controller unit tests, and `FinancialModuleSecurityIntegrationTest` | Committed baseline (`1d79787`) |
| **Task `4.4-4`** | *HEAD* | Expanded `OpenApiContractVerificationTest` (Phase 2 + Phase 3 + Phase 4 + all 10 Module 4.4 endpoints; **9** streaming endpoints checked for no `httpMethod` param), `FacilityExpenseFinancialE2ETest`, executable `docs/audits/curl/module-4-4-fr14-verification.sh`, Module 4.4 audit inventory, and Phase 2 header sync | **`626`** offline (`628` total incl. 2 skipped) + **`1 / 1`** OCI smoke + **`1 / 1`** VietQR smoke + `bash -n` PASS |

### Key Module 4.4 Architectural Invariants

1. **Zero Schema, Entity & Dependency Mutations in `4.4-4`:** `V1__clean_baseline.sql` (20 PostgreSQL tables, including `facility_expenses`, `uq_facility_expenses_salary_month`, `maintenance_tickets`, `ticket_replacement_parts`, `invoices`, `payment_transactions`, `violations`), all JPA entities, and `pom.xml` remain untouched in `4.4-4`; `ddl-auto: validate` passes on real PostgreSQL 16 Testcontainers.
2. **Exact `FR-14` Formula & Strict Exclusions (`FinancialReportServiceImpl`):**
   - `expectedRentRevenue`: sum of `invoices.amount` across all invoice statuses for `billingPeriod = YYYY-MM-01`.
   - `collectedRentRevenue`: sum of `invoices.amount` where `status = PAID` and `billingPeriod = YYYY-MM-01`. Late payments confirmed in a subsequent month (e.g., an October invoice paid on November 3) remain strictly attributed to `2026-10`, **not** `2026-11`. `DEBT` and `PENDING_CONFIRMATION` invoices are excluded from `collectedRentRevenue`.
   - **Deposits & Violation Fines Strictly Excluded:** Contract `deposit_amount` and `violations.fine_amount` are **never** included in `expectedRentRevenue` or `collectedRentRevenue`.
   - `maintenancePartsCost`: sum of `ticket_replacement_parts.quantity * unit_cost` across `maintenance_tickets` with `status = COMPLETED` and `resolvedAt ∈ [YYYY-MM-01T00:00:00+07:00, nextMonth-01T00:00:00+07:00)` in `TimeConfig.DORM_ZONE` (`Asia/Ho_Chi_Minh`). Completed tickets with zero replacement parts appear in `completedMaintenanceTickets` with `partsCost = 0` and increment `completedTicketCount`; `IN_PROGRESS` or non-completed tickets are excluded.
   - `facilityExpenseTotal`: sum of `facility_expenses.amount` for `expenseDate ∈ [YYYY-MM-01, monthEnd]` (`LocalDate` inclusive), with `expenseBreakdownByType` always containing all 5 `ExpenseType` keys (`ELECTRICITY`, `WATER`, `INTERNET`, `SALARY`, `OTHER`).
   - `totalExpenses = maintenancePartsCost + facilityExpenseTotal`.
   - `estimatedNetProfit = collectedRentRevenue - totalExpenses` (negative profit supported and preserved end-to-end).
   - **Mandatory `FR-14 §7` Label & Disclaimer:** Both JSON responses and XLSX/PDF exports always carry `label = "Lợi nhuận ròng (ước tính)"` and exact disclaimer `"Ước tính dựa trên các khoản thu và chi đã được ghi nhận; có thể chưa bao gồm đầy đủ chi phí, thuế, khấu hao và nghĩa vụ khác."`.
3. **`Asia/Ho_Chi_Minh` Time Boundaries & Period Resolution:**
   - Default `period` (when `null` or blank) resolves `YearMonth.now(clock.withZone(TimeConfig.DORM_ZONE))`.
   - Explicit `period` values must match `^\d{4}-(0[1-9]|1[0-2])$` (after trimming in `FinancialReportServiceImpl`; exact regex in `FacilityExpenseServiceImpl.getExpenses`), otherwise `400 EXPENSE_400_INVALID_PERIOD`.
   - UTC instants at `16:59:59Z` (`23:59:59+07:00`) vs `17:00:00Z` / `17:30:00Z` (`00:00:00+07:00` / `00:30:00+07:00`) route accurately across Vietnamese month boundaries.
4. **Single `SALARY` Per Month Guard & In-Place Update Safety (`FacilityExpenseServiceImpl`):**
   - `createExpense` and `updateExpense` check `existsByExpenseTypeAndExpenseDateBetween` / `existsByExpenseTypeAndExpenseDateBetweenAndIdNot` and catch `DataIntegrityViolationException` against PostgreSQL partial unique index `uq_facility_expenses_salary_month` → `409 EXPENSE_409_SALARY_MONTH`. Updating an existing `SALARY` expense within its own month succeeds, while moving it into another occupied `SALARY` month is rejected.
   - Multiple `ELECTRICITY`, `WATER`, `INTERNET`, and `OTHER` records are allowed in the same month.
   - Amounts are validated as strictly positive integers (`> 0`, zero decimal scale, `<= 14` integer digits) and normalized with `setScale(0, RoundingMode.UNNECESSARY)`.
   - Manager attribution (`recorded_by_staff_id`) is derived strictly from `@AuthenticationPrincipal UserPrincipal` (`StaffProfile` with `UserRole.MANAGER` required, else `403 EXPENSE_403_STAFF_PROFILE`).
5. **Private Receipt Storage, Rollback/`afterCommit()` Cleanup & Streaming Hygiene (`FacilityExpenseServiceImpl`):**
   - Receipt uploads accept `.jpg`, `.jpeg`, `.png`, `.webp`, and `.pdf` (`> 0` bytes, `<= 5 MB`), validating filename extension, declared MIME type, and leading magic bytes (`FF D8 FF`, PNG 8-byte signature, `RIFF....WEBP`, `%PDF-`).
   - Objects are stored under `expenses/{expenseId}/receipt/{uuid}.{ext}` in private OCI S3 (`InMemoryPrivateObjectStorage` in offline tests). API responses expose only `FacilityExpenseReceiptResponse(available=true, contentUrl="/api/v1/admin/expenses/{id}/receipt/content")` and never raw storage keys or `/uploads/**` paths.
   - `uploadReceipt` registers rollback cleanup (`afterCompletion` when not committed) for new keys and `afterCommit()` deletion for replaced keys; `deleteReceipt` and `deleteExpense` schedule `afterCommit()` deletion for removed keys.
   - `GET`/`HEAD /api/v1/admin/expenses/{id}/receipt/content` streams via `StreamingResponseUtils.okInline` with synthetic `Content-Disposition: inline; filename="expense-{id}-receipt.{ext}"` and hidden `@Parameter(hidden = true) HttpMethod` (no `httpMethod` parameter exposed in `/v3/api-docs`).
6. **XLSX Formula-Injection Sanitization & Vietnamese PDF Embedding (`FinancialReportExportServiceImpl`):**
   - `XLSX` exports (`Apache POI 5.4.0`) produce 4 worksheets (`Tong hop thu chi`, `Chi tiet chi phi`, `Hoa don da thu`, `Sua chua hoan thanh`) and run `sanitizeSpreadsheetText` on all text cells so leading `=`, `+`, `-`, `@`, `\t`, or `\r` characters are escaped with a leading `'`.
   - `PDF` exports (`OpenPDF 2.0.3`) embed `src/main/resources/fonts/NotoSans-Regular.ttf` (`BaseFont.IDENTITY_H`, `BaseFont.EMBEDDED`) with `Normalizer.Form.NFC` normalization so Vietnamese diacritics render cleanly across all 5 report sections.

---

## 2. Complete Module 4.4 API Contract Inventory (10 Endpoints)

All routes below require `ROLE_MANAGER` (`@PreAuthorize("hasRole('MANAGER')")`). JSON endpoints use standard `ResponseFactory` envelopes (`SingleResponse<T>`, `SingleResponse<PageResponse<T>>`, `SuccessResponse`, `ErrorResponse`, `DetailedErrorResponse`).

### 2.1. Facility Operating Expenses (`AdminFacilityExpenseController`)

Base path: `/api/v1/admin/expenses`

| HTTP Method | Route | Request DTO / Query / Path Params | Success Status & Envelope Payload | Error Codes |
|---|---|---|---|---|
| `GET` | `/api/v1/admin/expenses` | Query:<br>• `period` (optional `YYYY-MM`)<br>• `expenseType` (optional `ExpenseType`: `ELECTRICITY`, `WATER`, `INTERNET`, `SALARY`, `OTHER`)<br>• `search` (optional `String`: matches `title`, `invoiceCode`, `notes`)<br>• `Pageable` (default `size=20`, sort `expenseDate DESC, createdAt DESC`) | `200 OK` → `SingleResponse<PageResponse<FacilityExpenseResponse>>` (`id`, `expenseType`, `title`, `amount`, `expenseDate`, `invoiceCode`, `notes`, `recordedByStaffId`, `recordedByStaffName`, `receipt` [`{available, contentUrl}` or `null`], `createdAt`, `updatedAt`) | `EXPENSE_400_INVALID_PERIOD` (400)<br>`WEB_400_VAL` (400)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` | `/api/v1/admin/expenses/{id}` | Path: `id` (`UUID`) | `200 OK` → `SingleResponse<FacilityExpenseResponse>` | `EXPENSE_404` (404)<br>`WEB_400_VAL` (400)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `POST` | `/api/v1/admin/expenses` | Body: `CreateFacilityExpenseRequest` (`expenseType` [@NotNull], `title` [@NotBlank, <=255], `amount` [@NotNull positive integer <=14 digits], `expenseDate` [@NotNull `LocalDate`], `invoiceCode` [<=100], `notes` [<=2000])<br>Staff identity from `@AuthenticationPrincipal` | `201 Created` → `SingleResponse<FacilityExpenseResponse>` (`receipt: null`) | `EXPENSE_400_AMOUNT` (400)<br>`WEB_400_VAL` (400)<br>`EXPENSE_403_STAFF_PROFILE` (403)<br>`EXPENSE_409_SALARY_MONTH` (409)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `PUT` | `/api/v1/admin/expenses/{id}` | Path: `id` (`UUID`)<br>Body: `UpdateFacilityExpenseRequest` (`expenseType`, `title`, `amount`, `expenseDate`, `invoiceCode`, `notes`) | `200 OK` → `SingleResponse<FacilityExpenseResponse>` | `EXPENSE_404` (404)<br>`EXPENSE_400_AMOUNT` (400)<br>`WEB_400_VAL` (400)<br>`EXPENSE_409_SALARY_MONTH` (409)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `DELETE` | `/api/v1/admin/expenses/{id}` | Path: `id` (`UUID`) | `200 OK` → `SuccessResponse` *(schedules `afterCommit()` S3 receipt deletion if attached)* | `EXPENSE_404` (404)<br>`WEB_400_VAL` (400)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `POST` | `/api/v1/admin/expenses/{id}/receipt` | Path: `id` (`UUID`)<br>Multipart `form-data`: `file` (`MultipartFile`: `.jpg`, `.jpeg`, `.png`, `.webp`, `.pdf`; `> 0` bytes and `<= 5 MB`; declared MIME + magic bytes validated) | `200 OK` → `SingleResponse<FacilityExpenseResponse>` (`receipt: { available: true, contentUrl: "/api/v1/admin/expenses/{id}/receipt/content" }`; replaces previous S3 key `afterCommit()`) | `EXPENSE_404` (404)<br>`EXPENSE_400_RECEIPT_REQUIRED` (400)<br>`EXPENSE_400_RECEIPT_SIZE` (400)<br>`EXPENSE_400_RECEIPT_FORMAT` (400)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `DELETE` | `/api/v1/admin/expenses/{id}/receipt` | Path: `id` (`UUID`) | `200 OK` → `SingleResponse<FacilityExpenseResponse>` (`receipt: null`; S3 object deleted `afterCommit()`) | `EXPENSE_404` (404)<br>`EXPENSE_404_RECEIPT` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` / `HEAD` | `/api/v1/admin/expenses/{id}/receipt/content` | Path: `id` (`UUID`) *(no `httpMethod` OpenAPI parameter exposed)* | `200 OK` → Binary `Resource` stream (`Content-Type`: `image/jpeg` \| `image/png` \| `image/webp` \| `application/pdf`, `Content-Length`, `Content-Disposition: inline; filename="expense-{id}-receipt.{ext}"`; `HEAD` closes `ManagedStreamResource` unconsumed) | `EXPENSE_404` (404)<br>`EXPENSE_404_RECEIPT` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |

### 2.2. Financial Reports & Exports (`AdminFinancialReportController`)

Base path: `/api/v1/admin/financial-reports`

| HTTP Method | Route | Query Params | Success Status & Payload | Error Codes |
|---|---|---|---|---|
| `GET` | `/api/v1/admin/financial-reports/net-profit` | `period` (optional `YYYY-MM`; omitted/blank defaults to current `Asia/Ho_Chi_Minh` month via `Clock`) | `200 OK` → `SingleResponse<EstimatedNetProfitReportResponse>` (`period`, `periodStartDate`, `periodEndDate`, `label`, `disclaimer`, `expectedRentRevenue`, `collectedRentRevenue`, `maintenancePartsCost`, `facilityExpenseTotal`, `totalExpenses`, `estimatedNetProfit`, `expenseBreakdownByType`, `totalInvoiceCount`, `paidInvoiceCount`, `completedTicketCount`, `facilityExpenseCount`, `paidInvoices`, `completedMaintenanceTickets`, `facilityExpenses`) | `EXPENSE_400_INVALID_PERIOD` (400)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` | `/api/v1/admin/financial-reports/net-profit/export` | `period` (optional `YYYY-MM`; defaults to current `Asia/Ho_Chi_Minh` month)<br>`format` (`XLSX` \| `PDF`, case-insensitive; required non-blank) | `200 OK` → `byte[]` attachment (`Content-Disposition: attachment; filename="thu-chi-{period}.{xlsx\|pdf}"`, `Content-Length`, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` \| `application/pdf`) | `EXPENSE_400_EXPORT_FORMAT` (400)<br>`EXPENSE_400_INVALID_PERIOD` (400)<br>`WEB_401` (401)<br>`WEB_403` (403) |

---

## 3. Domain Error Code Matrix (`ExpenseErrorCode`) & Constraint Mappings

### 3.1. Complete `ExpenseErrorCode` Enum (10 Entries)

Verified directly from `src/main/java/com/uit/dormmanagement/exceptions/error/ExpenseErrorCode.java` and `DomainErrorCodeTest`:

| # | Enum Constant | `errorCode` | HTTP Status | Backend Message | Trigger Condition |
|---|---|---|---|---|---|
| 1 | `EXPENSE_NOT_FOUND` | `EXPENSE_404` | `404 NOT_FOUND` | Không tìm thấy chi phí vận hành | Missing/null `expenseId` on detail, update, delete, receipt upload, receipt delete, or receipt streaming |
| 2 | `EXPENSE_RECEIPT_NOT_FOUND` | `EXPENSE_404_RECEIPT` | `404 NOT_FOUND` | Không tìm thấy hóa đơn/chứng từ của chi phí vận hành | `deleteReceipt` or `loadReceiptContent` when `invoiceFileKey` is null/blank or missing in private object storage |
| 3 | `INVALID_REPORT_PERIOD` | `EXPENSE_400_INVALID_PERIOD` | `400 BAD_REQUEST` | Kỳ báo cáo không hợp lệ (định dạng hợp lệ: YYYY-MM) | `period` filter or report query param fails `^\d{4}-(0[1-9]|1[0-2])$` |
| 4 | `INVALID_EXPENSE_AMOUNT` | `EXPENSE_400_AMOUNT` | `400 BAD_REQUEST` | Số tiền chi phí không hợp lệ (phải là số nguyên dương) | `amount` is null, `<= 0`, has non-zero fractional scale, or exceeds `14` integer digits |
| 5 | `INVALID_EXPORT_FORMAT` | `EXPENSE_400_EXPORT_FORMAT` | `400 BAD_REQUEST` | Định dạng xuất báo cáo không hợp lệ (hỗ trợ: PDF, EXCEL) | `format` query param is null, blank, or not `XLSX` / `PDF` (case-insensitive) |
| 6 | `RECEIPT_FILE_REQUIRED` | `EXPENSE_400_RECEIPT_REQUIRED` | `400 BAD_REQUEST` | Tệp hóa đơn/chứng từ không được để trống | Missing, null, empty, or zero-byte `file` part on receipt upload |
| 7 | `RECEIPT_FILE_SIZE_EXCEEDED` | `EXPENSE_400_RECEIPT_SIZE` | `400 BAD_REQUEST` | Dung lượng tệp chứng từ vượt quá giới hạn cho phép (tối đa 5MB) | Uploaded receipt exceeds `5 * 1024 * 1024` bytes |
| 8 | `UNSUPPORTED_RECEIPT_FORMAT` | `EXPENSE_400_RECEIPT_FORMAT` | `400 BAD_REQUEST` | Định dạng tệp chứng từ không được hỗ trợ (chỉ chấp nhận JPG, JPEG, PNG, WEBP, PDF) | Missing extension, disallowed extension, mismatched declared MIME type, or magic-byte mismatch |
| 9 | `MANAGER_STAFF_PROFILE_REQUIRED` | `EXPENSE_403_STAFF_PROFILE` | `403 FORBIDDEN` | Yêu cầu tài khoản Quản lý có hồ sơ nhân viên hợp lệ | Authenticated `MANAGER` user has null ID, no linked `StaffProfile`, or staff user role `!= MANAGER` |
| 10 | `SALARY_EXPENSE_ALREADY_EXISTS` | `EXPENSE_409_SALARY_MONTH` | `409 CONFLICT` | Chi phí lương (SALARY) đã được ghi nhận trong tháng này | Creating a second `SALARY` expense in the same calendar month, updating an expense to `SALARY` in an occupied month, or hitting `uq_facility_expenses_salary_month` under concurrency |

*(Note: `INVALID_EXPORT_FORMAT` accepts canonical query values `XLSX` and `PDF` case-insensitively per `FinancialReportExportFormat`, while its user-facing Vietnamese message displays `(hỗ trợ: PDF, EXCEL)`.)*

**Cross-Domain Error Codes Surfaced on Module 4.4 Endpoints:**
- `WebErrorCode`: `WEB_400_VAL` (400 — bean validation `@NotNull`/`@NotBlank`/`@Size` or malformed UUID/enum/date params), `WEB_401` (401 — unauthenticated), `WEB_403` (403 — non-`MANAGER` role guard).
- `SystemErrorCode`: `SYS_500` (500 — multipart/export IO error), `SYS_502` / `EXTERNAL_SERVICE_ERROR` (OCI S3 storage failure).

### 3.2. PostgreSQL Unique Constraint Mapping (`DataIntegrityViolationClassifier`)

Verified from `src/main/java/com/uit/dormmanagement/exceptions/DataIntegrityViolationClassifier.java` (lines 58–59), `AppExceptionHandlerDataIntegrityTest` (line 71), and `FacilityExpensePostgresTest`:

| PostgreSQL Constraint / Index | Table & Index Definition | Mapped `AppErrorCode` | HTTP Status |
|---|---|---|---|
| `uq_facility_expenses_salary_month` | `facility_expenses (EXTRACT(YEAR FROM expense_date), EXTRACT(MONTH FROM expense_date)) WHERE expense_type = 'SALARY'` | `ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS` (`EXPENSE_409_SALARY_MONTH`) | `409 CONFLICT` |

---

## 4. Frontend Prototype Compatibility & Discrepancy Disposition Matrix

Inspected read-only at `/home/chinh/Projects/dorm-management-prototype` on branch `feature/backend-api-integration` (`d87a76d`). **Note:** The prototype currently runs against local mock state (`src/services/mock/mockDormService.ts`) while `src/services/api/apiDormService.ts` still throws placeholders for facility expenses (`getExpenses`, `createExpense`, `updateExpense`, `deleteExpense` throwing `"API chi phí chưa được thống nhất."` at L27–L30) and computes financial summaries/exports client-side in `FinancialPage.tsx`, `reportForPeriod.ts`, `financialCalculations.ts`, and `reportExport.ts`. The Backend enforces **SRS v3.4.1 FINAL** strictly; the Frontend adapter layer must reconcile these 7 discrepancies:

| ID | Discrepancy Area | Prototype Behavior (`d87a76d`) | Backend SRS v3.4.1 Contract | Disposition & Required FE Adapter Action |
|---|---|---|---|---|
| **F44-D01** | **Unimplemented API Routes, Pagination & Envelopes** | `apiDormService.ts` (L27–L30) throws `"API chi phí chưa được thống nhất."`, and `FinancialPage.tsx` filters an in-memory `FacilityExpense[]` prop client-side | `MANAGER`-only endpoints under `/api/v1/admin/expenses` (`SingleResponse<PageResponse<FacilityExpenseResponse>>`) and `/api/v1/admin/financial-reports/net-profit*` | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Wire expense CRUD/receipt and report/export calls to `/api/v1/admin/expenses*` and `/api/v1/admin/financial-reports/net-profit*`, pass `period`/`expenseType`/`search`/`Pageable`, and unwrap `res.data`. |
| **F44-D02** | **Category Labels vs `ExpenseType` Enum & Salary Rule** | Prototype uses Vietnamese labels `"Điện EVN" \| "Nước" \| "Internet" \| "Lương nhân viên" \| "Khác"` (`types/invoice.ts` L48, `FinancialPage.tsx` L11–L17, L207–L213, L381–L385) with no monthly salary uniqueness check | Canonical enum `ExpenseType`: `ELECTRICITY`, `WATER`, `INTERNET`, `SALARY`, `OTHER` (`ExpenseType.displayName` provides Vietnamese labels); at most one `SALARY` per calendar month (`409 EXPENSE_409_SALARY_MONTH`) | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Map UI labels to canonical `ExpenseType` values on requests/filters and surface `EXPENSE_409_SALARY_MONTH` when creating/moving a second `SALARY` in the same month. |
| **F44-D03** | **Client IDs, Field Names (`note` vs `notes`) & Attribution** | `FinancialPage.tsx` (L88–L97) generates `"EXP-" + crypto.randomUUID()`, uses `category` and `note`, stores locale string `createdAt`, and omits staff attribution | Server-assigned UUID `id`; canonical field names `expenseType`, `notes`, ISO `Instant` `createdAt`/`updatedAt`, plus JWT-derived `recordedByStaffId` and `recordedByStaffName` | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Drop client UUID/timestamp generation, map `expenseType`/`notes`, and display `recordedByStaffName` returned by the server. |
| **F44-D04** | **Receipt Attachment, PDF Support & Private Streaming** | `FinancialPage.tsx` (L451–L485, L554–L567) accepts only `image/*`, base64-encodes `FileReader` into `receiptImage` `dataUrl`, and embeds `<img src={receiptImage}>` | Dedicated multipart upload `POST /api/v1/admin/expenses/{id}/receipt` (`JPG`/`JPEG`/`PNG`/`WEBP`/`PDF`, `<= 5 MB`), `DELETE .../receipt`, and Bearer-protected streaming `receipt.contentUrl` (`/api/v1/admin/expenses/{id}/receipt/content`) | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Create/update JSON expense first, then upload/delete receipt via dedicated endpoints (`accept="image/jpeg,image/png,image/webp,application/pdf"`), fetch `receipt.contentUrl` with `Authorization: Bearer <token>` as a `Blob` URL, and support PDF preview/download alongside images. |
| **F44-D05** | **Server-Side Aggregation vs Client Calculation & Blocking** | `reportForPeriod.ts` (L25–L32) & `FinancialPage.tsx` (L63–L70, L117, L155) recompute totals client-side from loaded rows and block the whole report (`null`) if any completed ticket lacks `parts` | `GET /api/v1/admin/financial-reports/net-profit?period=YYYY-MM` computes all KPIs (`expectedRentRevenue`, `collectedRentRevenue`, `maintenancePartsCost`, `facilityExpenseTotal`, `totalExpenses`, `estimatedNetProfit`), `expenseBreakdownByType`, counts, and detail lists server-side; zero-part completed tickets appear with `partsCost = 0` | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Replace `reportForPeriod` / `calculateFinancialSummary` in API mode with `/api/v1/admin/financial-reports/net-profit`, surface `expectedRentRevenue`, and render zero-part tickets normally. |
| **F44-D06** | **Server-Side XLSX / PDF Export vs Client Generation** | `FinancialPage.tsx` (L118–L135) & `reportExport.ts` (L1–L44) build a 1-sheet `ExcelJS` workbook or 1-page `pdf-lib` PDF in browser memory | `GET /api/v1/admin/financial-reports/net-profit/export?period=YYYY-MM&format=XLSX\|PDF` streams 4-sheet sanitized `Apache POI` XLSX or multi-section `OpenPDF` + `NotoSans` PDF as attachment `thu-chi-{period}.{xlsx\|pdf}` | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Fetch the authenticated export endpoint as `Blob` with `format=XLSX` or `format=PDF` and trigger download from the response instead of running `ExcelJS`/`pdf-lib` in API mode. |
| **F44-D07** | **Vietnam Timezone Default Date & Period Validation** | `FinancialPage.tsx` (L50, L191) defaults `expenseDate` via `new Date().toISOString().split("T")[0]` (UTC date, off by +1 day before 07:00 VN time) | Backend enforces strict `YYYY-MM` period format, ISO `LocalDate` `expenseDate`, and `Asia/Ho_Chi_Minh` (`TimeConfig.DORM_ZONE`) clock/month boundaries for default period and ticket `resolvedAt` attribution | **Backend Authoritative (`FE_ADAPTER_REQUIRED`):** Format default `expenseDate` using `Asia/Ho_Chi_Minh` (`en-CA` formatter, matching `period` L43) and pass validated `YYYY-MM` to `/net-profit` and `/expenses`. |

---

## 5. Verification Suites, Live `curl` Script Status & Preserved Deferred Risk Ledger

### 5.1. Module 4.4 Verification Suites (`Task 4.4-4`)

Executed after final Task `4.4-4` edits on `feat/orca-phase3-final`:
- **Full Offline Suite (`./mvnw test`):** `Tests run: 628, Failures: 0, Errors: 0, Skipped: 2` (**`626`** offline tests passed; the `2` skipped tests are opt-in live smoke tests `OciS3LiveSmokeTest` and `VietQrLiveConfigSmokeTest`).

| Test Suite | Count | Scope Verified |
|---|---|---|
| `OpenApiContractVerificationTest` | `1` | Live `GET /v3/api-docs` verification across all Phase 2, Phase 3, Phase 4, and all **10 Module 4.4** endpoints (`8` Facility Expense + `2` Financial Report), absence of `PUT /api/v1/admin/vehicles/{id}`, and absence of `httpMethod` OpenAPI parameter across all **9** streaming endpoints |
| `FacilityExpenseFinancialE2ETest` | `1` | Full PostgreSQL 16 + Redis Testcontainers + `InMemoryPrivateObjectStorage` + `MutableTestClock` E2E FR-14 lifecycle (no class `@Transactional`): Cross-module seed (2 tenants onboarded `2026-10-01` with `4M` deposits, late `2026-11-03` cash payment attributed to `2026-10`, unpaid `DEBT` invoice, `500k` violation fine excluded, 4 maintenance tickets across VN midnight `16:59:59Z`/`17:30:00Z`, zero-part ticket, and `IN_PROGRESS` exclusion) → all 5 `ExpenseType`s, `409 EXPENSE_409_SALARY_MONTH` guard, PNG receipt upload/streaming, PDF replacement + `afterCommit()` PNG deletion, receipt deletion + `afterCommit()` PDF deletion → negative net profit (`-2,250,000`), in-place `SALARY` edit (`3M → 1.2M`) + `OTHER` delete (`100k`) immediate recalculation (`-350,000`), November `2026-11` attribution (`-400,000`) → `XSSFWorkbook` XLSX & OpenPDF `PdfTextExtractor` PDF KPI/disclaimer consistency |
| `FinancialModuleSecurityIntegrationTest` | `4` | RBAC `401` Anonymous & `403` `TENANT`/`TECHNICIAN` matrix across all 10 endpoints (+ `HEAD`), full Manager HTTP lifecycle, `409` salary conflict / `400` validation & raw S3 key redaction, and live HTTP JSON/XLSX/PDF report assertions |
| `FacilityExpenseControllerTest` | `8` | MockMvc routing, query filter/pageable binding, `201` creation, `PUT`/`DELETE`, controller-level empty/missing file guard, `GET`/`HEAD` `ManagedStreamResource` closing, and `400`/`404`/`409` exception mapping |
| `FinancialReportControllerTest` | `2` | MockMvc `/net-profit` (default/explicit/invalid period `400`) and `/net-profit/export` (`400 EXPENSE_400_EXPORT_FORMAT`, `XLSX`/`PDF` `Content-Disposition` attachment bytes) |
| `FacilityExpensePostgresTest` | `4` | Real PostgreSQL `uq_facility_expenses_salary_month` concurrent race serialization (1 winner, 1 `409 EXPENSE_409_SALARY_MONTH`), non-salary multiples & in-place vs cross-month `SALARY` updates, receipt `afterCommit()`/rollback S3 cleanup, and period/null-date/search/sum JPQL queries |
| `FacilityExpenseServiceImplTest` | `11` | Unit verification of expense filtering/paging, detail, `STAFF_PROFILE` & amount validation, salary conflict classification, JPG/PNG/WEBP/PDF magic-byte + MIME validation, rollback/`afterCommit()` S3 cleanup, IO error containment, and receipt streaming |
| `FinancialReportPostgresTest` | `4` | Real PostgreSQL revenue attribution & strict exclusion of `DEBT`/`PENDING_CONFIRMATION`/deposits/violation fines, `Asia/Ho_Chi_Minh` UTC midnight boundaries & zero-part tickets, negative net profit + 5-type breakdown + exact `FR-14 §7` disclaimer, and `Clock`-based period resolution |
| `FinancialReportExportServiceImplTest` | `4` | Export format validation (`400 EXPENSE_400_EXPORT_FORMAT`), case-insensitive `xlsx`/`pdf`, 4-sheet `XSSFWorkbook` numeric KPIs + formula-injection neutralization (`=`, `+`, `-`, `@`, `\t`, `\r`), and OpenPDF `NotoSans-Regular.ttf` Vietnamese diacritics + disclaimer verification |
| `DomainErrorCodeTest` & `AppExceptionHandlerDataIntegrityTest` | `6` + `27` | Complete `ExpenseErrorCode` (10 entries) prefix/status checks and PostgreSQL `uq_facility_expenses_salary_month` → `409 EXPENSE_409_SALARY_MONTH` classification |
| `DataIntegrityClassificationPostgresTest` | `1` | Live PostgreSQL `DataIntegrityViolationClassifier` SQLSTATE `23505` vs `23514`/`23502`/`23503`/`22001` integration check |
| `OciS3LiveSmokeTest` | `1` *(live)* | `OCI_SMOKE_TEST_ENABLED=true ./mvnw test -Dtest=OciS3LiveSmokeTest` → **`1 / 1`** passed (live OCI private bucket round-trip `putObject` → `getObject` → `deleteObject`) |
| `VietQrLiveConfigSmokeTest` | `1` *(live)* | `VIETQR_SMOKE_TEST_ENABLED=true ./mvnw test -Dtest=VietQrLiveConfigSmokeTest` → **`1 / 1`** passed (live `.env` VietQR config EMVCo TLV & CRC-16 check + redaction check, ZERO secrets printed) |

### 5.2. Live `curl` Runtime Verification Script Status

- **Script:** [module-4-4-fr14-verification.sh](curl/module-4-4-fr14-verification.sh) (`chmod +x` applied)
- **Syntax Check:** `bash -n docs/audits/curl/module-4-4-fr14-verification.sh` → **PASS** (exit code `0`)
- **Status:** **`SCRIPTED & READY`** — exercises `POST /api/v1/auth/login` plus all **10 Module 4.4 endpoints** (including `409 EXPENSE_409_SALARY_MONTH`, `400 EXPENSE_400_AMOUNT`, PNG upload, `HEAD`/`GET` byte comparison, `net-profit` disclaimer check, `XLSX` `PK` / `PDF` `%PDF-` magic headers, receipt deletion, and test-data cleanup) without hardcoding any credentials. Execute against a running backend instance by exporting `BASE_URL`, `MANAGER_EMAIL`, and `MANAGER_PASSWORD`.

### 5.3. Preserved Phase 2 Deferred Findings Ledger (Untouched)

The following Phase 2 review findings remain explicitly **`DEFERRED / ACCEPTED RISK`** per owner decision and were **NOT** modified in Phase 3A, Phase 3B, Phase 4 (`4-1`..`4-6`), or Module 4.4 (`4.4-1`..`4.4-4`):
- **`F-01` (`DEFERRED / ACCEPTED RISK`):** Old-email password recovery challenge race vs. Manager email correction (`PasswordRecoveryServiceImpl`).
- **`F-04` (`DEFERRED / ACCEPTED RISK`):** Non-atomic Redis login failure counter/TTL/lock (`LoginAttemptServiceImpl`).
- **`F-06` (`DEFERRED / ACCEPTED RISK`):** Non-atomic missing Redis session generation initialization (`UserTokenInvalidationServiceImpl`).
- **`F-07` (`DEFERRED / ACCEPTED RISK`):** Undelivered OTP challenge retained in Redis after email delivery failure (`PasswordRecoveryServiceImpl`).

### 5.4. Branch & Delivery Status Notice

All Phase 2 Hardening, Phase 3A, Phase 3B, Phase 4 (`4-1`..`4-6`), and Module 4.4 (`4.4-1`..`4.4-4`) work is committed **locally only** on branch `feat/orca-phase3-final`. No merge, push, or deployment has been performed. Phase 5 (Module 4.10 Notifications `FR-25`/`FR-27`) has not been started.
