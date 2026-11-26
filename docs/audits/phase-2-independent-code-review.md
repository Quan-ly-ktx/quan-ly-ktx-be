# Phase 2 independent backend code review

**Date:** 2026-11-16  
**Verdict:** `CHANGES_REQUIRED`  
**Basis:** Local working tree at `ac525f28bebda83efa03007252bdfb1c7cf4a88f`, branch `refactor/erd-and-implemented-code`. No implementation files were changed by this review.

## 1. Executive summary

The implementation covers the intended four Phase 2 domains, retains the Phase 1 schema and entities, and passes a fresh 339-test Maven run. The report from AGY is inaccurate about login identifiers: actual login resolves staff by email and tenants by CCCD; it does not resolve phone or student ID. The API contract inventory repeats that inaccurate claim and contains numerous other client-facing errors.

Acceptance should wait for the confirmed recovery race and vehicle evidence transaction defect. Several smaller gaps concern exact validation, failure handling, privacy of logs, and contract documentation. The approved simple architecture is suitable; none of the findings calls for a new session store, distributed lock, or extra workflow.

## 2. Repository and working-tree baseline

`git branch --show-current` returned `refactor/erd-and-implemented-code`; `git rev-parse HEAD` returned the specified pre-implementation SHA. `git status --short` showed **66 modified, 3 deleted, and 63 untracked path entries** (the last count includes the untracked test-support directory). There were no staged changes. I inspected `git diff HEAD`, `git diff --cached`, the untracked inventory, the relevant source and tests, and `git diff --check` (clean). The changes are in Phase 2 application code, tests, `.gitignore`, and Phase 2 documents. No Flyway migration or entity mapping changed. No Onboarding/Checkout or Phase 3–5 implementation file changed. Three deleted files are superseded request DTOs, not tests. Existing test changes adapt to the new private-evidence and pending-card contracts; no test deletion was found.

Review authority: `docs/SRS.md` v3.4.1, both final ERDs, the current-state audit, the five 2026-11-16 designs, five 2026-11-16 plans, handoff, and API inventory. Older Superpowers plans were not used as acceptance authority.

## 3. Complete 28-task compliance matrix

`PASS` means the inspected implementation and available tests support the task; it does not imply the entire domain is defect-free. Finding IDs below explain any downgrade.

| Task | Result | Implementation and evidence |
|---|---|---|
| AUTH-1 | PARTIAL | Sixth-attempt lock exists in `LoginAttemptServiceImpl`; counter/TTL/lock are separate Redis operations, and CCCD is logged in full (F-05). |
| AUTH-2 | PARTIAL | Generation claim, Redis check and invalidation exist; missing-key generation creation is non-atomic (F-06). |
| AUTH-3 | PASS | Refresh validates signed type, User, generation and active JTI; sequential reuse returns invalid token without global invalidation; logout checks both tokens and ownership. |
| AUTH-4 | PASS | Logout-all, password changes/resets and Technician lock rotate generation; reactivation does not restore prior tokens. |
| AUTH-5 | PARTIAL | User-bound challenge and reset token exist; recovery request lacks the agreed User lock and failed email send retains challenge (F-01, F-07). |
| AUTH-6 | PASS | Server-generated Technician/assisted passwords are returned once and stored as hashes. |
| AUTH-7 | PASS | Real-filter security tests cover anonymous, wrong role, locked/revoked tokens; 8 tests in `AuthenticationSecurityIntegrationTest`, not the 13 claimed by inventory. |
| ROOM-1 | PASS | 4/6/8 Bed creation, registry, amenities and derived counts are implemented and tested. |
| ROOM-2 | PASS | Occupancy-sensitive mutation locks Room then ordered Beds; rename updates slot keys; historical code check is present. |
| ROOM-3 | PASS | Manual Bed command locks only Bed, rejects occupied transitions and active-Contract maintenance. |
| ROOM-4 | PASS | Archive, separate archived list and restore exist; IDs/history retained. |
| ROOM-5 | PASS | Unfiltered lookup and history guard limit physical deletion to unused Rooms. |
| ROOM-6 | PASS | Room/Bed security-chain and PostgreSQL tests run; Phase 3 Onboarding lock adoption remains deferred. |
| TEN-1 | PARTIAL | Actor-specific DTOs, lock queries and errors exist; optional blank identity fields are silently ignored (F-08). |
| TEN-2 | PARTIAL | Manager correction locks User then Tenant, protects Room gender, preserves snapshots; uniqueness races fall to generic 400 rather than required domain 409 (F-09). |
| TEN-3 | PASS | Tenant contact PATCH is owner-derived and works after checkout; identity fields are absent from the DTO. |
| TEN-4 | PASS | Assisted reset generates a password and invalidates tokens/recovery credentials. |
| TEN-5 | PARTIAL | Security and uniqueness tests exist, but they do not close F-08/F-09. |
| VEH-1 | PASS | Five types, plate/evidence matrix, 10 MiB limit and JPEG/PNG/WebP header checks are implemented. |
| VEH-2 | PARTIAL | New evidence is private and owner-gated; update/rollback file handling is unsafe (F-02). |
| VEH-3 | PASS | Manager/Tenant registration creates `PENDING_CARD`, enforces active-resident quota and preserves historical records. |
| VEH-4 | PARTIAL | Six Vehicle states and primary transitions are implemented; issue-card uniqueness race has wrong HTTP mapping (F-09). |
| VEH-5 | PASS | Tenant-owned pending Change Requests, history and duplicate-pending protection exist. |
| VEH-6 | PASS | Approval locks Vehicle then Request, rechecks state, flushes old `REPLACED` before inserting successor and transfers card code in one DB transaction. |
| VEH-7 | PARTIAL | Protected evidence endpoints and security tests exist; F-02 remains. |
| REG-1 | PASS | Fresh Testcontainers PostgreSQL migration and mapping checks ran; no Phase 1 migration/entity diff. |
| REG-2 | PARTIAL | Cross-module/security suites ran, but do not exercise the confirmed recovery and file-rollback paths. |
| REG-3 | PARTIAL | Fresh compile and 339/339 tests pass; contract inventory is materially inaccurate (F-03). |

