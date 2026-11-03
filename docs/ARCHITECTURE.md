# Project Architecture — `dormitory-management` (KTX Hub / PDMS)

Tài liệu thiết kế kiến trúc kỹ thuật chuẩn hóa cho hệ thống **Quản lý Ký túc xá Tư nhân & Sleepbox (KTX Hub / PDMS)**, xây dựng trên nền tảng Java 25 và Spring Boot 4.x.

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
│   ├── Conceptual_ERD.md           # Mô hình thực thể khái niệm (14 thực thể, quan hệ)
│   ├── Logical-ERD.md              # Lược đồ logic chi tiết CSDL (12 bảng, kiểu dữ liệu, SQL View)
│   ├── Response-ErrorHandling-Architecutre.md # Chuẩn hóa Envelope Response & Exception
│   └── SRS.md                      # Đặc tả yêu cầu phần mềm v2.1 (BR-01..BR-11, FR-01..FR-27)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── uit/
│   │   │           └── dormmanagement/
│               ├── configs/        # Cấu hình Spring Beans (Security, CORS, Jackson, Redis, Scalar/OpenAPI)
│               ├── constants/      # Hằng số hệ thống, regex CCCD/biển số, mã lỗi
│               ├── controllers/    # REST API endpoints, @Valid DTO, định tuyến mỏng (AbstractBaseController)
│               ├── dtos/           # Data Transfer Objects (chuẩn hóa request / response)
│               │   ├── request/    # DTOs nhận dữ liệu đầu vào (auth, room, tenant, contract, ...)
│               │   └── response/   # DTOs trả về dữ liệu đầu ra
│               │       ├── base/   # Envelope mẫu: AbstractBaseResponse, SingleResponse, SuccessResponse, PageResponse, ErrorResponse, DetailedErrorResponse
│               │       ├── ResponseFactory.java # Component tạo Response chuẩn
│               │       └── (auth, room, tenant, contract, invoice, payment, expense, ticket, violation, vehicle, report)
│   │   │               ├── entities/       # JPA Entities ánh xạ 1:1 với 12 bảng PostgreSQL
│   │   │               ├── enums/          # Domain enumerations (GenderPolicy, RoomStatus, BedStatus, ...)
│   │   │               ├── exceptions/     # Custom domain exceptions, AppErrorCode, GlobalExceptionHandler
│   │   │               ├── mappers/        # MapStruct mappers chuyển đổi Entity <-> DTO
│   │   │               ├── repositories/   # Spring Data JPA interfaces (UUID làm PK, custom queries, locking)
│   │   │               ├── schedulers/     # @Scheduled cron jobs (Phát hành HĐ ngày 25, quét HĐ hết hạn)
│   │   │               ├── security/       # JWT filter, UserPrincipal, RBAC evaluators, SecurityUtils
│   │   │               ├── services/       # Interfaces & Implementation nghiệp vụ lõi (@Transactional)
│   │   │               ├── utils/          # Helper tĩnh (VietQR payload builder, format số tiền, date math)
│   │   │               └── DormitoryManagementApplication.java
│   │   └── resources/
│   │       ├── db/
│   │       │   └── migration/              # Flyway SQL migrations (V1__init_schema.sql, V2__seed_amenities.sql)
│   │       ├── static/
│   │       ├── application.yaml            # Cấu hình cơ sở dùng chung
│   │       ├── application-dev.yaml        # Cấu hình môi trường dev (PostgreSQL & Redis local)
│   │       └── application-prod.yaml       # Cấu hình môi trường production
│   └── test/
│       └── java/
│           └── com/
│               └── uit/
│                   └── dormmanagement/     # Unit tests, Slice tests (@WebMvcTest, @DataJpaTest), Integration tests
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
[Desktop Web (Manager) | Mobile Web/App (Tenant) | Mobile App (Technician)]
                        │ (JSON DTOs qua HTTPS)
                        ▼
                  controllers/    ──(Nhận @Valid Request DTO, trích xuất UserPrincipal)──►
                        │
                        ▼
                   services/      ──(@Transactional, kiểm tra BR-01..BR-11, tính Lợi nhuận)──►
                        │                                          ▲
                        ▼                                          │ (sử dụng)
                 repositories/    ◄──(ánh xạ qua mappers/)─────────┴──► entities/ & enums/
                        │
                        ▼
        [PostgreSQL 15+ (12 Bảng CSDL) & Redis (Cache/OTP)]
