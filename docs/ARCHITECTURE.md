# Project Architecture — `dormitory-management` (KTX Hub / PDMS)

Tài liệu thiết kế kiến trúc kỹ thuật chuẩn hóa cho hệ thống **Quản lý Ký túc xá Tư nhân & Sleepbox (KTX Hub / PDMS)**, xây dựng trên nền tảng **Java 25** và **Spring Boot 4.1.2**.

---

## 1. Cấu trúc Thư mục Toàn cục (Directory Structure)

```text
dormitory-management/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── docker/
│   ├── .dockerignore               # Loại bỏ file thừa khi build Docker image
│   ├── Dockerfile                  # Multi-stage Dockerfile (Eclipse Temurin JDK 25)
│   └── docker-compose.yml          # Dịch vụ hạ tầng cục bộ (PostgreSQL 17, Redis 8)
├── docs/
│   ├── ARCHITECTURE.md             # Tài liệu thiết kế kiến trúc & ranh giới package (file này)
│   ├── Authentication-Workflow.md  # Luồng JWT, Redis session/OTP và khôi phục mật khẩu
│   ├── Conceptual_ERD.md           # Mô hình thực thể khái niệm (v3.5 FINAL — 18 thực thể)
│   ├── Configuration-Profile-Architecture.md # Cấu hình profile Spring & biến môi trường
│   ├── Logical-ERD.md              # Lược đồ logic chi tiết CSDL (v3.6 FINAL — 20 bảng PostgreSQL)
│   ├── SRS.md                      # Đặc tả yêu cầu phần mềm v3.4.1 FINAL (BR-01..BR-11, FR-01..FR-27)
│   └── audits/                     # Kiểm kê hợp đồng API, đánh giá code và tương thích prototype
├── private-storage/                # Lưu trữ cục bộ riêng tư ảnh cà vẹt xe (Phase 2; OCI ở Phase 3)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── uit/
│   │   │           └── dormmanagement/
│   │   │               ├── configs/        # Cấu hình Spring Beans (OpenAPI, Redis, StorageConfig, WebMvcResourceConfig)
│   │   │               ├── constants/      # Hằng số Redis keys (RedisKeyConstants)
│   │   │               ├── controllers/    # REST API endpoints, @Valid DTO, định tuyến mỏng (AbstractBaseController, ScalarDocsController)
│   │   │               ├── dtos/           # Data Transfer Objects (Java record, request / response)
│   │   │               │   ├── request/    # DTOs đầu vào (auth, room, tenant, onboarding, contract, vehicle, ...)
│   │   │               │   └── response/   # DTOs đầu ra & ResponseFactory.java
│   │   │               │       └── base/   # Envelope chuẩn: AbstractBaseResponse, SingleResponse, SuccessResponse, PageResponse, ErrorResponse, DetailedErrorResponse
│   │   │               ├── entities/       # 20 JPA Entities + RoomAmenityId (khớp 1:1 V1__clean_baseline.sql)
│   │   │               ├── enums/          # 21 Domain enumerations (GenderPolicy, RoomType, BedStatus, VehicleStatus, ...)
│   │   │               ├── exceptions/     # BaseAppException, AppErrorCode, AppExceptionHandler, DataIntegrityViolationClassifier, error/*ErrorCode
│   │   │               ├── mappers/        # Package mapper (dự phòng khi cần tách chuyển đổi phức tạp)
│   │   │               ├── repositories/   # Spring Data JPA interfaces (UUID PK, composite/text key ngoại lệ)
│   │   │               ├── schedulers/     # Package @Scheduled (chờ tác vụ hợp đồng Phase 3)
│   │   │               ├── security/       # JWT filter, CustomUserDetailsService, UserPrincipal, SecurityConfig, EntryPoint/AccessDeniedHandler
│   │   │               ├── services/       # Interfaces & Implementation nghiệp vụ lõi (@Transactional)
│   │   │               ├── utils/          # Helper tĩnh (LogMaskingUtils)
│   │   │               └── DormitoryManagementApplication.java
│   │   └── resources/
│   │       ├── db/
│   │       │   └── migration/              # Flyway SQL baseline duy nhất (V1__clean_baseline.sql)
│   │       ├── static/
│   │       ├── application.yml             # Cấu hình cơ sở dùng chung
│   │       ├── application-dev.yml         # Cấu hình môi trường dev (PostgreSQL & Redis local)
│   │       └── application-prod.yml        # Cấu hình môi trường production
│   └── test/
│       └── java/
│           └── com/
│               └── uit/
│                   └── dormmanagement/     # Unit tests, Slice tests, Testcontainers PostgreSQL/Redis & E2E tests
├── .env.example                            # Biến môi trường mẫu cho hạ tầng Docker
├── .gitattributes
├── .gitignore
├── AGENT.md                                # Hướng dẫn nghiệp vụ & quy tắc vận hành cho AI Agent
├── Makefile                                # Các lệnh tự động hóa (up, down, run, build, format, test)
├── mvnw                                    # Maven Wrapper cho Linux/macOS
├── mvnw.cmd                                # Maven Wrapper cho Windows
└── pom.xml                                 # Quản lý dependencies & plugins dự án Maven
```

