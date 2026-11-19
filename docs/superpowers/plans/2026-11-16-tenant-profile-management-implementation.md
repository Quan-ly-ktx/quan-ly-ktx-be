# Tenant Profile Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate Manager identity correction from Tenant contact self-service and add Manager-assisted credential reset without absorbing onboarding or checkout.

**Architecture:** Retain `TenantServiceImpl`, Manager list/detail routes, and Phase 1 User–Tenant mappings. Add actor-specific request DTOs/methods, User→Tenant row locking for identity changes, one small Tenant self-service controller, and reuse the approved Auth recovery/session/password collaborators.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring MVC/Security, Spring Data JPA, PostgreSQL, Bean Validation, JUnit 5, Mockito, MockMvc, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-11-16-tenant-profile-management-design.md`

**Git execution policy:** Commit snippets are reference-only boundaries. An implementation agent MUST NOT stage, commit, or push unless the user or final execution prompt explicitly authorizes it.

## Global Constraints

- Preserve Manager Tenant list/detail behavior and historical User/Tenant rows.
- Manager and Tenant updates use distinct DTOs and service methods.
- Phone is never globally unique; canonical User email and 12-digit CCCD remain unique.
- Tenant self-service never accepts Tenant ID, email, name, CCCD, gender, DOB, residency, Room, Bed, price, deposit, or Contract fields.
- Checked-out Tenants may authenticate, read owned history, and update contact information; current-residency operations remain denied elsewhere.
- Do not implement Tenant creation, onboarding, returning-Tenant activation, checkout, Room Transfer, finance, or portal history APIs.
- Do not modify Flyway or Phase 1 schema.
- Authentication plan AUTH-2, AUTH-5, and AUTH-6 must be complete before assisted reset/email-recovery invalidation.

## Review Focus

- A Manager email correction racing password recovery must invalidate both OTP challenges and issued reset tokens; TEN-2 tests the shared User lock/recovery version.
- Null versus blank PUT fields must follow the exact retain/clear rules; TEN-2 adds parameterized DTO/service tests.
- A checked-out Tenant must be allowed to update contact information but not identity/residency; TEN-3 covers both positive and rejected binding cases.
- Duplicate phone numbers must remain valid while duplicate email/CCCD returns stable 409; TEN-2 and TEN-5 prove both layers.
- Assisted reset plaintext must appear exactly once and never be persisted/logged; TEN-4 tests response/hash/token invalidation.

---

### TEN-1: Add actor-specific DTOs, lock queries, and errors

**Classification:** REFACTOR foundation — FR-06/BR-09.

**Spec sections:** Tenant §3, §4.1, §5, §9–§10.

**Dependencies:** Phase 1 User–Tenant mapping; AUTH-5 owns the shared User lock method if it lands first.

**Acceptance criteria:** Actor-specific DTOs enforce exact field/null/blank rules, stable uniqueness errors exist, and User/Tenant pessimistic lock queries compile and are covered.

**Scope exclusions:** No service/controller behavior, Tenant creation, or schema change.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/tenant/ManagerUpdateTenantRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/tenant/TenantContactUpdateRequest.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/UserRepository.java:12-18`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/TenantRepository.java:16-78`
- Modify: `src/main/java/com/uit/dormmanagement/exceptions/error/TenantErrorCode.java`
- Test: `src/test/java/com/uit/dormmanagement/dtos/TenantContractVehicleDtoValidationTest.java`

**Interfaces:**
- Produces: `UserRepository.findByIdForUpdate(UUID)` and `TenantRepository.findByIdForUpdate(UUID)` using `PESSIMISTIC_WRITE`.
- Produces: exact DTO field semantics from Tenant spec §4.1 and §5.

- [ ] **Step 1: Write failing DTO validation tests**

Assert Manager required fields, 12-digit optional CCCD, canonicalizable optional email, valid optional phone fields, and blank rejection. Assert self DTO contains only four contact fields, requires at least one supplied value, rejects blank phone/address, and permits blank optional emergency values for clearing.

- [ ] **Step 2: Run DTO tests**

Run: `./mvnw -Dtest=TenantContractVehicleDtoValidationTest test`

Expected: FAIL because actor-specific DTOs do not exist.

- [ ] **Step 3: Add DTOs, lock methods, and stable error codes**

Add `TENANT_409_EMAIL`, `TENANT_409_ID_CARD`, and gender/current-Room conflict codes without changing Auth-domain codes. Keep the obsolete `UpdateTenantRequest` until TEN-2 removes its final references.

- [ ] **Step 4: Verify DTO tests**

Run: `./mvnw -Dtest=TenantContractVehicleDtoValidationTest test`

Expected: PASS.

- [ ] **Step 5: Commit the Tenant command contracts**

```bash
git add src/main/java/com/uit/dormmanagement/dtos/request/tenant/ManagerUpdateTenantRequest.java src/main/java/com/uit/dormmanagement/dtos/request/tenant/TenantContactUpdateRequest.java src/main/java/com/uit/dormmanagement/repositories/UserRepository.java src/main/java/com/uit/dormmanagement/repositories/TenantRepository.java src/main/java/com/uit/dormmanagement/exceptions/error/TenantErrorCode.java src/test/java/com/uit/dormmanagement/dtos/TenantContractVehicleDtoValidationTest.java
git commit -m "refactor: separate tenant update contracts"
```

### TEN-2: Implement Manager identity correction

**Classification:** EXTEND — FR-06.

**Spec sections:** Tenant §4, §8–§11.

**Dependencies:** TEN-1 and AUTH-5 recovery invalidation contract.

**Acceptance criteria:** Manager correction locks User→Tenant, preserves history/snapshots, applies canonical unique identity rules, enforces current Room gender, and invalidates recovery only when email changes.

**Scope exclusions:** No Tenant creation, room assignment, contract editing, or automatic status transition.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/controllers/TenantController.java:62-71`
- Modify: `src/main/java/com/uit/dormmanagement/services/TenantService.java:11-19`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java:35-93`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/ContractRepository.java`
- Remove after reference check: `src/main/java/com/uit/dormmanagement/dtos/request/tenant/UpdateTenantRequest.java`
- Test: `src/test/java/com/uit/dormmanagement/services/TenantServiceImplTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/TenantControllerTest.java`