No task is marked `DEFERRED`: all 28 entries are Phase 2 tasks. Deferred Phase 3–5 work is listed separately below.

## 4. Authentication and RBAC findings

**F-01 — MAJOR — FR-24, BR-09: old-email recovery race.** [PasswordRecoveryServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/PasswordRecoveryServiceImpl.java:68) lines 68–90 resolves the User and captures the destination email, then reads the recovery version and creates/sends a challenge without taking the User row lock. [TenantServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java:111) lines 111–118 and 147–177 locks the User, changes email and advances that version. Expected: request and email correction serialize on the same User lock, so a challenge sent to the old address cannot be valid after correction. Actual interleaving: recovery captures old email; Manager correction commits and increments version; recovery reads the new version and sends a valid OTP to the old address. Impact: possession of an obsolete email address can reset a corrected account. Minimal fix: lock the User during challenge creation/version capture and coordinate email send so it cannot target a stale address. Existing tests should have caught this; they assert calls, not the interleaving.

**F-04 — MINOR — FR-21, BR-09: login counter and lock are not one Redis operation.** [LoginAttemptServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/LoginAttemptServiceImpl.java:29) lines 29–43 increments, sets the counter TTL and sets the lock key in separate commands. Expected by the approved Auth design: the sixth failure creates the 15-minute lock with an atomic counter/TTL/lock operation. Actual: a Redis/process failure between commands can leave a counter without TTL or a sixth failure without a lock. Impact: the consecutive-failure window or rate limit can deviate from the SRS after partial failure. Minimal fix: one Redis script or equivalent atomic operation. Existing mock-based tests do not cover partial Redis operations.

**F-05 — MINOR — FR-21, BR-09: full login identifier in logs.** [LoginAttemptServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/LoginAttemptServiceImpl.java:35) lines 35–46 logs `identifier` on every failed attempt and lock, including the complete 12-digit CCCD. Expected: mask or hash the identifier in operational logs, consistent with SRS privacy and `AuthServiceImpl`'s own masking. Actual: every failed CCCD login writes the full CCCD. Impact: unnecessary sensitive identifier exposure to log readers/retention. Minimal fix: use a masked value. Existing tests do not inspect logs.

**F-06 — MINOR — FR-21/FR-22, BR-09: generation creation is not atomic.** [UserTokenInvalidationServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/UserTokenInvalidationServiceImpl.java:28) lines 28–42 performs Redis GET then unconditional SET when absent. Expected: create only if absent and use the effective stored generation. Actual: two concurrent first logins can each mint a different generation; the later SET invalidates the first successful login immediately. The approved design explicitly requires atomic missing-key creation. Minimal fix: Redis set-if-absent with TTL, then read the winner. Existing single-threaded tests cannot catch this.

**F-07 — MINOR — FR-24: undelivered OTP remains usable.** [PasswordRecoveryServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/PasswordRecoveryServiceImpl.java:83) lines 83–96 stores the challenge before email delivery and logs delivery failure without deleting it. Expected: failed delivery invalidates the challenge while preserving the generic 200 response. Actual: an undelivered challenge remains live for five minutes. Impact: confusing recovery state and a still-valid credential if an email provider partially sent before throwing. Minimal fix: delete the challenge in the catch path and log only masked operational details. Existing tests should cover a throwing mail sender.

