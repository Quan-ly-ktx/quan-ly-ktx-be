# KTX Hub Backend — Phase 2 API Contract Inventory & Verification Report

- **Authoritative Baseline:** SRS v3.4.1 FINAL | Logical ERD v3.6 FINAL | Conceptual ERD v3.5 FINAL
- **Repository Branch:** `feat/orca-phase3-final` (hardened from baseline `18e1d6e`)
- **Execution Date:** November 26, 2026
- **Stack:** Java 25 | Spring Boot `4.1.2` | PostgreSQL 16/17 + Flyway | Redis 8 | OpenAPI `/v3/api-docs` & Scalar `/scalar` (`/docs`)
- **Implementation Mode:** Phase 2 Hardening (`F-03`, `F-05`, `F-08`, `F-09`, `F-02` regression verified; `F-01`, `F-04`, `F-06`, `F-07` explicitly `DEFERRED / ACCEPTED RISK`)
- **Verification Status:** **388 / 388** Maven Tests Passed (`0` Failures, `0` Errors, `0` Skipped)

---

## 1. Executive Summary

Phase 2 Backend covers four foundational modules of the KTX Hub dormitory management platform (with FR-11 room transfer deferred, Phase 3 Onboarding/Contracts out of Phase 2 scope, and SRS sections 4.6–4.8 excluded):

1. **Authentication & RBAC:** FR-21, FR-22, FR-23, FR-24 (`AuthController`, `UserController`).
2. **Room, Bed & Amenity Management:** FR-01, FR-02, FR-03, FR-04, FR-05 (`RoomController`, `BedController`, `AmenityController`).
3. **Tenant Profile Management:** FR-06, FR-07 (`TenantController`, `TenantProfileController`).
4. **Vehicle & Evidence Management:** FR-08, BR-10, BR-11 (`TenantVehicleController`, `AdminVehicleController`, `TenantVehicleChangeRequestController`, `AdminVehicleChangeRequestController`).

### Standard Response Envelopes (`ResponseFactory` & `dtos/response/base/`)
- `SingleResponse<T>`: `{ httpStatus, message, data }`
- `SuccessResponse`: `{ httpStatus, message }`
- `SingleResponse<PageResponse<T>>`: `data` contains `{ items, page, size, totalElements, totalPages, last }`
- `ErrorResponse`: `{ httpStatus, message, errorCode, timestamp, path }`
- `DetailedErrorResponse`: `{ httpStatus, message, errorCode, timestamp, path, items }` (`items`: `Map<String, List<String>>`, returned on `WEB_400_VAL`)
- OpenAPI 3.1 JSON spec: `GET /v3/api-docs`; interactive Scalar UI: `GET /scalar` (alias `/docs`).

### Key Architectural Invariants Enforced
- **Zero Schema Mutations:** `V1__clean_baseline.sql` (20 PostgreSQL tables) and all JPA entity mappings remain untouched.
- **Strict Role Boundary:** Only `MANAGER`, `TECHNICIAN`, and `TENANT` exist in `UserRole` and `V1__clean_baseline.sql`.
- **Login Identifier Rule:** `MANAGER` and `TECHNICIAN` authenticate using **canonical email**; `TENANT` authenticates using **12-digit CCCD (`id_card_number`) ONLY** (Tenant email is for contact and password recovery OTP only; phone and student ID are not login identifiers).
- **Sensitive Log Masking (F-05):** `LogMaskingUtils` masks emails, 12-digit CCCDs, phones, and login identifiers across `LoginAttemptServiceImpl`, `CustomUserDetailsService`, `EmailServiceImpl`, `OtpServiceImpl`, `OnboardingServiceImpl`, `AuthServiceImpl`, `UserServiceImpl`, and `AppExceptionHandler`.
- **Immediate Cross-Device Revocation:** Redis `sessionGeneration` rotation + refresh JTI registry + access-token blacklist invalidate all sessions immediately on lock, logout-all, or password reset/change.
- **Anti-Deadlock Lock Ordering:**
  - Room hierarchy: `rooms` (`PESSIMISTIC_WRITE`) locked before `beds` (`id` ASC).
  - Vehicle change workflow: `vehicles` (`PESSIMISTIC_WRITE`) locked before `vehicle_change_requests`.
