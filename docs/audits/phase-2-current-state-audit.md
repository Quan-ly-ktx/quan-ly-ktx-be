# Phase 2 Current-State Audit — KTX Hub Backend

**Audit date:** 2026-11-16
**Engineering phase:** Phase 2 — existing core modules
**Scope:** FR-01–FR-08 and FR-21–FR-24; BR-01, BR-02, BR-08, BR-09, BR-10, BR-11
**Branch:** `refactor/erd-and-implemented-code`
**HEAD:** `ac525f28bebda83efa03007252bdfb1c7cf4a88f`

## 1. Purpose and evidence rules

> **Design-decision supersession (2026-11-16):** The implementation evidence and gap classifications in this audit remain historical current-state findings. Where a proposed disposition below mentions Refresh replay-triggered session revocation, advanced Vehicle idempotency/concurrency behavior, or post-commit Vehicle notification events, the reviewed specifications under `docs/superpowers/specs/` supersede that proposal: Refresh reuse returns `401 INVALID_REFRESH_TOKEN` without global logout; Vehicle registration relies on service validation plus existing PostgreSQL constraints; Vehicle approval uses a bounded transaction and scoped row locks; and Phase 5 notifications use a direct PostgreSQL-backed `NotificationService`. No legacy Vehicle evidence migration is required because this development/demo system has no existing Vehicle image files.

This is a brownfield audit. It identifies what should be retained, corrected, extended, implemented, or deferred; it does not authorize application changes. The precedence used is the current task, SRS v3.4.1, Logical ERD v3.6, Conceptual ERD v3.5, then implementation evidence.

The authoritative requirements are in `docs/SRS.md:134-147,192-229,253-505,818-883`. Persistence and transaction invariants are in `docs/Logical-ERD.md:169-342,805-823,831-895`. `users.status` remains exactly `ACTIVE` and `LOCKED`; no finding proposes `INACTIVE`.

Line references in this report identify the audited revision. Existing older documents under `docs/superpowers/` are historical and are not current specifications: for example, `docs/superpowers/specs/2026-11-03-room-bed-management-design.md` persists `Bed.price`, `occupiedBeds`, and `deletedAt`, and `docs/superpowers/specs/2026-11-04-tenant-vehicle-contract-onboarding-design.md` uses obsolete vehicle states and combines later-phase contract work. They must not be executed as Phase 2 plans.

## 2. Repository and Phase 1 baseline

### 2.1 Git and build baseline

| Item | Evidence | Result |
|---|---|---|
| Branch | `git branch --show-current` | `refactor/erd-and-implemented-code` |
| HEAD | `git rev-parse HEAD` | `ac525f28bebda83efa03007252bdfb1c7cf4a88f` |
| Initial worktree | `git status --short` | Clean; no pre-existing local changes |
| Stack | `pom.xml:5-9,17-35,37-191` | Java 25, Spring Boot 4.1.2, Maven, MVC, Validation, JPA, PostgreSQL, Flyway, Redis, Security/JJWT, mail, Testcontainers |
| Runtime schema policy | `src/main/resources/application.yml:28-46,64-69` | PostgreSQL; Hibernate `validate`; Flyway enabled; Redis configured |
| Compilation | `./mvnw -q -DskipTests compile` | PASS on 2026-11-16. First sandboxed attempt could not open the external `~/.m2` lock; the same command succeeded with access to the existing Maven cache. |
| Current tests | `src/test/java/**` | Inspected, not executed during this audit |

The historical Phase 1 report records 223 tests passing and successful PostgreSQL startup at `docs/audits/backend-srs-v3.4.1-audit.md:403-424`. That is credible historical evidence, not a fresh Phase 2 result.

### 2.2 Phase 1 foundation verification

`src/main/resources/db/migration/V1__clean_baseline.sql` creates exactly 20 application tables. The relevant domains and integrity mechanisms are present:

- Users: canonical unique email, roles `MANAGER/TENANT/TECHNICIAN`, statuses `ACTIVE/LOCKED` (`V1:8-18`).
- Rooms/Beds: canonical Room Code, exact capacity 4/6/8, Bed states, canonical Slot Key, exact Bed count and current-code registry triggers (`V1:109-300`).
- Vehicles: six statuses, evidence checks by vehicle type, one `PENDING_CARD/ISSUED` vehicle per Tenant, unique card among `ISSUED` vehicles (`V1:513-553`).
- Vehicle Change Requests: four statuses, same-Tenant composite FK, lifecycle check, and one pending request per Tenant (`V1:555-587`).

`Phase1FullPostgresMigrationTest` verifies all 20 tables and prohibited-column absence (`src/test/java/com/uit/dormmanagement/repositories/Phase1FullPostgresMigrationTest.java:71-124`), vehicle quota/card constraints (`:381-417`), and the final User status domain (`:753-788`). These tests were inspected, not rerun. No Phase 1 schema change is required by the current findings. Cross-row vehicle replacement correctness still belongs in the service transaction, as Logical ERD T10 states (`docs/Logical-ERD.md:889-895`).

