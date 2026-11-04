# Design Document: Phân hệ Khách thuê, Phương tiện, Tiếp nhận & Hợp đồng
## Mã phân hệ: 4.2 & 4.3 | Yêu cầu chức năng: `FR-06` -> `FR-13` | Quy tắc nghiệp vụ: `BR-01`, `BR-02`, `BR-04`, `BR-08`, `BR-09`, `BR-10`, `BR-11`

---

## 1. Intent & The Why

Trong mô hình vận hành Ký túc xá Tư nhân & Sleepbox cao cấp (KTX Hub / PDMS), vòng đời cư trú của khách thuê là trục nghiệp vụ trọng tâm kết nối toàn bộ hoạt động tài chính, phòng ốc và an ninh trật tự.

Phân hệ 4.2 (Hồ sơ Khách thuê & Phương tiện di chuyển) và 4.3 (Tiếp nhận, Xếp phòng & Hợp đồng) giải quyết các bài toán vận hành cốt lõi:
1. **Tiếp nhận khách mới trọn gói 1 bước (Direct Onboarding - FR-09):** Khách xem phòng trực tiếp ngoài đời, khi đồng ý thuê thì Ban Quản lý xếp ngay vào giường trống. Hệ thống lập tức tạo hồ sơ, cấp tài khoản đăng nhập, sinh hợp đồng và khóa giường chỉ trong 1 Transaction CSDL duy nhất nhằm tránh tình trạng thông tin rời rạc hoặc khách ở "chui".
2. **Tuân thủ tuyệt đối quy tắc Cách ly giới tính (Strict Gender Isolation - BR-02):** Chặn đứng 100% rủi ro xếp nhầm khách vào phòng khác giới tính ở cấp độ CSDL và Service.
3. **Quản lý phương tiện hầm xe bảo an & hạn mức nghiêm ngặt (BR-10 / FR-08):** Hầm xe có sức chứa giới hạn, áp dụng chính sách bao trọn gói 100% theo tiền phòng (miễn phí, không thu thêm). Mỗi khách thuê chỉ được đăng ký tối đa 01 phương tiện. Hệ thống hỗ trợ khách tự gửi ảnh cà vẹt 2 mặt để Quản lý đối soát trực tiếp trước khi cấp thẻ từ vật lý.
4. **Lưu trữ minh chứng hợp đồng đa tệp (Multi-file Contract Evidence - FR-10):** Sau khi ký hợp đồng giấy ngoài đời, Quản lý tải ảnh chụp/scan hợp đồng và phụ lục gia hạn lưu trữ an toàn trên OCI Object Storage, minh bạch hóa cho cả Quản lý và Khách thuê cùng tra cứu.
5. **Gia hạn hợp đồng linh hoạt (Contract Renewal - FR-12):** Thỏa thuận ngoài đời, ký phụ lục gia hạn giấy, Quản lý cập nhật ngày kết thúc mới và tải ảnh phụ lục, bảo lưu nguyên vẹn số tiền cọc ban đầu.
6. **Thanh lý phòng & Giải phóng giường tập trung (Checkout - FR-07 / FR-13 / BR-04):** Toàn bộ thao tác trả phòng được thực hiện tập trung tại trang Hợp đồng (`ContractsPage`), tự động kích hoạt 4 hành động đồng bộ: Thanh lý hợp đồng (`TERMINATED`), Đóng hồ sơ khách (`CHECKED_OUT`), Giải phóng giường (`AVAILABLE`), và Thu hồi thẻ xe (`REVOKED`).

---

## 2. Goals, Non-Goals & Anti-Goals

### 2.1. Goals
1. **Direct Onboarding 1-step (FR-09, BR-01, BR-02, BR-09):** Cung cấp API `POST /api/v1/admin/onboarding` thực thi trong 1 Transaction duy nhất:
   - Thẩm định tính hợp lệ của CCCD (12 số), kiểm tra không trùng lặp CCCD, SĐT, Email.
   - Thẩm định cách ly giới tính 100%: giới tính khách phải khớp với `genderPolicy` của phòng.
   - Thẩm định giường trống (`status == AVAILABLE`).
   - Tạo hồ sơ `Tenant` (mã tự sinh `NT-xxxx`).
   - Tự động sinh tài khoản `User` (username là email, mật khẩu ban đầu mặc định `Password123@`, vai trò `TENANT`, trạng thái `ACTIVE`).
   - Tạo hợp đồng `Contract` (mã tự sinh `HD-YYYY-xxxx`, trạng thái `ACTIVE`, tiền thuê lấy theo giá phòng `Room.basePrice`, lưu tiền cọc đã thu ngoài).
   - Chuyển trạng thái `Bed` sang `OCCUPIED`.
   - Trả về thông tin Onboarding kèm thông tin tài khoản đăng nhập để Quản lý gửi Zalo/SMS cho khách.
