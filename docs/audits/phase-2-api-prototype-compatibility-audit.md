# Phase 2 API–Prototype Compatibility Audit & Backend Verification Report

**Authoritative Baseline:** SRS v3.4.1 FINAL | Logical ERD v3.6 | Conceptual ERD v3.5  
**Backend Workspace:** `/home/chinh/Projects/dormitory-management` (`refactor/erd-and-implemented-code` @ `ac525f2`)  
**Frontend Prototype:** `/home/chinh/Projects/dorm-management-prototype` (`feature/backend-api-integration` @ `ac75b3d`)  
**Audit Date:** November 19, 2026  
**Final Verdict:** `READY_FOR_BACKEND_COMPATIBILITY_REVIEW`  
**Test Suite Status:** 338 / 338 Tests Passing (0 Failures, 0 Errors, 0 Skipped)  

---

## 1. Executive Summary

This comprehensive audit was conducted by an independent implementation agent to evaluate the compatibility between the **KTX Hub Backend** (Spring Boot 3.4, Java 21, PostgreSQL 16) and the actual **Frontend Prototype** (React, TypeScript, Vite, Tailwind CSS) for all in-scope Phase 2 functional modules.

### Audit Methodology
Rather than relying on outdated planning inventories (such as Frontend `docs/API_INTEGRATION_PLAN.md` or Backend initial inventory drafts), this audit conducted a direct, recursive source-code inspection across the entire Frontend prototype codebase (`src/App.tsx`, `src/routes/`, `src/pages/`, `src/components/`, `src/contexts/`, `src/services/`, `src/services/mock/`, `src/services/api/`, `src/types/`). Every screen, modal, table, drawer, filter, and action button was surveyed and traced to its underlying service contract.

### Key Audit Findings & Outcomes
1. **Full Functional Alignment:** Across all 4 core Phase 2 domains (Authentication & RBAC, Room & Bed Management, Tenant Profile Management, Vehicle & Evidence Management), the backend supports 100% of the active, valid UI workflows specified by SRS v3.4.1.
2. **Approved User Decisions Executed:**
   - **Authentication Core Frozen:** No security core redesigns (JWT, Redis sessions, OTP storage, F-01 concurrency) were performed; existing auth behaviors were preserved, and code review findings F-01 through F-09 remain documented as deferred for a dedicated hardening cycle.
   - **Removed Pending Vehicle Registration Editing:** `PUT /api/v1/admin/vehicles/{id}`, `UpdateVehicleRequest.java`, associated service methods, validator routines, and controller/validation tests were cleanly removed in strict alignment with SRS FR-08 and explicit user direction.
3. **Targeted Backend Gaps Resolved:**
   - **Amenity Metadata Catalog (`BACKEND_GAP_FIXED`):** Created `AmenityController` (`GET /api/v1/admin/amenities` as the single canonical endpoint) returning `AmenityResponse` records (`id`, `code`, `name`) to populate the Room creation and editing amenity checklist in `AddEditRoomModal.tsx`.
   - **Tenant Table Monthly Rent (`DTO_MISMATCH_RESOLVED`):** Extended `TenantSummaryResponse` with `BigDecimal monthlyRent` fetched directly from active contracts, enabling the Manager Tenants table to display the "Giá thuê" column following approved contract pricing rules.
   - **Room Drawer Occupant Identity (`DTO_MISMATCH_RESOLVED`):** Extended `OccupantSummaryResponse` with `String idCardNumber`, enabling the Room Detail Drawer's bed occupant card to render `CCCD: {slot.tenant.cccd}` without requiring extra round-trips. Verified accessible exclusively via Manager-authorized APIs.
4. **Verification Integrity:** All 338 Maven unit, integration, security, and repository tests pass cleanly. `git diff --check HEAD` confirms zero whitespace errors and zero conflict markers.

---

## 2. Local Repositories & Working Tree Baseline

Both local repositories were inspected in place without remote fetching or branch manipulation:

### 2.1. Backend Repository (Writable)
- **Local Path:** `/home/chinh/Projects/dormitory-management`
- **Active Branch:** `refactor/erd-and-implemented-code`
- **Baseline HEAD Commit:** `ac525f28bebda83efa03007252bdfb1c7cf4a88f` (`refactor(db): align PostgreSQL schema and JPA with ERD v3.6`)
- **Working Tree State:** Staged for commit (contains Phase 2 implementation baseline + targeted prototype compatibility fixes + removed vehicle edit endpoints + test coverage).
- **Compilation & Tests:** Clean Java compilation (`javac release 25`), 338 tests passing.

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
- `DIRECT_MATCH` (34 entries): Backend API route, request/response payload, and status code directly satisfy the UI requirement.
- `DTO_MISMATCH_RESOLVED` (2 entries): Response DTO extended in Backend (`TenantSummaryResponse.monthlyRent`, `OccupantSummaryResponse.idCardNumber`).
- `BACKEND_GAP_FIXED` (1 entry): Canonical endpoint implemented (`GET /api/v1/admin/amenities`).
- `FE_ADAPTER_REQUIRED` (4 distinct workflow integrations): Frontend adapter formatting/mapping (explicitly detailed in Section 6).
- `REMOVED_PER_USER_DECISION` (1 entry): Pending vehicle registration editing (`PUT /api/v1/admin/vehicles/{id}`) removed per SRS FR-08.
- `OUT_OF_SCOPE` (1 entry): Profile avatar upload (omitted in Phase 2 SRS v3.4.1 and ERD v3.6).
- `DEFERRED_PER_USER_DECISION` (9 review findings): F-01 through F-09 security/edge-case review findings preserved as deferred.

