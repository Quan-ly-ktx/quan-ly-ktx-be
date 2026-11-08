# Tenant, Vehicle, Contract & Onboarding Subsystem Implementation Plan
## Tickets: `ktx-33`, `ktx-49` | Requirements: `FR-06` -> `FR-13`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai hoàn chỉnh hai phân hệ Backend trọng tâm: 4.2 (Hồ sơ Khách thuê & Phương tiện di chuyển - FR-06 đến FR-08) và 4.3 (Tiếp nhận, Xếp phòng & Hợp đồng - FR-09 đến FR-13), bao gồm Onboarding 1 bước trọn gói, quản lý phương tiện hầm KTX (hạn mức 1 xe, đối soát cà vẹt và cấp/thu hồi thẻ từ vật lý), quản lý hợp đồng kèm lưu trữ minh chứng đa tệp (OCI/Local Storage), gia hạn hợp đồng và thanh lý trả phòng (Checkout 4-in-1 Transaction đồng bộ).

**Architecture:** Triển khai theo mô hình phân tầng Controller - Service - Repository chuẩn Spring Boot 4.x / Java 25. Các Entity (`Tenant`, `Vehicle`, `Contract`, `ContractDocument`) là JPA POJO thuần túy; toàn bộ quy tắc nghiệp vụ (cách ly giới tính 100% BR-02, hạn mức xe BR-10, Onboarding ACID, Checkout 4-in-1 cascade, tái cấp phát thẻ vật lý qua Partial Unique Index) được điều phối tại tầng Service và bảo toàn tính toàn vẹn dữ liệu qua `@Transactional`. Toàn bộ API trả về chuẩn envelope `SingleResponse<T>`, `SuccessResponse`, hoặc `PageResponse<T>` qua `ResponseFactory` kế thừa `AbstractBaseController`.

**Tech Stack:** Java 25, Spring Boot 4.x, Spring Data JPA, Hibernate 7, PostgreSQL 17, Flyway, Spring Security & JWT, Bean Validation (Jakarta), JUnit 5, Mockito, MockMvc, AssertJ.

**Spec:** `docs/superpowers/specs/2026-11-04-tenant-vehicle-contract-onboarding-design.md`

---

## Global Constraints

- **Platform & Runtime:** Java 25, Spring Boot 4.x, Maven Wrapper (`./mvnw`).
- **Phân quyền truy cập (RBAC):**
  - Các endpoint `/api/v1/admin/**` yêu cầu vai trò `hasRole('MANAGER')`.
  - Các endpoint `/api/v1/tenant/**` yêu cầu vai trò `hasRole('TENANT')` và chỉ truy cập dữ liệu thuộc sở hữu của chính mình (dựa vào `UserPrincipal.getId()`).
- **Response Format:** Kế thừa `AbstractBaseController`, sử dụng `ResponseFactory` cho mọi phản hồi (`SingleResponse<T>`, `PageResponse<T>`, `SuccessResponse`).
- **Xử lý ngoại lệ chuẩn hóa:** Kế thừa `BaseAppException` với các bộ mã lỗi nghiệp vụ: `TenantErrorCode`, `ContractErrorCode`, `VehicleErrorCode`.
- **Toàn vẹn dữ liệu & Soft Delete:** Nghiêm cấm dùng `DELETE FROM` cho các thực thể kế toán/pháp lý. Khi Checkout: `Contract` -> `TERMINATED`, `Bed` -> `AVAILABLE`, `Tenant` -> `CHECKED_OUT` (giữ `Tenant.deletedAt = NULL` để không bị `@SQLRestriction` chặn truy vấn lịch sử), `Vehicle` -> `REVOKED`.
- **Kiểm soát chất lượng:** Tuân thủ định dạng mã nguồn Spotless (`./mvnw spotless:check`) và độ phủ kiểm thử tự động đạt tối thiểu 80%.

---

## Review Focus

