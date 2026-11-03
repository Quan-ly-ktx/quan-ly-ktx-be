# AGENT.md — KTX Hub / PDMS (`dormitory-management`)

Tài liệu hướng dẫn bắt buộc dành cho AI Agent khi tham gia khảo sát, thiết kế, lập trình backend, viết migration CSDL, tích hợp API hoặc kiểm thử trong repository `dormitory-management`.

---

## 1. Định danh & Bối cảnh Dự án (Project Identity & Context)

- **Tên dự án:** Hệ thống Quản lý Ký túc xá Tư nhân & Sleepbox (**KTX Hub / PDMS** - Private Dormitory Management System).
- **Phạm vi kỹ thuật:** Java Spring Boot Backend REST API.
- **Base Package:** `com.uit.dormmanagement`
- **Ngôn ngữ & Runtime:** Java 25, Spring Boot 4.x, Maven (`./mvnw`).
- **Hệ quản trị CSDL:** PostgreSQL 15+ LTS (chuẩn hóa 3NF/BCNF, UUID v4, Soft Delete).
- **Mô hình vận hành thực tế:**
  1. **01 Tòa nhà duy nhất (Single-facility):** Quản lý tập trung theo các tầng (`floor: 1..4`), không có khái niệm chuỗi chi nhánh hay đa tòa nhà.
  2. **Quản lý theo vị trí giường riêng biệt (`Bed-level Tracking`):** Đơn vị cho thuê và định giá độc lập là từng giường/hộp ngủ (`G01, G02, ...`) bên trong phòng.
  3. **Mô hình Thuê trọn gói (`All-inclusive Rental`):** Giá thuê phòng hàng tháng đã bao gồm toàn bộ điện, nước, internet, gửi xe. Tuyệt đối không đo công tơ phụ chia lẻ theo phòng.
  4. **Hạch toán Lợi nhuận ròng thực tế:** Ban Quản lý nhập hóa đơn điện nước tổng cả cơ sở để hệ thống tự động tính Lợi nhuận ròng = `Doanh thu thực thu - Chi phí sửa chữa hoàn thành - Chi phí điện nước tổng cơ sở`.
  5. **Khách thuê đa đối tượng:** Người đi làm, chuyên gia trẻ, sinh viên, freelancer (loại bỏ toàn bộ các trường học vị/MSSV).
  6. **Phân định nền tảng thiết bị:**
     - **Quản lý / Chủ KTX (`MANAGER`):** Desktop Web Dashboard (quản trị toàn diện, bộ lọc nợ, hóa đơn, duyệt thẻ xe, lợi nhuận ròng).
     - **Kỹ thuật viên (`TECHNICIAN`):** Mobile App (xử lý sự cố theo thứ tự ưu tiên: Khẩn cấp > Cao > Bình thường, nghiệm thu vật tư).
     - **Khách thuê (`TENANT`):** Mobile-First Web/App (thẻ phòng điện tử, thanh toán VietQR động, đăng ký xe tải cà vẹt, báo hỏng).

---

## 2. Thứ bậc Nguồn Sự thật Tuyệt đối (Source of Truth Hierarchy)

Khi có bất kỳ mâu thuẫn nào giữa các quy tắc kỹ thuật chung và tài liệu dự án, **Agent BẮT BUỘC tuân thủ theo thứ tự ưu tiên sau**:

1. `docs/SRS.md` (Đặc tả Yêu cầu Phần mềm v2.1 — Quy tắc nghiệp vụ BR-01..BR-11, Yêu cầu chức năng FR-01..FR-27).
2. `docs/Logical-ERD.md` **&** `docs/Conceptual_ERD.md` (Lược đồ 12 bảng PostgreSQL, kiểu dữ liệu, quan hệ, chỉ mục, View Lợi nhuận ròng).
3. `docs/Response-ErrorHandling-Architecutre.md` (Kiến trúc chuẩn hóa Envelope Response, `ResponseFactory`, `AppErrorCode`, `GlobalExceptionHandler`).
4. `AGENT.md` (Tài liệu này — kim chỉ nam vận hành và các ràng buộc bất biến).
5. Các quy chuẩn mã hóa chung (Coding Standards, Design Tokens, Clean Architecture).

---

## 3. Các Quy tắc Nghiệp vụ Bất biến Cốt lõi (Core Business Rules - BR)

Mọi dòng code, API endpoint, migration SQL và logic Service **BẮT BUỘC** phải tuân thủ nghiêm ngặt 11 quy tắc sau:

### BR-01: Quản lý theo vị trí giường riêng biệt (Bed-level Tracking & Unique Slot Key)
- Mỗi phòng chứa danh sách giường đánh số cố định: `G01, G02, ..., G0n` (tương ứng sức chứa `total_beds`).
- Mỗi giường là một vị trí thuê độc lập có giá riêng (`price`) và trạng thái (`AVAILABLE` - Sẵn sàng, `OCCUPIED` - Đang thuê, `MAINTENANCE` - Bảo trì).
- Khóa vị trí giường là duy nhất toàn tòa nhà:
  $$\text{slotKey} = \text{roomNumber} + \text{"-"} + \text{bedCode} \quad (\text{Ví dụ: } \texttt{P.302-G02})$$
- **CẤM:** Không được phép tạo 2 hợp đồng có hiệu lực trùng cùng một vị trí giường tại cùng một thời điểm.

### BR-02: Cách ly giới tính nghiêm ngặt 100% (Strict Gender Isolation Policy)
- Mỗi phòng gắn cố định một chính sách giới tính duy nhất: `Nam` hoặc `Nữ`. Tuyệt đối không có phòng hỗn hợp (`MIXED`).
- Khách thuê có giới tính sinh học xác định trên CCCD: `Nam` hoặc `Nữ`.
- **Ràng buộc bất biến:** Khách chỉ được xếp hoặc chuyển vào phòng có `gender_policy == tenant.gender`.
- Khi cập nhật thông tin phòng, tuyệt đối **CẤM** đổi `gender_policy` nếu phòng đang có ít nhất 01 khách cư trú (`occupied_beds > 0`).

### BR-03: Giá thuê trọn gói & Quản lý Chi phí Tiện ích Cơ sở (All-inclusive Rental & Net Profit)
- Tiền phòng thu theo tháng trọn gói cố định (đã gồm tiền phòng, điện sinh hoạt, nước sinh hoạt, wifi, vệ sinh khu vực chung, gửi xe).
- Ban Quản lý nhập thông tin các hóa đơn chi phí phát sinh định kỳ của cả cơ sở vào bảng `facility_expenses` (Hóa đơn điện tổng EVN, nước tổng, internet cáp quang, rác/dịch vụ khác).
- **Công thức tính Lợi nhuận ròng thực tế (Net Profit):**
  $$\text{Lợi nhuận ròng} = \text{Doanh thu thực thu} - \text{Chi phí sửa chữa hoàn thành} - \sum \text{Chi phí điện nước internet tổng cơ sở}$$
- **CẤM:** Tuyệt đối không tạo bảng công tơ phụ chia lẻ điện nước từng phòng hay từng ngày.

### BR-04: Đặt cọc & Trả phòng giải phóng giường (Offline Deposit & Checkout)
- Mức cọc tiêu chuẩn bằng đúng **01 tháng tiền thuê giường**, thu trực tiếp bằng tiền mặt hoặc chuyển khoản cá nhân ngoài thực tế lúc ký hợp đồng giấy.
- Khách báo trước khi rời đi tối thiểu 15 ngày. Khi checkout, hai bên tự thỏa thuận đối soát cọc và hoàn trả ngoài thực tế.
- Hệ thống **tuyệt đối KHÔNG cần quản lý công thức tính toán hoàn cọc chi tiết**.
- Khi hoàn tất trả phòng: Quản lý bấm [Thanh lý hợp đồng] $\rightarrow$ Hợp đồng chuyển `Đã thanh lý` (`TERMINATED`), khách chuyển `Đã trả phòng` (`CHECKED_OUT`), giường tự động giải phóng về trạng thái `Sẵn sàng` (`AVAILABLE`).

### BR-05: Chu kỳ phát hành hóa đơn cố định & Hạn nộp ngày 05 hàng tháng
- **Thời điểm phát hành:** Đúng **00:00 ngày 25 hàng tháng**, phát hành hàng loạt hóa đơn tiền phòng trọn gói cho kỳ tháng tiếp theo với trạng thái ban đầu là `Nợ` (`UNPAID`).
- **Hạn thanh toán cố định (`due_date`):** Luôn là **ngày 05 của tháng tiếp theo** (ví dụ: kỳ tháng 11 phát hành ngày 25/10, `due_date` là `05/11/2026`).
- **Từ ngày 06 trở đi:** Hóa đơn chưa thanh toán tự động hiển thị quá hạn trên bộ lọc `Nợ` để Quản lý đôn đốc thu tiền.
- **CẤM:** Tuyệt đối **KHÔNG tính phí phạt nộp trễ tiền**.

