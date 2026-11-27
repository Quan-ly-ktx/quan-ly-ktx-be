# KTX Hub Backend — Phase 3 API Contract Inventory & Verification Report

- **Authoritative Baseline:** SRS v3.4.1 FINAL | Logical ERD v3.6 FINAL | Conceptual ERD v3.5 FINAL
- **Repository Branch:** `feat/orca-phase3-final` (layered on Phase 2 Hardening `f479fe6` / `18e1d6e`)
- **Frontend Prototype Reference (Read-only):** `/home/chinh/Projects/dorm-management-prototype` (`feature/backend-api-integration` @ `d87a76d`)
- **Execution Date:** November 26, 2026
- **Stack:** Java 25 | Spring Boot `4.1.2` | PostgreSQL 16/17 + Flyway (`V1__clean_baseline.sql`) | Redis 8 | AWS SDK for Java v2 (`software.amazon.awssdk:s3` BOM `2.59.10`) | OpenAPI `/v3/api-docs` & Scalar `/scalar` (`/docs`)
- **Implementation Mode:** Phase 3A OCI S3 Private Storage & Streaming (`2b391e3`, `eda3732`) + Phase 3B Module 4.3 Onboarding & Contracts (`3B-1`..`3B-6`; zero schema/entity/`pom.xml` changes in `3B-6`; Phase 2 deferred findings `F-01`, `F-04`, `F-06`, `F-07` untouched)
- **Verification Status (Task `3B-6`):**
  - **Compilation:** `./mvnw -DskipTests compile` → `BUILD SUCCESS` (`215` main Java source files)
  - **Full Offline Suite:** `./mvnw test` → **489** offline tests passed (`Tests run: 490, Failures: 0, Errors: 0, Skipped: 1` live smoke test `OciS3LiveSmokeTest`)
  - **Live OCI S3 Smoke Test:** `OCI_SMOKE_TEST_ENABLED=true ./mvnw test -Dtest=OciS3LiveSmokeTest` → **1 / 1** passed (`Failures: 0, Errors: 0, Skipped: 0`)

---

## 1. Executive Summary & Verification Baseline

Phase 3 delivers **Phase 3A** (private OCI Object Storage via S3 Compatibility API and Backend Streaming) and **Phase 3B** (**Module 4.3 — Direct Onboarding, Contract Lifecycle, Document Management, Tenant Contract History, Expiry Monitoring, and Minimal Invoice Issuance Support** strictly needed by Onboarding and Renewal).

### Sequential Delivery Ledger on `feat/orca-phase3-final`

| Step | Commit | Scope Delivered | Verified Test Baseline |
|---|---|---|---|
| **Phase 2 Hardening** | `f479fe6` | `F-03`, `F-05`, `F-08`, `F-09` fixed; `F-02` verified; `F-01`/`F-04`/`F-06`/`F-07` deferred | `388 / 388` offline tests |
| **Phase 3A** | `2b391e3` | OCI S3-compatible `PrivateObjectStorage`, Backend Streaming, `/uploads/**` `denyAll()` | `398` offline + `1 / 1` live OCI smoke |
| **Task `3A-Fix`** | `eda3732` | `ManagedStreamResource` safe stream abort/close on `HEAD` & errors across 6 streaming endpoints | `413` offline + `1 / 1` live OCI smoke |
| **Task `3B-1`** | `935ce99` | Injectable dorm `Clock` (`TimeConfig.DORM_ZONE = Asia/Ho_Chi_Minh`) + `InvoiceIssuanceService` | `426` offline |
| **Task `3B-2`** | `4d6856d` | `FR-09` Day-01 atomic Onboarding (`POST /api/v1/admin/onboarding`) + first-month `DEBT` invoice | `448` offline |
| **Task `3B-3`** | `62863b4` | `FR-12` Append-only `ContractRenewal`, locked `room.basePrice`, debt check, day-25+ `T05` catch-up | `464` offline |
| **Task `3B-4`** | `18295b2` | `FR-13` Atomic Checkout (`AVAILABLE`/`MAINTENANCE`), vehicle revocation & request cancellation | `472` offline |
| **Task `3B-5`** | `33d0f03` | `FR-10` Document magic-byte/MIME validation, `renewalId` link, Tenant contract history, scheduler fix | `489` offline |
| **Task `3B-6`** | *HEAD* | Expanded `OpenApiContractVerificationTest` (Phase 2 + Phase 3), expanded `ResidentLifecycleE2ETest`, Phase 3 audit inventory & Phase 2 doc sync | **489** offline (`490` total incl. 1 skipped) + **1 / 1** live OCI smoke |

### Key Phase 3 Architectural Invariants

