# Room & Bed Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Room/Bed lifecycle, locking, archived queries, and safe deletion while preserving the correct Phase 1 creation/registry implementation.

**Architecture:** Keep `RoomServiceImpl`, `BedServiceImpl`, current controllers, and Phase 1 tables. Add only repository lock queries, an archived summary DTO, and a focused read-only `RoomHistoryGuard`; every occupancy-sensitive mutation follows Room → Beds in stable UUID order.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Data JPA/Hibernate, PostgreSQL/Flyway, Bean Validation, JUnit 5, Mockito, MockMvc, Testcontainers PostgreSQL.

**Spec:** `docs/superpowers/specs/2026-11-16-room-bed-management-design.md`

**Git execution policy:** Commit snippets are reference-only boundaries. An implementation agent MUST NOT stage, commit, or push unless the user or final execution prompt explicitly authorizes it.

## Global Constraints

- Preserve correct Room creation: canonical code, exact 4/6/8 Beds, registry row, Amenities, and one transaction.
- Capacity and floor remain immutable; occupancy and Bed display price remain derived.
- Occupancy-sensitive commands lock Room first, then Beds in stable UUID order, then inspect active Contracts.
- Do not modify `OnboardingServiceImpl`; Phase 3 must adopt the same Room → Bed order.
- Archive never releases a Room Code; `used_in_business` never changes from true to false.
- Do not modify Flyway, Phase 1 schema, Maintenance behavior, or Room Transfer.
- PostgreSQL uniqueness/trigger constraints remain final integrity guards.

## Review Focus

- Renaming to a protected historical Room Code must return 409 without changing slot keys; ROOM-2 tests full rollback.
- An archived never-used Room must be physically deletable despite `@SQLRestriction`; ROOM-5 tests the unfiltered lookup.
- Invalid Amenity IDs must fail the entire create/update transaction; ROOM-1 preserves and tests rollback.
- A Bed with an active Contract must not enter MAINTENANCE even if its stored status is AVAILABLE; ROOM-3 covers the inconsistent-data guard.
- Restore must change only `archived_at` and return complete detail; ROOM-4 compares identity, Beds, statuses, code, price, and Amenities.

---

### ROOM-1: Preserve creation and complete derived Room responses

**Classification:** KEEP/EXTEND — FR-01/FR-02/FR-05.

**Spec sections:** Room §4.1, §5.1–§5.2, §6, §9.

**Dependencies:** Phase 1 Room/Bed/registry/Amenity mappings and constraints.

**Acceptance criteria:** Existing atomic creation remains green and Room responses derive available/occupied/maintenance counts plus display price from Beds/Room without persisted counters.

**Scope exclusions:** No new columns, Room capacity/floor mutation, Onboarding, or checkout work.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/dtos/response/room/RoomSummaryResponse.java`
- Modify: `src/main/java/com/uit/dormmanagement/dtos/response/room/RoomDetailResponse.java`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/RoomServiceImpl.java:57-147,272-331`
- Test: `src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/RoomControllerTest.java`
- Test: `src/test/java/com/uit/dormmanagement/dtos/room/RoomDtoValidationTest.java`

**Interfaces:**
- Preserves: `RoomService.createRoom`, `getRoomMatrix`, `getRooms`, `getRoomDetails`.
- Produces: derived `availableBeds` and `maintenanceBeds` alongside existing `occupiedBeds` without persistence.

- [ ] **Step 1: Add failing response and rollback tests**

Assert summaries/details derive all three counts from Bed states, Bed price equals `Room.basePrice`, creation generates exactly G01…G04/G06/G08, and an invalid Amenity leaves no Room/Bed/registry/association writes.

- [ ] **Step 2: Run focused tests**

Run: `./mvnw -Dtest=RoomServiceTest,RoomControllerTest,RoomDtoValidationTest test`

Expected: FAIL only for the newly required derived fields; existing creation assertions remain green.

- [ ] **Step 3: Extend DTO mapping without adding counters**

Add derived response fields and calculate them from loaded Beds in `RoomServiceImpl`. Do not add fields to `Room`, `Bed`, or migrations.

