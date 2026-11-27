# AGENT.md — KTX Hub / PDMS (`dormitory-management`)

Tài liệu hướng dẫn bắt buộc dành cho AI Agent khi tham gia khảo sát, thiết kế, lập trình backend, viết migration CSDL, tích hợp API hoặc kiểm thử trong repository `dormitory-management`.

---

## 1. Định danh & Bối cảnh Dự án (Project Identity & Context)

- **Tên dự án:** Hệ thống Quản lý Ký túc xá Tư nhân & Sleepbox (**KTX Hub / PDMS** - Private Dormitory Management System).
- **Phạm vi kỹ thuật:** Java Spring Boot Backend REST API.
- **Base Package:** `com.uit.dormmanagement`
- **Ngôn ngữ & Runtime:** Java 25, Spring Boot `4.1.2`, Maven Wrapper (`./mvnw`), Virtual Threads bật mặc định, Jackson TZ `Asia/Ho_Chi_Minh`.
- **Hệ quản trị CSDL & Cache:** PostgreSQL 16+ (20 bảng chuẩn hóa trong `V1__clean_baseline.sql`, `ddl-auto: validate`, `open-in-view: false`, UUID PK) kết hợp Redis (JWT session generation/rotation, refresh token registry, access token blacklist, giới hạn đăng nhập sai, OTP khôi phục mật khẩu — **không** tạo bảng session/OTP trong PostgreSQL).
- **Mô hình vận hành thực tế:**
  1. **01 Tòa nhà duy nhất (Single-facility):** Quản lý tập trung theo các tầng (`floor >= 1`), không có khái niệm chuỗi chi nhánh hay đa tòa nhà.
  2. **Quản lý theo vị trí giường riêng biệt (`Bed-level Tracking`):** Đơn vị cho thuê độc lập là từng giường/hộp ngủ (`G01, G02, ...`) bên trong phòng; giá thuê tiêu chuẩn dùng chung theo phòng (`rooms.base_price`) và khóa tại thời điểm ký hợp đồng/phụ lục.
  3. **Mô hình Thuê trọn gói (`All-inclusive Rental`):** Giá thuê phòng hàng tháng đã bao gồm toàn bộ điện, nước, internet, gửi xe. Tuyệt đối không đo công tơ phụ chia lẻ theo phòng.
  4. **Hạch toán Lợi nhuận ròng (ước tính):** Ban Quản lý nhập hóa đơn chi phí cơ sở và tổng lương nhân viên tự tính ngoài hệ thống để tính Lợi nhuận ròng (ước tính) = `Doanh thu thực thu theo kỳ hóa đơn - Chi phí vật tư phiếu sửa chữa hoàn thành - Chi phí điện nước internet tổng - Tổng lương nhân viên - Chi phí vận hành khác`.
  5. **Khách thuê đa đối tượng:** Người đi làm, chuyên gia trẻ, sinh viên, freelancer (loại bỏ toàn bộ các trường học vị/MSSV).
  6. **Phân định nền tảng thiết bị:**
     - **Quản lý / Chủ KTX (`MANAGER`):** Desktop Web Dashboard (quản trị phòng/giường, khách thuê, hợp đồng, hóa đơn, thu tiền, tài chính, nhân sự, xe, sự cố, vi phạm, KPI/biểu đồ).
     - **Kỹ thuật viên (`TECHNICIAN`):** Mobile/Tablet Web (xử lý sự cố theo thứ tự ưu tiên: Khẩn cấp > Cao > Bình thường, nghiệm thu vật tư).
     - **Khách thuê (`TENANT`):** Mobile Web (5 tab điều hướng: `Phòng`, `Hóa đơn`, `Phương tiện`, `Báo hỏng`, `Vi phạm` + biểu tượng chuông thông báo hệ thống).

---

## 2. Thứ bậc Nguồn Sự thật Tuyệt đối (Source of Truth Hierarchy)

Khi có bất kỳ mâu thuẫn nào giữa các quy tắc kỹ thuật chung và tài liệu dự án, **Agent BẮT BUỘC tuân thủ theo thứ tự ưu tiên sau**:

1. `docs/SRS.md` (**v3.4.1 — FINAL**, 11/11/2026 — Quy tắc nghiệp vụ BR-01..BR-11, Yêu cầu chức năng FR-01..FR-27).
2. `docs/Conceptual_ERD.md` (**v3.5 — FINAL**) **&** `docs/Logical-ERD.md` (**v3.6 — FINAL**) — 18 thực thể nghiệp vụ, 20 bảng PostgreSQL trong `src/main/resources/db/migration/V1__clean_baseline.sql`, kiểu dữ liệu `numeric(14,0)`, ràng buộc và giao dịch.
3. Kiến trúc chuẩn hóa Envelope Response & xử lý lỗi thực tế trong mã nguồn: `AppExceptionHandler` + `DataIntegrityViolationClassifier`, `ResponseFactory`, `dtos/response/base/`, và `exceptions/error/*ErrorCode.java`.
4. `AGENT.md` (Tài liệu này — kim chỉ nam vận hành và các ràng buộc bất biến) & `docs/ARCHITECTURE.md`.
5. Các quy chuẩn mã hóa chung (Google Java Format qua Spotless, Coding Standards, Clean Architecture).