1. **Zero Schema & Entity Mutations in `3B-6`:** `V1__clean_baseline.sql` (20 PostgreSQL tables) and all JPA entity mappings remain untouched; `ddl-auto: validate` passes on real PostgreSQL Testcontainers.
2. **Injectable Dorm Business Clock (`TimeConfig`):** All business date checks (`FR-09` day `01` onboarding + `startDate == today`, `FR-12` expiry cutoff + day-25+ catch-up issuance, `FR-13` `checkedOutAt`, `ContractStatusScheduler` 30-day lookahead) resolve via injected `Clock` anchored to `Asia/Ho_Chi_Minh`.
3. **Immutable Original Contract Terms (`BR-03` / `FR-12`):** `contracts.original_end_on` and `contracts.original_monthly_rent` are never overwritten on renewal. Renewals append immutable rows to `contract_renewals` (`sequence_no`, `signed_on`, `previous_end_on`, `effective_from`, `new_end_on`, `monthly_rent` locked to current `room.basePrice`), while `ContractDetailResponse` surfaces the effective `endDate` and `monthlyRent` from the latest renewal.
4. **Anti-Deadlock Pessimistic Lock Ordering:**
   - **Onboarding (`OnboardingServiceImpl`):** `rooms` (`PESSIMISTIC_WRITE`) → `beds` (`PESSIMISTIC_WRITE`), then for returning tenants `users` (`PESSIMISTIC_WRITE`) → `tenants` (`PESSIMISTIC_WRITE`).
   - **Checkout (`ContractServiceImpl.terminateContract`):** `rooms` → `beds` → `contracts` → `tenants` → `vehicles` (`id ASC`) → `vehicle_change_requests` (`PENDING`, `id ASC`).
5. **Private OCI S3 Storage & Backend Streaming:** Contract documents are stored under `contracts/{contractId}/{uuid}_{sanitizedName}.{ext}` in the private OCI bucket (`InMemoryPrivateObjectStorage` in offline test suites) and served solely via authorized Backend Streaming endpoints (`GET` / `HEAD`) with `ManagedStreamResource` safe close. `ContractDocumentResponse.fileUrl` never exposes `storage_file_key` or `/uploads/**`.

---

## 2. Complete Phase 3 API Contract Inventory

All Phase 3 endpoints wrap payloads in standard `ResponseFactory` envelopes (`SingleResponse<T>`, `SuccessResponse`, `SingleResponse<PageResponse<T>>`, `ErrorResponse`, `DetailedErrorResponse`), except binary document streaming endpoints which return `ResponseEntity<Resource>`.

### 2.1. Onboarding (`OnboardingController`)

| HTTP Method | Route | Authorization | Request DTO / Constraints | Success Status & Envelope Payload | Error Codes |
|---|---|---|---|---|---|
| `POST` | `/api/v1/admin/onboarding` | `MANAGER` | `OnboardingRequest` (`application/json`):<br>• `fullName` (`@NotBlank`, 2–100 chars)<br>• `gender` (`@NotNull`, `MALE` / `FEMALE`)<br>• `idCardNumber` (`@NotBlank`, `^[0-9]{12}$`)<br>• `phone` (`@NotBlank`, `^0[0-9]{9}$`)<br>• `email` (`@NotBlank`, `@Email`)<br>• `permanentAddress` (`@NotBlank`)<br>• `dateOfBirth` (optional `LocalDate`)<br>• `emergencyContactName` (optional)<br>• `emergencyContactPhone` (optional `^0[0-9]{9}$`)<br>• `bedId` (`@NotNull` `UUID`)<br>• `startDate` (`@NotNull` `LocalDate`: must equal today in `Asia/Ho_Chi_Minh` on day `01`)<br>• `endDate` (`@NotNull` `LocalDate`: `> startDate` and month-end)<br>• `depositAmount` (`@NotNull`, non-negative integer VND) | `201 Created` → `SingleResponse<OnboardingResponse>`:<br>• `tenant`: `TenantDetailResponse` (`residentStatus: ACTIVE`)<br>• `contract`: `ContractDetailResponse` (`status: ACTIVE`, `monthlyRent = room.basePrice`)<br>• `account`: `GeneratedAccountInfo` (`username = idCardNumber`, `email`, `phone`, `initialPassword` [random 1-time password for new tenant; `null` for returning tenant], `roomBed`)<br>• `firstInvoice`: `OnboardingInvoiceSummary` (`id`, `invoiceCode`, `billingPeriod = startDate`, `amount`, `dueOn = day 05`, `status: DEBT`) | `CONTRACT_400_DAY_ONE_ONLY` (400)<br>`CONTRACT_400_DATES` (400)<br>`CONTRACT_400_DEPOSIT` (400)<br>`CONTRACT_400_BED_UNAVAILABLE` (400)<br>`CONTRACT_400_GENDER_MISMATCH` (400)<br>`ROOM_404` / `BED_404` (404)<br>`TENANT_404` (404)<br>`CONTRACT_409_ROOM_ARCHIVED` (409)<br>`CONTRACT_409_BED_OCCUPIED` (409)<br>`CONTRACT_409_TENANT_ACTIVE` (409)<br>`CONTRACT_409_UNSETTLED_DEBT` (409)<br>`TENANT_409_EMAIL` (409)<br>`TENANT_409_ID_CARD` (409)<br>`WEB_400_VAL` (400)<br>`WEB_401` (401) / `WEB_403` (403) |

