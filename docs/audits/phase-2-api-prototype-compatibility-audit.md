# Phase 2 API–Prototype Compatibility Audit & Backend Verification Report

- **Authoritative Baseline:** SRS v3.4.1 FINAL | Logical ERD v3.6 FINAL | Conceptual ERD v3.5 FINAL
- **Backend Workspace:** `/home/chinh/Projects/dormitory-management` (`feat/orca-phase3-final` hardened from `18e1d6e`)
- **Frontend Prototype:** `/home/chinh/Projects/dorm-management-prototype` (`feature/backend-api-integration` @ `ac75b3d`)
- **Audit Date:** November 26, 2026 (Phase 2 Hardening sync)
- **Final Verdict:** `PHASE_2_HARDENED_AND_VERIFIED`
- **Test Suite Status:** 388 / 388 Tests Passing (0 Failures, 0 Errors, 0 Skipped)

---

## 1. Executive Summary

This comprehensive audit was conducted to evaluate compatibility between the **KTX Hub Backend** (Spring Boot `4.1.2`, Java 25, PostgreSQL 16/17, Redis 8) and the actual **Frontend Prototype** (React, TypeScript, Vite, Tailwind CSS) for all in-scope Phase 2 functional modules.

### Audit Methodology
Rather than relying on outdated planning inventories (such as obsolete Frontend integration drafts or early backend drafts), this audit traces the Frontend prototype UI surface against the live Backend controllers, DTOs, `*ErrorCode` enums, OpenAPI `/v3/api-docs`, Scalar `/scalar`, and PostgreSQL Testcontainers verification.

### Key Audit Findings & Outcomes
1. **Full Functional Alignment & F-03 Sync:** Across all 4 core Phase 2 domains (Authentication & RBAC, Room & Bed Management, Tenant Profile Management, Vehicle & Evidence Management), the API inventory and compatibility tables match 100% of live controllers, DTOs, HTTP methods (`PATCH` vs `POST`), and `*ErrorCode` enums.
2. **Phase 2 Hardening & Explicit Risk Deferrals:**
   - **Fixed in Phase 2 Hardening (`F-03`, `F-05`, `F-08`, `F-09`):** Synchronized audit/inventory/architecture docs, masked sensitive identifiers in application logs via `LogMaskingUtils`, rejected supplied blank `idCardNumber`/`email` with `400` (`WEB_400_VAL`), and classified `DataIntegrityViolationException` causes accurately between `409` domain/`WEB_409` conflicts and `400` (`WEB_400`).
   - **Resolved (`F-02`):** `PUT /api/v1/admin/vehicles/{id}` remains absent in controller and OpenAPI `/v3/api-docs`; evidence rollback and post-commit/compensation cleanup are verified by regression tests.
   - **Explicitly `DEFERRED / ACCEPTED RISK` (`F-01`, `F-04`, `F-06`, `F-07`):** Core auth/recovery concurrency & Redis atomicity findings are intentionally left untouched and accepted by the owner.
3. **Targeted Backend Gaps Resolved:**
   - **Amenity Metadata Catalog (`BACKEND_GAP_FIXED`):** Created `AmenityController` (`GET /api/v1/admin/amenities`) returning `AmenityResponse` records (`id`, `code`, `name`).
   - **Tenant Table Monthly Rent (`DTO_MISMATCH_RESOLVED`):** Extended `TenantSummaryResponse` with `BigDecimal monthlyRent` fetched from active contracts.
   - **Room Drawer Occupant Identity (`DTO_MISMATCH_RESOLVED`):** Extended `OccupantSummaryResponse` with `String idCardNumber` exclusively via Manager-authorized endpoints.
4. **Verification Integrity:** All 388 Maven unit, slice, security, PostgreSQL Testcontainers, and OpenAPI contract tests pass (`205` main source files compiled cleanly).

---

## 2. Local Repositories & Working Tree Baseline

Both local repositories were inspected without remote fetching or push:

### 2.1. Backend Repository
- **Local Path:** `/home/chinh/Projects/dormitory-management`
- **Active Branch:** `feat/orca-phase3-final`
- **Baseline HEAD Commit:** `18e1d6e` (with Phase 2 Hardening commits layered cleanly on top)
- **Compilation & Tests:** Clean Java compilation (`javac release 25`, Spring Boot `4.1.2`, 205 main files), 388/388 tests passing, scoped Spotless check clean.

### 2.2. Frontend Prototype Repository (Read-only)
- **Local Path:** `/home/chinh/Projects/dorm-management-prototype`
- **Active Branch:** `feature/backend-api-integration`
- **Baseline HEAD Commit:** `ac75b3d` (`docs(erd): add state machine, remove indexing strategies and sync the docs`)
- **Working Tree State:** Intact, with local deletion of obsolete `docs/API_INTEGRATION_PLAN.md`.
- **Integrity:** Strictly read-only; no files modified, created, or deleted by this agent.

---

## 3. Frontend Prototype Source-Code Survey (Screen & Component Inventory)

A recursive scan of the prototype source was conducted to identify every user-facing UI component in Phase 2 across 15 distinct screens, views, and modal dialogs.