| Screen / Component | User Action or Data Requirement | SRS FR | Backend Endpoint | Implemented Contract & Behavior | Classification | Action Taken / Status |
|---|---|---|---|---|---|---|
| **LoginPage** | Authenticate user with Identifier & Password | FR-21 | `POST /api/v1/auth/login` | `LoginRequest` (`identifier`, `password`) → `LoginResponse` (`accessToken`, `refreshToken`, `user`) | `DIRECT_MATCH` | Matches directly. Backend accepts email, phone, or student ID in `identifier`. |
| **AuthContext** | Silent token refresh on 401 | FR-21 | `POST /api/v1/auth/refresh-token` | `RefreshTokenRequest` (`refreshToken`) → `TokenRefreshResponse` (`accessToken`, `refreshToken`) | `DIRECT_MATCH` | Seamless JWT token rotation. |
| **AuthContext / Navbar** | Single device logout | FR-21 | `POST /api/v1/auth/logout` | `LogoutRequest` (`refreshToken`) → `SuccessResponse` | `DIRECT_MATCH` | Revokes current session in Redis. |
| **AuthContext / Profile** | Global logout (all devices) | FR-21 | `POST /api/v1/auth/logout-all` | No body required (Authenticated bearer token) → `SuccessResponse` | `DIRECT_MATCH` | Bumps user token generation, instantly terminating all active sessions. |
| **PasswordPage (Step 1)** | Request OTP for password reset | FR-24 | `POST /api/v1/auth/forgot-password` | `PasswordRecoveryRequest` (`identifier`) → `PasswordRecoveryRequestedResponse` (`identifier`, `maskedDestination`) | `FE_ADAPTER_REQUIRED` | Adapter Requirement 1: Passes email as `identifier`. |
| **PasswordPage (Step 2)** | Verify OTP code | FR-24 | `POST /api/v1/auth/verify-forgot-password-otp` | `VerifyRecoveryOtpRequest` (`identifier`, `otp`) → `OneTimePasswordResponse` (`recoveryToken`) | `FE_ADAPTER_REQUIRED` | Adapter Requirement 1: Receives opaque `recoveryToken` to pass to step 3. |
| **PasswordPage (Step 3)** | Reset password with token | FR-24 | `POST /api/v1/auth/reset-password` | `ResetPasswordRequest` (`recoveryToken`, `newPassword`, `confirmPassword`) → `SuccessResponse` | `DIRECT_MATCH` | Matches directly. Backend validates token and updates hash. |
| **ChangePasswordModal** | Change password while logged in | FR-24 | `POST /api/v1/auth/change-password` | `ChangePasswordRequest` (`currentPassword`, `newPassword`, `confirmPassword`) → `SuccessResponse` | `DIRECT_MATCH` | Matches directly. |
| **Navbar / Profile** | Fetch current user identity | FR-21 | `GET /api/v1/users/me` | Bearer token → `UserProfileResponse` (`id`, `username`, `email`, `role`, `status`) | `DIRECT_MATCH` | Matches directly. |
| **TechnicianAccountsPage** | List & search technician accounts | FR-22 | `GET /api/v1/users/technicians` | Query params: `page`, `size`, `search`, `status` → `PageResponse<TechnicianResponse>` | `DIRECT_MATCH` | Supports pagination, full-text search across name/email/phone, and status filtering. |
| **TechnicianAccountsPage** | Create new technician account | FR-22 | `POST /api/v1/users/technicians` | `CreateTechnicianRequest` (`email`, `fullName`, `phone`) → `CreateTechnicianResponse` (`id`, `temporaryPassword`, ...) | `FE_ADAPTER_REQUIRED` | Adapter Requirement 2: Extracts `temporaryPassword` to display in initial password modal. |
| **TechnicianAccountsPage** | Lock or Unlock technician | FR-22 | `PATCH /api/v1/users/technicians/{id}/status` | `UpdateUserStatusRequest` (`status`: `ACTIVE` / `LOCKED`) → `TechnicianResponse` | `DIRECT_MATCH` | Locking instantly invalidates all Redis sessions for the staff user. |
| **TechnicianAccountsPage** | Reset technician temporary password | FR-22 | `POST /api/v1/users/technicians/{id}/reset-password` | No body required → `OneTimePasswordResponse` (`temporaryPassword`) | `DIRECT_MATCH` | Generates new temporary password, bumps token generation. |
| **RoomMatrixView** | Render visual floor & room grid | FR-09 | `GET /api/v1/admin/rooms/matrix` | No params → `List<RoomMatrixFloorGroupResponse>` | `DIRECT_MATCH` | Returns structured hierarchy of floors, rooms, and bed statuses. |
| **RoomTableView** | List, search, and filter rooms | FR-09 | `GET /api/v1/admin/rooms` | Query params: `page`, `size`, `building`, `floor`, `genderPolicy`, `status`, `search` → `PageResponse<RoomSummaryResponse>` | `DIRECT_MATCH` | Returns paginated list with total beds, occupied beds, and price. |
| **RoomDetailDrawer** | Inspect room details & bed occupants | FR-09, FR-10 | `GET /api/v1/admin/rooms/{id}` | Path param `id` → `RoomDetailResponse` (includes `beds` list with `occupant` summary) | `DTO_MISMATCH_RESOLVED` | Extended `OccupantSummaryResponse` with `idCardNumber` so CCCD renders directly. |
| **RoomDetailDrawer** | Toggle bed status (Available ↔ Maintenance) | FR-10 | `PATCH /api/v1/admin/beds/{id}/status` | `UpdateBedStatusRequest` (`status`: `AVAILABLE` / `MAINTENANCE`) → `BedDetailResponse` | `DIRECT_MATCH` | Enforces state machine: prevents changing occupied beds to maintenance. |
| **AddEditRoomModal** | Populate amenity checklist | FR-09 | `GET /api/v1/admin/amenities` | No params → `List<AmenityResponse>` (`id`, `code`, `name`) | `BACKEND_GAP_FIXED` | Canonical endpoint returning all system amenities. |
| **AddEditRoomModal** | Create new room | FR-09 | `POST /api/v1/admin/rooms` | `CreateRoomRequest` (`roomNumber`, `genderPolicy`, `basePrice`, `floor`, `amenityIds`) → `RoomDetailResponse` | `DIRECT_MATCH` | Automatically provisions 4 standard beds (`BED-01` to `BED-04`). |
| **AddEditRoomModal** | Update existing room | FR-09 | `PUT /api/v1/admin/rooms/{id}` | `UpdateRoomRequest` (`roomNumber`, `genderPolicy`, `basePrice`, `amenityIds`) → `RoomDetailResponse` | `DIRECT_MATCH` | Blocks gender/number changes if occupied or active contracts exist. |
| **DeleteRoomModal** | Soft-archive empty room | FR-09 | `DELETE /api/v1/admin/rooms/{id}` | Path param `id` → `SuccessResponse` | `DIRECT_MATCH` | Verifies zero active occupants, sets room and beds to `ARCHIVED`. |
| **DeleteRoomModal** | Hard-delete never-used room | FR-09 | `DELETE /api/v1/admin/rooms/{id}/permanent` | Path param `id` → `SuccessResponse` | `DIRECT_MATCH` | Checks `RoomHistoryGuard`; deletes if never referenced in historical contracts or tickets. |
| **RoomsPage** | List archived rooms for restore | FR-09 | `GET /api/v1/admin/rooms/archived` | Query params: `page`, `size`, `search` → `PageResponse<ArchivedRoomSummaryResponse>` | `DIRECT_MATCH` | Lists archived rooms with reason and timestamp. |
| **RoomsPage** | Restore archived room | FR-09 | `POST /api/v1/admin/rooms/{id}/restore` | Path param `id` → `RoomDetailResponse` | `DIRECT_MATCH` | Restores room and beds to `AVAILABLE`. |
| **TenantsPage** | List, search, filter tenants | FR-06 | `GET /api/v1/admin/tenants` | Query params: `page`, `size`, `search`, `status` → `PageResponse<TenantSummaryResponse>` | `DTO_MISMATCH_RESOLVED` | Extended `TenantSummaryResponse` with `monthlyRent` from active contract for the "Giá thuê" column. |
| **TenantsPage (Drawer)** | Inspect full tenant profile | FR-06 | `GET /api/v1/admin/tenants/{id}` | Path param `id` → `TenantDetailResponse` | `DIRECT_MATCH` | Returns full identity, contacts, active contract, and registered vehicles. |
| **TenantsPage (Edit Modal)** | Manager updates tenant identity | FR-06 | `PUT /api/v1/admin/tenants/{id}` | `ManagerUpdateTenantRequest` (`fullName`, `gender`, `dateOfBirth`, `idCardNumber`, `phone`, `permanentAddress`, ...) → `TenantDetailResponse` | `DIRECT_MATCH` | Enforces active contract locks: identity locked if signed contract exists. |
| **TenantsPage (Reset Password)** | Manager resets tenant password | FR-06 | `POST /api/v1/admin/tenants/{id}/reset-password` | Path param `id` → `OneTimePasswordResponse` (`temporaryPassword`) | `DIRECT_MATCH` | Generates temporary password, invalidates all tenant sessions. |
| **TenantPortalPage** | Tenant updates contact details | FR-07 | `PATCH /api/v1/tenant/profile` | `TenantContactUpdateRequest` (`phone`, `permanentAddress`, `emergencyContactName`, `emergencyContactPhone`) → `UserProfileResponse` | `DIRECT_MATCH` | Tenants can safely self-update contact info without affecting legal identity. |
| **TenantPortalPage (Avatar)** | Tenant uploads profile avatar | — | — | Prototype UI has an avatar upload button, but schema and SRS v3.4.1 exclude avatars in Phase 2. | `OUT_OF_SCOPE` | Documented discrepancy; UI falls back gracefully to default initials. |
| **VehiclesPage** | List & filter dormitory vehicles | FR-08 | `GET /api/v1/admin/vehicles` | Query params: `page`, `size`, `search`, `status` → `PageResponse<VehicleResponse>` | `DIRECT_MATCH` | Returns paginated vehicles with tenant and room details. |
| **VehiclesPage** | Inspect vehicle details | FR-08 | `GET /api/v1/admin/vehicles/{id}` | Path param `id` → `VehicleResponse` | `DIRECT_MATCH` | Matches directly. |
| **VehiclesPage** | Issue parking card | FR-08 | `PATCH /api/v1/admin/vehicles/{id}/issue-card` | `IssueCardRequest` (`cardCode`) → `VehicleResponse` (`status: ISSUED`) | `DIRECT_MATCH` | Enforces unique active card code across all dormitory vehicles. |
| **VehiclesPage** | Reject pending registration | FR-08 | `POST /api/v1/admin/vehicles/{id}/reject` | `RejectVehicleRequest` (`rejectionReason`) → `VehicleResponse` (`status: REJECTED`) | `DIRECT_MATCH` | Updates status and records rejection reason. |
| **VehiclesPage** | Revoke parking card | FR-08 | `PATCH /api/v1/admin/vehicles/{id}/revoke-card` | Path param `id` → `VehicleResponse` (`status: REVOKED`) | `DIRECT_MATCH` | Releases card code for future assignment. |
| **VehiclesPage / Drawer** | View front / back registration evidence | FR-08 | `GET /api/v1/admin/vehicles/{id}/evidence/{side}` | Path params `id`, `side` (`front` / `back`) → Binary image stream | `DIRECT_MATCH` | Secure stream from private storage (`image/jpeg`, `image/png`, `image/webp`). |
| **VehiclesPage (Edit)** | Edit pending vehicle registration | FR-08 | `PUT /api/v1/admin/vehicles/{id}` | Removed per explicit user decision and SRS FR-08 alignment. | `REMOVED_PER_USER_DECISION` | Successfully deleted endpoint, DTO, service methods, and tests. |
| **TenantVehiclesPage** | View tenant's active & historical vehicles | FR-08 | `GET /api/v1/tenant/vehicles` | Bearer token → `List<VehicleResponse>` | `DIRECT_MATCH` | Returns full vehicle history for the authenticated tenant. |
| **TenantVehiclesPage** | Register new vehicle | FR-08 | `POST /api/v1/tenant/vehicles` | Multipart: `TenantRegisterVehicleRequest` + `frontImage`, `backImage` → `VehicleResponse` (`PENDING_CARD`) | `FE_ADAPTER_REQUIRED` | Adapter Requirement 3: Constructs `FormData` with text fields and file attachments. |
| **TenantVehiclesPage** | View change request history | FR-08 | `GET /api/v1/tenant/vehicle-change-requests` | Bearer token → `List<VehicleChangeRequestResponse>` | `DIRECT_MATCH` | Matches directly. |
| **TenantVehiclesPage** | Submit vehicle change request | FR-08 | `POST /api/v1/tenant/vehicle-change-requests` | Multipart: `CreateVehicleChangeRequest` + `frontImage`, `backImage` → `VehicleChangeRequestResponse` | `FE_ADAPTER_REQUIRED` | Adapter Requirement 4: Constructs `FormData` with proposed fields and file attachments. |
| **VehiclesPage (Admin)** | List & review vehicle change requests | FR-08 | `GET /api/v1/admin/vehicle-change-requests` | Query params: `page`, `size`, `status` → `PageResponse<VehicleChangeRequestResponse>` | `DIRECT_MATCH` | Matches directly. |
| **VehiclesPage (Admin)** | Approve vehicle change request | FR-08 | `POST /api/v1/admin/vehicle-change-requests/{id}/approve` | Path param `id` → `VehicleChangeRequestResponse` (`APPROVED`) | `DIRECT_MATCH` | Atomically transitions old vehicle to `REVOKED` and creates new vehicle in `PENDING_CARD`. |
| **VehiclesPage (Admin)** | Reject vehicle change request | FR-08 | `POST /api/v1/admin/vehicle-change-requests/{id}/reject` | `RejectVehicleChangeRequest` (`rejectionReason`) → `VehicleChangeRequestResponse` (`REJECTED`) | `DIRECT_MATCH` | Preserves existing vehicle in `ISSUED` state. |

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
  - `POST /api/v1/auth/forgot-password` takes `{ "identifier": email }` and returns `{ "identifier": "...", "maskedDestination": "..." }`.
  - `POST /api/v1/auth/verify-forgot-password-otp` takes `{ "identifier": email, "otp": code }` and returns `{ "recoveryToken": "raw-uuid" }`.
  - `POST /api/v1/auth/reset-password` takes `{ "recoveryToken": "raw-uuid", "newPassword": "...", "confirmPassword": "..." }`.
