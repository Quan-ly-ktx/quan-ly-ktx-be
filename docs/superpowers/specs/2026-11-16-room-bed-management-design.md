# Phase 2 Room & Bed Management — Focused Hardening Design

**Date:** 2026-11-16
**Status:** Revised — ready for final explicit approval
**Requirements:** FR-01–FR-05; BR-01, BR-02, BR-08; Logical ERD T01–T02
**Parent design:** `2026-11-16-phase-2-focused-brownfield-hardening-design.md`

## 1. Existing baseline

### 1.1 Preserve

- Manager-only Room matrix, creation, update, list/detail and Bed status routes (`RoomController.java:42-110`; `BedController.java:28-38`).
- `RoomServiceImpl.createRoom` transaction: canonical Room Code, 4/6/8 generated Beds, current registry row, amenities and rollback (`RoomServiceImpl.java:100-147`).
- Invalid Amenity IDs are rejected rather than ignored (`RoomServiceImpl.java:333-350`).
- Occupancy and Bed price are derived from Bed status and `Room.basePrice`; no mutable Room occupancy counter or Bed price (`RoomServiceImpl.java:272-315`).
- Active Room search combines floor, gender and case-insensitive partial Room Code (`RoomRepository.java:44-70`).
- Room Code Registry and PostgreSQL exact-Bed/current-code/history triggers in `V1__clean_baseline.sql:178-300`.
- Manager manually changes only `AVAILABLE ↔ MAINTENANCE`; `OCCUPIED` is reserved for onboarding/checkout (`BedServiceImpl.java:27-68`).

### 1.2 Correct

- Capacity/floor are absent from `UpdateRoomRequest`, so they are immutable through the API.
- Rename mechanics update current registry state and all Slot Keys in one transaction (`RoomServiceImpl.java:353-386`).
- Archive logic already checks occupied Beds and active Contracts (`RoomServiceImpl.java:198-219`).
- Restore retains IDs/Bed states via native `archived_at = NULL` update (`RoomRepository.java:34-36`).

### 1.3 Incorrect or incomplete

- Rename checks neither occupied Beds nor active Contracts.
- Gender policy change checks occupied Beds but not active Contracts.
- Update reads the Room without a write lock.
- Existing `DELETE /rooms/{id}` actually archives, while safe physical delete and restore have no route.
- Archived Rooms are hidden by `@SQLRestriction` and have no separate list/detail API.
- Physical delete checks only registry protection explicitly; clients receive no precise history conflict.
- Expected unique-constraint races are handled as generic HTTP 400.

## 2. Design goals and exclusions

### 2.1 Goals

1. Preserve the correct Room creation and matrix implementation.
2. Complete Room mutation preconditions under concurrency.
3. Expose explicit archive, restore, archived-list and incorrect-Room deletion operations.
4. Keep Room/Bed UUIDs, contract snapshots and registry history intact.
5. Avoid coupling Room logic to later onboarding/checkout implementation.

### 2.2 Exclusions

- No capacity/floor change, Room transfer, automatic occupancy mutation, Room-level status, performance index or speculative view.
- No onboarding/checkout implementation.
- No Maintenance workflow implementation; only read-only history detection for safe deletion if needed.

## 3. Focused component design

### 3.1 Existing components retained

- `RoomController`, `BedController`, `RoomService`/`RoomServiceImpl`, `BedService`/`BedServiceImpl`.
- `RoomRepository`, `BedRepository`, `RoomCodeRegistryRepository`, `RoomAmenityRepository`, `AmenityRepository`, `ContractRepository`.

### 3.2 Focused collaborator

Introduce **`RoomHistoryGuard`** as a focused read-only collaborator for the complete safe-delete preflight. It answers whether a Room/its Beds have protected business history using:

- monotonic `room_code_registry.used_in_business` as the primary authoritative signal;
- explicit Contract history existence;
- explicit Maintenance Ticket history existence, through the existing repository, without implementing Maintenance behavior; and
- database FK restrictions as the final guard.