---

## 3. Các Quy tắc Nghiệp vụ Bất biến Cốt lõi (Core Business Rules - BR)

Mọi dòng code, API endpoint, migration SQL và logic Service **BẮT BUỘC** phải tuân thủ nghiêm ngặt 11 quy tắc sau:

### BR-01: Quản lý theo vị trí giường riêng biệt (Bed-level Tracking & Unique Slot Key)
- Mỗi phòng có sức chứa cố định **4, 6 hoặc 8 giường** (`RoomType`: `FOUR_BEDS`, `SIX_BEDS`, `EIGHT_BEDS`), đánh mã `G01, G02, ..., G0n`. Không có `RoomStatus` riêng; phòng lưu trữ qua cột `archived_at` (`@SQLRestriction("archived_at IS NULL")`).
- Mỗi giường dùng chung giá thuê tiêu chuẩn của phòng (`rooms.base_price`) và có trạng thái (`AVAILABLE` - Sẵn sàng, `OCCUPIED` - Đang thuê, `MAINTENANCE` - Bảo trì).
- Khóa vị trí giường là duy nhất toàn tòa nhà:
  $$\text{slotKey} = \text{roomNumber} + \text{"-"} + \text{bedCode} \quad (\text{Ví dụ: } \texttt{P.302-G02})$$
- Mã phòng được bảo vệ qua bảng `room_code_registry` (`used_in_business`): mã đã gắn với hợp đồng hoặc phiếu sự cố không được tái sử dụng; chỉ phòng tạo nhầm chưa phát sinh nghiệp vụ mới được xóa vật lý qua `/permanent` (hoặc `/incorrect`).
- **CẤM:** Không được phép tạo 2 hợp đồng `ACTIVE` trùng cùng một vị trí giường hoặc cùng một khách thuê tại cùng thời điểm (`uq_contracts_active_bed`, `uq_contracts_active_tenant`).

### BR-02: Cách ly giới tính nghiêm ngặt 100% (Strict Gender Isolation Policy)
- Mỗi phòng gắn cố định một chính sách giới tính duy nhất: `MALE` (`Nam`) hoặc `FEMALE` (`Nữ`). Tuyệt đối không có phòng hỗn hợp (`MIXED`).
- Khách thuê có giới tính xác định trên CCCD: `MALE` hoặc `FEMALE`.
- **Ràng buộc bất biến:** Khách chỉ được xếp hoặc chuyển vào phòng có `gender_policy == tenant.gender`.
- Khi cập nhật thông tin phòng, tuyệt đối **CẤM** đổi `gender_policy` hoặc `room_number` nếu phòng đang có ít nhất 01 giường `OCCUPIED` hoặc hợp đồng `ACTIVE`.

### BR-03: Giá thuê trọn gói & Quản lý Chi phí Tiện ích Cơ sở (All-inclusive Rental & Net Profit)
- Tiền phòng thu theo tháng trọn gói cố định theo giá khóa trên hợp đồng/phụ lục (đã gồm tiền phòng, điện sinh hoạt, nước sinh hoạt, wifi, vệ sinh khu vực chung, gửi xe).
- Ban Quản lý nhập các khoản chi phí phát sinh của cơ sở vào bảng `facility_expenses` (`ELECTRICITY`, `WATER`, `INTERNET`, `SALARY` — tối đa 1 khoản tổng lương nhân viên nhập thủ công mỗi tháng, `OTHER`).
- **Công thức tính Lợi nhuận ròng (ước tính) của kỳ:**
  $$\text{Lợi nhuận ròng (ước tính)} = \text{Doanh thu thực thu theo kỳ hóa đơn} - \text{Chi phí vật tư Ticket hoàn thành} - \sum \text{Chi phí cơ sở (gồm tổng lương)}$$
- **CẤM:** Tuyệt đối không tạo bảng công tơ phụ chia lẻ điện nước từng phòng hay từng ngày, và không tự tính bảng lương từng nhân viên.

### BR-04: Đặt cọc & Trả phòng giải phóng giường (Offline Deposit & Checkout)
- Mức cọc tiêu chuẩn bằng đúng **01 tháng tiền thuê giường**, thu trực tiếp bằng tiền mặt hoặc chuyển khoản cá nhân ngoài thực tế lúc ký hợp đồng giấy.
- Khách báo trước khi rời đi tối thiểu 15 ngày. Khi checkout, hai bên tự thỏa thuận đối soát cọc và hoàn trả ngoài thực tế.
- Hệ thống **tuyệt đối KHÔNG cần quản lý công thức tính toán hoàn cọc chi tiết**.
- Khi hoàn tất trả phòng: Quản lý thanh lý hợp đồng $\rightarrow$ Hợp đồng chuyển `LIQUIDATED` (`Đã thanh lý`), khách chuyển `CHECKED_OUT` (`Đã trả phòng`), giường tự động giải phóng về trạng thái `AVAILABLE` (`Sẵn sàng`), thẻ xe đang cấp chuyển `REVOKED` và yêu cầu đổi xe `PENDING` chuyển `CANCELLED`.