- [ ] **Step 4: Verify focused tests**

Run: `./mvnw -Dtest=RoomServiceTest,RoomControllerTest,RoomDtoValidationTest test`

Expected: PASS.

- [ ] **Step 5: Commit response completion**

```bash
git add src/main/java/com/uit/dormmanagement/dtos/response/room/RoomSummaryResponse.java src/main/java/com/uit/dormmanagement/dtos/response/room/RoomDetailResponse.java src/main/java/com/uit/dormmanagement/services/impl/RoomServiceImpl.java src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java src/test/java/com/uit/dormmanagement/controllers/RoomControllerTest.java src/test/java/com/uit/dormmanagement/dtos/room/RoomDtoValidationTest.java
git commit -m "feat: expose derived room availability"
```

### ROOM-2: Enforce Room → Bed locking for rename and gender changes

**Classification:** REFACTOR — FR-03/BR-01/BR-08.

**Spec sections:** Room §3.3, §5.3–§5.4, §7–§9.

**Dependencies:** ROOM-1 regression baseline.

**Acceptance criteria:** Rename and gender-policy commands acquire Room then stable-order Bed locks, reject occupied/active-Contract conflicts, and roll back registry/code/slot changes on failure.

**Scope exclusions:** No Phase 3 Onboarding refactor or generalized distributed locking.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/repositories/BedRepository.java:12-26`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/RoomRepository.java:25-36`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/RoomServiceImpl.java:149-196,353-386`
- Modify: `src/main/java/com/uit/dormmanagement/exceptions/error/RoomErrorCode.java`
- Test: `src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/repositories/RoomBedRepositoryTest.java`

**Interfaces:**
- Produces: `BedRepository.findByRoomIdForUpdateOrderById(UUID)` using `PESSIMISTIC_WRITE` and stable UUID order.
- Consumes: existing `RoomRepository.findByIdForUpdate(UUID)` and Contract active-status queries.

- [ ] **Step 1: Add failing mutation-guard tests**

Cover rename/gender rejection for any OCCUPIED Bed or active Contract; successful rename updates Room current code and every slot key; protected/duplicate code returns 409; any failure preserves old Room/registry/slot values.

- [ ] **Step 2: Add PostgreSQL lock/constraint tests**

Verify cooperating Room mutations acquire Room then Beds and same-code create/rename resolves to one success plus one translated 409 without partial registry/slot state. Do not involve legacy Onboarding.

- [ ] **Step 3: Run focused tests**

Run: `./mvnw -Dtest=RoomServiceTest,RoomBedRepositoryTest test`

Expected: FAIL because update currently reads without locks and rename omits occupancy/Contract guards.

- [ ] **Step 4: Add the Bed lock query and shared loader**

Implement a private `lockRoomAndBeds(UUID)` flow in `RoomServiceImpl`: call Room write-lock first, then the stable Bed lock query, then active-Contract checks. Use it only for occupancy-sensitive mutations; price/Amenity-only changes may reuse the locked aggregate without new abstractions.

- [ ] **Step 5: Apply guards to rename and gender policy**

Reject rename/gender changes when any Bed is OCCUPIED or an active Contract exists. Preserve the existing registry flush ordering and update slot keys in the same transaction.

- [ ] **Step 6: Verify tests**

Run: `./mvnw -Dtest=RoomServiceTest,RoomBedRepositoryTest test`

Expected: PASS; no Phase 3 Onboarding file changed.

- [ ] **Step 7: Commit scoped Room locking**

```bash
git add src/main/java/com/uit/dormmanagement/repositories/BedRepository.java src/main/java/com/uit/dormmanagement/repositories/RoomRepository.java src/main/java/com/uit/dormmanagement/services/impl/RoomServiceImpl.java src/main/java/com/uit/dormmanagement/exceptions/error/RoomErrorCode.java src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java src/test/java/com/uit/dormmanagement/repositories/RoomBedRepositoryTest.java
git commit -m "fix: guard room mutations with room bed locks"
```

### ROOM-3: Restrict manual Bed status transitions

**Classification:** KEEP/EXTEND — FR-01/BR-08.

**Spec sections:** Room §3.3, §5.5, §8–§9.

**Dependencies:** ROOM-2 lock queries/order.

**Acceptance criteria:** Manager manual transitions allow only AVAILABLE↔MAINTENANCE under the documented guards; OCCUPIED and active-Contract Beds cannot be manually changed.

**Scope exclusions:** No checkout, occupancy assignment, or Maintenance-ticket workflow.

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/repositories/BedRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/BedServiceImpl.java:27-68`
- Test: `src/test/java/com/uit/dormmanagement/services/BedServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/BedControllerTest.java`