**Interfaces:**
- Produces: `TenantDetailResponse updateTenantAsManager(UUID managerUserId, UUID tenantId, ManagerUpdateTenantRequest request)`.
- Consumes: `PasswordRecoveryService.invalidateRecoveryCredentials(UUID)` from AUTH-5.

- [ ] **Step 1: Add failing Manager-update tests**

Cover all fields; optional identity null retains; DOB/emergency null clears; blank optional identity rejects; email lowercases; duplicate email/CCCD returns the Tenant codes; duplicate phone succeeds; gender mismatch with active Contract Room returns 409; no active Contract permits gender change; Contract snapshots remain unchanged.

- [ ] **Step 2: Add User-lock/recovery race assertions**

Verify service resolves target User ID, locks User before Tenant, rechecks their relationship, and invalidates recovery credentials only when email changes. Existing Access tokens remain valid after email-only correction.

- [ ] **Step 3: Run focused tests**

Run: `./mvnw -Dtest=TenantServiceImplTest,TenantControllerTest test`

Expected: FAIL because the current update cannot change identity/email and lacks actor/locks.

- [ ] **Step 4: Implement the Manager service command**

Resolve active Manager `StaffProfile`; pre-read target only to obtain User ID; lock User then Tenant; validate/recheck; apply canonical email/CCCD and exact null semantics; validate active Room gender; save one existing User/Tenant pair. Translate database uniqueness races to 409.

- [ ] **Step 5: Update the retained route**

Keep `PUT /api/v1/admin/tenants/{id}` and its response envelope. Add authenticated Manager principal to the controller call; replace old DTO/service usages and remove `UpdateTenantRequest` only after `rg` reports no references.

- [ ] **Step 6: Verify and commit**

