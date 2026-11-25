# Phase 2 Implementation Handoff — KTX Hub Backend

**Date:** 2026-11-16
**Status:** READY_FOR_IMPLEMENTATION_REVIEW
**Approved approach:** A — Focused Brownfield Hardening
**Branch inspected:** `refactor/erd-and-implemented-code`
**HEAD inspected:** `ac525f28bebda83efa03007252bdfb1c7cf4a88f`

## 1. Purpose

This document hands the approved Phase 2 design and executable plans to a separate implementation agent. It does not authorize implementation until the user reviews these plans and chooses an execution method.

All commit snippets in the plans are suggested change-set boundaries only. The implementation agent MUST NOT stage, commit, or push unless the user or final execution prompt explicitly authorizes those Git operations.

Phase 2 preserves correct brownfield behavior and changes only Authentication/RBAC, Room/Bed, Tenant Profile, and Vehicle Management work required by FR-01–FR-08 and FR-21–FR-24. Phase 1 persistence remains the foundation.

## 2. Source documents

### Audit

- [Phase 2 current-state audit](phase-2-current-state-audit.md)

### Approved design specifications

- [Phase 2 Focused Brownfield Hardening overview](../superpowers/specs/2026-11-16-phase-2-focused-brownfield-hardening-design.md)
- [Authentication & RBAC design](../superpowers/specs/2026-11-16-authentication-rbac-design.md)
- [Room & Bed design](../superpowers/specs/2026-11-16-room-bed-management-design.md)
- [Tenant Profile design](../superpowers/specs/2026-11-16-tenant-profile-management-design.md)
- [Vehicle Management design](../superpowers/specs/2026-11-16-vehicle-management-design.md)

### Executable plans

- [Authentication & RBAC implementation plan](../superpowers/plans/2026-11-16-authentication-rbac-implementation.md)
- [Room & Bed implementation plan](../superpowers/plans/2026-11-16-room-bed-management-implementation.md)
- [Tenant Profile implementation plan](../superpowers/plans/2026-11-16-tenant-profile-management-implementation.md)
- [Vehicle Management implementation plan](../superpowers/plans/2026-11-16-vehicle-management-implementation.md)
- [Phase 2 regression & acceptance plan](../superpowers/plans/2026-11-16-phase-2-regression-acceptance-implementation.md)

### Superseded historical plans

Earlier files such as `docs/superpowers/plans/2026-11-03-room-bed-management.md` and `docs/superpowers/plans/2026-11-04-tenant-vehicle-contract-onboarding.md` predate the approved Phase 1 baseline and revised Phase 2 decisions. They are historical only and MUST NOT be executed for this handoff. The five dated 2026-11-16 plans above are the sole Phase 2 execution set.

## 3. Final architectural decisions

1. Keep the existing Spring Boot monolith, controller/service/repository conventions, response envelopes, Phase 1 entities, and clean Flyway baseline.
2. Single logout requires Authorization Access Token plus body Refresh Token; signature, expiry, `REFRESH` type, current User/session generation, active Refresh JTI and ownership are validated before either token is revoked.
3. Refresh rotation is simple: revoke the old active Refresh and issue a new pair. Invalid/expired/revoked reuse returns `401 INVALID_REFRESH_TOKEN` and never logs out other sessions.
4. User-wide random session generation invalidates all old tokens only for logout-all, lock, and password change/reset.
5. Room occupancy-sensitive commands use Room → Beds in stable UUID order. The manual Bed-status command is a Bed-only leaf operation and never acquires a Room write lock after locking the Bed. Phase 3 Onboarding must adopt Room → Bed; Phase 2 does not change Onboarding.
6. Vehicle registration uses service validation and existing PostgreSQL constraints, with no Redis/distributed lock, idempotency key, or retry framework.
7. Vehicle/Change Request cross-record lifecycle commands use bounded Vehicle → Request row locking and state rechecks. Vehicle statuses are exactly `PENDING_CARD`, `ISSUED`, `REJECTED`, `REVOKED`, `REPLACED`, `CANCELLED`; Request statuses are exactly `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`.
8. Vehicle approval is one transaction and preserves the physical Card Code while creating exactly one successor.
9. New Vehicle evidence is private and ownership-authorized. There is no legacy file migration; real OCI remains deferred.
10. Phase 2 adds no Notification implementation/events. Phase 5 uses a direct PostgreSQL-backed `NotificationService` inside the relevant business transaction.