```

### Quy tắc Chi tiết Từng Gói (Package Responsibilities)

#### 1. `controllers/`:
- Giữ Controller luôn mỏng (**Thin Controller**): Chỉ đảm nhận định tuyến HTTP, đón nhận `@Valid` DTO, trích xuất thông tin người dùng từ Security Context và điều hướng tới Service.
- Kế thừa `AbstractBaseController` hoặc inject `ResponseFactory` để trả về phản hồi chuẩn Envelope: `SingleResponse<T>`, `SuccessResponse`, hoặc `SingleResponse<PageResponse<T>>`.
- **CẤM:** Tuyệt đối không inject trực tiếp `repositories/` vào `controllers/`.
- **CẤM:** Tuyệt đối không nhận hoặc trả về JPA `entities/` trong các hàm của Controller; luôn sử dụng `dtos/`.

#### 2. `dtos/`:
- Tổ chức phân tách thành 2 nhánh chính:
  - `dtos/request/`: Chứa các Request DTOs đại diện cho dữ liệu đầu vào từ Client, nhóm theo từng domain (`auth`, `room`, `tenant`, `contract`, `invoice`, `payment`, `expense`, `ticket`, `violation`, `vehicle`, `report`).
  - `dtos/response/`: Chứa các Response DTOs trả về cho Client.
    - `dtos/response/base/`: Chứa các lớp Envelope Response chuẩn hóa theo `docs/Response-ErrorHandling-Architecutre.md` (`AbstractBaseResponse`, `SingleResponse<T>`, `SuccessResponse`, `PageResponse<T>`, `ErrorResponse`, `DetailedErrorResponse`).
    - `dtos/response/ResponseFactory.java`: Factory component tập trung để sinh các `ResponseEntity` chuẩn.
- Ưu tiên sử dụng Java `record` bất biến (Immutable Data Transfer Object).
- Khai báo đầy đủ các annotation xác thực Jakarta Validation: `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Positive`, `@Email`.
#### 3. `services/`:
- Đóng gói toàn bộ các quy tắc nghiệp vụ cốt lõi (Core Business Rules BR-01..BR-11):
  - Kiểm tra cách ly giới tính nghiêm ngặt (`BR-02`) trước khi gán phòng hoặc đổi phòng.
  - Quản lý trạng thái giường và mã vị trí duy nhất `slotKey` (`BR-01`).
  - Hạch toán Lợi nhuận ròng thực tế từ các hóa đơn chi phí cơ sở (`BR-03`).
  - Điều phối và sắp xếp thứ tự ưu tiên phiếu sự cố: Khẩn cấp > Cao > Bình thường (`BR-06`).
- Thao tác truy vấn dữ liệu đánh dấu `@Transactional(readOnly = true)`.
- Thao tác thay đổi trạng thái (tạo hợp đồng, gạch nợ, thanh lý phòng) bắt buộc đánh dấu `@Transactional`.

#### 4. `repositories/`:
- Kế thừa `JpaRepository<Entity, UUID>` từ Spring Data JPA.
- Áp dụng `@Lock(LockModeType.PESSIMISTIC_WRITE)` khi cần khóa dòng để xử lý tranh chấp (ví dụ: gán vị trí giường, duyệt thanh toán hóa đơn).
- Khắc phục triệt để vấn đề N+1 query bằng `@EntityGraph` hoặc các câu lệnh JPQL `JOIN FETCH`.

#### 5. `entities/`:
- Ánh xạ chính xác 1:1 với 12 bảng trong `docs/Logical-ERD.md`:
  1. `User` (`users`)
  2. `Tenant` (`tenants`)
  3. `StaffProfile` (`staff_profiles`)
  4. `Room` (`rooms`)
  5. `Bed` (`beds`)
  6. `Amenity` (`amenities`)
  7. `RoomAmenity` (`room_amenities`)
  8. `Contract` (`contracts`)
  9. `Invoice` (`invoices`)
  10. `PaymentTransaction` (`payment_transactions`)
  11. `FacilityExpense` (`facility_expenses`)
  12. `MaintenanceTicket` (`maintenance_tickets`)
  13. `TicketReplacementPart` (`ticket_replacement_parts`)
  14. `Violation` (`violations`)
  15. `Vehicle` (`vehicles`)
- **Khóa chính:** Toàn bộ thực thể dùng `UUID id` với `@GeneratedValue(strategy = GenerationType.UUID)` hoặc tự sinh qua CSDL.
- **Tiền tệ:** Thuộc tính giá tiền luôn dùng `BigDecimal` trong Java và `numeric(12,2)` trong PostgreSQL.
- **Quan hệ:** Mọi liên kết `@ManyToOne` và `@OneToOne` bắt buộc dùng `fetch = FetchType.LAZY`.
- **Soft Delete:** Áp dụng `@SQLRestriction("deleted_at IS NULL")` trên các thực thể có cờ `deleted_at`.

#### 6. `enums/`:
- Toàn bộ giá trị hằng số Enum và ràng buộc `CHECK` trong CSDL được **chuẩn hóa 100% bằng tiếng Anh** (chỉ có giao diện người dùng hiển thị tiếng Việt tương ứng):
  - `GenderPolicy` (`MALE`, `FEMALE`) — *UI hiển thị: Nam, Nữ*
  - `RoomType` (`FOUR_BEDS`, `SIX_BEDS`, `EIGHT_BEDS`, `SLEEPBOX`) — *UI: 4 giường, 6 giường, 8 giường, Sleepbox*
  - `RoomStatus` (`AVAILABLE`, `OCCUPIED`, `MAINTENANCE`) — *UI: Trống, Đang ở, Bảo trì*
  - `BedStatus` (`AVAILABLE`, `OCCUPIED`, `MAINTENANCE`) — *UI: Sẵn sàng, Đang thuê, Bảo trì*
  - `UserRole` (`MANAGER`, `TECHNICIAN`, `TENANT`) — *UI: Quản lý, Kỹ thuật viên, Khách thuê*
  - `UserStatus` (`ACTIVE`, `INACTIVE`, `LOCKED`) — *UI: Hoạt động, Tạm khóa, Đã khóa*
  - `ResidentStatus` (`ACTIVE`, `CHECKED_OUT`, `RESERVED`) — *UI: Đang ở, Đã trả phòng, Bảo lưu*
  - `ContractStatus` (`ACTIVE`, `EXPIRING_SOON`, `TERMINATED`, `CANCELLED`) — *UI: Hiệu lực, Sắp hết hạn, Đã thanh lý, Đã hủy*
  - `InvoiceStatus` (`PAID`, `PENDING_CONFIRMATION`, `UNPAID`) — *UI: Đã thu, Chờ xác nhận, Nợ*
  - `InvoiceKind` (`ALL_INCLUSIVE_RENT`, `OTHER`) — *UI: Tiền phòng trọn gói, Phụ phí phát sinh*
  - `PaymentMethod` (`VIETQR`, `CASH`, `BANK_TRANSFER`) — *UI: VietQR Napas247, Tiền mặt, Chuyển khoản*
  - `ExpenseType` (`ELECTRICITY`, `WATER`, `INTERNET`, `OTHER`) — *UI: Điện EVN, Nước Sawaco, Internet cáp quang, Khác*
  - `TicketCategory` (`PLUMBING`, `AIR_CONDITIONING`, `ELECTRICAL`, `FURNITURE`, `LOCKS`, `OTHER`) — *UI: Điện nước, Điều hòa, Điện, Nội thất, Khóa cửa, Khác*
  - `TicketPriority` (`URGENT`, `HIGH`, `NORMAL`) — *UI: Khẩn cấp (Rank 0), Cao (Rank 1), Bình thường (Rank 2)*
  - `TicketStatus` (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CANCELLED`) — *UI: Mới giao, Đang xử lý, Hoàn thành, Đã hủy*
  - `ViolationType` (`LATE_RETURN`, `NOISE`, `HYGIENE`, `OUTSIDE_GUEST`, `SMOKING_FIRE`, `OTHER`) — *UI: Về muộn, Gây ồn, Vệ sinh, Khách ngoài, Hút thuốc/cháy nổ, Khác*
  - `ViolationLevel` (`REMINDER`, `WARNING`, `FINE`) — *UI: Nhắc nhở, Cảnh cáo, Phạt tiền*
  - `VehicleType` (`SCOOTER`, `MANUAL_MOTORCYCLE`, `ELECTRIC_MOTORBIKE`, `ELECTRIC_BICYCLE`, `BICYCLE`) — *UI: Xe tay ga, Xe số, Xe máy điện, Xe đạp điện, Xe đạp*
  - `VehicleStatus` (`PENDING`, `ACTIVE`, `REVOKED`) — *UI: Chưa cấp thẻ, Đã cấp thẻ, Đã thu hồi*