**Behavioral Notes (`OnboardingServiceImpl`):**
- Rejects non-day-01 requests (`Asia/Ho_Chi_Minh`) with `400 CONTRACT_400_DAY_ONE_ONLY`.
- Marks `room_code_registry.used_in_business = true` atomically, sets `bed.status = OCCUPIED`, and issues the first-month `DEBT` invoice via `InvoiceIssuanceService.issueMonthlyInvoiceIfAbsent`. Any failure rolls back the entire transaction.
- **Returning `CHECKED_OUT` tenant (matched by 12-digit `idCardNumber`):** Reuses existing `Tenant` and `User`, preserves existing password (`initialPassword = null`), blocks with `409 CONTRACT_409_UNSETTLED_DEBT` if any `DEBT` or `PENDING_CONFIRMATION` invoice exists, and invalidates password recovery OTP challenges if email changed.

---

### 2.2. Admin Contract Lifecycle & Document Management (`AdminContractController`)

| HTTP Method | Route | Authorization | Request DTO / Multipart / Params | Success Status & Envelope Payload | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/contracts` | `MANAGER` | Query: `Pageable`, `search` (matches `contractCode`, tenant name/phone/code, bed/room code), `status` (`ContractStatus`: `ACTIVE` / `LIQUIDATED`) | `200 OK` → `SingleResponse<PageResponse<ContractSummaryResponse>>` (`id`, `contractCode`, `tenantId`, `tenantName`, `tenantPhone`, `roomNumber`, `bedCode`, `startDate`, `endDate`, `monthlyRent`, `depositAmount`, `status`) | `WEB_401` (401)<br>`WEB_403` (403) |
| `GET` | `/api/v1/admin/contracts/{id}` | `MANAGER` | Path: `id` (`UUID`) | `200 OK` → `SingleResponse<ContractDetailResponse>` (`id`, `contractCode`, `tenantId`, `tenantName`, `tenantPhone`, `bedId`, `bedCode`, `roomId`, `roomNumber`, `startDate`, `endDate` [effective], `monthlyRent` [effective], `depositAmount`, `status`, `contractDocumentUrl: null`, `signedAt`, `checkedOutAt`, `createdAt`, `documents` [admin `/content` URLs], `renewals` [ordered by `sequenceNo ASC`]) | `CONTRACT_404` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `POST` | `/api/v1/admin/contracts/{id}/renew` | `MANAGER` | Path: `id` (`UUID`)<br>Multipart `form-data`:<br>• `@ModelAttribute @Valid RenewContractRequest` (`newEndDate` [`@NotNull LocalDate`]: must be `> currentEndDate` & month-end)<br>• `annexDocuments` (optional `List<MultipartFile>`) | `200 OK` → `SingleResponse<ContractDetailResponse>` (includes newly appended `ContractRenewalResponse` [`id`, `sequenceNo`, `signedOn`, `previousEndOn`, `effectiveFrom`, `newEndOn`, `monthlyRent` locked to current `room.basePrice`, `createdAt`] and any uploaded `RENEWAL_ANNEX` docs; triggers `T05` day-25+ catch-up `DEBT` invoice when signed on day `>= 25` of `previousEndOn`'s month) | `CONTRACT_404` (404)<br>`CONTRACT_400_TERMINATED` (400)<br>`TENANT_400_NOT_ACTIVE` (400)<br>`CONTRACT_400_BED_UNAVAILABLE` (400)<br>`ROOM_404` (404)<br>`CONTRACT_409_ROOM_ARCHIVED` (409)<br>`CONTRACT_409_EXPIRED` (409)<br>`CONTRACT_409_UNSETTLED_DEBT` (409)<br>`CONTRACT_400_DATES` (400)<br>`CONTRACT_409_RENEWAL_CONFLICT` (409)<br>`CONTRACT_400_FILE_SIZE` (400)<br>`CONTRACT_400_FILE_FORMAT` (400)<br>`WEB_400_VAL` (400) |
| `POST` | `/api/v1/admin/contracts/{id}/terminate` | `MANAGER` | Path: `id` (`UUID`)<br>JSON `CheckoutContractRequest`:<br>• `bedStatusAfterCheckout` (`@NotNull BedStatus`, `@JsonAlias("bedStatus")`: `AVAILABLE` or `MAINTENANCE`)<br>• `handoverConfirmed` (`@NotNull @AssertTrue Boolean`) | `200 OK` → `SingleResponse<ContractDetailResponse>` (`status: LIQUIDATED`, `checkedOutAt` populated; bed → chosen status; tenant → `CHECKED_OUT`; `User.status` remains `ACTIVE`; `ISSUED` vehicles → `REVOKED` keeping `cardCode`; `PENDING_CARD` vehicles & `PENDING` change requests → `CANCELLED`; unsettled `DEBT`/`PENDING_CONFIRMATION` invoices preserved) | `CONTRACT_404` (404)<br>`CONTRACT_400_HANDOVER_REQUIRED` (400)<br>`CONTRACT_400_CHECKOUT_BED_STATUS` (400)<br>`CONTRACT_400_TERMINATED` (400)<br>`ROOM_404` / `BED_404` / `TENANT_404` (404)<br>`WEB_400_VAL` (400) |
| `POST` | `/api/v1/admin/contracts/{id}/documents` | `MANAGER` | Path: `id` (`UUID`)<br>Multipart `form-data`:<br>• `category` (`ContractDocumentCategory`: `ORIGINAL_CONTRACT`, `RENEWAL_ANNEX`, `CHECKOUT_HANDOVER`, `OTHER`)<br>• `renewalId` (optional `UUID`: required & bound to contract `id` when `category == RENEWAL_ANNEX`; must be `null` otherwise)<br>• `files` (`List<MultipartFile>`: max 10 MB/file, extension + MIME + magic bytes validated for `.pdf`, `.doc`, `.docx`, `.jpg`, `.jpeg`, `.png`, `.webp`) | `200 OK` → `SingleResponse<List<ContractDocumentResponse>>` (`id`, `contractId`, `renewalId`, `fileName`, `fileUrl` [`/api/v1/admin/contracts/{contractId}/documents/{documentId}/content`], `fileType`, `fileSize`, `documentCategory`, `uploadedAt`) | `CONTRACT_404` (404)<br>`CONTRACT_400_DOCUMENT_RENEWAL` (400)<br>`CONTRACT_400_FILE_SIZE` (400)<br>`CONTRACT_400_FILE_FORMAT` (400)<br>`WEB_400_VAL` (400) |
| `DELETE` | `/api/v1/admin/contracts/{id}/documents/{documentId}` | `MANAGER` | Path: `id` (`UUID`), `documentId` (`UUID`) | `200 OK` → `SuccessResponse` (deletes DB row; schedules S3 object deletion in `afterCommit()` so rolled-back deletes do not remove stored bytes) | `CONTRACT_404` (404)<br>`CONTRACT_404_DOCUMENT` (404)<br>`WEB_401` (401) / `WEB_403` (403) |
| `GET` | `/api/v1/admin/contracts/{contractId}/documents/{documentId}/content` | `MANAGER` | Path: `contractId` (`UUID`), `documentId` (`UUID`) *(supports `GET` & `HEAD`; no `httpMethod` OpenAPI param)* | `200 OK` → Binary `Resource` stream (`Content-Type`, `Content-Length`, `Content-Disposition: inline; filename="..."`; `HEAD` closes `ManagedStreamResource` unconsumed) | `CONTRACT_404_DOCUMENT` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |

---

### 2.3. Tenant Contract Views & Streaming (`TenantContractController`)

| HTTP Method | Route | Authorization | Request Params / Constraints | Success Status & Envelope Payload | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/tenant/contracts/me` | `TENANT` | Bearer token (`@AuthenticationPrincipal UserPrincipal`) | `200 OK` → `SingleResponse<ContractDetailResponse>` (tenant-scoped `/api/v1/tenant/.../content` `fileUrl`s; `data: null` when tenant has no active contract / is `CHECKED_OUT`) | `TENANT_404` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` | `/api/v1/tenant/contracts` | `TENANT` | Bearer token (`ACTIVE` or `CHECKED_OUT` tenant) | `200 OK` → `SingleResponse<List<ContractDetailResponse>>` (all own `ACTIVE` and `LIQUIDATED` contracts ordered by `startOn DESC, createdAt DESC`, including `renewals` and `documents` with tenant-scoped `fileUrl`s) | `TENANT_404` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` | `/api/v1/tenant/contracts/{id}` | `TENANT` | Path: `id` (`UUID`), Bearer token (`ACTIVE` or `CHECKED_OUT` owning tenant) | `200 OK` → `SingleResponse<ContractDetailResponse>` (returns `404 CONTRACT_404` if contract does not exist or belongs to another tenant) | `CONTRACT_404` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |
| `GET` | `/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content` | `TENANT` | Path: `contractId` (`UUID`), `documentId` (`UUID`) *(supports `GET` & `HEAD`; no `httpMethod` OpenAPI param; `ACTIVE` or `CHECKED_OUT` owner)* | `200 OK` → Binary `Resource` stream (`Content-Type`, `Content-Length`, `Content-Disposition: inline; filename="..."`; cross-tenant or cross-contract IDs fail closed with `404`) | `CONTRACT_404` (404)<br>`CONTRACT_404_DOCUMENT` (404)<br>`WEB_401` (401)<br>`WEB_403` (403) |