Login identifier review: [AuthServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/AuthServiceImpl.java:319) lines 319–330 filters Tenant email out of email lookup and resolves Tenant by ID-card number. The Phase 1 database constrains CCCD to 12 digits. Phone and student ID are not lookup keys. The `LoginRequest` validation message still says “email or phone”, but the actual login path does not support phone. BCrypt strength 12, JWT signature/expiry/type, refresh registry, generic login errors, current User reload and generation checks are present. The real security-chain tests establish 401/403 and cross-tenant boundaries; they do not prove every concurrency or recovery failure path.

## 5. Room and Bed findings

No confirmed Room/Bed invariant violation was found in the inspected paths. Create is transactional; room-code registry and Bed slot keys update together; occupied/active-Contract guards and history deletion checks are present. The `Room → Bed` lock order is followed by Room mutation commands; the Bed-status command is a Bed-only leaf. The existing Phase 3 Onboarding path has not adopted this order, so cross-phase concurrency is **not** guaranteed today. This is a deferred integration requirement, not a Phase 2 finding.

## 6. Tenant profile findings

**F-08 — MINOR — FR-06: supplied blank optional identity is silently accepted.** [TenantServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java:137) lines 137–157 skips `idCardNumber` and `email` when blank. [ManagerUpdateTenantRequest.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/dtos/request/tenant/ManagerUpdateTenantRequest.java:15) lines 15–21 uses `@Pattern`/`@Email`, which permit empty strings. Expected per approved Tenant design: omitted/null retains identity; supplied blank is a validation error. Actual: `"idCardNumber":""` or `"email":""` returns success while retaining old data. Impact: clients receive false success for invalid corrections. Minimal fix: reject blank supplied identity fields in DTO/service. Existing DTO tests should have caught this.

Manager and self-service DTOs otherwise separate identity from contact fields. Manager updates lock User before Tenant and do not update Contract snapshots. Checked-out Tenant contact access is retained. Phone is not treated as globally unique.

## 7. Vehicle and evidence findings

**F-02 — MAJOR — FR-08, BR-08/BR-11: evidence files are deleted before DB commit.** [VehicleServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/VehicleServiceImpl.java:171) lines 171–207 writes replacement files, changes DB keys, then deletes old files before the enclosing `@Transactional` method commits. If DB flush/commit later fails, the row rolls back to the old key while its file is gone. If the second file write fails after the first succeeds, the first new file is left orphaned. Expected by the VEH-4 plan: retain old keys through rollback; delete replaced/cleared files only after successful commit; compensate newly written files on failure. Impact: permanent loss of private evidence or orphaned files. Minimal fix: register old-file deletion after commit and new-file cleanup on rollback/failure, including partial two-file writes. Existing service/storage tests should have caught both paths.

The replacement approval itself uses the documented Vehicle → Request order and explicitly flushes the old `REPLACED` row before inserting the successor. PostgreSQL tests verify partial uniqueness and rollback. `CANCELLED` Change Requests are intentionally assigned to Phase 3 checkout. All evidence responses use owner/Manager checks; `/uploads/vehicles/**` is denied. The service stores only Card Codes and does not implement RFID hardware, readers, barriers or scanning; “RFID” appears in controller text and a report, not in an integration.

## 8. Cross-cutting security and data integrity

**F-09 — MINOR — FR-02/FR-06/FR-08, BR-08: expected uniqueness races return generic 400.** [AppExceptionHandler.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/exceptions/AppExceptionHandler.java:174) lines 174–180 maps all `DataIntegrityViolationException` to `WebErrorCode.BAD_REQUEST`. [TenantServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java:137) lines 137–155 and [VehicleServiceImpl.java](/home/chinh/Projects/dormitory-management/src/main/java/com/uit/dormmanagement/services/impl/VehicleServiceImpl.java:240) lines 240–250 precheck unique values but do not translate a database race; Room creation/rename has the same precheck pattern. Expected: duplicate CCCD/email, protected Room Code and issued Card Code conflicts yield stable domain 409. Actual: a concurrent insert/update between precheck and flush reaches the global generic 400 handler. Impact: client cannot distinguish a business conflict from malformed input; inventory's 409 contract is broken under a real race. Minimal fix: translate the known constraint names at each boundary to the existing domain conflict code. Existing tests cover sequential duplicates, not constraint-race mapping.