#### 7. `mappers/`:
- Sử dụng MapStruct hoặc Component mappers để ánh xạ giữa Entity và DTOs.
- Tách bạch hoàn toàn lớp biểu diễn (DTO) khỏi lớp dữ liệu bền vững (Entity).

#### 8. `schedulers/`:
- `MonthlyBillingScheduler`: Tự động chạy vào lúc **00:00 ngày 25 hàng tháng** để phát hành hóa đơn tiền phòng trọn gói cho toàn bộ các hợp đồng có trạng thái `Hiệu lực` (`BR-05`).
- `ContractExpirationScheduler`: Quét các hợp đồng còn hạn dưới 30 ngày để chuyển trạng thái sang `Sắp hết hạn` và phát thông báo gia hạn (`FR-12`).

#### 9. `security/`:
- Triển khai xác thực JWT không trạng thái (**Stateless JWT Authentication**): `JwtAuthenticationFilter`, `JwtService`.
- Băm mật khẩu bằng thuật toán an toàn `Argon2id` hoặc `Bcrypt` (cost factor >= 12).
- Phân quyền tinh giản theo 3 vai trò: `MANAGER`, `TECHNICIAN`, `TENANT`.
- Tự động khóa tài khoản 15 phút nếu đăng nhập sai quá 5 lần liên tiếp.