### BR-05: Chu kỳ phát hành hóa đơn cố định & Hạn nộp ngày 05 hàng tháng
- **Thời điểm phát hành:** Đúng **00:00 ngày 25 hàng tháng**, phát hành hàng loạt hóa đơn tiền phòng trọn gói cho kỳ tháng tiếp theo với trạng thái ban đầu là `DEBT` (`Nợ`), chuyển `PENDING_CONFIRMATION` (`Chờ xác nhận`) khi có minh chứng, và `PAID` (`Đã thu`) khi Manager xác nhận qua `CASH` hoặc `VIETQR_TRANSFER`.
- **Hạn thanh toán cố định (`due_on`):** Luôn là **ngày 05 của tháng tiếp theo** (ví dụ: kỳ tháng 11 phát hành ngày 25/10, `due_on` là `05/11/2026`).
- **Từ ngày 06 trở đi:** Hóa đơn chưa thanh toán tự động hiển thị quá hạn (chỉ báo quá hạn, không phải trạng thái thứ tư).
- **CẤM:** Tuyệt đối **KHÔNG tính phí phạt nộp trễ tiền**.

### BR-06: Quy trình xử lý sự cố & Thứ tự ưu tiên kỹ thuật (Ticket Prioritization)
- Phân cấp 3 mức độ ưu tiên:
  1. `URGENT` (`Khẩn cấp`, Rank 0): Nguy cơ chập cháy điện, vỡ ống nước ngập sàn, kẹt khóa cửa.
  2. `HIGH` (`Cao`, Rank 1): Hỏng máy lạnh, mất nguồn tủ lạnh chung.
  3. `NORMAL` (`Bình thường`, Rank 2): Quạt kêu, đèn chập chờn, kẹt hộc tủ.
- **Thuật toán sắp xếp công việc của Kỹ thuật viên:**
  $$\text{SortOrder} = (\text{isCompleted} ? 1 : 0) \times 10 + \text{priorityRank}$$
  *(Việc chưa hoàn thành luôn đứng trước; trong các việc chưa xong: Khẩn cấp $\rightarrow$ Cao $\rightarrow$ Bình thường).*
- Vòng đời `TicketStatus`: `UNASSIGNED` (`Chưa phân công`) $\rightarrow$ `ASSIGNED` (`Mới giao`) $\rightarrow$ `IN_PROGRESS` (`Đang xử lý`) $\rightarrow$ `COMPLETED` (`Hoàn thành` — ảnh nghiệm thu, ghi nhận linh kiện `ticket_replacement_parts`).

### BR-07: Hồ sơ vi phạm nội quy & Xử phạt (Violations)
- Hồ sơ vi phạm gắn trực tiếp với cá nhân khách thuê (`tenant_id`), **KHÔNG gắn với phòng (`room_id`)**.
- 6 loại vi phạm chuẩn (`ViolationType`): `LATE_RETURN`, `NOISE`, `HYGIENE`, `OUTSIDER`, `SMOKING_FIRE`, `OTHER`.
- 3 hình thức xử lý (`ViolationLevel`): `REMINDER`, `WARNING`, `FINE`.
- **CẤM:** Tiền phạt (nếu có) được xử lý thu trực tiếp ngoài thực tế; Quản lý chỉ nhập số tiền vào hệ thống để theo dõi lịch sử, **tuyệt đối KHÔNG gộp hay kết chuyển vào hóa đơn tiền phòng trên web**.

### BR-08: Toàn vẹn dữ liệu & Bảo toàn lịch sử nghiệp vụ
- Các bảng tài chính, hợp đồng và lịch sử vận hành (`contracts`, `contract_renewals`, `contract_documents`, `invoices`, `payment_transactions`, `facility_expenses`, `maintenance_tickets`, `ticket_replacement_parts`, `violations`, `vehicles`, `vehicle_change_requests`, `tenants`, `users`, `notifications`) thiết lập khóa ngoại `ON DELETE RESTRICT` và **không xóa vật lý dữ liệu lịch sử** (không dùng cột `deleted_at`).
- Riêng `rooms` sử dụng lưu trữ mềm bằng cột `archived_at` (`@SQLRestriction("archived_at IS NULL")`); chỉ được xóa vật lý phòng tạo nhầm chưa từng gắn dữ liệu nghiệp vụ cần bảo toàn (`/permanent` hoặc `/incorrect`).