- **Private Vehicle Evidence (F-02 verified):** Evidence files are stored in private local storage (`private-storage/vehicles`), `/uploads/vehicles/**` is denied by `SecurityConfig`, `PUT /api/v1/admin/vehicles/{id}` is absent in both controller and OpenAPI `/v3/api-docs`, and evidence writes/rollbacks clean up orphaned files.
- **Non-unique Phone & License Plate:** Neither `phone` (Staff/Tenant) nor `license_plate` enforces global uniqueness in `V1__clean_baseline.sql` or service logic.

---

## 2. API Contract Inventory

### 2.1. Domain 1: Authentication & User Management (FR-21 – FR-24)

| HTTP Method | Route | Authorization | Request DTO / Body / Params | Success Status & Envelope Payload | Error Codes (`*ErrorCode`) |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Public | `LoginRequest` (`identifier` [Staff canonical email or Tenant 12-digit CCCD], `password`) | `200 OK` → `SingleResponse<AuthResponse>` (`accessToken`, `refreshToken`, `tokenType="Bearer"`, `expiresIn`, `user: UserResponse(id, email, role, status)`) | `AUTH_401_CREDENTIALS` (401)<br>`AUTH_403_RATE_LOCKED` (403)<br>`AUTH_403_LOCKED` (403)<br>`WEB_400_VAL` (400) |
| `POST` | `/api/v1/auth/refresh-token` | Public | `RefreshTokenRequest` (`refreshToken`) | `200 OK` → `SingleResponse<AuthResponse>` | `AUTH_401_REFRESH` (401)<br>`AUTH_403_LOCKED` (403)<br>`WEB_400_VAL` (400) |
| `POST` | `/api/v1/auth/logout` | Authenticated (Any) | Header `Authorization: Bearer <token>` + `RefreshTokenRequest` (`refreshToken`) | `200 OK` → `SuccessResponse` | `AUTH_401_CREDENTIALS` (401)<br>`AUTH_401_REFRESH` (401)<br>`AUTH_403_LOCKED` (403)<br>`WEB_400_VAL` (400) |
| `POST` | `/api/v1/auth/logout-all` | Authenticated (Any) | Header `Authorization: Bearer <token>` (checked in service) | `200 OK` → `SuccessResponse` | `AUTH_401_CREDENTIALS` (401)<br>`WEB_401` (401) |
| `POST` | `/api/v1/auth/change-password` | Authenticated (Any) | `ChangePasswordRequest` (`oldPassword`, `newPassword` [8–64 chars], `confirmPassword`; no OTP) | `200 OK` → `SuccessResponse` | `AUTH_404_USER` (404)<br>`AUTH_400_OLD_PASSWORD` (400)<br>`AUTH_400_PASSWORD_CONFIRM` (400)<br>`AUTH_400_PASSWORD_SAME` (400)<br>`WEB_400_VAL` (400) |
| `POST` | `/api/v1/auth/forgot-password` | Public | `PasswordRecoveryRequest` (`identifier`: Staff email or Tenant 12-digit CCCD) | `200 OK` → `SingleResponse<PasswordRecoveryRequestedResponse>` (`recoveryRequestId`, `expiresInSeconds`) *(generic 200 even if unknown)* | `AUTH_429_OTP_COOLDOWN` (429)<br>`AUTH_429_OTP_ATTEMPTS` (429)<br>`WEB_400_VAL` (400) |
| `POST` | `/api/v1/auth/verify-forgot-password-otp` | Public | `VerifyRecoveryOtpRequest` (`recoveryRequestId` [UUID], `otp` [6 digits]) | `200 OK` → `SingleResponse<VerifyResetPasswordOtpResponse>` (`resetToken`) | `AUTH_400_OTP` (400)<br>`AUTH_429_OTP_ATTEMPTS` (429)<br>`WEB_400_VAL` (400) |
| `POST` | `/api/v1/auth/reset-password` | Public | `ResetPasswordRequest` (`resetToken`, `newPassword` [8–64 chars], `confirmPassword`) | `200 OK` → `SuccessResponse` | `AUTH_400_RESET_TOKEN` (400)<br>`AUTH_400_PASSWORD_CONFIRM` (400)<br>`AUTH_400_PASSWORD_SAME` (400)<br>`WEB_400_VAL` (400) |
| `GET` | `/api/v1/users/me` | Authenticated (Any) | None | `200 OK` → `SingleResponse<UserProfileResponse>` (`id`, `email`, `role`, `status`, `staffProfile`, `tenantProfile`) | `AUTH_404_USER` (404)<br>`WEB_401` (401) |
| `POST` | `/api/v1/users/technicians` | `MANAGER` | `CreateTechnicianRequest` (`fullName`, `email`, `phone` [`^0[0-9]{9}$`]) | `201 Created` → `SingleResponse<CreateTechnicianResponse>` (`technician: TechnicianResponse`, `initialPassword`) | `AUTH_409_EMAIL` (409)<br>`WEB_400_VAL` (400)<br>`WEB_403` (403) |
| `GET` | `/api/v1/users/technicians` | `MANAGER` | Query: `active` (`Boolean`), `search`, `Pageable` | `200 OK` → `SingleResponse<PageResponse<TechnicianResponse>>` (`id`, `userId`, `email`, `phone`, `fullName`, `userStatus`, `createdAt`) | `WEB_403` (403) |
| `GET` | `/api/v1/users/technicians/{id}` | `MANAGER` | Path: `id` (Staff ID or User ID UUID) | `200 OK` → `SingleResponse<TechnicianResponse>` | `TECH_404` (404)<br>`WEB_403` (403) |
| `PATCH` | `/api/v1/users/technicians/{id}/status` | `MANAGER` | `UpdateTechnicianStatusRequest` (`active` [`@NotNull Boolean`]) | `200 OK` → `SingleResponse<TechnicianResponse>` | `TECH_404` (404)<br>`WEB_400_VAL` (400)<br>`WEB_403` (403) |
| `PUT` | `/api/v1/users/technicians/{id}` | `MANAGER` | `UpdateTechnicianRequest` (`fullName`, `email`, `phone` [`^0[0-9]{9}$`]) | `200 OK` → `SingleResponse<TechnicianResponse>` | `TECH_404` (404)<br>`AUTH_409_EMAIL` (409)<br>`WEB_400_VAL` (400)<br>`WEB_403` (403) |
| `POST` | `/api/v1/users/technicians/{id}/reset-password` | `MANAGER` | None | `200 OK` → `SingleResponse<OneTimePasswordResponse>` (`temporaryPassword`) | `TECH_404` (404)<br>`WEB_403` (403) |

