package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.uit.dormmanagement.dtos.request.expense.CreateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.expense.UpdateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseReceiptContent;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.FacilityExpense;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.MaintenanceTicket;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.TicketReplacementPart;
import com.uit.dormmanagement.entities.User;
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
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.DataIntegrityViolationClassifier;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.FacilityExpenseRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.MaintenanceTicketRepository;
import com.uit.dormmanagement.repositories.PaymentTransactionRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.TicketReplacementPartRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class FacilityExpensePostgresTest {

  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};
  private static final byte[] VALID_PDF_BYTES = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes();

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

  @Autowired private FacilityExpenseService facilityExpenseService;
  @Autowired private FacilityExpenseRepository facilityExpenseRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private MaintenanceTicketRepository maintenanceTicketRepository;
  @Autowired private TicketReplacementPartRepository ticketReplacementPartRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private RoomService roomService;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private PaymentTransactionRepository paymentTransactionRepository;
  @Autowired private InMemoryPrivateObjectStorage privateObjectStorage;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  private TransactionTemplate requiredTx;
  private TransactionTemplate notSupportedTx;
  private StaffProfile managerStaff;
  private StaffProfile techStaff;
  private Room room;
  private List<Bed> beds;

  @BeforeEach
  void setUp() {
    privateObjectStorage.clear();

    requiredTx = new TransactionTemplate(transactionManager);
    requiredTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

    notSupportedTx = new TransactionTemplate(transactionManager);
    notSupportedTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

    var roomDto =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.950",
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
                .email("manager.expense.pg@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    managerStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(managerUser)
                .fullName("Tran Quan Ly PG")
                .phone("0909555111")
                .build());

    User techUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("tech.expense.pg@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());
    techStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(techUser)
                .fullName("Le Ky Thuat PG")
                .phone("0909555222")
                .build());
  }

  @AfterEach
  void tearDown() {
    privateObjectStorage.clear();
    jdbcTemplate.execute(
        "TRUNCATE TABLE ticket_replacement_parts, maintenance_tickets, facility_expenses, "
            + "payment_transactions, invoices, contract_documents, contract_renewals, contracts, "
            + "room_code_registry, beds, room_amenities, rooms, tenants, staff_profiles, users CASCADE");
  }

  @Test
  @DisplayName(
      "Concurrent SALARY creation in same month triggers real uq_facility_expenses_salary_month: 1 succeeds and 1 gets 409 EXPENSE_409_SALARY_MONTH")
  void concurrentSalaryCreation_enforcesPostgresUniqueIndexAndClassifier() throws Exception {
    UUID managerUserId = managerStaff.getUser().getId();
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      CyclicBarrier barrier = new CyclicBarrier(2);
      CreateFacilityExpenseRequest req1 =
          new CreateFacilityExpenseRequest(
              ExpenseType.SALARY,
              "Luong dot 1",
              new BigDecimal("40000000"),
              LocalDate.of(2026, 10, 5),
              "SAL-2026-10-A",
              null);
      CreateFacilityExpenseRequest req2 =
          new CreateFacilityExpenseRequest(
              ExpenseType.SALARY,
              "Luong dot 2",
              new BigDecimal("42000000"),
              LocalDate.of(2026, 10, 25),
              "SAL-2026-10-B",
              null);

      Future<AttemptOutcome> f1 =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return attemptCreate(managerUserId, req1);
              });
      Future<AttemptOutcome> f2 =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return attemptCreate(managerUserId, req2);
              });

      List<AttemptOutcome> outcomes =
          List.of(f1.get(15, TimeUnit.SECONDS), f2.get(15, TimeUnit.SECONDS));
      List<FacilityExpenseResponse> winners = new ArrayList<>();
      List<Throwable> failures = new ArrayList<>();
      for (AttemptOutcome o : outcomes) {
        if (o.response() != null) {
          winners.add(o.response());
        } else {
          failures.add(o.error());
        }
      }

      assertThat(winners).hasSize(1);
      assertThat(failures).hasSize(1);
      assertThat(failures.getFirst())
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);
      assertThat(facilityExpenseRepository.count()).isEqualTo(1L);

      // Direct JDBC insert violating uq_facility_expenses_salary_month classifies to 409
      // EXPENSE_409_SALARY_MONTH
      DataIntegrityViolationException rawViolation =
          catchThrowableOfType(
              DataIntegrityViolationException.class,
              () ->
                  jdbcTemplate.update(
                      "INSERT INTO facility_expenses(recorded_by_staff_id, expense_date, expense_type, title, amount) "
                          + "VALUES (?, '2026-10-30', 'SALARY', 'Raw duplicate', 1000000)",
                      managerStaff.getId()));
      assertThat(rawViolation).isNotNull();
      assertThat(DataIntegrityViolationClassifier.classifyGlobal(rawViolation))
          .contains(ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  @DisplayName(
      "Multiple non-SALARY expenses in same month and SALARY across different months succeed; update moving SALARY into occupied month fails with 409 while in-place update succeeds")
  void nonSalaryMultiplesAndUpdateSalaryConflictRules_workOnPostgres() {
    UUID managerUserId = managerStaff.getUser().getId();

    // Multiple ELECTRICITY expenses in same month succeed
    FacilityExpenseResponse elec1 =
        facilityExpenseService.createExpense(
            managerUserId,
            new CreateFacilityExpenseRequest(
                ExpenseType.ELECTRICITY,
                "Dien khu A",
                new BigDecimal("3500000"),
                LocalDate.of(2026, 10, 5),
                "EVN-A",
                null));
    FacilityExpenseResponse elec2 =
        facilityExpenseService.createExpense(
            managerUserId,
            new CreateFacilityExpenseRequest(
                ExpenseType.ELECTRICITY,
                "Dien khu B",
                new BigDecimal("4100000"),
                LocalDate.of(2026, 10, 12),
                "EVN-B",
                null));

    // SALARY in Oct and Nov succeed
    FacilityExpenseResponse salOct =
        facilityExpenseService.createExpense(
            managerUserId,
            new CreateFacilityExpenseRequest(
                ExpenseType.SALARY,
                "Luong T10",
                new BigDecimal("40000000"),
                LocalDate.of(2026, 10, 28),
                null,
                null));
    FacilityExpenseResponse salNov =
        facilityExpenseService.createExpense(
            managerUserId,
            new CreateFacilityExpenseRequest(
                ExpenseType.SALARY,
                "Luong T11",
                new BigDecimal("41000000"),
                LocalDate.of(2026, 11, 28),
                null,
                null));

    assertThat(List.of(elec1.id(), elec2.id(), salOct.id(), salNov.id())).hasSize(4);

    // Moving Nov SALARY into Oct fails with 409
    assertThatThrownBy(
            () ->
                facilityExpenseService.updateExpense(
                    salNov.id(),
                    new UpdateFacilityExpenseRequest(
                        ExpenseType.SALARY,
                        "Chuyen sang T10",
                        new BigDecimal("41000000"),
                        LocalDate.of(2026, 10, 15),
                        null,
                        null)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);

    // Changing ELECTRICITY to SALARY in Oct also fails with 409
    assertThatThrownBy(
            () ->
                facilityExpenseService.updateExpense(
                    elec1.id(),
                    new UpdateFacilityExpenseRequest(
                        ExpenseType.SALARY,
                        "Doi loai sang SALARY",
                        new BigDecimal("3500000"),
                        LocalDate.of(2026, 10, 20),
                        null,
                        null)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);

    // In-place update of Oct SALARY preserves recordedByStaff and succeeds
    FacilityExpenseResponse salOctUpdated =
        facilityExpenseService.updateExpense(
            salOct.id(),
            new UpdateFacilityExpenseRequest(
                ExpenseType.SALARY,
                "Luong T10 da dieu chinh",
                new BigDecimal("42500000"),
                LocalDate.of(2026, 10, 31),
                "SAL-OCT-ADJ",
                "Da duyet"));
    assertThat(salOctUpdated.title()).isEqualTo("Luong T10 da dieu chinh");
    assertThat(salOctUpdated.amount()).isEqualByComparingTo("42500000");
    assertThat(salOctUpdated.recordedByStaffId()).isEqualTo(managerStaff.getId());
  }

  @Test
  @DisplayName(
      "Receipt upload, replace, deleteReceipt, deleteExpense, and rollback cleanup manage private S3 objects atomically")
  void receiptLifecycleAndRollbackCleanup_manageS3AfterCommitAndRollback() throws Exception {
    UUID managerUserId = managerStaff.getUser().getId();
    FacilityExpenseResponse created =
        facilityExpenseService.createExpense(
            managerUserId,
            new CreateFacilityExpenseRequest(
                ExpenseType.WATER,
                "Tien nuoc T10",
                new BigDecimal("1800000"),
                LocalDate.of(2026, 10, 18),
                "SAWACO-10",
                null));
    UUID expenseId = created.id();

    // 1. Upload PDF receipt -> readable with synthetic filename and exact bytes
    MockMultipartFile pdfFile =
        new MockMultipartFile("file", "sawaco.pdf", "application/pdf", VALID_PDF_BYTES);
    FacilityExpenseResponse uploadedPdf = facilityExpenseService.uploadReceipt(expenseId, pdfFile);
    assertThat(uploadedPdf.receipt()).isNotNull();
    assertThat(uploadedPdf.receipt().available()).isTrue();
    assertThat(uploadedPdf.receipt().contentUrl())
        .isEqualTo("/api/v1/admin/expenses/" + expenseId + "/receipt/content");

    String pdfKey =
        notSupportedTx.execute(
            status ->
                facilityExpenseRepository.findById(expenseId).orElseThrow().getInvoiceFileKey());
    assertThat(pdfKey).startsWith("expenses/" + expenseId + "/receipt/").endsWith(".pdf");
    assertThat(privateObjectStorage.exists(pdfKey)).isTrue();

    FacilityExpenseReceiptContent pdfContent = facilityExpenseService.loadReceiptContent(expenseId);
    assertThat(pdfContent.contentType()).isEqualTo("application/pdf");
    assertThat(pdfContent.filename()).isEqualTo("expense-" + expenseId + "-receipt.pdf");
    assertThat(pdfContent.resource().getContentAsByteArray()).isEqualTo(VALID_PDF_BYTES);

    // 2. Rollback during replacement upload -> new PNG key removed, old PDF key & DB row preserved
    MockMultipartFile pngFile =
        new MockMultipartFile("file", "replace.png", "image/png", VALID_PNG_BYTES);
    AtomicReference<String> rolledBackPngKey = new AtomicReference<>();
    requiredTx.executeWithoutResult(
        status -> {
          facilityExpenseService.uploadReceipt(expenseId, pngFile);
          String keyInTx =
              facilityExpenseRepository.findById(expenseId).orElseThrow().getInvoiceFileKey();
          assertThat(keyInTx).isNotEqualTo(pdfKey).endsWith(".png");
          assertThat(privateObjectStorage.exists(keyInTx)).isTrue();
          assertThat(privateObjectStorage.exists(pdfKey)).isTrue();
          rolledBackPngKey.set(keyInTx);
          status.setRollbackOnly();
        });
    assertThat(privateObjectStorage.exists(rolledBackPngKey.get())).isFalse();
    assertThat(privateObjectStorage.exists(pdfKey)).isTrue();
    String persistedAfterRollback =
        notSupportedTx.execute(
            status ->
                facilityExpenseRepository.findById(expenseId).orElseThrow().getInvoiceFileKey());
    assertThat(persistedAfterRollback).isEqualTo(pdfKey);

    // 3. Commit replacement upload -> old PDF key deleted after commit, new PNG readable
    facilityExpenseService.uploadReceipt(expenseId, pngFile);
    String committedPngKey =
        notSupportedTx.execute(
            status ->
                facilityExpenseRepository.findById(expenseId).orElseThrow().getInvoiceFileKey());
    assertThat(committedPngKey).isNotEqualTo(pdfKey).endsWith(".png");
    assertThat(privateObjectStorage.exists(pdfKey)).isFalse();
    assertThat(privateObjectStorage.exists(committedPngKey)).isTrue();

    FacilityExpenseReceiptContent pngContent = facilityExpenseService.loadReceiptContent(expenseId);
    assertThat(pngContent.contentType()).isEqualTo("image/png");
    assertThat(pngContent.filename()).isEqualTo("expense-" + expenseId + "-receipt.png");
    assertThat(pngContent.resource().getContentAsByteArray()).isEqualTo(VALID_PNG_BYTES);

    // 4. Rollback deleteReceipt -> S3 key & DB preserved; Commit deleteReceipt -> S3 key deleted
    requiredTx.executeWithoutResult(
        status -> {
          facilityExpenseService.deleteReceipt(expenseId);
          assertThat(privateObjectStorage.exists(committedPngKey)).isTrue();
          status.setRollbackOnly();
        });
    assertThat(privateObjectStorage.exists(committedPngKey)).isTrue();

    FacilityExpenseResponse deletedReceiptResp = facilityExpenseService.deleteReceipt(expenseId);
    assertThat(deletedReceiptResp.receipt()).isNull();
    assertThat(privateObjectStorage.exists(committedPngKey)).isFalse();

    // 5. Re-upload receipt, test rollback deleteExpense preserves S3 & DB, then commit
    // deleteExpense
    facilityExpenseService.uploadReceipt(expenseId, pdfFile);
    String finalKey =
        notSupportedTx.execute(
            status ->
                facilityExpenseRepository.findById(expenseId).orElseThrow().getInvoiceFileKey());
    assertThat(privateObjectStorage.exists(finalKey)).isTrue();

    requiredTx.executeWithoutResult(
        status -> {
          facilityExpenseService.deleteExpense(expenseId);
          assertThat(privateObjectStorage.exists(finalKey)).isTrue();
          status.setRollbackOnly();
        });
    assertThat(privateObjectStorage.exists(finalKey)).isTrue();
    assertThat(facilityExpenseRepository.findById(expenseId)).isPresent();

    facilityExpenseService.deleteExpense(expenseId);
    assertThat(privateObjectStorage.exists(finalKey)).isFalse();
    assertThat(facilityExpenseRepository.findById(expenseId)).isEmpty();
  }

  @Test
  @DisplayName(
      "Period filtering, null-date CAST guard, search across title/invoiceCode/notes, sum projection, and Invoice/MaintenanceTicket report queries work on PostgreSQL")
  void filterSearchSumsAndReportRepositories_workOnPostgres() {
    UUID managerUserId = managerStaff.getUser().getId();

    facilityExpenseService.createExpense(
        managerUserId,
        new CreateFacilityExpenseRequest(
            ExpenseType.ELECTRICITY,
            "Dien EVN T10",
            new BigDecimal("3000000"),
            LocalDate.of(2026, 10, 5),
            "EVN-1001",
            null));
    facilityExpenseService.createExpense(
        managerUserId,
        new CreateFacilityExpenseRequest(
            ExpenseType.ELECTRICITY,
            "Dien may bom T10",
            new BigDecimal("1200000"),
            LocalDate.of(2026, 10, 20),
            null,
            "Ghi chu EVN bo sung"));
    facilityExpenseService.createExpense(
        managerUserId,
        new CreateFacilityExpenseRequest(
            ExpenseType.INTERNET,
            "Internet VNPT T10",
            new BigDecimal("800000"),
            LocalDate.of(2026, 10, 10),
            "VNPT-10",
            "Cap quang"));
    facilityExpenseService.createExpense(
        managerUserId,
        new CreateFacilityExpenseRequest(
            ExpenseType.WATER,
            "Nuoc Sawaco T11",
            new BigDecimal("950000"),
            LocalDate.of(2026, 11, 5),
            "SAW-11",
            null));

    // Period = null exercises CAST(:startDate AS LocalDate) IS NULL / CAST(:endDate AS LocalDate)
    PageResponse<FacilityExpenseResponse> allNullPeriod =
        facilityExpenseService.getExpenses(null, null, null, PageRequest.of(0, 10));
    assertThat(allNullPeriod.getTotalElements()).isEqualTo(4);
    assertThat(allNullPeriod.getItems())
        .extracting(FacilityExpenseResponse::expenseDate)
        .containsExactly(
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 10, 10),
            LocalDate.of(2026, 10, 5));

    // Period + search matching title, invoiceCode, and notes (with nullable fields)
    PageResponse<FacilityExpenseResponse> evnOct =
        facilityExpenseService.getExpenses("2026-10", null, "evn", PageRequest.of(0, 10));
    assertThat(evnOct.getTotalElements()).isEqualTo(2);

    PageResponse<FacilityExpenseResponse> filteredType =
        facilityExpenseService.getExpenses(
            "2026-10", ExpenseType.INTERNET, "cap quang", PageRequest.of(0, 10));
    assertThat(filteredType.getTotalElements()).isEqualTo(1);
    assertThat(filteredType.getItems().getFirst().title()).isEqualTo("Internet VNPT T10");

    // Repository ordered list and aggregate sum projection
    List<FacilityExpense> octList =
        facilityExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDescCreatedAtDesc(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    assertThat(octList).hasSize(3);

    Map<ExpenseType, BigDecimal> sums =
        facilityExpenseRepository
            .sumAmountByExpenseTypeBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
            .stream()
            .collect(
                Collectors.toMap(
                    FacilityExpenseRepository.ExpenseTypeSumProjection::getExpenseType,
                    FacilityExpenseRepository.ExpenseTypeSumProjection::getTotalAmount));
    assertThat(sums).containsEntry(ExpenseType.ELECTRICITY, new BigDecimal("4200000"));
    assertThat(sums).containsEntry(ExpenseType.INTERNET, new BigDecimal("800000"));
    assertThat(sums).doesNotContainKey(ExpenseType.WATER);

    // Seed tenant, contract, invoices (PAID & DEBT in Oct, PAID in Nov)
    User tenantUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("tenant.report.pg@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant tenant =
        tenantRepository.saveAndFlush(
            Tenant.builder()
                .user(tenantUser)
                .tenantCode("NT-RPT-01")
                .fullName("Nguyen Report")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203950001")
                .phone("0901950001")
                .permanentAddress("Thu Duc")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());
    Contract contract =
        contractRepository.saveAndFlush(
            Contract.builder()
                .contractCode("HD-2026-9501")
                .tenant(tenant)
                .bed(beds.get(0))
                .roomCodeSnapshot("P.950")
                .bedCodeSnapshot(beds.get(0).getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    Invoice paidOct =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202610-HD-9501")
                .contract(contract)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 10, 5))
                .status(InvoiceStatus.PAID)
                .build());
    paymentTransactionRepository.saveAndFlush(
        PaymentTransaction.builder()
            .invoice(paidOct)
            .receiptCode("PT-INV-202610-HD-9501")
            .amount(new BigDecimal("1500000"))
            .paymentMethod(PaymentMethod.CASH)
            .confirmedByStaff(managerStaff)
            .build());

    invoiceRepository.saveAndFlush(
        Invoice.builder()
            .invoiceCode("INV-202611-HD-9501")
            .contract(contract)
            .billingPeriod(LocalDate.of(2026, 11, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 11, 5))
            .status(InvoiceStatus.DEBT)
            .build());

    List<Invoice> paidOctList =
        invoiceRepository.findPaidInvoicesForPeriodWithDetails(LocalDate.of(2026, 10, 1));
    assertThat(paidOctList).hasSize(1);
    Invoice fetchedInv = paidOctList.getFirst();
    assertThat(fetchedInv.getInvoiceCode()).isEqualTo("INV-202610-HD-9501");
    assertThat(fetchedInv.getContract().getTenant().getFullName()).isEqualTo("Nguyen Report");
    assertThat(fetchedInv.getPaymentTransaction().getReceiptCode())
        .isEqualTo("PT-INV-202610-HD-9501");

    // Seed MaintenanceTickets across half-open [2026-09-30T17:00:00Z, 2026-10-31T17:00:00Z)
    Instant startInclusive = Instant.parse("2026-09-30T17:00:00Z"); // 2026-10-01T00:00+07
    Instant endExclusive = Instant.parse("2026-10-31T17:00:00Z"); // 2026-11-01T00:00+07

    // Included: COMPLETED at exact startInclusive with 0 replacement parts
    MaintenanceTicket zeroPartTicket =
        maintenanceTicketRepository.saveAndFlush(
            MaintenanceTicket.builder()
                .ticketCode("MT-RPT-001")
                .room(room)
                .reporterTenant(tenant)
                .assigneeStaff(techStaff)
                .title("Sua den")
                .description("Den nhap nhay")
                .category(TicketCategory.ELECTRIC)
                .priority(TicketPriority.NORMAL)
                .status(TicketStatus.COMPLETED)
                .resolutionNotes("Siet lai duoi den")
                .resolutionImageFileKey("tickets/MT-RPT-001/res.jpg")
                .resolvedAt(startInclusive)
                .build());

    // Included: COMPLETED mid-month with 2 replacement parts (DISTINCT check)
    MaintenanceTicket twoPartTicket =
        maintenanceTicketRepository.saveAndFlush(
            MaintenanceTicket.builder()
                .ticketCode("MT-RPT-002")
                .room(room)
                .reporterTenant(tenant)
                .assigneeStaff(techStaff)
                .title("Thay voi nuoc")
                .description("Voi bi ro ri")
                .category(TicketCategory.WATER)
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.COMPLETED)
                .resolutionNotes("Thay voi va gioang")
                .resolutionImageFileKey("tickets/MT-RPT-002/res.jpg")
                .resolvedAt(Instant.parse("2026-10-15T10:00:00Z"))
                .build());
    ticketReplacementPartRepository.saveAllAndFlush(
        List.of(
            TicketReplacementPart.builder()
                .ticket(twoPartTicket)
                .partName("Voi nuoc inox")
                .quantity(1)
                .unitCost(new BigDecimal("120000"))
                .build(),
            TicketReplacementPart.builder()
                .ticket(twoPartTicket)
                .partName("Gioang cao su")
                .quantity(2)
                .unitCost(new BigDecimal("15000"))
                .build()));

    // Excluded: COMPLETED at exact endExclusive (belongs to next month)
    maintenanceTicketRepository.saveAndFlush(
        MaintenanceTicket.builder()
            .ticketCode("MT-RPT-003")
            .room(room)
            .reporterTenant(tenant)
            .assigneeStaff(techStaff)
            .title("Sua cua thang 11")
            .description("Ket khoa")
            .category(TicketCategory.OTHER)
            .priority(TicketPriority.NORMAL)
            .status(TicketStatus.COMPLETED)
            .resolutionNotes("Da tra dau")
            .resolutionImageFileKey("tickets/MT-RPT-003/res.jpg")
            .resolvedAt(endExclusive)
            .build());

    // Excluded: IN_PROGRESS
    maintenanceTicketRepository.saveAndFlush(
        MaintenanceTicket.builder()
            .ticketCode("MT-RPT-004")
            .room(room)
            .reporterTenant(tenant)
            .assigneeStaff(techStaff)
            .title("Dang sua")
            .description("Chua xong")
            .category(TicketCategory.INTERNET)
            .priority(TicketPriority.NORMAL)
            .status(TicketStatus.IN_PROGRESS)
            .build());

    List<MaintenanceTicket> completedOct =
        maintenanceTicketRepository.findCompletedTicketsBetweenWithParts(
            startInclusive, endExclusive);
    assertThat(completedOct).hasSize(2);
    assertThat(completedOct)
        .extracting(MaintenanceTicket::getTicketCode)
        .containsExactly("MT-RPT-002", "MT-RPT-001");
    assertThat(completedOct.get(0).getRoom().getRoomNumber()).isEqualTo("P.950");
    assertThat(completedOct.get(0).getReplacementParts()).hasSize(2);
    assertThat(completedOct.get(1).getId()).isEqualTo(zeroPartTicket.getId());
    assertThat(completedOct.get(1).getReplacementParts()).isEmpty();
  }

  private record AttemptOutcome(FacilityExpenseResponse response, Throwable error) {}

  private AttemptOutcome attemptCreate(UUID managerUserId, CreateFacilityExpenseRequest request) {
    try {
      return new AttemptOutcome(facilityExpenseService.createExpense(managerUserId, request), null);
    } catch (Throwable t) {
      return new AttemptOutcome(null, t);
    }
  }
}
