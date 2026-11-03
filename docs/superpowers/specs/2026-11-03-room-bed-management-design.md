# Design Document: Phân hệ Cơ sở, Phòng & Giường (Room & Bed Management Subsystem)
## Mã Ticket: `ktx-29` | Yêu cầu chức năng: `FR-01` -> `FR-05`, `BR-01`, `BR-02`

---

## 1. Intent & The Why

Trong mô hình Ký túc xá Tư nhân & Sleepbox (KTX Hub / PDMS), không gian lưu trú được chia nhỏ và cho thuê theo từng vị trí giường tiêu chuẩn thay vì cho thuê nguyên căn. Do đó, bài toán vận hành sống còn #1 của hệ thống là **Bed-level Tracking** — quản lý chính xác trạng thái, sức chứa và tỷ lệ lấp đầy đến từng vị trí giường (`P.302-G01`, `P.302-G02`...), phục vụ đối tượng Ban Quản lý / Chủ cơ sở (Manager).

Phân hệ Cơ sở, Phòng & Giường cung cấp công cụ quản trị tập trung:
- Hiển thị trực quan sơ đồ ma trận phòng & giường theo tầng (1 tòa nhà duy nhất).
- Thêm mới phòng và tự động sinh danh mục giường con với đơn giá kế thừa đồng nhất.
- Cập nhật thông tin phòng & tiện nghi trang bị kèm kiểm soát chặt chẽ các ràng buộc bất biến (cách ly giới tính 100%, an toàn cư trú).
- Bật/tắt bảo trì độc lập cho từng vị trí giường trống, tuyệt đối bảo vệ quyền lợi cư trú của khách đang thuê.
- Xóa mềm an toàn dữ liệu phòng ốc khi không còn ràng buộc pháp lý.

---

## 2. Goals, Non-Goals & Anti-Goals

### 2.1. Goals
1. **FR-01 (Ma trận phòng & giường):** Cung cấp API `GET /api/v1/admin/rooms/matrix` hỗ trợ lọc theo tầng (`floor`), chính sách giới tính (`genderPolicy`), và tìm kiếm theo số phòng (`search`). Trả về danh sách phòng kèm tiện nghi, thước đo lấp đầy (`occupiedBeds / totalBeds`), và danh mục từng giường con (kèm thông tin tóm tắt người thuê nếu giường đang có khách).
2. **FR-02 (Tạo phòng & sinh giường tự động):** Cung cấp API `POST /api/v1/admin/rooms`. Kiểm tra tính duy nhất của số phòng (`roomNumber`), tạo phòng và tự động sinh $N$ giường (`G01..G0N`) với khóa duy nhất `slotKey = roomNumber + "-" + bedCode`, trạng thái ban đầu `AVAILABLE`, đơn giá kế thừa đồng nhất từ `basePrice` của phòng. Lưu danh mục tiện nghi kèm số lượng (`room_amenities`).
3. **FR-03 (Cập nhật thông tin phòng & tiện nghi):** Cung cấp API `PUT /api/v1/admin/rooms/{id}`. Cho phép cập nhật `basePrice` (áp dụng cho các hợp đồng phát sinh tiếp theo), danh sách tiện nghi và chính sách giới tính `genderPolicy` (chỉ khi phòng hoàn toàn chưa có người ở). Sức chứa phòng (`roomType` / `totalBeds`) cố định không thay đổi.
4. **FR-04 (Xóa phòng khỏi danh mục):** Cung cấp API `DELETE /api/v1/admin/rooms/{id}`. Thực hiện Soft Delete (`deletedAt = NOW()`). Hệ thống chặn xóa mềm nếu phòng đang có khách ở (`occupiedBeds > 0`) hoặc có hợp đồng còn hiệu lực (`status IN ('ACTIVE', 'EXPIRING_SOON')`). Nếu các hợp đồng cũ đều đã thanh lý (`TERMINATED`, `CANCELLED`), cho phép soft delete phòng an toàn.
5. **FR-05 (Tìm kiếm & phân trang danh mục phòng):** Cung cấp API `GET /api/v1/admin/rooms` hỗ trợ phân trang (`page`, `size`, `sort`) và lọc đa tiêu chí (tầng, giới tính, từ khóa), trả về `PageResponse<RoomSummaryResponse>`.
6. **Quản lý bảo trì giường độc lập (Bed Maintenance Invariant):** Cung cấp API `PATCH /api/v1/admin/beds/{id}/status` cho phép bật/tắt trạng thái giữa `AVAILABLE` và `MAINTENANCE`. Chỉ cho phép chuyển sang `MAINTENANCE` khi giường không có người ở (`status !== OCCUPIED` và không có hợp đồng `ACTIVE`/`EXPIRING_SOON`).