---

## 2. Ranh giới Phân tầng Kỹ thuật (Technical-Layered Boundaries)

Dự án áp dụng kiến trúc phân tầng kỹ thuật nghiêm ngặt (**Technical-Layered Architecture**) dưới package `com.uit.dormmanagement`:

```text
[Desktop Web (Manager) | Mobile Web (Tenant / Technician)]
                        │ (JSON DTOs & Multipart qua HTTPS)
                        ▼
                  controllers/    ──(Nhận @Valid Request DTO, trích xuất UserPrincipal)──►
                        │
                        ▼
                   services/      ──(@Transactional, kiểm tra BR-01..BR-11, lưu trữ chứng từ)──►
                        │                                          ▲
                        ▼                                          │ (sử dụng)
                 repositories/    ◄────────────────────────────────┴──► entities/ & enums/
                        │
                        ▼
    [PostgreSQL 17 (20 Bảng CSDL) | Redis 8 (Session/OTP/Rate Limit) | private-storage/vehicles]
```

### Quy tắc Chi tiết Từng Gói (Package Responsibilities)

#### 1. `controllers/`:
- Giữ Controller luôn mỏng (**Thin Controller**): Chỉ đảm nhận định tuyến HTTP, đón nhận `@Valid` DTO, trích xuất thông tin người dùng từ Security Context và điều hướng tới Service.
- Kế thừa `AbstractBaseController` hoặc inject `ResponseFactory` để trả về phản hồi chuẩn Envelope: `SingleResponse<T>`, `SuccessResponse`, hoặc `SingleResponse<PageResponse<T>>`.
- **CẤM:** Tuyệt đối không inject trực tiếp `repositories/` vào `controllers/`.
- **CẤM:** Tuyệt đối không nhận hoặc trả về JPA `entities/` trong các hàm của Controller; luôn sử dụng `dtos/`.

#### 2. `dtos/`:
- Tổ chức phân tách thành 2 nhánh chính:
  - `dtos/request/`: Chứa các Request DTOs đại diện cho dữ liệu đầu vào từ Client, nhóm theo từng domain (`auth`, `room`, `tenant`, `onboarding`, `contract`, `vehicle`, ...).
  - `dtos/response/`: Chứa các Response DTOs trả về cho Client.
    - `dtos/response/base/`: Chứa các lớp Envelope Response chuẩn hóa (`AbstractBaseResponse`, `SingleResponse<T>`, `SuccessResponse`, `PageResponse<T>`, `ErrorResponse`, `DetailedErrorResponse`).
    - `dtos/response/ResponseFactory.java`: Factory component tập trung để sinh các `ResponseEntity` chuẩn.
- Ưu tiên sử dụng Java `record` bất biến (Immutable Data Transfer Object).
- Khai báo đầy đủ các annotation xác thực Jakarta Validation (`@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Positive`, `@Email`, `@PositiveOrZero`).
#### 3. `services/`:
- Đóng gói toàn bộ các quy tắc nghiệp vụ cốt lõi (Core Business Rules BR-01..BR-11):
  - Kiểm tra cách ly giới tính nghiêm ngặt (`BR-02`) trước khi gán giường hoặc cập nhật phòng/hồ sơ khách thuê.
  - Quản lý trạng thái giường, `room_code_registry` (`used_in_business`) và mã vị trí duy nhất `slotKey` (`BR-01`).
  - Xử lý đăng ký xe, cấp/thu thẻ từ, yêu cầu đổi xe (khóa bi-quan `Vehicle` $\rightarrow$ `VehicleChangeRequest`) và lưu trữ ảnh cà vẹt qua private local storage (`LocalVehicleEvidenceStorage`); tích hợp **OCI Object Storage private bucket** (`OciObjectStorageServiceImpl`) thực hiện ở đầu Phase 3.
  - Phạm vi triển khai: Phase 2 bao gồm Auth/RBAC, Rooms/Beds/Amenities, Tenant Profile, Vehicles/Change Requests; Phase 3 xử lý Onboarding/Contracts; **FR-11** (chuyển phòng) đang tạm hoãn (`deferred`) và các phân hệ **4.6** (Chi phí & Lợi nhuận), **4.7** (Hóa đơn & Thu tiền), **4.8** (Sự cố, Vi phạm, Báo cáo & Thông báo) bị loại trừ khỏi phạm vi hiện tại.