| Screen Name & Route | Exact Component File & Lines | Actor / Role | Displayed Information | User Actions | Form Fields | Expected Business Outcome | Relevant SRS FR | Related Mock / Service / API Implementation |
|---|---|---|---|---|---|---|---|---|
| **Login Screen**<br>`/login` | `src/pages/LoginPage.tsx` (L1–L140) | Public | Brand title, error alert, role toggle (mock mode helper), submit button | Input credentials, submit login, navigate to Forgot Password | `identifier` (email/phone/studentId), `password` | Authenticate user, receive JWT & Refresh Token, store in AuthContext, redirect by role | FR-21 | `src/services/api/authAdapter.ts` (`login`), `authService.ts` |
| **Forgot Password Screen**<br>`/forgot-password` | `src/pages/PasswordPage.tsx` (L1–L150) | Public | 3-step wizard (1: Request OTP, 2: Enter OTP, 3: New Password) | Step progression, submit identifier, enter OTP, submit new password | `email` (Step 1), `otp` (Step 2), `newPassword`, `confirmPassword` (Step 3) | Issue recovery request, verify OTP to get recovery token, reset password | FR-24 | `src/services/mock/mockAuthService.ts`, `authService.ts` (`forgotPassword`, `verifyOtp`, `resetPassword`) |
| **Change Password Modal**<br>(Modal Dialog) | `src/components/common/ChangePasswordModal.tsx` (L1–L120) | Authenticated (Any) | Current password input, new password, confirm password, validation errors | Input passwords, submit change, close modal | `currentPassword`, `newPassword`, `confirmPassword` | Invalidate password, update hash, revoke existing sessions | FR-24 | `authService.ts` (`changePassword`), `src/services/api/authAdapter.ts` |
| **Technician Accounts Page**<br>`/admin/technicians` | `src/pages/TechnicianAccountsPage.tsx` (L1–L350) | `MANAGER` | Statistics cards (Total, Active, Locked), Technicians table (Name, Email, Phone, Status, CreatedAt, Actions) | Search by name/email/phone, filter by status, pagination, open Add modal, Lock/Unlock account, Reset password | Search query, status dropdown | Manage staff accounts, inspect activity, revoke access | FR-22 | `src/services/technicianService.ts` (`getTechnicians`, `createTechnician`, `updateStatus`, `resetPassword`) |
| **Add Technician Modal**<br>(Modal Dialog) | `src/pages/TechnicianAccountsPage.tsx` (L220–L310) | `MANAGER` | Form fields, initial password alert dialog upon creation | Fill details, submit form, copy generated temporary password | `fullName`, `email`, `phone` | Create new technician user & staff record, generate secure initial password | FR-22 | `technicianService.ts` (`createTechnician`), `CreateTechnicianResponse` |
| **Room Matrix View**<br>`/admin/rooms` (Matrix Tab) | `src/components/rooms/RoomMatrixView.tsx` (L1–L180), `RoomsPage.tsx` | `MANAGER` | Building selector, floor grouping, room cards with bed slots, occupancy badges (Available, Occupied, Maintenance) | Click room card to open drawer, filter by building/floor/gender/status | Search query, building filter, floor filter, gender filter, status filter | Overview of dormitory occupancy across all physical locations | FR-09 | `apiDormService.ts`, `roomAdapter.ts` (`getMatrix`) |
| **Room Table View**<br>`/admin/rooms` (Table Tab) | `src/components/rooms/RoomTableView.tsx` (L1–L160) | `MANAGER` | Table columns: Room Number, Floor, Gender Policy, Base Price, Total Beds, Available Beds, Status, Actions | Search, filter, sort, pagination, click room row for drawer, edit room, archive room | Search query, filters | Tabular inspection and management of room inventory | FR-09 | `roomAdapter.ts` (`getRooms`), `apiDormService.ts` |
| **Room Detail Drawer**<br>(Side Drawer) | `src/components/rooms/RoomDetailDrawer.tsx` (L1–L240) | `MANAGER` | Room metadata (Code, Floor, Gender, Price, Amenities), Bed slot list with status, Occupant details (Name, CCCD, Code, Contract End Date) | Change bed status (Available ↔ Maintenance), Edit Room button, Archive Room button, Close drawer | Bed status transition toggle | Deep inspection of room and bed occupancy; maintenance scheduling | FR-09, FR-10 | `roomAdapter.ts` (`getRoomById`, `updateBedStatus`), `OccupantSummaryResponse` |
| **Add / Edit Room Modal**<br>(Modal Dialog) | `src/components/rooms/AddEditRoomModal.tsx` (L1–L220) | `MANAGER` | Form fields: Room Number, Floor, Gender Policy, Base Price, Amenities checklist | Fill form, select amenities, submit create/update | `roomNumber`, `floor`, `genderPolicy`, `basePrice`, `amenityIds` | Create new room with 4 automatic beds, or update existing room metadata | FR-09 | `apiDormService.ts` (`createRoom`, `updateRoom`), `getAmenities` |
| **Delete / Archive Room Modal**<br>(Modal Dialog) | `src/components/rooms/DeleteRoomModal.tsx` (L1–L90) | `MANAGER` | Warning message, confirmation prompt for archiving or permanently deleting an empty room | Confirm archive/delete action, cancel | Room ID | Safely soft-archive room with historical preservation, or hard-delete if never used | FR-09 | `roomAdapter.ts` (`archiveRoom`, `deleteRoomPermanent`) |
| **Manager Tenants Page**<br>`/admin/tenants` | `src/pages/TenantsPage.tsx` (L1–L320) | `MANAGER` | Statistics cards, Tenants table: Tenant Code, Full Name, Gender, Room & Bed, Phone, Monthly Rent, Contract End Date, Status, Actions | Search by code/name/phone, filter by status, view tenant details, edit tenant identity, reset password | Search query, status dropdown | Comprehensive resident directory and administrative management | FR-06, FR-07 | `apiTenantService.ts`, `mockTenantService.ts`, `TenantSummaryResponse` |
| **Tenant Detail Modal**<br>(Modal Dialog) | `src/pages/TenantsPage.tsx` (L230–L310) | `MANAGER` | Detailed view: CCCD, DOB, Permanent Address, Emergency Contact, Active Contract Info, Vehicle Info | Inspect profile, trigger password reset, edit details | None (read-only view with action triggers) | Inspect resident identity, emergency contacts, and active contracts | FR-06 | `apiTenantService.ts` (`getTenantById`) |
| **Manager Edit Tenant Modal**<br>(Modal Dialog) | `src/pages/TenantsPage.tsx` (L315–L400) | `MANAGER` | Identity correction form (Full Name, Gender, DOB, CCCD, Phone, Permanent Address, Emergency Contact) | Fill form, submit updates | `fullName`, `gender`, `dateOfBirth`, `idCardNumber`, `phone`, `permanentAddress`, `emergencyContactName`, `emergencyContactPhone` | Correct administrative or identity errors for a tenant | FR-06 | `apiTenantService.ts` (`updateTenantAdmin`) |
| **Tenant Portal Profile**<br>`/tenant/profile` | `src/pages/TenantPortalPage.tsx` (L1–L280) | `TENANT` | Personal profile card, Contract summary, Room & Bed card, Contact info editor | Edit personal contact details, submit phone/address/emergency contact | `phone`, `permanentAddress`, `emergencyContactName`, `emergencyContactPhone` | Self-service contact information maintenance | FR-07 | `apiTenantService.ts` (`updateProfile`), `UserProfileResponse` |
| **Manager Vehicles Page**<br>`/admin/vehicles` | `src/pages/VehiclesPage.tsx` (L1–L380) | `MANAGER` | Statistics cards (Total, Issued, Pending), Vehicles table: Tenant Name & Code, Room, Vehicle Type, License Plate, Brand, Color, Card Code, Status, Actions | Search, filter by status/type, view vehicle details & evidence images, issue card, reject registration, revoke card | Search query, status filter, card code dialog, rejection reason dialog | Manage dormitory vehicle registrations, physical cards, and parking safety | FR-08 | `apiVehicleService.ts`, `mockVehicleService.ts`, `AdminVehicleController` |
| **Tenant Vehicles Page**<br>`/tenant/vehicles` | `src/pages/TenantVehiclesPage.tsx` (L1–L340) | `TENANT` | Active vehicle card (Status, License Plate, Card Code, Registration Date), Vehicle history list, Change request status | Register new vehicle (with front/back image upload), Request vehicle change, View evidence | Type, License plate, Brand, Color, Front image, Back image, Proposed fields | Register vehicle, submit evidence, track card issuance and change requests | FR-08 | `apiVehicleService.ts`, `TenantVehicleController`, `TenantVehicleChangeRequestController` |