Run: `./mvnw -Dtest=TenantServiceImplTest,TenantControllerTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/controllers/TenantController.java src/main/java/com/uit/dormmanagement/services/TenantService.java src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java src/main/java/com/uit/dormmanagement/repositories/ContractRepository.java src/main/java/com/uit/dormmanagement/dtos/request/tenant/ManagerUpdateTenantRequest.java src/main/java/com/uit/dormmanagement/dtos/request/tenant/UpdateTenantRequest.java src/test/java/com/uit/dormmanagement/services/TenantServiceImplTest.java src/test/java/com/uit/dormmanagement/controllers/TenantControllerTest.java
git commit -m "feat: support manager tenant identity correction"
```

### TEN-3: Add Tenant contact self-service

**Classification:** IMPLEMENT — FR-06/FR-07.

**Spec sections:** Tenant §5–§6, §8, §10–§11.

**Dependencies:** TEN-1 actor-specific self-service DTO; reuses TEN-2 service conventions.

**Acceptance criteria:** The authenticated Tenant can update only owned contact fields, including after checkout, while identity/residency fields cannot bind and cross-Tenant access is hidden.

**Scope exclusions:** No portal history API, identity self-edit, current-residency command, or onboarding behavior.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/controllers/TenantProfileController.java`
- Modify: `src/main/java/com/uit/dormmanagement/services/TenantService.java`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java`
- Test: `src/test/java/com/uit/dormmanagement/services/TenantServiceImplTest.java`
- Create: `src/test/java/com/uit/dormmanagement/controllers/TenantProfileControllerTest.java`

**Interfaces:**
- Produces: `UserProfileResponse updateOwnContact(UUID tenantUserId, TenantContactUpdateRequest request)`.
- Route: `PATCH /api/v1/tenant/profile`, Tenant only.

- [ ] **Step 1: Add failing owner-only update tests**

Assert the service resolves Tenant by principal User ID, changes only supplied contact fields, clears blank emergency fields, rejects blank phone/address, permits both ACTIVE and CHECKED_OUT, and cannot bind identity/residency fields.

- [ ] **Step 2: Run focused tests**

Run: `./mvnw -Dtest=TenantServiceImplTest,TenantProfileControllerTest test`

Expected: FAIL because route/method do not exist.

- [ ] **Step 3: Implement self-service command and controller**

Lock the caller's Tenant row, apply only four contact fields, and return the existing full `UserProfileResponse` shape. The controller accepts no path Tenant ID and uses `UserPrincipal.getId()`.

- [ ] **Step 4: Verify and commit**

Run: `./mvnw -Dtest=TenantServiceImplTest,TenantProfileControllerTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/controllers/TenantProfileController.java src/main/java/com/uit/dormmanagement/services/TenantService.java src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java src/test/java/com/uit/dormmanagement/services/TenantServiceImplTest.java src/test/java/com/uit/dormmanagement/controllers/TenantProfileControllerTest.java
git commit -m "feat: allow tenant contact self service"
```

### TEN-4: Add Manager-assisted Tenant password reset

**Classification:** IMPLEMENT — FR-24.

**Spec sections:** Tenant §7–§8, §10–§11; Authentication §5.4.

**Dependencies:** AUTH-2, AUTH-5, AUTH-6, then TEN-2 service authorization conventions.

**Acceptance criteria:** Manager-assisted reset returns one generated password once, persists only its hash, rotates recovery/session state, and is limited to Tenant accounts.

**Scope exclusions:** No Technician reset duplication, email delivery, or Tenant-initiated assisted reset.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/controllers/TenantController.java`
- Modify: `src/main/java/com/uit/dormmanagement/services/TenantService.java`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java`
- Test: `src/test/java/com/uit/dormmanagement/services/TenantServiceImplTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/TenantControllerTest.java`

**Interfaces:**
- Produces: `OneTimePasswordResponse resetTenantPasswordAsManager(UUID managerUserId, UUID tenantId)`.
- Consumes: Auth `RandomPasswordGenerator`, `PasswordRecoveryService.invalidateRecoveryCredentials`, and `UserTokenInvalidationService.invalidateAll`.
- Route: `POST /api/v1/admin/tenants/{id}/reset-password`.

- [ ] **Step 1: Add failing assisted-reset tests**