### 2.2. Non-Goals
- Xếp phòng và tạo hợp đồng cho khách (thuộc phạm vi FR-09).
- Luồng đổi phòng, gia hạn hoặc thanh lý hợp đồng trả phòng (thuộc phạm vi FR-11, FR-12, FR-13).
- Cổng đăng ký tìm phòng hoặc chọn giường online từ bên ngoài dành cho khách thuê (nghiệp vụ quy định quản lý dẫn khách xem phòng offline và gán trực tiếp).
- Quản lý cơ sở đa chi nhánh hoặc nhiều tòa nhà (hệ thống là Single-facility Architecture: 01 tòa nhà duy nhất chia theo tầng).

### 2.3. Anti-Goals
- **Không quản lý trạng thái cào bằng ở cấp phòng:** Phòng không lưu cột `status` riêng biệt trong CSDL. Trạng thái được theo dõi chuẩn xác ở cấp giường (`Bed.status`: `AVAILABLE`, `OCCUPIED`, `MAINTENANCE`).
- **Không xâm phạm quyền lợi khách thuê:** Tuyệt đối cấm đưa giường đang có khách cư trú (`OCCUPIED`) hoặc có hợp đồng còn hiệu lực vào trạng thái bảo trì.
- **Không phá vỡ nguyên tắc cách ly giới tính (BR-02):** Tuyệt đối cấm thay đổi chính sách giới tính của phòng khi phòng đang có ít nhất 01 khách cư trú.
- **Không xóa vật lý (No Hard Delete):** Không sử dụng `DELETE FROM rooms` hay `DELETE FROM beds`; toàn bộ dữ liệu lưu trữ lịch sử kế toán và đối soát.

---

## 3. Constraints & Architecture Rules

- **Nền tảng công nghệ:** Java 25, Spring Boot 4.x, Spring Data JPA, Hibernate 7, PostgreSQL 17, Flyway.
- **Envelope Response:** Toàn bộ API trả về cấu trúc chuẩn `SingleResponse<T>`, `SuccessResponse`, hoặc `PageResponse<T>` thông qua `ResponseFactory` và kế thừa `AbstractBaseController`.
- **Xử lý lỗi (Exception Handling):** Ném `BaseAppException` kết hợp `RoomErrorCode` (HTTP 400, 404, 409).
- **Phân quyền (RBAC):** Toàn bộ các endpoint quản trị phòng & giường chỉ cho phép vai trò `MANAGER` (`hasRole('MANAGER')`).
- **Clean Entity Architecture:** Các JPA Entities (`Room`, `Bed`) là POJO thuần túy (chỉ chứa getters/setters/annotations, không chứa logic nghiệp vụ hay ném exception); toàn bộ logic kiểm tra và điều phối nghiệp vụ nằm tại Service layer.
- **Toàn vẹn Transaction:** Các thao tác tạo phòng kèm sinh giường, cập nhật tiện nghi, hoặc cập nhật trạng thái giường phải được bọc trong `@Transactional`.
- **Độ bao phủ kiểm thử:** Unit tests và MockMvc Integration tests đạt tối thiểu 80% coverage.

---

## 4. Decided How-It-Works (Quyết định nghiệp vụ & kỹ thuật đã chốt)

### 4.1. Quy tắc Đơn giá thuê đồng nhất (Unified Bed Price)
- `Room.basePrice` là đơn giá tiêu chuẩn hàng tháng cho mỗi giường trong phòng.
- Mọi giường trong phòng khi được sinh ra tự động kế thừa `Bed.price = Room.basePrice`.
- Khi Quản lý cập nhật `basePrice` mới của phòng, giá của các giường trong CSDL được cập nhật đồng bộ để các hợp đồng ký mới sau thời điểm đó áp dụng giá mới. Các hợp đồng đang hiệu lực trước đó giữ nguyên `monthlyRent` đã ký trong bảng `contracts`.

### 4.2. Bỏ cột Trạng thái Phòng & Quản lý Trạng thái ở Cấp Giường (Bed-level Tracking)
- Bảng `rooms` không chứa cột `status`.
- Trạng thái vận hành và lưu trú được lưu vết chuẩn xác ở từng bản ghi `beds` (`AVAILABLE`, `OCCUPIED`, `MAINTENANCE`).
- Phòng theo dõi độ lấp đầy thông qua `occupiedBeds` và `totalBeds`.