---

### 2.2. Domain 2: Room, Bed & Amenity Management (FR-01 – FR-05)

| HTTP Method | Route | Authorization | Request DTO / Body / Params | Success Status & Envelope Payload | Error Codes (`RoomErrorCode`) |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/rooms/matrix` | `MANAGER` | Query: `floor` (`Integer`), `genderPolicy` (`GenderPolicy`), `search` | `200 OK` → `SingleResponse<List<RoomDetailResponse>>` (rooms with `roomType`, `amenities`, `beds` & `OccupantSummaryResponse`) | `WEB_403` (403) |
| `GET` | `/api/v1/admin/rooms` | `MANAGER` | Query: `floor`, `genderPolicy`, `search`, `Pageable` | `200 OK` → `SingleResponse<PageResponse<RoomSummaryResponse>>` | `WEB_403` (403) |
| `GET` | `/api/v1/admin/rooms/archived` | `MANAGER` | Query: `floor`, `genderPolicy`, `search`, `Pageable` | `200 OK` → `SingleResponse<PageResponse<ArchivedRoomSummaryResponse>>` (`..., archivedAt`) | `WEB_403` (403) |
| `GET` | `/api/v1/admin/rooms/{id}` | `MANAGER` | Path: `id` (UUID) | `200 OK` → `SingleResponse<RoomDetailResponse>` (includes `OccupantSummaryResponse` with `idCardNumber`) | `ROOM_404` (404) |
| `POST` | `/api/v1/admin/rooms` | `MANAGER` | `CreateRoomRequest` (`roomNumber` [`^P\.[0-9]{3,4}$`], `floor` [>=1], `roomType` [`FOUR_BEDS`/`SIX_BEDS`/`EIGHT_BEDS`], `genderPolicy` [`MALE`/`FEMALE`], `basePrice` [>0], `amenities: List<RoomAmenityRequest(amenityId, quantity)>`) | `201 Created` → `SingleResponse<RoomDetailResponse>` (auto-provisions 4/6/8 beds `G01..G0n`) | `ROOM_409` (409)<br>`ROOM_409_CODE_PROTECTED` (409)<br>`ROOM_404_AMENITY` (404)<br>`WEB_400_VAL` (400) |
| `PUT` | `/api/v1/admin/rooms/{id}` | `MANAGER` | `UpdateRoomRequest` (`roomNumber` [nullable pattern `^P\.[0-9]{3,4}$`], `genderPolicy` [nullable], `basePrice` [>0], `amenities: List<RoomAmenityRequest>`) | `200 OK` → `SingleResponse<RoomDetailResponse>` | `ROOM_404` (404)<br>`ROOM_409` (409)<br>`ROOM_409_CODE_PROTECTED` (409)<br>`ROOM_409_OCCUPIED` (409)<br>`ROOM_404_AMENITY` (404)<br>`WEB_400_VAL` (400) |
| `DELETE` | `/api/v1/admin/rooms/{id}` | `MANAGER` | Path: `id` (UUID) | `200 OK` → `SuccessResponse` (soft-archive via `archivedAt`) | `ROOM_404` (404)<br>`ROOM_409_ARCHIVED` (409)<br>`ROOM_409_OCCUPIED` (409) |
| `POST` | `/api/v1/admin/rooms/{id}/restore` | `MANAGER` | Path: `id` (UUID) | `200 OK` → `SingleResponse<RoomDetailResponse>` | `ROOM_404` (404)<br>`ROOM_409_NOT_ARCHIVED` (409)<br>`ROOM_409` (409) |
| `DELETE` | `/api/v1/admin/rooms/{id}/permanent` **&** `/api/v1/admin/rooms/{id}/incorrect` | `MANAGER` | Path: `id` (UUID) | `200 OK` → `SuccessResponse` (physical delete of unused room) | `ROOM_404` (404)<br>`ROOM_409_OCCUPIED` (409)<br>`ROOM_409_HISTORY` (409) |
| `PATCH` | `/api/v1/admin/beds/{id}/status` | `MANAGER` | `UpdateBedStatusRequest` (`status`: `AVAILABLE` / `MAINTENANCE`) | `200 OK` → `SingleResponse<BedResponse>` (`id`, `roomId`, `roomNumber`, `bedCode`, `slotKey`, `price`, `status`) | `BED_404` (404)<br>`BED_400_MAINTENANCE` (400)<br>`BED_409_STATUS` (409)<br>`WEB_400_VAL` (400) |
| `GET` | `/api/v1/admin/amenities` | `MANAGER` | None | `200 OK` → `SingleResponse<List<AmenityResponse>>` (`id`, `code`, `name`) | `WEB_403` (403) |

---

### 2.3. Domain 3: Tenant Profile Management (FR-06, FR-07)

| HTTP Method | Route | Authorization | Request DTO / Body / Params | Success Status & Envelope Payload | Error Codes (`TenantErrorCode`) |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/tenants` | `MANAGER` | Query: `Pageable`, `search`, `roomId` (`UUID`), `status` (`ResidentStatus`) | `200 OK` → `SingleResponse<PageResponse<TenantSummaryResponse>>` (includes `monthlyRent`) | `WEB_403` (403) |
| `GET` | `/api/v1/admin/tenants/{id}` | `MANAGER` | Path: `id` (UUID) | `200 OK` → `SingleResponse<TenantDetailResponse>` | `TENANT_404` (404) |
| `PUT` | `/api/v1/admin/tenants/{id}` | `MANAGER` | `ManagerUpdateTenantRequest` (`fullName`, optional `idCardNumber` [12 digits], optional `gender`, optional `email`, `phone`, `permanentAddress`, nullable `dateOfBirth`, nullable `emergencyContactName`, nullable/blank-clearing `emergencyContactPhone`). **Omitted/`null` `idCardNumber` or `email` retains current value; supplied blank `""`/whitespace rejected with `400`.** | `200 OK` → `SingleResponse<TenantDetailResponse>` | `TENANT_404` (404)<br>`TENANT_409_ID_CARD` (409)<br>`TENANT_409_EMAIL` (409)<br>`TENANT_409_GENDER` (409)<br>`TENANT_400_ID_CARD_FORMAT` (400)<br>`WEB_400_VAL` (400) |
| `POST` | `/api/v1/admin/tenants/{id}/reset-password` | `MANAGER` | Path: `id` (UUID) | `200 OK` → `SingleResponse<OneTimePasswordResponse>` (`temporaryPassword`) | `TENANT_404` (404) |
| `PATCH` | `/api/v1/tenant/profile` | `TENANT` (Self) | `TenantContactUpdateRequest` (optional `phone`, optional `permanentAddress`, optional `emergencyContactName`, optional `emergencyContactPhone`). **Blank `phone` or `permanentAddress` rejected with `400`; blank emergency fields clear values.** | `200 OK` → `SingleResponse<UserProfileResponse>` | `TENANT_404` (404)<br>`TENANT_400_NO_FIELDS` (400)<br>`WEB_400_VAL` (400) |