- Thao tác truy vấn dữ liệu đánh dấu `@Transactional(readOnly = true)`.
- Thao tác thay đổi trạng thái bắt buộc đánh dấu `@Transactional`.

#### 4. `repositories/`:
- Kế thừa `JpaRepository<Entity, UUID>` cho 18 bảng dùng `UUID` khóa chính; ngoại lệ gồm `RoomAmenityRepository` (`RoomAmenityId`) và `RoomCodeRegistryRepository` (`String code`).
- Áp dụng `@Lock(LockModeType.PESSIMISTIC_WRITE)` và `saveAndFlush` khi cần khóa dòng hoặc bắt lỗi ràng buộc DB tại ranh giới service để tránh deadlock và phản hồi đúng HTTP `409`/`400`.
- Khắc phục triệt để vấn đề N+1 query bằng `@EntityGraph` hoặc các câu lệnh JPQL `JOIN FETCH`.

#### 5. `entities/`:
- Ánh xạ chính xác 1:1 với **20 bảng** trong `docs/Logical-ERD.md` (v3.6 FINAL) và `src/main/resources/db/migration/V1__clean_baseline.sql`:
  1. `User` (`users`)
  2. `Tenant` (`tenants`)
  3. `StaffProfile` (`staff_profiles`)
  4. `Room` (`rooms`)
  5. `Bed` (`beds`)
  6. `Amenity` (`amenities`)
  7. `RoomAmenity` (`room_amenities`, composite PK `RoomAmenityId`)
  8. `RoomCodeRegistry` (`room_code_registry`, natural PK `code`)
  9. `Contract` (`contracts`)
  10. `ContractRenewal` (`contract_renewals`)
  11. `ContractDocument` (`contract_documents`)
  12. `Invoice` (`invoices`)
  13. `PaymentTransaction` (`payment_transactions`)
  14. `FacilityExpense` (`facility_expenses`)
  15. `MaintenanceTicket` (`maintenance_tickets`)
  16. `TicketReplacementPart` (`ticket_replacement_parts`)
  17. `Violation` (`violations`)
  18. `Vehicle` (`vehicles`)
  19. `VehicleChangeRequest` (`vehicle_change_requests`)
  20. `Notification` (`notifications`)
- **Khóa chính:** 18 bảng dùng `UUID id`; ngoại lệ `room_amenities` (`@EmbeddedId RoomAmenityId`) và `room_code_registry` (`String code`).
- **Tiền tệ:** Thuộc tính giá tiền luôn dùng `BigDecimal` với `precision = 14, scale = 0` trong Java và `numeric(14,0)` trong PostgreSQL.
- **Thời gian & Ngày:** Dùng `Instant` ánh xạ `timestamptz` và `LocalDate` ánh xạ `date`.
- **Quan hệ:** Mọi liên kết `@ManyToOne` và `@OneToOne` bắt buộc dùng `fetch = FetchType.LAZY`.
- **Lưu trữ mềm / Bảo toàn lịch sử:** Bảng lịch sử dùng `ON DELETE RESTRICT` (không dùng `deleted_at`); riêng `Room` dùng `@SQLRestriction("archived_at IS NULL")` với cột `archived_at`.