2. **Quản lý hồ sơ khách thuê (FR-06):**
   - API `GET /api/v1/admin/tenants`: Phân trang, tìm kiếm (họ tên, CCCD, email, SĐT, phòng, giường), lọc theo phòng (`roomId`), lọc theo trạng thái cư trú (`residentStatus`: `ACTIVE`, `CHECKED_OUT`, `RESERVED`).
   - API `GET /api/v1/admin/tenants/{id}`: Trả về chi tiết hồ sơ định danh, phòng/giường hiện tại, hợp đồng hiện tại, lịch sử hợp đồng và tóm tắt công nợ.
   - API `PUT /api/v1/admin/tenants/{id}`: Cập nhật thông tin cá nhân (họ tên, SĐT, địa chỉ thường trú, người liên hệ khẩn cấp).
3. **Quản lý phương tiện di chuyển hầm xe (FR-08, BR-10):**
   - Danh mục phân loại xe chuẩn hóa: `SCOOTER` (Xe tay ga), `MANUAL_MOTORCYCLE` (Xe số), `ELECTRIC_MOTORBIKE` (Xe máy điện), `BICYCLE` (Xe đạp), `ELECTRIC_BICYCLE` (Xe đạp điện).
   - Hạn mức: Mỗi khách thuê chỉ được sở hữu tối đa 01 phương tiện ở trạng thái `PENDING` hoặc `ACTIVE`.
   - Phía Khách thuê (Mobile Portal):
     - API `GET /api/v1/tenant/vehicles/me`: Lấy thông tin phương tiện hiện tại của khách đang đăng nhập.
     - API `POST /api/v1/tenant/vehicles`: Khách tự đăng ký xe (biển số, loại xe, hãng xe, màu sơn, 2 ảnh chụp mặt trước/sau cà vẹt xe). Trạng thái ban đầu `PENDING` ("Chưa cấp thẻ" / "Chờ duyệt").
   - Phía Quản lý (Admin Web):
     - API `GET /api/v1/admin/vehicles`: Phân trang, tìm kiếm (biển số, tên khách, loại xe, hãng xe, mã thẻ), lọc theo tab trạng thái (`ALL`, `PENDING`, `ACTIVE`, `REVOKED`).
     - API `POST /api/v1/admin/vehicles`: Quản lý khai báo phương tiện trực tiếp cho khách thuê (nhập luôn mã thẻ nếu đã cấp).
     - API `PUT /api/v1/admin/vehicles/{id}`: Cập nhật thông tin phương tiện.
     - API `PATCH /api/v1/admin/vehicles/{id}/issue-card`: Quản lý đối soát 2 ảnh cà vẹt xe, nhập mã thẻ từ vật lý (`cardCode`, VD: `THE-0187`), kích hoạt trạng thái `ACTIVE` ("Đã cấp thẻ").
     - API `PATCH /api/v1/admin/vehicles/{id}/revoke-card`: Thu hồi thẻ xe, chuyển trạng thái `REVOKED` ("Đã thu hồi").
     - API `DELETE /api/v1/admin/vehicles/{id}`: Xóa mềm phương tiện (`deletedAt = NOW()`).
4. **Quản lý hợp đồng & Minh chứng đa tệp (FR-10):**
   - API `GET /api/v1/admin/contracts`: Danh sách hợp đồng phân trang, tìm kiếm, lọc theo trạng thái (`ACTIVE`, `EXPIRING_SOON`, `TERMINATED`).
   - API `GET /api/v1/admin/contracts/{id}`: Chi tiết hợp đồng kèm danh sách tài liệu minh chứng (`contract_documents`).
   - API `POST /api/v1/admin/contracts/{id}/documents`: Tải lên đa tệp minh chứng (PDF, DOCX, JPG, PNG, dung lượng tối đa 10 MB/tệp) kèm phân loại: `ORIGINAL_CONTRACT` (Hợp đồng gốc), `RENEWAL_ANNEX` (Phụ lục gia hạn), `CHECKOUT_HANDOVER` (Biên bản bàn giao), `OTHER` (Tài liệu khác).
   - API `DELETE /api/v1/admin/contracts/{id}/documents/{documentId}`: Xóa tệp tài liệu minh chứng.
   - Phía Khách thuê: API `GET /api/v1/tenant/contracts/me` để xem hợp đồng hiện tại và duyệt danh sách các tệp minh chứng đã lưu.