## 4. Recommended execution order

| Order | Tasks | Dependency / acceptance gate |
|---|---|---|
| 1 | AUTH-1 → AUTH-4 | Establish login/token foundation; Auth focused tests green |
| 2 | AUTH-5 → AUTH-7 | Recovery/Technician/security chain; complete Auth slice green |
| 3 | ROOM-1 → ROOM-6 | Independent after Auth role foundation; Room/Bed slice green on PostgreSQL |
| 4 | TEN-1 → TEN-3 | Requires AUTH-5 recovery invalidation contract for email correction |
| 5 | TEN-4 → TEN-5 | Requires AUTH-2, AUTH-5, AUTH-6; Tenant slice green |
| 6 | VEH-1 → VEH-4 | Registration/private storage/card lifecycle; focused tests green |
| 7 | VEH-5 → VEH-7 | Change Request transaction and protected evidence; PostgreSQL/real-security slice green |
| 8 | REG-1 → REG-3 | Fresh migration, cross-module E2E, compile, slices, full suite, diff review |

Room work may proceed in parallel with AUTH-5/AUTH-6 if execution uses isolated commits and merges only after AUTH-7. Tenant assisted reset must not begin before the three named Auth contracts exist. Vehicle ownership/security acceptance should run after Auth/Tenant role behavior is stable.

## 5. Task dependency map

```text
AUTH-1
AUTH-2 ──> AUTH-3 ──> AUTH-4 ──> AUTH-7
  │                       │
  └──────> AUTH-5 ──> AUTH-6
              │          │
              └──> TEN-2 │
                     │    └──> TEN-4
TEN-1 ──> TEN-2 ──> TEN-3 ──> TEN-5

ROOM-1 ──> ROOM-2 ──> ROOM-3 ──> ROOM-4 ──> ROOM-5 ──> ROOM-6

VEH-1 ──┬──> VEH-3 ──> VEH-4 ──┐
VEH-2 ──┘                        ├──> VEH-5 ──> VEH-6 ──> VEH-7
                                ┘

AUTH-7 + ROOM-6 + TEN-5 + VEH-7 ──> REG-1 ──> REG-2 ──> REG-3
```

## 6. Audit-to-spec-to-plan traceability