1. **Cách ly giới tính 100% khi Onboarding (BR-02):** Gửi `POST /api/v1/admin/onboarding` với khách `MALE` vào phòng `FEMALE` (hoặc ngược lại) bắt buộc bị chặn đứng ở Service layer, ném HTTP 400 `CONTRACT_400_GENDER_MISMATCH`, không tạo bất kỳ bản ghi nào trong DB.
2. **Hạn mức 1 phương tiện/khách thuê (BR-10):** Gửi đăng ký phương tiện mới khi khách đã có 01 xe ở trạng thái `ACTIVE` hoặc `PENDING` bắt buộc bị từ chối, ném HTTP 400 `VEHICLE_400_QUOTA_EXCEEDED`.
3. **Tái cấp phát mã thẻ từ vật lý sau khi thu hồi:** Thu hồi thẻ xe của khách A (`REVOKED`), sau đó cấp phát chính mã thẻ đó (`cardCode`) cho xe của khách B phải thành công trơn tru nhờ Partial Unique Index (không bị lỗi trùng khóa PostgreSQL).
4. **Checkout 4-in-1 Transaction Atomicity (BR-04, FR-13):** Khi gọi `POST /api/v1/admin/contracts/{id}/terminate`, nếu bất kỳ bước nào trong 4 thao tác gặp sự cố, toàn bộ giao dịch phải rollback; khi thành công, cả 4 thực thể (Contract, Bed, Tenant, Vehicle) phải đổi trạng thái đồng bộ trong cùng 1 thời điểm.
5. **Tiếp nhận lại khách cũ từng ở (Re-onboarding):** Khách có CCCD đã tồn tại trong DB nhưng ở trạng thái `CHECKED_OUT` khi được Onboard lại phải được kích hoạt lại hồ sơ thành công (`ACTIVE`), liên kết với tài khoản `User` cũ và tạo hợp đồng mới, không ném lỗi trùng CCCD.

---

### Task 1: Database Migration V7 & Entity Refinements

**Files:**
- Create: `src/main/resources/db/migration/V7__refine_vehicles_card_code_and_indexes.sql`
- Modify: `src/main/java/com/uit/dormmanagement/entities/Vehicle.java`
- Modify: `src/main/java/com/uit/dormmanagement/entities/Contract.java`
- Modify: `src/main/java/com/uit/dormmanagement/entities/Tenant.java`
- Test: `src/test/java/com/uit/dormmanagement/repositories/V7SchemaMigrationTest.java`

**Interfaces:**
- Consumes: CSDL PostgreSQL 17, Flyway migration files V1..V6.
- Produces:
  - Bỏ constraint cứng `vehicles_card_code_key` trên toàn bảng.
  - Tạo partial unique index: `uq_vehicles_active_card_code ON vehicles (card_code) WHERE status = 'ACTIVE' AND deleted_at IS NULL`.
  - Tạo partial unique index: `uq_vehicles_active_license_plate ON vehicles (license_plate) WHERE status != 'REVOKED' AND deleted_at IS NULL`.
  - Đánh index bổ trợ cho `contracts(tenant_id, status)` và `tenants(resident_status, deleted_at)`.

- [ ] **Step 1: Write the failing migration test**

Viết test kiểm tra migration V7 áp dụng thành công và ràng buộc partial unique index hoạt động: cho phép chèn 2 bản ghi xe cùng `card_code` nếu bản ghi đầu tiên có `status = 'REVOKED'`.

```java
@Test
void cardCode_whenRevoked_allowsReissuingToAnotherVehicle() {
  // Insert revoked vehicle with card_code 'THE-001'
  // Insert active vehicle with same card_code 'THE-001' -> should succeed
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=V7SchemaMigrationTest`
Expected: FAIL (V7 migration script not found / unique constraint violation).

- [ ] **Step 3: Create migration script V7 and adjust entities**

Tạo `src/main/resources/db/migration/V7__refine_vehicles_card_code_and_indexes.sql`:
```sql
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_card_code_key;
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_license_plate_key;

CREATE UNIQUE INDEX uq_vehicles_active_card_code
    ON vehicles (card_code)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_vehicles_active_license_plate
    ON vehicles (license_plate)
    WHERE status != 'REVOKED' AND deleted_at IS NULL;

CREATE INDEX idx_contracts_tenant_status ON contracts (tenant_id, status);
CREATE INDEX idx_tenants_resident_status ON tenants (resident_status, deleted_at);
```
Cập nhật annotations trên `Vehicle.java` (bỏ `unique = true` ở `cardCode` và `licensePlate` trong JPA entity vì đã chuyển sang Partial Index CSDL).

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=V7SchemaMigrationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V7* src/main/java/com/uit/dormmanagement/entities/ src/test/java/com/uit/dormmanagement/repositories/V7SchemaMigrationTest.java
git commit -m "feat(db): add V7 migration for partial unique card_code index and performance indexes"
```

---

### Task 2: Storage Service Architecture (Local Fallback & OCI Abstraction)

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/StorageService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/LocalStorageServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/OciObjectStorageServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/configs/StorageConfig.java`
- Create: `src/main/java/com/uit/dormmanagement/configs/WebMvcResourceConfig.java`
- Test: `src/test/java/com/uit/dormmanagement/services/LocalStorageServiceTest.java`