This collaborator is read-only and contains no deletion or workflow logic.

### 3.3 Shared lock order

All occupancy-sensitive Room commands use one lock order: **Room first, then its Beds in stable UUID order, then read/check active Contracts**. Phase 3 onboarding/checkout must use the same order (Room, then selected Bed) before changing occupancy or creating/liquidating a Contract. This avoids deadlocks and makes rename/gender/archive checks serializable against cooperating residency workflows.

This is ordinary PostgreSQL pessimistic locking scoped to Room/Bed integrity. No Redis lock, distributed lock or generalized concurrency framework is introduced.

The current legacy `OnboardingServiceImpl` does not acquire these locks (`OnboardingServiceImpl.java:52-84`). Changing that workflow is outside Engineering Phase 2. Therefore full Room-mutation-versus-onboarding concurrency acceptance remains blocked until the Phase 3 onboarding refactor adopts this lock contract. Phase 2 documents this gap and does not modify onboarding merely to make its own tests green.

## 4. Query behavior

### 4.1 Active matrix and list

- Retain `GET /api/v1/admin/rooms/matrix`, `GET /api/v1/admin/rooms`, and `GET /api/v1/admin/rooms/{id}`.
- Default results include only `archived_at IS NULL` Rooms.
- Matrix occupancy is `OCCUPIED / total physical Beds`; maintenance remains in the denominator.
- Add derived `availableBeds` to active Room summary/detail responses because FR-01 requires the available count explicitly. It is calculated from Bed states and is never persisted; this is an additive response change.
- Empty search results return 200 with an empty collection/page.

### 4.2 Archived Rooms

- Add `GET /api/v1/admin/rooms/archived` with the same search/floor/gender/page parameters.
- Do not add a separate archived-detail route in Phase 2; the archived list response contains the facts needed to review and restore a Room.
- Use an explicit repository native query/projection that intentionally bypasses `@SQLRestriction`. Do not remove the restriction from normal entity reads.
- `ArchivedRoomSummaryResponse` contains `id`, `roomNumber`, `floor`, derived `roomType`, `genderPolicy`, `totalBeds`, derived `occupiedBeds`, `availableBeds`, `maintenanceBeds`, `basePrice`, amenities and `archivedAt`.

## 5. Command behavior

### 5.1 Create Room

Preserve the current request and route. The transaction must:

1. Canonicalize Room Code with `Locale.ROOT` uppercase/trim.
2. Validate floor, capacity/RoomType, gender, positive price and positive Amenity quantities.
3. Reject a current or protected Room Code.
4. Insert Room, current registry row, exact 4/6/8 Beds `G01...`, and Room Amenities.
5. Commit all or nothing.

Application prechecks provide friendly errors; PostgreSQL canonical uniqueness and constraint triggers decide races. A race maps to `409 ROOM_NUMBER_DUPLICATED` or `ROOM_CODE_PROTECTED`, not generic 400.

### 5.2 Update price and amenities

- Retain `PUT /api/v1/admin/rooms/{id}` and current response.
- Acquire a pessimistic write lock on the Room before evaluating any requested mutation.
- Price updates change only `rooms.base_price`; historical Contract/Renewal/Invoice snapshots remain unchanged.
- Amenity replacement validates the complete requested ID set before deleting the old set. A failed replacement rolls back and retains the original amenities.

### 5.3 Rename Room Code

Within the same update transaction:

1. Lock the active Room, then all its Beds in stable UUID order.
2. Recheck that every Bed is not `OCCUPIED` and that no active Contract references any Bed in the Room.
3. Canonicalize and validate the new code.
4. Reject a code belonging to any active/archived Room or a protected historical registry entry.
5. Mark/delete the old registry row according to monotonic `used_in_business` rules, flush that change, create the new current registry row, update `rooms.room_number`, and update every Bed Slot Key.
6. Preserve Room ID, Bed IDs, Bed codes, Bed states, Room/Bed FKs and all contract snapshots.