---

## 4. Complete UI Functionality–API Compatibility Matrix & Reconciliation

Every UI action, table, modal, and drawer in the Prototype has been evaluated against the Backend implementation.

### Classification Categories & Reconciled Counts:
Across the 15 surveyed screens, **44 distinct UI interaction entries** were evaluated and classified:
- `DIRECT_MATCH` (23 entries): Backend API route, request/response payload, and HTTP method directly satisfy the UI requirement.
- `FE_ADAPTER_REQUIRED` (16 entries across 4 primary workflow groups + DTO/HTTP method mappings): Frontend adapter formatting/field/method mapping against live Backend contracts (key flows detailed in Section 6).
- `DTO_MISMATCH_RESOLVED` (2 entries): Response DTO extended in Backend (`TenantSummaryResponse.monthlyRent`, `OccupantSummaryResponse.idCardNumber`).
- `BACKEND_GAP_FIXED` (1 entry): Canonical endpoint implemented (`GET /api/v1/admin/amenities`).
- `REMOVED_PER_USER_DECISION` (1 entry): Pending vehicle registration editing (`PUT /api/v1/admin/vehicles/{id}`) removed per SRS FR-08.
- `OUT_OF_SCOPE` (1 entry): Profile avatar upload (omitted in Phase 2 SRS v3.4.1 and ERD v3.6).
- `DEFERRED_PER_USER_DECISION` (4 review findings): `F-01`, `F-04`, `F-06`, `F-07` explicitly marked `DEFERRED / ACCEPTED RISK` (while `F-03`, `F-05`, `F-08`, `F-09` are fixed in Phase 2 Hardening and `F-02` is resolved).