## 3. Current implementation inventory

| Domain | Controllers/API | Services and infrastructure | Persistence | Current maturity |
|---|---|---|---|---|
| Authentication | `AuthController` exposes login, refresh, logout, logout-all, change password, forgot/verify/reset (`src/main/java/com/uit/dormmanagement/controllers/AuthController.java:31-118`) | `AuthServiceImpl`, `LoginAttemptServiceImpl`, `TokenRevocationServiceImpl`, `OtpServiceImpl`, `JwtService`, `JwtAuthenticationFilter` | `UserRepository`, `TenantRepository`, `StaffProfileRepository`; Redis only for transient auth state | Partial; correct identifier routing and basic token rotation, but global token invalidation and Tenant recovery are wrong |
| Technician/RBAC | `UserController` exposes `/me` and Manager-only Technician CRUD/status (`UserController.java:40-108`) | `UserServiceImpl` | `users`, `staff_profiles` | Partial; CRUD/search/lock exists; initial password contract is wrong |
| Room/Bed | Manager Room and Bed controllers (`RoomController.java:42-110`; `BedController.java:28-38`) | `RoomServiceImpl`, `BedServiceImpl` | `rooms`, `beds`, amenities, registry, contracts | Substantially implemented; missing guards/endpoints around rename, archive/restore and safe delete |
| Tenant profile | Manager-only Tenant list/detail/update (`TenantController.java:35-70`) | `TenantServiceImpl` | `tenants`, `users`, contracts, vehicles | Partial; no Tenant self-update and Manager identity correction is incomplete |
| Vehicles | Tenant register/current vehicle; Manager list/detail/create/update/issue/revoke/delete (`TenantVehicleController.java:35-59`; `AdminVehicleController.java:43-113`) | `VehicleServiceImpl`, `StorageService` | `vehicles`; `vehicle_change_requests` entity/repository exists but unused | Incomplete and state-unsafe; change-request workflow and initial rejection are absent |
| Files | `/uploads/**` static resource handler; local and placeholder OCI services | `LocalStorageServiceImpl`, `OciObjectStorageServiceImpl` | Vehicle file-key columns | Unsafe/incomplete; local evidence is public and OCI implementation only simulates a URL |
| Error handling | Global response/error advice | `AppExceptionHandler` | N/A | General mapping exists; database conflicts are flattened to HTTP 400 (`AppExceptionHandler.java:173-180`) |

No Phase 2 scheduled job is required. Onboarding/checkout scheduling or finance jobs are later engineering phases.

## 4. API and service map

| Route | Role | Current operation | Audit result |
|---|---|---|---|
| `POST /api/v1/auth/login` | Public | Common identifier login | Retain route; correct lock threshold tests and token lifecycle |
| `POST /api/v1/auth/refresh-token` | Public | Refresh rotation | Retain route; enforce user-wide session generation |
| `POST /api/v1/auth/logout` | Authenticated | Revokes supplied refresh and current access JTI | Retain |
| `POST /api/v1/auth/logout-all` | Authenticated | Deletes all refresh sessions and blacklists only current access JTI | Refactor semantics |
| `POST /api/v1/auth/change-password` | Authenticated | Old password, new password, no OTP | Retain contract; invalidate every old token |
| `POST /api/v1/auth/forgot-password` | Public | Email-only request | Breaking DTO change to generic `identifier` |
| `POST /api/v1/auth/verify-forgot-password-otp` | Public | Email + OTP | Bind recovery challenge to resolved User, not caller-supplied email |
| `POST /api/v1/auth/reset-password` | Public | Email + reset token + new password | Bind token to resolved User; invalidate old tokens |
| `GET /api/v1/users/me` | Authenticated | Role-specific profile | Retain |
| `/api/v1/users/technicians/**` | Manager | Create/list/detail/update/status | Retain routes; change create request/response for generated password |
| `GET /api/v1/admin/rooms/matrix` | Manager | Active Room/Bed matrix | Retain |
| `POST /api/v1/admin/rooms` | Manager | Atomic Room/Bed/registry/amenity creation | Retain |
| `PUT /api/v1/admin/rooms/{id}` | Manager | Room Code/gender/price/amenity update | Retain route; add locks and complete vacancy/contract guards |
| `DELETE /api/v1/admin/rooms/{id}` | Manager | Archives a Room | Rename semantics in OpenAPI/client contract; do not represent archive as physical delete |
| `GET /api/v1/admin/rooms` | Manager | Active rooms only | Retain; add separate archived listing/filter contract |
| `GET /api/v1/admin/rooms/{id}` | Manager | Active Room detail | Retain |
| `PATCH /api/v1/admin/beds/{id}/status` | Manager | AVAILABLE/MAINTENANCE | Retain; add locking/regression cases |
| `GET/PUT /api/v1/admin/tenants/**` | Manager | List/detail/update limited fields | Retain routes; expand Manager correction DTO safely |
| Tenant profile self-update | Tenant | Missing | Add a `/api/v1/tenant/profile` or `/api/v1/users/me/profile` contract after approach approval |
| `GET /api/v1/tenant/vehicles/me` | Tenant | Returns one active/pending vehicle or null | Extend to current + history/change-request view without exposing file keys |
| `POST /api/v1/tenant/vehicles` | Tenant | Multipart registration; both images and plate effectively required | Refactor validation by vehicle type and private file handling |
| `/api/v1/admin/vehicles/**` | Manager | List/detail/direct create/update/issue/revoke/delete | Preserve list/detail/issue where conforming; restrict/remove generic mutations that rewrite history |
| Initial registration rejection | Manager | Missing | Implement |
| Vehicle Change Request Tenant/Admin APIs | Tenant/Manager | Missing | Implement |