**Interfaces:**
- Consumes: `MultipartFile`, `application.yml` properties (`oci.storage.*`).
- Produces:
  - `StorageService.uploadFile(MultipartFile file, String folder)` -> returns String (URL or public path).
  - `StorageService.deleteFile(String fileUrl)` -> void.
  - Resource Handler ánh xạ `/uploads/**` tới thư mục cục bộ khi chạy dev/test.

- [ ] **Step 1: Write the failing storage service test**

Viết unit test cho `LocalStorageServiceImpl`: upload một file mẫu (`MockMultipartFile`), kiểm tra file được lưu vào thư mục và trả về đường dẫn URL hợp lệ; kiểm tra xóa file thành công.

```java
@Test
void uploadFile_shouldSaveFileAndReturnUrl() {
  MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
  String url = storageService.uploadFile(file, "contracts");
  assertThat(url).contains("/uploads/contracts/");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=LocalStorageServiceTest`
Expected: FAIL (StorageService classes not found).

- [ ] **Step 3: Implement StorageService and Local/OCI implementations**

1. Tạo interface `StorageService`.
2. Tạo `LocalStorageServiceImpl` với thư mục gốc `uploads/`.
3. Tạo `OciObjectStorageServiceImpl` cấu trúc khung kết nối OCI (sử dụng credentials từ config, nếu `enabled=false` thì không khởi tạo).
4. Tạo `StorageConfig` với `@ConditionalOnProperty` nạp `OciObjectStorageServiceImpl` khi `oci.storage.enabled=true`, ngược lại nạp `LocalStorageServiceImpl`.
5. Tạo `WebMvcResourceConfig` cấu hình `ResourceHandlerRegistry` ánh xạ `/uploads/**` vào `file:uploads/`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=LocalStorageServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/services/StorageService.java src/main/java/com/uit/dormmanagement/services/impl/*Storage* src/main/java/com/uit/dormmanagement/configs/ src/test/java/com/uit/dormmanagement/services/LocalStorageServiceTest.java
git commit -m "feat(storage): implement StorageService with LocalStorageService fallback and OCI abstraction"
```

---

### Task 3: Domain Error Codes for Tenant, Contract, and Vehicle

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/exceptions/error/TenantErrorCode.java`
- Create: `src/main/java/com/uit/dormmanagement/exceptions/error/ContractErrorCode.java`
- Create: `src/main/java/com/uit/dormmanagement/exceptions/error/VehicleErrorCode.java`
- Test: `src/test/java/com/uit/dormmanagement/exceptions/DomainErrorCodeTest.java`

**Interfaces:**
- Consumes: `AppErrorCode` interface, `HttpStatus`.
- Produces:
  - `TenantErrorCode`: `TENANT_NOT_FOUND (404)`, `ID_CARD_NUMBER_DUPLICATED (409)`, `EMAIL_DUPLICATED (409)`, `PHONE_DUPLICATED (409)`, `TENANT_NOT_ACTIVE (400)`.
  - `ContractErrorCode`: `CONTRACT_NOT_FOUND (404)`, `CONTRACT_ALREADY_TERMINATED (400)`, `BED_NOT_AVAILABLE (400)`, `GENDER_MISMATCH (400)`, `INVALID_CONTRACT_DATES (400)`, `DOCUMENT_NOT_FOUND (404)`, `DOCUMENT_FILE_SIZE_EXCEEDED (400)`, `UNSUPPORTED_DOCUMENT_FORMAT (400)`.
  - `VehicleErrorCode`: `VEHICLE_NOT_FOUND (404)`, `LICENSE_PLATE_DUPLICATED (409)`, `CARD_CODE_DUPLICATED (409)`, `VEHICLE_QUOTA_EXCEEDED (400)`, `VEHICLE_ALREADY_ACTIVE (400)`, `VEHICLE_NOT_PENDING (400)`.

- [ ] **Step 1: Write the failing error code test**

```java
@Test
void errorCodes_shouldHaveProperHttpStatusAndCodePrefix() {
  assertThat(TenantErrorCode.TENANT_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
  assertThat(ContractErrorCode.GENDER_MISMATCH.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=DomainErrorCodeTest`
Expected: FAIL (Enum classes not found).

- [ ] **Step 3: Implement ErrorCode Enums**

Tạo `TenantErrorCode.java`, `ContractErrorCode.java`, `VehicleErrorCode.java` hiện thực `AppErrorCode`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=DomainErrorCodeTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/exceptions/error/ src/test/java/com/uit/dormmanagement/exceptions/DomainErrorCodeTest.java
git commit -m "feat(exception): define domain error codes for tenant, contract, and vehicle modules"
```

---

### Task 4: Data Transfer Objects (DTOs) with Bean Validation

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/onboarding/OnboardingRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/onboarding/OnboardingResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/onboarding/GeneratedAccountInfo.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/tenant/UpdateTenantRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/tenant/TenantSummaryResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/tenant/TenantDetailResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/CreateVehicleAdminRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/UpdateVehicleRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/IssueCardRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/vehicle/TenantRegisterVehicleRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/vehicle/VehicleResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/contract/RenewContractRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/contract/ContractSummaryResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/contract/ContractDetailResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/contract/ContractDocumentResponse.java`
- Test: `src/test/java/com/uit/dormmanagement/dtos/TenantContractVehicleDtoValidationTest.java`

**Interfaces:**
- Consumes: Jakarta Validation (`@NotBlank`, `@NotNull`, `@Pattern`, `@DecimalMin`).
- Produces: Toàn bộ Request và Response DTOs chuẩn hóa cho các phân hệ 4.2 và 4.3.

- [ ] **Step 1: Write the failing DTO validation test**

Viết test kiểm tra tính hợp lệ của `OnboardingRequest` (CCCD 12 số regex `^[0-9]{12}$`, SĐT 10 số `^0[0-9]{9}$`, Email valid, `depositAmount >= 0`), `IssueCardRequest` (`cardCode` not blank), `RenewContractRequest` (`newEndDate` not null).

```java
@Test
void onboardingRequest_whenInvalidCccd_shouldFailValidation() {
  OnboardingRequest req = OnboardingRequest.builder().idCardNumber("123").build();
  Set<ConstraintViolation<OnboardingRequest>> violations = validator.validate(req);
  assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("idCardNumber"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TenantContractVehicleDtoValidationTest`
Expected: FAIL (DTO classes not found).

- [ ] **Step 3: Implement all DTO classes**

Tạo các class DTOs trong packages:
- `com.uit.dormmanagement.dtos.request.onboarding` & `.response.onboarding`
- `com.uit.dormmanagement.dtos.request.tenant` & `.response.tenant`
- `com.uit.dormmanagement.dtos.request.vehicle` & `.response.vehicle`
- `com.uit.dormmanagement.dtos.request.contract` & `.response.contract`

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=TenantContractVehicleDtoValidationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/dtos/ src/test/java/com/uit/dormmanagement/dtos/TenantContractVehicleDtoValidationTest.java
git commit -m "feat(dto): create request and response DTOs with validation constraints for modules 4.2 and 4.3"
```

---

### Task 5: Repositories & Advanced Queries

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/repositories/TenantRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/VehicleRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/ContractRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/ContractDocumentRepository.java`
- Test: `src/test/java/com/uit/dormmanagement/repositories/TenantContractVehicleRepositoryTest.java`

**Interfaces:**
- Consumes: JPA entities, Spring Data specifications/queries.
- Produces:
  - `TenantRepository`: `findByIdCardNumber(String)`, `findByEmail(String)`, `findActiveTenantsWithFilters(String search, UUID roomId, ResidentStatus status, Pageable)`, `findNextTenantCodeSequence()`.
  - `VehicleRepository`: `findVehiclesWithFilters(String search, VehicleStatus status, Pageable)`, `countByTenantIdAndStatusInAndDeletedAtIsNull(UUID, Collection<VehicleStatus>)`, `findByTenantIdAndDeletedAtIsNull(UUID)`.
  - `ContractRepository`: `findContractsWithFilters(String search, ContractStatus status, Pageable)`, `findNextContractCodeSequence(int year)`, `findAllByStatusAndEndDateLessThanEqual(ContractStatus, LocalDate)`.
  - `ContractDocumentRepository`: `findByContractIdOrderByUploadedAtDesc(UUID)`.

- [ ] **Step 1: Write the failing repository test**

```java
@Test
void countActiveVehiclesByTenant_shouldReturnCorrectCount() {
  long count = vehicleRepository.countByTenantIdAndStatusInAndDeletedAtIsNull(
      tenant.getId(), List.of(VehicleStatus.PENDING, VehicleStatus.ACTIVE));
  assertThat(count).isEqualTo(1);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TenantContractVehicleRepositoryTest`
Expected: FAIL (Query methods do not exist).

- [ ] **Step 3: Implement repository query methods**

Thêm các methods và JPQL / Specifications trong `TenantRepository`, `VehicleRepository`, `ContractRepository`, `ContractDocumentRepository`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=TenantContractVehicleRepositoryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/repositories/ src/test/java/com/uit/dormmanagement/repositories/TenantContractVehicleRepositoryTest.java
git commit -m "feat(repo): add query methods and filters for tenant, vehicle, and contract repositories"
```

---

### Task 6: Direct Onboarding Service & Controller (FR-09, BR-01, BR-02, BR-09)

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/OnboardingService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/OnboardingServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/controllers/OnboardingController.java`
- Test: `src/test/java/com/uit/dormmanagement/services/OnboardingServiceImplTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/OnboardingControllerTest.java`

**Interfaces:**
- Consumes: `OnboardingRequest`, `TenantRepository`, `UserRepository`, `BedRepository`, `ContractRepository`, `PasswordEncoder`.
- Produces:
  - `OnboardingService.onboardTenant(OnboardingRequest)` -> `OnboardingResponse`.
  - Endpoint `POST /api/v1/admin/onboarding`.

- [ ] **Step 1: Write the failing service unit test**

Kiểm tra:
1. Thành công Onboard khách mới: tạo Tenant, User role TENANT, Contract ACTIVE, Bed OCCUPIED.
2. Thất bại khi phòng sai giới tính (`CANNOT_ASSIGN_GENDER_MISMATCH`).
3. Thất bại khi giường không ở trạng thái `AVAILABLE` (`BED_NOT_AVAILABLE`).
4. Xử lý Re-onboarding: Khách cũ có `residentStatus == CHECKED_OUT` quay lại -> tái kích hoạt thành công.

```java
@Test
void onboardTenant_whenGenderMismatch_shouldThrowException() {
  when(bedRepository.findById(bedId)).thenReturn(Optional.of(bedInFemaleRoom));
  OnboardingRequest req = OnboardingRequest.builder().gender(GenderPolicy.MALE).bedId(bedId).build();
  assertThatThrownBy(() -> onboardingService.onboardTenant(req))
      .isInstanceOf(BaseAppException.class)
      .hasMessageContaining("GENDER_MISMATCH");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=OnboardingServiceImplTest`
Expected: FAIL (Service class not found).

- [ ] **Step 3: Implement OnboardingService and OnboardingController**

1. Cài đặt `OnboardingServiceImpl` bọc trong `@Transactional`.
2. Kiểm tra tính hợp lệ, thẩm tra giới tính với phòng (`BR-02`), thẩm tra giường trống.
3. Tạo `Tenant`, băm mật khẩu `Password123@` và lưu `User`, tạo `Contract`, đổi `Bed.status = OCCUPIED`, tăng `Room.occupiedBeds`.
4. Tạo `OnboardingController` kế thừa `AbstractBaseController` với `@PreAuthorize("hasRole('MANAGER')")`.

- [ ] **Step 4: Run service and controller tests to verify they pass**

Run: `./mvnw test -Dtest=OnboardingServiceImplTest,OnboardingControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/services/OnboardingService.java src/main/java/com/uit/dormmanagement/services/impl/OnboardingServiceImpl.java src/main/java/com/uit/dormmanagement/controllers/OnboardingController.java src/test/java/com/uit/dormmanagement/*/Onboarding*
git commit -m "feat(onboarding): implement Direct Onboarding 1-step transaction and API endpoint (FR-09)"
```

---

### Task 7: Tenant Profile Management Subsystem (FR-06)

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/TenantService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/controllers/TenantController.java`
- Test: `src/test/java/com/uit/dormmanagement/services/TenantServiceImplTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/TenantControllerTest.java`