| Screen / Component | User Action or Data Requirement | SRS FR | Backend Endpoint | Implemented Contract & Behavior | Classification | Action Taken / Status |
|---|---|---|---|---|---|---|
| **LoginPage** | Authenticate user with Identifier & Password | FR-21 | `POST /api/v1/auth/login` | `LoginRequest` (`identifier`, `password`) → `SingleResponse<AuthResponse>` (`accessToken`, `refreshToken`, `user`) | `FE_ADAPTER_REQUIRED` | Backend resolves canonical email for `MANAGER`/`TECHNICIAN` and 12-digit CCCD only for `TENANT` (phone & student ID are not login keys). |
| **AuthContext** | Silent token refresh on 401 | FR-21 | `POST /api/v1/auth/refresh-token` | `RefreshTokenRequest` (`refreshToken`) → `SingleResponse<AuthResponse>` | `DIRECT_MATCH` | Seamless JWT token rotation. |
| **AuthContext / Navbar** | Single device logout | FR-21 | `POST /api/v1/auth/logout` | `RefreshTokenRequest` (`refreshToken`) + `Authorization` header → `SuccessResponse` | `DIRECT_MATCH` | Revokes refresh token JTI and blacklists access token in Redis. |
| **AuthContext / Profile** | Global logout (all devices) | FR-21 | `POST /api/v1/auth/logout-all` | Authenticated bearer token → `SuccessResponse` | `DIRECT_MATCH` | Rotates user `sessionGeneration`, terminating all active sessions immediately. |
| **PasswordPage (Step 1)** | Request OTP for password reset | FR-24 | `POST /api/v1/auth/forgot-password` | `PasswordRecoveryRequest` (`identifier`) → `SingleResponse<PasswordRecoveryRequestedResponse>` (`recoveryRequestId`, `expiresInSeconds`) | `FE_ADAPTER_REQUIRED` | Adapter Requirement 1: Passes identifier (email for Staff, CCCD for Tenant) and retains `recoveryRequestId`. |
| **PasswordPage (Step 2)** | Verify OTP code | FR-24 | `POST /api/v1/auth/verify-forgot-password-otp` | `VerifyRecoveryOtpRequest` (`recoveryRequestId`, `otp`) → `SingleResponse<VerifyResetPasswordOtpResponse>` (`resetToken`) | `FE_ADAPTER_REQUIRED` | Adapter Requirement 1: Submits `recoveryRequestId` + 6-digit `otp` and retains opaque `resetToken`. |
| **PasswordPage (Step 3)** | Reset password with token | FR-24 | `POST /api/v1/auth/reset-password` | `ResetPasswordRequest` (`resetToken`, `newPassword`, `confirmPassword`) → `SuccessResponse` | `FE_ADAPTER_REQUIRED` | Adapter passes `resetToken` field name from Step 2. |
| **ChangePasswordModal** | Change password while logged in | FR-24 | `POST /api/v1/auth/change-password` | `ChangePasswordRequest` (`oldPassword`, `newPassword`, `confirmPassword`; no OTP) → `SuccessResponse` | `FE_ADAPTER_REQUIRED` | Maps modal current-password field to `oldPassword`. |
| **Navbar / Profile** | Fetch current user identity | FR-21 | `GET /api/v1/users/me` | Bearer token → `SingleResponse<UserProfileResponse>` (`id`, `email`, `role`, `status`, `staffProfile`, `tenantProfile`) | `DIRECT_MATCH` | Matches directly. |
| **TechnicianAccountsPage** | List & search technician accounts | FR-22 | `GET /api/v1/users/technicians` | Query params: `page`, `size`, `search`, `active` (`Boolean`) → `SingleResponse<PageResponse<TechnicianResponse>>` | `FE_ADAPTER_REQUIRED` | Maps UI status toggle to `active` boolean query parameter. |
| **TechnicianAccountsPage** | Create new technician account | FR-22 | `POST /api/v1/users/technicians` | `CreateTechnicianRequest` (`fullName`, `email`, `phone`) → `SingleResponse<CreateTechnicianResponse>` (`technician`, `initialPassword`) | `FE_ADAPTER_REQUIRED` | Adapter Requirement 2: Extracts nested `initialPassword` to display in initial password modal. |
| **TechnicianAccountsPage** | Lock or Unlock technician | FR-22 | `PATCH /api/v1/users/technicians/{id}/status` | `UpdateTechnicianStatusRequest` (`active`: `true`/`false`) → `SingleResponse<TechnicianResponse>` | `FE_ADAPTER_REQUIRED` | Maps `ACTIVE`/`LOCKED` toggle to `active` boolean; locking immediately invalidates Redis sessions. |
| **TechnicianAccountsPage** | Reset technician temporary password | FR-22 | `POST /api/v1/users/technicians/{id}/reset-password` | No body required → `SingleResponse<OneTimePasswordResponse>` (`temporaryPassword`) | `DIRECT_MATCH` | Generates new temporary password, rotates `sessionGeneration`. |
| **RoomMatrixView** | Render visual floor & room grid | FR-01 | `GET /api/v1/admin/rooms/matrix` | Query params: `floor`, `genderPolicy`, `search` → `SingleResponse<List<RoomDetailResponse>>` | `FE_ADAPTER_REQUIRED` | Groups flat room list by `floor` on client if grouped view desired. |
| **RoomTableView** | List, search, and filter rooms | FR-05 | `GET /api/v1/admin/rooms` | Query params: `page`, `size`, `floor`, `genderPolicy`, `search` → `SingleResponse<PageResponse<RoomSummaryResponse>>` | `DIRECT_MATCH` | Returns paginated list with bed capacity/occupancy breakdown and `basePrice`. |
| **RoomDetailDrawer** | Inspect room details & bed occupants | FR-01, FR-05 | `GET /api/v1/admin/rooms/{id}` | Path param `id` → `SingleResponse<RoomDetailResponse>` (includes `beds` list with `OccupantSummaryResponse`) | `DTO_MISMATCH_RESOLVED` | Extended `OccupantSummaryResponse` with `idCardNumber` so CCCD renders directly. |
| **RoomDetailDrawer** | Toggle bed status (Available ↔ Maintenance) | FR-03 | `PATCH /api/v1/admin/beds/{id}/status` | `UpdateBedStatusRequest` (`status`: `AVAILABLE` / `MAINTENANCE`) → `SingleResponse<BedResponse>` | `DIRECT_MATCH` | Prevents transitioning `OCCUPIED` beds or beds with active contracts to `MAINTENANCE`. |
| **AddEditRoomModal** | Populate amenity checklist | FR-02 | `GET /api/v1/admin/amenities` | No params → `SingleResponse<List<AmenityResponse>>` (`id`, `code`, `name`) | `BACKEND_GAP_FIXED` | Canonical endpoint returning all 7 system amenities. |
| **AddEditRoomModal** | Create new room | FR-02 | `POST /api/v1/admin/rooms` | `CreateRoomRequest` (`roomNumber`, `floor`, `roomType`, `genderPolicy`, `basePrice`, `amenities: List<RoomAmenityRequest(amenityId, quantity)>`) → `201 SingleResponse<RoomDetailResponse>` | `FE_ADAPTER_REQUIRED` | Provisions 4/6/8 beds (`G01`..`G0n`) per `roomType`; maps selected amenities with quantities. |
| **AddEditRoomModal** | Update existing room | FR-03 | `PUT /api/v1/admin/rooms/{id}` | `UpdateRoomRequest` (`roomNumber`, `genderPolicy`, `basePrice`, `amenities`) → `SingleResponse<RoomDetailResponse>` | `FE_ADAPTER_REQUIRED` | Blocks `genderPolicy` or `roomNumber` changes if occupied or active contract exists; maps `amenities` with quantities. |
| **DeleteRoomModal** | Soft-archive empty room | FR-04 | `DELETE /api/v1/admin/rooms/{id}` | Path param `id` → `SuccessResponse` | `DIRECT_MATCH` | Verifies zero occupants & active contracts; sets `archivedAt` (`@SQLRestriction`). |
| **DeleteRoomModal** | Hard-delete never-used room | FR-04 | `DELETE /api/v1/admin/rooms/{id}/permanent` (or `/incorrect`) | Path param `id` → `SuccessResponse` | `DIRECT_MATCH` | Checks `RoomHistoryGuard` (`room_code_registry.used_in_business`, contracts, tickets); physically removes unused room. |
| **RoomsPage** | List archived rooms for restore | FR-04 | `GET /api/v1/admin/rooms/archived` | Query params: `page`, `size`, `floor`, `genderPolicy`, `search` → `SingleResponse<PageResponse<ArchivedRoomSummaryResponse>>` | `DIRECT_MATCH` | Lists archived rooms with `archivedAt` timestamp. |
| **RoomsPage** | Restore archived room | FR-04 | `POST /api/v1/admin/rooms/{id}/restore` | Path param `id` → `SingleResponse<RoomDetailResponse>` | `DIRECT_MATCH` | Restores archived room (`archivedAt = null`). |
| **TenantsPage** | List, search, filter tenants | FR-06 | `GET /api/v1/admin/tenants` | Query params: `page`, `size`, `search`, `roomId`, `status` → `SingleResponse<PageResponse<TenantSummaryResponse>>` | `DTO_MISMATCH_RESOLVED` | Extended `TenantSummaryResponse` with `monthlyRent` from active contract for "Giá thuê". |
| **TenantsPage (Drawer)** | Inspect full tenant profile | FR-06 | `GET /api/v1/admin/tenants/{id}` | Path param `id` → `SingleResponse<TenantDetailResponse>` | `DIRECT_MATCH` | Returns full identity, contacts, `currentContract`, `contractHistory`, and `vehicles`. |
| **TenantsPage (Edit Modal)** | Manager updates tenant identity & email | FR-06 | `PUT /api/v1/admin/tenants/{id}` | `ManagerUpdateTenantRequest` (`fullName`, `idCardNumber`, `gender`, `email`, `phone`, `permanentAddress`, `dateOfBirth`, `emergencyContactName`, `emergencyContactPhone`) → `SingleResponse<TenantDetailResponse>` | `DIRECT_MATCH` | Omitted/`null` identity retains existing value; supplied blank `idCardNumber`/`email` rejected with `400 WEB_400_VAL`; optional contact clearing preserved; phone non-unique. |
| **TenantsPage (Reset Password)** | Manager resets tenant password | FR-24 | `POST /api/v1/admin/tenants/{id}/reset-password` | Path param `id` → `SingleResponse<OneTimePasswordResponse>` (`temporaryPassword`) | `DIRECT_MATCH` | Generates temporary password, invalidates all tenant sessions. |
| **TenantPortalPage** | Tenant updates contact details | FR-07 | `PATCH /api/v1/tenant/profile` | `TenantContactUpdateRequest` (`phone`, `permanentAddress`, `emergencyContactName`, `emergencyContactPhone`) → `SingleResponse<UserProfileResponse>` | `DIRECT_MATCH` | Rejects blank `phone`/`permanentAddress` with `400`, clears blank emergency fields, omits identity mutation. |
| **TenantPortalPage (Avatar)** | Tenant uploads profile avatar | — | — | Prototype UI has an avatar upload button, but ERD v3.6 and SRS v3.4.1 exclude avatars. | `OUT_OF_SCOPE` | Documented discrepancy; UI falls back gracefully to default initials. |
| **VehiclesPage** | List & filter dormitory vehicles | FR-08 | `GET /api/v1/admin/vehicles` | Query params: `page`, `size`, `search`, `status` → `SingleResponse<PageResponse<VehicleResponse>>` | `DIRECT_MATCH` | Returns paginated vehicles with tenant info and evidence availability booleans. |
| **VehiclesPage** | Inspect vehicle details | FR-08 | `GET /api/v1/admin/vehicles/{id}` | Path param `id` → `SingleResponse<VehicleResponse>` | `DIRECT_MATCH` | Matches directly. |
| **VehiclesPage** | Issue parking card | FR-08 | `PATCH /api/v1/admin/vehicles/{id}/issue-card` | `IssueCardRequest` (`cardCode`) → `SingleResponse<VehicleResponse>` (`status: ISSUED`) | `DIRECT_MATCH` | Enforces unique canonical `cardCode` across `ISSUED` vehicles (`409 VEHICLE_409_CARD_CODE`). |
| **VehiclesPage** | Reject pending registration | FR-08 | `PATCH /api/v1/admin/vehicles/{id}/reject` | `RejectVehicleRequest` (`reason`) → `SingleResponse<VehicleResponse>` (`status: REJECTED`) | `FE_ADAPTER_REQUIRED` | Uses HTTP `PATCH` and request field `reason` (`rejectionReason` on response). |
| **VehiclesPage** | Revoke parking card | FR-08 | `PATCH /api/v1/admin/vehicles/{id}/revoke-card` | Path param `id` → `SingleResponse<VehicleResponse>` (`status: REVOKED`) | `DIRECT_MATCH` | Releases card code for future assignment; blocks if change request pending. |
| **VehiclesPage / Drawer** | View front / back registration evidence | FR-08 | `GET /api/v1/admin/vehicles/{vehicleId}/evidence/{side}` | Path params `vehicleId`, `side` (`front` / `back`) → Binary `Resource` stream | `DIRECT_MATCH` | Private storage stream (`image/jpeg`, `image/png`, `image/webp`); `/uploads/vehicles/**` denied. |
| **VehiclesPage (Edit)** | Edit pending vehicle registration | FR-08 | `PUT /api/v1/admin/vehicles/{id}` | Removed per explicit user decision and SRS FR-08 alignment. | `REMOVED_PER_USER_DECISION` | Endpoint, DTO, service methods, and edit tests remain removed (F-02 verified). |
| **TenantVehiclesPage** | View tenant's active & historical vehicles | FR-08 | `GET /api/v1/tenant/vehicles` | Bearer token → `SingleResponse<List<VehicleResponse>>` | `DIRECT_MATCH` | Returns full vehicle history for authenticated tenant. |
| **TenantVehiclesPage** | Register new vehicle | FR-08 | `POST /api/v1/tenant/vehicles` | Multipart: `TenantRegisterVehicleRequest` + `frontImage`, `backImage` → `201 SingleResponse<VehicleResponse>` (`PENDING_CARD`) | `FE_ADAPTER_REQUIRED` | Adapter Requirement 3: Constructs `FormData` with `@ModelAttribute` fields and optional image files. |
| **TenantVehiclesPage** | View change request history | FR-08 | `GET /api/v1/tenant/vehicle-change-requests` | Bearer token → `SingleResponse<List<VehicleChangeRequestResponse>>` | `DIRECT_MATCH` | Matches directly. |
| **TenantVehiclesPage** | Submit vehicle change request | FR-08 | `POST /api/v1/tenant/vehicle-change-requests` | Multipart: `CreateVehicleChangeRequest` + `frontImage`, `backImage` → `201 SingleResponse<VehicleChangeRequestResponse>` | `FE_ADAPTER_REQUIRED` | Adapter Requirement 4: Constructs `FormData` with proposed fields and optional image files. |
| **VehiclesPage (Admin)** | List & review vehicle change requests | FR-08 | `GET /api/v1/admin/vehicle-change-requests` | Query params: `page`, `size`, `search`, `status` → `SingleResponse<PageResponse<VehicleChangeRequestResponse>>` | `DIRECT_MATCH` | Matches directly. |
| **VehiclesPage (Admin)** | Approve vehicle change request | FR-08 | `PATCH /api/v1/admin/vehicle-change-requests/{id}/approve` | Path param `id` → `SingleResponse<VehicleChangeRequestResponse>` (`APPROVED`) | `FE_ADAPTER_REQUIRED` | Uses HTTP `PATCH`; atomically sets old vehicle `REPLACED` and inserts new successor vehicle as `ISSUED` carrying the same `cardCode`. |
| **VehiclesPage (Admin)** | Reject vehicle change request | FR-08 | `PATCH /api/v1/admin/vehicle-change-requests/{id}/reject` | `RejectVehicleChangeRequest` (`reason`) → `SingleResponse<VehicleChangeRequestResponse>` (`REJECTED`) | `FE_ADAPTER_REQUIRED` | Uses HTTP `PATCH` and field `reason`; preserves existing vehicle in `ISSUED` state. |