---

## 3. Domain Error Code Matrix (`ContractErrorCode`)

All **19** enum entries verified directly from `src/main/java/com/uit/dormmanagement/exceptions/error/ContractErrorCode.java`:

| # | Enum Constant | `errorCode` | HTTP Status | Backend Message | Trigger Condition |
|---|---|---|---|---|---|
| 1 | `CONTRACT_NOT_FOUND` | `CONTRACT_404` | `404 NOT_FOUND` | Không tìm thấy hợp đồng tương ứng | Contract `id` missing, or tenant requests/streams a contract owned by another tenant (`404` isolation) |
| 2 | `CONTRACT_ALREADY_TERMINATED` | `CONTRACT_400_TERMINATED` | `400 BAD_REQUEST` | Hợp đồng đã chấm dứt hiệu lực hoặc đã thanh lý | Attempting `renewContract` or `terminateContract` on an already `LIQUIDATED` contract |
| 3 | `BED_NOT_AVAILABLE` | `CONTRACT_400_BED_UNAVAILABLE` | `400 BAD_REQUEST` | Giường không ở trạng thái khả dụng để tạo hợp đồng | Onboarding onto a bed whose status `!= AVAILABLE`, or renewing when the contract's bed is `!= OCCUPIED` |
| 4 | `GENDER_MISMATCH` | `CONTRACT_400_GENDER_MISMATCH` | `400 BAD_REQUEST` | Giới tính của khách thuê không khớp với chính sách giới tính của phòng | Onboarding request `gender != room.genderPolicy` |
| 5 | `INVALID_CONTRACT_DATES` | `CONTRACT_400_DATES` | `400 BAD_REQUEST` | Khoảng thời gian hiệu lực của hợp đồng không hợp lệ | Onboarding `startDate != today` or `endDate` not month-end/`<= startDate`; renewal `newEndDate` missing, `<= currentEndDate`, not month-end, or `effectiveFrom` not day `01` |
| 6 | `ONBOARDING_ONLY_ON_DAY_ONE` | `CONTRACT_400_DAY_ONE_ONLY` | `400 BAD_REQUEST` | Chỉ được thực hiện tiếp nhận khách vào ngày 01 hằng tháng theo múi giờ cơ sở | Executing `POST /api/v1/admin/onboarding` when local date in `Asia/Ho_Chi_Minh` has `dayOfMonth != 1` |
| 7 | `INVALID_DEPOSIT_AMOUNT` | `CONTRACT_400_DEPOSIT` | `400 BAD_REQUEST` | Tiền đặt cọc phải là số nguyên VND không âm | `depositAmount` is `null`, `< 0`, has non-zero fractional scale, or exceeds `numeric(14,0)` |
| 8 | `BED_ALREADY_RENTED` | `CONTRACT_409_BED_OCCUPIED` | `409 CONFLICT` | Giường đang có hợp đồng chiếm chỗ còn hiệu lực | Bed already has an `ACTIVE` contract (`existsByBedIdAndStatus` or `uq_contracts_active_bed` race) |
| 9 | `TENANT_ALREADY_HAS_ACTIVE_CONTRACT` | `CONTRACT_409_TENANT_ACTIVE` | `409 CONFLICT` | Khách thuê đã có hợp đồng chiếm chỗ còn hiệu lực | Returning tenant is still `ACTIVE` or has an `ACTIVE` contract (`uq_contracts_active_tenant` race) |
| 10 | `TENANT_HAS_UNSETTLED_INVOICES` | `CONTRACT_409_UNSETTLED_DEBT` | `409 CONFLICT` | Khách thuê còn hóa đơn chưa tất toán (Nợ hoặc Chờ xác nhận) | Returning tenant onboarding or contract renewal attempted while tenant has any `DEBT` or `PENDING_CONFIRMATION` invoice (`BR-04 §6`) |
| 11 | `ROOM_ARCHIVED` | `CONTRACT_409_ROOM_ARCHIVED` | `409 CONFLICT` | Phòng đang ở trạng thái lưu trữ, không thể tiếp nhận khách mới | Target room has `archivedAt != null` during onboarding or renewal |
| 12 | `CONTRACT_EXPIRED` | `CONTRACT_409_EXPIRED` | `409 CONFLICT` | Hợp đồng đã quá hạn, không thể gia hạn | Renewal attempted when `today` (`Asia/Ho_Chi_Minh`) is strictly after `currentEndDate` |
| 13 | `CONTRACT_RENEWAL_CONFLICT` | `CONTRACT_409_RENEWAL_CONFLICT` | `409 CONFLICT` | Xung đột đồng thời khi gia hạn hợp đồng, vui lòng thử lại | Concurrent renewal conflict hitting `uq_contract_renewals_sequence` or `uq_contract_renewals_effective` |
| 14 | `DOCUMENT_NOT_FOUND` | `CONTRACT_404_DOCUMENT` | `404 NOT_FOUND` | Không tìm thấy tài liệu hợp đồng tương ứng | Document `id` missing, belongs to a different `contractId`, blank `storageFileKey`, or missing in S3 |
| 15 | `DOCUMENT_FILE_SIZE_EXCEEDED` | `CONTRACT_400_FILE_SIZE` | `400 BAD_REQUEST` | Kích thước tài liệu đính kèm vượt quá giới hạn cho phép | Uploaded contract/annex document exceeds `10 MB` (`10 * 1024 * 1024` bytes) |
| 16 | `UNSUPPORTED_DOCUMENT_FORMAT` | `CONTRACT_400_FILE_FORMAT` | `400 BAD_REQUEST` | Định dạng tập tin tài liệu không được hỗ trợ | Empty/null/missing filename, disallowed extension, mismatched declared MIME, or magic-byte mismatch (allowed: `.pdf`, `.doc`, `.docx`, `.jpg`, `.jpeg`, `.png`, `.webp`) |
| 17 | `INVALID_DOCUMENT_RENEWAL_LINK` | `CONTRACT_400_DOCUMENT_RENEWAL` | `400 BAD_REQUEST` | Thông tin liên kết phụ lục gia hạn của tài liệu không hợp lệ | `RENEWAL_ANNEX` uploaded without `renewalId`, `renewalId` missing/belonging to another contract, or non-annex category uploaded with `renewalId` |
| 18 | `INVALID_CHECKOUT_BED_STATUS` | `CONTRACT_400_CHECKOUT_BED_STATUS` | `400 BAD_REQUEST` | Trạng thái giường sau bàn giao phải là AVAILABLE hoặc MAINTENANCE | Checkout `bedStatusAfterCheckout` is not `AVAILABLE` or `MAINTENANCE` (e.g., `OCCUPIED`) |
| 19 | `CHECKOUT_HANDOVER_NOT_CONFIRMED` | `CONTRACT_400_HANDOVER_REQUIRED` | `400 BAD_REQUEST` | Phải xác nhận đã hoàn tất bàn giao phòng trước khi thanh lý hợp đồng | Checkout request missing or `handoverConfirmed != true` |