---

### 2.4. Domain 4: Vehicle & Change Request Management (FR-08, BR-10, BR-11)

Note: **`PUT /api/v1/admin/vehicles/{id}` does not exist** (removed in F-02). `VehicleResponse` returns boolean flags `registrationFrontAvailable` and `registrationBackAvailable` (never raw internal storage paths). `/uploads/vehicles/**` returns access denied (`denyAll()`).

| HTTP Method | Route | Authorization | Request DTO / Form Parts | Success Status & Envelope Payload | Error Codes (`VehicleErrorCode` / `TenantErrorCode`) |
|---|---|---|---|---|---|
| `GET` | `/api/v1/tenant/vehicles/me` | `TENANT` (Self) | None | `200 OK` → `SingleResponse<VehicleResponse>` | `TENANT_404` (404) |
| `GET` | `/api/v1/tenant/vehicles` | `TENANT` (Self) | None | `200 OK` → `SingleResponse<List<VehicleResponse>>` | `TENANT_404` (404) |
| `POST` | `/api/v1/tenant/vehicles` | `TENANT` (Self) | Multipart `form-data`: `@ModelAttribute TenantRegisterVehicleRequest` (`vehicleType`, `licensePlate`, `brand`, `color`) + optional `frontImage`, `backImage` | `201 Created` → `SingleResponse<VehicleResponse>` (`status: PENDING_CARD`) | `TENANT_404` (404)<br>`TENANT_400_NOT_ACTIVE` (400)<br>`VEHICLE_409_QUOTA` (409)<br>`VEHICLE_400_INVALID_TYPE_DATA` (400)<br>`VEHICLE_400_INVALID_EVIDENCE` (400)<br>`WEB_400_VAL` (400) |
| `GET` | `/api/v1/tenant/vehicles/{vehicleId}/evidence/{side}` | `TENANT` (Owner) | Path: `vehicleId`, `side` (`front`/`back`) | `200 OK` → Binary `Resource` stream (`image/jpeg`, `image/png`, `image/webp`) | `VEHICLE_404` (404)<br>`VEHICLE_EVIDENCE_404` (404) |
| `POST` | `/api/v1/tenant/vehicle-change-requests` | `TENANT` (Self) | Multipart `form-data`: `@ModelAttribute CreateVehicleChangeRequest` (`currentVehicleId`, `proposedVehicleType`, `proposedLicensePlate`, `proposedBrand`, `proposedColor`) + optional `frontImage`, `backImage` | `201 Created` → `SingleResponse<VehicleChangeRequestResponse>` (`status: PENDING`) | `TENANT_404` (404)<br>`TENANT_400_NOT_ACTIVE` (400)<br>`VEHICLE_404` (404)<br>`VEHICLE_409_INVALID_STATE` (409)<br>`VEHICLE_CHANGE_REQUEST_409_EXISTS` (409)<br>`VEHICLE_400_INVALID_TYPE_DATA` (400)<br>`VEHICLE_400_INVALID_EVIDENCE` (400) |
| `GET` | `/api/v1/tenant/vehicle-change-requests` | `TENANT` (Self) | None | `200 OK` → `SingleResponse<List<VehicleChangeRequestResponse>>` | `TENANT_404` (404) |
| `GET` | `/api/v1/tenant/vehicle-change-requests/{id}` | `TENANT` (Owner) | Path: `id` (UUID) | `200 OK` → `SingleResponse<VehicleChangeRequestResponse>` | `TENANT_404` (404)<br>`VEHICLE_CHANGE_REQUEST_404` (404) |
| `GET` | `/api/v1/tenant/vehicle-change-requests/{requestId}/evidence/{side}` | `TENANT` (Owner) | Path: `requestId`, `side` (`front`/`back`) | `200 OK` → Binary `Resource` stream | `VEHICLE_CHANGE_REQUEST_404` (404)<br>`VEHICLE_EVIDENCE_404` (404) |
| `GET` | `/api/v1/admin/vehicles` | `MANAGER` | Query: `Pageable`, `search`, `status` (`VehicleStatus`) | `200 OK` → `SingleResponse<PageResponse<VehicleResponse>>` | `WEB_403` (403) |
| `GET` | `/api/v1/admin/vehicles/{id}` | `MANAGER` | Path: `id` (UUID) | `200 OK` → `SingleResponse<VehicleResponse>` | `VEHICLE_404` (404) |
| `POST` | `/api/v1/admin/vehicles` | `MANAGER` | Multipart `form-data`: `@ModelAttribute CreateVehicleAdminRequest` (`tenantId`, `vehicleType`, `licensePlate`, `brand`, `color`) + optional `frontImage`, `backImage` | `201 Created` → `SingleResponse<VehicleResponse>` (`status: PENDING_CARD`) | `TENANT_404` (404)<br>`TENANT_400_NOT_ACTIVE` (400)<br>`VEHICLE_409_QUOTA` (409)<br>`VEHICLE_400_INVALID_TYPE_DATA` (400)<br>`VEHICLE_400_INVALID_EVIDENCE` (400) |
| `PATCH` | `/api/v1/admin/vehicles/{id}/issue-card` | `MANAGER` | `IssueCardRequest` (`cardCode`) | `200 OK` → `SingleResponse<VehicleResponse>` (`status: ISSUED`) | `VEHICLE_404` (404)<br>`VEHICLE_409_ACTIVE` (409)<br>`VEHICLE_409_NOT_PENDING` (409)<br>`TENANT_400_NOT_ACTIVE` (400)<br>`VEHICLE_409_CARD_CODE` (409)<br>`WEB_400_VAL` (400) |
| `PATCH` | `/api/v1/admin/vehicles/{id}/reject` | `MANAGER` | `RejectVehicleRequest` (`reason`) | `200 OK` → `SingleResponse<VehicleResponse>` (`status: REJECTED`) | `VEHICLE_404` (404)<br>`VEHICLE_409_INVALID_STATE` (409)<br>`WEB_400_VAL` (400) |
| `PATCH` | `/api/v1/admin/vehicles/{id}/revoke-card` | `MANAGER` | None | `200 OK` → `SingleResponse<VehicleResponse>` (`status: REVOKED`) | `VEHICLE_404` (404)<br>`VEHICLE_409_INVALID_STATE` (409)<br>`VEHICLE_409_PENDING_CHANGE_REQUEST` (409) |
| `DELETE` | `/api/v1/admin/vehicles/{id}` | `MANAGER` | Path: `id` (UUID) | `200 OK` → `SuccessResponse` (`status: CANCELLED`) | `VEHICLE_404` (404)<br>`VEHICLE_409_INVALID_STATE` (409) |
| `GET` | `/api/v1/admin/vehicles/{vehicleId}/evidence/{side}` | `MANAGER` | Path: `vehicleId`, `side` (`front`/`back`) | `200 OK` → Binary `Resource` stream | `VEHICLE_404` (404)<br>`VEHICLE_EVIDENCE_404` (404) |
| `GET` | `/api/v1/admin/vehicle-change-requests` | `MANAGER` | Query: `Pageable`, `search`, `status` (`VehicleChangeRequestStatus`) | `200 OK` → `SingleResponse<PageResponse<VehicleChangeRequestResponse>>` | `WEB_403` (403) |
| `GET` | `/api/v1/admin/vehicle-change-requests/{id}` | `MANAGER` | Path: `id` (UUID) | `200 OK` → `SingleResponse<VehicleChangeRequestResponse>` | `VEHICLE_CHANGE_REQUEST_404` (404) |
| `PATCH` | `/api/v1/admin/vehicle-change-requests/{id}/approve` | `MANAGER` | None | `200 OK` → `SingleResponse<VehicleChangeRequestResponse>` (`status: APPROVED`; old vehicle → `REPLACED`, new vehicle → `ISSUED` with same `cardCode`) | `VEHICLE_CHANGE_REQUEST_404` (404)<br>`VEHICLE_404` (404)<br>`VEHICLE_CHANGE_REQUEST_409_NOT_PENDING` (409)<br>`VEHICLE_409_INVALID_STATE` (409)<br>`TENANT_400_NOT_ACTIVE` (400) |
| `PATCH` | `/api/v1/admin/vehicle-change-requests/{id}/reject` | `MANAGER` | `RejectVehicleChangeRequest` (`reason`) | `200 OK` → `SingleResponse<VehicleChangeRequestResponse>` (`status: REJECTED`) | `VEHICLE_CHANGE_REQUEST_404` (404)<br>`VEHICLE_CHANGE_REQUEST_409_NOT_PENDING` (409)<br>`WEB_400_VAL` (400) |
| `GET` | `/api/v1/admin/vehicle-change-requests/{requestId}/evidence/{side}` | `MANAGER` | Path: `requestId`, `side` (`front`/`back`) | `200 OK` → Binary `Resource` stream | `VEHICLE_CHANGE_REQUEST_404` (404)<br>`VEHICLE_EVIDENCE_404` (404) |

