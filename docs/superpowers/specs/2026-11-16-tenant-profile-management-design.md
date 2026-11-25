# Phase 2 Tenant Profile Management — Focused Hardening Design

**Date:** 2026-11-16
**Status:** Revised set — ready for final explicit approval
**Requirements:** FR-06–FR-07; BR-08, BR-09, BR-11
**Parent design:** `2026-11-16-phase-2-focused-brownfield-hardening-design.md`

## 1. Existing baseline

### 1.1 Preserve

- Manager-only paged Tenant list with search, Room and resident-status filters (`TenantController.java:35-49`; `TenantRepository.java:47-78`).
- Manager detail containing identity, current Room/Bed/Contract, vehicle history and contract history (`TenantServiceImpl.java:132-189`).
- Existing User–Tenant one-to-one relationship, immutable UUIDs, canonical globally unique User email and unique 12-digit CCCD.
- Phone numbers are not globally unique; current tests correctly permit repeated Tenant phone (`TenantServiceImplTest.java:189-203`).
- No standalone Tenant creation API; Phase 3 onboarding owns creation/reuse.
- `GET /api/v1/users/me` remains the common authenticated self-profile query.

### 1.2 Correct

- Contact updates do not rewrite Contract snapshots or history.
- Checked-out Tenant/User rows are retained.
- Auth does not block a checked-out Tenant solely due to `resident_status`, so historical login remains possible.

### 1.3 Incorrect or incomplete

- Manager update cannot correct CCCD, gender or User email (`UpdateTenantRequest.java:9-26`; `TenantServiceImpl.java:70-93`).
- There is no Tenant self-update operation.
- Existing update DTO does not encode different Manager/Tenant permissions.
- Manager-assisted Tenant password reset is absent.
- No security integration test proves ownership and post-checkout restrictions.
- Tenant detail still assembles later-phase contract DTO compatibility fields; Phase 2 must not rewrite those workflows.

## 2. Design goals and exclusions

### 2.1 Goals

1. Preserve Manager query/detail behavior.
2. Separate Manager identity correction from Tenant contact self-update through distinct DTOs and service methods.
3. Enforce canonical email/CCCD uniqueness and current-Room gender policy under transaction/locking.
4. Permit checked-out Tenant historical authentication and contact continuity while denying current-residency actions.
5. Provide the SRS Manager-assisted random-password reset without a new approval workflow.

### 2.2 Exclusions

- No independent Tenant creation, onboarding, returning-Tenant activation, checkout, debt processing or Room transfer.
- No Tenant self-service email, name, CCCD, gender, DOB, residency, Room, Bed, price, deposit or contract mutation.
- No email-change OTP, multi-level approval or change-history table.
- No full Tenant Portal implementation.

## 3. Focused service design

Retain `TenantService`/`TenantServiceImpl` and split its command methods by actor:

- `updateTenantAsManager(managerUserId, tenantId, ManagerUpdateTenantRequest)`.
- `updateOwnContact(tenantUserId, TenantContactUpdateRequest)`.
- `resetTenantPasswordAsManager(managerUserId, tenantId)`.

No generic `updateTenant` method accepts both actor types. Controller role guards remain, while service methods still resolve the actor/owner so direct service invocation cannot bypass permissions.

Repository additions are limited to write-lock reads and conflict queries. `UserTokenInvalidationService`, `PasswordRecoveryService` challenge invalidation, and `RandomPasswordGenerator` come from the Authentication design.

## 4. Manager Tenant correction

### 4.1 Request semantics

Retain `PUT /api/v1/admin/tenants/{id}`. Rename the DTO in code to `ManagerUpdateTenantRequest`. It has these exact semantics:

- `fullName` — required, nonblank
- `idCardNumber` (optional change; exactly 12 digits)
- `gender` (optional change)
- `email` (optional change; canonicalized lowercase/trim)
- `phone` — required, nonblank and format-valid
- `permanentAddress` — required and nonblank because the database column is not null
- `dateOfBirth` — nullable; null clears the optional value
- `emergencyContactName` — nullable; null/blank clears the optional value
- `emergencyContactPhone` — nullable; null/blank clears the optional value, otherwise format-valid

