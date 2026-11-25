# Phase 2 Vehicle Management — Focused Hardening Design

**Date:** 2026-11-16
**Status:** Revised — ready for final explicit approval
**Requirements:** FR-08; BR-08, BR-10, BR-11; Logical ERD T10
**Parent design:** `2026-11-16-phase-2-focused-brownfield-hardening-design.md`

## 1. Existing baseline

### 1.1 Preserve

- Manager vehicle list/search/filter/detail routes (`AdminVehicleController.java:43-61`).
- Tenant current vehicle lookup derives ownership from authenticated User ID (`TenantVehicleController.java:35-43`; `VehicleServiceImpl.java:192-204`).
- Tenant registration and explicit Manager card-issuance route concepts.
- All six `VehicleStatus` values and all four `VehicleChangeRequestStatus` values.
- Phase 1 `vehicles` and `vehicle_change_requests` schema, historical links and conditional unique indexes (`V1__clean_baseline.sql:513-587`).
- One `PENDING_CARD`/`ISSUED` vehicle per Tenant and one pending change request per Tenant enforced in PostgreSQL.
- Card Code uniqueness only among currently `ISSUED` vehicles; reuse after revocation/replacement is intentional.

### 1.2 Correct

- Registration already requires `ResidentStatus.ACTIVE` (`VehicleServiceImpl.java:213-220`).
- Card issuance requires source state `PENDING_CARD` (`VehicleServiceImpl.java:131-156`).
- Manager list can filter by all six persisted states.

### 1.3 Incorrect or incomplete

- Registration requires plate and two images for every vehicle type, contrary to the SRS matrix.
- Service invents active license-plate uniqueness not present in SRS/Logical ERD (`VehicleServiceImpl.java:258-265`).
- Registration check-then-insert races are not translated from the DB constraint.
- Initial registration rejection is absent.
- Generic update rewrites records regardless of lifecycle state.
- Revoke accepts any source state; delete maps unrelated terminal states to `REVOKED`.
- Admin direct create may skip evidence and issue a card immediately.
- Vehicle Change Request has only entity/repository mappings; no workflow exists.
- File keys are returned as URLs and new local vehicle files are publicly reachable.
- No authorization, PostgreSQL integrity or transaction-rollback tests cover the lifecycle.

## 2. Design goals and exclusions

### 2.1 Goals

1. Implement the exact six-state Vehicle and four-state Request lifecycles.
2. Preserve every historical Vehicle/Request record.
3. Use service validation, database constraints and bounded transactions to protect registration, card issuance and replacement.
4. Validate plate/evidence by vehicle type at the service boundary.
5. Keep vehicle evidence private and ownership-authorized while deferring real OCI to Phase 5.
6. Preserve current routes when their operation remains valid; narrow unsafe generic routes rather than rewrite the whole module.

### 2.2 Exclusions

- No parking fee, barrier/card reader, scan history, lost-card workflow or appeal workflow.
- No checkout orchestration, but a transaction-compatible checkout command is defined for Phase 3.
- No Notification API, event publisher or listener in Phase 2; only future `NotificationService` call points are documented for Phase 5.
- No real OCI SDK/presigned URL implementation in Phase 2.
- No global or active license-plate uniqueness rule.

## 3. Focused component design

### 3.1 Existing components retained

- `AdminVehicleController`, `TenantVehicleController`.
- `VehicleService`/`VehicleServiceImpl` for list/detail/registration/card lifecycle.
- `VehicleRepository`, `VehicleChangeRequestRepository`, `TenantRepository`, `StaffProfileRepository`.
- `Vehicle`, `VehicleChangeRequest` entities and enums.

### 3.2 Focused collaborators