### 4.3. Quy tắc Bảo trì từng Giường (Bed Maintenance Invariant)
- Thao tác bật/tắt bảo trì giường diễn ra trên Drawer chi tiết phòng (`ADM-03`).
- Một giường chỉ được chuyển sang `MAINTENANCE` nếu `Bed.status !== OCCUPIED` và không có hợp đồng nào đang ở trạng thái `ACTIVE` hoặc `EXPIRING_SOON` liên kết với giường đó.
- Cả tầng Service (`BedServiceImpl`) và tầng Database Trigger (`V4__enforce_bed_maintenance_rule.sql`) đều cùng kiểm soát chặt chẽ quy tắc này (Defense in Depth).

---

## 5. API Endpoints Specification

### 5.1. `GET /api/v1/admin/rooms/matrix`
- **Mục đích:** Cung cấp dữ liệu trực quan cho màn hình ma trận phòng & giường (`ADM-03`).
- **Query Params:**
  - `floor` (Integer, tùy chọn): Lọc theo tầng (1..10).
  - `genderPolicy` (GenderPolicy enum: `MALE`, `FEMALE`, tùy chọn).
  - `search` (String, tùy chọn): Tìm kiếm gần đúng theo số phòng (`roomNumber`).
- **Response Data (`RoomMatrixResponse`):**
  - Danh sách các phòng thỏa mãn điều kiện. Mỗi phòng gồm:
    - Thông tin phòng: `id`, `roomNumber`, `floor`, `roomType`, `genderPolicy`, `totalBeds`, `occupiedBeds`, `basePrice`.
    - Danh sách tiện nghi: danh sách tên tiện nghi kèm số lượng (`AmenityItemResponse`).
    - Danh sách giường (`BedSlotResponse`): `id`, `bedCode`, `slotKey`, `price`, `status`, `occupant` (nếu có khách: `fullName`, `phone`, `contractEndDate`).

### 5.2. `POST /api/v1/admin/rooms`
- **Mục đích:** Thêm mới phòng, tự động sinh $N$ giường và gán tiện nghi (FR-02).
- **Request Body (`CreateRoomRequest`):**
  - `roomNumber` (String, bắt buộc, định dạng regex ví dụ `^P\.[0-9]{3,4}$` hoặc không rỗng, tối đa 20 ký tự).
  - `floor` (int, bắt buộc, từ 1 đến 10).
  - `roomType` (RoomType enum: `FOUR_BEDS`, `SIX_BEDS`, `EIGHT_BEDS`, bắt buộc).
  - `genderPolicy` (GenderPolicy enum: `MALE`, `FEMALE`, bắt buộc).
  - `basePrice` (BigDecimal, bắt buộc, > 0).
  - `amenityIds` (List<RoomAmenityRequest>, tùy chọn: danh sách `amenityId` và `quantity`).
- **Xử lý:**
  - `totalBeds` suy ra từ `roomType`: `FOUR_BEDS` -> 4, `SIX_BEDS` -> 6, `EIGHT_BEDS` -> 8.
  - Kiểm tra `roomNumber` chưa tồn tại trong các phòng chưa bị xóa (`deletedAt IS NULL`). Nếu trùng ném `ROOM_NUMBER_DUPLICATED`.
  - Lưu `Room` với `occupiedBeds = 0`.
  - Sinh $N$ bản ghi `Bed` với `bedCode` từ `G01` đến `G0n`, `slotKey = roomNumber + "-" + bedCode`, `price = basePrice`, `status = AVAILABLE`.
  - Lưu danh mục tiện nghi trong bảng `room_amenities`.
- **Response:** `SingleResponse<RoomDetailResponse>` (HTTP 201 Created).

### 5.3. `PUT /api/v1/admin/rooms/{id}`
- **Mục đích:** Cập nhật thông tin phòng, tiện nghi và chính sách giới tính khi chưa có khách (FR-03).
- **Request Body (`UpdateRoomRequest`):**
  - `genderPolicy` (GenderPolicy enum: `MALE`, `FEMALE`, tùy chọn).
  - `basePrice` (BigDecimal, bắt buộc, > 0).
  - `amenityIds` (List<RoomAmenityRequest>, tùy chọn).