#### 6. `enums/`:
- Toàn bộ **21 hằng số Enum** và ràng buộc `CHECK` trong CSDL được chuẩn hóa 100% bằng tiếng Anh khớp chính xác với mã nguồn `enums/*.java`:
  - `GenderPolicy` (`MALE`, `FEMALE`)
  - `RoomType` (`FOUR_BEDS`, `SIX_BEDS`, `EIGHT_BEDS`)
  - `BedStatus` (`AVAILABLE`, `OCCUPIED`, `MAINTENANCE`)
  - `UserRole` (`MANAGER`, `TECHNICIAN`, `TENANT`)
  - `UserStatus` (`ACTIVE`, `LOCKED`)
  - `ResidentStatus` (`ACTIVE`, `CHECKED_OUT`)
  - `ContractStatus` (`ACTIVE`, `LIQUIDATED`)
  - `ContractDocumentCategory` (`ORIGINAL_CONTRACT`, `RENEWAL_ANNEX`, `CHECKOUT_HANDOVER`, `OTHER`)
  - `InvoiceStatus` (`DEBT`, `PENDING_CONFIRMATION`, `PAID`)
  - `InvoiceKind` (`ALL_INCLUSIVE_RENT`, `OTHER`)
  - `PaymentMethod` (`CASH`, `VIETQR_TRANSFER`)
  - `ExpenseType` (`ELECTRICITY`, `WATER`, `INTERNET`, `SALARY`, `OTHER`)
  - `TicketCategory` (`ELECTRIC`, `WATER`, `INTERNET`, `FURNITURE`, `AMENITY`, `OTHER`)
  - `TicketPriority` (`URGENT`, `HIGH`, `NORMAL`)
  - `TicketStatus` (`UNASSIGNED`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`)
  - `ViolationType` (`LATE_RETURN`, `NOISE`, `HYGIENE`, `OUTSIDER`, `SMOKING_FIRE`, `OTHER`)
  - `ViolationLevel` (`REMINDER`, `WARNING`, `FINE`)
  - `VehicleType` (`SCOOTER`, `MANUAL_MOTORCYCLE`, `ELECTRIC_MOTORBIKE`, `ELECTRIC_BICYCLE`, `BICYCLE`)
  - `VehicleStatus` (`PENDING_CARD`, `ISSUED`, `REJECTED`, `REVOKED`, `REPLACED`, `CANCELLED`)
  - `VehicleChangeRequestStatus` (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`)
  - `NotificationEventType` (`INVOICE_ISSUED`, `INVOICE_DUE`, `INVOICE_OVERDUE`, `PAYMENT_CONFIRMED`, `PAYMENT_PROOF_REJECTED`, `VIOLATION_CREATED`, `VEHICLE_REGISTRATION_REJECTED`, `VEHICLE_CHANGE_APPROVED`, `VEHICLE_CHANGE_REJECTED`)
#### 7. `mappers/`:
- Giữ ranh giới chuyển đổi Entity <-> DTO tách bạch hoàn toàn khỏi tầng Repository và Controller (hiện tại DTO mapping được thực hiện tập trung tại Service hoặc thành phần trợ giúp).

#### 8. `schedulers/`:
- Package `schedulers/` hiện chưa kích hoạt tác vụ cron nào ở Phase 2 (dự phòng cho các job hợp đồng như `ContractStatusScheduler` ở Phase 3; các job hóa đơn/chi phí thuộc phân hệ 4.6–4.7 đang nằm ngoài phạm vi triển khai hiện tại).

#### 9. `security/`:
- Triển khai xác thực JWT không trạng thái kết hợp trạng thái phiên trên Redis (`JwtAuthenticationFilter`, `JwtService`, `CustomUserDetailsService`, `SecurityConfig`, `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler`).
- Quy tắc đăng nhập theo vai trò: `MANAGER` và `TECHNICIAN` đăng nhập bằng canonical email; `TENANT` đăng nhập bằng CCCD 12 chữ số (email Tenant chỉ dùng để liên hệ và nhận OTP khôi phục mật khẩu).
- Đổi mật khẩu khi đã đăng nhập (`POST /api/v1/auth/change-password`) xác thực qua `oldPassword` và xoay `sessionGeneration` trên Redis (không dùng OTP); quên mật khẩu (`forgot-password` $\rightarrow$ `verify-forgot-password-otp` $\rightarrow$ `reset-password`) dùng OTP 6 số hiệu lực 5 phút (`recoveryRequestId`, `resetToken`) lưu trên Redis.
- Băm mật khẩu bằng BCrypt; khóa tài khoản/tác nhân sau >5 lần đăng nhập sai trong 15 phút (`LoginAttemptServiceImpl`).
- `SecurityConfig` chặn truy cập tĩnh trực tiếp vào `/uploads/vehicles/**` (`denyAll()`) và mọi log xác thực/định danh đều được che nhạy cảm qua `LogMaskingUtils`.

