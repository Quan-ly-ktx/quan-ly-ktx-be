# Phase 2 Focused Brownfield Hardening — Design Overview

**Date:** 2026-11-16
**Status:** Revised — ready for final explicit approval
**Selected approach:** A — Focused Brownfield Hardening
**Audit input:** `docs/audits/phase-2-current-state-audit.md`

## 1. Intent

Engineering Phase 2 brings the existing Authentication/RBAC, Room/Bed, Tenant Profile, and Vehicle modules into conformance with SRS v3.4.1 without replacing correct Phase 1 persistence or rewriting the application architecture.

The design preserves existing controllers, response envelopes, entities, repositories, and service behavior when they already comply. Focused collaborators are introduced only where one of these boundaries must be explicit:

- Redis token/recovery atomicity and user-wide invalidation.
- Room mutation locking and historical-reference checks.
- Tenant self-update versus Manager identity correction.
- Vehicle lifecycle commands, Vehicle Change Request transactions, and private evidence access.

## 2. Scope

### 2.1 In scope

- FR-01–FR-05: Room and Bed management.
- FR-06–FR-08: Tenant Profile and Vehicle management.
- FR-21–FR-24: Authentication, Authorization, and Technician administration.
- BR-01, BR-02, BR-08, BR-09, BR-10, BR-11 where they govern those functions.
- API, validation, authorization, transaction, concurrency, error, and test changes necessary for those requirements.

### 2.2 Explicitly deferred

- FR-09, FR-10, FR-12, FR-13: Phase 3 onboarding/contract/renewal/checkout.
- Finance and payment workflows: Phase 4.
- Dashboard, Tenant Portal completion, REST notifications, and real OCI integration: Phase 5.
- FR-11 Room Transfer: future product phase.
- FR-17–FR-19 Maintenance/Violations implementation: teammate-owned.

Phase 2 may define stable integration commands or future notification call points needed by later phases, but it must not implement the later workflow.

## 3. Existing baseline to preserve

- Java 25, Spring Boot 4.1.2, Spring MVC/Security/Data JPA, PostgreSQL/Flyway, Redis and JJWT (`pom.xml:5-191`).
- Standard `SingleResponse`, `SuccessResponse`, `PageResponse`, `ResponseFactory`, and `BaseAppException` conventions.
- The final 20-table `V1__clean_baseline.sql` and current JPA mappings. No Flyway change is planned.
- Exactly three User roles and exactly two User statuses (`ACTIVE`, `LOCKED`).
- Method security with `@PreAuthorize` and authenticated principal-derived Tenant ownership.
- Current correct Room creation, Room Code Registry, exact Bed generation, derived occupancy/price, and active-room filtering.
- Current correct login identifier split, BCrypt hashing, Refresh Token rotation concept, current-User reload in the JWT filter, and Manager Technician list/update/status endpoints.
- Current Vehicle/Vehicle Change Request schema, enum domains, conditional uniqueness, and history columns.

## 4. Considered approaches

### 4.1 A — Focused Brownfield Hardening (selected)

Keep current module surfaces and add narrow collaborators around security state, Room mutation guards, Tenant permission-specific commands, and Vehicle lifecycle transactions. This has the lowest regression surface and follows the user-selected primary principle.

### 4.2 B — Domain application-service extraction (not selected)

Extract every command/query into new application services and turn current services into façades. This offers cleaner theoretical boundaries but causes substantial movement of already-correct code.

### 4.3 C — Parallel versioned APIs (not selected)

Add new versioned routes and retain all legacy routes through adapters. This reduces immediate client migration pressure but duplicates behavior and is unjustified without a deployed compatibility requirement.

## 5. Module specifications