1. **`VehicleChangeRequestService` (new):** owns request create/query/approve/reject transactions.
2. **`VehicleRegistrationValidator` (new):** one conditional validation implementation reused by registration, Manager correction and proposed replacement.
3. **`VehicleEvidenceStorage` (new interface):** stores/loads/deletes opaque private vehicle evidence keys. Phase 2 supplies a private local implementation; Phase 5 supplies OCI.
4. **`VehicleEvidenceAccessService` (new):** authorizes Manager or owning Tenant before loading evidence.

Do not introduce a generic workflow engine, state-machine framework, distributed lock, idempotency framework or event broker.

## 4. Vehicle data rules

### 4.1 Type-specific fields

| Vehicle type | License plate | Front/back registration evidence |
|---|---|---|
| `SCOOTER` | Required | Both required |
| `MANUAL_MOTORCYCLE` | Required | Both required |
| `ELECTRIC_MOTORBIKE` | Required | Both required |
| `ELECTRIC_BICYCLE` | Optional/null | Optional/null |
| `BICYCLE` | Optional/null | Optional/null |

- Required text is trimmed and nonblank.
- Plate is canonicalized uppercase/trim when present.
- Brand/color are always required and trimmed.
- The application does not reject a plate merely because another Vehicle record uses it; the approved model has no plate uniqueness invariant.
- Card Code is canonicalized uppercase/trim and assigned only through issuance or approved replacement.

### 4.2 Evidence validation

- Vehicle registration evidence accepts actual JPEG, PNG or WebP image content.
- Filename extension and client `Content-Type` are advisory; the Backend verifies magic bytes/signature.
- Each file respects the configured 10 MB multipart per-file limit. This is an operational limit already configured in `application.yml:22-26`; no file size is persisted.
- Motorized types require two valid images before persistence. Bicycle types may omit both; if supplied, each is still validated.
- Upload newly validated files before the DB insert, then delete the new keys as compensation if validation/persistence fails. Historical evidence is not deleted on rejection/replacement.

## 5. Private evidence boundary

### 5.1 Phase 2 local behavior

- Vehicle uploads no longer use the public `/uploads/**` path or `StorageService` URL-return contract.
- `VehicleEvidenceStorage` returns an opaque key such as `vehicles/<uuid>/<side>-<uuid>` and stores local objects under a private root not registered with Spring's static resource handler.
- Vehicle/Request DTOs return booleans or protected evidence descriptors, never raw keys or permanent URLs.
- Authorized Backend endpoints stream local evidence after role/ownership checks.
- The existing public resource configuration may remain temporarily for out-of-scope contract documents, but `/uploads/vehicles/**` is explicitly denied and no Phase 2 Vehicle response points to it.

There are no existing Vehicle image files to migrate. Phase 2 adds private storage only for new evidence and adds no migration utility, compatibility adapter, legacy-object inventory or reconciliation workflow.

### 5.2 Phase 5 OCI behavior

- The same access service authorizes the request.
- OCI implementation returns/redirects to a presigned URL valid no longer than 15 minutes.
- DB keys remain stable; presigned URLs are never persisted.
- The current simulated `OciObjectStorageServiceImpl` is not treated as working OCI and must not be enabled for acceptance.

### 5.3 Evidence endpoints

- `GET /api/v1/admin/vehicles/{vehicleId}/evidence/{side}` — Manager.
- `GET /api/v1/tenant/vehicles/{vehicleId}/evidence/{side}` — owning Tenant, including historical records.
- `GET /api/v1/admin/vehicle-change-requests/{requestId}/evidence/{side}` — Manager.
- `GET /api/v1/tenant/vehicle-change-requests/{requestId}/evidence/{side}` — owning Tenant.

`side` is `front` or `back`. Missing optional evidence returns 404. Wrong ownership returns 404 to avoid disclosing another Tenant's resource.

## 6. Initial registration and card lifecycle

### 6.1 Tenant registration

`POST /api/v1/tenant/vehicles` remains multipart.