**Cross-Domain Error Codes Surfaced on Phase 3 Endpoints:**
- `TenantErrorCode`: `TENANT_404` (404), `TENANT_400_NOT_ACTIVE` (400), `TENANT_409_EMAIL` (409), `TENANT_409_ID_CARD` (409).
- `RoomErrorCode`: `ROOM_404` (404), `BED_404` (404).
- `WebErrorCode`: `WEB_400_VAL` (400 bean validation), `WEB_401` (401 unauthenticated), `WEB_403` (403 wrong role).
- `SystemErrorCode`: `SYS_500` (500 file read error), `SYS_502` / `EXTERNAL_SERVICE_ERROR` (OCI S3 failure).

---

## 4. Frontend Prototype Compatibility Matrix (`/home/chinh/Projects/dorm-management-prototype`)

Inspected read-only on branch `feature/backend-api-integration` (`d87a76d`). In `src/services/api/apiDormService.ts` (L26, L41–L45), contract and onboarding methods (`getContracts`, `onboard`, `renewContract`, `checkoutContract`, `addContractDocuments`, `deleteContractDocument`) currently throw placeholder errors (`"API ... chưa được thống nhất."`), while full reference business logic is prototyped in `src/services/mock/contractLifecycle.ts`.

| Prototype Component / Service | Prototype Flow & Source Lines | Target Backend Endpoint(s) | Compatibility & Required FE Adapter Mapping |
|---|---|---|---|
| **`TenantsPage.tsx` (Onboarding Modal)** | `createTenant` (L115–L134), Modal form (L287–L452), `createdAccountModal` (L574–L637); `OnboardingInput` (`name`, `gender` `"Nam"/"Nữ"`, `phone`, `cccd`, `email`, `address`, `bedId`, `startDate`, `endDate`, `deposit`) | `POST /api/v1/admin/onboarding` | **`FE_ADAPTER_REQUIRED` (Semantic Match):**<br>• Map `name`→`fullName`, `"Nam"/"Nữ"`→`MALE/FEMALE`, `cccd`→`idCardNumber`, `address`→`permanentAddress`, `deposit`→`depositAmount`.<br>• Returning-tenant lookup by CCCD & conditional `createdAccountModal` (`if (result.initialPassword)`) match Backend `GeneratedAccountInfo.initialPassword` (`string` vs `null`).<br>• Toast (`"Đã tiếp nhận và phát hành hóa đơn tháng đầu."`) aligns with `OnboardingResponse.firstInvoice`. |
| **`ContractsPage.tsx` (Contracts List & Stats)** | Status badges & filters (L148–L222), Table (L224–L302) showing `id`, `tenantName`, `room`, `bed`, `startDate`, `endDate`, `monthlyRent`, `deposit`, `status` | `GET /api/v1/admin/contracts` | **`FE_ADAPTER_REQUIRED`:**<br>• Backend `ContractSummaryResponse` separates `id` (UUID) and `contractCode` (`HD-YYYY-NNNN`), whereas the mock prototype uses `item.id` as display code.<br>• Backend `ContractStatus` has 2 persisted values (`ACTIVE`, `LIQUIDATED`); UI renders 3 display statuses (`Hiệu lực`, `Sắp hết hạn`, `Đã thanh lý`). FE adapter computes `"Sắp hết hạn"` client-side when `status === "ACTIVE" && endDate <= today + 30 days`. |
| **`ContractsPage.tsx` (Detail Drawer & Renewals Tab)** | Drawer (L305–L408), Renewals list (L411–L456) rendering `renewal.signedAt`, `renewal.startDate → renewal.endDate`, `renewal.monthlyRent` | `GET /api/v1/admin/contracts/{id}` | **`FE_ADAPTER_REQUIRED`:**<br>• Map `ContractRenewalResponse` fields: `effectiveFrom` → `startDate`, `newEndOn` → `endDate`, `signedOn` → `signedAt`.<br>• Both sides snapshot original contract terms and append immutable renewal rows. |
| **`ContractsPage.tsx` (Documents Tab)** | `uploadFiles` (L105–L135), `handleDeleteDoc` (L137–L146), UI (L459–L590): categories (`ORIGINAL_CONTRACT`, `RENEWAL_ANNEX`, `CHECKOUT_HANDOVER`, `OTHER`), `renewalId` select, extensions `.jpg/.jpeg/.png/.webp/.pdf/.doc/.docx`, 10 MB limit | `POST /api/v1/admin/contracts/{id}/documents`<br>`DELETE /api/v1/admin/contracts/{id}/documents/{documentId}`<br>`GET /api/v1/admin/contracts/{contractId}/documents/{documentId}/content` | **`FE_ADAPTER_REQUIRED`:**<br>• Categories, allowed extensions, 10 MB limit, and `renewalId` linkage match Backend 1:1.<br>• Replace mock `FileReader` base64 Data URL with `FormData` (`category`, optional `renewalId`, `files`).<br>• Require non-empty `renewalId` in UI when `category === "RENEWAL_ANNEX"` (Backend returns `400 CONTRACT_400_DOCUMENT_RENEWAL` when omitted).<br>• For preview/open/download, fetch `doc.fileUrl` (`/api/v1/admin/contracts/.../content`) with `Authorization: Bearer <token>` into a `Blob` object URL rather than plain `<img src>`/`<a href>`. |
| **`ContractsPage.tsx` (Renew Action)** | `handleRenew` (L84–L91), Form (L605–L633), Modal (L678–L710) taking `endDate` | `POST /api/v1/admin/contracts/{id}/renew` | **`FE_ADAPTER_REQUIRED`:**<br>• Backend expects `multipart/form-data` with `newEndDate` and optional `annexDocuments` (`List<MultipartFile>`). Prototype sends `(id, endDate)` without inline annex files; FE adapter can post `FormData` with `newEndDate` alone or enhance modal with optional file picker. |
| **`ContractsPage.tsx` (Checkout Action)** | `handleCheckout` (L93–L103), Form (L636–L669), Modal (L713–L745) selecting `bedStatus` in `AVAILABLE \| MAINTENANCE` | `POST /api/v1/admin/contracts/{id}/terminate` | **`DIRECT_MATCH` (with `handoverConfirmed`):**<br>• Backend `CheckoutContractRequest` accepts `@JsonAlias("bedStatus")` for `bedStatusAfterCheckout` in `{AVAILABLE, MAINTENANCE}` and requires `handoverConfirmed: true` (sent when the confirmation modal is confirmed). |
| **`RoomPage.tsx` & `TenantPortalPage.tsx` (Tenant Contracts & Docs)** | `RoomPage` (L12–L49, L187–L229, L281–L382) active contract & documents modal; `TenantPortalPage` (L59–L78, L292–L340) checked-out guard & `"Lịch sử hợp đồng"` section | `GET /api/v1/tenant/contracts/me`<br>`GET /api/v1/tenant/contracts`<br>`GET /api/v1/tenant/contracts/{id}`<br>`GET /api/v1/tenant/contracts/{contractId}/documents/{documentId}/content` | **`FE_ADAPTER_REQUIRED`:**<br>• `GET /api/v1/tenant/contracts/me` returns `data: null` when checked out; `GET /api/v1/tenant/contracts` returns all `ACTIVE` and `LIQUIDATED` contracts for both active and `CHECKED_OUT` tenants (powering `"Lịch sử hợp đồng"`).<br>• Tenant documents modal/links use `fileUrl` (`/api/v1/tenant/contracts/.../content`) via Bearer-authenticated `fetch` → `Blob` URL and map `contractCode` for display. |