**Interfaces:**
- Produces: `BedRepository.findByIdForUpdate(UUID)` as a Bed-only leaf lock that never subsequently acquires a Room write lock.
- Preserves: only AVAILABLE ↔ MAINTENANCE or same-state idempotent requests; OCCUPIED is never manually entered/exited.

- [ ] **Step 1: Add failing transition-table tests**

Assert AVAILABLE→MAINTENANCE and MAINTENANCE→AVAILABLE succeed; same allowed state returns unchanged; any transition from/to OCCUPIED fails; active Contract blocks MAINTENANCE even when Bed status is AVAILABLE. Verify the command takes only the Bed write lock and never requests a Room write lock after it.

- [ ] **Step 2: Run focused tests**

Run: `./mvnw -Dtest=BedServiceTest,BedControllerTest test`

Expected: FAIL on lock/idempotent transition coverage.

- [ ] **Step 3: Implement the locked transition table**

Load the Bed with `PESSIMISTIC_WRITE`, evaluate the explicit transition table, check active Contract before MAINTENANCE, and return the existing `BedResponse` with Room-derived price. Keep this as a Bed-only leaf command: it must not acquire a Room write lock after locking the Bed. Commands that require both aggregates continue to use ROOM-2's Room → stable-order Beds protocol, so no inverse Bed → Room lock path exists.

- [ ] **Step 4: Verify and commit**

Run: `./mvnw -Dtest=BedServiceTest,BedControllerTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/repositories/BedRepository.java src/main/java/com/uit/dormmanagement/services/impl/BedServiceImpl.java src/test/java/com/uit/dormmanagement/services/BedServiceTest.java src/test/java/com/uit/dormmanagement/controllers/BedControllerTest.java
git commit -m "fix: enforce manual bed status transitions"
```

### ROOM-4: Add the minimal archive, archived-list, and restore contract

**Classification:** IMPLEMENT — FR-04.

**Spec sections:** Room §4.2, §5.6–§5.7, §6, §9.

**Dependencies:** ROOM-2 aggregate locking and ROOM-1 derived mapping.

**Acceptance criteria:** The retained archive route, one archived-list route, and one restore route preserve identity/code/Beds/Amenities; archive/restore reject occupancy or active-Contract conflicts and return stable errors. No archived-detail API is added.