| SRS / BR | Current code / audit finding | Approved specification | Plan tasks | Verification evidence |
|---|---|---|---|---|
| FR-01 | Matrix exists; available count and mutation locking incomplete | Room §4, §5.5, §9 | ROOM-1, ROOM-3, ROOM-6 | Derived-count service tests; Bed transition tests; security/PostgreSQL slice |
| FR-02 | Atomic Room/Bed/registry/Amenity creation largely correct | Room §5.1, §7, §9 | ROOM-1, ROOM-6 | Exact 4/6/8 Beds, invalid-Amenity rollback, Phase 1 trigger tests |
| FR-03 | Rename/gender update lacks complete occupancy/Contract guards | Room §5.2–§5.4 | ROOM-2 | Room→Bed lock tests, protected-code rollback, active-Contract rejection |
| FR-04 | Archive partly exists; archived list/restore/safe-delete behavior incomplete | Room §4.2, §5.6–§6 | ROOM-4, ROOM-5 | Minimal archive/list/restore contract tests; active/archived safe-delete history tests; no archived-detail route |
| FR-05 | Active search/list exists; archived view missing | Room §4, §6 | ROOM-1, ROOM-4 | Active/archived controller and derived summary tests |
| BR-01/BR-02 | Registry exists; mutation/history guards partial | Room §3.3, §5, §7 | ROOM-2, ROOM-4, ROOM-5 | Code protection, archive retention, unfiltered-delete tests |
| FR-06 | Manager query/detail exists; identity correction/self update missing | Tenant §3–§5, §8–§11 | TEN-1, TEN-2, TEN-3, TEN-5 | DTO semantics, uniqueness, owner-only and real-security tests |
| FR-07 | Historical Tenant retained; post-checkout permissions unproven | Tenant §6, §12; Vehicle §11 | TEN-3, TEN-5, VEH-7, REG-2 | Checked-out contact/history positive and current-residency negative tests |
| FR-08 registration | Current type/evidence matrix, public files, direct Admin issue are wrong | Vehicle §4–§6 | VEH-1, VEH-2, VEH-3 | Five-type validator, private storage, quota constraint tests |
| FR-08 card lifecycle | Issue partially correct; rejection/revoke/cancel/update states wrong | Vehicle §6 | VEH-4 | Full transition table, actor/reason/time, stale-state tests |
| FR-08 Change Request | Entity/repository only; workflow absent | Vehicle §7–§12 | VEH-5, VEH-6, VEH-7 | Owner/query tests; PostgreSQL atomic approval/rollback tests |
| BR-08 | Several transitions lack transaction/locking proof | Overview §6.2; Room §3.3; Vehicle §7/§10 | ROOM-2–ROOM-6, VEH-4–VEH-7, REG-2 | Scoped lock, rollback, stale-command, constraint tests |
| BR-10 | Vehicle quotas/history/change workflow largely absent | Vehicle §4–§14 | VEH-1–VEH-7 | Six/four-state acceptance and successor/Card Code history assertions |
| FR-21 login | CCCD/staff email correct; lock threshold wrong | Auth §4.1–§4.2 | AUTH-1, AUTH-7 | Identifier, sixth-failure, real-chain tests |
| FR-21 tokens | Refresh exists; reuse globally revokes sessions; Access invalidation incomplete | Auth §4.3–§4.5 | AUTH-2, AUTH-3 | Generation tests; normal rotation/reuse/unrelated-session tests |
| FR-22 | Single logout lacks ownership/full Refresh validation; global logout leaves Access tokens alive | Auth §4.5–§4.6 | AUTH-2–AUTH-4, AUTH-7 | Required body; signature/expiry/type/User/generation/active-JTI/ownership rejection tests; global invalidation tests |
| FR-23 | Technician CRUD exists; caller supplies password; lock revocation incomplete | Auth §5.4, §6 | AUTH-4, AUTH-6, AUTH-7 | Random password/hash-only, lock/reactivate, role tests |
| FR-24 | Change password exists; recovery is email/caller-keyed | Auth §5–§9; Tenant §7 | AUTH-4–AUTH-6, TEN-4 | User-bound challenge, OTP boundary, one-use reset, assisted reset tests |
| BR-09 | Token/recovery lifecycle partial | Auth §4–§9; Tenant §4/§7 | AUTH-1–AUTH-7, TEN-2, TEN-4 | Redis generation/recovery plus real Security-chain tests |
| BR-11 | Method guards exist; current controller tests bypass Security | Overview §6.1 and module test sections | AUTH-7, ROOM-6, TEN-5, VEH-7, REG-2 | Anonymous 401, wrong-role 403, owner/non-owner tests |
| Phase 3 contract | Onboarding/checkout currently outside refactor | Overview §8.1; Room §3.3; Vehicle §11 | Documentation only; REG-2 uses fixtures | No Onboarding/Checkout code in diff; Room→Bed and Vehicle→Request contracts retained |
| Phase 5 contract | OCI/Notifications deferred | Overview §8.3; Vehicle §5/§12.4 | VEH-2 documents stable private boundary only | No OCI SDK/event/Notification implementation; raw-key/public-path tests |

