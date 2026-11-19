# Vehicle Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Vehicle registration/card/history and Vehicle Change Request workflows with private evidence, bounded transactions, and the existing Phase 1 integrity constraints.

**Architecture:** Preserve `VehicleServiceImpl`, existing routes, entities, enums, and schema. Add focused validation/private-storage/access collaborators plus a dedicated `VehicleChangeRequestService`; lifecycle operations use service state checks, existing PostgreSQL conditional uniqueness, and only scoped Vehicle→Request row locks where two records interact.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring MVC/Security, Spring Data JPA, PostgreSQL/Flyway, multipart I/O, JUnit 5, Mockito, MockMvc, Testcontainers PostgreSQL.

**Spec:** `docs/superpowers/specs/2026-11-16-vehicle-management-design.md`

**Git execution policy:** Commit snippets are reference-only boundaries. An implementation agent MUST NOT stage, commit, or push unless the user or final execution prompt explicitly authorizes it.

## Global Constraints

- Preserve only the six Vehicle statuses `PENDING_CARD`, `ISSUED`, `REJECTED`, `REVOKED`, `REPLACED`, `CANCELLED` and the four Vehicle Change Request statuses `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`.
- Preserve Phase 1 `vehicles`/`vehicle_change_requests` entities, columns, FKs, checks, and partial unique indexes; no Flyway change.
- Do not impose license-plate uniqueness; Card Code is unique only among `ISSUED` Vehicles.
- Registration uses service validation plus existing PostgreSQL constraints; no Redis lock, idempotency key, retry framework, or distributed concurrency mechanism.
- Change approval is one transaction: eligible `ISSUED` old Vehicle → `REPLACED`, exactly one successor → `ISSUED` with the same Card Code, Request → `APPROVED`, all-or-nothing.
- Cross-record lifecycle locking uses Vehicle → VehicleChangeRequest order and rechecks state after locking.
- There are no legacy Vehicle images to migrate; store only new evidence in a private root and never expose keys/public URLs.
- Do not implement OCI, Notifications, checkout orchestration, Maintenance, Finance, or teammate-owned modules.
- Phase 5 may call a simple PostgreSQL `NotificationService` in the business transaction; Phase 2 adds no events/listeners.

## Review Focus

- Switching a pending Vehicle from bicycle to motorized without both effective images must fail and retain old files/data; VEH-4 tests complete-state validation and compensation.
- A Change Request must not be inserted after revoke passes its state check; VEH-5 locks Vehicle through the pending-request insert and VEH-4 rechecks pending Request while holding that lock.
- Approval must release immediate partial uniqueness before inserting the successor with the same Card Code; VEH-6 tests explicit flush ordering and rollback.
- Tenant evidence access for another Tenant must return 404 without exposing raw keys; VEH-2 and VEH-7 test ownership-hiding behavior.
- Rejection, revocation, replacement, and cancellation must remain distinct terminal histories; VEH-4 and VEH-6 test every invalid source transition.

---

### VEH-1: Implement the shared Vehicle registration validator

**Classification:** REFACTOR — FR-08 data rules.

**Spec sections:** Vehicle §4, §12.1.

**Dependencies:** Phase 1 Vehicle enums/entities and existing DTO validation conventions.

**Acceptance criteria:** One shared validator enforces all five Vehicle types, canonical plate/card-neutral registration fields, and exact type-specific front/back evidence requirements.

**Scope exclusions:** No storage, registration transaction, database constraint, or quota change.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/VehicleRegistrationValidator.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/VehicleRegistrationValidatorImpl.java`
- Modify: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/TenantRegisterVehicleRequest.java`
- Modify: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/CreateVehicleAdminRequest.java`
- Modify: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/UpdateVehicleRequest.java`
- Test: `src/test/java/com/uit/dormmanagement/dtos/TenantContractVehicleDtoValidationTest.java`
- Create: `src/test/java/com/uit/dormmanagement/services/VehicleRegistrationValidatorTest.java`