No license-plate uniqueness was introduced. No application code outside the approved Phase 2 modules changed. The untouched `WebMvcResourceConfig` still serves general `/uploads/**`, but new Vehicle storage defaults to `private-storage/vehicles`, outside that resource handler; no unauthorized alternate Vehicle route was identified. Private object keys are absent from Vehicle response DTOs. Tests use isolated PostgreSQL/Redis containers where persistence or Redis is required.

## 9. API inventory consistency

**F-03 — MAJOR — FR-01–FR-08, FR-21–FR-24: the API inventory is materially inaccurate.** [phase-2-api-contract-inventory.md](/home/chinh/Projects/dormitory-management/docs/audits/phase-2-api-contract-inventory.md:33) lines 33–112 lists all endpoint paths, but repeatedly gives wrong request fields, response types, filters and error codes. Examples verified against controllers/DTOs:

| Inventory entry | Actual local contract |
|---|---|
| Login identifier “Email / Phone / Student ID”, `LoginResponse` | CCCD or staff email; `AuthResponse` in `SingleResponse` (`AuthController` lines 39–46). |
| Refresh `TokenRefreshResponse` | `AuthResponse` in `SingleResponse` (`AuthController` lines 49–56). |
| Forgot by email, masked destination; verify by identifier; reset by recovery token | Request uses `identifier`; generic request response contains `recoveryRequestId` and `expiresInSeconds`; verification uses request ID+OTP; reset uses `resetToken` (`AuthController` lines 91–118). |
| Technician create response flat `temporaryPassword`; status uses `status`; list uses `status` | Response is `CreateTechnicianResponse(technician,initialPassword)`; status DTO uses `active`; list filters `active` (`UserController`, `UserServiceImpl`). |
| Room create/update uses `amenityIds`, matrix floor groups; room list has `building/status` | DTO uses `roomType` and `amenities` with quantities; matrix returns `List<RoomDetailResponse>`; list has floor/gender/search (`RoomController` lines 43–136). |
| Manager Tenant update omits email and says phone unique | DTO includes email; phone is nonunique (`ManagerUpdateTenantRequest`, `TenantServiceImpl`). |
| Vehicle update uses `clearFront/clearBack`; registration may return license-plate duplicate | DTO uses `clearFrontEvidence/clearBackEvidence`; there is no plate uniqueness (`UpdateVehicleRequest`, Phase 1 index). |

All listed endpoint paths were found in the local controllers. The Room physical-delete alias `/api/v1/admin/rooms/{id}/incorrect` exists but is omitted. Error-code tables also use names/statuses that differ from `AuthErrorCode`, `RoomErrorCode`, `TenantErrorCode`, `VehicleErrorCode` and the global handler. The inventory's test counts for individual suites are inaccurate, although its **total** is correct. Expected: an endpoint inventory from which Frontend can implement without guessing. Impact: wrong login UI, broken recovery flow and request binding errors if used as written. Minimal fix: regenerate/review the inventory against controller mappings, DTO fields, envelopes and actual error enums. An API-contract test or inventory check should have caught this.

## 10. Independent test verification

Commands run on the local working tree: `git diff --check HEAD` (clean); `./mvnw test` (**BUILD SUCCESS**, 339 tests, 0 failures, 0 errors, 0 skipped, 27.203 seconds); `./mvnw -DskipTests compile` (**BUILD SUCCESS**). Fresh Surefire reports corroborate the run. Logs show PostgreSQL 16 and Redis 7 Testcontainers starting on mapped local ports; migration tests use fresh PostgreSQL containers, and security suites use Redis Testcontainers. No shared database is required for this run. The reported 339 total is verified, but a green suite does not cover F-01/F-02 or prove the inventory correct.

## 11. Findings ranked by severity

| ID | Severity | Required action |
|---|---|---|
| F-01 | MAJOR | Serialize recovery challenge against User email correction. |
| F-02 | MAJOR | Make evidence deletion/compensation follow DB commit/rollback. |
| F-03 | MAJOR | Correct the API inventory to match the implemented contract. |
| F-04 | MINOR | Make login counter/TTL/lock atomic. |
| F-05 | MINOR | Mask login identifiers in logs. |
| F-06 | MINOR | Atomically create missing session generation. |
| F-07 | MINOR | Delete challenge after email delivery failure. |
| F-08 | MINOR | Reject supplied blank optional identity fields. |
| F-09 | MINOR | Map known database uniqueness races to domain 409. |

## 12. Phase 3–5 deferred dependencies