Controller tests use standalone `MockMvcBuilders` without the Spring Security filter chain; for example `VehicleControllerTest.java:1-73` and `UserControllerTest.java:1-64`. They verify routing/serialization, not actual 401/403 method security.

## 5. FR-level implementation gap matrix

Status meanings: `KEEP`, `REFACTOR`, `EXTEND`, `IMPLEMENT`, `INTEGRATE_LATER`, `OUT_OF_SCOPE`.

| FR | Requirement | Current implementation | Evidence | Status | Required work | Priority | Dependencies |
|---|---|---|---|---|---|---|---|
| FR-01 | Active Room/Bed matrix with filters, occupants, amenities and derived occupancy | Reads active Rooms, Beds, active contracts and amenities; occupancy is counted from Bed status | `RoomServiceImpl.java:57-98,272-315`; SRS `:253-277` | KEEP | Regression tests for calculation, archived exclusion, empty results | P1 | Phase 1 schema |
| FR-01 | Manager may only set `AVAILABLE ↔ MAINTENANCE`; never manually set `OCCUPIED` | Service rejects OCCUPIED target and refuses moving an occupied/contracted Bed to maintenance | `BedServiceImpl.java:27-68`; `BedServiceTest.java:71-137` | KEEP | Add pessimistic lock/concurrent update test and active-contract guard regression in both directions | P1 | Contract read model |
| FR-01 | Prevent concurrent occupation of one Bed | PostgreSQL active-contract uniqueness exists; onboarding performs occupation later | `V1:328-329`; SRS `:273-275` | INTEGRATE_LATER | Phase 3 onboarding must lock Bed and rely on unique constraint | P0 | Phase 3 FR-09 |
| FR-02 | Atomically create Room, exact 4/6/8 Beds, registry and amenities | One `@Transactional` method creates all records and rejects missing Amenity IDs | `RoomServiceImpl.java:100-147,333-350`; V1 exact-count triggers `:190-221` | KEEP | Preserve; add PostgreSQL rollback/concurrent-code tests to Phase 2 acceptance | P1 | None |
| FR-02 | Canonical, protected and conditionally reusable Room Code | Service checks Room and registry; DB guards current/protected codes | `RoomServiceImpl.java:103-106`; `V1:178-300` | KEEP | Map DB race conflicts to stable 409 error | P1 | Exception mapping |
| FR-03 | Update price/amenities without altering historical prices | Updates Room base price and amenities; Beds derive display price; contracts keep snapshots | `RoomServiceImpl.java:168-175,305-315`; V1 contract snapshots `:303-322` | KEEP | Preserve |
| FR-03 | Rename only when no occupant and no active contract; update registry/Slot Keys atomically | Rename updates registry and Slot Keys, but performs neither vacancy nor active-contract check | `RoomServiceImpl.java:151-159,353-386`; SRS `:319-329`; Logical T02 `:837-842` | REFACTOR | Lock Room/Beds; recheck Bed/contract conditions; translate constraint conflicts; test rollback/concurrency | P0 | Contract repository |
| FR-03 | Change gender policy only when no occupant and no active contract | Checks occupied Beds only | `RoomServiceImpl.java:161-166`; SRS `:321,327` | REFACTOR | Add active-contract predicate under Room lock | P0 | Contract repository |
| FR-03 | Floor and capacity immutable | Update DTO exposes neither field | `UpdateRoomRequest.java:39-52` | KEEP | Controller regression test that unknown/forbidden fields cannot mutate entity | P2 | None |
| FR-04 | Archive only when vacant/no active contract | `deleteRoom` performs the correct two checks and sets `archivedAt` | `RoomServiceImpl.java:198-219` | EXTEND | Keep service logic, rename operation/error language to archive, expose explicit API semantics | P1 | API contract |
| FR-04 | Restore archived Room unchanged | Service/native update exists; no controller route | `RoomServiceImpl.java:221-228`; `RoomRepository.java:34-36`; `RoomController.java:42-110` | IMPLEMENT | Add Manager restore endpoint and tests; no Bed status mutation | P1 | Archived-room lookup |
| FR-04 | Physically delete only a never-used incorrect Room after full history check | Service locks Room and checks protected registry, then deletes configuration; it does not explicitly inspect all historical references and has no route | `RoomServiceImpl.java:230-243`; SRS `:340-344` | EXTEND | Add complete preflight/readable conflict mapping and Manager endpoint; retain FK/registry as final guard | P1 | Contract/Ticket references; teammate module read-only dependency |
| FR-05 | Case-insensitive combined active-room search/filter | Implemented through Specification; archived Rooms hidden by `@SQLRestriction` | `RoomRepository.java:44-70`; `Room.java:28-31` | KEEP | Preserve |
| FR-05 | Separate archived-room list and restore entry point | Missing | `RoomController.java:87-110`; SRS `:365-376` | IMPLEMENT | Add archived read path using explicit native/projection query unaffected by `@SQLRestriction` | P1 | FR-04 restore |
| FR-06 | Manager list/filter/detail with history | Implemented; detail includes contracts and vehicle history | `TenantController.java:35-70`; `TenantServiceImpl.java:48-68,95-189` | KEEP | Preserve; correct later-phase DTO compatibility separately | P1 | Phase 3/4 read models |
| FR-06 | Manager can correct name, CCCD, gender, DOB, email and contact fields under rules | Current DTO/service only update name, phone, address, DOB and emergency fields | `UpdateTenantRequest.java:9-26`; `TenantServiceImpl.java:70-93`; SRS `:404-419` | EXTEND | Add Manager correction DTO; canonical email uniqueness; CCCD validation/unique conflict; current-room gender guard; update existing User, never create one | P1 | User/Tenant repositories; active contract/Room read |
| FR-06 | Tenant self-updates only phone/address/emergency contact | No self-update endpoint | `TenantController.java:27-70`; `UserController.java:40-48` | IMPLEMENT | Add authenticated ownership-derived self-update; exclude identity/email/status fields from DTO | P1 | JWT principal |
| FR-06 | Email globally canonical/unique; phone not globally unique | PostgreSQL/User mapping enforces email; service permits repeated Tenant phone | `V1:8-18`; `TenantServiceImplTest.java:189-203` | KEEP | Translate unique DB race to 409; remove obsolete duplicate-phone errors from affected flows | P1 | Exception mapping |
| FR-07 | Retain Tenant/User/history and allow post-checkout login/history | Persistence retains records and auth does not reject `CHECKED_OUT`; Manager detail reads history | `TenantServiceImpl.java:132-189`; `AuthServiceImpl.java:65-87`; SRS `:430-445` | EXTEND | Add Tenant-owned historical read APIs in later Tenant Portal; Phase 2 must preserve login and enforce residency on current-residency actions | P1 | Phase 3 checkout; Phase 4 invoices; Phase 5 portal |
| FR-07 | Post-checkout Tenant cannot register vehicle | Registration checks `ResidentStatus.ACTIVE` | `VehicleServiceImpl.java:213-220` | KEEP | Apply same guard to change requests; authorization tests | P1 | Vehicle changes |
| FR-07 | Checkout cancels/revokes vehicle state correctly | Checkout is outside this engineering phase | Logical T07 `:871-875` | INTEGRATE_LATER | Define callable vehicle lifecycle operation for Phase 3 checkout; do not implement checkout here | P0 | Phase 3 FR-13 |
| FR-08 | Vehicle list/search/filter and Tenant ownership | Manager list/detail and Tenant lookup by principal exist | `VehicleServiceImpl.java:42-58,192-204`; controller role guards | EXTEND | Add Tenant history and change-request status; security integration tests | P1 | Response redesign |
| FR-08 | Type-specific plate/evidence rules | Tenant DTO requires plate for every type; controller requires two images for every type; DB correctly makes them optional for bicycles | `TenantRegisterVehicleRequest.java:9-13`; `TenantVehicleController.java:51-58`; `V1:543-547`; SRS `:454-462` | REFACTOR | Conditional service validation; optional multipart parts; content validation boundary; reject invalid combinations before storage | P0 | Storage boundary |
| FR-08 | Do not invent a global/active license-plate uniqueness rule | Service rejects a plate used by another active vehicle, but neither SRS nor Logical ERD defines that uniqueness invariant | `VehicleServiceImpl.java:258-265`; no plate unique constraint in `V1:513-553` | REFACTOR | Remove the unsupported application uniqueness rule; normalize plates and enforce only the documented type/requiredness rules | P1 | Vehicle validation |
| FR-08 | One pending/issued vehicle per active Tenant, concurrency safe | Service prechecks count; PostgreSQL partial unique index is final guard | `VehicleServiceImpl.java:249-255`; `V1:549-550`; PostgreSQL test `:381-417` | EXTEND | Catch unique race as stable 409; add concurrent integration test | P0 | PostgreSQL integration |
| FR-08 | Issue physical card only from `PENDING_CARD`, unique among issued | State guard and uniqueness precheck exist; DB index is final guard | `VehicleServiceImpl.java:131-156`; `V1:535-552` | KEEP | Add Manager actor/event boundary and race mapping; preserve route | P1 | Notification later |
| FR-08 | Reject initial registration with reason, actor and time | Missing despite schema columns | `Vehicle.java:81-89`; no reject controller/service operation | IMPLEMENT | Add locked transition `PENDING_CARD → REJECTED`; require Manager/reason; notification outbox boundary only, actual notification later | P0 | Staff identity; Phase 5 notifications |
| FR-08 | Preserve history; do not rewrite/soft-delete arbitrary vehicle states | Generic update mutates any state; delete maps most states to `REVOKED`; revoke has no source-state guard | `VehicleServiceImpl.java:108-129,158-190` | REFACTOR | Replace generic mutation semantics with allowed commands; only `ISSUED → REVOKED`, `PENDING_CARD → CANCELLED`; never rewrite rejected/replaced history | P0 | State-machine errors |
| FR-08 | Vehicle Change Request create/list/detail/approve/reject | Entity/repository only; no service/controller/DTO | `VehicleChangeRequest.java:26-87`; `VehicleChangeRequestRepository.java:11-23` | IMPLEMENT | Implement independent workflow and ownership APIs | P0 | Vehicle transaction design |
| FR-08 | Approval atomically keeps card: old `REPLACED`, new `ISSUED`, request `APPROVED` | Missing | SRS `:483-500`; Logical T10 `:889-895` | IMPLEMENT | Pessimistically lock request and old vehicle; one transaction; retry/idempotency behavior; preserve both IDs/card/history | P0 | DB conditional uniqueness; staff actor |
| FR-08 | Private evidence; never expose raw object keys/public files | `/uploads/**` is permit-all; response labels stored keys as URLs; OCI implementation simulates public URL | `SecurityConfig.java:80-95`; `WebMvcResourceConfig.java:19-30`; `VehicleServiceImpl.java:276-291`; `OciObjectStorageServiceImpl.java:48-104` | REFACTOR | Stop public serving for vehicle evidence; response uses authorized media references, not keys; real OCI/presigned implementation remains Phase 5 | P0 | Phase 5 OCI |
| FR-08 | Approval/rejection notifications | Notification persistence exists, feature is later | SRS `:481,487-501`; task scope | INTEGRATE_LATER | Define post-commit domain event/application callback and deterministic dedupe input; implement REST notifications in Phase 5 | P2 | Phase 5 |
| FR-21 | Tenant CCCD login; staff email login; no Tenant email/phone/username login | Correct branching: staff email filtered against TENANT, fallback CCCD | `AuthServiceImpl.java:292-304`; tests `AuthServiceTest.java:105-160` | KEEP | Add format/role integration tests and retain generic error | P0 | None |
| FR-21 | Bcrypt password hash | BCrypt strength 12 configured and used | `SecurityConfig.java:52-55`; `AuthServiceImpl.java:79-82` | KEEP | Preserve | P1 | None |
| FR-21 | Lock for 15 minutes after more than five consecutive failures | Redis lock is 15 minutes but activates at `>=5`, and test codifies fifth-attempt lock | `LoginAttemptServiceImpl.java:17-47`; `LoginAttemptServiceTest.java:46-59`; SRS `:825` | REFACTOR | Lock on sixth failure; reset only on success; test expiry/canonical identifier | P0 | Redis |
| FR-21 | Access + rotating Refresh Tokens in Redis | Refresh token is recorded, rotated and reuse revokes refresh sessions | `AuthServiceImpl.java:90-131,274-289`; `TokenRevocationServiceImpl.java:22-47` | EXTEND | Add a per-User random Redis session generation checked by both token types; do not enumerate issued Access JTIs | P0 | JWT filter/Redis |
| FR-21 | Locked Technician cannot use old token | Filter reloads User and checks locked status on every request | `JwtAuthenticationFilter.java:51-70`; `CustomUserDetailsService.java:51-59` | KEEP | Add real security-chain integration test | P0 | Security test config |
| FR-21 | Backend role/ownership enforcement | Method role guards exist; ownership generally derives from principal in Tenant vehicle API | Controller `@PreAuthorize` annotations; `VehicleServiceImpl.java:194-216` | EXTEND | Cover every new Tenant resource by principal-derived ownership; add 401/403 integration tests because current standalone tests bypass security | P0 | Spring Security tests |
| FR-22 | Logout current session | Current Access JTI blacklist plus supplied Refresh revocation | `AuthServiceImpl.java:133-163` | KEEP | Validate supplied Refresh belongs to current authenticated User; test missing/other-user token | P0 | Principal-aware logout |
| FR-22 | Logout-all and lock/password changes invalidate all old Access Tokens | `revokeAllUserSessions` deletes only Refresh records; only current Access JTI is blacklisted on logout-all | `AuthServiceImpl.java:165-183,205-209,267-271`; `TokenRevocationServiceImpl.java:69-80` | REFACTOR | Rotate a random Redis User session generation for logout-all, password change/reset and lock; enforce it in token validation | P0 | FR-21 token design |
| FR-23 | Manager-only create/list/search/filter/detail/update/lock/reactivate | Present with method guards | `UserController.java:50-108`; `UserServiceImpl.java:129-223` | KEEP | Preserve endpoints; security-chain tests | P1 | None |
| FR-23 | Random initial Technician password, shown once; hash only persisted | Caller supplies password and response does not carry one-time credential | `CreateTechnicianRequest.java:45-59`; `UserServiceImpl.java:95-127`; SRS `:852-856` | REFACTOR | Generate cryptographically secure password server-side; remove request password; return/display once without logs/persistence | P1 | API breaking change |
| FR-23 | Lock revokes all sessions; reactivate does not restore old sessions | Status changes and refresh sessions are revoked on lock; access is blocked while locked, but global old-access invalidation is incomplete after reactivation | `UserServiceImpl.java:167-186`; filter `:51-70` | EXTEND | Rotate the User session generation on lock so old tokens stay invalid after reactivation | P0 | Token design |
| FR-23 | Manager email immutable | No Manager update-email endpoint exists | `UserController.java:40-108` | KEEP | Regression authorization/absence test | P2 | None |
| FR-24 | Authenticated change password with old password and no OTP | Correct request flow and BCrypt update | `AuthServiceImpl.java:185-210`; SRS `:868-871` | EXTEND | Add complete token invalidation and policy tests | P0 | FR-22 |
| FR-24 | Forgot password accepts Tenant CCCD or staff email and stays enumeration-resistant | DTO and service are email-only and directly find any User by email, including Tenant | `ForgotPasswordRequest.java:6-9`; `AuthServiceImpl.java:212-230`; SRS `:873-879` | REFACTOR | Use generic identifier resolution matching login; always generic response/timing; send OTP to resolved `users.email` | P0 | Mail/Redis |
| FR-24 | OTP 6 digits, 5 minutes, limited attempts, one use; reset challenge bound to one User | OTP TTL/one-use/three attempts exists, but keys and reset requests are controlled by caller-supplied email | `OtpServiceImpl.java:21-129`; `VerifyOtpRequest.java:16-22`; `ResetPasswordRequest.java:29-37` | REFACTOR | Key challenge by opaque recovery ID/User UUID; bind reset token to User; use atomic Redis consume/attempt operations | P0 | Redis concurrency |
| FR-24 | Manager assists inaccessible-email case by changing email or issuing new random password | Technician email update exists; Tenant email correction and assisted credential reset are absent | `UserServiceImpl.java:188-211`; `TenantServiceImpl.java:70-93` | EXTEND | Include in Manager Tenant/Technician commands; no multi-level approval or audit-history feature | P1 | FR-06, FR-23 |