1. Resolve Tenant from principal; require `resident_status=ACTIVE`.
2. Validate type-specific fields/files.
3. Precheck no `PENDING_CARD`/`ISSUED` Vehicle for a useful domain error; retain the existing PostgreSQL partial unique index as the final integrity guard.
4. Store evidence privately.
5. Insert one `PENDING_CARD` Vehicle with no Card Code.
6. Translate a uniqueness violation to `409 VEHICLE_QUOTA_EXCEEDED`; compensate newly uploaded files.

No Redis lock, idempotency key, retry framework or special concurrent-registration protocol is added.

### 6.2 Manager registration on behalf of Tenant

Retain `POST /api/v1/admin/vehicles`, but align it with the same registration command:

- Change it to multipart with Tenant ID, vehicle data and optional/required evidence by type.
- Remove `cardCode`, `registrationFrontUrl` and `registrationBackUrl` from the request.
- It always creates `PENDING_CARD`; Manager then uses the explicit issuance command after verification/handover.
- It requires active Tenant residency and applies the same service validation plus PostgreSQL quota constraint.

This is an intentional breaking correction; retaining direct immediate issuance would bypass the SRS lifecycle.

### 6.3 Pending registration correction

Retain `PUT /api/v1/admin/vehicles/{id}` but narrow it:

- Only a `PENDING_CARD` record may be corrected.
- Lock the Vehicle before evaluating status or replacing evidence.
- Use multipart so replacement evidence can be provided when the resulting vehicle type requires it.
- An omitted evidence part retains the existing object. `clearFrontEvidence`/`clearBackEvidence` flags explicitly remove optional evidence; a clear flag and replacement upload for the same side are mutually exclusive. Clearing is rejected when the resulting motorized type requires that side.
- Validate the complete resulting state before replacing fields/keys.
- When changing from a motorized type to a bicycle type, retained evidence remains unless the Manager explicitly clears it. Delete replaced/cleared private keys only after the database transaction commits; on rollback keep old keys and compensate new keys.
- `ISSUED`, `REJECTED`, `REVOKED`, `REPLACED`, and `CANCELLED` records are immutable history through this route.

### 6.4 Issue card

- Retain `PATCH /api/v1/admin/vehicles/{id}/issue-card`.
- Resolve active Manager actor and lock the Vehicle.
- Require `PENDING_CARD`, valid evidence/type state and active Tenant.
- Canonicalize the Card Code, precheck availability and rely on the partial unique index for a race.
- Set `ISSUED` and `issued_at` in one transaction.
- Repeated issuance or stale state returns 409 without mutation.
- No notification event is published for ordinary card issuance because FR-27 does not require one.

### 6.5 Reject initial registration

Add `PATCH /api/v1/admin/vehicles/{id}/reject`.

- Request contains nonblank textual `reason`; no evidence file is accepted.
- Lock the Vehicle and require `PENDING_CARD`.
- Set `REJECTED`, `rejection_reason`, `rejected_by_staff_id`, and `rejected_at`; Card Code remains null.
- Preserve registration data/evidence as history.
- Record the rejection without publishing a Phase 2 event. Phase 5 may call `NotificationService` from this business transaction after the state validation succeeds.
- A new registration is a new Vehicle row; the rejected row is never reopened.

### 6.6 Revoke or cancel

- Retain `PATCH /api/v1/admin/vehicles/{id}/revoke-card`, but allow only `ISSUED → REVOKED` after physical card return. Lock the Vehicle first, recheck its state, then query for a `PENDING` Vehicle Change Request while holding that lock. If one exists, return 409 rather than silently cancelling it or leaving it unapprovable. Preserve Card Code and set `revoked_at`.
- Retain `DELETE /api/v1/admin/vehicles/{id}` only as cancellation of an unissued registration: lock the Vehicle, allow only `PENDING_CARD → CANCELLED`, set `cancelled_at`, keep history. Update its OpenAPI description from “delete” to “cancel pending registration.”
- Any terminal/wrong source state returns 409 and is unchanged.
- Rejection never uses revoke, and replacement never uses revoke/issue as two external REST calls.