---

## 5. Confirmed Backend Gaps & Implemented Solutions

During the UI survey and service tracing, three concrete gaps were identified where the prototype required data or endpoints that were either missing or incomplete in the backend.

### 5.1. Gap 1: Missing System Amenities Catalog Endpoint (`BACKEND_GAP_FIXED`)
- **UI Requirement:** In `src/components/rooms/AddEditRoomModal.tsx` (lines 45–65, 140–180), creating or editing a room requires presenting an interactive checkbox list of amenities (e.g., Air Conditioner, Water Heater, Refrigerator). The form payload requires an array of UUIDs (`amenityIds: string[]`).
- **Backend Initial State:** The PostgreSQL database already contains the master `amenities` table, Flyway seed data, and Spring Data JPA `AmenityRepository`. However, there was no exposed REST controller or endpoint to retrieve this list.
- **Implemented Fix:**
  1. Created `AmenityResponse` DTO record (`UUID id`, `String code`, `String name`).
  2. Implemented `AmenityController` under single canonical endpoint `GET /api/v1/admin/amenities` secured for `MANAGER` role.
  3. Added `AmenityControllerTest` verifying HTTP 200 and the list of amenities.

### 5.2. Gap 2: Tenant Summary Missing Monthly Rent (`DTO_MISMATCH_RESOLVED`)
- **UI Requirement:** In `src/pages/TenantsPage.tsx` (lines 205, 249), the Tenants table renders a column header `<th>Giá thuê</th>` and formats `{money(tenant.monthlyRent)}`.
- **Backend Initial State:** `TenantSummaryResponse` only contained basic tenant info, room number, bed code, and contract end date, leaving `monthlyRent` absent.
- **Implemented Fix:**
  1. Extended `TenantSummaryResponse` with `@Builder.Default BigDecimal monthlyRent = null`.
  2. Updated `TenantServiceImpl.toSummaryResponse()` to look up the tenant's active contract and populate `monthlyRent = contract.getMonthlyRent()` following approved pricing rules.
  3. Updated `TenantServiceImplTest` to verify `monthlyRent` mapping.