## 6. BR coverage and state-machine findings

| BR | Finding | Result |
|---|---|---|
| BR-01 | Phase 1 persistence and most Room creation/derived occupancy behavior conform. Rename lacks the vacancy/active-contract gate; archive/restore/delete APIs are incomplete. | Partial |
| BR-02 | Onboarding gender enforcement is Phase 3. Current Room gender update checks Beds but not active contracts. Manager Tenant gender correction also needs current-room policy validation. | Partial |
| BR-08 | Database constraints are strong. Multi-record Room rename and future Vehicle replacement need explicit locks, transactions and concurrency tests. Generic vehicle mutation currently threatens history. | Partial / high risk |
| BR-09 | Login identifiers and canonical email are correct. Random initial credentials, Tenant recovery identifier, manager-assisted identity correction and universal token invalidation are incomplete. | Partial / high risk |
| BR-10 | The schema models all six/four states and conditional uniqueness correctly. Services implement only registration/issue/revoke fragments and misuse state transitions; change requests are absent. | Major gap |
| BR-11 | Method guards exist, but controller tests do not load Security. Tenant profile ownership and future vehicle request ownership need service-level verification. | Partial |

### 6.1 Required state machines

**Vehicle:**

- `PENDING_CARD → ISSUED` only by Manager card issuance.
- `PENDING_CARD → REJECTED` only by Manager with reason/actor/time.
- `PENDING_CARD → CANCELLED` only for a legitimate cancellation such as checkout.
- `ISSUED → REVOKED` only after physical card return/revocation.
- `ISSUED → REPLACED` only inside approved Vehicle Change Request; same transaction creates the successor `ISSUED` vehicle with the same `card_code`.
- `REJECTED`, `REVOKED`, `REPLACED`, and `CANCELLED` are terminal historical records in Phase 2.

