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
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
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
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.dao.DataIntegrityViolationException;
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
@Import(InvoiceProofPostgresTest.TestClockConfig.class)
class InvoiceProofPostgresTest {

  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};
  private static final byte[] VALID_JPEG_BYTES =
      new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
  private static final byte[] VALID_WEBP_BYTES =
      new byte[] {
        'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
      };

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
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentTransactionRepository paymentTransactionRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  private TransactionTemplate requiredTx;
  private TransactionTemplate notSupportedTx;
  private List<Bed> beds;

  @BeforeEach
  void setUp() {
    testClock.reset();
    privateObjectStorage.clear();

    requiredTx = new TransactionTemplate(transactionManager);
    requiredTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

    notSupportedTx = new TransactionTemplate(transactionManager);
    notSupportedTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

    var room =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.930",
                9,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    beds = bedRepository.findByRoomIdOrderByBedCodeAsc(room.id());
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
    privateObjectStorage.clear();
    jdbcTemplate.execute(
        "TRUNCATE TABLE payment_transactions, invoices, contract_documents, contract_renewals, "
            + "contracts, room_code_registry, beds, room_amenities, rooms, tenants, users CASCADE");
  }

  private Tenant createTenant(String suffix, String idCard, ResidentStatus residentStatus) {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .email("proof." + suffix + "@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    return tenantRepository.saveAndFlush(
        Tenant.builder()
            .user(user)
            .tenantCode("NT-PROOF-" + suffix)
            .fullName("Nguyen Proof " + suffix)
            .gender(GenderPolicy.MALE)
            .idCardNumber(idCard)
            .phone("090299" + idCard.substring(6))
            .permanentAddress("Thu Duc, TP.HCM")
            .residentStatus(residentStatus)
            .build());
  }

  private Invoice createDebtInvoice(
      String invoiceCode,
      String contractCode,
      Tenant tenant,
      Bed bed,
      ContractStatus contractStatus,
      LocalDate billingPeriod,
      LocalDate dueOn) {
    Contract contract =
        contractRepository.saveAndFlush(
            Contract.builder()
                .contractCode(contractCode)
                .tenant(tenant)
                .bed(bed)
                .roomCodeSnapshot("P.930")
                .bedCodeSnapshot(bed.getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(contractStatus)
                .checkedOutAt(
                    contractStatus == ContractStatus.LIQUIDATED
                        ? Instant.parse("2026-10-08T08:00:00Z")
                        : null)
                .build());
    return invoiceRepository.saveAndFlush(
        Invoice.builder()
            .invoiceCode(invoiceCode)
            .contract(contract)
            .billingPeriod(billingPeriod)
            .amount(new BigDecimal("1500000"))
            .dueOn(dueOn)
            .status(InvoiceStatus.DEBT)
            .build());
  }

  @Test
  @DisplayName(
      "Active tenant uploads PNG proof -> satisfies ck_invoices_proof_consistency, zero PaymentTransaction, and Admin/Tenant stream exact bytes with synthetic filename")
  void activeTenantUploadAndStreaming_satisfiesPostgresCheckAndStreamsExactBytes()
      throws Exception {
    Tenant activeTenant = createTenant("active", "079203930001", ResidentStatus.ACTIVE);
    Invoice invoice =
        createDebtInvoice(
            "INV-202610-HD-9301",
            "HD-2026-9301",
            activeTenant,
            beds.get(0),
            ContractStatus.ACTIVE,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 5));

    MockMultipartFile pngFile =
        new MockMultipartFile("file", "chuyen-khoan.png", "image/png", VALID_PNG_BYTES);

    InvoiceDetailResponse uploaded =
        invoiceService.submitProof(activeTenant.getUser().getId(), invoice.getId(), pngFile);

    assertThat(uploaded.status()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);
    assertThat(uploaded.proofAvailable()).isTrue();
    assertThat(uploaded.proofSubmittedAt()).isEqualTo(testClock.instant());
    assertThat(uploaded.vietQr()).isNull();
    assertThat(uploaded.overdue()).isFalse();
    assertThat(uploaded.overdueDays()).isZero();
    assertThat(uploaded.payment()).isNull();
    assertThat(uploaded.proof()).isNotNull();
    assertThat(uploaded.proof().contentUrl())
        .isEqualTo("/api/v1/tenant/invoices/" + invoice.getId() + "/proof/content");

    Invoice persisted =
        notSupportedTx.execute(status -> invoiceRepository.findById(invoice.getId()).orElseThrow());
    assertThat(persisted.getStatus()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);
    assertThat(persisted.getProofFileKey())
        .startsWith("invoices/" + invoice.getId() + "/proof/")
        .endsWith(".png");
    assertThat(persisted.getProofSubmittedAt()).isEqualTo(testClock.instant());
    assertThat(paymentTransactionRepository.count()).isZero();
    assertThat(privateObjectStorage.exists(persisted.getProofFileKey())).isTrue();

    // Admin and Tenant stream exact bytes with safe synthetic filename
    InvoiceProofContent adminStream = invoiceService.loadProofContentForAdmin(invoice.getId());
    assertThat(adminStream.contentType()).isEqualTo("image/png");
    assertThat(adminStream.contentLength()).isEqualTo(VALID_PNG_BYTES.length);
    assertThat(adminStream.downloadFilename()).isEqualTo("INV-202610-HD-9301-proof.png");
    assertThat(adminStream.resource().getContentAsByteArray()).isEqualTo(VALID_PNG_BYTES);

    InvoiceProofContent tenantStream =
        invoiceService.loadProofContentForTenant(activeTenant.getUser().getId(), invoice.getId());
    assertThat(tenantStream.contentType()).isEqualTo("image/png");
    assertThat(tenantStream.contentLength()).isEqualTo(VALID_PNG_BYTES.length);
    assertThat(tenantStream.downloadFilename()).isEqualTo("INV-202610-HD-9301-proof.png");
    assertThat(tenantStream.resource().getContentAsByteArray()).isEqualTo(VALID_PNG_BYTES);

    // Verify PostgreSQL ck_invoices_proof_consistency rejects PENDING_CONFIRMATION with null key
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE invoices SET status = 'PENDING_CONFIRMATION', proof_file_key = NULL WHERE id = ?",
                    invoice.getId()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_invoices_proof_consistency");
  }

  @Test
  @DisplayName(
      "Checked-out tenant uploads JPG proof on unpaid DEBT invoice and cross-tenant access returns 404 INVOICE_404")
  void checkedOutTenantUploadAndCrossTenantIsolation_succeedsForOwnerAndHidesFromOthers()
      throws Exception {
    Tenant checkedOutTenant = createTenant("checkout", "079203930002", ResidentStatus.CHECKED_OUT);
    Tenant otherTenant = createTenant("other", "079203930003", ResidentStatus.ACTIVE);

    Invoice checkedOutInvoice =
        createDebtInvoice(
            "INV-202610-HD-9302",
            "HD-2026-9302",
            checkedOutTenant,
            beds.get(1),
            ContractStatus.LIQUIDATED,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 5));

    MockMultipartFile jpgFile =
        new MockMultipartFile("file", "bill.jpg", "image/jpeg", VALID_JPEG_BYTES);

    // Other tenant cannot upload to checked-out tenant's invoice -> 404 INVOICE_404
    assertThatThrownBy(
            () ->
                invoiceService.submitProof(
                    otherTenant.getUser().getId(), checkedOutInvoice.getId(), jpgFile))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);
    assertThat(privateObjectStorage.size()).isZero();

    // Checked-out owner uploads JPG successfully
    InvoiceDetailResponse uploaded =
        invoiceService.submitProof(
            checkedOutTenant.getUser().getId(), checkedOutInvoice.getId(), jpgFile);
    assertThat(uploaded.status()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);

    // Checked-out owner streams proof with synthetic filename
    InvoiceProofContent ownerStream =
        invoiceService.loadProofContentForTenant(
            checkedOutTenant.getUser().getId(), checkedOutInvoice.getId());
    assertThat(ownerStream.contentType()).isEqualTo("image/jpeg");
    assertThat(ownerStream.downloadFilename()).isEqualTo("INV-202610-HD-9302-proof.jpg");
    assertThat(ownerStream.resource().getContentAsByteArray()).isEqualTo(VALID_JPEG_BYTES);

    // Other tenant cannot stream proof -> 404 INVOICE_404
    assertThatThrownBy(
            () ->
                invoiceService.loadProofContentForTenant(
                    otherTenant.getUser().getId(), checkedOutInvoice.getId()))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "Transaction lifecycle: submitProof rollback cleans S3, rejectProof rollback preserves S3, rejectProof commit deletes S3 after commit, and tenant resubmits cleanly")
  void transactionRollbackAndCommit_managesPrivateS3ObjectsAndAllowsResubmission()
      throws Exception {
    Tenant tenant = createTenant("tx", "079203930004", ResidentStatus.ACTIVE);
    Invoice invoice =
        createDebtInvoice(
            "INV-202610-HD-9303",
            "HD-2026-9303",
            tenant,
            beds.get(2),
            ContractStatus.ACTIVE,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 5));

    UUID tenantUserId = tenant.getUser().getId();
    UUID invoiceId = invoice.getId();

    MockMultipartFile pngFile =
        new MockMultipartFile("file", "first.png", "image/png", VALID_PNG_BYTES);

    try {
      // 1. Rollback after submitProof -> newly uploaded S3 object must be deleted and DB remains
      // DEBT
      AtomicReference<String> rolledBackKeyRef = new AtomicReference<>();
      requiredTx.executeWithoutResult(
          status -> {
            invoiceService.submitProof(tenantUserId, invoiceId, pngFile);
            String keyInTx = invoiceRepository.findById(invoiceId).orElseThrow().getProofFileKey();
            assertThat(privateObjectStorage.exists(keyInTx)).isTrue();
            rolledBackKeyRef.set(keyInTx);
            status.setRollbackOnly();
          });
      assertThat(privateObjectStorage.exists(rolledBackKeyRef.get())).isFalse();
      Invoice afterSubmitRollback =
          notSupportedTx.execute(status -> invoiceRepository.findById(invoiceId).orElseThrow());
      assertThat(afterSubmitRollback.getStatus()).isEqualTo(InvoiceStatus.DEBT);
      assertThat(afterSubmitRollback.getProofFileKey()).isNull();
      assertThat(afterSubmitRollback.getProofSubmittedAt()).isNull();

      // 2. Commit submitProof cleanly
      invoiceService.submitProof(tenantUserId, invoiceId, pngFile);
      String committedKey =
          notSupportedTx.execute(
              status -> invoiceRepository.findById(invoiceId).orElseThrow().getProofFileKey());
      assertThat(committedKey).isNotNull();
      assertThat(privateObjectStorage.exists(committedKey)).isTrue();

      // 3. Rollback after rejectProof -> old S3 object must be PRESERVED and DB remains
      // PENDING_CONFIRMATION
      requiredTx.executeWithoutResult(
          status -> {
            invoiceService.rejectProof(invoiceId);
            assertThat(privateObjectStorage.exists(committedKey)).isTrue();
            status.setRollbackOnly();
          });
      assertThat(privateObjectStorage.exists(committedKey)).isTrue();
      Invoice afterRejectRollback =
          notSupportedTx.execute(status -> invoiceRepository.findById(invoiceId).orElseThrow());
      assertThat(afterRejectRollback.getStatus()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);
      assertThat(afterRejectRollback.getProofFileKey()).isEqualTo(committedKey);
      assertThat(afterRejectRollback.getProofSubmittedAt()).isNotNull();

      // 4. Commit rejectProof -> old S3 object deleted AFTER commit, DB returns to DEBT with null
      // proof columns, VietQR & overdue recomputed
      InvoiceDetailResponse rejected = invoiceService.rejectProof(invoiceId);
      assertThat(privateObjectStorage.exists(committedKey)).isFalse();
      assertThat(rejected.status()).isEqualTo(InvoiceStatus.DEBT);
      assertThat(rejected.proofAvailable()).isFalse();
      assertThat(rejected.proofSubmittedAt()).isNull();
      assertThat(rejected.proof()).isNull();
      assertThat(rejected.vietQr()).isNotNull();
      assertThat(rejected.vietQr().transferContent()).isEqualTo("KTX INV-202610-HD-9303");
      assertThat(rejected.overdue()).isTrue();
      assertThat(rejected.overdueDays()).isEqualTo(5L);

      Invoice afterRejectCommit =
          notSupportedTx.execute(status -> invoiceRepository.findById(invoiceId).orElseThrow());
      assertThat(afterRejectCommit.getStatus()).isEqualTo(InvoiceStatus.DEBT);
      assertThat(afterRejectCommit.getProofFileKey()).isNull();
      assertThat(afterRejectCommit.getProofSubmittedAt()).isNull();

      // 5. Tenant resubmits fresh WEBP proof cleanly
      MockMultipartFile webpFile =
          new MockMultipartFile("file", "resubmit.webp", "image/webp", VALID_WEBP_BYTES);
      InvoiceDetailResponse resubmitted =
          invoiceService.submitProof(tenantUserId, invoiceId, webpFile);
      assertThat(resubmitted.status()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);

      String newKey =
          notSupportedTx.execute(
              status -> invoiceRepository.findById(invoiceId).orElseThrow().getProofFileKey());
      assertThat(newKey).isNotNull().isNotEqualTo(committedKey).endsWith(".webp");
      assertThat(privateObjectStorage.exists(newKey)).isTrue();

      InvoiceProofContent resubmittedStream = invoiceService.loadProofContentForAdmin(invoiceId);
      assertThat(resubmittedStream.contentType()).isEqualTo("image/webp");
      assertThat(resubmittedStream.downloadFilename()).isEqualTo("INV-202610-HD-9303-proof.webp");
      assertThat(resubmittedStream.resource().getContentAsByteArray()).isEqualTo(VALID_WEBP_BYTES);
    } finally {
      privateObjectStorage.clear();
    }
  }
}