### BR-06: Quy trình xử lý sự cố & Thứ tự ưu tiên kỹ thuật (Ticket Prioritization)
- Phân cấp 3 mức độ ưu tiên:
  1. `Khẩn cấp` (Rank 0): Nguy cơ chập cháy điện, vỡ ống nước ngập sàn, kẹt khóa cửa. (Cờ đỏ nhấp nháy, banner đỏ trên màn hình thợ).
  2. `Cao` (Rank 1): Hỏng máy lạnh, mất nguồn tủ lạnh chung. (Cờ cam).
  3. `Bình thường` (Rank 2): Quạt kêu, đèn chập chờn, kẹt hộc tủ. (Cờ xanh).
- **Thuật toán sắp xếp công việc của Kỹ thuật viên:**
  $$\text{SortOrder} = (\text{isCompleted} ? 1 : 0) \times 10 + \text{priorityRank}$$
  *(Việc chưa hoàn thành luôn đứng trước; trong các việc chưa xong: Khẩn cấp $\rightarrow$ Cao $\rightarrow$ Bình thường).*
- Vòng đời: `Mới giao` (`OPEN`) $\rightarrow$ `Đang xử lý` (`IN_PROGRESS`) $\rightarrow$ `Hoàn thành` (`RESOLVED` / `CLOSED` - chụp ảnh nghiệm thu, ghi nhận linh kiện `ticket_replacement_parts`).

### BR-07: Hồ sơ vi phạm nội quy & Xử phạt (Violations)
- Hồ sơ vi phạm gắn trực tiếp với cá nhân khách thuê (`tenant_id`), **KHÔNG gắn với phòng (`room_id`)**.
- 5 loại vi phạm chuẩn: `Về muộn`, `Gây ồn`, `Vệ sinh`, `Khách ngoài`, `Hút thuốc / cháy nổ` (và `Khác`).
- 3 hình thức xử lý: `Nhắc nhở`, `Cảnh cáo`, `Phạt tiền`.
- **CẤM:** Tiền phạt (nếu có) được xử lý thu trực tiếp ngoài thực tế; Quản lý chỉ nhập số tiền vào hệ thống để theo dõi lịch sử, **tuyệt đối KHÔNG gộp hay kết chuyển vào hóa đơn tiền phòng trên web**.

### BR-08: Toàn vẹn dữ liệu & Nghiêm cấm xóa vật lý (Soft Delete Policy)
- Toàn bộ các bảng tài chính, hợp đồng và vận hành (`contracts`, `invoices`, `payment_transactions`, `facility_expenses`, `violations`, `rooms`, `tenants`) **tuyệt đối cấm dùng lệnh SQL `DELETE`**.
- Mọi quan hệ khóa ngoại thiết lập `ON DELETE RESTRICT`. Thao tác xóa sử dụng cờ `deleted_at = NOW()`.

### BR-09: Cấp phát tài khoản trực tiếp & Chuẩn hóa hồ sơ khách thuê đa đối tượng
- Khách thuê không tự đăng ký tài khoản từ web ngoài. Khách đến xem phòng trực tiếp; Quản lý bấm [+ Thêm người thuê và gán phòng] để tạo hồ sơ và cấp tài khoản.
- **Loại bỏ triệt để các trường học vị sinh viên:** Tuyệt đối không tạo cột hay yêu cầu bắt buộc: `university`, `faculty`, `student_code`, `priority_type`.
- Trường định danh chuẩn hóa: *Họ tên, Giới tính, Số điện thoại, CCCD (12 số), Email, Địa chỉ thường trú*.

### BR-10: Quản lý phương tiện di chuyển & Thẻ gửi xe (Vehicles)
- **Quy trình 4 bước:**
  1. Khách khai báo xe trên mobile app (Biển số, loại xe, hãng, màu) và tải 2 ảnh cà vẹt xe (mặt trước/mặt sau). Trạng thái: `Chưa cấp thẻ` (`PENDING`).
  2. Quản lý đối chiếu ảnh cà vẹt, nhập Mã thẻ từ vật lý (`card_code`, VD: `THE-0187`), bấm cấp thẻ $\rightarrow$ Trạng thái: `Đã cấp thẻ` (`ACTIVE`).
  3. Phí gửi xe bao trọn gói 100% trong tiền phòng; **hệ thống tuyệt đối không thu thêm bất kỳ khoản phí xe nào**.
  4. Khi khách checkout: Quản lý thu hồi thẻ từ vật lý, chuyển trạng thái xe sang `Đã thu hồi` (`REVOKED`).

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