A failure at any step rolls back the registry, Room and all Slot Keys. Concurrent same-code renames yield exactly one success.

### 5.4 Change gender policy

Under the same Room-then-Beds locks, allow a change only if:

- no Bed is `OCCUPIED`; and
- no active Contract references a Bed in the Room.

The two checks are independent. The service never changes Tenant gender or moves a Tenant.

### 5.5 Bed maintenance status

- Retain `PATCH /api/v1/admin/beds/{id}/status`.
- Load the Bed with a write lock to serialize concurrent manual state changes.
- Treat this as a Bed-only leaf command: after the Bed lock is acquired, do not acquire a Room write lock. Any command that needs both Room and Bed locks must instead follow §3.3 Room → stable-order Beds, so no Bed → Room inversion exists.
- Permit only `AVAILABLE → MAINTENANCE`, `MAINTENANCE → AVAILABLE`, or an idempotent request that leaves the same allowed state.
- Reject any request involving `OCCUPIED` and reject transition to maintenance when an active Contract exists.
- Do not expose `OCCUPIED` as a valid manual transition.

### 5.6 Archive

- Keep `DELETE /api/v1/admin/rooms/{id}` as the compatibility route for archive, but document it explicitly as archive and deprecate the misleading “delete” description.
- Do not add a second archive route in Phase 2; preserving one existing route avoids duplicate contracts. The service operation is renamed/documented as archive internally.
- Under the Room-then-Beds locks, require no occupied Bed and no active Contract, then set `archived_at`.
- Archive never changes Bed states, Room/Bed IDs, registry current ownership or historical data.
- Repeated archive returns `409 ROOM_ALREADY_ARCHIVED` or a documented idempotent 200; this design chooses **409** to expose stale client state consistently with other commands.

### 5.7 Restore

- Add `POST /api/v1/admin/rooms/{id}/restore`.
- Resolve the archived row explicitly, lock it, and set only `archived_at = NULL` plus update timestamp.
- Keep Room Code, IDs, Beds, Bed states, price and amenities unchanged.
- Reject an active Room with `409 ROOM_NOT_ARCHIVED`; missing ID returns 404.
- The registry invariant prevents a code conflict; any unexpected conflict rolls back and maps to 409.
- Return `SingleResponse<RoomDetailResponse>` after restoration so the frontend receives the restored active representation.

### 5.8 Delete an incorrectly created Room

- Add `DELETE /api/v1/admin/rooms/{id}/incorrect`.
- This is not archive and is intentionally explicit.
- Permit both active and archived Rooms when they are truly never used. Archived lookup/locking explicitly bypasses `@SQLRestriction`; archive status never substitutes for the history checks.
- Lock the Room and all Beds; reject when `RoomHistoryGuard` finds protected registry, Contract, Ticket, residence or other business history.
- When safe, delete unused Room Amenities, Beds, unprotected registry rows and Room in one transaction, respecting the Phase 1 FK ordering.
- A late FK conflict is a `409 ROOM_HAS_HISTORY`, not a generic database error.
- Archive does not make a historically used Room eligible for physical deletion.

## 6. API contract

| Method and route | Role | Request | Response | Status and errors |
|---|---|---|---|---|
| `GET /api/v1/admin/rooms/matrix` | Manager | Existing filters | Existing Room details | 200, empty list allowed |
| `POST /api/v1/admin/rooms` | Manager | Existing `CreateRoomRequest` | Existing `RoomDetailResponse` | 201; 400 validation, 404 Amenity, 409 code race/protection |
| `PUT /api/v1/admin/rooms/{id}` | Manager | Existing `UpdateRoomRequest` | Existing detail | 200; 400 validation, 404, 409 occupied/contract/code conflict |
| `GET /api/v1/admin/rooms` | Manager | Existing filters/page | Existing page | 200 active only |
| `GET /api/v1/admin/rooms/archived` | Manager | Same filters/page | `ArchivedRoomSummaryResponse` | New, 200 |
| `DELETE /api/v1/admin/rooms/{id}` | Manager | None | Success | Retained compatibility alias; archives, never physical delete |
| `POST /api/v1/admin/rooms/{id}/restore` | Manager | None | `SingleResponse<RoomDetailResponse>` | New; 404/409 |
| `DELETE /api/v1/admin/rooms/{id}/incorrect` | Manager | None | Success | New; 404/409 history |
| `PATCH /api/v1/admin/beds/{id}/status` | Manager | Existing status DTO | Existing Bed response | 200; 404/409 invalid state/current contract |

