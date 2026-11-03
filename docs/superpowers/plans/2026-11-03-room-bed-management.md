# Room & Bed Management Subsystem (ktx-29) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai hoàn chỉnh phân hệ Cơ sở, Phòng & Giường (FR-01 đến FR-05, BR-01, BR-02, ticket `ktx-29`) cho Ban Quản lý (Manager), bao gồm hiển thị ma trận phòng-giường theo tầng, tạo phòng tự động sinh danh mục giường con, cập nhật giá/tiện nghi/chính sách giới tính, bật/tắt bảo trì giường độc lập và xóa mềm phòng an toàn.

**Architecture:** Triển khai theo kiến trúc Controller-Service-Repository chuẩn Spring Boot 4.x / Java 25. Các Entity (`Room`, `Bed`) đóng vai trò là JPA POJO thuần túy; toàn bộ nghiệp vụ kiểm soát cách ly giới tính và bảo trì giường được thực thi tại `RoomServiceImpl` và `BedServiceImpl` kết hợp bảo vệ nhiều lớp (Defense in Depth) qua PostgreSQL trigger `trg_check_bed_maintenance`. Các API trả về chuẩn envelope `SingleResponse<T>`, `SuccessResponse`, hoặc `PageResponse<T>` thông qua `ResponseFactory`.

**Tech Stack:** Java 25, Spring Boot 4.x, Spring Data JPA, Hibernate 7, PostgreSQL 17, Flyway, Bean Validation (Jakarta), JUnit 5, Mockito, MockMvc, AssertJ.

**Spec:** `docs/superpowers/specs/2026-11-03-room-bed-management-design.md`

## Global Constraints

- **Platform & Language:** Java 25, Spring Boot 4.x, Maven Wrapper (`./mvnw`).
- **Phân quyền truy cập:** Tất cả các endpoint quản trị phòng & giường yêu cầu quyền `hasRole('MANAGER')`.
- **Response Format:** Kế thừa `AbstractBaseController` và trả về `ResponseFactory.single(...)`, `ResponseFactory.page(...)`, hoặc `ResponseFactory.success(...)`.
- **Exception Handling:** Sử dụng `BaseAppException` kết hợp `RoomErrorCode` (`ROOM_NOT_FOUND`, `BED_NOT_FOUND`, `ROOM_NUMBER_DUPLICATED`, `GENDER_POLICY_MISMATCH`, `CANNOT_CHANGE_GENDER_POLICY`, `BED_NOT_AVAILABLE`, `CANNOT_MAINTAIN_OCCUPIED_BED`, `CANNOT_DELETE_OCCUPIED_ROOM`, `ROOM_CAPACITY_CANNOT_BE_REDUCED`).
- **Data Integrity:** Không xóa vật lý dữ liệu (Soft delete qua `deleted_at`); mọi thao tác thay đổi trạng thái và quan hệ giường/tiện nghi phải bọc trong `@Transactional`.
- **Code Style:** Tuân thủ kiểm tra định dạng Spotless (`./mvnw spotless:check`) và độ phủ kiểm thử >= 80%.

## Review Focus

- **Thay đổi chính sách giới tính khi có khách:** Gửi `PUT /api/v1/admin/rooms/{id}` đổi `genderPolicy` khi `occupiedBeds > 0` phải bị chặn và trả về HTTP 400 `ROOM_400_OCCUPIED`.
- **Bảo trì giường đang có khách hoặc hợp đồng hiệu lực:** Gửi `PATCH /api/v1/admin/beds/{id}/status` với `MAINTENANCE` khi giường `OCCUPIED` hoặc có hợp đồng `ACTIVE`/`EXPIRING_SOON` phải bị chặn và trả về HTTP 400 `BED_400_MAINTENANCE`.
- **Trùng lặp số phòng:** Gửi `POST /api/v1/admin/rooms` với `roomNumber` đã tồn tại trong các phòng chưa xóa (`deleted_at IS NULL`) phải trả về HTTP 409 `ROOM_409`.
- **Chặn xóa mềm phòng đang có khách hoặc hợp đồng hiệu lực:** Gửi `DELETE /api/v1/admin/rooms/{id}` khi phòng có `occupiedBeds > 0` hoặc hợp đồng `ACTIVE`/`EXPIRING_SOON` phải trả về HTTP 400 `ROOM_400_DELETE`.
- **Cho phép xóa mềm phòng có lịch sử hợp đồng đã thanh lý:** Gửi `DELETE /api/v1/admin/rooms/{id}` khi phòng chỉ có hợp đồng `TERMINATED` hoặc `CANCELLED` và `occupiedBeds == 0` phải thành công, cập nhật `deleted_at = NOW()`, và phòng bị ẩn khỏi truy vấn ma trận và danh sách.