## 7. Critical review gates

### Gate A — Authentication foundation

- AUTH-1–AUTH-7 pass.
- Refresh reuse does not call global invalidation.
- Logout-all/lock/password mutations do call global invalidation.
- Real filter-chain tests distinguish 401 from 403.

### Gate B — Core domain integrity

- ROOM-6 and TEN-5 pass.
- No Onboarding, Checkout, Room Transfer, Contract lifecycle, or teammate-owned code changed.
- Phase 1 schema/Flyway remains untouched.

### Gate C — Vehicle integrity and privacy

- VEH-7 passes on PostgreSQL where constraints matter.
- Vehicle→Request order is consistent in create/revoke/approval-sensitive paths.
- Bed-only status mutation never requests a Room write lock; every command needing both follows Room→Bed.
- Approval rollback restores old Vehicle/Request and creates no successor.
- No public Vehicle image path, raw key, migration utility, event publisher, or Notification implementation exists.

### Gate D — Final acceptance

- REG-1–REG-3 pass.
- Actual compile/test counts and Docker/Redis/PostgreSQL availability are reported.
- Full diff contains only approved Phase 2 changes and planning documentation.

## 8. Deferred work and explicit exclusions

- Phase 3: Onboarding, Contract lifecycle, Renewals, Checkout, returning-Tenant activation, and adopting Room→Bed/Vehicle→Request integration contracts.
- Future product phase: FR-11 Room Transfer.
- Phase 4: Facility Expenses, Invoice, Payment, Finance.
- Phase 5: Dashboard, Tenant Portal completion, PostgreSQL `NotificationService`, actual OCI storage/presigned URLs.
- Teammate-owned: Maintenance and Violations implementation.
- No Kafka, RabbitMQ, Firebase, WebSocket, mandatory `ApplicationEventPublisher`, microservices, distributed locks, retry framework, or speculative performance work.

## 9. Definition of Done

Phase 2 implementation is ready for acceptance only when:

1. Every task checkbox in the five plans is completed or explicitly blocked with evidence.
2. All module-plan acceptance criteria pass.
3. Fresh PostgreSQL migration and Hibernate validation pass with the unchanged 20-table baseline.
4. Real Security-chain tests cover every new protected route.
5. Vehicle evidence is private and owner/Manager authorized.
6. Vehicle Change Request approval is transactionally atomic with one successor and preserved Card Code.
7. Simplified Refresh reuse and strict single logout match the approved design.
8. Full Maven tests pass with actual counts reported.
9. Authoritative SRS/ERD, Flyway, and out-of-scope modules remain unchanged.
10. No implementation commit/push occurs without the user's execution approval.

## 10. Plan-review checklist

- [x] Exact paths refer to existing files or are explicitly marked Create.
- [x] Proposed method names/types are consistent across plans.
- [x] Every in-scope FR/BR maps to tasks and tests.
- [x] Review Focus items appear in owning task tests.
- [x] Simplified Refresh, Vehicle concurrency, evidence, and Notification decisions are preserved.
- [x] Commit boundaries are small and dependency ordered.
- [x] No task implements Phase 3–5 or teammate-owned workflows.
- [x] Final regression commands and expected observable results are explicit.
- [x] VEH-3 uses `PENDING_CARD`; only the six Vehicle and four Change Request statuses are referenced.
- [x] ROOM-4 contains only archive, archived list and restore; no archived-detail API is planned.
- [x] AUTH-3 validates Refresh signature, expiry, type, User/generation, active JTI and ownership before logout revocation.
- [x] ROOM-3 is a Bed-only leaf lock and introduces no Bed→Room lock-order inversion.
- [x] Commit snippets are reference-only; staging, commit and push require explicit execution authorization.

The reviewed execution set contains 28 dependency-ordered tasks: 7 Authentication/RBAC, 6 Room/Bed, 5 Tenant Profile, 7 Vehicle, and 3 final regression/acceptance tasks.