---

## 5. Verification Suites & Preserved Deferred Risk Ledger

### 5.1. Phase 3B & Contract Verification Suites (`Task 3B-6`)

| Test Suite | Count | Scope Verified |
|---|---|---|
| `OpenApiContractVerificationTest` | `1` | Live `GET /v3/api-docs` verification for all Phase 2 AND all 12 Phase 3 Onboarding & Contract endpoints, absence of `PUT /api/v1/admin/vehicles/{id}`, and absence of `httpMethod` query param across all 6 streaming endpoints |
| `ResidentLifecycleE2ETest` | `1` | End-to-end SRS v3.4.1 Phase 3 flow: Day-01 rule + Onboarding (`FR-09`, `T03`) → Vehicle, RFID card, quota & pending change request (`FR-08`) → Document upload, debt-blocked renewal, day-25+ `T05` catch-up invoice, `renewalId` annex upload & `ContractStatusScheduler` (`FR-10`, `FR-12`, `T04`, `T05`) → `MAINTENANCE` checkout, vehicle revocation, pending request cancellation & debt preservation (`FR-13`, `T07`) → Post-checkout tenant contract history & PDF byte streaming → Physical card reuse, debt-blocked re-onboarding & clean Day-01 re-onboarding (`BR-04 §6`) |
| `ContractDocumentSecurityIntegrationTest` | `10` | `GET`/`HEAD` admin & tenant streaming (`ACTIVE` + `CHECKED_OUT`), RBAC/cross-tenant/cross-contract fail-closed `404`, `/uploads/**` `denyAll()`, commit/rollback S3 delete, tenant contract history & detail isolation |
| `OnboardingSecurityIntegrationTest` | `4` | Anonymous `401`, `TENANT`/`TECHNICIAN` `403`, `MANAGER` `201` + `firstInvoice`, validation & date `400` |
| `ContractServiceImplTest` | `28` | Renewal chaining, price lock, expiry boundary, checkout lock order, file validation (magic bytes, MIME, size, `renewalId`), rollback/post-commit S3 cleanup, tenant history |
| `OnboardingServiceImplTest` | `15` | Day-01 timezone boundaries, deposit & date checks, preconditions, returning tenant reuse & debt block, log masking (`ListAppender`) |
| `InvoiceIssuanceServiceImplTest` | `10` | `TimeConfig.DORM_ZONE`, unsettled debt detection (`DEBT`/`PENDING_CONFIRMATION`), idempotent monthly issuance, day-25+ catch-up boundary |
| `ContractControllerTest` & `OnboardingControllerTest` | `19` + `11` | MVC serialization, `@JsonAlias("bedStatus")`, `renewalId` forwarding, `HEAD` closeable resource handling, all error mappings |
| `ContractRenewalPostgresTest` & `ContractCheckoutPostgresTest` | `7` + `3` | Real PostgreSQL 16 Testcontainers + Flyway `V1` validation for renewal chaining, `T05` catch-up, `renewalId` FK/CHECK constraints, scheduler query, checkout transitions & repeat-checkout guard |
| `OnboardingPostgresTest` & `InvoiceIssuancePostgresTest` | `5` + `3` | Real PostgreSQL rollback atomicity, returning tenant reuse, archived room `409`, partial unique indexes (`uq_contracts_active_bed`, `uq_contracts_active_tenant`), Flyway invoice constraints |
| `ContractStatusSchedulerTest` | `2` | `Asia/Ho_Chi_Minh` boundary, read-only transaction, effective end date query, non-mutation of `Contract.status` |
| `OciS3LiveSmokeTest` | `1` *(live)* | Live OCI Object Storage S3 private bucket round-trip (`putObject` → `getObject` → `HEAD`/close → `deleteObject`) when `OCI_SMOKE_TEST_ENABLED=true` |