---

### Task 1: Room & Bed Data Transfer Objects (DTOs)

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/room/CreateRoomRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/room/RoomAmenityRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/room/UpdateRoomRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/request/room/UpdateBedStatusRequest.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/room/AmenityItemResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/room/OccupantSummaryResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/room/BedSlotResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/room/BedResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/room/RoomDetailResponse.java`
- Create: `src/main/java/com/uit/dormmanagement/dtos/response/room/RoomSummaryResponse.java`
- Test: `src/test/java/com/uit/dormmanagement/dtos/room/RoomDtoValidationTest.java`

**Interfaces:**
- Consumes: `RoomType`, `GenderPolicy`, `BedStatus` enums.
- Produces:
  - `CreateRoomRequest(String roomNumber, int floor, RoomType roomType, GenderPolicy genderPolicy, BigDecimal basePrice, List<RoomAmenityRequest> amenities)`
  - `UpdateRoomRequest(GenderPolicy genderPolicy, BigDecimal basePrice, List<RoomAmenityRequest> amenities)`
  - `UpdateBedStatusRequest(BedStatus status)`
  - `RoomDetailResponse`, `RoomSummaryResponse`, `BedSlotResponse`, `BedResponse`, `OccupantSummaryResponse`, `AmenityItemResponse`

- [ ] **Step 1: Write the failing validation test**

Viết test kiểm tra Bean Validation cho `CreateRoomRequest`, `UpdateRoomRequest`, `UpdateBedStatusRequest` (kiểm tra `@NotBlank`, `@Min(1)`, `@Max(10)`, `@DecimalMin("0.0")`, `@NotNull`).

```java
@Test
void createRoomRequest_whenInvalid_shouldViolateConstraints() {
  CreateRoomRequest request = CreateRoomRequest.builder().build();
  Set<ConstraintViolation<CreateRoomRequest>> violations = validator.validate(request);
  assertThat(violations).isNotEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=RoomDtoValidationTest`
Expected: FAIL with compilation error (classes not found).

- [ ] **Step 3: Implement DTO classes**

Tạo các record hoặc class DTOs trong package `com.uit.dormmanagement.dtos.request.room` và `com.uit.dormmanagement.dtos.response.room` với đầy đủ validation annotations (`@NotBlank`, `@NotNull`, `@Min`, `@Max`, `@DecimalMin`, `@Valid`).

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=RoomDtoValidationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/dtos/ src/test/java/com/uit/dormmanagement/dtos/
git commit -m "feat(room): add request and response DTOs with validation rules"
```

---

### Task 2: Repository Queries & Methods for Room, Bed, and Contract

**Files:**
- Modify: `src/main/java/com/uit/dormmanagement/repositories/RoomRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/BedRepository.java`
- Modify: `src/main/java/com/uit/dormmanagement/repositories/ContractRepository.java`
- Test: `src/test/java/com/uit/dormmanagement/repositories/RoomBedRepositoryTest.java`

**Interfaces:**
- Consumes: `Room`, `Bed`, `Contract`, `GenderPolicy`, `ContractStatus`.
- Produces:
  - `RoomRepository.existsByRoomNumberAndDeletedAtIsNull(String roomNumber)`
  - `RoomRepository.findActiveRoomsWithFilters(Integer floor, GenderPolicy genderPolicy, String search)`
  - `RoomRepository.findActiveRoomsPaged(Integer floor, GenderPolicy genderPolicy, String search, Pageable pageable)`
  - `BedRepository.findByRoomIdOrderByIdAsc(UUID roomId)`
  - `ContractRepository.existsByBedIdAndStatusInAndDeletedAtIsNull(UUID bedId, Collection<ContractStatus> statuses)`
  - `ContractRepository.findActiveContractByBedId(UUID bedId)`

- [ ] **Step 1: Write the failing repository test**

```java
@Test
void existsByRoomNumberAndDeletedAtIsNull_shouldReturnTrueForActiveRoom() {
  Room room = roomRepository.save(Room.builder().roomNumber("P.101").floor(1).roomType(RoomType.FOUR_BEDS).genderPolicy(GenderPolicy.MALE).totalBeds(4).basePrice(new BigDecimal("1500000.00")).build());
  assertThat(roomRepository.existsByRoomNumberAndDeletedAtIsNull("P.101")).isTrue();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=RoomBedRepositoryTest`
Expected: FAIL with missing query methods.

- [ ] **Step 3: Implement repository query methods**

Bổ sung các phương thức truy vấn JPQL/Spring Data vào `RoomRepository`, `BedRepository`, và `ContractRepository`. Sử dụng `@Query` tối ưu với `LEFT JOIN FETCH` khi cần để tránh N+1 Query.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=RoomBedRepositoryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/repositories/ src/test/java/com/uit/dormmanagement/repositories/
git commit -m "feat(room): add repository query methods for room and bed management"
```

---

### Task 3: BedService & BedController (Bed Maintenance Invariant)

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/BedService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/BedServiceImpl.java`
- Create: `src/main/java/com/uit/dormmanagement/controllers/BedController.java`
- Test: `src/test/java/com/uit/dormmanagement/services/BedServiceTest.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/BedControllerTest.java`

**Interfaces:**
- Consumes: `BedRepository`, `ContractRepository`, `UpdateBedStatusRequest`, `RoomErrorCode`.
- Produces:
  - `BedService.updateBedStatus(UUID bedId, UpdateBedStatusRequest request) -> BedResponse`
  - Endpoint `PATCH /api/v1/admin/beds/{id}/status`

- [ ] **Step 1: Write the failing unit tests for BedService**

Kiểm tra:
1. `updateBedStatus` chuyển sang `MAINTENANCE` thành công khi giường `AVAILABLE` và không có hợp đồng hiệu lực.
2. `updateBedStatus` ném `CANNOT_MAINTAIN_OCCUPIED_BED` khi giường đang `OCCUPIED`.
3. `updateBedStatus` ném `CANNOT_MAINTAIN_OCCUPIED_BED` khi giường có hợp đồng `ACTIVE` hoặc `EXPIRING_SOON`.
4. `updateBedStatus` chuyển lại sang `AVAILABLE` thành công khi giường đang `MAINTENANCE`.
5. `updateBedStatus` ném `BED_NOT_FOUND` khi UUID không tồn tại.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=BedServiceTest`
Expected: FAIL with class not found.

- [ ] **Step 3: Implement BedService and BedController**

1. Triển khai `BedServiceImpl` với `@Transactional`:
   - Tìm giường qua `bedRepository.findById(bedId)`.
   - Nếu `request.getStatus() == BedStatus.MAINTENANCE`:
     - Kiểm tra `bed.getStatus() == BedStatus.OCCUPIED` -> Ném `BaseAppException(RoomErrorCode.CANNOT_MAINTAIN_OCCUPIED_BED)`.
     - Kiểm tra `contractRepository.existsByBedIdAndStatusInAndDeletedAtIsNull(bedId, Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON))` -> Ném `BaseAppException(RoomErrorCode.CANNOT_MAINTAIN_OCCUPIED_BED)`.
     - Cập nhật `bed.setStatus(BedStatus.MAINTENANCE)`.
   - Nếu `request.getStatus() == BedStatus.AVAILABLE`:
     - Nếu `bed.getStatus() == BedStatus.OCCUPIED` -> Ném `BaseAppException(RoomErrorCode.CANNOT_MAINTAIN_OCCUPIED_BED)`.
     - Cập nhật `bed.setStatus(BedStatus.AVAILABLE)`.
   - Lưu giường và ánh xạ sang `BedResponse`.
2. Triển khai `BedController` kế thừa `AbstractBaseController` với `@PreAuthorize("hasRole('MANAGER')")` và `@PatchMapping("/{id}/status")`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=BedServiceTest,BedControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/services/ src/main/java/com/uit/dormmanagement/controllers/BedController.java src/test/java/com/uit/dormmanagement/
git commit -m "feat(bed): implement bed status update API with maintenance invariant protection"
```