---

## 5. Kiến trúc Gói & Quy chuẩn Kỹ thuật (Package Architecture & Standards)

Toàn bộ mã nguồn backend được tổ chức theo kiến trúc phân tầng kỹ thuật sạch sẽ dưới package `com.uit.dormmanagement`:

```text
com.uit.dormmanagement/
├── configs/            # Cấu hình Spring Beans (Security, CORS, Jackson, Swagger/OpenAPI)
├── constants/          # Hằng số hệ thống, regex CCCD/biển số, định dạng ngày giờ
├── controllers/        # REST Controllers (kế thừa AbstractBaseController, dùng ResponseFactory)
├── dtos/               # Request/Response DTOs (Java record, Jakarta Validation)
│   ├── auth/           # Đăng nhập, đổi mật khẩu, OTP
│   ├── room/           # Phòng, giường, tiện nghi
│   ├── tenant/         # Hồ sơ khách thuê, đổi phòng, checkout
│   ├── contract/       # Hợp đồng, tài liệu scan
│   ├── invoice/        # Hóa đơn trọn gói, gạch nợ
│   ├── expense/        # Chi phí tiện ích cơ sở, lợi nhuận ròng
│   ├── ticket/         # Phiếu sự cố, nghiệm thu linh kiện
│   ├── violation/      # Biên bản kỷ luật, vi phạm
│   ├── vehicle/        # Đăng ký xe, cấp thẻ từ, ảnh cà vẹt
│   └── response/base/  # SingleResponse, SuccessResponse, PageResponse, ErrorResponse
├── entities/           # JPA Entities (khớp 1:1 với 12 bảng trong docs/Logical-ERD.md)
├── enums/              # Java enums khớp với DB CHECK constraints
├── exceptions/         # BaseAppException, GlobalExceptionHandler, AppErrorCode enums
├── mappers/            # MapStruct mappers (chuyển đổi Entity <-> DTO)
├── repositories/       # Spring Data JPA Repositories (UUID làm PK)
├── security/           # JWT filter, UserDetailsService, AuthenticationEntryPoint
├── services/           # Business Services interface & implementation (@Transactional)
└── utils/              # Helper tĩnh (VietQR payload builder, tính toán lợi nhuận ròng)
```

### Quy chuẩn Dữ liệu & Mã hóa:
- **Khóa chính (PK):** Bắt buộc dùng `java.util.UUID` trên toàn bộ 12 bảng (`UUID id`). Không dùng `Long` hay `BIGSERIAL`.
- **Tiền tệ (VND):** Luôn dùng `BigDecimal` trong Java và `numeric(12,2)` trong PostgreSQL. Tuyệt đối **CẤM** dùng `Double` hoặc `Float`.
- **Thời gian:** Dùng `Instant` hoặc `OffsetDateTime` trong Java ánh xạ với `timestamptz` trong CSDL.
- **Fetch Type:** Mọi quan hệ `@ManyToOne` và `@OneToOne` trong JPA Entity bắt buộc thiết lập `fetch = FetchType.LAZY`.
- **Controllers:** Giữ controller mỏng (thin controller), chỉ đón nhận `@Valid` request DTO và trả về response DTO bọc qua `ResponseFactory`. Tuyệt đối không inject `Repository` vào `Controller`.
- **Services:** Chứa 100% logic nghiệp vụ; thao tác đọc dùng `@Transactional(readOnly = true)`, thao tác ghi dùng `@Transactional`.

---

## 6. Danh mục 12 Bảng CSDL Cốt lõi (Database Entities Checklist)

Tham chiếu chi tiết thuộc tính tại `docs/Logical-ERD.md`:

| STT | Bảng CSDL | JPA Entity | Ý nghĩa nghiệp vụ |
|:---:|:---|:---|:---|
| 1 | `users` | `User` | Tài khoản đăng nhập (role: `MANAGER`, `TECHNICIAN`, `TENANT`). |
| 2 | `tenants` | `Tenant` | Hồ sơ khách thuê đa đối tượng (CCCD 12 số, SĐT, email, thường trú). |
| 3 | `staff_profiles` | `StaffProfile` | Hồ sơ nhân sự quản lý & kỹ thuật viên bảo trì. |
| 4 | `rooms` | `Room` | Phòng ở thuộc tầng 1..4, chính sách giới tính cố định (`Nam`/`Nữ`). |
| 5 | `beds` | `Bed` | Giường/hộp ngủ độc lập (`slot_key`: `P.302-G02`, giá riêng). |
| 6 | `amenities` & `room_amenities` | `Amenity`, `RoomAmenity` | Tiện nghi phòng (Điều hòa, Nóng lạnh, Tủ lạnh...). |
| 7 | `contracts` | `Contract` | Hợp đồng thuê giường trọn gói, tiền cọc, file ảnh scan giấy tờ. |
| 8 | `invoices` | `Invoice` | Hóa đơn trọn gói hàng tháng (phát hành ngày 25, hạn nộp ngày 05). |
| 9 | `payment_transactions` | `PaymentTransaction` | Giao dịch thanh toán (VietQR động hoặc Tiền mặt có Quản lý duyệt). |
| 10 | `facility_expenses` | `FacilityExpense` | Hóa đơn điện EVN, nước, internet tổng để tính **Lợi nhuận ròng**. |
| 11 | `maintenance_tickets` | `MaintenanceTicket` | Phiếu sự cố kỹ thuật (Ưu tiên: Khẩn cấp > Cao > Bình thường). |
| 12 | `ticket_replacement_parts` | `TicketReplacementPart` | Linh kiện phụ tùng thay thế kèm đơn giá xuất kho. |
| 13 | `violations` | `Violation` | Biên bản vi phạm nội quy gắn cá nhân (tiền phạt thu ngoài). |
| 14 | `vehicles` | `Vehicle` | Phương tiện hầm xe, mã thẻ từ, 2 ảnh cà vẹt xe (miễn phí 100%). |

---

## 7. Chuẩn hóa API Response & Xử lý Lỗi (Response & Error Handling)

Theo chuẩn `docs/Response-ErrorHandling-Architecutre.md`:

### 7.1. Cấu trúc Response Envelope
1. **API có dữ liệu trả về:**
   ```json
   {
     "httpStatus": 200,
     "message": "Lấy thông tin phòng thành công",
     "data": { ... }
   }
   ```
2. **API dạng Command (không mang dữ liệu):**
   ```json
   {
     "httpStatus": 200,
     "message": "Thanh lý hợp đồng và giải phóng giường thành công"
   }
   ```