### 5.2. Preserved Phase 2 Deferred Findings Ledger (Untouched)

The following Phase 2 review findings remain explicitly **`DEFERRED / ACCEPTED RISK`** per owner decision and were **NOT** modified in Phase 3A or Phase 3B (`3B-1`..`3B-6`):
- **`F-01` (`DEFERRED / ACCEPTED RISK`):** Old-email password recovery challenge race vs. Manager email correction (`PasswordRecoveryServiceImpl`).
- **`F-04` (`DEFERRED / ACCEPTED RISK`):** Non-atomic Redis login failure counter/TTL/lock (`LoginAttemptServiceImpl`).
- **`F-06` (`DEFERRED / ACCEPTED RISK`):** Non-atomic missing Redis session generation initialization (`UserTokenInvalidationServiceImpl`).
- **`F-07` (`DEFERRED / ACCEPTED RISK`):** Undelivered OTP challenge retained in Redis after email delivery failure (`PasswordRecoveryServiceImpl`).

### 5.3. Branch & Delivery Status Notice

All Phase 2 Hardening, Phase 3A storage/streaming, and Phase 3B (`3B-1`..`3B-6`) work is committed **locally only** on branch `feat/orca-phase3-final`. No merge, push, or deployment has been performed. Phase 3B stops here for owner approval prior to Phase 4.
