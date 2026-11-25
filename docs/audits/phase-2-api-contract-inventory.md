# KTX Hub Backend — Phase 2 API Contract Inventory & Verification Report

**Authoritative Baseline:** SRS v3.4.1 FINAL | Logical ERD v3.6 | Conceptual ERD v3.5  
**Repository Branch:** `refactor/erd-and-implemented-code`  
**Execution Date:** November 19, 2026  
**Implementation Mode:** Approach A — Focused Brownfield Hardening & Prototype Compatibility  
**Verification Status:** 338/338 Maven Tests Passed (0 Failures, 0 Errors, 0 Skipped)  

---

## 1. Executive Summary

Phase 2 Backend Implementation encompasses four foundational business modules of the KTX Hub dormitory management platform:
1. **Authentication & RBAC (AUTH-1 through AUTH-7):** FR-01, FR-02, FR-03, FR-04, FR-05, FR-21, FR-22, FR-23, FR-24.
2. **Room & Bed Management (ROOM-1 through ROOM-6):** FR-09, FR-10.
3. **Tenant Profile Management (TEN-1 through TEN-5):** FR-06, FR-07.
4. **Vehicle Management (VEH-1 through VEH-7):** FR-08, BR-10, BR-11.
5. **Regression & Acceptance (REG-1 through REG-3):** Cross-module scenarios, persistence regression, full suite execution.

### Key Architectural Invariants Enforced
- **Zero Schema Mutations:** The Phase 1 clean Flyway baseline (`V1__clean_baseline.sql`) and all 20 PostgreSQL tables remain strictly unchanged.
- **Strict Role Boundary:** Only three application roles are recognized: `MANAGER`, `TECHNICIAN`, and `TENANT`. No transitional or legacy `ADMIN` role is accepted.
- **Immediate Cross-Device Revocation:** Multi-session token invalidation via Redis token generation invalidates all tokens of locked accounts or reset passwords immediately across all modules without waiting for JWT expiry.
- **Anti-Deadlock Lock Ordering:**
  - Room hierarchy: `rooms` locked first, then `beds` ordered by `id` ASC.
  - Vehicle change workflow: `vehicles` locked first, then `vehicle_change_requests` locked second.
- **Private Vehicle Evidence:** All vehicle evidence is stored in isolated private storage (`/private-storage/vehicles/...`) and served exclusively via role/owner-authorized streaming endpoints. Public `/uploads/vehicles/**` web paths are explicitly denied.
- **Strict Business Registration Workflow:** In alignment with SRS FR-08 and approved user decisions, editing of pending vehicle registrations (`PUT /api/v1/admin/vehicles/{id}`) is prohibited and removed. Rejections require a reason and subsequent re-registration.

---

## 2. API Contract Inventory

### 2.1. Domain 1: Authentication & User Management (FR-01 – FR-05, FR-21 – FR-24)