### BR-09: Cấp phát tài khoản, định danh đăng nhập & Khôi phục mật khẩu
- Khách thuê không tự đăng ký tài khoản từ web ngoài. Khách đến xem phòng trực tiếp; Quản lý thực hiện Onboarding (FR-09) để tạo/tái sử dụng hồ sơ và tài khoản.
- **Loại bỏ triệt để các trường học vị sinh viên:** Tuyệt đối không tạo cột hay yêu cầu bắt buộc: `university`, `faculty`, `student_code`, `priority_type`.
- Trường định danh chuẩn hóa: *Họ tên, Giới tính, Số điện thoại (không yêu cầu duy nhất), CCCD (12 chữ số, duy nhất), Email (duy nhất toàn hệ thống theo lowercase/trim), Địa chỉ thường trú*.
- **Quy tắc đăng nhập & mật khẩu:**
  - `MANAGER` và `TECHNICIAN` đăng nhập bằng **canonical email**. `TENANT` đăng nhập bằng **CCCD 12 chữ số** (email của Tenant chỉ dùng để liên hệ và nhận OTP khôi phục mật khẩu, **không** dùng đăng nhập; số điện thoại không dùng đăng nhập).
  - **Change Password (đã đăng nhập):** xác thực mật khẩu hiện tại (`oldPassword`), **không dùng OTP**.
  - **Forgot Password (chưa đăng nhập):** OTP 6 chữ số (hiệu lực 5 phút) lưu trên Redis (kèm `recoveryRequestId`, `resetToken`), **không** tạo bảng OTP/Session trong PostgreSQL.
  - Quản lý phiên JWT qua Redis: `sessionGeneration` rotation, refresh token JTI registry, access token blacklist, khóa đăng nhập 15 phút sau >5 lần sai.

### BR-10: Quản lý phương tiện di chuyển & Thẻ gửi xe (Vehicles & Change Requests)
- **Vòng đời xe (`VehicleStatus` — 6 trạng thái):** `PENDING_CARD` $\rightarrow$ `ISSUED` (khi Manager nhập `card_code`, duy nhất giữa các xe `ISSUED`), hoặc `REJECTED` (từ chối kèm lý do bắt buộc), `CANCELLED` (hủy đơn chờ cấp thẻ), `REVOKED` (thu hồi khi checkout hoặc thu thẻ), `REPLACED` (xe cũ sau khi duyệt yêu cầu đổi xe). Tối đa 01 xe `PENDING_CARD`/`ISSUED` mỗi khách thuê đang cư trú (`ACTIVE`); biển số không ràng buộc duy nhất toàn cục.
- **Yêu cầu đổi xe (`VehicleChangeRequestStatus` — 4 trạng thái):** Khách thuê đang có xe `ISSUED` có thể tạo `PENDING` change request; khi Manager duyệt (`APPROVED`), hệ thống khóa `Vehicle` trước rồi `VehicleChangeRequest`, chuyển xe cũ sang `REPLACED` và tạo xe kế nhiệm `ISSUED` giữ nguyên `card_code`. Từ chối (`REJECTED`) giữ nguyên xe cũ; checkout hủy yêu cầu đang chờ (`CANCELLED`).
- Phí gửi xe bao trọn gói 100% trong tiền phòng; **hệ thống tuyệt đối không thu thêm bất kỳ khoản phí xe nào**.
- Ảnh cà vẹt xe lưu trong **OCI Object Storage private bucket** qua AWS SDK v2 S3 Compatibility API (`PrivateObjectStorage` / `S3PrivateObjectStorage`), chặn toàn bộ truy cập tĩnh `/uploads/**` (`denyAll()`), stream qua endpoint Backend kiểm tra quyền/chủ sở hữu (`AdminVehicleController`, `TenantVehicleController`, `AdminVehicleChangeRequestController`, `TenantVehicleChangeRequestController`).

### BR-11: Phân quyền quản trị tinh giản (Simplified RBAC & Exclusive Subtyping)
- Phân định rõ 3 vai trò qua `users.role`:
  - `MANAGER`: Sử dụng Desktop Web, toàn quyền quản trị phòng ốc, hợp đồng, tài chính, thẻ xe, nhân sự.
  - `TECHNICIAN`: Sử dụng Mobile App, chỉ tiếp nhận và xử lý phiếu báo hỏng kỹ thuật.
  - `TENANT`: Sử dụng Mobile Web/App, xem phòng, hóa đơn VietQR, đăng ký xe, gửi báo hỏng.
- **Phân rã danh tính độc quyền (Exclusive Subtyping):**
  - Tài khoản có `role = 'TENANT'` $\rightarrow$ Sở hữu duy nhất 1 bản ghi `tenants`, **KHÔNG** có bản ghi `staff_profiles`.
  - Tài khoản có `role IN ('MANAGER', 'TECHNICIAN')` $\rightarrow$ Sở hữu duy nhất 1 bản ghi `staff_profiles`, **KHÔNG** có bản ghi `tenants`.

---

## 4. Những điều Tuyệt đối NGOÀI PHẠM VI (Out-of-Scope & Negative Guardrails)

Agent **TUYỆT ĐỐI KHÔNG** tự ý thiết kế hoặc triển khai các tính năng sau:

1. **KHÔNG** hỗ trợ chuỗi nhiều tòa nhà hay đa cơ sở (loại bỏ bảng `buildings`, hệ thống chỉ quản lý 01 tòa nhà chia theo `floor`).
2. **KHÔNG** tạo bảng công tơ điện nước từng phòng (`meter_readings`) hoặc bảng phân bổ tiền lẻ (`proration_details`).
3. **KHÔNG** tích hợp cổng thanh toán quốc tế (Visa/Mastercard) hay trích nợ tự động (Auto-debit). Chỉ dùng mã VietQR Napas247 động và xác nhận tiền mặt.
4. **KHÔNG** tính phí phạt nộp trễ tiền phòng.
5. **KHÔNG** tích hợp phần cứng IoT (công tơ điện tử Wi-Fi, cổng xoay FaceID).
6. **KHÔNG** tạo cổng tìm phòng/đặt cọc trực tuyến công khai cho người ngoài. Khách luôn xem phòng offline trước khi Quản lý tạo hợp đồng.
7. **Ngoài phạm vi triển khai hiện tại (Scope Guardrails):** **FR-11** (chuyển phòng giữa kỳ) đang tạm hoãn (`deferred`); các phân hệ **4.6** (Chi phí cơ sở & Lợi nhuận ròng), **4.7** (Hóa đơn & Thu tiền), và **4.8** (Sự cố, Vi phạm, Báo cáo & Thông báo) bị **loại trừ khỏi phạm vi thực thi hiện tại** — chỉ giữ cấu trúc CSDL nền trong `V1__clean_baseline.sql` và không tự ý triển khai API/Service khi chưa có chỉ định Phase mới.

---

## 5. Kiến trúc Gói & Quy chuẩn Kỹ thuật (Package Architecture & Standards)

Toàn bộ mã nguồn backend được tổ chức theo kiến trúc phân tầng kỹ thuật sạch sẽ dưới package `com.uit.dormmanagement`:

```text
com.uit.dormmanagement/
├── configs/            # Cấu hình Spring Beans (OpenAPI, Redis, StorageConfig, StorageProperties)
├── constants/          # Hằng số Redis key (RedisKeyConstants)
├── controllers/        # REST Controllers mỏng (AbstractBaseController, ScalarDocsController)
├── dtos/               # Request/Response DTOs (Java record, Jakarta Validation)
│   ├── request/        # auth, room, tenant, onboarding, contract, vehicle, ...
│   └── response/       # ResponseFactory, base/ (SingleResponse, SuccessResponse, PageResponse, ErrorResponse, DetailedErrorResponse)
├── entities/           # 20 JPA Entity + RoomAmenityId (khớp 20 bảng V1__clean_baseline.sql)
├── enums/              # 21 Java enum khớp 1:1 với DB CHECK constraints
├── exceptions/         # BaseAppException, AppErrorCode, AppExceptionHandler, DataIntegrityViolationClassifier, error/*ErrorCode
├── mappers/            # Thư mục mapper (khi phát sinh chuyển đổi phức tạp)
├── repositories/       # Spring Data JPA Repositories (UUID PK, composite/text key ngoại lệ)
├── schedulers/         # Gói cho tác vụ định kỳ
├── security/           # JWT filter, CustomUserDetailsService, UserPrincipal, SecurityConfig, EntryPoint/AccessDeniedHandler
├── services/           # Business Services interface & impl/ (@Transactional)
└── utils/              # Helper tĩnh (LogMaskingUtils, ...)
```

### Quy chuẩn Dữ liệu & Mã hóa:
- **Khóa chính (PK):** Dùng `java.util.UUID` trên 18 bảng nghiệp vụ (`UUID id`); ngoại lệ theo `V1__clean_baseline.sql` gồm `room_amenities` (khóa tổng hợp `(room_id, amenity_id)` qua `RoomAmenityId`) và `room_code_registry` (`code TEXT PRIMARY KEY`). Không dùng `Long` hay `BIGSERIAL`.
- **Tiền tệ (VND):** Luôn dùng `BigDecimal` trong Java với `precision = 14, scale = 0` ánh xạ `numeric(14,0)` trong PostgreSQL. Tuyệt đối **CẤM** dùng `Double` hoặc `Float`.
- **Thời gian & Ngày:** Dùng `Instant` (hoặc `OffsetDateTime`) ánh xạ với `timestamptz`, và `LocalDate` ánh xạ với `date` trong PostgreSQL (Jackson TZ `Asia/Ho_Chi_Minh`).
- **Fetch Type:** Mọi quan hệ `@ManyToOne` và `@OneToOne` trong JPA Entity bắt buộc thiết lập `fetch = FetchType.LAZY`.
- **Controllers:** Giữ controller mỏng (thin controller), chỉ đón nhận `@Valid` request DTO và trả về response DTO bọc qua `ResponseFactory`. Tuyệt đối không inject `Repository` vào `Controller`.
- **Services:** Chứa 100% logic nghiệp vụ; thao tác đọc dùng `@Transactional(readOnly = true)`, thao tác ghi dùng `@Transactional`.
- **Lưu trữ file chứng từ:** Tích hợp **OCI Object Storage private bucket** qua AWS SDK v2 S3 Compatibility API (`PrivateObjectStorage` / `S3PrivateObjectStorage`); chặn truy cập tĩnh `/uploads/**` (`denyAll()`), toàn bộ ảnh cà vẹt xe và tài liệu hợp đồng được stream qua Backend (`GET /api/v1/admin/contracts/{contractId}/documents/{documentId}/content`, `GET /api/v1/tenant/contracts/{contractId}/documents/{documentId}/content`, và các endpoint vehicle evidence) kèm kiểm tra quyền/chủ sở hữu.

---

## 6. Danh mục 20 Bảng CSDL Cốt lõi (Database Entities Checklist)

Tham chiếu chi tiết thuộc tính tại `docs/Logical-ERD.md` (v3.6 FINAL) và `src/main/resources/db/migration/V1__clean_baseline.sql`:

| STT | Bảng CSDL | JPA Entity | Ý nghĩa nghiệp vụ |
|:---:|:---|:---|:---|
| 1 | `users` | `User` | Tài khoản đăng nhập (`MANAGER`, `TECHNICIAN`, `TENANT`). |
| 2 | `tenants` | `Tenant` | Hồ sơ khách thuê đa đối tượng (CCCD 12 số duy nhất, email, SĐT, thường trú). |
| 3 | `staff_profiles` | `StaffProfile` | Hồ sơ nhân sự quản lý & kỹ thuật viên bảo trì. |
| 4 | `rooms` | `Room` | Phòng ở (`floor >= 1`, `RoomType` 4/6/8 giường, `gender_policy`, `archived_at` soft archive). |
| 5 | `beds` | `Bed` | Giường/hộp ngủ độc lập (`slot_key`: `P.302-G02`, trạng thái `AVAILABLE`/`OCCUPIED`/`MAINTENANCE`). |
| 6 | `amenities` | `Amenity` | Danh mục tiện nghi chuẩn hóa (7 dòng seed trong baseline). |
| 7 | `room_amenities` | `RoomAmenity` (`RoomAmenityId`) | Bảng nối phòng – tiện nghi kèm số lượng (`quantity >= 1`). |
| 8 | `room_code_registry` | `RoomCodeRegistry` | Sổ cái mã phòng (`code TEXT PK`, bảo vệ mã `used_in_business = true`). |
| 9 | `contracts` | `Contract` | Hợp đồng thuê giường trọn gói (`monthly_rent`, `deposit_amount` `numeric(14,0)`, tối đa 1 `ACTIVE` mỗi giường/khách). |
| 10 | `contract_renewals` | `ContractRenewal` | Lịch sử gia hạn hợp đồng (`previous_end_date`, `new_end_date`, `new_monthly_rent`). |
| 11 | `contract_documents` | `ContractDocument` | Tài liệu scan hợp đồng, phụ lục gia hạn, biên bản bàn giao trả phòng. |
| 12 | `invoices` | `Invoice` | Hóa đơn trọn gói hàng tháng (`DEBT` $\rightarrow$ `PENDING_CONFIRMATION` $\rightarrow$ `PAID`). |
| 13 | `payment_transactions` | `PaymentTransaction` | Giao dịch thanh toán (`CASH` hoặc `VIETQR_TRANSFER`, ảnh minh chứng, người xác nhận). |
| 14 | `facility_expenses` | `FacilityExpense` | Chi phí cơ sở (`ELECTRICITY`, `WATER`, `INTERNET`, `SALARY`, `OTHER`). |
| 15 | `maintenance_tickets` | `MaintenanceTicket` | Phiếu sự cố kỹ thuật (`URGENT` > `HIGH` > `NORMAL`, ảnh nghiệm thu). |
| 16 | `ticket_replacement_parts` | `TicketReplacementPart` | Linh kiện vật tư thay thế gắn phiếu sự cố. |
| 17 | `violations` | `Violation` | Biên bản vi phạm nội quy gắn cá nhân khách thuê (`tenant_id`). |
| 18 | `vehicles` | `Vehicle` | Phương tiện hầm xe, mã thẻ từ (`card_code`), 2 ảnh cà vẹt private (miễn phí 100%). |
| 19 | `vehicle_change_requests` | `VehicleChangeRequest` | Yêu cầu đổi xe của khách đang có xe `ISSUED` (`PENDING`/`APPROVED`/`REJECTED`/`CANCELLED`). |
| 20 | `notifications` | `Notification` | Hộp thông báo in-app (`NotificationEventType`, trạng thái `is_read`). |

---

## 7. Chuẩn hóa API Response & Xử lý Lỗi (Response & Error Handling)

Chuẩn hóa trực tiếp bởi `ResponseFactory`, `dtos/response/base/`, `AppExceptionHandler`, `DataIntegrityViolationClassifier`, và `exceptions/error/*ErrorCode.java`:

### 7.1. Cấu trúc Response Envelope
1. **API có dữ liệu trả về (`SingleResponse<T>`):**
   ```json
   {
     "httpStatus": 200,
     "message": "Lấy thông tin phòng thành công",
     "data": { ... }
   }
   ```
2. **API dạng Command không mang dữ liệu (`SuccessResponse`):**
   ```json
   {
     "httpStatus": 200,
     "message": "Yêu cầu thành công"
   }
   ```
3. **API Phân trang (`SingleResponse<PageResponse<T>>`):**
   ```json
   {
     "httpStatus": 200,
     "message": "Lấy danh sách thành công",
     "data": {
       "items": [ ... ],
       "page": 0,
       "size": 10,
       "totalElements": 48,
       "totalPages": 5,
       "last": false
     }
   }
   ```
4. **API Báo lỗi (`ErrorResponse` / `DetailedErrorResponse`):**
   ```json
   {
     "httpStatus": 409,
     "errorCode": "TENANT_409_ID_CARD",
     "message": "Số CCCD đã tồn tại trong hệ thống",
     "timestamp": "2026-11-26T08:30:00Z",
     "path": "/api/v1/admin/tenants/..."
   }
   ```
   *(Riêng lỗi `WEB_400_VAL` dùng `DetailedErrorResponse` bổ sung trường `items: Map<String, List<String>>`.)*