**Vehicle Change Request:**

- `PENDING → APPROVED`, `REJECTED`, or `CANCELLED`; processed states are terminal.
- Repeated approval/rejection must fail deterministically and must not create a second vehicle.
- The database proves only parts of this state machine. Repository locking plus service checks must prove old vehicle ownership/status, request status, actor role and card handoff.

**Access/Refresh tokens:**

- Single logout revokes the current Access JTI and one Refresh JTI.
- Logout-all, lock, password change and password reset advance a per-User Redis token version and revoke Refresh sessions.
- `JwtAuthenticationFilter` must reject a token whose version differs from the current User version, in addition to JTI blacklist and current `users.status` checks.

## 7. Security, privacy and error-handling risks

| Risk | Evidence | Severity | Required disposition |
|---|---|---|---|
| Old Access Tokens survive password change/reset/logout-all | `TokenRevocationServiceImpl.java:69-80` only deletes Refresh tokens | P0 | User-wide random Redis session generation checked in filter |
| Public vehicle evidence | `/uploads/**` is permit-all and statically mapped (`SecurityConfig.java:80-95`; `WebMvcResourceConfig.java:19-30`) | P0 | Remove public evidence path; authorized retrieval boundary |
| OCI service is a stub | It logs “Simulating OCI” and returns a constructed URL (`OciObjectStorageServiceImpl.java:90-104`) | P1 / later integration | Do not claim OCI; Phase 5 implements private objects/presigned access |
| Caller-controlled recovery subject | Forgot/verify/reset all accept email; Tenant CCCD is unsupported (`AuthServiceImpl.java:212-271`) | P0 | Opaque User-bound recovery challenge |
| Authentication tests miss real filter chain | Controller tests build standalone MockMvc | P0 test gap | Add `@SpringBootTest`/security integration tests for 401/403/revocation |
| DB conflicts return generic HTTP 400 | `AppExceptionHandler.java:173-180` | P1 | Map expected unique/integrity conflicts to stable 409 domain errors |
| Sensitive identifiers in logs | Forgot password logs raw email (`AuthServiceImpl.java:217-229`); login-attempt service logs raw identifier (`LoginAttemptServiceImpl.java:35-46`) | P1 | Mask/hash identifiers and never log OTP/reset token |