---

### Task 4: RoomService Implementation (FR-01 to FR-05 Business Logic)

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/services/RoomService.java`
- Create: `src/main/java/com/uit/dormmanagement/services/impl/RoomServiceImpl.java`
- Test: `src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java`

**Interfaces:**
- Consumes: `RoomRepository`, `BedRepository`, `RoomAmenityRepository`, `AmenityRepository`, `ContractRepository`, `CreateRoomRequest`, `UpdateRoomRequest`.
- Produces:
  - `RoomService.getRoomMatrix(Integer floor, GenderPolicy genderPolicy, String search) -> List<RoomDetailResponse>`
  - `RoomService.createRoom(CreateRoomRequest request) -> RoomDetailResponse`
  - `RoomService.updateRoom(UUID roomId, UpdateRoomRequest request) -> RoomDetailResponse`
  - `RoomService.deleteRoom(UUID roomId) -> void`
  - `RoomService.getRoomDetails(UUID roomId) -> RoomDetailResponse`
  - `RoomService.getRooms(Pageable pageable, Integer floor, GenderPolicy genderPolicy, String search) -> Page<RoomSummaryResponse>`

- [ ] **Step 1: Write the failing unit tests for RoomService**

Kiểm tra:
1. `createRoom` thành công: sinh đúng $N$ giường (`G01..G0N`), `slotKey`, `price = basePrice`, `status = AVAILABLE`, lưu tiện nghi.
2. `createRoom` ném `ROOM_NUMBER_DUPLICATED` khi số phòng đã tồn tại.
3. `updateRoom` đổi `genderPolicy` thành công khi `occupiedBeds == 0`.
4. `updateRoom` ném `CANNOT_CHANGE_GENDER_POLICY` khi `occupiedBeds > 0`.
5. `updateRoom` đổi `basePrice` thành công và đồng bộ giá các giường con.
6. `deleteRoom` ném `CANNOT_DELETE_OCCUPIED_ROOM` khi `occupiedBeds > 0`.
7. `deleteRoom` ném `CANNOT_DELETE_OCCUPIED_ROOM` khi có hợp đồng `ACTIVE` hoặc `EXPIRING_SOON`.
8. `deleteRoom` thành công khi phòng có hợp đồng cũ `TERMINATED` và `occupiedBeds == 0`, gán `deletedAt = NOW()`.
9. `getRoomMatrix` trả về đúng danh sách phòng kèm giường và tóm tắt người thuê (occupant) từ hợp đồng hiệu lực.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=RoomServiceTest`
Expected: FAIL with compilation errors.