| Domain | Specification | Primary classification |
|---|---|---|
| Authentication & RBAC | `docs/superpowers/specs/2026-11-16-authentication-rbac-design.md` | KEEP / REFACTOR / EXTEND |
| Room & Bed | `docs/superpowers/specs/2026-11-16-room-bed-management-design.md` | KEEP / REFACTOR / EXTEND / IMPLEMENT |
| Tenant Profile | `docs/superpowers/specs/2026-11-16-tenant-profile-management-design.md` | KEEP / EXTEND / IMPLEMENT |
| Vehicle Management | `docs/superpowers/specs/2026-11-16-vehicle-management-design.md` | REFACTOR / EXTEND / IMPLEMENT |

Each document is independently reviewable. Approval may identify changes in one domain without invalidating correct decisions in the others.

## 6. Common architecture rules

### 6.1 Authorization

- Controllers retain coarse role guards.
- Services derive Tenant ownership from authenticated `UserPrincipal.getId()`; Tenant-supplied Tenant IDs are never trusted.
- Manager commands resolve the authenticated Manager to an active `StaffProfile` when an actor FK is required.
- Role checks are tested through the real Spring Security chain, not only standalone MockMvc.

### 6.2 Transactions and concurrency

- Single-record reads remain ordinary repository queries.
- State transitions use only the scoped database locks necessary for their invariant; no global locking framework is introduced.
- Occupancy-sensitive Room mutations lock `Room → Bed` in stable UUID order. Future Phase 3 Onboarding must adopt the same order; Phase 2 does not refactor Onboarding.
- Vehicle registration uses service validation plus existing PostgreSQL uniqueness constraints. It adds no Redis lock, idempotency key, retry framework or special concurrent-registration protocol.
- Vehicle lifecycle operations that touch both records use a bounded `Vehicle → VehicleChangeRequest` row-lock order. Approval rechecks both lifecycle states and commits or rolls back the replacement as one database transaction.
- PostgreSQL partial unique indexes remain the final concurrency guard for cross-request uniqueness.
- Expected constraint races are translated to stable domain `409 Conflict` responses.
- No distributed transaction, broker, or microservice is introduced.

### 6.3 Validation

- Bean Validation handles structural request validation.
- Service validation handles role, ownership, current state, cross-field rules, and cross-entity invariants.
- PostgreSQL remains the final enforcement layer for domains, FKs, checks, and conditional uniqueness.
- No requirement is weakened to match a legacy DTO.

### 6.4 Sensitive files

- Database columns store opaque private object keys only.
- Raw keys and permanent public URLs are never returned by Phase 2 DTOs.
- Phase 2 removes public local access for vehicle evidence and uses an authenticated Backend retrieval boundary.
- This development/demo system has no existing Vehicle image files requiring migration. Phase 2 stores only new Vehicle evidence privately and adds no migration utility or compatibility adapter.
- Phase 5 replaces the local retrieval implementation with private OCI objects and presigned URLs valid no longer than 15 minutes. The API authorization semantics remain stable.

### 6.5 Error contract

- `400`: malformed request or invalid field combination.
- `401`: missing/invalid/revoked authentication, a legacy/mismatched session generation, or a token whose current User is `LOCKED`.
- `403`: an authenticated active identity without the required role, ownership, or current-residency permission.
- `404`: resource not visible/found within the caller's authorized scope.
- `409`: state transition conflict, duplicate protected identifier, processed request, or concurrency race.
- `429`: temporary login/OTP rate limit, with `Retry-After` where available.

## 7. Dependency order

1. Authentication token-generation invalidation and authorization test foundation.
2. Room mutation guards and lifecycle endpoints.
3. Tenant permission-specific update commands.
4. Vehicle registration/card lifecycle and private evidence boundary.
5. Vehicle Change Request workflow.
6. Cross-module PostgreSQL/security regression.

This order ensures later ownership and actor-sensitive commands rely on stable authentication behavior. It is a design dependency, not an implementation plan.

## 8. Cross-phase contracts

### 8.1 Phase 3