### 7.2. Danh mục Mã lỗi Theo Domain (`*ErrorCode.java`) & Phân loại Ràng buộc DB
- **`AuthErrorCode`:** `AUTH_401_CREDENTIALS`, `AUTH_401_REFRESH`, `AUTH_403_LOCKED`, `AUTH_403_RATE_LOCKED`, `AUTH_404_USER`, `AUTH_409_EMAIL`, `TECH_404`, `AUTH_400_OLD_PASSWORD`, `AUTH_400_PASSWORD_CONFIRM`, `AUTH_400_PASSWORD_SAME`, `AUTH_400_OTP`, `AUTH_429_OTP_COOLDOWN`, `AUTH_429_OTP_ATTEMPTS`, `AUTH_400_RESET_TOKEN`.
- **`RoomErrorCode`:** `ROOM_404`, `ROOM_404_AMENITY`, `ROOM_409`, `ROOM_409_CODE_PROTECTED`, `ROOM_409_OCCUPIED`, `ROOM_409_ARCHIVED`, `ROOM_409_NOT_ARCHIVED`, `ROOM_409_HISTORY`, `BED_404`, `BED_400_MAINTENANCE`, `BED_409_STATUS`, `BED_409_CONTRACT`.
- **`TenantErrorCode`:** `TENANT_404`, `TENANT_409_ID_CARD`, `TENANT_409_EMAIL`, `TENANT_409_GENDER`, `TENANT_400_ID_CARD_FORMAT`, `TENANT_400_NO_FIELDS`, `TENANT_400_NOT_ACTIVE`.
- **`VehicleErrorCode`:** `VEHICLE_404`, `VEHICLE_409_QUOTA`, `VEHICLE_409_CARD_CODE`, `VEHICLE_409_ACTIVE`, `VEHICLE_409_NOT_PENDING`, `VEHICLE_409_INVALID_STATE`, `VEHICLE_409_PENDING_CHANGE_REQUEST`, `VEHICLE_400_INVALID_TYPE_DATA`, `VEHICLE_400_INVALID_EVIDENCE`, `VEHICLE_EVIDENCE_404`, `VEHICLE_CHANGE_REQUEST_404`, `VEHICLE_CHANGE_REQUEST_409_EXISTS`, `VEHICLE_CHANGE_REQUEST_409_NOT_PENDING`.
- **`ContractErrorCode`:** `CONTRACT_404`, `CONTRACT_400_TERMINATED`, `CONTRACT_400_BED_UNAVAILABLE`, `CONTRACT_400_GENDER_MISMATCH`, `CONTRACT_400_DATES`, `CONTRACT_404_DOCUMENT`, `CONTRACT_400_FILE_SIZE`, `CONTRACT_400_FILE_FORMAT`.
- **`WebErrorCode` / `SystemErrorCode`:** `WEB_400`, `WEB_400_VAL`, `WEB_401`, `WEB_403`, `WEB_404`, `WEB_405`, `WEB_409`, `WEB_415`, `WEB_429`, `SYS_500`.
- **Phân loại `DataIntegrityViolationException` (`DataIntegrityViolationClassifier`):** Các ràng buộc unique đã biết (`tenants_id_card_number_key`, `uq_users_email_canonical`, `uq_rooms_number_canonical`, `room_code_registry_pkey`, `uq_beds_slot_key_canonical`, `uq_vehicles_issued_card_code`, `uq_vehicles_tenant_active_quota`, `uq_vehicle_change_requests_pending_tenant`) trả mã `409` domain tương ứng; lỗi unique chưa ánh xạ (`SQLSTATE 23505`) trả `409 WEB_409`; lỗi `NOT NULL (23502)`, `CHECK (23514)`, khóa ngoại chung (`23503`, trừ xóa cứng phòng đang có lịch sử $\rightarrow$ `ROOM_409_HISTORY`), tràn độ dài (`22xxx`), hoặc không phân loại được trả `400 WEB_400`.

---

## 8. Ma trận Phân hệ Chức năng & Trạng thái Phạm vi (FR-01 đến FR-27)

Khi nhận task phát triển tính năng, Agent đối soát trạng thái thực tế theo bảng sau:

| Phân hệ (SRS Section) | Mã FR | Tên chức năng | Trạng thái / Ghi chú kỹ thuật |
|:---|:---|:---|:---|
| **Hệ thống & Phân quyền (4.1)** | FR-21 $\rightarrow$ FR-24 | Đăng nhập Bcrypt, JWT/Redis session, nhân sự Kỹ thuật viên, OTP quên mật khẩu | **Phase 2 (Hoàn thiện + Hardening).** Staff email / Tenant CCCD 12 số; OTP qua Redis. |
| **Phòng & Giường (4.2)** | FR-01 $\rightarrow$ FR-05 | Ma trận phòng giường, thêm phòng 4/6/8 giường, cập nhật, lưu trữ/khôi phục/xóa nhầm | **Phase 2 (Hoàn thiện + Hardening).** `gender_policy` & `slot_key` + `room_code_registry`. |
| **Khách thuê (4.3)** | FR-06, FR-07 | Danh sách/chi tiết khách thuê, cập nhật hồ sơ, đổi SĐT/khẩn cấp | **Phase 2 (Hoàn thiện + Hardening).** Từ chối chuỗi rỗng cho `idCardNumber`/`email`. |
| **Phương tiện (4.4)** | FR-08 | Đăng ký xe (01 xe hoạt động/khách), cấp/thu thẻ từ, từ chối, yêu cầu đổi xe, ảnh private | **Phase 2 (Hoàn thiện + Hardening) + Phase 3A Storage.** Lưu trữ private trên OCI Object Storage (S3 API) qua Backend Streaming. |
| **Tiếp nhận & Hợp đồng (4.5)** | FR-09, FR-10, FR-12, FR-13 | Direct Onboarding, tài liệu hợp đồng, gia hạn, checkout thanh lý | **Phase 3 (Phase 3A đã hoàn tất OCI S3 storage & streaming tài liệu hợp đồng; nghiệp vụ Phase 3B chưa triển khai).** Riêng **FR-11 (Chuyển phòng)** đang **hoãn (deferred)**. |
| **Chi phí & Lợi nhuận (4.6)** | FR-14 | Chi phí cơ sở (`facility_expenses`), Lợi nhuận ròng ước tính | **Ngoài phạm vi triển khai hiện tại (Excluded).** Chỉ có bảng nền V1. |
| **Hóa đơn & Thu tiền (4.7)** | FR-15, FR-16 | Phát hành ngày 25, hạn ngày 05, xác nhận VietQR / tiền mặt | **Ngoài phạm vi triển khai hiện tại (Excluded).** Chỉ có bảng nền V1. |
| **Sự cố, Vi phạm, Báo cáo & TB (4.8)** | FR-17 $\rightarrow$ FR-20, FR-25 $\rightarrow$ FR-27 | Phiếu sửa chữa ưu tiên, biên bản vi phạm, KPI dashboard, thông báo, cổng di động | **Ngoài phạm vi triển khai hiện tại (Excluded).** Chỉ có bảng nền V1. |

---

## 9. Lệnh Xây dựng, Kiểm thử & Xác thực (Build & Verification)

Agent luôn sử dụng Maven Wrapper (`./mvnw`) từ thư mục gốc của repository:

```bash
# 1. Biên dịch và kiểm tra lỗi cú pháp
./mvnw clean compile

# 2. Chạy toàn bộ Unit & Integration Tests
./mvnw test

# 3. Chạy đóng gói và kiểm tra toàn diện
./mvnw clean package

# 4. Chạy ứng dụng Spring Boot local (profile dev)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 5. Khởi động hạ tầng PostgreSQL & Redis bằng Docker Compose
docker compose -f docker/docker-compose.yml --env-file .env up -d
```

---

## 10. Giao thức Vận hành Dành cho AI Agent (Agent Operating Protocol)

Mọi Agent khi thực thi công việc trên repository này **BẮT BUỘC** tuân thủ 5 bước:

1. **Trước khi tạo hoặc chỉnh sửa JPA Entity / Migration SQL:**
   - Đọc kỹ `docs/Logical-ERD.md` (v3.6 FINAL) và `src/main/resources/db/migration/V1__clean_baseline.sql` để lấy chính xác tên bảng, tên cột, kiểu dữ liệu (`numeric(14,0)`, `uuid`, `date`, `timestamptz`), nullability và khóa ngoại.
   - Tuyệt đối không tự ý thêm các cột ngoài đặc tả (ví dụ: `university`, `student_code`, `building_id`, `late_fee_amount`).
2. **Trước khi viết Logic Nghiệp vụ trong Service:**
   - Đối soát với 11 Quy tắc nghiệp vụ cốt lõi (BR-01 đến BR-11) tại Mục 3.
   - Luôn kiểm tra ràng buộc cách ly giới tính (`BR-02`) khi xếp khách hoặc chỉnh sửa phòng.
   - Tuân thủ thứ tự khóa chống deadlock và bọc `@Transactional` cho mọi thao tác thay đổi trạng thái.
3. **Trước khi tạo REST API Controller:**
   - Kế thừa `AbstractBaseController` hoặc sử dụng `ResponseFactory` để trả về định dạng chuẩn Envelope (`SingleResponse`, `SuccessResponse`, `PageResponse`).
   - Mọi mã lỗi ném ra phải là `BaseAppException` gắn với một `AppErrorCode` trong `exceptions/error/*ErrorCode.java`, không ném raw String hoặc exception không kiểm soát.
4. **Trước khi khai báo hoàn thành nhiệm vụ:**
   - Chạy `./mvnw -DskipTests compile`, `./mvnw test`, kiểm tra Spotless cho các tệp đã sửa (`./mvnw spotless:check -DspotlessFiles="..."`) và `git diff --check`.
   - Đảm bảo không ghi log thông tin nhạy cảm (`LogMaskingUtils`), không hardcode bí mật, không tự ý sửa `V1__clean_baseline.sql` hoặc xóa dữ liệu lịch sử.