## 8. Reusable components

Preserve these unless a focused test disproves them:

- `ResponseFactory`, response envelopes and `AppExceptionHandler` structure.
- BCrypt encoder and the filter’s per-request User reload.
- Login identifier selection in `AuthServiceImpl.findUserByIdentifier`.
- Refresh-token JTI storage/rotation and single-JTI access blacklist concepts.
- Method-level `@PreAuthorize` declarations and the three fixed roles.
- Phase 1 entities, enums, repositories, V1 constraints and PostgreSQL tests.
- Room creation transaction, Room Code Registry persistence, exact Bed generation, invalid-Amenity rejection, derived occupancy/price, active-room filtering.
- Manager/Tenant principal-derived lookup patterns.
- Vehicle list/search and card issuance source-state guard, subject to the stricter lifecycle transaction.

## 9. Test baseline and missing coverage

### 9.1 Existing tests inspected

| Area | Existing evidence | Important coverage present | Important gaps |
|---|---|---|---|
| Auth services | `AuthServiceTest`, `LoginAttemptServiceTest`, `OtpServiceTest`, `TokenRevocationServiceTest` | CCCD login, Tenant email rejection, refresh rotation, lock, OTP basics | Logout-all old access, password invalidation, sixth-attempt threshold, User-bound recovery, Redis races, generic timing |
| Technician | `UserServiceTest` | Create/list/update/status | Random password, one-time response, reactivation rejecting pre-lock token, real Manager authorization |
| Rooms/Beds | `RoomServiceTest`, `BedServiceTest`, controller tests | Create, derived price, registry rename mechanics, archive guards, status transitions | Rename with occupant/contract, gender with contract, restore, archived list, safe physical delete, concurrent rename/status |
| Tenant | `TenantServiceImplTest`, `TenantControllerTest` | Manager list/detail/contact update, duplicate phone allowed | Manager CCCD/gender/email correction, Tenant self update/ownership, post-checkout authorization |
| Vehicles | `VehicleServiceImplTest`, `VehicleControllerTest`, Phase 1 PostgreSQL test | Registration happy path/quota precheck, issue/revoke/cancel fragments, DB quota/card constraints | Type matrix, rejection, state-machine negatives, history, change request, card handoff, concurrency/idempotency, ownership/security, private files |