5. **Gia hạn hợp đồng cư trú (FR-12):**
   - API `POST /api/v1/admin/contracts/{id}/renew`: Nhận `newEndDate` (phải sau ngày kết thúc cũ) và tùy chọn tệp phụ lục gia hạn giấy (`RENEWAL_ANNEX`).
   - Xử lý: Cập nhật `endDate = newEndDate`, chuyển trạng thái hợp đồng về `ACTIVE`, bảo lưu nguyên vẹn `depositAmount`.
6. **Thanh lý hợp đồng & Trả phòng Checkout (FR-07, FR-13, BR-04):**
   - API `POST /api/v1/admin/contracts/{id}/terminate`: Quản lý xác nhận thanh lý và hoàn cọc trực tiếp ngoài đời.
   - Thực thi đồng bộ 4 hành động trong 1 Transaction CSDL:
     1. Hợp đồng (`contracts`): chuyển trạng thái `TERMINATED`.
     2. Giường (`beds`): giải phóng về trạng thái `AVAILABLE`.
     3. Hồ sơ người thuê (`tenants`): chuyển sang `resident_status = CHECKED_OUT` (Soft Delete để lưu lịch sử kế toán, không xóa vật lý).
     4. Phương tiện gửi xe (`vehicles`): nếu khách có xe đang cấp thẻ (`ACTIVE`), chuyển sang `REVOKED`.
7. **Lưu trữ đám mây OCI Object Storage:**
   - Module `StorageService` trừu tượng hóa việc upload ảnh cà vẹt và tài liệu hợp đồng lên Oracle Cloud Infrastructure (OCI) Object Storage.
   - Nạp cấu hình từ `.env` / `application.yml`.
   - Có cơ chế Local Mock Storage khi chưa cấu hình key OCI thực tế để toàn bộ test và môi trường dev chạy độc lập, trơn tru.

### 2.2. Non-Goals
- Xử lý yêu cầu đổi phòng (FR-11): Tạm hoãn theo lộ trình giai đoạn 2 như SRS đã quy định.
- Tự động phát hành hóa đơn hàng tháng (thuộc phạm vi FR-15).
- Tích hợp cổng thanh toán trực tuyến VietQR / Thu nợ (thuộc phạm vi FR-16).
- Cổng đăng ký tìm phòng công khai từ internet (Khách thuê phải xem phòng offline trước khi quản lý tạo hồ sơ).

### 2.3. Anti-Goals
- **Không tạo khách thuê "lơ lửng" không phòng không hợp đồng:** Loại bỏ API tạo khách thuê độc lập không gán giường. Mọi khách mới đều đi qua luồng Onboarding chuẩn hóa.
- **Không thu phí gửi xe hầm KTX:** Phí gửi xe bao trọn gói 100% trong tiền phòng theo BR-10. Tuyệt đối không sinh thêm khoản thu phương tiện vào hóa đơn.
- **Không vi phạm quy tắc cách ly giới tính:** Chặn đứng ở Service layer, ném `CANNOT_ASSIGN_GENDER_MISMATCH` nếu giới tính khách khác giới tính phòng.
- **Không xóa vật lý dữ liệu (No Hard Delete):** Tuyệt đối không dùng `DELETE FROM tenants`, `DELETE FROM contracts`, `DELETE FROM vehicles`. Áp dụng `deleted_at = NOW()` và `resident_status = 'CHECKED_OUT'` / `status = 'REVOKED'`.

---

## 3. Constraints & Architecture Rules

- **Nền tảng:** Java 25, Spring Boot 4.x, Spring Data JPA, Hibernate 7, PostgreSQL 17, Flyway.
- **Response Format:** Toàn bộ API trả về `SingleResponse<T>`, `SuccessResponse`, hoặc `PageResponse<T>` thông qua `ResponseFactory` kế thừa `AbstractBaseController`.
- **Mô hình Phân quyền (RBAC):**
  - Quản trị viên (`MANAGER`): Toàn quyền gọi các API Onboarding, quản lý Khách thuê, Phương tiện, Hợp đồng và Tài liệu.
  - Khách thuê (`TENANT`): Chỉ được gọi các API cá nhân (`/api/v1/tenant/**`) cho chính hồ sơ của mình (dựa trên `UserPrincipal.getId()`).