- **Adapter Work:** The frontend `authAdapter.ts` maps `email` to `identifier` on Step 1, passes `identifier` and `otp` on Step 2, and retains the returned opaque `recoveryToken` in memory/state to submit with `newPassword` on Step 3.

### Interaction 2: Technician Initial Temporary Password Presentation (FR-22)
- **Backend Endpoint:** `POST /api/v1/users/technicians` returns HTTP 201 with `CreateTechnicianResponse` containing `{ id, email, fullName, phone, temporaryPassword }`.
- **Adapter Work:** The frontend adapter extracts `temporaryPassword` from the response payload and supplies it to `TechnicianAccountsPage` to trigger the display modal for the manager.

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

## 9. Deferred Security & Edge-Case Findings

As instructed by the user, the core Authentication and security implementation was frozen during this compatibility stage. The following independent review findings are recorded as **deferred** for a future security-focused iteration:

- **F-01 (High):** Password Recovery OTP Concurrency & Rate-Limiting window.
- **F-02 (Medium):** Token payload caching & Redis key expiration synchronicity.
- **F-03 (Medium):** Refresh token rotation race condition under high parallel request volume.
- **F-04 (Low):** Password policy regex complexity consistency across endpoints.
- **F-05–F-09 (Low):** Miscellaneous defensive edge cases in file cleanup and pagination boundaries.

---

## 10. Verification & Quality Assurance Evidence

The final Backend implementation was subjected to comprehensive verification:

```bash
# 1. Compilation Verification
./mvnw -DskipTests compile
-> Result: BUILD SUCCESS (203 source files compiled, zero errors)

# 2. Complete Test Suite Execution
./mvnw test
-> Result: Tests run: 338, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS.

# 3. Git Diff Formatting & Conflict Check
git diff --check HEAD
-> Result: Clean (zero whitespace errors, zero merge conflict markers)

# 4. Git Working Tree Integrity
git status --short
-> Result: Working tree contains only expected Phase 2 files and targeted fixes.
```

---

## 11. Final Readiness Verdict

**Verdict:** `READY_FOR_BACKEND_COMPATIBILITY_REVIEW`

The Backend API suite completely satisfies the functional and data contract requirements of the Frontend Prototype for Phase 2. All confirmed backend gaps are fixed, obsolete vehicle editing code is removed, 338/338 tests pass, and local repository integrity is preserved.