### 9.2 Required PostgreSQL and security tests

1. Concurrent Room Code creation/rename yields one success and one stable 409, never partial Slot Keys.
2. Rename/gender change rejects active contracts even if Bed status is inconsistent.
3. Physical Room deletion rejects every historical reference; archive/restore retains registry and Bed IDs.
4. Concurrent vehicle registration for one Tenant yields one `PENDING_CARD` row.
5. Concurrent card issuance with the same canonical card yields one `ISSUED` row.
6. Concurrent Vehicle Change Request creation yields one `PENDING` request.
7. Concurrent approval/retry creates exactly one successor, preserves the card, and leaves old/new/request in the required final states.
8. Real Spring Security chain: anonymous 401, wrong role 403, Tenant ownership isolation, locked account denial, revoked-before Access rejection.
9. Redis integration: refresh reuse, logout-all, lock/reactivate, change/reset password, OTP single-use and concurrent verify.
10. File tests: unauthorized evidence access denied; response never exposes raw object key/public URL; invalid type/content rejected before durable metadata write.

## 10. Cross-phase contracts and exclusions

| Consumer/owner | Phase 2 contract | Deferred implementation |
|---|---|---|
| Phase 3 onboarding | Stable login/User/Tenant model; Room/Bed availability/locking; Room Code protection | Atomic Tenant/User/Contract/Bed/first Invoice creation, returning-Tenant debt checks |
| Phase 3 checkout | A vehicle lifecycle command that can revoke `ISSUED`, cancel `PENDING_CARD`, and cancel pending change request in the caller's transaction | Checkout orchestration and contract/Bed/Tenant updates |
| Phase 4 finance | Preserve post-checkout Tenant access/ownership semantics | Invoice/payment APIs and debt proof |
| Phase 5 Tenant Portal | Ownership-safe Tenant profile/vehicle query contracts | Complete historical portal views |
| Phase 5 notifications | Stable vehicle outcome boundary plus deterministic event/dedupe inputs | PostgreSQL REST notification creation/delivery; no WebSocket/broker requirement |
| Phase 5 OCI | Store private file keys and expose authorized media references | Real OCI client, private bucket access and expiring URLs |
| Teammate Maintenance/Violations | Room safe-delete preflight may query whether historical records exist; no mutation | FR-17–FR-19 workflows remain outside this plan |