- [ ] **Step 3: Implement RoomServiceImpl**

Triển khai `RoomServiceImpl` với `@Transactional` và đầy đủ các xử lý kiểm tra nghiệp vụ bám sát Spec:
- `createRoom`: Tính `totalBeds` từ `roomType.getBedCount()`, sinh các bản ghi `Bed` con, lưu `RoomAmenity`.
- `updateRoom`: Kiểm tra `occupiedBeds` trước khi cho phép đổi `genderPolicy`; cập nhật `basePrice` đồng bộ sang `Bed.price`; thay thế danh sách `RoomAmenity`.
- `deleteRoom`: Kiểm tra cả `occupiedBeds > 0` và `contractRepository.existsByBed_Room_IdAndStatusInAndDeletedAtIsNull(roomId, Set.of(ACTIVE, EXPIRING_SOON))`; gán `deletedAt = Instant.now()`.
- `getRoomMatrix`: Truy vấn danh sách phòng kèm tiện nghi và giường con; truy vấn các hợp đồng `ACTIVE` tương ứng để gắn thông tin tóm tắt người thuê `occupant`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=RoomServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/services/ src/test/java/com/uit/dormmanagement/services/RoomServiceTest.java
git commit -m "feat(room): implement RoomService with full business rules and invariants"
```

---

### Task 5: RoomController REST Endpoints & OpenAPI Documentation

**Files:**
- Create: `src/main/java/com/uit/dormmanagement/controllers/RoomController.java`
- Test: `src/test/java/com/uit/dormmanagement/controllers/RoomControllerTest.java`

**Interfaces:**
- Consumes: `RoomService`, `ResponseFactory`, `CreateRoomRequest`, `UpdateRoomRequest`.
- Produces:
  - `GET /api/v1/admin/rooms/matrix`
  - `POST /api/v1/admin/rooms` (201 Created)
  - `PUT /api/v1/admin/rooms/{id}` (200 OK)
  - `DELETE /api/v1/admin/rooms/{id}` (200 OK)
  - `GET /api/v1/admin/rooms` (200 OK, paged)
  - `GET /api/v1/admin/rooms/{id}` (200 OK)

- [ ] **Step 1: Write the failing MockMvc tests for RoomController**

Kiểm tra:
1. `GET /api/v1/admin/rooms/matrix` trả về HTTP 200 với danh sách ma trận phòng.
2. `POST /api/v1/admin/rooms` trả về HTTP 201 khi dữ liệu hợp lệ; trả về HTTP 400 khi thiếu trường bắt buộc.
3. `PUT /api/v1/admin/rooms/{id}` trả về HTTP 200 khi cập nhật thành công.
4. `DELETE /api/v1/admin/rooms/{id}` trả về HTTP 200 với SuccessResponse.
5. `GET /api/v1/admin/rooms` trả về HTTP 200 với PageResponse.
6. Quyền truy cập: Người dùng không có vai trò `MANAGER` (hoặc unauthenticated) bị từ chối với HTTP 401/403.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=RoomControllerTest`
Expected: FAIL with 404 Not Found (Controller chưa tồn tại).