## 7. Vehicle Change Request workflow

### 7.1 Tenant create

Add `POST /api/v1/tenant/vehicle-change-requests` as multipart.

1. Resolve the owning Tenant; require `ACTIVE` residency.
2. Lock the supplied current Vehicle, then verify that it belongs to the Tenant and is `ISSUED` with a nonblank Card Code. Holding this row lock through the pending-request insert prevents a concurrent revoke from passing its lifecycle check.
3. Validate proposed vehicle data/evidence by the same type matrix.
4. Require no existing `PENDING` request. The PostgreSQL partial unique index is the race guard.
5. Store proposed evidence and insert one `PENDING` Request only.
6. Do not mutate the current Vehicle, create a Vehicle row, reserve a second quota, revoke/issue a card or notify a Manager through new infrastructure.

A duplicate protected by the existing partial unique index maps to 409 and compensates newly stored proposed files. No separate idempotency or retry mechanism is added.

### 7.2 Tenant query

- `GET /api/v1/tenant/vehicle-change-requests` returns only the caller's requests, newest first.
- `GET /api/v1/tenant/vehicle-change-requests/{id}` returns current/proposed comparison and outcome/reason for the owner.
- Tenant may view historical `APPROVED`, `REJECTED`, and `CANCELLED` requests after checkout.
- No Tenant cancel endpoint is required by the SRS; checkout cancellation is Phase 3.

### 7.3 Manager query

- `GET /api/v1/admin/vehicle-change-requests` supports page, status and Tenant/plate/name search.
- `GET /api/v1/admin/vehicle-change-requests/{id}` returns current vehicle, proposed snapshot/evidence availability, status, processor/time/reason and successor vehicle when approved.

### 7.4 Approve

Add `PATCH /api/v1/admin/vehicle-change-requests/{id}/approve`.

1. Resolve active Manager StaffProfile.
2. Read the Request to resolve its current Vehicle ID, then lock the current Vehicle and the Request in that order within one database transaction. Recheck both after locking; no distributed or generalized concurrency framework is introduced.
3. Recheck Request `PENDING`, Tenant ownership/residency, current Vehicle `ISSUED`, nonblank Card Code and unchanged relationship.
4. Set old Vehicle to `REPLACED`, set `replaced_at`, and flush so the partial unique quota/Card Code indexes release the old `ISSUED` row.
5. Insert a new Vehicle with a new UUID, proposed data/evidence, `ISSUED`, the exact old Card Code, `issued_at`, and `replaced_vehicle_id=old`.
6. Link old `replaced_by_vehicle_id=new`.
7. Set Request `APPROVED`, `new_vehicle_id`, processor and time.
8. Commit all changes together; any error rolls everything back.
9. Do not publish a Phase 2 event. Phase 5 may call `NotificationService` within this same transaction after the status and successor have been established.

No physical card is returned/reissued and no new Card Code is generated.

### 7.5 Reject

Add `PATCH /api/v1/admin/vehicle-change-requests/{id}/reject`.

- Require a nonblank textual reason and no file.
- Lock the Request and require `PENDING`.
- Set `REJECTED`, reason, processor and time.
- Do not change the current Vehicle or Card Code and do not create a Vehicle.
- Preserve proposed data/evidence.
- Do not publish a Phase 2 event. Phase 5 may call `NotificationService` within the same business transaction.

### 7.6 Repeated and stale commands

- A repeated approve/reject on a processed Request returns 409 with its current status and creates no successor Vehicle.
- The Request lock plus status recheck ensures a stale second decision cannot perform another transition.
- This behavior is ordinary state validation, not an idempotency-key or retry framework.

## 8. Query and response contracts

### 8.1 Vehicle history