Omitted/null optional identity fields (`idCardNumber`, `gender`, `email`) retain their current values; a supplied blank identity string is invalid. Because this remains a PUT contract, omitted/null `dateOfBirth` and emergency-contact fields clear those optional contact values. IDs, tenant code, resident status, Room, Bed, Contract and User role/status are never accepted in this DTO.

### 4.2 Transaction and validation

1. Resolve the authenticated actor as an active `MANAGER` StaffProfile.
2. Resolve the target User ID, then pessimistically lock the User first and Tenant second. Password recovery uses the same User lock when creating a challenge, preventing an old-email challenge from racing an email correction.
3. Validate/normalize all supplied fields before mutation.
4. If CCCD changes, precheck uniqueness and rely on PostgreSQL unique constraint for races. Update the existing Tenant only; never create a new User/Tenant.
5. If email changes, precheck global User uniqueness and rely on the canonical DB unique index. Update `users.email`; do not duplicate email in Tenant.
6. If gender changes and an active Contract exists, require the current Room gender policy to equal the new Tenant gender. Do not mutate the Room or move the Tenant.
7. Phone remains non-unique.
8. Save in one transaction. A DB race maps to the corresponding 409 error.
9. On email change, advance the User's Redis `recoveryVersion`, invalidating all outstanding OTP challenges **and already-issued reset tokens**. A challenge/reset token stores its recovery version and is accepted only when it equals the current value. Existing Access Tokens need not be invalidated because authorization uses User UUID/current DB state and the SRS does not require logout for a contact email correction.

The update does not modify signed Contract/Renewal/Invoice snapshots.

## 5. Tenant self-service contact update

Add `PATCH /api/v1/tenant/profile` guarded by `hasRole('TENANT')`.

`TenantContactUpdateRequest` contains only:

- `phone`
- `permanentAddress`
- `emergencyContactName`
- `emergencyContactPhone`

At least one field must be present. The service resolves Tenant by `UserPrincipal.getId()`; no Tenant ID is accepted from the client. `null` means “not supplied”; a blank emergency-contact name/phone clears that optional value after validation, while blank phone/address is rejected. This convention avoids introducing JSON Merge Patch infrastructure.

Self-update is allowed for both `ACTIVE` and `CHECKED_OUT` Tenants because it preserves long-lived contact information and does not perform a current-residency action. It cannot change email or identity fields.

The response is the existing full `UserProfileResponse`, matching `GET /api/v1/users/me`. Its current `TenantInfo` already contains every updated contact field, so no duplicate profile response type is introduced.

## 6. Historical access and residency guard

- A checked-out Tenant retains `users.status=ACTIVE` unless separately locked and continues to authenticate by CCCD.
- `GET /api/v1/users/me` continues to return owned profile data.
- Manager queries include `ACTIVE` and `CHECKED_OUT` based on filters.
- Vehicle registration and change-request creation require `resident_status=ACTIVE` in their own service commands.
- Phase 3 checkout updates resident status; Phase 4/5 own historical invoice/document portal endpoints.
- No `FORMER_TENANT` role is introduced.

## 7. Manager-assisted password reset

Add `POST /api/v1/admin/tenants/{id}/reset-password`, Manager only.

1. Resolve active Manager actor and target Tenant/User.
2. Generate a policy-compliant random password using the shared generator.
3. Store only its BCrypt hash.
4. Under the common User-row lock, advance the target User's recovery version so every earlier OTP/reset token is invalid, then globally invalidate Access/Refresh tokens by rotating the session generation.
5. Return the plaintext exactly once in `OneTimePasswordResponse` for offline handover.
6. Do not email it automatically, log it, persist it elsewhere, or add a reset-history table.

The operation represents the Manager's documented offline identity verification; no separate approval workflow is added.

## 8. API contract