#### 10. `exceptions/`:
- Xử lý ngoại lệ tập trung qua `AppExceptionHandler` (`@RestControllerAdvice`) phối hợp với `DataIntegrityViolationClassifier`.
- Mọi exception nghiệp vụ ném `BaseAppException` gắn với `AppErrorCode` cụ thể trong `exceptions/error/*ErrorCode.java` (`AuthErrorCode`, `RoomErrorCode`, `TenantErrorCode`, `VehicleErrorCode`, `ContractErrorCode`, `WebErrorCode`, `SystemErrorCode`).
- `DataIntegrityViolationClassifier` ánh xạ đúng các ràng buộc unique trong `V1__clean_baseline.sql` sang mã `409` domain tương ứng (hoặc `409 WEB_409` cho unique `23505` chưa ánh xạ), và giữ các lỗi `NOT NULL` (`23502`), `CHECK` (`23514`), FK chung (`23503`), tràn độ dài (`22xxx`), hoặc không phân loại được ở HTTP `400` (`WEB_400`).

---

## 3. Chiến lược Migration Cơ sở Dữ liệu (`db/migration/`)

Hệ thống quản lý phiên bản cơ sở dữ liệu bằng **Flyway**:
- Hiện trạng sử dụng file baseline sạch duy nhất `V1__clean_baseline.sql`: khởi tạo toàn bộ **20 bảng PostgreSQL**, kiểu `numeric(14,0)`, ràng buộc `CHECK`, khóa ngoại `ON DELETE RESTRICT`, các partial unique index (`uq_users_email_canonical`, `uq_rooms_number_canonical`, `uq_beds_slot_key_canonical`, `uq_contracts_active_bed`, `uq_contracts_active_tenant`, `uq_vehicles_issued_card_code`, `uq_vehicles_tenant_active_quota`, `uq_vehicle_change_requests_pending_tenant`), trigger đồng bộ, và dữ liệu seed danh mục `amenities` (7 tiện nghi chuẩn).
- Không sửa đổi `V1__clean_baseline.sql` khi chưa có yêu cầu migration chính thức; mọi dữ liệu kiểm thử được nạp riêng trong profile `dev` hoặc test fixtures.

---

## 4. Tích hợp Hạ tầng Docker (`docker/`)

1. **`docker/Dockerfile`:**
   - Multi-stage build tối ưu cho Java 25:
     - Stage 1: Build bằng Maven và Temurin JDK 25, giải nén Spring Boot Layertools (`dependencies`, `spring-boot-loader`, `application`).
     - Stage 2: Runtime image tinh gọn với `eclipse-temurin:25-jre`, chạy dưới non-root user (`appuser:appgroup`).
2. **`docker/docker-compose.yml`:**
   - Khởi chạy PostgreSQL 17 và Redis 8 cho môi trường phát triển local.
   - Thiết lập Healthcheck tự động và cấu hình biến môi trường qua file `.env`.

---

## 5. Chuẩn hóa Phản hồi & Xử lý Ngoại lệ

Toàn bộ hệ thống tuân thủ cấu trúc chuẩn hóa trực tiếp bởi `ResponseFactory`, `dtos/response/base/`, `AppExceptionHandler`, `DataIntegrityViolationClassifier` và `exceptions/error/*ErrorCode.java`:
- Đóng gói dữ liệu đầu ra qua Envelope Pattern:
  - `SingleResponse<T>`: Chứa mã `httpStatus`, thông điệp `message` và payload `data`.
  - `SuccessResponse`: Cho các thao tác Command không có payload trả về.
  - `PageResponse<T>`: Chuẩn hóa phân trang (`items`, `page`, `size`, `totalElements`, `totalPages`, `last`).
  - `ErrorResponse`: Phản hồi lỗi chuẩn chứa `httpStatus`, `errorCode`, `message`, `timestamp`, `path`.
  - `DetailedErrorResponse`: Bổ sung `items` (`Map<String, List<String>>`) cho lỗi xác thực đầu vào (`WEB_400_VAL`).

---

## 6. Tài liệu Hóa API Hiện Đại với Scalar (Scalar API Reference)

Hệ thống sử dụng **Scalar** thay thế cho giao diện Swagger truyền thống:
- **Thư viện tích hợp:** `org.springdoc:springdoc-openapi-starter-webmvc-scalar` `3.1.1` (tương thích Spring Boot 4.x và OpenAPI 3.1).
- **Đường dẫn OpenAPI JSON:** `/v3/api-docs`
- **Giao diện API Reference tương tác:** `/scalar` (kèm alias `/docs`)
- **Ưu điểm:** Giao diện người dùng hiện đại, tối ưu tra cứu DTOs, hỗ trợ dark/light theme, tích hợp client kiểm thử API mượt mà và trực quan hơn.