### 5.3. Gap 3: Room Detail Drawer Occupant Missing CCCD (`DTO_MISMATCH_RESOLVED`)
- **UI Requirement:** In `src/components/rooms/RoomDetailDrawer.tsx` (line 185), each occupied bed card renders:
  ```tsx
  <span>CCCD: {slot.tenant.cccd}</span>
  ```
- **Backend Initial State:** `OccupantSummaryResponse` only contained `id`, `tenantCode`, and `fullName`.
- **Implemented Fix:**
  1. Added `String idCardNumber` to `OccupantSummaryResponse`.
  2. Updated `RoomServiceImpl.toOccupantSummaryResponse()` to map `tenant.getIdCardNumber()`.
  3. Verified that `OccupantSummaryResponse` is exclusively exposed via Manager-authorized endpoints (`/api/v1/admin/rooms/**`).
  4. Updated `RoomServiceTest` to verify `idCardNumber` population.

---

## 6. Explicitly Identified Frontend Adapter Requirements

The following four interactions represent normal Frontend integration mapping where the Backend adheres strictly to SRS v3.4.1 and ERD v3.6:

> [!IMPORTANT]
> **Distinction Between Backend Compatibility and Frontend Integration:**  
> Backend API compatibility has been thoroughly verified via automated integration, repository, and security tests. Actual end-to-end browser execution of the frontend adapters will take place during the subsequent frontend integration phase.

