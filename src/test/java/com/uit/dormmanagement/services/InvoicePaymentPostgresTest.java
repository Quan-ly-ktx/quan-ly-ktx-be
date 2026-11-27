package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofContent;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.InvoiceErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.PaymentTransactionRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(InvoicePaymentPostgresTest.TestClockConfig.class)
class InvoicePaymentPostgresTest {

  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};

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
    registry.add("app.vietqr.bank-bin", () -> "970405");
    registry.add("app.vietqr.account-number", () -> "1900123456789");
    registry.add("app.vietqr.account-name", () -> "KY TUC XA TEST HUB");
    registry.add("app.vietqr.transfer-prefix", () -> "KTX");
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
    private volatile Clock delegate =
        Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), TimeConfig.DORM_ZONE);

    void reset() {
      this.delegate = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), TimeConfig.DORM_ZONE);
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
  @Autowired private InvoiceService invoiceService;
  @Autowired private InMemoryPrivateObjectStorage privateObjectStorage;
  @Autowired private RoomService roomService;
  @Autowired private BedRepository bedRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentTransactionRepository paymentTransactionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private List<Bed> beds;
  private StaffProfile managerStaff;

  @BeforeEach
  void setUp() {
    testClock.reset();
    privateObjectStorage.clear();

    var room =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.940",
                9,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    beds = bedRepository.findByRoomIdOrderByBedCodeAsc(room.id());

    User managerUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("manager.payment@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    managerStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(managerUser)
                .fullName("Tran Quan Ly")
                .phone("0909000111")
                .build());
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
    privateObjectStorage.clear();
    jdbcTemplate.execute(
        "TRUNCATE TABLE payment_transactions, invoices, contract_documents, contract_renewals, "
            + "contracts, room_code_registry, beds, room_amenities, rooms, tenants, staff_profiles, users CASCADE");
  }

  private Tenant createTenant(String suffix, String idCard, ResidentStatus residentStatus) {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .email("pay." + suffix + "@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    return tenantRepository.saveAndFlush(
        Tenant.builder()
            .user(user)
            .tenantCode("NT-PAY-" + suffix)
            .fullName("Nguyen Pay " + suffix)
            .gender(GenderPolicy.MALE)
            .idCardNumber(idCard)
            .phone("090399" + idCard.substring(6))
            .permanentAddress("Thu Duc, TP.HCM")
            .residentStatus(residentStatus)
            .build());
  }

  private Invoice createDebtInvoice(
      String invoiceCode, String contractCode, Tenant tenant, Bed bed, LocalDate billingPeriod) {
    Contract contract =
        contractRepository.saveAndFlush(
            Contract.builder()
                .contractCode(contractCode)
                .tenant(tenant)
                .bed(bed)
                .roomCodeSnapshot("P.940")
                .bedCodeSnapshot(bed.getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());
    return invoiceRepository.saveAndFlush(
        Invoice.builder()
            .invoiceCode(invoiceCode)
            .contract(contract)
            .billingPeriod(billingPeriod)
            .amount(new BigDecimal("1500000"))
            .dueOn(billingPeriod.withDayOfMonth(5))
            .status(InvoiceStatus.DEBT)
            .build());
  }

  @Test
  @DisplayName(
      "Case 1: confirmCashPayment transitions DEBT -> PAID atomically, persists CASH PaymentTransaction, and blocks double confirmation")
  void confirmCashPayment_persistsAtomicTransactionAndBlocksDoubleConfirm() {
    Tenant tenant = createTenant("cash", "079203940001", ResidentStatus.ACTIVE);
    Invoice invoice =
        createDebtInvoice(
            "INV-202610-HD-9401", "HD-2026-9401", tenant, beds.get(0), LocalDate.of(2026, 10, 1));

    UUID managerUserId = managerStaff.getUser().getId();
    InvoiceDetailResponse confirmed =
        invoiceService.confirmCashPayment(managerUserId, invoice.getId());

    assertThat(confirmed.status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(confirmed.overdue()).isFalse();
    assertThat(confirmed.overdueDays()).isZero();
    assertThat(confirmed.vietQr()).isNull();
    assertThat(confirmed.proofAvailable()).isFalse();
    assertThat(confirmed.proof()).isNull();
    assertThat(confirmed.payment()).isNotNull();
    assertThat(confirmed.payment().receiptCode()).isEqualTo("PT-INV-202610-HD-9401");
    assertThat(confirmed.payment().amount()).isEqualByComparingTo("1500000");
    assertThat(confirmed.payment().paymentMethod()).isEqualTo(PaymentMethod.CASH);
    assertThat(confirmed.payment().confirmedByStaffId()).isEqualTo(managerStaff.getId());
    assertThat(confirmed.payment().confirmedByStaffName()).isEqualTo("Tran Quan Ly");
    assertThat(confirmed.payment().confirmedAt()).isNotNull();

    Invoice persistedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
    assertThat(persistedInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    assertThat(paymentTransactionRepository.count()).isEqualTo(1L);

    PaymentTransaction persistedTx =
        paymentTransactionRepository.findByInvoiceId(invoice.getId()).orElseThrow();
    assertThat(persistedTx.getReceiptCode()).isEqualTo("PT-INV-202610-HD-9401");
    assertThat(persistedTx.getAmount()).isEqualByComparingTo("1500000");
    assertThat(persistedTx.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
    assertThat(persistedTx.getConfirmedByStaff().getId()).isEqualTo(managerStaff.getId());
    assertThat(persistedTx.getConfirmedAt()).isNotNull();

    // Repeat cash or transfer confirm on PAID invoice -> 409 INVOICE_409_ALREADY_PAID
    assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, invoice.getId()))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoice.getId()))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    assertThat(paymentTransactionRepository.count()).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "Case 2: VietQR transfer confirmation enforces cross-method guards, preserves proof for audit, and blocks post-PAID actions")
  void confirmTransferPayment_enforcesCrossMethodGuardsPreservesProofAndBlocksPostPaidActions()
      throws Exception {
    Tenant tenantA = createTenant("xfera", "079203940002", ResidentStatus.ACTIVE);
    Tenant tenantB = createTenant("xferb", "079203940003", ResidentStatus.ACTIVE);
    Invoice pendingInvoice =
        createDebtInvoice(
            "INV-202610-HD-9402", "HD-2026-9402", tenantA, beds.get(1), LocalDate.of(2026, 10, 1));
    Invoice debtInvoice =
        createDebtInvoice(
            "INV-202610-HD-9403", "HD-2026-9403", tenantB, beds.get(2), LocalDate.of(2026, 10, 1));

    UUID managerUserId = managerStaff.getUser().getId();
    MockMultipartFile pngFile =
        new MockMultipartFile("file", "transfer.png", "image/png", VALID_PNG_BYTES);

    try {
      invoiceService.submitProof(tenantA.getUser().getId(), pendingInvoice.getId(), pngFile);

      // confirmCashPayment on PENDING_CONFIRMATION -> 409 INVOICE_409_NOT_DEBT
      assertThatThrownBy(
              () -> invoiceService.confirmCashPayment(managerUserId, pendingInvoice.getId()))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_DEBT);
      assertThat(invoiceRepository.findById(pendingInvoice.getId()).orElseThrow().getStatus())
          .isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);
      assertThat(paymentTransactionRepository.count()).isZero();

      // confirmTransferPayment on DEBT (no proof) -> 409 INVOICE_409_NOT_PENDING
      assertThatThrownBy(
              () -> invoiceService.confirmTransferPayment(managerUserId, debtInvoice.getId()))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", InvoiceErrorCode.INVOICE_NOT_PENDING_CONFIRMATION);
      assertThat(invoiceRepository.findById(debtInvoice.getId()).orElseThrow().getStatus())
          .isEqualTo(InvoiceStatus.DEBT);
      assertThat(paymentTransactionRepository.count()).isZero();

      // confirmTransferPayment on PENDING_CONFIRMATION -> PAID, VIETQR_TRANSFER, proof preserved
      InvoiceDetailResponse confirmed =
          invoiceService.confirmTransferPayment(managerUserId, pendingInvoice.getId());

      assertThat(confirmed.status()).isEqualTo(InvoiceStatus.PAID);
      assertThat(confirmed.overdue()).isFalse();
      assertThat(confirmed.overdueDays()).isZero();
      assertThat(confirmed.vietQr()).isNull();
      assertThat(confirmed.proofAvailable()).isTrue();
      assertThat(confirmed.proof()).isNotNull();
      assertThat(confirmed.proof().contentUrl())
          .isEqualTo("/api/v1/admin/invoices/" + pendingInvoice.getId() + "/proof/content");
      assertThat(confirmed.payment()).isNotNull();
      assertThat(confirmed.payment().receiptCode()).isEqualTo("PT-INV-202610-HD-9402");
      assertThat(confirmed.payment().amount()).isEqualByComparingTo("1500000");
      assertThat(confirmed.payment().paymentMethod()).isEqualTo(PaymentMethod.VIETQR_TRANSFER);
      assertThat(confirmed.payment().confirmedByStaffId()).isEqualTo(managerStaff.getId());
      assertThat(confirmed.payment().confirmedByStaffName()).isEqualTo("Tran Quan Ly");
      assertThat(confirmed.payment().confirmedAt()).isNotNull();

      Invoice persistedPaid = invoiceRepository.findById(pendingInvoice.getId()).orElseThrow();
      assertThat(persistedPaid.getStatus()).isEqualTo(InvoiceStatus.PAID);
      assertThat(persistedPaid.getProofFileKey()).isNotNull().endsWith(".png");
      assertThat(persistedPaid.getProofSubmittedAt()).isNotNull();
      assertThat(privateObjectStorage.exists(persistedPaid.getProofFileKey())).isTrue();

      InvoiceProofContent adminProof =
          invoiceService.loadProofContentForAdmin(pendingInvoice.getId());
      assertThat(adminProof.resource().getContentAsByteArray()).isEqualTo(VALID_PNG_BYTES);

      assertThat(paymentTransactionRepository.count()).isEqualTo(1L);
      PaymentTransaction persistedTx =
          paymentTransactionRepository.findByInvoiceId(pendingInvoice.getId()).orElseThrow();
      assertThat(persistedTx.getPaymentMethod()).isEqualTo(PaymentMethod.VIETQR_TRANSFER);
      assertThat(persistedTx.getReceiptCode()).isEqualTo("PT-INV-202610-HD-9402");

      // Post-PAID rejectProof & second confirmTransferPayment both fail with ALREADY_PAID
      assertThatThrownBy(() -> invoiceService.rejectProof(pendingInvoice.getId()))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

      assertThatThrownBy(
              () -> invoiceService.confirmTransferPayment(managerUserId, pendingInvoice.getId()))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

      assertThat(paymentTransactionRepository.count()).isEqualTo(1L);
      assertThat(privateObjectStorage.exists(persistedPaid.getProofFileKey())).isTrue();
    } finally {
      privateObjectStorage.clear();
    }
  }

  @Test
  @DisplayName(
      "Case 3: Concurrent double-confirmation is serialized by PostgreSQL FOR UPDATE so exactly 1 wins and 1 gets 409 ALREADY_PAID")
  void concurrentDoubleConfirmation_serializedByPessimisticLock_allowsExactlyOneWinner()
      throws Exception {
    Tenant tenantCash = createTenant("racecash", "079203940004", ResidentStatus.ACTIVE);
    Invoice cashInvoice =
        createDebtInvoice(
            "INV-202610-HD-9404",
            "HD-2026-9404",
            tenantCash,
            beds.get(0),
            LocalDate.of(2026, 10, 1));

    Tenant tenantXfer = createTenant("racexfer", "079203940005", ResidentStatus.ACTIVE);
    Invoice xferInvoice =
        createDebtInvoice(
            "INV-202610-HD-9405",
            "HD-2026-9405",
            tenantXfer,
            beds.get(1),
            LocalDate.of(2026, 10, 1));
    MockMultipartFile pngFile =
        new MockMultipartFile("file", "race.png", "image/png", VALID_PNG_BYTES);

    UUID managerUserId = managerStaff.getUser().getId();
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      // 3a. Concurrent cash confirmation race
      CyclicBarrier cashBarrier = new CyclicBarrier(2);
      Future<AttemptOutcome> cashF1 =
          executor.submit(
              () -> {
                cashBarrier.await(5, TimeUnit.SECONDS);
                return attemptConfirmCash(managerUserId, cashInvoice.getId());
              });
      Future<AttemptOutcome> cashF2 =
          executor.submit(
              () -> {
                cashBarrier.await(5, TimeUnit.SECONDS);
                return attemptConfirmCash(managerUserId, cashInvoice.getId());
              });

      assertSingleWinnerAndSingleAlreadyPaid(
          List.of(cashF1.get(15, TimeUnit.SECONDS), cashF2.get(15, TimeUnit.SECONDS)),
          PaymentMethod.CASH);
      assertThat(invoiceRepository.findById(cashInvoice.getId()).orElseThrow().getStatus())
          .isEqualTo(InvoiceStatus.PAID);
      assertThat(paymentTransactionRepository.findByInvoiceId(cashInvoice.getId())).isPresent();

      // 3b. Concurrent transfer confirmation race
      invoiceService.submitProof(tenantXfer.getUser().getId(), xferInvoice.getId(), pngFile);
      CyclicBarrier xferBarrier = new CyclicBarrier(2);
      Future<AttemptOutcome> xferF1 =
          executor.submit(
              () -> {
                xferBarrier.await(5, TimeUnit.SECONDS);
                return attemptConfirmTransfer(managerUserId, xferInvoice.getId());
              });
      Future<AttemptOutcome> xferF2 =
          executor.submit(
              () -> {
                xferBarrier.await(5, TimeUnit.SECONDS);
                return attemptConfirmTransfer(managerUserId, xferInvoice.getId());
              });

      assertSingleWinnerAndSingleAlreadyPaid(
          List.of(xferF1.get(15, TimeUnit.SECONDS), xferF2.get(15, TimeUnit.SECONDS)),
          PaymentMethod.VIETQR_TRANSFER);
      assertThat(invoiceRepository.findById(xferInvoice.getId()).orElseThrow().getStatus())
          .isEqualTo(InvoiceStatus.PAID);
      assertThat(paymentTransactionRepository.findByInvoiceId(xferInvoice.getId())).isPresent();
      assertThat(paymentTransactionRepository.count()).isEqualTo(2L);
    } finally {
      privateObjectStorage.clear();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  @DisplayName(
      "Case 4: Non-MANAGER staff (TECHNICIAN) and MANAGER without StaffProfile row fail with 403 INVOICE_403_STAFF_PROFILE")
  void confirmPayment_rejectsTechnicianAndManagerWithoutStaffProfile() {
    Tenant tenant = createTenant("guard", "079203940006", ResidentStatus.ACTIVE);
    Invoice invoice =
        createDebtInvoice(
            "INV-202610-HD-9406", "HD-2026-9406", tenant, beds.get(3), LocalDate.of(2026, 10, 1));

    User techUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("tech.payment@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TECHNICIAN)
                .status(UserStatus.ACTIVE)
                .build());
    staffProfileRepository.saveAndFlush(
        StaffProfile.builder().user(techUser).fullName("Le Ky Thuat").phone("0908000222").build());

    User managerWithoutProfile =
        userRepository.saveAndFlush(
            User.builder()
                .email("noprofile.manager@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    assertThatThrownBy(() -> invoiceService.confirmCashPayment(techUser.getId(), invoice.getId()))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    assertThatThrownBy(
            () -> invoiceService.confirmCashPayment(managerWithoutProfile.getId(), invoice.getId()))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    assertThatThrownBy(
            () ->
                invoiceService.confirmTransferPayment(
                    managerWithoutProfile.getId(), invoice.getId()))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus())
        .isEqualTo(InvoiceStatus.DEBT);
    assertThat(paymentTransactionRepository.count()).isZero();
  }

  private record AttemptOutcome(InvoiceDetailResponse response, Throwable error) {}

  private AttemptOutcome attemptConfirmCash(UUID managerUserId, UUID invoiceId) {
    try {
      return new AttemptOutcome(invoiceService.confirmCashPayment(managerUserId, invoiceId), null);
    } catch (Throwable t) {
      return new AttemptOutcome(null, t);
    }
  }

  private AttemptOutcome attemptConfirmTransfer(UUID managerUserId, UUID invoiceId) {
    try {
      return new AttemptOutcome(
          invoiceService.confirmTransferPayment(managerUserId, invoiceId), null);
    } catch (Throwable t) {
      return new AttemptOutcome(null, t);
    }
  }

  private static void assertSingleWinnerAndSingleAlreadyPaid(
      List<AttemptOutcome> outcomes, PaymentMethod expectedMethod) {
    List<InvoiceDetailResponse> winners = new ArrayList<>();
    List<Throwable> failures = new ArrayList<>();
    for (AttemptOutcome outcome : outcomes) {
      if (outcome.response() != null) {
        winners.add(outcome.response());
      } else {
        failures.add(outcome.error());
      }
    }
    assertThat(winners).hasSize(1);
    assertThat(winners.getFirst().status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(winners.getFirst().payment()).isNotNull();
    assertThat(winners.getFirst().payment().paymentMethod()).isEqualTo(expectedMethod);
    assertThat(winners.getFirst().payment().confirmedAt()).isNotNull();

    assertThat(failures).hasSize(1);
    assertThat(failures.getFirst())
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);
  }
}