**Scope exclusions:** No permanent deletion logic in this task and no code release on archive.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/room/ArchivedRoomSummaryResponse.java`
- Modify: `src/main/java/com/uit/dormmanagement/controllers/RoomController.java:77-110`
- Modify: `src/main/java/com/uit/dormmanagement/services/RoomService.java:13-30`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/RoomServiceImpl.java:198-290`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/RoomRepository.java`
- Test: `src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/RoomControllerTest.java`

**Interfaces:**
- Produces: `Page<ArchivedRoomSummaryResponse> getArchivedRooms(Pageable)` and `RoomDetailResponse restoreRoom(UUID)`; an internal unfiltered ID lookup is used only to restore or safely delete an archived Room.
- Routes: retain `DELETE /api/v1/admin/rooms/{id}` as archive; add only `GET /api/v1/admin/rooms/archived` and `POST /api/v1/admin/rooms/{id}/restore`.

- [ ] **Step 1: Add failing route/service tests**

Assert archive requires no OCCUPIED Bed/active Contract and retains code/Beds; archived rows disappear from active APIs; archived list exposes the exact spec fields; restore returns `SingleResponse<RoomDetailResponse>` and changes only archive state; repeated archive/restore returns 409/404 as specified.

- [ ] **Step 2: Run focused tests**

Run: `./mvnw -Dtest=RoomServiceTest,RoomControllerTest test`

Expected: FAIL because the archived-list and restore contracts are absent.

- [ ] **Step 3: Add unfiltered archived repository queries**

Use native/explicit queries that can see `archived_at IS NOT NULL` despite `@SQLRestriction`: a paged list for the approved API and an internal ID lookup only for restore/ROOM-5 safe deletion. Do not remove the entity restriction globally or expose the ID lookup as an archived-detail API.

- [ ] **Step 4: Implement controller/service contracts**

Archive under Room→Beds locks. Map archived summaries from stored Room plus derived Bed counts/Amenities. Restore through the existing native update, then reload unarchived detail.

- [ ] **Step 5: Verify and commit**

Run: `./mvnw -Dtest=RoomServiceTest,RoomControllerTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/controllers/RoomController.java src/main/java/com/uit/dormmanagement/dtos/response/room/ArchivedRoomSummaryResponse.java src/main/java/com/uit/dormmanagement/services/RoomService.java src/main/java/com/uit/dormmanagement/services/impl/RoomServiceImpl.java src/main/java/com/uit/dormmanagement/repositories/RoomRepository.java src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java src/test/java/com/uit/dormmanagement/controllers/RoomControllerTest.java
git commit -m "feat: expose room archive lifecycle"
```

### ROOM-5: Protect history during physical deletion

**Classification:** EXTEND — FR-04/BR-02.

**Spec sections:** Room §3.2–§3.3, §5.8, §7–§9.

**Dependencies:** ROOM-2 locking and ROOM-4 archived lookup behavior.

**Acceptance criteria:** Only never-used Rooms with no protected history are physically removed in one transaction; historical/occupied/FK-protected Rooms return 409 and retain all data/code protection.

**Scope exclusions:** No Maintenance behavior, history deletion, cascading around FKs, or schema change.

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/RoomHistoryGuard.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/RoomHistoryGuardImpl.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/MaintenanceTicketRepository.java:13-28`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/ContractRepository.java:18-47`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/RoomRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/services/impl/RoomServiceImpl.java:230-243`
- Modify: `src/main/java/com/uit/dormmanagement/controllers/RoomController.java`
- Test: `src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java`
- Create: `src/test/java/com/uit/dormmanagement/services/RoomHistoryGuardTest.java`
- Test: `src/test/java/com/uit/dormmanagement/repositories/RoomBedRepositoryTest.java`

**Interfaces:**
- Produces: `boolean hasProtectedHistory(UUID roomId, Collection<UUID> bedIds)`.
- Route: add `DELETE /api/v1/admin/rooms/{id}/permanent` for active or archived incorrectly created Rooms.

- [ ] **Step 1: Add failing protected-history tests**

Assert any `used_in_business=true`, Contract history, Maintenance Ticket history, occupied Bed, or FK-protected history rejects deletion; an active or archived never-used Room deletes Amenities, Beds, unused registry rows, and Room atomically; archive alone never makes a code reusable.

- [ ] **Step 2: Run focused tests**

Run: `./mvnw -Dtest=RoomHistoryGuardTest,RoomServiceTest,RoomBedRepositoryTest test`

Expected: FAIL because current preflight checks only registry usage and cannot load archived Room.

- [ ] **Step 3: Implement focused history queries and guard**

Add existence-only repository methods; do not add Maintenance business methods. The guard is read-only and returns protected when any known history exists.

- [ ] **Step 4: Implement safe deletion transaction**

Load active/archived Room unfiltered, lock Room then Beds, run the guard, then delete associations/Beds/unprotected registry/Room in FK-safe order. Translate remaining FK violations to a stable 409 rather than deleting history.

- [ ] **Step 5: Verify and commit**