- **Xử lý ngoại lệ chuẩn hóa:** Định nghĩa các bộ mã lỗi riêng biệt:
  - `TenantErrorCode`: `TENANT_NOT_FOUND`, `ID_CARD_NUMBER_DUPLICATED`, `EMAIL_DUPLICATED`, `PHONE_DUPLICATED`, `INVALID_ID_CARD_FORMAT`.
  - `ContractErrorCode`: `CONTRACT_NOT_FOUND`, `CONTRACT_ALREADY_TERMINATED`, `BED_NOT_AVAILABLE`, `GENDER_MISMATCH`, `INVALID_CONTRACT_DATES`, `DOCUMENT_NOT_FOUND`, `DOCUMENT_FILE_SIZE_EXCEEDED`, `UNSUPPORTED_DOCUMENT_FORMAT`.
  - `VehicleErrorCode`: `VEHICLE_NOT_FOUND`, `LICENSE_PLATE_DUPLICATED`, `CARD_CODE_DUPLICATED`, `VEHICLE_QUOTA_EXCEEDED`, `VEHICLE_ALREADY_ACTIVE`, `VEHICLE_NOT_PENDING`.
- **Clean Architecture & Transaction:**
  - JPA Entities là POJO thuần túy (không chứa business logic).
  - Nghiệp vụ đa bước (Onboarding, Checkout, Issue Card) bắt buộc bọc trong `@Transactional`.
- **Lưu trữ OCI Object Storage:**
  - Interface `StorageService` gồm `uploadFile(MultipartFile file, String folder)` và `deleteFile(String fileUrl)`.
  - Khi chưa có credential trong `.env`, tự động fallback sang `LocalStorageService` hoặc `MockStorageService` để test và build xanh 100%.

---

## 4. Decided How-It-Works (Quyết định nghiệp vụ & kỹ thuật đã chốt)

### 4.1. Quy trình Onboarding 1 bước & Tái tiếp nhận khách cũ (Re-onboarding)
- Endpoint: `POST /api/v1/admin/onboarding`.
- **Khách mới hoàn toàn:** Kiểm tra trùng CCCD, Email, SĐT. Sinh mã khách thuê: `NT-` + 4 chữ số (VD: `NT-0187`). Sinh mã hợp đồng: `HD-` + năm bắt đầu + `-` + 4 số (VD: `HD-2026-0187`). Sinh tài khoản `User` với username là email, mật khẩu mặc định: `Password123@`. Khóa giường sang `OCCUPIED`.
- **Khách cũ từng ở quay lại (Re-onboarding):** Nếu CCCD đã tồn tại nhưng có `residentStatus == CHECKED_OUT`, hệ thống không báo lỗi trùng mà tự động kích hoạt lại hồ sơ: cập nhật thông tin cá nhân mới nhất, chuyển `residentStatus = ACTIVE`, liên kết với tài khoản `User` sẵn có, và tạo `Contract` mới gắn với giường mới.
- Tiền thuê hàng tháng: Tự động lấy từ `Bed.price` (bằng `Room.basePrice`).
- Tiền cọc: Nhập từ form (mặc định bằng 1 tháng tiền thuê giường).

### 4.2. Quy trình Thanh lý (Checkout) duy nhất tại Hợp đồng & Bảo toàn Soft Delete
- Endpoint: `POST /api/v1/admin/contracts/{id}/terminate`.
- Một Transaction duy nhất giải phóng toàn bộ tài nguyên:
  - `Contract.status = ContractStatus.TERMINATED`.
  - `Bed.status = BedStatus.AVAILABLE`.
  - `Tenant.residentStatus = ResidentStatus.CHECKED_OUT`. **Lưu ý kiến trúc:** Giữ `Tenant.deletedAt = NULL` khi checkout để bảo đảm Hibernate `@SQLRestriction("deleted_at IS NULL")` không làm mất dữ liệu lịch sử đối soát công nợ, hợp đồng và hóa đơn cũ. Cờ `deletedAt` chỉ dùng khi xóa bỏ hồ sơ rác.
  - `Vehicle.status = VehicleStatus.REVOKED` (nếu khách đang có xe).
  - Tài khoản `User` của khách vẫn được duy trì ở trạng thái `ACTIVE` để khách có thể đăng nhập xem lịch sử hóa đơn, biên lai và hợp đồng cũ trên ứng dụng di động; nhưng toàn bộ các API mang tính chất đăng ký mới (như đăng ký xe) sẽ kiểm tra `tenant.residentStatus == ACTIVE`, nếu đã `CHECKED_OUT` sẽ từ chối thao tác.