## 7. Persistence

- No Flyway or entity-column change.
- Existing exact-Bed and registry triggers remain authoritative.
- Add repository lock queries where needed; repository methods are implementation changes, not schema changes.
- Archived queries must be explicit because `Room` has `@SQLRestriction("archived_at IS NULL")` (`Room.java:28-31`).
- Contract/Ticket reads are preconditions; Room code does not mutate those modules.

## 8. Error behavior

New/clarified domain errors:

- `ROOM_ALREADY_ARCHIVED`, `ROOM_NOT_ARCHIVED` — 409.
- `ROOM_HAS_HISTORY` — 409.
- `ROOM_OCCUPIED_OR_CONTRACT_ACTIVE` — 409, used for rename/gender/archive.
- `ROOM_NUMBER_DUPLICATED`, `ROOM_CODE_PROTECTED` — 409.
- Invalid Amenity IDs remain the existing 404 response; do not silently ignore them.

Do not expose raw database messages.

## 9. Testing strategy

### 9.1 Unit/service

- Preserve existing create/matrix/price/amenity/status tests.
- Rename rejects occupied Bed and active Contract separately.
- Gender change rejects occupied Bed and active Contract separately.
- Failed Amenity replacement retains original relations.
- Archive retains Room Code/Bed IDs/statuses; restore changes only archive state.
- Safe delete rejects each history category and deletes only never-used configuration.

### 9.2 PostgreSQL integration

- Concurrent same-code Room create and rename: one success, one 409, no partial Room/Beds/registry.
- Cooperating Room mutations follow the Room→Beds lock order without deadlock; full legacy-onboarding race verification is explicitly deferred until Phase 3 adopts the same lock order.
- Rename rollback retains old code and every old Slot Key when one update fails.
- Exact 4/6/8 Bed constraint remains valid.
- Protected `used_in_business=true` can never reset or be deleted/reused.
- Archive continues to reserve its code.
- Safe delete is blocked by Contract/Ticket FKs even if an application precheck regresses.

### 9.3 Controller/security

- Manager succeeds; Tenant/Technician receive 403; anonymous receives 401.
- Active list never returns archived Rooms; archived list never returns active Rooms.
- The retained DELETE route performs archive only and never physical deletion.

## 10. Cross-phase contracts

- Phase 3 onboarding must reject archived Rooms and lock the selected Bed; it owns `AVAILABLE → OCCUPIED`.
- Phase 3 checkout owns `OCCUPIED → AVAILABLE/MAINTENANCE` and must not call the manual Bed-status endpoint.
- Phase 3 contract creation and teammate Maintenance Ticket creation continue to mark the current Room Code registry as used within their transactions.
- FR-11 Room transfer is not designed or implemented here.

## 11. Acceptance criteria

1. Existing active matrix/create/search behavior remains compatible and correct.
2. Rename and gender changes are impossible with occupancy or active Contract, including concurrent requests.
3. Archive, restore and incorrect-Room deletion are distinct operations with explicit routes/semantics.
4. Archived Rooms retain codes/IDs/Beds/history and are queryable separately.
5. Never-used Room deletion releases only unprotected codes; historically used codes never become reusable.
6. Manual Bed commands cannot create or remove `OCCUPIED`.
7. No Phase 1 schema change or Room transfer/onboarding/checkout implementation is introduced; the known legacy onboarding lock gap remains explicitly deferred to Phase 3.