- Onboarding consumes the existing Bed availability and Room gender rules, then locks the Room first and selected Bed second before creating the Contract atomically.
- Checkout calls one Vehicle lifecycle operation inside its transaction: `ISSUED → REVOKED`, `PENDING_CARD → CANCELLED`, and pending change request → `CANCELLED`.
- Returning-Tenant reuse continues to resolve by CCCD and reuse the existing User/Tenant.

### 8.2 Phase 4

- A checked-out Tenant remains able to authenticate and access owned historical data.
- No Phase 2 command deletes Tenant/User or historical Vehicle records.

### 8.3 Phase 5

- Phase 5 adds a simple Spring Boot `NotificationService` backed by PostgreSQL. A workflow that requires a notification may call it inside its existing database transaction.
- The authorized evidence-access API remains, while its storage implementation changes to OCI presigned access.
- No mandatory `ApplicationEventPublisher`, after-commit event architecture, WebSocket, Firebase, Kafka, RabbitMQ, SMS, or Web Push is required.

## 9. Acceptance gates

Phase 2 is design-complete only when:

1. Every in-scope FR is covered by one domain specification.
2. Existing correct behavior is explicitly marked for preservation.
3. Breaking API changes are enumerated.
4. PostgreSQL integrity/transaction boundaries and required Redis atomic operations are testable without mandating stress testing.
5. Security tests exercise real filters and method authorization.
6. Later-phase responsibilities remain deferred.
7. The user explicitly approves these design documents before `superpowers:writing-plans` is invoked.

## 10. Audit-to-spec coverage

| Requirement | Audit classification | Owning specification and sections |
|---|---|---|
| FR-01 | KEEP / INTEGRATE_LATER | Room §4, §5.5, §9; concurrent occupation remains Phase 3 |
| FR-02 | KEEP | Room §5.1, §7, §9 |
| FR-03 | KEEP / REFACTOR | Room §5.2–§5.4, §9 |
| FR-04 | EXTEND / IMPLEMENT | Room §5.6–§5.8, §6 |
| FR-05 | KEEP / IMPLEMENT | Room §4, §6 |
| FR-06 | KEEP / EXTEND / IMPLEMENT | Tenant §4–§5, §7–§11 |
| FR-07 | KEEP / EXTEND / INTEGRATE_LATER | Tenant §6, §12; Vehicle §11 |
| FR-08 | REFACTOR / EXTEND / IMPLEMENT / INTEGRATE_LATER | Vehicle §4–§13 |
| FR-21 | KEEP / REFACTOR / EXTEND | Auth §4, §6–§9 |
| FR-22 | KEEP / REFACTOR | Auth §4.5–§4.6, §6, §9 |
| FR-23 | KEEP / REFACTOR / EXTEND | Auth §5.4, §6, §9 |
| FR-24 | EXTEND / REFACTOR | Auth §5, §6, §9; Tenant §7 |
| BR-01 / BR-02 | Partial | Room §4–§10 |
| BR-08 | Partial / high risk | Common §6.2; Room §5; Vehicle §7, §10–§12 |
| BR-09 | Partial / high risk | Auth §4–§9; Tenant §4, §7 |
| BR-10 | Major gap | Vehicle §4–§12 |
| BR-11 | Partial | Common §6.1; all domain security-test sections |

Following explicit design approval and invocation of `superpowers:writing-plans`, the complete `Requirement → audit → specification → plan task → verification` mapping is recorded in the [Phase 2 implementation handoff](../../audits/phase-2-implementation-handoff.md#6-audit-to-spec-to-plan-traceability).

## 11. Known non-blocking limitation

Real OCI storage is not implemented in the current code: `OciObjectStorageServiceImpl` only constructs a URL. Phase 2 can make local evidence private and stabilize authorized access, but OCI credentials, SDK operations and presigned URLs remain Phase 5 acceptance work. No design document claims otherwise.

The legacy onboarding path does not yet follow the shared Tenant/Room/Bed/Contract lock protocol. Its concurrent occupancy race remains a documented Phase 3 defect; Phase 2 hardens Room commands and defines the protocol but does not refactor onboarding outside the approved scope.