Phase 3 Onboarding must adopt Room → Bed lock order; Checkout must cancel pending Vehicle registrations/change requests and revoke issued cards within its workflow. Phase 4 historical finance access and Phase 5 notifications/private OCI integration remain deferred. FR-11 Room Transfer and teammate-owned Maintenance/Violation workflows are outside this review. The existing Phase 3 Onboarding code is not evidence of a Phase 2 locking guarantee.

## 13. Required fixes before acceptance

Resolve F-01, F-02 and F-03, then add focused regression checks for their failure paths. F-04 through F-09 are narrower corrections and should be addressed before the API is handed to Frontend where they affect validation/error expectations. No implementation changes were made here.

## 14. Final readiness verdict

`CHANGES_REQUIRED`. The build and 339 tests pass, and most task implementations are present, but two confirmed behavioral defects and a materially false API contract prevent acceptance. The review is complete for the local working tree; no commit or push was made.

---

## 15. Phase 2 Hardening Disposition (2026-11-26)

Disposition recorded during Phase 2 Hardening on branch `feat/orca-phase3-final` (from baseline `18e1d6e`):

| Finding ID | Disposition | Summary & Evidence |
|---|---|---|
| **F-02** | **Resolved (regression verified)** | `PUT /api/v1/admin/vehicles/{id}` remains absent in `AdminVehicleController` and OpenAPI `/v3/api-docs`; evidence upload rollback/cleanup verified in `VehicleServiceImplTest`, `VehicleChangeRequestServiceTest`, `VehicleControllerTest`, `LocalVehicleEvidenceStorageTest`, and `OpenApiContractVerificationTest`. |
| **F-03** | **Fixed in Phase 2 Hardening** | Synchronized `docs/audits/phase-2-api-contract-inventory.md`, `docs/audits/phase-2-api-prototype-compatibility-audit.md`, `AGENT.md`, and `docs/ARCHITECTURE.md` with live Phase 2 controllers, DTOs, `*ErrorCode` enums, `V1__clean_baseline.sql`, and `/v3/api-docs` / Scalar (verified by `OpenApiContractVerificationTest`). |
| **F-05** | **Fixed in Phase 2 Hardening** | Added shared `LogMaskingUtils` and sanitized identifier/email/CCCD/phone logging in `LoginAttemptServiceImpl`, `CustomUserDetailsService`, `EmailServiceImpl`, `OtpServiceImpl`, `OnboardingServiceImpl`, `AuthServiceImpl`, `UserServiceImpl`, and `AppExceptionHandler`. Verified with Logback `ListAppender` regression tests. |
| **F-08** | **Fixed in Phase 2 Hardening** | Rejected supplied blank `idCardNumber` / `email` (`""` or whitespace) with HTTP `400` (`WEB_400_VAL`) in `ManagerUpdateTenantRequest` and `TenantServiceImpl.updateTenantAsManager`, while omitted/`null` identity fields retain current values and optional contact clearing (`dateOfBirth`, `emergencyContactName`, `emergencyContactPhone`) remains unchanged. Verified `TenantContactUpdateRequest` and `updateOwnContact` reject blank `phone`/`permanentAddress` (`400`) and clear blank emergency fields. |
| **F-09** | **Fixed in Phase 2 Hardening** | Added `DataIntegrityViolationClassifier` and `WebErrorCode.CONFLICT` (`WEB_409`), mapped known unique constraint/index races at service `saveAndFlush` boundaries and `AppExceptionHandler` to exact domain `409` codes, mapped unmapped `23505` to `409 WEB_409`, and preserved HTTP `400` (`WEB_400`) for NOT NULL (`23502`), CHECK (`23514`), generic FK (`23503`), data truncation (`22xxx`), and unclassified integrity violations. Verified with unit, service-race, and PostgreSQL Testcontainers tests. |
| **F-01** | **DEFERRED / ACCEPTED RISK** | Old-email password recovery race vs. Manager email correction. Known limitation accepted by owner; **NOT** implemented in Phase 2 Hardening. |
| **F-04** | **DEFERRED / ACCEPTED RISK** | Non-atomic Redis login counter/TTL/lock in `LoginAttemptServiceImpl`. Known limitation accepted by owner; **NOT** implemented in Phase 2 Hardening. |
| **F-06** | **DEFERRED / ACCEPTED RISK** | Non-atomic Redis session generation creation in `UserTokenInvalidationServiceImpl`. Known limitation accepted by owner; **NOT** implemented in Phase 2 Hardening. |
| **F-07** | **DEFERRED / ACCEPTED RISK** | Undelivered OTP challenge retained on email send failure in `PasswordRecoveryServiceImpl`. Known limitation accepted by owner; **NOT** implemented in Phase 2 Hardening. |