### Interaction 1: Password Recovery State & OTP Multi-step Flow (FR-24)
- **Backend Endpoints:**
  - `POST /api/v1/auth/forgot-password` takes `{ "identifier": emailOrCccd }` and returns `{ "recoveryRequestId": "<uuid>", "expiresInSeconds": 300 }` (generic 200).
  - `POST /api/v1/auth/verify-forgot-password-otp` takes `{ "recoveryRequestId": "<uuid>", "otp": "123456" }` and returns `{ "resetToken": "<opaque-token>" }`.
  - `POST /api/v1/auth/reset-password` takes `{ "resetToken": "<opaque-token>", "newPassword": "...", "confirmPassword": "..." }`.
- **Adapter Work:** The frontend `authAdapter.ts` sends `identifier` (email for Staff, 12-digit CCCD for Tenant) in Step 1, retains `recoveryRequestId` in state to submit alongside `otp` in Step 2, and retains `resetToken` from Step 2 to submit with `newPassword` and `confirmPassword` in Step 3.

### Interaction 2: Technician Initial Temporary Password Presentation (FR-22)
- **Backend Endpoint:** `POST /api/v1/users/technicians` returns HTTP `201` with `SingleResponse<CreateTechnicianResponse>` containing `{ technician: { id, userId, email, phone, fullName, userStatus, createdAt }, initialPassword }`.
- **Adapter Work:** The frontend adapter extracts `data.initialPassword` (alongside `data.technician`) to trigger the initial password modal, maps status toggle to `active` (`UpdateTechnicianStatusRequest`), and maps the list status filter to query param `active`.

### Interaction 3: Tenant Vehicle Registration Multipart Form Submission (FR-08)
- **Backend Endpoint:** `POST /api/v1/tenant/vehicles` requires `multipart/form-data` with text parameters (`vehicleType`, `licensePlate`, `brand`, `color`) and binary parts (`frontImage`, `backImage`).
- **Adapter Work:** The frontend `apiVehicleService.ts` constructs a `FormData` object appending string fields and `File` blobs before dispatching the POST request.

### Interaction 4: Tenant Vehicle Change Request Multipart Form Submission (FR-08)
- **Backend Endpoint:** `POST /api/v1/tenant/vehicle-change-requests` requires `multipart/form-data` with text parameters (`currentVehicleId`, `proposedVehicleType`, `proposedLicensePlate`, `proposedBrand`, `proposedColor`) and binary parts (`frontImage`, `backImage`).
- **Adapter Work:** The frontend vehicle adapter constructs a `FormData` object with the proposed vehicle attributes and replacement image blobs.

---

## 7. Documented UI / SRS Discrepancies

1. **Pending Vehicle Registration Editing (FR-08):**
   - *Prototype Behavior:* The prototype vehicle table previously included an "Edit" action button for vehicles in pending status.
   - *SRS Specification:* SRS v3.4.1 FR-08 defines vehicle registration as a submit-and-review workflow. If rejected, the tenant submits a new registration. There is no editing of submitted registrations.
   - *Resolution:* Per explicit user instruction, editing of pending vehicle registrations was completely removed from the backend.