**Interfaces:**
- Produces: `ValidatedVehicleRegistration validate(VehicleType type, String plate, String brand, String color, MultipartFile front, MultipartFile back)` where the result contains canonical type/plate/brand/color and validated optional files.
- Produces: `validateEffectiveState(...)` for pending correction using retained/replaced/cleared evidence availability.

- [ ] **Step 1: Add failing five-type matrix tests**

Assert SCOOTER/MANUAL_MOTORCYCLE/ELECTRIC_MOTORBIKE require canonical nonblank plate and both JPEG/PNG/WebP images; ELECTRIC_BICYCLE/BICYCLE permit null plate/images; brand/color are always required; client MIME/extension cannot override invalid magic bytes; each file is at most 10 MB.

- [ ] **Step 2: Add correction-state tests**

Assert omitted evidence retains old keys; upload and clear on the same side conflict; clear is allowed only when resulting type does not require that side; motorized effective state must have both evidence sides.

- [ ] **Step 3: Run focused validation tests**

Run: `./mvnw -Dtest=VehicleRegistrationValidatorTest,TenantContractVehicleDtoValidationTest test`

Expected: FAIL because type-specific validation is not centralized and current DTOs require all fields.

- [ ] **Step 4: Implement canonicalization and magic-byte validation**

Implement the exact matrix without querying plate uniqueness. Reuse the configured multipart size limit; do not persist size or trust filename/client MIME.

- [ ] **Step 5: Verify and commit**

Run: `./mvnw -Dtest=VehicleRegistrationValidatorTest,TenantContractVehicleDtoValidationTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/services/VehicleRegistrationValidator.java src/main/java/com/uit/dormmanagement/services/impl/VehicleRegistrationValidatorImpl.java src/main/java/com/uit/dormmanagement/dtos/request/vehicle/TenantRegisterVehicleRequest.java src/main/java/com/uit/dormmanagement/dtos/request/vehicle/CreateVehicleAdminRequest.java src/main/java/com/uit/dormmanagement/dtos/request/vehicle/UpdateVehicleRequest.java src/test/java/com/uit/dormmanagement/services/VehicleRegistrationValidatorTest.java src/test/java/com/uit/dormmanagement/dtos/TenantContractVehicleDtoValidationTest.java
git commit -m "feat: validate vehicle registration by type"
```

### VEH-2: Add private Vehicle evidence storage and authorized access

**Classification:** IMPLEMENT/REFACTOR — FR-08 file boundary.

**Spec sections:** Vehicle §5, §8.2–§8.3, §12.1/§12.3.

**Dependencies:** VEH-1 evidence requirements; existing `StorageConfig` conventions.

**Acceptance criteria:** New evidence is stored under a private root, accessed only through owner/Manager authorization, never exposed as a key/public URL, and compensated on failed commands.