FR-11 Room Transfer is product Phase 2 and explicitly outside the current engineering Phase 2. FR-09/10/12/13, finance, dashboard, notifications, Maintenance and Violations are not to be absorbed into implementation tasks.

## 11. Priority summary

### P0

1. User-wide Access Token invalidation after logout-all, lock, password change/reset.
2. Recovery by CCCD for Tenant/email for staff with User-bound Redis challenge.
3. Correct login lock threshold and Redis concurrency semantics.
4. Room rename and gender change vacancy/active-contract guards under lock.
5. Vehicle state-machine restrictions and initial rejection.
6. Complete Vehicle Change Request transaction, concurrency and idempotency.
7. Vehicle type-specific evidence validation and private file boundary.
8. Real authorization/ownership integration tests.

### P1

1. Explicit Room archive/restore/safe-delete APIs and archived list.
2. Manager Tenant identity correction and Tenant self-update.
3. Server-generated Technician password shown once.
4. Stable 409 mapping for integrity races.
5. Tenant vehicle history/current/request response contracts.

### P2

1. Remove obsolete auth/phone/username error vocabulary in touched paths.
2. Define later notification event/dedupe payloads without implementing notifications.
3. Documentation/OpenAPI wording corrections for archive and vehicle states.

## 12. Audit conclusion

The Phase 1 database is a suitable foundation and should remain unchanged. Room/Bed is the most mature Phase 2 module; Authentication has a usable core but a P0 invalidation/recovery gap; Tenant management has correct Manager read behavior but incomplete edit/ownership contracts; Vehicle management has a correct persistence model but only a legacy fragment of the required business workflow.

Phase 2 should be implemented as bounded extensions around current services and routes, not as a package rewrite. The design must first decide the degree of command/policy extraction used to make security and vehicle transactions testable. That choice is intentionally left for the required Superpowers brainstorming approval gate.

## 13. Verification record

- Executed: `./mvnw -q -DskipTests compile` — PASS, default project configuration, tests skipped.
- Not executed: Maven tests, Docker/Testcontainers, application startup, Redis integration, OCI integration.
- Historical only: 223/223 Maven tests and PostgreSQL startup recorded at `docs/audits/backend-srs-v3.4.1-audit.md:403-424`.
- No Java source, Flyway migration, configuration, test, database, Redis, OCI, SRS or ERD file was changed by this audit.
