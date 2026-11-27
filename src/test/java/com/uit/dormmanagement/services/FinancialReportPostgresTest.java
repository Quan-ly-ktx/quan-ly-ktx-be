package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.expense.CreateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.response.finance.EstimatedNetProfitReportResponse;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportMaintenanceTicketItem;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportPaidInvoiceItem;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.MaintenanceTicket;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.TicketReplacementPart;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Violation;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.TicketCategory;
import com.uit.dormmanagement.enums.TicketPriority;
import com.uit.dormmanagement.enums.TicketStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.ViolationLevel;
import com.uit.dormmanagement.enums.ViolationType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.MaintenanceTicketRepository;
import com.uit.dormmanagement.repositories.PaymentTransactionRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.TicketReplacementPartRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.ViolationRepository;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(FinancialReportPostgresTest.TestClockConfig.class)
class FinancialReportPostgresTest {

  private static final Instant DEFAULT_CLOCK_INSTANT = Instant.parse("2026-10-20T05:00:00Z");

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
    private volatile Clock delegate = Clock.fixed(DEFAULT_CLOCK_INSTANT, TimeConfig.DORM_ZONE);

    void setInstant(Instant instant) {
      this.delegate = Clock.fixed(instant, TimeConfig.DORM_ZONE);
    }

    void reset() {
      this.delegate = Clock.fixed(DEFAULT_CLOCK_INSTANT, TimeConfig.DORM_ZONE);
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
  @Autowired private FinancialReportService financialReportService;
  @Autowired private FacilityExpenseService facilityExpenseService;
  @Autowired private RoomService roomService;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentTransactionRepository paymentTransactionRepository;
  @Autowired private MaintenanceTicketRepository maintenanceTicketRepository;
  @Autowired private TicketReplacementPartRepository ticketReplacementPartRepository;
  @Autowired private ViolationRepository violationRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Room room;
  private List<Bed> beds;
  private StaffProfile managerStaff;
  private StaffProfile techStaff;

  @BeforeEach
  void setUp() {
    testClock.reset();

    var roomDto =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.960",
                9,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    room = roomRepository.findById(roomDto.id()).orElseThrow();
    beds = bedRepository.findByRoomIdOrderByBedCodeAsc(room.getId());

    User managerUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("manager.fin.pg@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    managerStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(managerUser)
                .fullName("Nguyen Quan Ly Tai Chinh")
                .phone("0909600111")
                .build());

    User techUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("tech.fin.pg@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());
    techStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(techUser)
                .fullName("Tran Ky Thuat Vien")
                .phone("0909600222")
                .build());
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
    jdbcTemplate.execute(
        "TRUNCATE TABLE violations, ticket_replacement_parts, maintenance_tickets, facility_expenses, "
            + "payment_transactions, invoices, contract_documents, contract_renewals, contracts, "
            + "room_code_registry, beds, room_amenities, rooms, tenants, staff_profiles, users CASCADE");
  }

  private Tenant createTenant(String suffix, String idCard, String phone) {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .email("tenant.fin." + suffix + "@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    return tenantRepository.saveAndFlush(
        Tenant.builder()
            .user(user)
            .tenantCode("NT-FIN-" + suffix)
            .fullName("Nguyen Tenant " + suffix)
            .gender(GenderPolicy.MALE)
            .idCardNumber(idCard)
            .phone(phone)
            .permanentAddress("Thu Duc, TP.HCM")
            .residentStatus(ResidentStatus.ACTIVE)
            .build());
  }

  private Contract createActiveContract(String code, Tenant tenant, Bed bed, BigDecimal deposit) {
    return contractRepository.saveAndFlush(
        Contract.builder()
            .contractCode(code)
            .tenant(tenant)
            .bed(bed)
            .roomCodeSnapshot(room.getRoomNumber())
            .bedCodeSnapshot(bed.getBedCode())
            .startOn(LocalDate.of(2026, 9, 1))
            .originalEndOn(LocalDate.of(2027, 2, 28))
            .originalMonthlyRent(new BigDecimal("1500000"))
            .depositAmount(deposit)
            .status(ContractStatus.ACTIVE)
            .build());
  }

  private PaymentTransaction createPaidTransaction(
      Invoice invoice, String receiptCode, PaymentMethod method, Instant confirmedAt) {
    PaymentTransaction tx =
        paymentTransactionRepository.saveAndFlush(
            PaymentTransaction.builder()
                .invoice(invoice)
                .receiptCode(receiptCode)
                .amount(invoice.getAmount())
                .paymentMethod(method)
                .confirmedByStaff(managerStaff)
                .build());
    jdbcTemplate.update(
        "UPDATE payment_transactions SET confirmed_at = ? WHERE id = ?",
        Timestamp.from(confirmedAt),
        tx.getId());
    return tx;
  }

  @Test
  @DisplayName(
      "Late payment confirmed in later month belongs to original billingPeriod; DEBT/PENDING_CONFIRMATION excluded from collectedRentRevenue; deposits and violation fines strictly excluded")
  void revenueAttributionAndStrictExclusions_workOnPostgres() {
    Tenant t1 = createTenant("01", "079203960001", "0901960001");
    Tenant t2 = createTenant("02", "079203960002", "0901960002");
    Tenant t3 = createTenant("03", "079203960003", "0901960003");
    Tenant t4 = createTenant("04", "079203960004", "0901960004");

    // Deposits totaling 6,500,000 must never appear in rent revenue
    Contract c1 = createActiveContract("HD-2026-9601", t1, beds.get(0), new BigDecimal("1500000"));
    Contract c2 = createActiveContract("HD-2026-9602", t2, beds.get(1), new BigDecimal("2000000"));
    Contract c3 = createActiveContract("HD-2026-9603", t3, beds.get(2), new BigDecimal("1500000"));
    Contract c4 = createActiveContract("HD-2026-9604", t4, beds.get(3), new BigDecimal("1500000"));

    // Violation fine in 2026-10 must strictly be excluded
    violationRepository.saveAndFlush(
        Violation.builder()
            .violationCode("VP-2026-9601")
            .tenant(t1)
            .issuedByStaff(managerStaff)
            .occurredOn(LocalDate.of(2026, 10, 12))
            .violationType(ViolationType.NOISE)
            .level(ViolationLevel.FINE)
            .fineAmount(new BigDecimal("500000"))
            .note("Phat gay on")
            .build());

    // 1. Oct invoice paid in Oct
    Invoice octPaidOnTime =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202610-001")
                .contract(c1)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 10, 5))
                .status(InvoiceStatus.PAID)
                .build());
    Instant octConfirmTime = Instant.parse("2026-10-08T03:00:00Z");
    createPaidTransaction(octPaidOnTime, "PT-202610-001", PaymentMethod.CASH, octConfirmTime);

    // 2. Oct invoice paid LATE in Nov -> must still belong to 2026-10, NOT 2026-11
    Invoice octPaidLate =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202610-002")
                .contract(c2)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 10, 5))
                .status(InvoiceStatus.PAID)
                .build());
    Instant novLateConfirmTime = Instant.parse("2026-11-12T04:00:00Z");
    createPaidTransaction(
        octPaidLate, "PT-202610-002", PaymentMethod.VIETQR_TRANSFER, novLateConfirmTime);

    // 3. Oct invoice PENDING_CONFIRMATION -> expected only, NOT collected
    invoiceRepository.saveAndFlush(
        Invoice.builder()
            .invoiceCode("INV-202610-003")
            .contract(c3)
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 10, 5))
            .status(InvoiceStatus.PENDING_CONFIRMATION)
            .proofFileKey("invoices/proof-9603.jpg")
            .proofSubmittedAt(Instant.parse("2026-10-15T06:00:00Z"))
            .build());

    // 4. Oct invoice DEBT -> expected only, NOT collected
    invoiceRepository.saveAndFlush(
        Invoice.builder()
            .invoiceCode("INV-202610-004")
            .contract(c4)
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 10, 5))
            .status(InvoiceStatus.DEBT)
            .build());

    // 5. Nov invoice PAID in Nov -> belongs only to 2026-11
    Invoice novPaid =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202611-001")
                .contract(c1)
                .billingPeriod(LocalDate.of(2026, 11, 1))
                .amount(new BigDecimal("1600000"))
                .dueOn(LocalDate.of(2026, 11, 5))
                .status(InvoiceStatus.PAID)
                .build());
    createPaidTransaction(
        novPaid, "PT-202611-001", PaymentMethod.CASH, Instant.parse("2026-11-05T02:00:00Z"));

    EstimatedNetProfitReportResponse octReport =
        financialReportService.getEstimatedNetProfitReport("2026-10");
    assertThat(octReport.period()).isEqualTo("2026-10");
    assertThat(octReport.periodStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(octReport.periodEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    assertThat(octReport.totalInvoiceCount()).isEqualTo(4);
    assertThat(octReport.paidInvoiceCount()).isEqualTo(2);
    assertThat(octReport.expectedRentRevenue()).isEqualByComparingTo("6000000");
    assertThat(octReport.collectedRentRevenue()).isEqualByComparingTo("3000000");
    assertThat(octReport.estimatedNetProfit()).isEqualByComparingTo("3000000");
    assertThat(octReport.paidInvoices())
        .extracting(FinancialReportPaidInvoiceItem::invoiceCode)
        .containsExactly("INV-202610-002", "INV-202610-001");
    assertThat(octReport.paidInvoices().getFirst().paymentMethod())
        .isEqualTo(PaymentMethod.VIETQR_TRANSFER);
    assertThat(octReport.paidInvoices().getFirst().confirmedAt()).isEqualTo(novLateConfirmTime);
    assertThat(octReport.paidInvoices().getFirst().roomNumber()).isEqualTo("P.960");

    // 2026-11 must NOT pull in octPaidLate even though confirmedAt was in Nov
    EstimatedNetProfitReportResponse novReport =
        financialReportService.getEstimatedNetProfitReport("2026-11");
    assertThat(novReport.totalInvoiceCount()).isEqualTo(1);
    assertThat(novReport.paidInvoiceCount()).isEqualTo(1);
    assertThat(novReport.expectedRentRevenue()).isEqualByComparingTo("1600000");
    assertThat(novReport.collectedRentRevenue()).isEqualByComparingTo("1600000");
    assertThat(novReport.paidInvoices())
        .extracting(FinancialReportPaidInvoiceItem::invoiceCode)
        .containsExactly("INV-202611-001");
  }

  @Test
  @DisplayName(
      "Asia/Ho_Chi_Minh UTC midnight boundary routes maintenance tickets to correct month and includes zero-part tickets with partsCost = 0")
  void maintenanceTimezoneBoundaryAndZeroParts_workOnPostgres() {
    Tenant reporter = createTenant("mt", "079203960010", "0901960010");

    // 2026-10-31T16:59:59Z == 2026-10-31T23:59:59+07:00 -> counts in 2026-10
    MaintenanceTicket octLateNight =
        maintenanceTicketRepository.saveAndFlush(
            MaintenanceTicket.builder()
                .ticketCode("MT-OCT-END")
                .room(room)
                .reporterTenant(reporter)
                .assigneeStaff(techStaff)
                .title("Thay khoa cua")
                .description("Khoa ket")
                .category(TicketCategory.FURNITURE)
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.COMPLETED)
                .resolutionNotes("Da thay khoa")
                .resolutionImageFileKey("tickets/MT-OCT-END/res.jpg")
                .resolvedAt(Instant.parse("2026-10-31T16:59:59Z"))
                .build());
    ticketReplacementPartRepository.saveAllAndFlush(
        List.of(
            TicketReplacementPart.builder()
                .ticket(octLateNight)
                .partName("O khoa tay gat")
                .quantity(1)
                .unitCost(new BigDecimal("120000"))
                .build(),
            TicketReplacementPart.builder()
                .ticket(octLateNight)
                .partName("Oc vit")
                .quantity(2)
                .unitCost(new BigDecimal("15000"))
                .build()));

    // 2026-09-30T17:00:00Z == 2026-10-01T00:00:00+07:00 with 0 replacement parts -> counts in
    // 2026-10
    maintenanceTicketRepository.saveAndFlush(
        MaintenanceTicket.builder()
            .ticketCode("MT-OCT-ZERO")
            .room(room)
            .reporterTenant(reporter)
            .assigneeStaff(techStaff)
            .title("Kiem tra wifi")
            .description("Mang chap chon")
            .category(TicketCategory.INTERNET)
            .priority(TicketPriority.NORMAL)
            .status(TicketStatus.COMPLETED)
            .resolutionNotes("Da khoi dong lai router")
            .resolutionImageFileKey("tickets/MT-OCT-ZERO/res.jpg")
            .resolvedAt(Instant.parse("2026-09-30T17:00:00Z"))
            .build());

    // 2026-10-31T17:30:00Z == 2026-11-01T00:30:00+07:00 -> counts in 2026-11, NOT 2026-10
    MaintenanceTicket novBoundary =
        maintenanceTicketRepository.saveAndFlush(
            MaintenanceTicket.builder()
                .ticketCode("MT-NOV-START")
                .room(room)
                .reporterTenant(reporter)
                .assigneeStaff(techStaff)
                .title("Thay bong den LED")
                .description("Chay bong")
                .category(TicketCategory.ELECTRIC)
                .priority(TicketPriority.NORMAL)
                .status(TicketStatus.COMPLETED)
                .resolutionNotes("Da thay bong moi")
                .resolutionImageFileKey("tickets/MT-NOV-START/res.jpg")
                .resolvedAt(Instant.parse("2026-10-31T17:30:00Z"))
                .build());
    ticketReplacementPartRepository.saveAndFlush(
        TicketReplacementPart.builder()
            .ticket(novBoundary)
            .partName("Bong den LED 18W")
            .quantity(2)
            .unitCost(new BigDecimal("85000"))
            .build());

    // IN_PROGRESS ticket -> excluded from both months
    maintenanceTicketRepository.saveAndFlush(
        MaintenanceTicket.builder()
            .ticketCode("MT-INPROG")
            .room(room)
            .reporterTenant(reporter)
            .assigneeStaff(techStaff)
            .title("Dang xu ly")
            .description("Chua hoan thanh")
            .category(TicketCategory.OTHER)
            .priority(TicketPriority.NORMAL)
            .status(TicketStatus.IN_PROGRESS)
            .build());

    EstimatedNetProfitReportResponse octReport =
        financialReportService.getEstimatedNetProfitReport("2026-10");
    assertThat(octReport.completedTicketCount()).isEqualTo(2);
    assertThat(octReport.maintenancePartsCost()).isEqualByComparingTo("150000");
    assertThat(octReport.completedMaintenanceTickets())
        .extracting(FinancialReportMaintenanceTicketItem::ticketCode)
        .containsExactly("MT-OCT-END", "MT-OCT-ZERO");
    assertThat(octReport.completedMaintenanceTickets().get(0).partsCost())
        .isEqualByComparingTo("150000");
    assertThat(octReport.completedMaintenanceTickets().get(1).partsCost())
        .isEqualByComparingTo("0");

    EstimatedNetProfitReportResponse novReport =
        financialReportService.getEstimatedNetProfitReport("2026-11");
    assertThat(novReport.completedTicketCount()).isEqualTo(1);
    assertThat(novReport.maintenancePartsCost()).isEqualByComparingTo("170000");
    assertThat(novReport.completedMaintenanceTickets())
        .extracting(FinancialReportMaintenanceTicketItem::ticketCode)
        .containsExactly("MT-NOV-START");
  }

  @Test
  @DisplayName(
      "Negative net profit, all 5 ExpenseType breakdown keys, exact label, and exact Vietnamese disclaimer work end-to-end")
  void negativeNetProfitAllFiveExpenseTypesAndExactDisclaimer_workOnPostgres() {
    Tenant tenant = createTenant("neg", "079203960020", "0901960020");
    Contract contract =
        createActiveContract("HD-2026-9620", tenant, beds.get(0), new BigDecimal("1500000"));
    Invoice paidInvoice =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202610-NEG")
                .contract(contract)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 10, 5))
                .status(InvoiceStatus.PAID)
                .build());
    createPaidTransaction(
        paidInvoice, "PT-202610-NEG", PaymentMethod.CASH, Instant.parse("2026-10-05T03:00:00Z"));

    MaintenanceTicket ticket =
        maintenanceTicketRepository.saveAndFlush(
            MaintenanceTicket.builder()
                .ticketCode("MT-202610-NEG")
                .room(room)
                .reporterTenant(tenant)
                .assigneeStaff(techStaff)
                .title("Sua ong nuoc")
                .description("Vo ong")
                .category(TicketCategory.WATER)
                .priority(TicketPriority.URGENT)
                .status(TicketStatus.COMPLETED)
                .resolutionNotes("Da thay ong")
                .resolutionImageFileKey("tickets/MT-202610-NEG/res.jpg")
                .resolvedAt(Instant.parse("2026-10-12T04:00:00Z"))
                .build());
    ticketReplacementPartRepository.saveAndFlush(
        TicketReplacementPart.builder()
            .ticket(ticket)
            .partName("Ong nhua PVC")
            .quantity(2)
            .unitCost(new BigDecimal("100000"))
            .build());

    UUID managerUserId = managerStaff.getUser().getId();
    facilityExpenseService.createExpense(
        managerUserId,
        new CreateFacilityExpenseRequest(
            ExpenseType.ELECTRICITY,
            "Tien dien EVN T10",
            new BigDecimal("1000000"),
            LocalDate.of(2026, 10, 10),
            "EVN-10",
            null));
    facilityExpenseService.createExpense(
        managerUserId,
        new CreateFacilityExpenseRequest(
            ExpenseType.SALARY,
            "Luong nhan vien T10",
            new BigDecimal("3000000"),
            LocalDate.of(2026, 10, 25),
            "SAL-10",
            null));
    facilityExpenseService.createExpense(
        managerUserId,
        new CreateFacilityExpenseRequest(
            ExpenseType.OTHER,
            "Ve sinh dinh ky",
            new BigDecimal("500000"),
            LocalDate.of(2026, 10, 28),
            null,
            null));

    EstimatedNetProfitReportResponse report =
        financialReportService.getEstimatedNetProfitReport("2026-10");

    assertThat(report.label()).isEqualTo("Lợi nhuận ròng (ước tính)");
    assertThat(report.disclaimer())
        .isEqualTo(
            "Ước tính dựa trên các khoản thu và chi đã được ghi nhận; có thể chưa bao gồm đầy đủ chi phí, thuế, khấu hao và nghĩa vụ khác.");
    assertThat(report.collectedRentRevenue()).isEqualByComparingTo("1500000");
    assertThat(report.maintenancePartsCost()).isEqualByComparingTo("200000");
    assertThat(report.facilityExpenseTotal()).isEqualByComparingTo("4500000");
    assertThat(report.totalExpenses()).isEqualByComparingTo("4700000");
    assertThat(report.estimatedNetProfit()).isEqualByComparingTo("-3200000");

    assertThat(report.expenseBreakdownByType()).containsOnlyKeys(ExpenseType.values());
    assertThat(report.expenseBreakdownByType().get(ExpenseType.ELECTRICITY))
        .isEqualByComparingTo("1000000");
    assertThat(report.expenseBreakdownByType().get(ExpenseType.WATER)).isEqualByComparingTo("0");
    assertThat(report.expenseBreakdownByType().get(ExpenseType.INTERNET)).isEqualByComparingTo("0");
    assertThat(report.expenseBreakdownByType().get(ExpenseType.SALARY))
        .isEqualByComparingTo("3000000");
    assertThat(report.expenseBreakdownByType().get(ExpenseType.OTHER))
        .isEqualByComparingTo("500000");

    assertThat(report.facilityExpenseCount()).isEqualTo(3);
    assertThat(report.facilityExpenses()).hasSize(3);
    assertThat(report.facilityExpenses().getFirst().recordedByStaffName())
        .isEqualTo("Nguyen Quan Ly Tai Chinh");
  }

  @Test
  @DisplayName(
      "Null/blank period resolves current Asia/Ho_Chi_Minh month from Clock; invalid period throws 400 EXPENSE_400_INVALID_PERIOD")
  void periodResolutionViaClockAndValidation_workCorrectly() {
    // 2026-10-31T17:30:00Z is already 2026-11-01T00:30:00+07:00 in Asia/Ho_Chi_Minh
    testClock.setInstant(Instant.parse("2026-10-31T17:30:00Z"));

    EstimatedNetProfitReportResponse defaultPeriodReport =
        financialReportService.getEstimatedNetProfitReport(null);
    assertThat(defaultPeriodReport.period()).isEqualTo("2026-11");
    assertThat(defaultPeriodReport.periodStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(defaultPeriodReport.periodEndDate()).isEqualTo(LocalDate.of(2026, 11, 30));

    EstimatedNetProfitReportResponse blankPeriodReport =
        financialReportService.getEstimatedNetProfitReport("   ");
    assertThat(blankPeriodReport.period()).isEqualTo("2026-11");

    EstimatedNetProfitReportResponse trimmedPeriodReport =
        financialReportService.getEstimatedNetProfitReport(" 2026-10 ");
    assertThat(trimmedPeriodReport.period()).isEqualTo("2026-10");

    for (String invalid : List.of("2026-13", "2026-00", "2026/10", "2026-1", "invalid")) {
      assertThatThrownBy(() -> financialReportService.getEstimatedNetProfitReport(invalid))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.INVALID_REPORT_PERIOD);
    }
  }
}