**Scope exclusions:** No legacy migration/cutover, OCI SDK, public static serving, or Phase 5 notification work.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/VehicleEvidenceStorage.java`
- Create: `src/main/java/com/uit/dormmanagement/services/VehicleEvidenceAccessService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/LocalVehicleEvidenceStorage.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/VehicleEvidenceAccessServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/vehicle/VehicleEvidenceContent.java`
- Modify: `src/main/java/com/uit/dormmanagement/configs/StorageConfig.java:12-34`
- Modify: `src/main/java/com/uit/dormmanagement/security/SecurityConfig.java`
- Test: `src/test/java/com/uit/dormmanagement/configs/StorageConfigTest.java`
- Create: `src/test/java/com/uit/dormmanagement/services/LocalVehicleEvidenceStorageTest.java`
- Create: `src/test/java/com/uit/dormmanagement/services/VehicleEvidenceAccessServiceTest.java`

**Interfaces:**
- Produces: `String store(UUID aggregateId, String side, MultipartFile file)`, `VehicleEvidenceContent load(String key)`, `void delete(String key)`.
- Produces: `VehicleEvidenceContent loadVehicleEvidence(UUID actorUserId, boolean manager, UUID vehicleId, String side)` and equivalent Request access method.

- [ ] **Step 1: Add failing private-storage tests**

Assert stored keys are opaque `vehicles/<uuid>/<side>-<uuid>` values; physical paths stay under a private root; traversal is rejected; load/delete work only on regular contained files; returned content includes detected media type/length; no `/uploads/` URL is returned.

- [ ] **Step 2: Add failing ownership tests**

Assert Manager can load any Vehicle/Request evidence; owning Tenant can load own current/history; non-owner receives 404; missing optional evidence returns 404; no raw key appears in the response DTO.

- [ ] **Step 3: Run focused tests**

Run: `./mvnw -Dtest=LocalVehicleEvidenceStorageTest,VehicleEvidenceAccessServiceTest,StorageConfigTest test`

Expected: FAIL because private Vehicle storage/access do not exist.

- [ ] **Step 4: Implement the private storage bean**

Use an optional `storage.vehicle.private-base-dir` property with a safe default outside `storage.local.base-dir`; do not edit `application.yml` or enable OCI. Add an explicit security denial for `/uploads/vehicles/**` before the existing public upload matcher.

- [ ] **Step 5: Implement ownership-aware load service**

Resolve ownership from authenticated User→Tenant; accept only `front`/`back`; stream through Backend responses. Do not expose permanent URLs or object keys.

- [ ] **Step 6: Verify and commit**

Run: `./mvnw -Dtest=LocalVehicleEvidenceStorageTest,VehicleEvidenceAccessServiceTest,StorageConfigTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/services/VehicleEvidenceStorage.java src/main/java/com/uit/dormmanagement/services/VehicleEvidenceAccessService.java src/main/java/com/uit/dormmanagement/services/impl/LocalVehicleEvidenceStorage.java src/main/java/com/uit/dormmanagement/services/impl/VehicleEvidenceAccessServiceImpl.java src/main/java/com/uit/dormmanagement/dtos/response/vehicle/VehicleEvidenceContent.java src/main/java/com/uit/dormmanagement/configs/StorageConfig.java src/main/java/com/uit/dormmanagement/security/SecurityConfig.java src/test/java/com/uit/dormmanagement/configs/StorageConfigTest.java src/test/java/com/uit/dormmanagement/services/LocalVehicleEvidenceStorageTest.java src/test/java/com/uit/dormmanagement/services/VehicleEvidenceAccessServiceTest.java
git commit -m "feat: store vehicle evidence privately"
```

### VEH-3: Align registration and Vehicle history APIs

**Classification:** REFACTOR/EXTEND — FR-08 registration/history.

**Spec sections:** Vehicle §6.1–§6.3, §8–§10, §12.

**Dependencies:** VEH-1 and VEH-2.

**Acceptance criteria:** Current-resident Tenant registration creates one `PENDING_CARD` record under existing quota constraints; Manager/Tenant history queries preserve all six states without exposing evidence keys.

**Scope exclusions:** No card issue/reject/revoke decisions, Change Request workflow, checkout, or advanced idempotency.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/controllers/AdminVehicleController.java:43-83`
- Modify: `src/main/java/com/uit/dormmanagement/controllers/TenantVehicleController.java:35-60`
- Modify: `src/main/java/com/uit/dormmanagement/services/VehicleService.java:14-36`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/VehicleServiceImpl.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/VehicleRepository.java:17-67`
- Modify: `src/main/java/com/uit/dormmanagement/dtos/response/vehicle/VehicleResponse.java`
- Test: `src/test/java/com/uit/dormmanagement/services/VehicleServiceImplTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/VehicleControllerTest.java`

**Interfaces:**
- Produces: multipart Manager/Tenant registration sharing VEH-1 validator and VEH-2 storage.
- Produces: `List<VehicleResponse> getVehicleHistory(UUID tenantUserId)` and retained `getMyVehicle(UUID)`.
- Route: add `GET /api/v1/tenant/vehicles`; retain `/me`.

- [ ] **Step 1: Add failing registration/history tests**

Assert both actor routes require ACTIVE Tenant, always create `PENDING_CARD` with null Card Code, honor the type matrix, use private keys, reject second active/pending Vehicle as 409, and compensate newly stored evidence on validation/database failure. Assert no plate-uniqueness rejection.

- [ ] **Step 2: Add response-history tests**

Assert Tenant history includes all owned statuses; Manager list/detail retains all six; response exposes lifecycle timestamps/predecessor/successor IDs/evidence booleans but no URL/raw key.

- [ ] **Step 3: Run focused tests**

Run: `./mvnw -Dtest=VehicleServiceImplTest,VehicleControllerTest test`

Expected: FAIL because Admin create is JSON/direct issue, responses expose URL fields, and history route is absent.

- [ ] **Step 4: Refactor both registration entry points**

Convert Admin create to multipart and remove request Card Code/public URL fields. Use service prechecks for useful errors and rely on `uq_vehicles_tenant_active_quota` as final guard; translate its violation to `VEHICLE_QUOTA_EXCEEDED`. Do not add special retry/lock infrastructure.

- [ ] **Step 5: Correct response mapping and add history query**

Remove compatibility URL output from API mapping while leaving entity persistence unchanged. Add evidence availability and lifecycle facts; order Tenant history newest first.

- [ ] **Step 6: Verify and commit**

Run: `./mvnw -Dtest=VehicleServiceImplTest,VehicleControllerTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/controllers/AdminVehicleController.java src/main/java/com/uit/dormmanagement/controllers/TenantVehicleController.java src/main/java/com/uit/dormmanagement/services/VehicleService.java src/main/java/com/uit/dormmanagement/services/impl/VehicleServiceImpl.java src/main/java/com/uit/dormmanagement/repositories/VehicleRepository.java src/main/java/com/uit/dormmanagement/dtos/response/vehicle/VehicleResponse.java src/test/java/com/uit/dormmanagement/services/VehicleServiceImplTest.java src/test/java/com/uit/dormmanagement/controllers/VehicleControllerTest.java
git commit -m "refactor: align vehicle registration and history"
```

### VEH-4: Enforce initial card lifecycle transitions

**Classification:** REFACTOR/IMPLEMENT — FR-08 lifecycle.

**Spec sections:** Vehicle §6.3–§6.6, §9–§10, §12.

**Dependencies:** VEH-1 through VEH-3.

**Acceptance criteria:** Initial issue/reject and issued revoke transitions enforce source state, reason/actor/time, Card Code uniqueness, pending-request conflicts, complete evidence, and transactional file compensation.

**Scope exclusions:** No replacement approval, checkout cancellation, notification, or distributed concurrency framework.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/RejectVehicleRequest.java`
- Modify: `src/main/java/com/uit/dormmanagement/controllers/AdminVehicleController.java:75-113`
- Modify: `src/main/java/com/uit/dormmanagement/services/VehicleService.java`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/VehicleServiceImpl.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/VehicleRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/VehicleChangeRequestRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/exceptions/error/VehicleErrorCode.java`
- Test: `src/test/java/com/uit/dormmanagement/services/VehicleServiceImplTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/VehicleControllerTest.java`

**Interfaces:**
- Produces: Vehicle write-lock query and `rejectVehicle(UUID managerUserId,UUID vehicleId,String reason)`.
- Retains: issue/revoke/delete routes with narrowed status transitions.

- [ ] **Step 1: Add failing lifecycle-table tests**

Assert PENDING_CARD→ISSUED only with active Tenant/valid evidence/unique canonical Card Code; PENDING_CARD→REJECTED captures reason/Manager/time and preserves evidence; ISSUED→REVOKED only after no pending Change Request; DELETE performs PENDING_CARD→CANCELLED only; every other source returns 409 unchanged.

- [ ] **Step 2: Add pending-correction tests**

Assert only PENDING_CARD is mutable; effective-state validation follows VEH-1; old keys are retained on rollback and replaced/cleared keys are deleted only after commit; terminal history cannot be edited.

- [ ] **Step 3: Add revoke/request race-shape tests**

Verify revoke locks Vehicle, rechecks state, then checks for PENDING Request while holding the Vehicle lock. Request existence returns 409 and leaves Vehicle ISSUED.

- [ ] **Step 4: Run focused tests**

Run: `./mvnw -Dtest=VehicleServiceImplTest,VehicleControllerTest test`

Expected: FAIL because current update/delete/revoke allow invalid states and rejection is absent.

- [ ] **Step 5: Implement scoped lock queries and transition methods**

Use `PESSIMISTIC_WRITE` only for lifecycle mutations. Preserve Card Code/history; flush state where database uniqueness requires it; map state/constraint conflicts to stable 409 codes. Add no Notification event or service.

- [ ] **Step 6: Rewire Admin routes**

Keep issue/revoke/delete paths; update DELETE description to cancellation; add `PATCH /{id}/reject`; accept multipart correction with explicit clear flags.

- [ ] **Step 7: Verify and commit**

Run: `./mvnw -Dtest=VehicleServiceImplTest,VehicleControllerTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/controllers/AdminVehicleController.java src/main/java/com/uit/dormmanagement/dtos/request/vehicle/RejectVehicleRequest.java src/main/java/com/uit/dormmanagement/services/VehicleService.java src/main/java/com/uit/dormmanagement/services/impl/VehicleServiceImpl.java src/main/java/com/uit/dormmanagement/repositories/VehicleRepository.java src/main/java/com/uit/dormmanagement/repositories/VehicleChangeRequestRepository.java src/main/java/com/uit/dormmanagement/exceptions/error/VehicleErrorCode.java src/test/java/com/uit/dormmanagement/services/VehicleServiceImplTest.java src/test/java/com/uit/dormmanagement/controllers/VehicleControllerTest.java
git commit -m "feat: enforce vehicle card lifecycle"
```

### VEH-5: Add Vehicle Change Request creation and queries

**Classification:** IMPLEMENT — FR-08/BR-10.

**Spec sections:** Vehicle §7.1–§7.3, §8–§10, §12.

**Dependencies:** VEH-1 through VEH-4 and existing pending-request partial uniqueness.

**Acceptance criteria:** An eligible owner can create exactly one PENDING request while the current ISSUED Vehicle is locked; queries enforce ownership/history rules and expose comparison data without raw keys.

**Scope exclusions:** No approval mutation, Tenant cancellation, checkout orchestration, idempotency key, or retry framework.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/VehicleChangeRequestService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/VehicleChangeRequestServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/controllers/TenantVehicleChangeRequestController.java`
- Create: `src/main/java/com/uit/dormmanagement/controllers/AdminVehicleChangeRequestController.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/CreateVehicleChangeRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/vehicle/VehicleChangeRequestResponse.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/VehicleRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/VehicleChangeRequestRepository.java:11-23`
- Create: `src/test/java/com/uit/dormmanagement/services/VehicleChangeRequestServiceTest.java`
- Create: `src/test/java/com/uit/dormmanagement/controllers/VehicleChangeRequestControllerTest.java`

**Interfaces:**
- Produces: `create(UUID tenantUserId, CreateVehicleChangeRequest, MultipartFile, MultipartFile)`, owner/admin list/detail methods.
- Routes: Tenant `GET/POST /api/v1/tenant/vehicle-change-requests`, Tenant detail, Manager paged list/detail.

- [ ] **Step 1: Add failing creation tests**

Assert ACTIVE owner only; lock current Vehicle through validation and Request insert; require owned ISSUED Vehicle/nonblank Card Code; validate proposed state/private evidence; insert one PENDING Request; do not mutate old Vehicle or create a successor; unique pending conflict returns 409 with upload compensation.

- [ ] **Step 2: Add failing query/ownership tests**

Assert Tenant sees only own newest-first history including terminal requests after checkout; other-Tenant detail is 404; Manager filters by status/search; comparison response contains current/proposed/outcome/processor/successor/evidence availability without raw keys.

- [ ] **Step 3: Run focused tests**

Run: `./mvnw -Dtest=VehicleChangeRequestServiceTest,VehicleChangeRequestControllerTest test`

Expected: FAIL because no workflow/controller/DTO exists.

- [ ] **Step 4: Implement creation under Vehicle lock**

Resolve Tenant from principal, lock current Vehicle, recheck ownership/status, then check/insert PENDING Request before releasing the lock. Rely on the existing pending partial unique index as final guard; add no idempotency key or retry framework.

- [ ] **Step 5: Implement owner/admin queries and controllers**

Keep role guards at controllers and ownership in service. Do not add Tenant cancellation; checkout cancellation remains Phase 3.

- [ ] **Step 6: Verify and commit**

Run: `./mvnw -Dtest=VehicleChangeRequestServiceTest,VehicleChangeRequestControllerTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/controllers/TenantVehicleChangeRequestController.java src/main/java/com/uit/dormmanagement/controllers/AdminVehicleChangeRequestController.java src/main/java/com/uit/dormmanagement/dtos/request/vehicle/CreateVehicleChangeRequest.java src/main/java/com/uit/dormmanagement/dtos/response/vehicle/VehicleChangeRequestResponse.java src/main/java/com/uit/dormmanagement/services/VehicleChangeRequestService.java src/main/java/com/uit/dormmanagement/services/impl/VehicleChangeRequestServiceImpl.java src/main/java/com/uit/dormmanagement/repositories/VehicleRepository.java src/main/java/com/uit/dormmanagement/repositories/VehicleChangeRequestRepository.java src/test/java/com/uit/dormmanagement/services/VehicleChangeRequestServiceTest.java src/test/java/com/uit/dormmanagement/controllers/VehicleChangeRequestControllerTest.java
git commit -m "feat: add vehicle change request submission"
```

### VEH-6: Implement atomic Change Request approval and rejection

**Classification:** IMPLEMENT — FR-08/BR-08/BR-10.

**Spec sections:** Vehicle §7.4–§7.6, §10, §12.1–§12.2.

**Dependencies:** VEH-5 plus Vehicle→Request repository locks.

**Acceptance criteria:** One transaction changes old ISSUED→REPLACED, creates exactly one ISSUED successor with the same Card Code, and marks the Request APPROVED; rejection changes only Request outcome; injected failures roll everything back.

**Scope exclusions:** No generalized idempotency/distributed locks, notification/event architecture, checkout, or schema change.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/RejectVehicleChangeRequest.java`
- Modify (created by VEH-5): `src/main/java/com/uit/dormmanagement/controllers/AdminVehicleChangeRequestController.java`
- Modify (created by VEH-5): `src/main/java/com/uit/dormmanagement/services/VehicleChangeRequestService.java`
- Modify (created by VEH-5): `src/main/java/com/uit/dormmanagement/services/impl/VehicleChangeRequestServiceImpl.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/VehicleRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/VehicleChangeRequestRepository.java`
- Test (created by VEH-5): `src/test/java/com/uit/dormmanagement/services/VehicleChangeRequestServiceTest.java`
- Create: `src/test/java/com/uit/dormmanagement/repositories/VehicleLifecyclePostgresTest.java`
- Test (created by VEH-5): `src/test/java/com/uit/dormmanagement/controllers/VehicleChangeRequestControllerTest.java`

**Interfaces:**
- Produces: `approve(UUID managerUserId,UUID requestId)` and `reject(UUID managerUserId,UUID requestId,String reason)`.
- Routes: `PATCH /api/v1/admin/vehicle-change-requests/{id}/approve|reject`.

- [ ] **Step 1: Add failing approval state/lock tests**

Read Request only to obtain current Vehicle ID, then assert implementation locks Vehicle followed by Request and rechecks PENDING/ownership/residency/ISSUED/Card Code/relationship. A stale/terminal request returns 409 without mutation.

- [ ] **Step 2: Add failing atomic replacement tests**

Assert old becomes REPLACED with time; flush releases active quota/Card Code uniqueness; exactly one new UUID becomes ISSUED with the exact same Card Code/proposed data/evidence and predecessor link; old successor link and Request APPROVED/newVehicle/processor/time are set.

- [ ] **Step 3: Add rejection and rollback tests**

Assert rejection requires reason and changes only Request outcome metadata. Inject failure after old status, after successor insert, and before Request update; each rolls back old Vehicle, successor, links, and Request together.

- [ ] **Step 4: Run focused tests**

Run: `./mvnw -Dtest=VehicleChangeRequestServiceTest,VehicleLifecyclePostgresTest,VehicleChangeRequestControllerTest test`

Expected: FAIL because decision workflow is absent.

- [ ] **Step 5: Implement Vehicle→Request locking and approval transaction**

Use one `@Transactional` method, explicit `saveAndFlush` after old `REPLACED`, insert/link successor, then update Request. Resolve active Manager `StaffProfile`. Do not publish events or call Notifications in Phase 2.

- [ ] **Step 6: Implement rejection and stale-command behavior**

Lock Request, require PENDING, set reason/processor/time, leave current Vehicle untouched. Repeated approve/reject returns 409; this is state validation, not generalized idempotency.

- [ ] **Step 7: Verify and commit**

Run: `./mvnw -Dtest=VehicleChangeRequestServiceTest,VehicleLifecyclePostgresTest,VehicleChangeRequestControllerTest test`

Expected: PASS on PostgreSQL Testcontainers.

```bash
git add src/main/java/com/uit/dormmanagement/controllers/AdminVehicleChangeRequestController.java src/main/java/com/uit/dormmanagement/dtos/request/vehicle/RejectVehicleChangeRequest.java src/main/java/com/uit/dormmanagement/services/VehicleChangeRequestService.java src/main/java/com/uit/dormmanagement/services/impl/VehicleChangeRequestServiceImpl.java src/main/java/com/uit/dormmanagement/repositories/VehicleRepository.java src/main/java/com/uit/dormmanagement/repositories/VehicleChangeRequestRepository.java src/test/java/com/uit/dormmanagement/services/VehicleChangeRequestServiceTest.java src/test/java/com/uit/dormmanagement/repositories/VehicleLifecyclePostgresTest.java src/test/java/com/uit/dormmanagement/controllers/VehicleChangeRequestControllerTest.java
git commit -m "feat: approve vehicle replacement atomically"
```

### VEH-7: Expose protected evidence and verify the complete Vehicle slice

**Classification:** EXTEND — FR-08/BR-11 acceptance.

**Spec sections:** Vehicle §5.3, §9–§10, §12, §14; Overview §9.

**Dependencies:** VEH-1 through VEH-6 and stable Auth/Tenant security semantics.

**Acceptance criteria:** Protected evidence endpoints enforce 401/403/404 ownership behavior, PostgreSQL constraints reject lifecycle duplicates, and the full Vehicle slice passes without public paths or schema changes.

**Scope exclusions:** No OCI implementation, legacy-object reconciliation, stress testing, or production abstraction added solely for tests.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/controllers/AdminVehicleController.java`
- Modify: `src/main/java/com/uit/dormmanagement/controllers/TenantVehicleController.java`
- Modify (created by VEH-5): `src/main/java/com/uit/dormmanagement/controllers/AdminVehicleChangeRequestController.java`
- Modify (created by VEH-5): `src/main/java/com/uit/dormmanagement/controllers/TenantVehicleChangeRequestController.java`
- Modify: `src/test/java/com/uit/dormmanagement/controllers/VehicleControllerTest.java`
- Create: `src/test/java/com/uit/dormmanagement/security/VehicleSecurityIntegrationTest.java`
- Modify: `src/test/java/com/uit/dormmanagement/repositories/ContractVehicleRepositoryTest.java`
- Modify: `src/test/java/com/uit/dormmanagement/repositories/TenantContractVehicleRepositoryTest.java`

**Interfaces:**
- Consumes: VEH-2 evidence access and VEH-3–VEH-6 lifecycle services.
- Produces: four protected GET evidence endpoints from spec §5.3.

- [ ] **Step 1: Add evidence endpoint tests**

Assert Manager/owner streams correct bytes/media type; missing side=404; non-owner=404; anonymous=401; wrong role=403; JSON never contains raw key/public URL; `/uploads/vehicles/**` is denied.

- [ ] **Step 2: Add PostgreSQL integrity tests**

Verify existing partial unique constraints reject second active Vehicle, duplicate issued Card Code, and second pending Request. Verify service maps expected violations to 409 and no schema/index changes were introduced.

- [ ] **Step 3: Add real Security-chain lifecycle tests**

Assert only Manager can decide/issue/reject/revoke/cancel; Tenant only creates/views own resources; checked-out Tenant reads history but cannot register/change; all six/four statuses serialize correctly.

- [ ] **Step 4: Run complete Vehicle slice**

Run: `./mvnw -Dtest='VehicleRegistrationValidatorTest,LocalVehicleEvidenceStorageTest,VehicleEvidenceAccessServiceTest,VehicleServiceImplTest,VehicleChangeRequestServiceTest,VehicleControllerTest,VehicleChangeRequestControllerTest,VehicleLifecyclePostgresTest,VehicleSecurityIntegrationTest,ContractVehicleRepositoryTest,TenantContractVehicleRepositoryTest' test`

Expected: all selected tests PASS; no event, notification, migration, distributed lock, or concurrency-stress assertion exists.

- [ ] **Step 5: Commit Vehicle acceptance coverage**

```bash
git add src/main/java/com/uit/dormmanagement/controllers/AdminVehicleController.java src/main/java/com/uit/dormmanagement/controllers/TenantVehicleController.java src/main/java/com/uit/dormmanagement/controllers/AdminVehicleChangeRequestController.java src/main/java/com/uit/dormmanagement/controllers/TenantVehicleChangeRequestController.java src/test/java/com/uit/dormmanagement/controllers/VehicleControllerTest.java src/test/java/com/uit/dormmanagement/security/VehicleSecurityIntegrationTest.java src/test/java/com/uit/dormmanagement/repositories/ContractVehicleRepositoryTest.java src/test/java/com/uit/dormmanagement/repositories/TenantContractVehicleRepositoryTest.java
git commit -m "test: verify vehicle lifecycle and evidence security"
```

## Plan Acceptance Criteria

- Registration/card/history behavior covers all six Vehicle states with the approved type/evidence matrix.
- Initial rejection and Change Request rejection are separate.
- Existing PostgreSQL constraints protect active quota, Card Code, and pending Request uniqueness.
- Change approval is all-or-nothing, creates exactly one successor, and preserves physical Card Code/history links.
- Vehicle→Request lock ordering is consistent for request creation/revoke/approval-sensitive flows.
- New evidence is private and owner/Manager authorized; no legacy migration or public URL exists.
- No plate uniqueness, event architecture, Notification implementation, OCI, checkout orchestration, schema, or Flyway change is included.