**Interfaces:**
- Consumes: `TenantRepository`, `ContractRepository`, `VehicleRepository`, `InvoiceRepository`.
- Produces:
  - `TenantService.getTenants(Pageable, search, roomId, residentStatus)` -> `PageResponse<TenantSummaryResponse>`.
  - `TenantService.getTenantById(UUID)` -> `TenantDetailResponse`.
  - `TenantService.updateTenant(UUID, UpdateTenantRequest)` -> `TenantDetailResponse`.
  - Endpoints: `GET /api/v1/admin/tenants`, `GET /api/v1/admin/tenants/{id}`, `PUT /api/v1/admin/tenants/{id}`.

- [ ] **Step 1: Write the failing tenant service test**

Viết test kiểm tra tìm kiếm, lọc theo phòng, lọc theo `residentStatus`, lấy chi tiết người thuê kèm giường/hợp đồng hiện tại, và cập nhật thông tin cá nhân.

```java
@Test
void getTenantById_shouldReturnFullProfileWithCurrentBedAndContract() {
  TenantDetailResponse res = tenantService.getTenantById(tenantId);
  assertThat(res.getFullName()).isEqualTo("Nguyễn Hoàng Nam");
  assertThat(res.getCurrentRoomNumber()).isEqualTo("P.302");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TenantServiceImplTest`