#### 10. `exceptions/`:
- Xử lý ngoại lệ tập trung qua `GlobalExceptionHandler` (`@RestControllerAdvice`).
- Mọi exception nghiệp vụ kế thừa từ `BaseAppException` kèm mã lỗi chuẩn `AppErrorCode`.

---

## 3. Chiến lược Migration Cơ sở Dữ liệu (`db/migration/`)

Hệ thống quản lý phiên bản cơ sở dữ liệu bằng **Flyway**:
- Quy tắc đặt tên file: `V{version}__{snake_case_description}.sql`
  - `V1__init_dorm_management_schema.sql`: Khởi tạo 12 bảng PostgreSQL, các ràng buộc `CHECK`, khóa ngoại `ON DELETE RESTRICT`, chỉ mục B-tree và SQL View tính Lợi nhuận ròng.
  - `V2__seed_master_amenities.sql`: Khởi tạo danh mục tiện nghi phòng mặc định (Điều hòa, Tủ lạnh, Bình nóng lạnh, Bàn học...).
  - `V3__seed_initial_admin_account.sql`: Tạo tài khoản quản trị viên mặc định (`MANAGER`) với mật khẩu băm.

**Quy tắc Dữ liệu Seed:**
- **CHO PHÉP trong Flyway:** Dữ liệu danh mục dùng chung của hệ thống (`amenities`, tài khoản bootstrap admin đầu tiên).
- **CẤM trong Flyway:** Không lưu dữ liệu mock, giao dịch thử nghiệm hay thông tin cá nhân khách thuê vào migration chính (sử dụng profile `dev` hoặc bộ nạp dữ liệu riêng cho môi trường thử nghiệm).

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

Toàn bộ hệ thống tuân thủ cấu trúc của `docs/Response-ErrorHandling-Architecutre.md`:
- Đóng gói dữ liệu đầu ra qua Envelope Pattern:
  - `SingleResponse<T>`: Chứa mã `httpStatus`, thông điệp `message` và payload `data`.
  - `SuccessResponse`: Cho các thao tác Command không có payload trả về.
  - `PageResponse<T>`: Chuẩn hóa phân trang (`items`, `page`, `size`, `totalElements`, `totalPages`, `last`).
  - `ErrorResponse`: Phản hồi lỗi chi tiết chứa `errorCode`, `message`, `timestamp`, `path`.

---

## 6. Tài liệu Hóa API Hiện Đại với Scalar (Scalar API Reference)

Hệ thống sử dụng **Scalar** thay thế cho giao diện Swagger truyền thống:
- **Thư viện tích hợp:** `org.springdoc:springdoc-openapi-starter-webmvc-scalar` (tương thích Spring Boot 4.x và OpenAPI 3.1).
- **Đường dẫn OpenAPI JSON:** `/v3/api-docs`
- **Giao diện API Reference tương tác:** `/scalar`
- **Ưu điểm:** Giao diện người dùng hiện đại, tối ưu tra cứu DTOs, hỗ trợ dark/light theme, tích hợp client kiểm thử API mượt mà và trực quan hơn.