### 4.3. Quy trình Phương tiện di chuyển & Tái cấp phát thẻ vật lý
- Danh mục xe: Loại bỏ "Xe côn tay". Hệ thống hỗ trợ 4 phân loại chính: `SCOOTER` (Xe tay ga), `MANUAL_MOTORCYCLE` (Xe số), `ELECTRIC_MOTORBIKE` (Xe máy điện), `BICYCLE` (Xe đạp), cùng `ELECTRIC_BICYCLE` (Xe đạp điện).
- Hạn mức: Mỗi khách thuê chỉ có tối đa 01 xe chưa thu hồi (`status IN ('PENDING', 'ACTIVE')`). Nếu đã có, chặn đăng ký mới và ném `VEHICLE_QUOTA_EXCEEDED`.
- **Tái cấp phát thẻ từ vật lý (Partial Unique Index):** Thẻ từ gửi xe (`cardCode`, VD: `THE-0187`) là tài sản vật lý cố định. Khi khách trả phòng hoặc thu hồi thẻ xe, `status` đổi sang `REVOKED`. Bổ sung Migration V7 để thay thế ràng buộc `UNIQUE` cứng toàn bảng bằng Partial Unique Index:
  `CREATE UNIQUE INDEX uq_vehicles_active_card_code ON vehicles (card_code) WHERE status = 'ACTIVE' AND deleted_at IS NULL;`
  Cơ chế này vừa bảo lưu lịch sử xe cũ từng dùng thẻ nào phục vụ bảo an, vừa cho phép Quản lý tái cấp phát mã thẻ từ vật lý đó cho xe của khách mới.

### 4.4. Cơ chế tự động quét Hợp đồng Sắp hết hạn (EXPIRING_SOON)
- Thiết lập Scheduled Task `@Scheduled(cron = "0 0 0 * * *")` chạy vào 00:00 hàng ngày.
- Quét các hợp đồng có `status == ACTIVE` và `endDate <= LocalDate.now().plusDays(30)` để tự động chuyển sang `status = EXPIRING_SOON`.

### 4.5. Quy trình Quản lý Tài liệu Hợp đồng & OCI Storage
- Bảng `contract_documents` đã có sẵn trong migration V6.
- Hỗ trợ tải nhiều tệp cùng lúc (multi-file upload): `.pdf`, `.docx`, `.doc`, `.jpg`, `.jpeg`, `.png`, `.webp` (tối đa 10 MB/tệp).
- Phân loại tài liệu (`ContractDocumentCategory`): `ORIGINAL_CONTRACT`, `RENEWAL_ANNEX`, `CHECKOUT_HANDOVER`, `OTHER`.
- Hỗ trợ `LocalStorageService` map đường dẫn `/uploads/**` qua `ResourceHandler` khi chạy dev/test; sẵn sàng switch sang OCI Object Storage khi có credentials.

---

## 5. Chi tiết API Endpoints Specification

### 5.1. Nhóm API Tiếp nhận & Xếp phòng (Onboarding)
- `POST /api/v1/admin/onboarding`
  - **Quyền:** `hasRole('MANAGER')`
  - **Body (`OnboardingRequest`):**
    - `fullName` (String, required, 2-100 ký tự)
    - `gender` (GenderPolicy: `MALE`, `FEMALE`, required)
    - `idCardNumber` (String, required, đúng 12 chữ số)
    - `phone` (String, required, 10 chữ số bắt đầu bằng 0)
    - `email` (String, required, định dạng email hợp lệ)
    - `permanentAddress` (String, required)
    - `dateOfBirth` (LocalDate, optional)
    - `emergencyContactName` (String, optional)
    - `emergencyContactPhone` (String, optional)
    - `bedId` (UUID, required)
    - `startDate` (LocalDate, required)
    - `endDate` (LocalDate, required, sau `startDate`)
    - `depositAmount` (BigDecimal, required, >= 0)
  - **Response:** `SingleResponse<OnboardingResponse>` (HTTP 201 Created) chứa đầy đủ thông tin `TenantResponse`, `ContractResponse`, và `GeneratedAccountInfo` (email, phone, initialPassword = `Password123@`, roomBed).