| Method and route | Role | Request | Response | Errors / compatibility |
|---|---|---|---|---|
| `GET /api/v1/admin/tenants` | Manager | Existing page/search/room/status | Existing summary page | Retained; 200 empty page allowed |
| `GET /api/v1/admin/tenants/{id}` | Manager | Path ID | Existing detail | Retained; 404 |
| `PUT /api/v1/admin/tenants/{id}` | Manager | Expanded `ManagerUpdateTenantRequest` | Existing detail | Route retained; additive optional identity fields; 400/404/409 |
| `GET /api/v1/users/me` | Authenticated | None | Existing role profile | Retained |
| `PATCH /api/v1/tenant/profile` | Tenant | `TenantContactUpdateRequest` | Owned Tenant profile | New; 400/404 |
| `POST /api/v1/admin/tenants/{id}/reset-password` | Manager | None | `OneTimePasswordResponse` | New; 404; token invalidation required |

Manager Tenant update and Tenant self-update must not share the same request class.

## 9. Persistence

- No Flyway or entity-column change.
- Add pessimistic lock repository queries for Tenant/User mutation as needed.
- PostgreSQL canonical email and CCCD uniqueness remain final guards.
- The existing `tenants.user_id` unique FK and role trigger remain authoritative.
- Phone is never made unique in application validation or database migration.

## 10. Error behavior

- Invalid CCCD format: 400.
- Duplicate CCCD/email, including a concurrent race: `TENANT_409_ID_CARD` or `TENANT_409_EMAIL` with HTTP 409. Auth/Technician endpoints continue using their Auth-domain email code.
- Gender inconsistent with active Room: 409, not an automatic Room/Tenant correction.
- Tenant self-update attempting forbidden fields is impossible through the DTO; malformed/unknown fields follow the configured request-binding policy and cannot mutate them.
- Wrong role: 403; anonymous: 401; target not found: 404.

## 11. Testing strategy

### 11.1 Unit/service

- Manager updates every permitted field and retains omitted optional identity fields.
- CCCD canonical validation and duplicate conflict.
- Email lowercasing/global duplicate conflict.
- Phone duplicates are accepted.
- Gender change succeeds without active Contract or when matching current Room; mismatched current Room is rejected.
- Contact/identity edits do not mutate Contract history/snapshots.
- Tenant self-update mutates only own contact fields and permits checked-out Tenant.
- Assisted password reset returns one-time plaintext, stores a nonmatching hash and invalidates tokens.
- Email correction invalidates outstanding recovery challenges.
- Email correction and assisted reset invalidate both outstanding challenges and already-issued reset tokens, including concurrent recovery attempts serialized by the User lock/recovery version.

### 11.2 PostgreSQL integration

- Concurrent same-email and same-CCCD corrections yield one success and one 409.
- User–Tenant relationship/role constraints remain valid.
- No update creates a second User/Tenant or changes UUIDs.

### 11.3 Security/controller

- Manager routes: Manager 2xx, Tenant/Technician 403, anonymous 401.
- Self route: Tenant 2xx, Manager/Technician 403.
- A Tenant cannot specify or update another Tenant's ID.
- Forbidden identity/email/residency fields are not bound by self-update.
- Checked-out Tenant can read/update contact but cannot register/change a vehicle.

## 12. Cross-phase contracts

- Phase 3 onboarding is the sole creator/reactivator and reuses Tenant/User by CCCD.
- Phase 3 checkout sets `CHECKED_OUT`; this module only interprets that status for permissions.
- Phase 4/5 historical invoice/document access uses User→Tenant ownership, not current residency.
- Vehicle module consumes resident status but does not alter it.
- No teammate-owned Maintenance/Violation behavior is introduced.

## 13. Acceptance criteria

1. Manager can correct all SRS-permitted identity/contact fields without changing IDs/history.
2. Tenant can update only their own phone/address/emergency contacts.
3. Global email and CCCD uniqueness are enforced safely under concurrency; phone remains non-unique.
4. Gender correction cannot violate the current Room policy.
5. Checked-out Tenant retains login/contact/history readiness but cannot perform current-residency Vehicle commands.
6. Manager-assisted reset generates a one-time random password and invalidates old tokens.
7. No Tenant creation/onboarding/checkout/Room transfer or new persistence entity is implemented.