2. **Profile Avatar Upload:**
   - *Prototype Behavior:* `TenantPortalPage.tsx` features an avatar upload button.
   - *SRS Specification:* SRS v3.4.1 explicitly excludes avatar file storage from Phase 2. Neither the `users` nor `tenants` table has an `avatar_url` column in ERD v3.6.
   - *Resolution:* Marked as `OUT_OF_SCOPE` for Phase 2. The prototype UI gracefully falls back to generating initials badges based on `fullName`.

---

## 8. Removal of Obsolete Vehicle Editing Functionality

In accordance with the approved user decision, the pending vehicle edit workflow was removed:

1. **Controller Endpoint Removed:** Removed `@PutMapping("/{id}")` and `updateVehicle(...)` from `src/main/java/com/uit/dormmanagement/controllers/AdminVehicleController.java`.
2. **Service Interface & Implementation Cleaned:**
   - Removed `updateVehicle(...)` from `VehicleService.java` and `VehicleServiceImpl.java`.
   - Removed `validateEffectiveState(...)` and unused state clearance logic from `VehicleRegistrationValidator.java` and `VehicleRegistrationValidatorImpl.java`.
3. **Obsolete DTO Deleted:** Deleted `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/UpdateVehicleRequest.java`.
4. **Obsolete Tests Removed:** Removed `updateVehicle_shouldReturn200` from `VehicleControllerTest.java`, `updateVehicleRequest_whenMissingFields_shouldFailValidation` from `TenantContractVehicleDtoValidationTest.java`, and `validateEffectiveState_shouldCheckRules` from `VehicleRegistrationValidatorTest.java`.

---

## 9. Phase 2 Hardening & Deferred Risk Register (F-01 – F-09)

In accordance with owner decisions on Phase 2 Hardening (`docs/audits/phase-2-independent-code-review.md` §15):

- **Fixed in Phase 2 Hardening (`F-03`, `F-05`, `F-08`, `F-09`):**
  - **F-03 (Major):** Synchronized API contract inventory, prototype compatibility audit, `AGENT.md`, and `docs/ARCHITECTURE.md` with live controllers, DTOs, `*ErrorCode` enums, `V1__clean_baseline.sql`, `/v3/api-docs`, and Scalar `/scalar` (verified via `OpenApiContractVerificationTest`).
  - **F-05 (Minor):** Added `LogMaskingUtils` and masked sensitive identifiers/emails/CCCDs/phones across `LoginAttemptServiceImpl`, `CustomUserDetailsService`, `EmailServiceImpl`, `OtpServiceImpl`, `OnboardingServiceImpl`, `AuthServiceImpl`, `UserServiceImpl`, and `AppExceptionHandler`.
  - **F-08 (Minor):** Rejected supplied blank `idCardNumber` / `email` (`""` or whitespace) with HTTP `400` (`WEB_400_VAL`) in `ManagerUpdateTenantRequest` and `TenantServiceImpl.updateTenantAsManager` (while retaining omitted/`null` identity values and preserving optional contact clearing); verified consistent blank handling on `PATCH /api/v1/tenant/profile`.
  - **F-09 (Minor):** Classified `DataIntegrityViolationException` causes via `DataIntegrityViolationClassifier` (`AppExceptionHandler` + service `saveAndFlush` boundaries) so known unique conflicts return exact domain `409` codes, unmapped `23505` returns `409 WEB_409`, and `23502`/`23514`/`23503`/`22xxx`/unclassified errors remain `400 WEB_400`.
- **Resolved (`F-02` — Major):** Vehicle evidence replacement/rollback cleanup & post-commit deletion verified by regression tests; `PUT /api/v1/admin/vehicles/{id}` remains absent in both controller and OpenAPI `/v3/api-docs`.
- **`DEFERRED / ACCEPTED RISK` (`F-01`, `F-04`, `F-06`, `F-07` — NOT Implemented, Accepted by Owner):**
  - **F-01 (Major):** Old-email password recovery challenge race vs. Manager email correction (`PasswordRecoveryServiceImpl`).
  - **F-04 (Minor):** Non-atomic Redis login failure counter/TTL/lock in `LoginAttemptServiceImpl`.
  - **F-06 (Minor):** Non-atomic missing Redis session generation initialization in `UserTokenInvalidationServiceImpl`.
  - **F-07 (Minor):** Undelivered OTP challenge retained in Redis after email delivery failure in `PasswordRecoveryServiceImpl`.

---

## 10. Verification & Quality Assurance Evidence

The hardened Backend implementation was subjected to comprehensive verification:

```bash
# 1. Compilation Verification
./mvnw -o -DskipTests compile
-> Result: BUILD SUCCESS (205 main source files compiled, zero errors)

# 2. Complete Test Suite Execution
./mvnw -o test
-> Result: Tests run: 388, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS.

# 3. Git Diff Formatting & Conflict Check
git diff --check
-> Result: Clean (zero whitespace errors, zero merge conflict markers)

# 4. Scoped Spotless Formatting Check (38 modified/new Java files)
./mvnw -o spotless:check -DspotlessFiles="..."
-> Result: BUILD SUCCESS (0 files need formatting changes)
```

---

## 11. Final Readiness Verdict

**Verdict:** `PHASE_2_HARDENED_AND_VERIFIED`

The Backend API suite satisfies the Phase 2 functional, data contract, logging, validation, and data-integrity requirements (`F-03`, `F-05`, `F-08`, `F-09` fixed; `F-02` resolved and regression verified; `F-01`, `F-04`, `F-06`, `F-07` explicitly `DEFERRED / ACCEPTED RISK`), with **388/388** Maven tests passing.