### 5.2. Nhóm API Quản lý Hồ sơ Khách thuê (Tenants)
- `GET /api/v1/admin/tenants`
  - **Quyền:** `hasRole('MANAGER')`
  - **Params:** `page`, `size`, `sort`, `search` (tìm tên, CCCD, email, SĐT, phòng, giường), `roomId` (UUID, optional), `residentStatus` (ResidentStatus, optional).
  - **Response:** `PageResponse<TenantSummaryResponse>`
- `GET /api/v1/admin/tenants/{id}`
  - **Quyền:** `hasRole('MANAGER')`
  - **Response:** `SingleResponse<TenantDetailResponse>` (thông tin cá nhân, phòng/giường hiện tại, hợp đồng hiện tại, phương tiện hiện tại, lịch sử hợp đồng).
- `PUT /api/v1/admin/tenants/{id}`
  - **Quyền:** `hasRole('MANAGER')`
  - **Body (`UpdateTenantRequest`):** `fullName`, `phone`, `permanentAddress`, `dateOfBirth`, `emergencyContactName`, `emergencyContactPhone`.
  - **Response:** `SingleResponse<TenantDetailResponse>`

### 5.3. Nhóm API Quản lý Phương tiện (Vehicles)
- **Admin Endpoints:**
  - `GET /api/v1/admin/vehicles`
    - **Params:** `page`, `size`, `search` (biển số, tên khách, mã thẻ, hãng xe), `status` (`ALL`, `PENDING`, `ACTIVE`, `REVOKED`).
    - **Response:** `PageResponse<VehicleResponse>`
  - `POST /api/v1/admin/vehicles`
    - **Body (`CreateVehicleAdminRequest`):** `tenantId`, `licensePlate`, `vehicleType`, `brand`, `color`, `cardCode` (optional), `registrationFrontUrl` (optional), `registrationBackUrl` (optional).
    - **Response:** `SingleResponse<VehicleResponse>` (HTTP 201)
  - `PUT /api/v1/admin/vehicles/{id}`
    - **Body (`UpdateVehicleRequest`):** `licensePlate`, `vehicleType`, `brand`, `color`.
    - **Response:** `SingleResponse<VehicleResponse>`
  - `PATCH /api/v1/admin/vehicles/{id}/issue-card`
    - **Body (`IssueCardRequest`):** `cardCode` (String, required, unique).
    - **Response:** `SingleResponse<VehicleResponse>` (đổi status sang `ACTIVE`).
  - `PATCH /api/v1/admin/vehicles/{id}/revoke-card`
    - **Response:** `SingleResponse<VehicleResponse>` (đổi status sang `REVOKED`, xóa hoặc giữ cardCode để lưu vết).
  - `DELETE /api/v1/admin/vehicles/{id}`
    - **Response:** `SuccessResponse` (Soft Delete).
- **Tenant Portal Endpoints:**
  - `GET /api/v1/tenant/vehicles/me`
    - **Quyền:** `hasRole('TENANT')`
    - **Response:** `SingleResponse<VehicleResponse>` (hoặc null nếu chưa có xe).
  - `POST /api/v1/tenant/vehicles`
    - **Quyền:** `hasRole('TENANT')`
    - **Consumes:** `multipart/form-data`
    - **Parts:** `licensePlate`, `vehicleType`, `brand`, `color`, file `frontImage` (required), file `backImage` (required).
    - **Xử lý:** Kiểm tra khách chưa có xe hoạt động (`quota == 0`), upload 2 ảnh lên OCI, tạo xe với status `PENDING`.
    - **Response:** `SingleResponse<VehicleResponse>` (HTTP 201)