---

### 2.5. Data Integrity Classification (`DataIntegrityViolationException` — F-09) & Hardening Disposition

`DataIntegrityViolationClassifier` + `AppExceptionHandler` classify database constraint races cleanly between HTTP `409` and HTTP `400`:

| Constraint / SQLSTATE | HTTP Status | Error Code | Notes |
|---|---|---|---|
| `tenants_id_card_number_key` | `409` | `TENANT_409_ID_CARD` | Tenant CCCD uniqueness conflict |
| `uq_users_email_canonical` | `409` | `TENANT_409_EMAIL` / `AUTH_409_EMAIL` | `TenantServiceImpl` maps Tenant context to `TENANT_409_EMAIL`; `UserServiceImpl` & global fallback map to `AUTH_409_EMAIL` |
| `uq_rooms_number_canonical`, `room_code_registry_pkey`, `uq_beds_slot_key_canonical` | `409` | `ROOM_409` / `ROOM_409_CODE_PROTECTED` | Room/slot conflict (or `ROOM_409_CODE_PROTECTED` when protected registry entry conflicts in `RoomServiceImpl`) |
| `uq_vehicles_issued_card_code` | `409` | `VEHICLE_409_CARD_CODE` | Active card code conflict |
| `uq_vehicles_tenant_active_quota` | `409` | `VEHICLE_409_QUOTA` | Concurrent registration exceeding 1 active/pending vehicle |
| `uq_vehicle_change_requests_pending_tenant` | `409` | `VEHICLE_CHANGE_REQUEST_409_EXISTS` | Concurrent pending change request |
| `23503` during `RoomServiceImpl.deleteIncorrectRoom` | `409` | `ROOM_409_HISTORY` | Room hard-delete FK history race |
| Any other unmapped SQLSTATE `23505` | `409` | `WEB_409` | Generic unique conflict |
| NOT NULL (`23502`), CHECK (`23514`), generic FK (`23503`), truncation (`22xxx`), unclassified | `400` | `WEB_400` | Non-uniqueness data integrity / validation errors |