Expected: FAIL (TenantService not found).

- [ ] **Step 3: Implement TenantService and TenantController**

1. Cài đặt `TenantServiceImpl` truy vấn danh sách có phân trang và tra cứu chi tiết kèm tổng hợp công nợ.
2. Cài đặt `updateTenant`: cập nhật họ tên, SĐT, địa chỉ, người liên hệ khẩn cấp.
3. Cài đặt `TenantController` với `@PreAuthorize("hasRole('MANAGER')")`.

- [ ] **Step 4: Run service and controller tests to verify they pass**

Run: `./mvnw test -Dtest=TenantServiceImplTest,TenantControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/services/TenantService.java src/main/java/com/uit/dormmanagement/services/impl/TenantServiceImpl.java src/main/java/com/uit/dormmanagement/controllers/TenantController.java src/test/java/com/uit/dormmanagement/*/Tenant*
git commit -m "feat(tenant): implement tenant list, detail, and profile update endpoints (FR-06)"
```

---

### Task 8: Vehicle Management Subsystem (FR-08, BR-10)

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/VehicleService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/VehicleServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/controllers/AdminVehicleController.java`
- Create: `src/main/java/com/uit/dormmanagement/controllers/TenantVehicleController.java`
- Test: `src/test/java/com/uit/dormmanagement/services/VehicleServiceImplTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/VehicleControllerTest.java`

**Interfaces:**
- Consumes: `VehicleRepository`, `TenantRepository`, `StorageService`.
- Produces:
  - Admin: `getVehicles`, `createVehicleAdmin`, `updateVehicle`, `issueCard`, `revokeCard`, `deleteVehicle`.
  - Tenant: `getMyVehicle`, `registerVehicle(tenantId, request, frontImage, backImage)`.
  - Endpoints:
    - `/api/v1/admin/vehicles` (GET, POST)
    - `/api/v1/admin/vehicles/{id}` (PUT, DELETE)
    - `/api/v1/admin/vehicles/{id}/issue-card` (PATCH)
    - `/api/v1/admin/vehicles/{id}/revoke-card` (PATCH)
    - `/api/v1/tenant/vehicles/me` (GET)
    - `/api/v1/tenant/vehicles` (POST multipart)

- [ ] **Step 1: Write the failing vehicle service test**

Kiểm tra:
1. Đăng ký xe mới thành công khi khách chưa có xe.
2. Chặn đăng ký xe mới khi khách đã có 01 xe đang `PENDING` hoặc `ACTIVE` (`VEHICLE_QUOTA_EXCEEDED`).
3. Cấp thẻ xe: đối soát thành công, gán `cardCode`, chuyển sang `ACTIVE`.
4. Thu hồi thẻ xe: chuyển sang `REVOKED`. Tái cấp phát mã thẻ đó cho xe khác thành công.

```java
@Test
void registerVehicle_whenTenantAlreadyHasActiveVehicle_shouldThrowQuotaExceeded() {
  when(vehicleRepository.countByTenantIdAndStatusInAndDeletedAtIsNull(any(), any())).thenReturn(1L);
  assertThatThrownBy(() -> vehicleService.registerVehicle(tenantId, request, front, back))
      .isInstanceOf(BaseAppException.class)
      .hasMessageContaining("VEHICLE_QUOTA_EXCEEDED");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=VehicleServiceImplTest`
Expected: FAIL (VehicleService not found).

- [ ] **Step 3: Implement VehicleService and Controllers**

1. Cài đặt `VehicleServiceImpl`:
   - `registerVehicle`: Upload 2 ảnh cà vẹt qua `StorageService`, lưu xe với status `PENDING`.
   - `issueCard`: Kiểm tra mã thẻ chưa bị xe `ACTIVE` nào sử dụng, chuyển status sang `ACTIVE`.
   - `revokeCard`: Chuyển status sang `REVOKED`.
2. Cài đặt `AdminVehicleController` (`hasRole('MANAGER')`).
3. Cài đặt `TenantVehicleController` (`hasRole('TENANT')`), tự động trích xuất `tenantId` từ `UserPrincipal`.

- [ ] **Step 4: Run service and controller tests to verify they pass**

Run: `./mvnw test -Dtest=VehicleServiceImplTest,VehicleControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/services/VehicleService.java src/main/java/com/uit/dormmanagement/services/impl/VehicleServiceImpl.java src/main/java/com/uit/dormmanagement/controllers/*Vehicle* src/test/java/com/uit/dormmanagement/*/Vehicle*
git commit -m "feat(vehicle): implement vehicle registration, card issuing, revocation, and quota control (FR-08)"
```

---

### Task 9: Contract Lifecycle, Renewal, Documents & Checkout 4-in-1 (FR-07, FR-10, FR-12, FR-13)

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/ContractService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/ContractServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/schedulers/ContractStatusScheduler.java`
- Create: `src/main/java/com/uit/dormmanagement/controllers/AdminContractController.java`
- Create: `src/main/java/com/uit/dormmanagement/controllers/TenantContractController.java`
- Test: `src/test/java/com/uit/dormmanagement/services/ContractServiceImplTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/ContractControllerTest.java`

**Interfaces:**
- Consumes: `ContractRepository`, `ContractDocumentRepository`, `BedRepository`, `TenantRepository`, `VehicleRepository`, `StorageService`.
- Produces:
  - `ContractService.getContracts`, `getContractById`, `renewContract`, `uploadDocuments`, `deleteDocument`, `terminateContract`.
  - `ContractStatusScheduler`: Tự động quét hợp đồng sắp hết hạn.
  - Endpoints:
    - `/api/v1/admin/contracts` (GET)
    - `/api/v1/admin/contracts/{id}` (GET)
    - `/api/v1/admin/contracts/{id}/renew` (POST)
    - `/api/v1/admin/contracts/{id}/terminate` (POST - Checkout 4-in-1)
    - `/api/v1/admin/contracts/{id}/documents` (POST multipart)
    - `/api/v1/admin/contracts/{id}/documents/{documentId}` (DELETE)
    - `/api/v1/tenant/contracts/me` (GET)

- [ ] **Step 1: Write the failing contract service test**

Kiểm tra:
1. `renewContract`: Cập nhật `endDate` mới, lưu phụ lục `RENEWAL_ANNEX`, đưa status về `ACTIVE`.
2. `terminateContract` (Checkout):
   - Contract -> `TERMINATED`.
   - Bed -> `AVAILABLE` (giảm `occupiedBeds` của phòng).
   - Tenant -> `residentStatus = CHECKED_OUT` (giữ `deletedAt = null`).
   - Vehicle -> `status = REVOKED`.
3. `uploadDocuments`: Hỗ trợ nhiều file, kiểm tra định dạng và dung lượng $\le 10$ MB.

```java
@Test
void terminateContract_shouldCascadeToBedTenantAndVehicle() {
  contractService.terminateContract(contractId);
  assertThat(contract.getStatus()).isEqualTo(ContractStatus.TERMINATED);
  assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
  assertThat(tenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);
  assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.REVOKED);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ContractServiceImplTest`
Expected: FAIL (ContractService not found).

- [ ] **Step 3: Implement ContractService, Scheduler, and Controllers**

1. Cài đặt `ContractServiceImpl` bọc `terminateContract` trong `@Transactional`.
2. Cài đặt `uploadDocuments` và `deleteDocument` kết nối với `StorageService`.
3. Cài đặt `ContractStatusScheduler` với `@Scheduled(cron = "0 0 0 * * *")` quét các hợp đồng có `endDate <= now + 30 days` đổi sang `EXPIRING_SOON`.
4. Cài đặt `AdminContractController` và `TenantContractController`.

- [ ] **Step 4: Run service and controller tests to verify they pass**

Run: `./mvnw test -Dtest=ContractServiceImplTest,ContractControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/services/ContractService.java src/main/java/com/uit/dormmanagement/services/impl/ContractServiceImpl.java src/main/java/com/uit/dormmanagement/schedulers/ContractStatusScheduler.java src/main/java/com/uit/dormmanagement/controllers/*Contract* src/test/java/com/uit/dormmanagement/*/Contract*
git commit -m "feat(contract): implement contract lifecycle, renewal, multi-file documents, and checkout 4-in-1 (FR-10, FR-12, FR-13)"
```

---

### Task 10: End-to-End Resident Lifecycle Smoke Test & Code Quality Check

**Files:**
- Create: `src/test/java/com/uit/dormmanagement/e2e/ResidentLifecycleE2ETest.java`
- Modify: `pom.xml` (nếu cần bổ sung test dependency)

**Interfaces:**
- Consumes: Toàn bộ các API Onboarding, Tenant, Vehicle, Contract.
- Produces: Test luồng đời thực khách thuê trọn vẹn từ lúc bước vào KTX đến khi rời đi.

- [ ] **Step 1: Write the full lifecycle E2E integration test**

Viết test MockMvc kịch bản liên hoàn:
1. `POST /api/v1/admin/onboarding`: Tiếp nhận sinh viên Nam vào phòng P.302 giường G01 -> Kiểm tra giường bị khóa, user được tạo.
2. Login với tài khoản sinh viên mới (`email`, `Password123@`) lấy JWT Token.
3. Sinh viên gọi `POST /api/v1/tenant/vehicles`: Khai báo xe máy kèm 2 ảnh cà vẹt -> Kiểm tra xe `PENDING`.
4. Quản lý gọi `PATCH /api/v1/admin/vehicles/{id}/issue-card`: Cấp mã thẻ `THE-0187` -> Kiểm tra xe `ACTIVE`.
5. Sinh viên gọi đăng ký xe thứ 2 -> Bị từ chối HTTP 400 (`VEHICLE_QUOTA_EXCEEDED`).
6. Quản lý gọi `POST /api/v1/admin/contracts/{id}/renew`: Gia hạn thêm 1 năm -> Kiểm tra hạn mới.
7. Quản lý gọi `POST /api/v1/admin/contracts/{id}/terminate`: Thanh lý trả phòng -> Kiểm tra đồng bộ: Contract `TERMINATED`, Bed `AVAILABLE`, Tenant `CHECKED_OUT`, Vehicle `REVOKED`.
8. Quản lý Onboard khách mới B vào chính giường G01 đó và cấp lại chính mã thẻ `THE-0187` -> Thành công trơn tru.

- [ ] **Step 2: Run E2E test to verify it passes**

Run: `./mvnw test -Dtest=ResidentLifecycleE2ETest`
Expected: PASS (All steps in lifecycle execute cleanly).

- [ ] **Step 3: Run full test suite and Spotless verification**

Run: `./mvnw test spotless:check`
Expected: Tất cả các test đều PASS, Spotless sạch không có lỗi format.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/uit/dormmanagement/e2e/ResidentLifecycleE2ETest.java
git commit -m "test(e2e): add resident lifecycle integration smoke test covering onboarding to checkout"
```