3. **API Phân trang (`PageResponse<T>`):**
   ```json
   {
     "httpStatus": 200,
     "message": "Lấy danh sách hóa đơn thành công",
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
4. **API Báo lỗi (`ErrorResponse`):**
   ```json
   {
     "httpStatus": 400,
     "errorCode": "ROOM_GENDER_MISMATCH",
     "message": "Không thể xếp khách nam vào phòng có chính sách giới tính Nữ",
     "timestamp": "2026-10-30T10:15:30Z",
     "path": "/api/v1/contracts/onboard"
   }
   ```

### 7.2. Danh mục Mã lỗi Theo Domain (`AppErrorCode`)
- `AUTH_xxx`: Lỗi xác thực, sai mật khẩu, tài khoản bị khóa, token hết hạn.
- `ROOM_xxx`: `ROOM_NOT_FOUND`, `ROOM_GENDER_MISMATCH`, `BED_NOT_AVAILABLE`, `ROOM_HAS_RESIDENTS`.
- `CONTRACT_xxx`: `CONTRACT_NOT_FOUND`, `BED_ALREADY_RENTED`, `CONTRACT_EXPIRED`.
- `INVOICE_xxx`: `INVOICE_NOT_FOUND`, `INVOICE_ALREADY_PAID`.
- `TICKET_xxx`: `TICKET_NOT_FOUND`, `TICKET_INVALID_STATUS_TRANSITION`.
- `VEHICLE_xxx`: `VEHICLE_ALREADY_REGISTERED`, `CARD_CODE_DUPLICATED`.

---

## 8. Ma trận Phân hệ Chức năng (FR-01 đến FR-27)

Khi nhận task phát triển tính năng, Agent đối soát với bảng sau:

| Phân hệ | Mã FR | Tên chức năng | Ghi chú kỹ thuật |
|:---|:---|:---|:---|
| **Cơ sở, Phòng & Giường** | FR-01 $\rightarrow$ FR-05 | Ma trận phòng giường, thêm phòng sinh giường, cập nhật, soft delete, bộ lọc | Kiểm soát `gender_policy` 100%, mã slot `RoomCode-BedCode`. |
| **Khách thuê & Phương tiện** | FR-06 $\rightarrow$ FR-08 | Hồ sơ đa đối tượng, checkout soft delete, đăng ký xe hầm & cấp thẻ từ | Không lưu MSSV/trường ĐH; lưu 2 ảnh cà vẹt; phí gửi xe 0đ. |
| **Tiếp nhận & Hợp đồng** | FR-09 $\rightarrow$ FR-13 | Direct Onboarding, tải ảnh scan hợp đồng giấy, chuyển phòng, gia hạn, checkout | Gán giường + tạo user + kích hoạt HĐ trong 1 Transaction CSDL. |
| **Chi phí Cơ sở & Lợi nhuận** | FR-14 | Ghi nhận hóa đơn tiện ích tổng, tính Lợi nhuận ròng, xuất Excel | SQL View tính Lợi nhuận ròng = Thu - Sửa chữa - Chi phí tổng. |
| **Hóa đơn & Thu tiền** | FR-15, FR-16 | Phát hành ngày 25, hạn ngày 05, bộ lọc Nợ, xác nhận tiền mặt, VietQR | Không phạt nộp trễ; Modal xác nhận tiền mặt lưu staff ID. |
| **Sự cố & Kỹ thuật** | FR-17, FR-18 | Báo hỏng 3 cấp độ, điều phối thợ, sắp xếp ưu tiên, nghiệm thu phụ tùng | Sort: Khẩn cấp (Rank 0) > Cao (Rank 1) > Bình thường (Rank 2). |
| **Kỷ luật & Vi phạm** | FR-19 | Lập biên bản vi phạm cá nhân, 5 loại vi phạm, 3 mức xử lý | Gắn với `tenant_id` (không gắn phòng); tiền phạt thu ngoài. |
| **Báo cáo & Thống kê** | FR-20 | Dashboard KPI tỷ lệ lấp đầy, doanh thu, nợ quá hạn, sự cố | 4 Card KPI tổng quan vận hành. |
| **Hệ thống & Phân quyền** | FR-21 $\rightarrow$ FR-24 | Đăng nhập Bcrypt, đăng xuất, phân quyền tinh giản 3 role, OTP | Khóa tài khoản sau 5 lần sai; OTP hiệu lực 5 phút. |
| **Cổng Khách thuê Di động** | FR-25 $\rightarrow$ FR-27 | Thẻ phòng điện tử, Tab hóa đơn VietQR động, hòm thư vi phạm | Tối ưu Mobile Web/App 360px - 430px, Bottom Navigation Bar. |

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
   - Đọc kỹ `docs/Logical-ERD.md` để lấy chính xác tên bảng, tên cột, kiểu dữ liệu, nullability và khóa ngoại.
   - Tuyệt đối không tự ý thêm các cột ngoài đặc tả (ví dụ: `university`, `student_code`, `building_id`, `late_fee_amount`).
2. **Trước khi viết Logic Nghiệp vụ trong Service:**
   - Đối soát với 11 Quy tắc nghiệp vụ cốt lõi (BR-01 đến BR-11) tại Mục 3.
   - Luôn kiểm tra ràng buộc cách ly giới tính (`BR-02`) khi xếp hoặc đổi phòng.
   - Thao tác thay đổi trạng thái giường, hợp đồng, gạch nợ hóa đơn bắt buộc phải bọc trong `@Transactional`.
3. **Trước khi tạo REST API Controller:**
   - Kế thừa hoặc sử dụng `ResponseFactory` để trả về định dạng chuẩn Envelope (`SingleResponse`, `SuccessResponse`, `PageResponse`).
   - Mọi mã lỗi ném ra phải là một `AppErrorCode` có cấu trúc rõ ràng, không ném raw String hoặc exception không kiểm soát.
4. **Trước khi khai báo hoàn thành nhiệm vụ:**
   - Chạy lệnh `./mvnw test` (hoặc `./mvnw clean compile` nếu chưa có bài test tương ứng) để chứng minh mã nguồn không có lỗi biên dịch.
   - Đảm bảo tuân thủ nguyên tắc Immutability, không hardcode giá trị nhạy cảm, không dùng lệnh SQL `DELETE` vật lý.
