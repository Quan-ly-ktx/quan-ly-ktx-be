package com.uit.dormmanagement.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.contract.CheckoutContractRequest;
import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceBatchIssuanceResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofContent;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryStatsResponse;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.dtos.response.room.RoomDetailResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.InvoiceErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.PaymentTransactionRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.schedulers.MonthlyInvoiceIssuanceScheduler;
import com.uit.dormmanagement.services.ContractService;
import com.uit.dormmanagement.services.InvoiceBatchService;
import com.uit.dormmanagement.services.InvoiceService;
import com.uit.dormmanagement.services.OnboardingService;
import com.uit.dormmanagement.services.RoomService;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(BillingPaymentE2ETest.TestClockConfig.class)
@TestPropertySource(
    properties = {
      "app.vietqr.bank-bin=970405",
      "app.vietqr.account-number=000123456789",
      "app.vietqr.account-name=KTX HUB TEST",
      "app.vietqr.bank-name=Agribank",
      "app.vietqr.transfer-prefix=KTX"
    })
class BillingPaymentE2ETest {

  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02, 0x03, 0x04};
  private static final byte[] VALID_JPEG_BYTES =
      new byte[] {
        (byte) 0xFF,
        (byte) 0xD8,
        (byte) 0xFF,
        (byte) 0xE0,
        0x00,
        0x10,
        0x4A,
        0x46,
        0x49,
        0x46,
        0x00,
        0x01
      };
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
  @Autowired private OnboardingService onboardingService;
  @Autowired private ContractService contractService;
  @Autowired private InvoiceService invoiceService;
  @Autowired private InvoiceBatchService invoiceBatchService;
  @Autowired private MonthlyInvoiceIssuanceScheduler monthlyInvoiceIssuanceScheduler;
  @Autowired private RoomService roomService;
  @Autowired private InMemoryPrivateObjectStorage privateObjectStorage;
  @Autowired private UserRepository userRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentTransactionRepository paymentTransactionRepository;
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
        "TRUNCATE TABLE payment_transactions, invoices, contract_documents, contract_renewals, "
            + "contracts, tenants, beds, room_code_registry, room_amenities, rooms, staff_profiles, users CASCADE");
  }

  @Test
  @DisplayName(
      "E2E Phase 4 Full Billing Lifecycle: Day-01 Onboarding -> Dynamic Overdue & Cash -> Day-25 Scheduler & Idempotency -> VietQR, Proof Upload, Blocked Cash, Reject & S3 Cleanup -> Transfer Confirm & Locked-Rent Renewal -> Post-Checkout Debt, Unblock & Summary Reconciliation")
  void testCompleteBillingAndPaymentLifecycle() throws IOException {
    // -------------------------------------------------------------------------
    // Stage 1: Day-01 Onboarding (2026-08-01)
    // -------------------------------------------------------------------------
    User managerUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("manager.billing.e2e@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    StaffProfile managerStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(managerUser)
                .fullName("Tran Quan Ly E2E")
                .phone("0909111222")
                .build());
    UUID managerUserId = managerUser.getId();
    UUID managerStaffId = managerStaff.getId();

    RoomDetailResponse createdRoom =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.401",
                4,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    UUID roomId = createdRoom.id();
    Bed bed = bedRepository.findByRoomIdOrderByBedCodeAsc(roomId).getFirst();
    assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);

    testClock.setDormTime("2026-08-01T09:00:00+07:00");

    OnboardingResponse onboardRes =
        onboardingService.onboardTenant(
            OnboardingRequest.builder()
                .fullName("Nguyen Van Billing")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079206111111")
                .phone("0987112233")
                .email("billing.e2e.tenant@uit.edu.vn")
                .permanentAddress("123 Linh Trung, Thu Duc, TP.HCM")
                .bedId(bed.getId())
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .depositAmount(new BigDecimal("1500000"))
                .build());

    assertThat(onboardRes.firstInvoice()).isNotNull();
    assertThat(onboardRes.firstInvoice().billingPeriod()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(onboardRes.firstInvoice().amount()).isEqualByComparingTo("1500000");
    assertThat(onboardRes.firstInvoice().dueOn()).isEqualTo(LocalDate.of(2026, 8, 5));
    assertThat(onboardRes.firstInvoice().status()).isEqualTo(InvoiceStatus.DEBT);

    UUID tenantId = onboardRes.tenant().id();
    UUID tenantUserId = onboardRes.tenant().userId();
    UUID contractId = onboardRes.contract().id();
    UUID augInvoiceId = onboardRes.firstInvoice().id();
    String augInvoiceCode = onboardRes.firstInvoice().invoiceCode();

    // -------------------------------------------------------------------------
    // Stage 2: Dynamic Overdue Detection & Cash Payment Confirmation (2026-08-07)
    // -------------------------------------------------------------------------
    testClock.setDormTime("2026-08-07T10:00:00+07:00");

    Page<InvoiceSummaryResponse> overduePage =
        invoiceService.getAdminInvoices(PageRequest.of(0, 20), null, null, null, true);
    assertThat(overduePage.getContent()).hasSize(1);
    InvoiceSummaryResponse augOverdueRow = overduePage.getContent().getFirst();
    assertThat(augOverdueRow.id()).isEqualTo(augInvoiceId);
    assertThat(augOverdueRow.overdue()).isTrue();
    assertThat(augOverdueRow.overdueDays()).isEqualTo(2L);

    InvoiceSummaryStatsResponse augPrePaidSummary =
        invoiceService.getAdminInvoiceSummary(LocalDate.of(2026, 8, 1));
    assertThat(augPrePaidSummary.overdueAmount()).isEqualByComparingTo("1500000");
    assertThat(augPrePaidSummary.overdueCount()).isEqualTo(1L);
    assertThat(augPrePaidSummary.paidCount()).isZero();
    assertThat(augPrePaidSummary.unpaidInTermCount()).isZero();
    assertThat(augPrePaidSummary.pendingConfirmationCount()).isZero();

    InvoiceDetailResponse cashConfirmed =
        invoiceService.confirmCashPayment(managerUserId, augInvoiceId);
    assertThat(cashConfirmed.status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(cashConfirmed.overdue()).isFalse();
    assertThat(cashConfirmed.overdueDays()).isZero();
    assertThat(cashConfirmed.vietQr()).isNull();
    assertThat(cashConfirmed.payment()).isNotNull();
    assertThat(cashConfirmed.payment().paymentMethod()).isEqualTo(PaymentMethod.CASH);
    assertThat(cashConfirmed.payment().receiptCode()).isEqualTo("PT-" + augInvoiceCode);
    assertThat(cashConfirmed.payment().amount()).isEqualByComparingTo("1500000");
    assertThat(cashConfirmed.payment().confirmedByStaffId()).isEqualTo(managerStaffId);
    assertThat(cashConfirmed.payment().confirmedAt()).isNotNull();

    PaymentTransaction augTx =
        paymentTransactionRepository.findByInvoiceId(augInvoiceId).orElseThrow();
    assertThat(augTx.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
    assertThat(augTx.getReceiptCode()).isEqualTo("PT-" + augInvoiceCode);
    assertThat(augTx.getAmount()).isEqualByComparingTo("1500000");
    assertThat(augTx.getConfirmedByStaff().getId()).isEqualTo(managerStaffId);
    assertThat(augTx.getConfirmedAt()).isNotNull();

    InvoiceSummaryStatsResponse augPostPaidSummary =
        invoiceService.getAdminInvoiceSummary(LocalDate.of(2026, 8, 1));
    assertThat(augPostPaidSummary.paidAmount()).isEqualByComparingTo("1500000");
    assertThat(augPostPaidSummary.paidCount()).isEqualTo(1L);
    assertThat(augPostPaidSummary.overdueCount()).isZero();
    assertThat(augPostPaidSummary.overdueAmount()).isEqualByComparingTo("0");

    // -------------------------------------------------------------------------
    // Stage 3: Day-25 Monthly Batch Issuance via Scheduler & Idempotent Retry (2026-08-25)
    // -------------------------------------------------------------------------
    testClock.setDormTime("2026-08-25T00:05:00+07:00");
    monthlyInvoiceIssuanceScheduler.issueNextMonthInvoices();

    Invoice sepInvoice =
        invoiceRepository
            .findByContractIdAndBillingPeriod(contractId, LocalDate.of(2026, 9, 1))
            .orElseThrow();
    assertThat(sepInvoice.getAmount()).isEqualByComparingTo("1500000");
    assertThat(sepInvoice.getDueOn()).isEqualTo(LocalDate.of(2026, 9, 5));
    assertThat(sepInvoice.getStatus()).isEqualTo(InvoiceStatus.DEBT);

    UUID sepInvoiceId = sepInvoice.getId();
    String sepInvoiceCode = sepInvoice.getInvoiceCode();

    InvoiceBatchIssuanceResponse sepRetryBatch =
        invoiceBatchService.issueMonthlyBatch(LocalDate.of(2026, 9, 1));
    assertThat(sepRetryBatch.issuedCount()).isZero();
    assertThat(sepRetryBatch.skippedExistingCount()).isGreaterThanOrEqualTo(1);
    assertThat(invoiceRepository.findByContractId(contractId)).hasSize(2);

    // -------------------------------------------------------------------------
    // Stage 4: VietQR Generation, Proof Upload, Blocked Cash, Reject & Post-Commit S3 Cleanup
    // (2026-09-02 -> 2026-09-07)
    // -------------------------------------------------------------------------
    testClock.setDormTime("2026-09-02T14:00:00+07:00");

    InvoiceDetailResponse sepTenantView =
        invoiceService.getTenantInvoiceById(tenantUserId, sepInvoiceId);
    assertThat(sepTenantView.vietQr()).isNotNull();
    assertThat(sepTenantView.vietQr().bankBin()).isEqualTo("970405");
    assertThat(sepTenantView.vietQr().bankName()).isEqualTo("Agribank");
    assertThat(sepTenantView.vietQr().accountNumber()).isEqualTo("000123456789");
    assertThat(sepTenantView.vietQr().accountName()).isEqualTo("KTX HUB TEST");
    assertThat(sepTenantView.vietQr().amount()).isEqualByComparingTo("1500000");
    assertThat(sepTenantView.vietQr().transferContent()).isEqualTo("KTX " + sepInvoiceCode);
    assertValidEmvCoCrc16(sepTenantView.vietQr().qrPayload());

    MockMultipartFile pngProof =
        new MockMultipartFile("file", "sep-transfer.png", "image/png", VALID_PNG_BYTES);
    InvoiceDetailResponse sepSubmitted =
        invoiceService.submitProof(tenantUserId, sepInvoiceId, pngProof);
    assertThat(sepSubmitted.status()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);
    assertThat(sepSubmitted.vietQr()).isNull();
    assertThat(sepSubmitted.proof()).isNotNull();
    assertThat(sepSubmitted.proof().contentUrl())
        .isEqualTo("/api/v1/tenant/invoices/" + sepInvoiceId + "/proof/content");
    assertThat(paymentTransactionRepository.findByInvoiceId(sepInvoiceId)).isEmpty();

    String oldProofKey = invoiceRepository.findById(sepInvoiceId).orElseThrow().getProofFileKey();
    assertThat(oldProofKey).isNotBlank();
    assertThat(privateObjectStorage.exists(oldProofKey)).isTrue();

    testClock.setDormTime("2026-09-07T09:00:00+07:00");

    InvoiceDetailResponse sepAdminPendingDetail = invoiceService.getAdminInvoiceById(sepInvoiceId);
    assertThat(sepAdminPendingDetail.overdue()).isFalse();
    assertThat(sepAdminPendingDetail.overdueDays()).isZero();

    Page<InvoiceSummaryResponse> sepListPage =
        invoiceService.getAdminInvoices(
            PageRequest.of(0, 20), null, null, LocalDate.of(2026, 9, 1), false);
    assertThat(sepListPage.getContent()).hasSize(1);
    assertThat(sepListPage.getContent().getFirst().overdue()).isFalse();
    assertThat(sepListPage.getContent().getFirst().overdueDays()).isZero();

    Page<InvoiceSummaryResponse> overdueWhilePending =
        invoiceService.getAdminInvoices(PageRequest.of(0, 20), null, null, null, true);
    assertThat(overdueWhilePending.getContent()).isEmpty();

    InvoiceSummaryStatsResponse sepPendingSummary =
        invoiceService.getAdminInvoiceSummary(LocalDate.of(2026, 9, 1));
    assertThat(sepPendingSummary.pendingConfirmationAmount()).isEqualByComparingTo("1500000");
    assertThat(sepPendingSummary.pendingConfirmationCount()).isEqualTo(1L);
    assertThat(sepPendingSummary.overdueAmount()).isEqualByComparingTo("0");
    assertThat(sepPendingSummary.overdueCount()).isZero();

    assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, sepInvoiceId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> {
              BaseAppException bae = (BaseAppException) ex;
              return bae.getErrorCode() == InvoiceErrorCode.INVOICE_NOT_DEBT
                  && bae.getErrorCode().getHttpStatus() == HttpStatus.CONFLICT;
            });

    InvoiceDetailResponse sepRejected = invoiceService.rejectProof(sepInvoiceId);
    assertThat(sepRejected.status()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(sepRejected.proof()).isNull();
    assertThat(sepRejected.proofAvailable()).isFalse();
    assertThat(sepRejected.proofSubmittedAt()).isNull();
    assertThat(sepRejected.vietQr()).isNotNull();
    assertValidEmvCoCrc16(sepRejected.vietQr().qrPayload());
    assertThatThrownBy(() -> privateObjectStorage.getObject(oldProofKey))
        .isInstanceOf(ObjectStorageNotFoundException.class);

    // -------------------------------------------------------------------------
    // Stage 5: Proof Resubmission, Admin Streaming, VietQR Transfer Confirmation & Multi-Renewal
    // Locked Rent (2026-09-10 -> 2026-09-25)
    // -------------------------------------------------------------------------
    testClock.setDormTime("2026-09-10T10:00:00+07:00");

    MockMultipartFile jpegProof =
        new MockMultipartFile("file", "sep-resubmit.jpg", "image/jpeg", VALID_JPEG_BYTES);
    InvoiceDetailResponse sepResubmitted =
        invoiceService.submitProof(tenantUserId, sepInvoiceId, jpegProof);
    assertThat(sepResubmitted.status()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);

    InvoiceProofContent adminStreamedSepProof =
        invoiceService.loadProofContentForAdmin(sepInvoiceId);
    assertThat(adminStreamedSepProof.contentType()).isEqualTo("image/jpeg");
    assertThat(adminStreamedSepProof.downloadFilename()).isEqualTo(sepInvoiceCode + "-proof.jpg");
    assertThat(adminStreamedSepProof.resource().getContentAsByteArray())
        .isEqualTo(VALID_JPEG_BYTES);

    InvoiceDetailResponse sepTransferConfirmed =
        invoiceService.confirmTransferPayment(managerUserId, sepInvoiceId);
    assertThat(sepTransferConfirmed.status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(sepTransferConfirmed.vietQr()).isNull();
    assertThat(sepTransferConfirmed.proofAvailable()).isTrue();
    assertThat(sepTransferConfirmed.payment()).isNotNull();
    assertThat(sepTransferConfirmed.payment().paymentMethod())
        .isEqualTo(PaymentMethod.VIETQR_TRANSFER);
    assertThat(sepTransferConfirmed.payment().receiptCode()).isEqualTo("PT-" + sepInvoiceCode);
    assertThat(sepTransferConfirmed.payment().amount()).isEqualByComparingTo("1500000");
    assertThat(sepTransferConfirmed.payment().confirmedByStaffId()).isEqualTo(managerStaffId);
    assertThat(sepTransferConfirmed.payment().confirmedAt()).isNotNull();

    InvoiceProofContent preservedSepProof = invoiceService.loadProofContentForAdmin(sepInvoiceId);
    assertThat(preservedSepProof.resource().getContentAsByteArray()).isEqualTo(VALID_JPEG_BYTES);

    Room roomForFirstRenewal = roomRepository.findById(roomId).orElseThrow();
    roomForFirstRenewal.setBasePrice(new BigDecimal("1800000"));
    roomRepository.saveAndFlush(roomForFirstRenewal);

    ContractDetailResponse renewedContract =
        contractService.renewContract(
            contractId,
            RenewContractRequest.builder().newEndDate(LocalDate.of(2026, 11, 30)).build(),
            List.of());
    assertThat(renewedContract.renewals()).hasSize(1);
    assertThat(renewedContract.renewals().getFirst().sequenceNo()).isEqualTo(1);
    assertThat(renewedContract.renewals().getFirst().effectiveFrom())
        .isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(renewedContract.renewals().getFirst().newEndOn())
        .isEqualTo(LocalDate.of(2026, 11, 30));
    assertThat(renewedContract.renewals().getFirst().monthlyRent()).isEqualByComparingTo("1800000");

    // Raise room.basePrice after renewal signing; October batch MUST use renewal's locked 1,800,000
    Room roomAfterRenewal = roomRepository.findById(roomId).orElseThrow();
    roomAfterRenewal.setBasePrice(new BigDecimal("2000000"));
    roomRepository.saveAndFlush(roomAfterRenewal);

    testClock.setDormTime("2026-09-25T08:00:00+07:00");
    InvoiceBatchIssuanceResponse octBatch =
        invoiceBatchService.issueMonthlyBatch(LocalDate.of(2026, 10, 1));
    assertThat(octBatch.issuedCount()).isEqualTo(1);

    Invoice octInvoice =
        invoiceRepository
            .findByContractIdAndBillingPeriod(contractId, LocalDate.of(2026, 10, 1))
            .orElseThrow();
    assertThat(octInvoice.getAmount()).isEqualByComparingTo("1800000");
    assertThat(octInvoice.getDueOn()).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(octInvoice.getStatus()).isEqualTo(InvoiceStatus.DEBT);
    UUID octInvoiceId = octInvoice.getId();
    String octInvoiceCode = octInvoice.getInvoiceCode();

    // -------------------------------------------------------------------------
    // Stage 6: Post-Checkout Debt Handling & Returning Tenant Re-Onboarding Unblock
    // (2026-10-02 -> 2026-11-01)
    // -------------------------------------------------------------------------
    testClock.setDormTime("2026-10-02T10:00:00+07:00");
    ContractDetailResponse terminatedContract =
        contractService.terminateContract(
            managerUserId,
            contractId,
            CheckoutContractRequest.builder()
                .bedStatusAfterCheckout(BedStatus.AVAILABLE)
                .handoverConfirmed(true)
                .build());
    assertThat(terminatedContract.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(terminatedContract.checkedOutAt()).isNotNull();

    Contract dbLiquidated = contractRepository.findById(contractId).orElseThrow();
    assertThat(dbLiquidated.getStatus()).isEqualTo(ContractStatus.LIQUIDATED);
    Tenant dbCheckedOutTenant = tenantRepository.findById(tenantId).orElseThrow();
    assertThat(dbCheckedOutTenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);
    assertThat(bedRepository.findById(bed.getId()).orElseThrow().getStatus())
        .isEqualTo(BedStatus.AVAILABLE);
    assertThat(invoiceRepository.findById(octInvoiceId).orElseThrow().getStatus())
        .isEqualTo(InvoiceStatus.DEBT);

    // Liquidated contract must NOT generate November invoice on 2026-10-25
    testClock.setDormTime("2026-10-25T08:00:00+07:00");
    InvoiceBatchIssuanceResponse novBatchAfterCheckout =
        invoiceBatchService.issueMonthlyBatch(LocalDate.of(2026, 11, 1));
    assertThat(novBatchAfterCheckout.eligibleContractCount()).isZero();
    assertThat(novBatchAfterCheckout.issuedCount()).isZero();
    assertThat(
            invoiceRepository.findByContractIdAndBillingPeriod(
                contractId, LocalDate.of(2026, 11, 1)))
        .isEmpty();

    testClock.setDormTime("2026-11-01T09:00:00+07:00");
    OnboardingRequest reOnboardReq =
        OnboardingRequest.builder()
            .fullName("Nguyen Van Billing")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079206111111")
            .phone("0987112233")
            .email("billing.e2e.tenant@uit.edu.vn")
            .permanentAddress("123 Linh Trung, Thu Duc, TP.HCM")
            .bedId(bed.getId())
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2026, 12, 31))
            .depositAmount(new BigDecimal("2000000"))
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(reOnboardReq))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> {
              BaseAppException bae = (BaseAppException) ex;
              return bae.getErrorCode() == ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES
                  && bae.getErrorCode().getHttpStatus() == HttpStatus.CONFLICT;
            });

    List<InvoiceDetailResponse> checkedOutTenantInvoices =
        invoiceService.getTenantInvoices(tenantUserId, null);
    assertThat(checkedOutTenantInvoices).hasSize(3);
    assertThat(checkedOutTenantInvoices.get(0).id()).isEqualTo(octInvoiceId);
    assertThat(checkedOutTenantInvoices.get(0).status()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(checkedOutTenantInvoices.get(0).vietQr()).isNotNull();
    assertValidEmvCoCrc16(checkedOutTenantInvoices.get(0).vietQr().qrPayload());
    assertThat(checkedOutTenantInvoices.get(1).id()).isEqualTo(sepInvoiceId);
    assertThat(checkedOutTenantInvoices.get(1).status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(checkedOutTenantInvoices.get(2).id()).isEqualTo(augInvoiceId);
    assertThat(checkedOutTenantInvoices.get(2).status()).isEqualTo(InvoiceStatus.PAID);

    MockMultipartFile webpProof =
        new MockMultipartFile("file", "oct-checkout.webp", "image/webp", VALID_WEBP_BYTES);
    InvoiceDetailResponse octProofSubmitted =
        invoiceService.submitProof(tenantUserId, octInvoiceId, webpProof);
    assertThat(octProofSubmitted.status()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);

    InvoiceProofContent tenantStreamedOctProof =
        invoiceService.loadProofContentForTenant(tenantUserId, octInvoiceId);
    assertThat(tenantStreamedOctProof.contentType()).isEqualTo("image/webp");
    assertThat(tenantStreamedOctProof.downloadFilename()).isEqualTo(octInvoiceCode + "-proof.webp");
    assertThat(tenantStreamedOctProof.resource().getContentAsByteArray())
        .isEqualTo(VALID_WEBP_BYTES);

    // Still blocked while October invoice is PENDING_CONFIRMATION
    assertThatThrownBy(() -> onboardingService.onboardTenant(reOnboardReq))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> {
              BaseAppException bae = (BaseAppException) ex;
              return bae.getErrorCode() == ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES
                  && bae.getErrorCode().getHttpStatus() == HttpStatus.CONFLICT;
            });

    InvoiceDetailResponse octTransferConfirmed =
        invoiceService.confirmTransferPayment(managerUserId, octInvoiceId);
    assertThat(octTransferConfirmed.status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(octTransferConfirmed.payment()).isNotNull();
    assertThat(octTransferConfirmed.payment().paymentMethod())
        .isEqualTo(PaymentMethod.VIETQR_TRANSFER);
    assertThat(octTransferConfirmed.payment().amount()).isEqualByComparingTo("1800000");

    // Re-onboarding now succeeds at room.basePrice = 2,000,000
    OnboardingResponse reOnboardRes = onboardingService.onboardTenant(reOnboardReq);
    assertThat(reOnboardRes.tenant().id()).isEqualTo(tenantId);
    assertThat(reOnboardRes.tenant().residentStatus()).isEqualTo(ResidentStatus.ACTIVE);
    assertThat(reOnboardRes.account().initialPassword()).isNull();
    assertThat(reOnboardRes.contract().id()).isNotEqualTo(contractId);
    assertThat(reOnboardRes.contract().status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(reOnboardRes.firstInvoice()).isNotNull();
    assertThat(reOnboardRes.firstInvoice().billingPeriod()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(reOnboardRes.firstInvoice().amount()).isEqualByComparingTo("2000000");
    assertThat(reOnboardRes.firstInvoice().dueOn()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(reOnboardRes.firstInvoice().status()).isEqualTo(InvoiceStatus.DEBT);

    InvoiceSummaryStatsResponse finalAllPeriodSummary = invoiceService.getAdminInvoiceSummary(null);
    assertThat(finalAllPeriodSummary.paidAmount()).isEqualByComparingTo("4800000");
    assertThat(finalAllPeriodSummary.paidCount()).isEqualTo(3L);
    assertThat(finalAllPeriodSummary.unpaidInTermAmount()).isEqualByComparingTo("2000000");
    assertThat(finalAllPeriodSummary.unpaidInTermCount()).isEqualTo(1L);
    assertThat(finalAllPeriodSummary.overdueAmount()).isEqualByComparingTo("0");
    assertThat(finalAllPeriodSummary.overdueCount()).isZero();
    assertThat(finalAllPeriodSummary.pendingConfirmationAmount()).isEqualByComparingTo("0");
    assertThat(finalAllPeriodSummary.pendingConfirmationCount()).isZero();
    assertThat(finalAllPeriodSummary.totalExpectedAmount()).isEqualByComparingTo("6800000");
    assertThat(finalAllPeriodSummary.totalInvoiceCount()).isEqualTo(4L);
  }

  private static void assertValidEmvCoCrc16(String qrPayload) {
    assertThat(qrPayload).isNotBlank();
    assertThat(qrPayload.length()).isGreaterThan(8);
    int crcStart = qrPayload.length() - 4;
    assertThat(qrPayload.substring(crcStart - 4, crcStart)).isEqualTo("6304");
    byte[] prefixBytes = qrPayload.substring(0, crcStart).getBytes(StandardCharsets.UTF_8);
    int crc = 0xFFFF;
    for (byte b : prefixBytes) {
      crc ^= (b & 0xFF) << 8;
      for (int i = 0; i < 8; i++) {
        crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
      }
    }
    String expectedCrc = String.format("%04X", crc);
    assertThat(qrPayload.substring(crcStart)).isEqualTo(expectedCrc);
  }
}