- [ ] **Step 3: Implement RoomController**

Triển khai `RoomController` kế thừa `AbstractBaseController` với:
- `@Tag(name = "Rooms", description = "Room and bed management endpoints for managers (FR-01 to FR-05)")`
- `@PreAuthorize("hasRole('MANAGER')")`
- Đầy đủ `@Operation` cho Scalar/OpenAPI documentation.
- Gọi các phương thức tương ứng của `RoomService` và bọc dữ liệu trả về qua `ResponseFactory.single(...)`, `ResponseFactory.page(...)`, hoặc `ResponseFactory.success(...)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=RoomControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/uit/dormmanagement/controllers/RoomController.java src/test/java/com/uit/dormmanagement/controllers/RoomControllerTest.java
git commit -m "feat(room): implement RoomController REST endpoints with OpenAPI annotations"
```

---

### Task 6: Comprehensive Verification, Spotless Formatting & Regression Check

**Files:**
- Modify: Tất cả các file có liên quan nếu phát hiện vi phạm định dạng hoặc lỗi logic.
- Test: Toàn bộ test suite của dự án (`./mvnw test`).

**Interfaces:**
- Kiểm tra toàn diện hệ thống: compile, spotless code formatting, 100% tests pass.

- [ ] **Step 1: Run Spotless check and apply formatting**

Run: `./mvnw spotless:apply && ./mvnw spotless:check`
Expected: BUILD SUCCESS (0 violations).

- [ ] **Step 2: Run full test suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS (Tất cả unit tests và integration tests đều PASSED, không có failure hay error).

- [ ] **Step 3: Verify Flyway migrations integrity**

Kiểm tra `V1` -> `V4` cú pháp SQL hợp lệ, không có xung đột.

- [ ] **Step 4: Commit**

```bash
git add .
git commit -m "chore(room): format code and verify full test suite for room and bed subsystem"
```