### 5.4. Nhóm API Quản lý Hợp đồng & Minh chứng (Contracts)
- **Admin Endpoints:**
  - `GET /api/v1/admin/contracts`
    - **Params:** `page`, `size`, `search` (mã hợp đồng, tên khách, phòng/giường), `status` (ContractStatus, optional).
    - **Response:** `PageResponse<ContractSummaryResponse>`
  - `GET /api/v1/admin/contracts/{id}`
    - **Response:** `SingleResponse<ContractDetailResponse>` (thông tin hợp đồng + danh sách `ContractDocumentResponse`).
  - `POST /api/v1/admin/contracts/{id}/renew`
    - **Consumes:** `multipart/form-data` hoặc JSON
    - **Params/Parts:** `newEndDate` (LocalDate, required, > oldEndDate), optional files `annexDocuments`.
    - **Xử lý:** Cập nhật `endDate`, chuyển status về `ACTIVE`, tải các file đính kèm với category `RENEWAL_ANNEX`.
    - **Response:** `SingleResponse<ContractDetailResponse>`
  - `POST /api/v1/admin/contracts/{id}/terminate`
    - **Response:** `SingleResponse<ContractDetailResponse>` (Thực hiện Checkout 4-in-1 Transaction).
  - `POST /api/v1/admin/contracts/{id}/documents`
    - **Consumes:** `multipart/form-data`
    - **Parts:** `category` (ContractDocumentCategory), `files` (List<MultipartFile>).
    - **Response:** `SingleResponse<List<ContractDocumentResponse>>`
  - `DELETE /api/v1/admin/contracts/{id}/documents/{documentId}`
    - **Response:** `SuccessResponse` (xóa tài liệu khỏi hợp đồng và OCI).
- **Tenant Portal Endpoints:**
  - `GET /api/v1/tenant/contracts/me`
    - **Quyền:** `hasRole('TENANT')`
    - **Response:** `SingleResponse<ContractDetailResponse>` (hợp đồng đang hiệu lực của khách kèm các file minh chứng scan để xem trực tiếp).

---

## 6. Lưu trữ OCI Object Storage Architecture

### 6.1. Interface thiết kế
```java
public interface StorageService {
  String uploadFile(MultipartFile file, String directory);
  void deleteFile(String fileUrl);
}
```

### 6.2. Cấu hình linh hoạt qua biến môi trường
- `application.yml`:
  ```yaml
  oci:
    storage:
      enabled: ${OCI_STORAGE_ENABLED:false}
      tenancy-id: ${OCI_TENANCY_ID:}
      user-id: ${OCI_USER_ID:}
      fingerprint: ${OCI_FINGERPRINT:}
      private-key-path: ${OCI_PRIVATE_KEY_PATH:}
      region: ${OCI_REGION:ap-singapore-1}
      namespace: ${OCI_NAMESPACE:}
      bucket-name: ${OCI_BUCKET_NAME:dormitory-storage}
  ```
- **Fallback an toàn:** Nếu `OCI_STORAGE_ENABLED=false` (hoặc thiếu key), hệ thống tự động kích hoạt `LocalStorageService` (hoặc mock URL) lưu tệp vào thư mục tạm cục bộ (`uploads/`), giúp lập trình viên và test suite có thể chạy kiểm thử bình thường mà không cần chờ tài khoản OCI thực tế. Khi người dùng bổ sung credential vào `.env`, hệ thống tự động chuyển sang OCI thật.

---

## 7. Kế hoạch Kiểm thử & Báo cáo chất lượng (Testing Strategy)

1. **Unit Tests (Service Layer):**
   - `OnboardingServiceTest`: Kiểm tra thành công tạo 4 thực thể; kiểm tra các ca lỗi: trùng CCCD, trùng email, phòng sai giới tính (`CANNOT_ASSIGN_GENDER_MISMATCH`), giường không trống (`BED_NOT_AVAILABLE`).
   - `TenantServiceTest`: Lọc danh sách, tìm kiếm, cập nhật hồ sơ cá nhân.
   - `VehicleServiceTest`: Đăng ký xe, chặn vượt hạn mức 1 xe (`VEHICLE_QUOTA_EXCEEDED`), cấp thẻ từ, thu hồi thẻ, xóa mềm.
   - `ContractServiceTest`: Gia hạn hợp đồng, upload tài liệu, thanh lý hợp đồng (kiểm tra đủ 4 hành động đồng bộ).
2. **Integration Tests (MockMvc / WebMvc Layer):**
   - `OnboardingControllerTest`: Test endpoint `/api/v1/admin/onboarding`.
   - `TenantControllerTest`: Test CRUD và bảo mật quyền `MANAGER`.
   - `VehicleControllerTest`: Test quyền `MANAGER` và quyền `TENANT`.
   - `ContractControllerTest`: Test upload multi-file, renew, terminate.
3. **Mục tiêu Coverage:** Đạt tối thiểu 80% trên toàn bộ các package mới.