Assert Manager actor/target resolution, User-first locking, policy-compliant random plaintext returned once, BCrypt hash persistence, no email send/history row, recovery invalidation, and global session invalidation. Wrong role/unknown Tenant returns 403/404.

- [ ] **Step 2: Run focused tests**

Run: `./mvnw -Dtest=TenantServiceImplTest,TenantControllerTest test`

Expected: FAIL because reset method/route is absent.

- [ ] **Step 3: Implement reset using Auth collaborators**

Resolve active Manager, lock target User then Tenant, generate/hash/save, invalidate recovery credentials, rotate session generation, and return `OneTimePasswordResponse`. Never log or persist plaintext.

- [ ] **Step 4: Verify and commit**

Run: `./mvnw -Dtest=TenantServiceImplTest,TenantControllerTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/controllers/TenantController.java src/main/java/com/uit/dormmanagement/services/TenantService.java src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java src/test/java/com/uit/dormmanagement/services/TenantServiceImplTest.java src/test/java/com/uit/dormmanagement/controllers/TenantControllerTest.java
git commit -m "feat: add assisted tenant password reset"
```

### TEN-5: Prove Tenant uniqueness and authorization

**Classification:** KEEP/EXTEND — BR-08/BR-09/BR-11.

**Spec sections:** Tenant §9–§11, §13; Overview §9.

**Dependencies:** TEN-1 through TEN-4 and AUTH-7 security-test support.

**Acceptance criteria:** PostgreSQL and real-security tests prove email/CCCD uniqueness, nonunique phone, Manager/Tenant ownership boundaries, and checked-out contact/history permissions.

**Scope exclusions:** No production behavior is introduced solely for tests and no Phase 3 lifecycle is exercised.

**Files:**
- Create: `src/test/java/com/uit/dormmanagement/security/TenantProfileSecurityIntegrationTest.java`
- Modify: `src/test/java/com/uit/dormmanagement/repositories/TenantContractVehicleRepositoryTest.java`
- Modify: `src/test/java/com/uit/dormmanagement/controllers/TenantControllerTest.java`

**Interfaces:**
- Consumes: TEN-1–TEN-4 and the Auth plan.
- Produces: PostgreSQL/real-security acceptance evidence.

- [ ] **Step 1: Add PostgreSQL integrity tests**

Assert canonical duplicate email and CCCD are rejected, duplicate phone succeeds, UUID/User–Tenant relationship remains unchanged, and concurrent/conflicting corrections are translated to one success plus one 409 without duplicate rows.

- [ ] **Step 2: Add real Security-chain tests**

Assert Manager routes allow Manager only; self route allows Tenant only; anonymous=401 and wrong role=403; Tenant cannot choose another Tenant ID; checked-out Tenant may read/update own contact but Vehicle current-residency routes remain denied by the Vehicle plan.

- [ ] **Step 3: Run the Tenant slice**

Run: `./mvnw -Dtest='TenantServiceImplTest,TenantControllerTest,TenantProfileControllerTest,TenantContractVehicleDtoValidationTest,TenantContractVehicleRepositoryTest,TenantProfileSecurityIntegrationTest' test`

Expected: all selected tests PASS.

- [ ] **Step 4: Commit acceptance tests**

```bash
git add src/test/java/com/uit/dormmanagement/security/TenantProfileSecurityIntegrationTest.java src/test/java/com/uit/dormmanagement/repositories/TenantContractVehicleRepositoryTest.java src/test/java/com/uit/dormmanagement/controllers/TenantControllerTest.java
git commit -m "test: verify tenant profile permissions and integrity"
```

## Plan Acceptance Criteria

- Manager list/detail routes remain compatible.
- Manager correction supports the approved identity/contact fields with exact null/blank semantics.
- Email/CCCD uniqueness and active-Room gender rules are enforced; phone duplicates remain allowed.
- Tenant self-update is owner-derived, contact-only, and works after checkout.
- Assisted reset returns one credential once and invalidates recovery/session state.
- Historical Tenant/User/Contract data is preserved.
- No Tenant creation, onboarding, checkout, Room Transfer, portal, schema, or Flyway work is included.