Run: `./mvnw -Dtest=RoomHistoryGuardTest,RoomServiceTest,RoomBedRepositoryTest,RoomControllerTest test`

Expected: PASS.

```bash
git add src/main/java/com/uit/dormmanagement/controllers/RoomController.java src/main/java/com/uit/dormmanagement/repositories/MaintenanceTicketRepository.java src/main/java/com/uit/dormmanagement/repositories/ContractRepository.java src/main/java/com/uit/dormmanagement/repositories/RoomRepository.java src/main/java/com/uit/dormmanagement/services/RoomHistoryGuard.java src/main/java/com/uit/dormmanagement/services/impl/RoomHistoryGuardImpl.java src/main/java/com/uit/dormmanagement/services/impl/RoomServiceImpl.java src/test/java/com/uit/dormmanagement/services/RoomHistoryGuardTest.java src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java src/test/java/com/uit/dormmanagement/repositories/RoomBedRepositoryTest.java src/test/java/com/uit/dormmanagement/controllers/RoomControllerTest.java
git commit -m "feat: safely remove unused rooms"
```

### ROOM-6: Run Room/Bed security and PostgreSQL acceptance

**Classification:** KEEP/EXTEND — final Room slice gate.

**Spec sections:** Room §9, §11; Overview §9.

**Dependencies:** ROOM-1 through ROOM-5.

**Acceptance criteria:** Real-security and PostgreSQL tests prove route authorization, uniqueness/triggers, rollback, archive/restore/delete safety, and the unchanged Phase 1 schema.

**Scope exclusions:** Acceptance tests must not modify production behavior, Flyway, Onboarding, Checkout, or Room Transfer.

**Files:**
- Create: `src/test/java/com/uit/dormmanagement/security/RoomBedSecurityIntegrationTest.java`
- Modify: `src/test/java/com/uit/dormmanagement/repositories/Phase1APostgresMigrationTest.java`
- Modify: `src/test/java/com/uit/dormmanagement/repositories/RoomBedRepositoryTest.java`

**Interfaces:**
- Consumes: ROOM-1–ROOM-5.
- Produces: real Security-chain and PostgreSQL acceptance evidence.

- [ ] **Step 1: Add real-role tests**

Assert anonymous=401, Tenant/Technician=403, Manager allowed for every Room/Bed mutation and archived query route.

- [ ] **Step 2: Add PostgreSQL regression assertions**

Verify existing exact-Bed/current-code/history triggers and partial uniqueness remain unchanged; rollback leaves no partial registry/slot/deletion state. Explicitly document that Onboarding concurrency is not exercised until Phase 3.

- [ ] **Step 3: Run the Room/Bed slice**

Run: `./mvnw -Dtest='RoomServiceTest,BedServiceTest,RoomControllerTest,BedControllerTest,RoomDtoValidationTest,RoomHistoryGuardTest,RoomBedRepositoryTest,Phase1APostgresMigrationTest,RoomBedSecurityIntegrationTest' test`

Expected: all selected tests PASS on Docker-backed PostgreSQL where required.

- [ ] **Step 4: Commit acceptance tests**

```bash
git add src/test/java/com/uit/dormmanagement/security/RoomBedSecurityIntegrationTest.java src/test/java/com/uit/dormmanagement/repositories/Phase1APostgresMigrationTest.java src/test/java/com/uit/dormmanagement/repositories/RoomBedRepositoryTest.java
git commit -m "test: verify room bed lifecycle integrity"
```

## Plan Acceptance Criteria

- Correct Phase 1 Room creation remains intact.
- Rename/gender/archive/delete operations follow Room → Bed locks and reject occupied/active-contract cases.
- Manual Bed changes cannot touch OCCUPIED and honor active Contract safety.
- Archived Rooms are queryable/restorable without releasing identity or code.
- Only truly never-used active/archived Rooms can be physically deleted.
- Derived occupancy/availability/price values are accurate and not persisted twice.
- PostgreSQL integrity and real Manager authorization tests pass.
- Onboarding, checkout, Room Transfer, Maintenance behavior, schema, and Flyway remain untouched.