#### Phase 2 Hardening Disposition Summary
- **Fixed in Phase 2 Hardening:** `F-03`, `F-05`, `F-08`, `F-09`.
- **Resolved (Regression Verified):** `F-02`.
- **`DEFERRED / ACCEPTED RISK` (Untouched, Accepted by Owner):** `F-01`, `F-04`, `F-06`, `F-07`.

---

## 3. Verification & Test Suite Summary

### Test Execution Metrics
- **Compile:** `./mvnw -o -DskipTests compile` → `BUILD SUCCESS` (`205` main source files, Java 25, Spring Boot `4.1.2`).
- **Total Test Suite:** **388 tests** (`0` Failures, `0` Errors, `0` Skipped).
- **Build Status:** `BUILD SUCCESS`.

### Key Verification Suites Executed
1. **PostgreSQL Migration, Integrity & Classification:**
   - `Phase1FullPostgresMigrationTest` (`12` tests) — Clean Flyway V1 migration across 20 tables & triggers on PostgreSQL 16 Testcontainers.
   - `Phase1APostgresMigrationTest` (`14` tests) — Table constraints and triggers verification.
   - `VehicleLifecyclePostgresTest` (`4` tests) — Partial unique indexes and atomic vehicle replacement.
   - `DataIntegrityClassificationPostgresTest` (`1` integration test covering `uq_users_email_canonical`, `tenants_id_card_number_key`, `uq_rooms_number_canonical`, `room_code_registry_pkey`, `uq_vehicles_issued_card_code`, `uq_vehicles_tenant_active_quota`, `uq_vehicle_change_requests_pending_tenant`, unmapped `23505`, `23514`, `23502`, `23503`, `22001`).
   - `AppExceptionHandlerDataIntegrityTest` (`18` tests) — Unit classification matrix for `409` vs `400`.
