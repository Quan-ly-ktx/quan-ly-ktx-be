package com.uit.dormmanagement.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.expense.CreateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.expense.UpdateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseReceiptContent;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.dtos.response.finance.EstimatedNetProfitReportResponse;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportExportFile;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportMaintenanceTicketItem;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportPaidInvoiceItem;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.dtos.response.room.RoomDetailResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.FacilityExpense;
import com.uit.dormmanagement.entities.MaintenanceTicket;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.TicketReplacementPart;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Violation;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.TicketCategory;
import com.uit.dormmanagement.enums.TicketPriority;
import com.uit.dormmanagement.enums.TicketStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.ViolationLevel;
import com.uit.dormmanagement.enums.ViolationType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.FacilityExpenseRepository;
import com.uit.dormmanagement.repositories.MaintenanceTicketRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.TicketReplacementPartRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.ViolationRepository;
import com.uit.dormmanagement.services.FacilityExpenseService;
import com.uit.dormmanagement.services.FinancialReportExportService;
import com.uit.dormmanagement.services.FinancialReportService;
import com.uit.dormmanagement.services.InvoiceService;
import com.uit.dormmanagement.services.OnboardingService;
import com.uit.dormmanagement.services.RoomService;
import com.uit.dormmanagement.services.impl.FinancialReportExportServiceImpl;
import com.uit.dormmanagement.services.impl.FinancialReportServiceImpl;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(FacilityExpenseFinancialE2ETest.TestClockConfig.class)
class FacilityExpenseFinancialE2ETest {

  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02, 0x03, 0x04};
  private static final byte[] VALID_PDF_BYTES =
      "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.flyway.baseline-on-migrate", () -> false);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    MutableTestClock testClock() {
      return new MutableTestClock();
    }
  }

  static class MutableTestClock extends Clock {
    private volatile Clock delegate = Clock.system(TimeConfig.DORM_ZONE);

    void setDormTime(String isoOffsetDateTime) {
      this.delegate =
          Clock.fixed(OffsetDateTime.parse(isoOffsetDateTime).toInstant(), TimeConfig.DORM_ZONE);
    }

    void reset() {
      this.delegate = Clock.system(TimeConfig.DORM_ZONE);
    }

    @Override
    public ZoneId getZone() {
      return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return delegate.withZone(zone);
    }

    @Override
    public Instant instant() {
      return delegate.instant();
    }
  }

  @Autowired private MutableTestClock testClock;
  @Autowired private RoomService roomService;
  @Autowired private OnboardingService onboardingService;
  @Autowired private InvoiceService invoiceService;
  @Autowired private FacilityExpenseService facilityExpenseService;
  @Autowired private FinancialReportService financialReportService;
  @Autowired private FinancialReportExportService financialReportExportService;
  @Autowired private InMemoryPrivateObjectStorage privateObjectStorage;
  @Autowired private UserRepository userRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private FacilityExpenseRepository facilityExpenseRepository;
  @Autowired private MaintenanceTicketRepository maintenanceTicketRepository;
  @Autowired private TicketReplacementPartRepository ticketReplacementPartRepository;
  @Autowired private ViolationRepository violationRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    testClock.reset();
    privateObjectStorage.clear();
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
    privateObjectStorage.clear();
    jdbcTemplate.execute(
        "TRUNCATE TABLE violations, ticket_replacement_parts, maintenance_tickets, facility_expenses, "
            + "payment_transactions, invoices, contract_documents, contract_renewals, contracts, "
            + "tenants, room_code_registry, beds, room_amenities, rooms, staff_profiles, users CASCADE");
  }

  @Test
  @DisplayName(
      "E2E Module 4.4 FR-14 Lifecycle: Cross-Module Seed (Late Invoice, Deposits/Fines Exclusion, VN Midnight Maintenance) -> 5 Expense Types, Salary Guard & Receipt Storage -> Net Profit, Edit/Delete Immediate Recalculation & Nov Attribution -> XLSX/PDF Export Consistency")
  void testCompleteFacilityExpenseAndFinancialReportLifecycle() throws IOException {
    // -------------------------------------------------------------------------
    // Stage 1: Seed Cross-Module Reality (2026-10 & 2026-11)
    // -------------------------------------------------------------------------
    User managerUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("manager.finance.e2e@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    StaffProfile managerStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(managerUser)
                .fullName("Nguyen Quan Ly E2E")
                .phone("0909333444")
                .build());
    UUID managerUserId = managerUser.getId();

    User techUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("tech.finance.e2e@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());
    StaffProfile techStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(techUser)
                .fullName("Tran Ky Thuat E2E")
                .phone("0909555666")
                .build());

    RoomDetailResponse createdRoom =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.501",
                5,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("2000000"),
                List.of()));
    Room room = roomRepository.findById(createdRoom.id()).orElseThrow();
    List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(room.getId());
    assertThat(beds).hasSize(4);
    assertThat(beds.get(0).getStatus()).isEqualTo(BedStatus.AVAILABLE);
    assertThat(beds.get(1).getStatus()).isEqualTo(BedStatus.AVAILABLE);

    testClock.setDormTime("2026-10-01T09:00:00+07:00");

    // Deposits totaling 4,000,000 must NEVER be counted in rent revenue
    OnboardingResponse tenant1Onboard =
        onboardingService.onboardTenant(
            OnboardingRequest.builder()
                .fullName("Le Van Mot")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079206222201")
                .phone("0987222001")
                .email("finance.e2e.t1@uit.edu.vn")
                .permanentAddress("100 Vo Van Ngan, Thu Duc, TP.HCM")
                .bedId(beds.get(0).getId())
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2027, 3, 31))
                .depositAmount(new BigDecimal("2000000"))
                .build());
    OnboardingResponse tenant2Onboard =
        onboardingService.onboardTenant(
            OnboardingRequest.builder()
                .fullName("Pham Van Hai")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079206222202")
                .phone("0987222002")
                .email("finance.e2e.t2@uit.edu.vn")
                .permanentAddress("200 Hoang Dieu 2, Thu Duc, TP.HCM")
                .bedId(beds.get(1).getId())
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2027, 3, 31))
                .depositAmount(new BigDecimal("2000000"))
                .build());

    UUID tenant1OctInvoiceId = tenant1Onboard.firstInvoice().id();
    String tenant1OctInvoiceCode = tenant1Onboard.firstInvoice().invoiceCode();
    assertThat(tenant1Onboard.firstInvoice().billingPeriod()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(tenant1Onboard.firstInvoice().amount()).isEqualByComparingTo("2000000");
    assertThat(tenant2Onboard.firstInvoice().billingPeriod()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(tenant2Onboard.firstInvoice().amount()).isEqualByComparingTo("2000000");
    assertThat(tenant2Onboard.firstInvoice().status()).isEqualTo(InvoiceStatus.DEBT);

    // Tenant 1 pays October invoice late on 2026-11-03 -> must count in 2026-10, NOT 2026-11
    testClock.setDormTime("2026-11-03T10:00:00+07:00");
    InvoiceDetailResponse paidOctInvoice =
        invoiceService.confirmCashPayment(managerUserId, tenant1OctInvoiceId);
    assertThat(paidOctInvoice.status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(paidOctInvoice.payment().paymentMethod()).isEqualTo(PaymentMethod.CASH);

    Instant lateNovConfirmInstant = Instant.parse("2026-11-03T03:00:00Z");
    jdbcTemplate.update(
        "UPDATE payment_transactions SET confirmed_at = ? WHERE invoice_id = ?",
        Timestamp.from(lateNovConfirmInstant),
        tenant1OctInvoiceId);

    Tenant tenant1Entity = tenantRepository.findById(tenant1Onboard.tenant().id()).orElseThrow();

    // Violation fine 500,000 on 2026-10-15 must NEVER be counted in rent revenue
    violationRepository.saveAndFlush(
        Violation.builder()
            .violationCode("VP-E2E-202610-01")
            .tenant(tenant1Entity)
            .issuedByStaff(managerStaff)
            .occurredOn(LocalDate.of(2026, 10, 15))
            .violationType(ViolationType.NOISE)
            .level(ViolationLevel.FINE)
            .fineAmount(new BigDecimal("500000"))
            .note("Phat gay on gio nghi")
            .build());

    // Ticket A: COMPLETED at 2026-10-31T16:59:59Z (23:59:59+07:00) with 2 parts (2 x 150k = 300k)
    MaintenanceTicket ticketA =
        maintenanceTicketRepository.saveAndFlush(
            MaintenanceTicket.builder()
                .ticketCode("MT-E2E-OCT-A")
                .room(room)
                .reporterTenant(tenant1Entity)
                .assigneeStaff(techStaff)
                .title("Sua o khoa cua chinh")
                .description("Khoa cua hong")
                .category(TicketCategory.FURNITURE)
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.COMPLETED)
                .resolutionNotes("Da thay khoa va tay nam")
                .resolutionImageFileKey("tickets/MT-E2E-OCT-A/res.jpg")
                .resolvedAt(Instant.parse("2026-10-31T16:59:59Z"))
                .build());
    ticketReplacementPartRepository.saveAllAndFlush(
        List.of(
            TicketReplacementPart.builder()
                .ticket(ticketA)
                .partName("Than khoa cua")
                .quantity(1)
                .unitCost(new BigDecimal("150000"))
                .build(),
            TicketReplacementPart.builder()
                .ticket(ticketA)
                .partName("Tay nam inox")
                .quantity(1)
                .unitCost(new BigDecimal("150000"))
                .build()));

    // Ticket B: COMPLETED at 2026-10-31T17:30:00Z (2026-11-01T00:30:00+07:00) -> 2026-11 ONLY
    MaintenanceTicket ticketB =
        maintenanceTicketRepository.saveAndFlush(
            MaintenanceTicket.builder()
                .ticketCode("MT-E2E-NOV-B")
                .room(room)
                .reporterTenant(tenant1Entity)
                .assigneeStaff(techStaff)
                .title("Thay bo mach dieu hoa")
                .description("Dieu hoa khong lanh")
                .category(TicketCategory.AMENITY)
                .priority(TicketPriority.URGENT)
                .status(TicketStatus.COMPLETED)
                .resolutionNotes("Da thay bo mach moi")
                .resolutionImageFileKey("tickets/MT-E2E-NOV-B/res.jpg")
                .resolvedAt(Instant.parse("2026-10-31T17:30:00Z"))
                .build());
    ticketReplacementPartRepository.saveAndFlush(
        TicketReplacementPart.builder()
            .ticket(ticketB)
            .partName("Bo mach dieu khien")
            .quantity(1)
            .unitCost(new BigDecimal("400000"))
            .build());

    // Ticket C: COMPLETED at 2026-10-10T05:00:00Z with 0 parts -> appears in 2026-10 with partsCost
    // = 0
    maintenanceTicketRepository.saveAndFlush(
        MaintenanceTicket.builder()
            .ticketCode("MT-E2E-OCT-C")
            .room(room)
            .reporterTenant(tenant1Entity)
            .assigneeStaff(techStaff)
            .title("Kiem tra duong truyen mang")
            .description("Mat ket noi mang")
            .category(TicketCategory.INTERNET)
            .priority(TicketPriority.NORMAL)
            .status(TicketStatus.COMPLETED)
            .resolutionNotes("Da bam lai dau cap")
            .resolutionImageFileKey("tickets/MT-E2E-OCT-C/res.jpg")
            .resolvedAt(Instant.parse("2026-10-10T05:00:00Z"))
            .build());

    // Ticket D: IN_PROGRESS in 2026-10 -> excluded from both periods
    MaintenanceTicket ticketD =
        maintenanceTicketRepository.saveAndFlush(
            MaintenanceTicket.builder()
                .ticketCode("MT-E2E-INPROG-D")
                .room(room)
                .reporterTenant(tenant1Entity)
                .assigneeStaff(techStaff)
                .title("Thay voi nuoc rua mat")
                .description("Voi bi ro ri")
                .category(TicketCategory.WATER)
                .priority(TicketPriority.NORMAL)
                .status(TicketStatus.IN_PROGRESS)
                .build());
    ticketReplacementPartRepository.saveAndFlush(
        TicketReplacementPart.builder()
            .ticket(ticketD)
            .partName("Voi nuoc inox")
            .quantity(1)
            .unitCost(new BigDecimal("250000"))
            .build());

    // -------------------------------------------------------------------------
    // Stage 2: Facility Expenses Lifecycle & Receipt Streaming
    // -------------------------------------------------------------------------
    FacilityExpenseResponse electricity =
        facilityExpenseService.createExpense(
            managerUserId,
            new CreateFacilityExpenseRequest(
                ExpenseType.ELECTRICITY,
                "Tiền điện EVN tháng 10/2026",
                new BigDecimal("500000"),
                LocalDate.of(2026, 10, 10),
                "EVN-202610-01",
                "Thanh toán chuyển khoản"));
    facilityExpenseService.createExpense(
        managerUserId,
        new CreateFacilityExpenseRequest(
            ExpenseType.WATER,
            "Tiền nước Sawaco tháng 10/2026",
            new BigDecimal("200000"),
            LocalDate.of(2026, 10, 12),
            "SAW-202610-01",
            null));
    facilityExpenseService.createExpense(
        managerUserId,
        new CreateFacilityExpenseRequest(
            ExpenseType.INTERNET,
            "Cước Internet cáp quang tháng 10/2026",
            new BigDecimal("150000"),
            LocalDate.of(2026, 10, 15),
            "NET-202610-01",
            null));
    FacilityExpenseResponse salary =
        facilityExpenseService.createExpense(
            managerUserId,
            new CreateFacilityExpenseRequest(
                ExpenseType.SALARY,
                "Tổng lương nhân viên tháng 10/2026",
                new BigDecimal("3000000"),
                LocalDate.of(2026, 10, 25),
                "SAL-202610-01",
                null));
    FacilityExpenseResponse other =
        facilityExpenseService.createExpense(
            managerUserId,
            new CreateFacilityExpenseRequest(
                ExpenseType.OTHER,
                "Vật tư vệ sinh tháng 10/2026",
                new BigDecimal("100000"),
                LocalDate.of(2026, 10, 28),
                "OTH-202610-01",
                null));

    UUID electricityId = electricity.id();
    UUID salaryId = salary.id();
    UUID otherId = other.id();

    // Second SALARY in 2026-10 blocked with 409 EXPENSE_409_SALARY_MONTH
    CreateFacilityExpenseRequest duplicateSalaryRequest =
        new CreateFacilityExpenseRequest(
            ExpenseType.SALARY,
            "Lương bổ sung tháng 10/2026",
            new BigDecimal("500000"),
            LocalDate.of(2026, 10, 29),
            null,
            null);
    assertThatThrownBy(
            () -> facilityExpenseService.createExpense(managerUserId, duplicateSalaryRequest))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> {
              BaseAppException bae = (BaseAppException) ex;
              return bae.getErrorCode() == ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS
                  && "EXPENSE_409_SALARY_MONTH".equals(bae.getErrorCode().getErrorCode())
                  && bae.getErrorCode().getHttpStatus() == HttpStatus.CONFLICT;
            });

    // Upload PNG receipt -> verify storage + streaming
    MockMultipartFile pngReceipt =
        new MockMultipartFile("file", "evn-oct.png", "image/png", VALID_PNG_BYTES);
    FacilityExpenseResponse withPng =
        facilityExpenseService.uploadReceipt(electricityId, pngReceipt);
    assertThat(withPng.receipt()).isNotNull();
    assertThat(withPng.receipt().available()).isTrue();
    assertThat(withPng.receipt().contentUrl())
        .isEqualTo("/api/v1/admin/expenses/" + electricityId + "/receipt/content");

    FacilityExpense pngEntity = facilityExpenseRepository.findById(electricityId).orElseThrow();
    String pngKey = pngEntity.getInvoiceFileKey();
    assertThat(pngKey).startsWith("expenses/" + electricityId + "/receipt/").endsWith(".png");
    assertThat(privateObjectStorage.exists(pngKey)).isTrue();

    FacilityExpenseReceiptContent streamedPng =
        facilityExpenseService.loadReceiptContent(electricityId);
    assertThat(streamedPng.contentType()).isEqualTo("image/png");
    assertThat(streamedPng.filename()).isEqualTo("expense-" + electricityId + "-receipt.png");
    assertThat(streamedPng.contentLength()).isEqualTo(VALID_PNG_BYTES.length);
    try (InputStream is = streamedPng.resource().getInputStream()) {
      assertThat(is.readAllBytes()).isEqualTo(VALID_PNG_BYTES);
    }

    // Replace with PDF receipt -> verify old PNG key deleted afterCommit & new PDF streamed
    MockMultipartFile pdfReceipt =
        new MockMultipartFile("file", "evn-oct.pdf", "application/pdf", VALID_PDF_BYTES);
    FacilityExpenseResponse withPdf =
        facilityExpenseService.uploadReceipt(electricityId, pdfReceipt);
    assertThat(withPdf.receipt()).isNotNull();
    assertThat(withPdf.receipt().available()).isTrue();

    FacilityExpense pdfEntity = facilityExpenseRepository.findById(electricityId).orElseThrow();
    String pdfKey = pdfEntity.getInvoiceFileKey();
    assertThat(pdfKey).isNotEqualTo(pngKey).endsWith(".pdf");
    assertThat(privateObjectStorage.exists(pdfKey)).isTrue();
    assertThat(privateObjectStorage.exists(pngKey)).isFalse();
    assertThatThrownBy(() -> privateObjectStorage.getObject(pngKey))
        .isInstanceOf(ObjectStorageNotFoundException.class);

    FacilityExpenseReceiptContent streamedPdf =
        facilityExpenseService.loadReceiptContent(electricityId);
    assertThat(streamedPdf.contentType()).isEqualTo("application/pdf");
    assertThat(streamedPdf.filename()).isEqualTo("expense-" + electricityId + "-receipt.pdf");
    assertThat(streamedPdf.contentLength()).isEqualTo(VALID_PDF_BYTES.length);
    try (InputStream is = streamedPdf.resource().getInputStream()) {
      assertThat(is.readAllBytes()).isEqualTo(VALID_PDF_BYTES);
    }

    // Delete receipt -> verify PDF key deleted afterCommit
    FacilityExpenseResponse afterDeleteReceipt =
        facilityExpenseService.deleteReceipt(electricityId);
    assertThat(afterDeleteReceipt.receipt()).isNull();
    assertThat(facilityExpenseRepository.findById(electricityId).orElseThrow().getInvoiceFileKey())
        .isNull();
    assertThat(privateObjectStorage.exists(pdfKey)).isFalse();
    assertThatThrownBy(() -> privateObjectStorage.getObject(pdfKey))
        .isInstanceOf(ObjectStorageNotFoundException.class);

    // -------------------------------------------------------------------------
    // Stage 3: Estimated Net Profit Formula, Negative Profit & Immediate Recalculation
    // -------------------------------------------------------------------------
    EstimatedNetProfitReportResponse octBeforeEdit =
        financialReportService.getEstimatedNetProfitReport("2026-10");

    assertThat(octBeforeEdit.period()).isEqualTo("2026-10");
    assertThat(octBeforeEdit.periodStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(octBeforeEdit.periodEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    assertThat(octBeforeEdit.label()).isEqualTo("Lợi nhuận ròng (ước tính)");
    assertThat(octBeforeEdit.disclaimer())
        .isEqualTo(
            "Ước tính dựa trên các khoản thu và chi đã được ghi nhận; có thể chưa bao gồm đầy đủ chi phí, thuế, khấu hao và nghĩa vụ khác.");
    assertThat(octBeforeEdit.expectedRentRevenue()).isEqualByComparingTo("4000000");
    assertThat(octBeforeEdit.collectedRentRevenue()).isEqualByComparingTo("2000000");
    assertThat(octBeforeEdit.totalInvoiceCount()).isEqualTo(2);
    assertThat(octBeforeEdit.paidInvoiceCount()).isEqualTo(1);
    assertThat(octBeforeEdit.paidInvoices())
        .extracting(FinancialReportPaidInvoiceItem::invoiceCode)
        .containsExactly(tenant1OctInvoiceCode);
    assertThat(octBeforeEdit.maintenancePartsCost()).isEqualByComparingTo("300000");
    assertThat(octBeforeEdit.completedTicketCount()).isEqualTo(2);
    assertThat(octBeforeEdit.completedMaintenanceTickets())
        .extracting(
            FinancialReportMaintenanceTicketItem::ticketCode,
            FinancialReportMaintenanceTicketItem::partsCost)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("MT-E2E-OCT-A", new BigDecimal("300000")),
            org.assertj.core.groups.Tuple.tuple("MT-E2E-OCT-C", BigDecimal.ZERO));
    assertThat(octBeforeEdit.facilityExpenseTotal()).isEqualByComparingTo("3950000");
    assertThat(octBeforeEdit.facilityExpenseCount()).isEqualTo(5);
    assertThat(octBeforeEdit.totalExpenses()).isEqualByComparingTo("4250000");
    assertThat(octBeforeEdit.estimatedNetProfit()).isEqualByComparingTo("-2250000");
    assertThat(octBeforeEdit.expenseBreakdownByType()).containsOnlyKeys(ExpenseType.values());
    assertThat(octBeforeEdit.expenseBreakdownByType().get(ExpenseType.ELECTRICITY))
        .isEqualByComparingTo("500000");
    assertThat(octBeforeEdit.expenseBreakdownByType().get(ExpenseType.WATER))
        .isEqualByComparingTo("200000");
    assertThat(octBeforeEdit.expenseBreakdownByType().get(ExpenseType.INTERNET))
        .isEqualByComparingTo("150000");
    assertThat(octBeforeEdit.expenseBreakdownByType().get(ExpenseType.SALARY))
        .isEqualByComparingTo("3000000");
    assertThat(octBeforeEdit.expenseBreakdownByType().get(ExpenseType.OTHER))
        .isEqualByComparingTo("100000");

    // Edit SALARY in-place from 3,000,000 -> 1,200,000 AND delete OTHER (100,000)
    FacilityExpenseResponse updatedSalary =
        facilityExpenseService.updateExpense(
            salaryId,
            new UpdateFacilityExpenseRequest(
                ExpenseType.SALARY,
                "Tổng lương nhân viên tháng 10/2026 (điều chỉnh)",
                new BigDecimal("1200000"),
                LocalDate.of(2026, 10, 25),
                "SAL-202610-01",
                "Điều chỉnh ca trực"));
    assertThat(updatedSalary.amount()).isEqualByComparingTo("1200000");

    facilityExpenseService.deleteExpense(otherId);

    EstimatedNetProfitReportResponse octAfterEdit =
        financialReportService.getEstimatedNetProfitReport("2026-10");
    assertThat(octAfterEdit.collectedRentRevenue()).isEqualByComparingTo("2000000");
    assertThat(octAfterEdit.maintenancePartsCost()).isEqualByComparingTo("300000");
    assertThat(octAfterEdit.facilityExpenseTotal()).isEqualByComparingTo("2050000");
    assertThat(octAfterEdit.facilityExpenseCount()).isEqualTo(4);
    assertThat(octAfterEdit.totalExpenses()).isEqualByComparingTo("2350000");
    assertThat(octAfterEdit.estimatedNetProfit()).isEqualByComparingTo("-350000");
    assertThat(octAfterEdit.expenseBreakdownByType().get(ExpenseType.SALARY))
        .isEqualByComparingTo("1200000");
    assertThat(octAfterEdit.expenseBreakdownByType().get(ExpenseType.OTHER))
        .isEqualByComparingTo("0");

    // November 2026: late October payment excluded, VN-midnight Ticket B included -> -400,000
    EstimatedNetProfitReportResponse novReport =
        financialReportService.getEstimatedNetProfitReport("2026-11");
    assertThat(novReport.expectedRentRevenue()).isEqualByComparingTo("0");
    assertThat(novReport.collectedRentRevenue()).isEqualByComparingTo("0");
    assertThat(novReport.totalInvoiceCount()).isZero();
    assertThat(novReport.paidInvoiceCount()).isZero();
    assertThat(novReport.maintenancePartsCost()).isEqualByComparingTo("400000");
    assertThat(novReport.completedTicketCount()).isEqualTo(1);
    assertThat(novReport.completedMaintenanceTickets())
        .extracting(FinancialReportMaintenanceTicketItem::ticketCode)
        .containsExactly("MT-E2E-NOV-B");
    assertThat(novReport.facilityExpenseTotal()).isEqualByComparingTo("0");
    assertThat(novReport.totalExpenses()).isEqualByComparingTo("400000");
    assertThat(novReport.estimatedNetProfit()).isEqualByComparingTo("-400000");

    // -------------------------------------------------------------------------
    // Stage 4: Export Consistency Verification (XLSX & PDF)
    // -------------------------------------------------------------------------
    FinancialReportExportFile xlsxFile =
        financialReportExportService.exportEstimatedNetProfitReport("2026-10", "XLSX");
    assertThat(xlsxFile.contentType())
        .isEqualTo(FinancialReportExportServiceImpl.XLSX_CONTENT_TYPE);
    assertThat(xlsxFile.filename()).isEqualTo("thu-chi-2026-10.xlsx");
    assertThat(new String(xlsxFile.content(), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxFile.content()))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
      Sheet summarySheet = workbook.getSheet("Tong hop thu chi");
      assertThat(summarySheet).isNotNull();
      assertThat(summarySheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("2026-10");

      assertNumericCellEquals(summarySheet, 6, 1, 4_000_000d);
      assertNumericCellEquals(summarySheet, 6, 2, 2d);
      assertNumericCellEquals(summarySheet, 7, 1, 2_000_000d);
      assertNumericCellEquals(summarySheet, 7, 2, 1d);
      assertNumericCellEquals(summarySheet, 8, 1, 300_000d);
      assertNumericCellEquals(summarySheet, 8, 2, 2d);
      assertNumericCellEquals(summarySheet, 9, 1, 2_050_000d);
      assertNumericCellEquals(summarySheet, 9, 2, 4d);
      assertNumericCellEquals(summarySheet, 10, 1, 2_350_000d);
      assertThat(summarySheet.getRow(11).getCell(0).getStringCellValue())
          .isEqualTo(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL);
      assertNumericCellEquals(summarySheet, 11, 1, -350_000d);

      assertNumericCellEquals(summarySheet, 15, 2, 500_000d);
      assertNumericCellEquals(summarySheet, 16, 2, 200_000d);
      assertNumericCellEquals(summarySheet, 17, 2, 150_000d);
      assertNumericCellEquals(summarySheet, 18, 2, 1_200_000d);
      assertNumericCellEquals(summarySheet, 19, 2, 0d);

      assertThat(summarySheet.getRow(21).getCell(1).getStringCellValue())
          .isEqualTo(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER);

      assertThat(workbook.getSheet("Chi tiet chi phi").getLastRowNum()).isEqualTo(4);
      assertThat(workbook.getSheet("Hoa don da thu").getLastRowNum()).isEqualTo(1);
      assertThat(workbook.getSheet("Sua chua hoan thanh").getLastRowNum()).isEqualTo(2);
    }

    FinancialReportExportFile pdfFile =
        financialReportExportService.exportEstimatedNetProfitReport("2026-10", "PDF");
    assertThat(pdfFile.contentType()).isEqualTo(FinancialReportExportServiceImpl.PDF_CONTENT_TYPE);
    assertThat(pdfFile.filename()).isEqualTo("thu-chi-2026-10.pdf");
    assertThat(new String(pdfFile.content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

    try (PdfReader reader = new PdfReader(pdfFile.content())) {
      assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      StringBuilder rawText = new StringBuilder();
      for (int page = 1; page <= reader.getNumberOfPages(); page++) {
        rawText.append(extractor.getTextFromPage(page)).append('\n');
      }
      String normalizedText =
          Normalizer.normalize(rawText.toString(), Normalizer.Form.NFC)
              .replaceAll("\\s+", " ")
              .trim();

      assertThat(normalizedText).contains("2026-10");
      assertThat(normalizedText).contains(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL);
      assertThat(normalizedText)
          .contains(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER);
      assertThat(normalizedText).contains("4.000.000 đ");
      assertThat(normalizedText).contains("2.000.000 đ");
      assertThat(normalizedText).contains("300.000 đ");
      assertThat(normalizedText).contains("2.050.000 đ");
      assertThat(normalizedText).contains("2.350.000 đ");
      assertThat(normalizedText).contains("-350.000 đ");
    }
  }

  private static void assertNumericCellEquals(
      Sheet sheet, int rowIndex, int colIndex, double expected) {
    Cell cell = sheet.getRow(rowIndex).getCell(colIndex);
    assertThat(cell).isNotNull();
    assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
    assertThat(cell.getNumericCellValue()).isEqualTo(expected);
  }
}