- Retain `GET /api/v1/tenant/vehicles/me` for the current `PENDING_CARD`/`ISSUED` Vehicle or null.
- Add `GET /api/v1/tenant/vehicles` for all owned Vehicle records, including terminal history.
- Manager list/detail remain and expose all six statuses.

### 8.2 `VehicleResponse`

Retain identity and descriptive fields, then add lifecycle facts needed by the SRS:

- rejection reason/time and rejecting staff summary where authorized;
- issued/revoked/cancelled/replaced times;
- predecessor/successor Vehicle IDs;
- `registrationFrontAvailable` / `registrationBackAvailable`;
- no `registrationFrontUrl`, `registrationBackUrl` or raw file key.

### 8.3 Change-request response

Contains Request ID/status/time, Tenant summary (Manager response), current Vehicle summary, proposed vehicle snapshot, evidence availability, reason, processor/time and new Vehicle ID when approved. Tenant responses never expose another Tenant's data.

## 9. API contract

| Method and route | Role | Behavior | Breaking change / status |
|---|---|---|---|
| `GET /api/v1/admin/vehicles` | Manager | List/search/filter all states | Retained |
| `GET /api/v1/admin/vehicles/{id}` | Manager | Detail/history | Retained, response corrected |
| `POST /api/v1/admin/vehicles` | Manager | Register for active Tenant as `PENDING_CARD` | **Breaking:** multipart; no direct Card Code/public URLs |
| `PUT /api/v1/admin/vehicles/{id}` | Manager | Correct `PENDING_CARD` only | **Breaking:** multipart and state restriction |
| `PATCH /api/v1/admin/vehicles/{id}/issue-card` | Manager | `PENDING_CARD → ISSUED` | Retained |
| `PATCH /api/v1/admin/vehicles/{id}/reject` | Manager | `PENDING_CARD → REJECTED` | New |
| `PATCH /api/v1/admin/vehicles/{id}/revoke-card` | Manager | `ISSUED → REVOKED` | Retained, narrowed |
| `DELETE /api/v1/admin/vehicles/{id}` | Manager | `PENDING_CARD → CANCELLED` only | Retained route, corrected semantics |
| `GET /api/v1/tenant/vehicles/me` | Tenant | Current active/pending | Retained |
| `GET /api/v1/tenant/vehicles` | Tenant | Own history | New |
| `POST /api/v1/tenant/vehicles` | Tenant | Type-valid private registration | Retained, conditional multipart fields |
| `GET/POST /api/v1/tenant/vehicle-change-requests` | Tenant | Own list/create | New |
| `GET /api/v1/tenant/vehicle-change-requests/{id}` | Tenant owner | Detail | New |
| `GET /api/v1/admin/vehicle-change-requests` | Manager | Paged queue/history | New |
| `GET /api/v1/admin/vehicle-change-requests/{id}` | Manager | Comparison/detail | New |
| `PATCH /api/v1/admin/vehicle-change-requests/{id}/approve` | Manager | Atomic replacement | New |
| `PATCH /api/v1/admin/vehicle-change-requests/{id}/reject` | Manager | Reject request only | New |
| Evidence GET routes in §5.3 | Manager/owner | Authorized private access | New |

Structural validation errors are 400; missing/hidden resources 404; quota/card/state/concurrency conflicts 409; wrong role 403.

## 10. Persistence

- No Flyway migration or entity-column change.
- Add only the pessimistic lock repository queries needed for lifecycle transitions, especially Request approval and the current Vehicle replacement.
- Use existing partial unique indexes as integrity mechanisms, not performance indexes.
- Use the composite current-Vehicle/Tenant FK and lifecycle checks already present.
- The service must order/flush old `REPLACED` before new `ISSUED` to satisfy immediate partial uniqueness within approval.
- Do not add plate uniqueness, total counters, public URLs or notification relation columns.

## 11. Checkout integration contract

Expose an internal transaction-compatible command for Phase 3, not a public Phase 2 API:

- For Tenant's `ISSUED` Vehicle after physical return: set `REVOKED`, preserve Card Code, set time.
- For `PENDING_CARD`: set `CANCELLED`, Card Code remains null.
- For pending Vehicle Change Request: set `CANCELLED`, `processed_at`; do not create a Vehicle or change terminal history.
- Keep `REPLACED`, `REJECTED`, `REVOKED`, `CANCELLED` history unchanged.

Phase 3 owns when this command is invoked and coordinates it with Contract/Tenant/Bed updates in one database transaction. Its future implementation must respect the Room→Bed order defined by the Room specification and use the bounded Vehicle→Request order for vehicle mutations. Unlike the standalone Manager revoke route, checkout deliberately cancels pending Requests before revoking/cancelling the current Vehicle.

## 12. Testing strategy

### 12.1 Unit/service

- Full five-type plate/evidence matrix, including optional bicycle evidence.
- No unsupported plate uniqueness rejection.
- Active-residency and ownership checks.
- Register, issue, reject, revoke and cancel valid/invalid transitions.
- Terminal records cannot be generically edited/deleted/reopened.
- Change request creates no second Vehicle and leaves current card active.
- Approval preserves exact Card Code and every historical link.
- Rejection leaves current Vehicle unchanged.
- Repeated terminal commands create no additional state changes or successor rows.
- Upload compensation on validation/DB failure; historical files retained.

### 12.2 PostgreSQL integrity and transaction tests

- Existing partial unique constraints reject a second active Vehicle, duplicate issued Card Code and second pending Change Request.
- Service validation translates expected uniqueness violations to stable 409 errors.
- Approval creates exactly one successor, preserves the Card Code and links both historical Vehicles.
- Approval failure at any intermediate write rolls back the old Vehicle, successor and Request changes together.
- A stale or repeated approval/rejection cannot create another successor or overwrite a terminal Request.

These are targeted constraint and transaction tests, not concurrency stress, retry or distributed-lock tests.

### 12.3 Security/file integration

- Anonymous/wrong role denied.
- Tenant cannot view another Tenant's Vehicle/Request/evidence.
- Checked-out Tenant can view history but cannot register/request change.
- Direct `/uploads/vehicles/**` is unavailable for Phase 2 vehicle evidence.
- Raw keys/public URLs never appear in API JSON.
- Invalid magic bytes, oversize files and missing motor evidence are rejected.
- New evidence is stored under the private root and is never written to or served from `/uploads/vehicles/**`.

### 12.4 Future notification boundary

- No event, listener, notification row or Notification API is expected in Phase 2 tests.
- Phase 5 tests will verify that required workflow notifications are created through a simple PostgreSQL-backed `NotificationService` call within the business transaction.

## 13. Cross-phase contracts

- Phase 3 checkout consumes §11 and does not reproduce vehicle transitions.
- Phase 5 adds a simple PostgreSQL-backed `NotificationService`; relevant business services call it within their existing transaction when a notification is required.
- Phase 5 swaps local `VehicleEvidenceStorage` for OCI private objects/presigned URLs ≤15 minutes.
- Finance, Maintenance and Violations have no Vehicle implementation dependency.

## 14. Acceptance criteria

1. All six Vehicle and four Request states are represented with only documented transitions.
2. Initial rejection and change-request rejection remain distinct workflows.
3. Existing PostgreSQL integrity constraints prevent two pending/issued Vehicles or two pending Requests for one Tenant.
4. Approval atomically makes old Vehicle `REPLACED`, new Vehicle `ISSUED`, preserves the Card Code and records processor/time/links.
5. Historical Vehicles/Requests/evidence are never overwritten or deleted by normal lifecycle commands.
6. New Vehicle evidence is private, ownership-authorized and never exposed as raw keys/public URLs; no legacy migration workflow is introduced.
7. No Phase 1 schema change, notification implementation, checkout orchestration or real OCI integration is introduced.