2. **Spring Security, Logging & RBAC Integration:**
   - `AuthenticationSecurityIntegrationTest` (`8` tests) — Filter chain, Redis session invalidation, rate-limiting locks, role checks.
   - `RoomBedSecurityIntegrationTest` (`4` tests) — Room/Bed authorization boundaries.
   - `TenantProfileSecurityIntegrationTest` (`10` tests) — Actor-specific profile editing and blank-identity rejection.
   - `VehicleSecurityIntegrationTest` (`7` tests) — Anonymous 401, RBAC 403, cross-tenant 404, evidence streaming, `/uploads/vehicles/**` denial.
   - `LogMaskingUtilsTest` (`4` tests), `LoginAttemptServiceTest` (`8` tests), `CustomUserDetailsServiceTest` (`1` test), `EmailServiceImplTest` (`1` test), `OtpServiceTest` (`7` tests), `OnboardingServiceImplTest` (`10` tests), `AuthServiceTest` (`17` tests), `UserServiceTest` (`11` tests) — Sensitive log masking (`ListAppender`) & auth unit coverage.
3. **Domain Unit / MVC / OpenAPI & E2E Acceptance:**
   - `OpenApiContractVerificationTest` (`1` test) — Live `/v3/api-docs` route & HTTP method contract verification including absence of `PUT /api/v1/admin/vehicles/{id}`.
   - `TenantServiceImplTest` (`13` tests), `TenantControllerTest` (`8` tests), `TenantProfileControllerTest` (`3` tests), `TenantContractVehicleDtoValidationTest` (`16` tests), `RoomServiceTest` (`15` tests), `RoomControllerTest` (`11` tests), `VehicleServiceImplTest` (`14` tests), `VehicleControllerTest` (`11` tests), `VehicleChangeRequestServiceTest` (`6` tests), `VehicleChangeRequestControllerTest` (`5` tests), `LocalVehicleEvidenceStorageTest` (`3` tests), `DomainErrorCodeTest` (`4` tests).
   - `Phase2CoreModulesE2ETest` (`4` tests) & `ResidentLifecycleE2ETest` (`1` test).

---

## 4. Branch Status Notice

All Phase 2 Hardening changes are committed locally on branch `feat/orca-phase3-final` (on top of `18e1d6e`). No merge, push, or deployment has been performed.