| HTTP Method | Route | Authorization | Request Body / Params | Success Status & Response | Error Codes |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Public | `LoginRequest` (`identifier`: Email / Phone / Student ID, `password`) | `200 OK` → `LoginResponse` (`accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `user`) | `AUTH_401_BAD_CREDENTIALS`<br>`AUTH_403_USER_LOCKED`<br>`AUTH_429_TOO_MANY_REQUESTS` |
| `POST` | `/api/v1/auth/refresh-token` | Public | `RefreshTokenRequest` (`refreshToken`) | `200 OK` → `TokenRefreshResponse` (`accessToken`, `refreshToken`, `tokenType`, `expiresIn`) | `AUTH_401_INVALID_REFRESH_TOKEN`<br>`AUTH_403_USER_LOCKED` |
| `POST` | `/api/v1/auth/logout` | Authenticated (Any) | `LogoutRequest` (`refreshToken`) | `200 OK` → `SuccessResponse` | `AUTH_401_UNAUTHORIZED` |
| `POST` | `/api/v1/auth/logout-all` | Authenticated (Any) | None | `200 OK` → `SuccessResponse` | `AUTH_401_UNAUTHORIZED` |
| `POST` | `/api/v1/auth/change-password` | Authenticated (Any) | `ChangePasswordRequest` (`currentPassword`, `newPassword`, `confirmPassword`) | `200 OK` → `SuccessResponse` | `AUTH_400_CURRENT_PASSWORD_INCORRECT`<br>`AUTH_400_CONFIRM_PASSWORD_MISMATCH` |
| `POST` | `/api/v1/auth/forgot-password` | Public | `PasswordRecoveryRequest` (`email` / `identifier`) | `200 OK` → `PasswordRecoveryRequestedResponse` (`identifier`, `maskedDestination`) | `AUTH_404_USER_NOT_FOUND` |
| `POST` | `/api/v1/auth/verify-forgot-password-otp` | Public | `VerifyRecoveryOtpRequest` (`identifier`, `otp`) | `200 OK` → `OneTimePasswordResponse` (`recoveryToken`) | `AUTH_400_INVALID_OTP`<br>`AUTH_400_EXPIRED_OTP` |
| `POST` | `/api/v1/auth/reset-password` | Public | `ResetPasswordRequest` (`recoveryToken`, `newPassword`, `confirmPassword`) | `200 OK` → `SuccessResponse` | `AUTH_400_INVALID_RECOVERY_TOKEN`<br>`AUTH_400_CONFIRM_PASSWORD_MISMATCH` |
| `GET` | `/api/v1/users/me` | Authenticated (Any) | None | `200 OK` → `UserProfileResponse` | `AUTH_401_UNAUTHORIZED` |
| `POST` | `/api/v1/users/technicians` | `MANAGER` | `CreateTechnicianRequest` (`email`, `fullName`, `phone`) | `201 Created` → `CreateTechnicianResponse` (`id`, `email`, `fullName`, `phone`, `temporaryPassword`) | `USER_409_EMAIL_EXISTS`<br>`STAFF_409_PHONE_EXISTS` |
| `GET` | `/api/v1/users/technicians` | `MANAGER` | `Pageable`, `search`, `status` | `200 OK` → `PageResponse<TechnicianResponse>` | `AUTH_403_FORBIDDEN` |
| `GET` | `/api/v1/users/technicians/{id}` | `MANAGER` | `id` (UUID) | `200 OK` → `TechnicianResponse` | `USER_404_NOT_FOUND` |
| `PUT` | `/api/v1/users/technicians/{id}` | `MANAGER` | `UpdateTechnicianRequest` (`email`, `fullName`, `phone`) | `200 OK` → `TechnicianResponse` | `USER_404_NOT_FOUND`<br>`USER_409_EMAIL_EXISTS`<br>`STAFF_409_PHONE_EXISTS` |
| `PATCH` | `/api/v1/users/technicians/{id}/status` | `MANAGER` | `UpdateUserStatusRequest` (`status`: `ACTIVE` / `LOCKED`) | `200 OK` → `TechnicianResponse` | `USER_404_NOT_FOUND` |
| `POST` | `/api/v1/users/technicians/{id}/reset-password` | `MANAGER` | None | `200 OK` → `OneTimePasswordResponse` (`temporaryPassword`) | `USER_404_NOT_FOUND` |

---

### 2.2. Domain 2: Room & Bed Management (FR-09, FR-10)

| HTTP Method | Route | Authorization | Request Body / Params | Success Status & Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/rooms` | `MANAGER` | `Pageable`, `building`, `floor`, `genderPolicy`, `status`, `search` | `200 OK` → `PageResponse<RoomSummaryResponse>` | `AUTH_403_FORBIDDEN` |
| `GET` | `/api/v1/admin/rooms/archived` | `MANAGER` | `Pageable`, `search` | `200 OK` → `PageResponse<ArchivedRoomSummaryResponse>` | `AUTH_403_FORBIDDEN` |
| `GET` | `/api/v1/admin/rooms/matrix` | `MANAGER` | None | `200 OK` → `List<RoomMatrixFloorGroupResponse>` | `AUTH_403_FORBIDDEN` |
| `GET` | `/api/v1/admin/rooms/{id}` | `MANAGER` | `id` (UUID) | `200 OK` → `RoomDetailResponse` (with `OccupantSummaryResponse` containing `idCardNumber`) | `ROOM_404_NOT_FOUND` |
| `POST` | `/api/v1/admin/rooms` | `MANAGER` | `CreateRoomRequest` (`roomNumber`, `genderPolicy`, `basePrice`, `floor`, `amenityIds`) | `201 Created` → `RoomDetailResponse` | `ROOM_409_NUMBER_EXISTS`<br>`AMENITY_404_NOT_FOUND` |
| `PUT` | `/api/v1/admin/rooms/{id}` | `MANAGER` | `UpdateRoomRequest` (`roomNumber`, `genderPolicy`, `basePrice`, `amenityIds`) | `200 OK` → `RoomDetailResponse` | `ROOM_404_NOT_FOUND`<br>`ROOM_409_NUMBER_EXISTS`<br>`ROOM_400_OCCUPIED_GENDER_MISMATCH`<br>`ROOM_400_ACTIVE_CONTRACT_RENAME_FORBIDDEN` |
| `DELETE` | `/api/v1/admin/rooms/{id}` | `MANAGER` | `id` (UUID) | `200 OK` → `SuccessResponse` (Soft-archive) | `ROOM_404_NOT_FOUND`<br>`ROOM_400_OCCUPIED_ARCHIVE_FORBIDDEN` |
| `POST` | `/api/v1/admin/rooms/{id}/restore` | `MANAGER` | `id` (UUID) | `200 OK` → `RoomDetailResponse` | `ROOM_404_NOT_FOUND`<br>`ROOM_409_RESTORE_CONFLICT` |
| `DELETE` | `/api/v1/admin/rooms/{id}/permanent` | `MANAGER` | `id` (UUID) | `200 OK` → `SuccessResponse` (Hard-delete) | `ROOM_404_NOT_FOUND`<br>`ROOM_400_PERMANENT_DELETE_USED_IN_BUSINESS` |
| `PATCH` | `/api/v1/admin/beds/{id}/status` | `MANAGER` | `UpdateBedStatusRequest` (`status`: `AVAILABLE` / `MAINTENANCE`) | `200 OK` → `BedDetailResponse` | `BED_404_NOT_FOUND`<br>`BED_400_INVALID_STATE_TRANSITION` |
| `GET` | `/api/v1/admin/amenities` | `MANAGER` | None | `200 OK` → `List<AmenityResponse>` (`id`, `code`, `name`) | `AUTH_403_FORBIDDEN` |

---

### 2.3. Domain 3: Tenant Profile Management (FR-06, FR-07)

| HTTP Method | Route | Authorization | Request Body / Params | Success Status & Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/tenants` | `MANAGER` | `Pageable`, `search`, `status` | `200 OK` → `PageResponse<TenantSummaryResponse>` (with `monthlyRent`) | `AUTH_403_FORBIDDEN` |
| `GET` | `/api/v1/admin/tenants/{id}` | `MANAGER` | `id` (UUID) | `200 OK` → `TenantDetailResponse` | `TENANT_404_NOT_FOUND` |
| `PUT` | `/api/v1/admin/tenants/{id}` | `MANAGER` | `ManagerUpdateTenantRequest` (`fullName`, `gender`, `dateOfBirth`, `idCardNumber`, `phone`, `permanentAddress`, `emergencyContactName`, `emergencyContactPhone`) | `200 OK` → `TenantDetailResponse` | `TENANT_404_NOT_FOUND`<br>`TENANT_409_ID_CARD_EXISTS`<br>`TENANT_409_PHONE_EXISTS`<br>`TENANT_400_ACTIVE_CONTRACT_IDENTITY_LOCKED` |
| `POST` | `/api/v1/admin/tenants/{id}/reset-password` | `MANAGER` | None | `200 OK` → `OneTimePasswordResponse` (`temporaryPassword`) | `TENANT_404_NOT_FOUND` |
| `PATCH` | `/api/v1/tenant/profile` | `TENANT` (Self) | `TenantContactUpdateRequest` (`phone`, `permanentAddress`, `emergencyContactName`, `emergencyContactPhone`) | `200 OK` → `UserProfileResponse` | `TENANT_404_NOT_FOUND`<br>`TENANT_409_PHONE_EXISTS` |

---

### 2.4. Domain 4: Vehicle & Evidence Management (FR-08, BR-10, BR-11)

| HTTP Method | Route | Authorization | Request Body / Form Parts | Success Status & Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/tenant/vehicles/me` | `TENANT` (Self) | None | `200 OK` → `VehicleResponse` | `TENANT_404_NOT_FOUND` |
| `GET` | `/api/v1/tenant/vehicles` | `TENANT` (Self) | None (Includes historical vehicles) | `200 OK` → `List<VehicleResponse>` | `TENANT_404_NOT_FOUND` |
| `POST` | `/api/v1/tenant/vehicles` | `TENANT` (Self) | Multipart: `TenantRegisterVehicleRequest` (`vehicleType`, `licensePlate`, `brand`, `color`) + `frontImage`, `backImage` | `201 Created` → `VehicleResponse` (`status: PENDING_CARD`) | `TENANT_400_NOT_ACTIVE`<br>`VEHICLE_400_INVALID_TYPE_DATA`<br>`VEHICLE_400_INVALID_EVIDENCE`<br>`VEHICLE_409_QUOTA_EXCEEDED`<br>`VEHICLE_409_LICENSE_PLATE_DUPLICATE` |
| `GET` | `/api/v1/tenant/vehicles/{vehicleId}/evidence/{side}` | `TENANT` (Owner) | Path: `vehicleId`, `side` (`front`/`back`) | `200 OK` → Binary Stream (`image/jpeg`, `image/png`, `image/webp`) | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_404_EVIDENCE_NOT_FOUND` |
| `POST` | `/api/v1/tenant/vehicle-change-requests` | `TENANT` (Self) | Multipart: `CreateVehicleChangeRequest` (`currentVehicleId`, `proposedVehicleType`, `proposedLicensePlate`, `proposedBrand`, `proposedColor`) + `frontImage`, `backImage` | `201 Created` → `VehicleChangeRequestResponse` (`status: PENDING`) | `TENANT_400_NOT_ACTIVE`<br>`VEHICLE_404_NOT_FOUND`<br>`VEHICLE_409_CHANGE_REQUEST_PENDING`<br>`VEHICLE_400_INVALID_TYPE_DATA` |
| `GET` | `/api/v1/tenant/vehicle-change-requests` | `TENANT` (Self) | None | `200 OK` → `List<VehicleChangeRequestResponse>` | `TENANT_404_NOT_FOUND` |
| `GET` | `/api/v1/tenant/vehicle-change-requests/{id}` | `TENANT` (Owner) | Path: `id` | `200 OK` → `VehicleChangeRequestResponse` | `VEHICLE_404_NOT_FOUND` |
| `GET` | `/api/v1/tenant/vehicle-change-requests/{requestId}/evidence/{side}` | `TENANT` (Owner) | Path: `requestId`, `side` (`front`/`back`) | `200 OK` → Binary Stream | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_404_EVIDENCE_NOT_FOUND` |
| `GET` | `/api/v1/admin/vehicles` | `MANAGER` | `Pageable`, `search`, `status` | `200 OK` → `PageResponse<VehicleResponse>` | `AUTH_403_FORBIDDEN` |
| `GET` | `/api/v1/admin/vehicles/{id}` | `MANAGER` | Path: `id` | `200 OK` → `VehicleResponse` | `VEHICLE_404_NOT_FOUND` |
| `POST` | `/api/v1/admin/vehicles` | `MANAGER` | Multipart: `CreateVehicleAdminRequest` (`tenantId`, `vehicleType`, `licensePlate`, `brand`, `color`) + `frontImage`, `backImage` | `201 Created` → `VehicleResponse` (`status: PENDING_CARD`) | `TENANT_404_NOT_FOUND`<br>`VEHICLE_409_QUOTA_EXCEEDED`<br>`VEHICLE_400_INVALID_TYPE_DATA` |
| `PATCH` | `/api/v1/admin/vehicles/{id}/issue-card` | `MANAGER` | `IssueCardRequest` (`cardCode`) | `200 OK` → `VehicleResponse` (`status: ISSUED`, `cardCode`, `issuedAt`) | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_409_NOT_PENDING`<br>`VEHICLE_409_CARD_CODE_DUPLICATE` |
| `POST` | `/api/v1/admin/vehicles/{id}/reject` | `MANAGER` | `RejectVehicleRequest` (`reason`) | `200 OK` → `VehicleResponse` (`status: REJECTED`, `rejectionReason`) | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_400_INVALID_STATE_TRANSITION` |
| `PATCH` | `/api/v1/admin/vehicles/{id}/revoke-card` | `MANAGER` | None | `200 OK` → `VehicleResponse` (`status: REVOKED`, `revokedAt`) | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_400_INVALID_STATE_TRANSITION`<br>`VEHICLE_409_PENDING_CHANGE_REQUEST` |
| `DELETE` | `/api/v1/admin/vehicles/{id}` | `MANAGER` | None | `200 OK` → `SuccessResponse` (`status: CANCELLED`) | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_400_INVALID_STATE_TRANSITION` |
| `GET` | `/api/v1/admin/vehicles/{vehicleId}/evidence/{side}` | `MANAGER` | Path: `vehicleId`, `side` | `200 OK` → Binary Stream | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_404_EVIDENCE_NOT_FOUND` |
| `GET` | `/api/v1/admin/vehicle-change-requests` | `MANAGER` | `Pageable`, `search`, `status` | `200 OK` → `PageResponse<VehicleChangeRequestResponse>` | `AUTH_403_FORBIDDEN` |
| `GET` | `/api/v1/admin/vehicle-change-requests/{id}` | `MANAGER` | Path: `id` | `200 OK` → `VehicleChangeRequestResponse` | `VEHICLE_404_NOT_FOUND` |
| `POST` | `/api/v1/admin/vehicle-change-requests/{id}/approve` | `MANAGER` | None | `200 OK` → `VehicleChangeRequestResponse` (`status: APPROVED`, `newVehicleId`) | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_409_NOT_PENDING`<br>`VEHICLE_400_INVALID_STATE_TRANSITION` |
| `POST` | `/api/v1/admin/vehicle-change-requests/{id}/reject` | `MANAGER` | `RejectVehicleChangeRequest` (`reason`) | `200 OK` → `VehicleChangeRequestResponse` (`status: REJECTED`, `rejectionReason`) | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_409_NOT_PENDING` |
| `GET` | `/api/v1/admin/vehicle-change-requests/{requestId}/evidence/{side}` | `MANAGER` | Path: `requestId`, `side` | `200 OK` → Binary Stream | `VEHICLE_404_NOT_FOUND`<br>`VEHICLE_404_EVIDENCE_NOT_FOUND` |

---

## 3. Verification & Test Suite Summary

### Test Execution Metrics
- **Total Test Suite:** 338 tests.
- **Failures:** 0.
- **Errors:** 0.
- **Skipped:** 0.
- **Build Status:** `BUILD SUCCESS`.

### Key Verification Suites Executed
1. **PostgreSQL Migration & Integrity:**
   - `Phase1FullPostgresMigrationTest` (12 tests) — Validates clean Flyway V1 creation of 20 tables and trigger definitions on PostgreSQL 16 Testcontainers.
   - `Phase1APostgresMigrationTest` (14 tests) — Verifies specific PostgreSQL table constraints and triggers.
   - `VehicleLifecyclePostgresTest` (4 tests) — Verifies partial unique indexes (`uq_vehicles_tenant_active_quota`, `uq_vehicles_issued_card_code`, `uq_vehicle_change_requests_pending_tenant`) and atomic replacement with lock order.
2. **Spring Security & RBAC Integration:**
   - `AuthenticationSecurityIntegrationTest` (13 tests) — Verifies Redis-backed session invalidation, rate-limiting locks, role checks.
   - `RoomBedSecurityIntegrationTest` (11 tests) — Verifies anti-deadlock lock order, Bed status checks, and archive/delete guards.
   - `TenantProfileSecurityIntegrationTest` (10 tests) — Verifies actor-specific profile editing and permission limits.
   - `VehicleSecurityIntegrationTest` (7 tests) — Verifies anonymous 401, RBAC 403, cross-tenant 404, evidence streaming 200, `/uploads/vehicles/**` denial, and checked-out split.
3. **End-to-End Acceptance:**
   - `Phase2CoreModulesE2ETest` (4 comprehensive multi-step scenarios) — Verifies end-to-end integration across Auth, Rooms, Tenants, and Vehicles with real security and Redis session invalidation.
   - `ResidentLifecycleE2ETest` (1 comprehensive E2E lifecycle test) — Verifies complete student lifecycle including onboarding, room assignment, vehicle registration, RFID card issue, renewal, checkout, and card reuse.

---

## 4. Git Worktree Integrity Notice
Per task instructions, all changes reside locally in the working directory on branch `refactor/erd-and-implemented-code` at commit `ac525f2`. No git commit, push, or branch switch has been executed automatically. All modifications are ready for final code review and authorized commit.