- **Xử lý:**
  - Tìm phòng theo `id` (nếu không thấy ném `ROOM_NOT_FOUND`).
  - Nếu có yêu cầu đổi `genderPolicy` và giá trị mới khác giá trị hiện tại:
    - Kiểm tra `room.occupiedBeds > 0` -> Ném `CANNOT_CHANGE_GENDER_POLICY` (theo BR-02 & FR-03).
    - Cập nhật `room.genderPolicy = newGenderPolicy`.
  - Sức chứa (`roomType` / `totalBeds`): Cố định không thay đổi sau khi tạo phòng để bảo đảm tính toàn vẹn danh mục giường `G01..G0N`.
  - Cập nhật `basePrice` của phòng và đồng bộ đơn giá cho các giường con.
  - Cập nhật danh sách tiện nghi (xóa tiện nghi cũ, thêm mới theo danh sách).
  - Lưu phòng và trả về chi tiết.
- **Response:** `SingleResponse<RoomDetailResponse>` (HTTP 200 OK).

### 5.4. `DELETE /api/v1/admin/rooms/{id}`
- **Mục đích:** Xóa mềm phòng khỏi hệ thống (FR-04).
- **Xử lý:**
  - Tìm phòng theo `id`.
  - Kiểm tra điều kiện chặn xóa:
    - Nếu `occupiedBeds > 0` -> Ném `CANNOT_DELETE_OCCUPIED_ROOM`.
    - Nếu có bất kỳ hợp đồng nào đang có hiệu lực (`status IN ('ACTIVE', 'EXPIRING_SOON')`) trên các giường của phòng -> Ném `CANNOT_DELETE_OCCUPIED_ROOM`.
    - Nếu các hợp đồng trong quá khứ đều đã thanh lý (`TERMINATED`, `CANCELLED`), cho phép xóa mềm.
  - Gán `deletedAt = Instant.now()`.
- **Response:** `SuccessResponse` (HTTP 200 OK).

### 5.5. `GET /api/v1/admin/rooms`
- **Mục đích:** Danh sách quản lý phòng dạng bảng phân trang (FR-05).
- **Query Params:** `page`, `size`, `sort`, `floor`, `genderPolicy`, `search`.
- **Response:** `PageResponse<RoomSummaryResponse>` (HTTP 200 OK).

### 5.6. `GET /api/v1/admin/rooms/{id}`
- **Mục đích:** Lấy thông tin chi tiết một phòng kèm tiện nghi và danh sách giường.
- **Response:** `SingleResponse<RoomDetailResponse>` (HTTP 200 OK).

### 5.7. `PATCH /api/v1/admin/beds/{id}/status`
- **Mục đích:** Bật/tắt trạng thái bảo trì cho từng vị trí giường trống trong Drawer chi tiết.
- **Request Body (`UpdateBedStatusRequest`):**
  - `status` (BedStatus enum: `AVAILABLE` hoặc `MAINTENANCE`, bắt buộc).
- **Xử lý:**
  - Tìm giường theo `id` (nếu không thấy ném `BED_NOT_FOUND`).
  - Nếu yêu cầu chuyển sang `MAINTENANCE`:
    - Kiểm tra nếu `bed.status == OCCUPIED` -> Ném `CANNOT_MAINTAIN_OCCUPIED_BED`.
    - Kiểm tra nếu có hợp đồng `ACTIVE` hoặc `EXPIRING_SOON` liên kết -> Ném `CANNOT_MAINTAIN_OCCUPIED_BED`.
    - Chuyển `bed.status = MAINTENANCE`.
  - Nếu yêu cầu chuyển sang `AVAILABLE`:
    - Chuyển `bed.status = AVAILABLE`.
  - Lưu giường.
- **Response:** `SingleResponse<BedResponse>` (HTTP 200 OK).

---

## 6. What's Left to the Builder (Chi tiết để người thực thi quyết định)
- Định nghĩa chi tiết các DTO trong package `dtos/request/room/` và `dtos/response/room/`.
- Tối ưu hóa truy vấn dữ liệu ma trận (ví dụ: dùng EntityGraph hoặc JOIN FETCH trong `RoomRepository` để tránh vấn đề N+1 Query khi load danh sách phòng, giường, tiện nghi và hợp đồng).
- Xây dựng mapper hoặc converter giữa Entity và DTO (ưu tiên MapStruct hoặc mapper component).
- Viết bộ test đầy đủ cho Controller và Service (`RoomServiceTest`, `BedServiceTest`, `RoomControllerTest`).
