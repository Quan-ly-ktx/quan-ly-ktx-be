package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.contract.CheckoutContractRequest;
import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractRenewal;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractDocumentRepository;
import com.uit.dormmanagement.repositories.ContractRenewalRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.schedulers.ContractStatusScheduler;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(ContractRenewalPostgresTest.TestClockConfig.class)
class ContractRenewalPostgresTest {

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
    private volatile Clock delegate =
        Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE);

    void setClock(Clock clock) {
      this.delegate = clock;
    }

    void reset() {
      this.delegate = Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE);
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
  @Autowired private ContractService contractService;
  @Autowired private RoomRepository roomRepository;
  @Autowired private RoomCodeRegistryRepository roomCodeRegistryRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ContractRenewalRepository contractRenewalRepository;
  @Autowired private ContractDocumentRepository contractDocumentRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private ContractStatusScheduler contractStatusScheduler;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID roomId;
  private Bed bed1;
  private Bed bed2;
  private Bed bed3;

  @BeforeEach
  void setUp() {
    testClock.reset();
    var roomResp =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.901",
                9,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    roomId = roomResp.id();
    List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(roomId);
    bed1 = beds.get(0);
    bed2 = beds.get(1);
    bed3 = beds.get(2);
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
    jdbcTemplate.execute(
        "TRUNCATE TABLE payment_transactions, invoices, contract_documents, contract_renewals, "
            + "contracts, room_code_registry, beds, room_amenities, rooms, tenants, users CASCADE");
  }

  private OnboardingResponse onboardAndMarkFirstInvoicePaid(
      Bed bed, String cccd, String email, String phone) {
    testClock.setClock(Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));
    OnboardingResponse onboarded =
        onboardingService.onboardTenant(
            OnboardingRequest.builder()
                .fullName("Nguyen Van Renewal")
                .gender(GenderPolicy.MALE)
                .idCardNumber(cccd)
                .phone(phone)
                .email(email)
                .permanentAddress("123 Nguyen Van Cu, TP.HCM")
                .bedId(bed.getId())
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2027, 2, 28))
                .depositAmount(new BigDecimal("1500000"))
                .build());

    Invoice firstInvoice = invoiceRepository.findById(onboarded.firstInvoice().id()).orElseThrow();
    firstInvoice.setStatus(InvoiceStatus.PAID);
    invoiceRepository.saveAndFlush(firstInvoice);
    return onboarded;
  }

  private void updateRoomBasePrice(BigDecimal newPrice) {
    Room room = roomRepository.findById(roomId).orElseThrow();
    room.setBasePrice(newPrice);
    roomRepository.saveAndFlush(room);
  }

  @Test
  @DisplayName(
      "Early renewal (day 15): captures current room.basePrice, keeps originalMonthlyRent/originalEndOn immutable, skips catch-up invoice, and ignores later room price changes")
  void earlyRenewal_locksCurrentRoomPrice_preservesOriginals_andSkipsCatchUp() {
    OnboardingResponse onboarded =
        onboardAndMarkFirstInvoicePaid(
            bed1, "079203890001", "renew.early@example.com", "0901890001");
    UUID contractId = onboarded.contract().id();

    updateRoomBasePrice(new BigDecimal("1800000"));
    testClock.setClock(Clock.fixed(Instant.parse("2027-02-15T03:00:00Z"), TimeConfig.DORM_ZONE));

    ContractDetailResponse response =
        contractService.renewContract(
            contractId, new RenewContractRequest(LocalDate.of(2027, 8, 31)), null);

    assertThat(response.endDate()).isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(response.monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(response.status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(response.renewals()).hasSize(1);
    assertThat(response.renewals().getFirst().sequenceNo()).isEqualTo(1);
    assertThat(response.renewals().getFirst().signedOn()).isEqualTo(LocalDate.of(2027, 2, 15));
    assertThat(response.renewals().getFirst().previousEndOn()).isEqualTo(LocalDate.of(2027, 2, 28));
    assertThat(response.renewals().getFirst().effectiveFrom()).isEqualTo(LocalDate.of(2027, 3, 1));
    assertThat(response.renewals().getFirst().newEndOn()).isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(response.renewals().getFirst().monthlyRent()).isEqualByComparingTo("1800000");

    Contract reloadedContract = contractRepository.findById(contractId).orElseThrow();
    assertThat(reloadedContract.getOriginalMonthlyRent()).isEqualByComparingTo("1500000");
    assertThat(reloadedContract.getOriginalEndOn()).isEqualTo(LocalDate.of(2027, 2, 28));

    List<ContractRenewal> renewals =
        contractRenewalRepository.findByContractIdOrderBySequenceNoAsc(contractId);
    assertThat(renewals).hasSize(1);
    assertThat(renewals.getFirst().getMonthlyRent()).isEqualByComparingTo("1800000");
    assertThat(renewals.getFirst().getMonthlyRent().scale()).isZero();

    assertThat(
            invoiceRepository.findByContractIdAndBillingPeriod(
                contractId, LocalDate.of(2027, 3, 1)))
        .isEmpty();

    // Subsequent room basePrice increase must NOT alter the already-signed renewal
    updateRoomBasePrice(new BigDecimal("2000000"));
    ContractRenewal afterPriceBump =
        contractRenewalRepository.findByContractIdOrderBySequenceNoAsc(contractId).getFirst();
    assertThat(afterPriceBump.getMonthlyRent()).isEqualByComparingTo("1800000");
    ContractDetailResponse reloadedDetail = contractService.getContractById(contractId);
    assertThat(reloadedDetail.monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(reloadedDetail.renewals()).hasSize(1);
    assertThat(reloadedDetail.renewals().getFirst().monthlyRent()).isEqualByComparingTo("1800000");
  }

  @Test
  @DisplayName(
      "Day-26 renewal: creates DEBT catch-up invoice for next month at locked renewal rent (idempotent if already present) and attaches optional annex")
  void day26Renewal_createsCatchUpInvoiceIdempotently_andAttachesOptionalAnnex() {
    OnboardingResponse contractA =
        onboardAndMarkFirstInvoicePaid(
            bed1, "079203890002", "renew.day26a@example.com", "0901890002");
    UUID contractAId = contractA.contract().id();

    OnboardingResponse contractB =
        onboardAndMarkFirstInvoicePaid(
            bed2, "079203890003", "renew.day26b@example.com", "0901890003");
    UUID contractBId = contractB.contract().id();
    Contract contractBEntity = contractRepository.findById(contractBId).orElseThrow();
    Invoice existingNextMonthInvoice =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202703-" + contractBEntity.getContractCode())
                .contract(contractBEntity)
                .billingPeriod(LocalDate.of(2027, 3, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2027, 3, 5))
                .status(InvoiceStatus.PAID)
                .build());

    updateRoomBasePrice(new BigDecimal("1800000"));
    testClock.setClock(Clock.fixed(Instant.parse("2027-02-26T04:00:00Z"), TimeConfig.DORM_ZONE));

    MockMultipartFile annexPdf =
        new MockMultipartFile(
            "annexDocuments",
            "phu-luc-1.pdf",
            "application/pdf",
            "%PDF-1.4 annex bytes".getBytes(StandardCharsets.UTF_8));

    ContractDetailResponse detailA =
        contractService.renewContract(
            contractAId, new RenewContractRequest(LocalDate.of(2027, 8, 31)), List.of(annexPdf));

    Invoice catchUpInvoice =
        invoiceRepository
            .findByContractIdAndBillingPeriod(contractAId, LocalDate.of(2027, 3, 1))
            .orElseThrow();
    assertThat(catchUpInvoice.getAmount()).isEqualByComparingTo("1800000");
    assertThat(catchUpInvoice.getAmount().scale()).isZero();
    assertThat(catchUpInvoice.getDueOn()).isEqualTo(LocalDate.of(2027, 3, 5));
    assertThat(catchUpInvoice.getStatus()).isEqualTo(InvoiceStatus.DEBT);

    assertThat(detailA.documents()).hasSize(1);
    assertThat(detailA.documents().getFirst().documentCategory())
        .isEqualTo(ContractDocumentCategory.RENEWAL_ANNEX);
    UUID renewalId = detailA.renewals().getFirst().id();
    assertThat(detailA.documents().getFirst().renewalId()).isEqualTo(renewalId);
    UUID docId = detailA.documents().getFirst().id();
    UUID fkRenewalId =
        jdbcTemplate.queryForObject(
            "SELECT renewal_id FROM contract_documents WHERE id = ?", UUID.class, docId);
    assertThat(fkRenewalId).isEqualTo(renewalId);
    assertThat(contractService.getContractById(contractAId).documents()).hasSize(1);

    // Contract B already had a 2027-03-01 invoice -> idempotently kept untouched
    ContractDetailResponse detailB =
        contractService.renewContract(
            contractBId, new RenewContractRequest(LocalDate.of(2027, 8, 31)), null);
    assertThat(detailB.monthlyRent()).isEqualByComparingTo("1800000");
    List<Invoice> contractBInvoices = invoiceRepository.findByContractId(contractBId);
    assertThat(contractBInvoices).hasSize(2);
    Invoice reloadedExisting =
        invoiceRepository
            .findByContractIdAndBillingPeriod(contractBId, LocalDate.of(2027, 3, 1))
            .orElseThrow();
    assertThat(reloadedExisting.getId()).isEqualTo(existingNextMonthInvoice.getId());
    assertThat(reloadedExisting.getAmount()).isEqualByComparingTo("1500000");
    assertThat(reloadedExisting.getStatus()).isEqualTo(InvoiceStatus.PAID);
  }

  @Test
  @DisplayName(
      "Unsettled debt blocks renewal on DEBT and PENDING_CONFIRMATION, and succeeds once invoice is PAID")
  void unsettledDebt_blocksOnDebtAndPendingConfirmation_thenPaidSucceeds() {
    testClock.setClock(Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));
    OnboardingResponse onboarded =
        onboardingService.onboardTenant(
            OnboardingRequest.builder()
                .fullName("Nguyen Van Debt")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203890004")
                .phone("0901890004")
                .email("renew.debt@example.com")
                .permanentAddress("123 Nguyen Van Cu, TP.HCM")
                .bedId(bed1.getId())
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2027, 2, 28))
                .depositAmount(new BigDecimal("1500000"))
                .build());
    UUID contractId = onboarded.contract().id();
    Invoice firstInvoice = invoiceRepository.findById(onboarded.firstInvoice().id()).orElseThrow();

    testClock.setClock(Clock.fixed(Instant.parse("2027-02-10T02:00:00Z"), TimeConfig.DORM_ZONE));
    RenewContractRequest renewRequest = new RenewContractRequest(LocalDate.of(2027, 8, 31));

    assertThatThrownBy(() -> contractService.renewContract(contractId, renewRequest, null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES);
    assertThat(contractRenewalRepository.findByContractIdOrderBySequenceNoAsc(contractId))
        .isEmpty();

    firstInvoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    firstInvoice.setProofFileKey("invoices/proof-renew-test.jpg");
    firstInvoice.setProofSubmittedAt(Instant.parse("2027-02-10T01:00:00Z"));
    invoiceRepository.saveAndFlush(firstInvoice);

    assertThatThrownBy(() -> contractService.renewContract(contractId, renewRequest, null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES);
    assertThat(contractRenewalRepository.findByContractIdOrderBySequenceNoAsc(contractId))
        .isEmpty();

    firstInvoice.setStatus(InvoiceStatus.PAID);
    invoiceRepository.saveAndFlush(firstInvoice);

    ContractDetailResponse renewed = contractService.renewContract(contractId, renewRequest, null);
    assertThat(renewed.endDate()).isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(renewed.renewals()).hasSize(1);
  }

  @Test
  @DisplayName(
      "Post-expiry renewal is rejected with 409 CONTRACT_409_EXPIRED at Asia/Ho_Chi_Minh midnight while last second before midnight succeeds")
  void expiryBoundary_enforcesAsiaHoChiMinhMidnightCutoff() {
    OnboardingResponse onboarded =
        onboardAndMarkFirstInvoicePaid(
            bed1, "079203890005", "renew.expiry@example.com", "0901890005");
    UUID contractId = onboarded.contract().id();
    RenewContractRequest renewRequest = new RenewContractRequest(LocalDate.of(2027, 8, 31));

    // 2027-02-28T17:00:00Z == 2027-03-01T00:00:00+07:00
    testClock.setClock(Clock.fixed(Instant.parse("2027-02-28T17:00:00Z"), TimeConfig.DORM_ZONE));
    assertThatThrownBy(() -> contractService.renewContract(contractId, renewRequest, null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_EXPIRED);
    assertThat(contractRenewalRepository.findByContractIdOrderBySequenceNoAsc(contractId))
        .isEmpty();

    // 2027-02-28T16:59:59Z == 2027-02-28T23:59:59+07:00 (same day as originalEndOn -> valid)
    testClock.setClock(Clock.fixed(Instant.parse("2027-02-28T16:59:59Z"), TimeConfig.DORM_ZONE));
    ContractDetailResponse renewed = contractService.renewContract(contractId, renewRequest, null);
    assertThat(renewed.renewals()).hasSize(1);
    assertThat(renewed.renewals().getFirst().signedOn()).isEqualTo(LocalDate.of(2027, 2, 28));
    assertThat(
            invoiceRepository.findByContractIdAndBillingPeriod(
                contractId, LocalDate.of(2027, 3, 1)))
        .isPresent();
  }

  @Test
  @DisplayName(
      "Sequential 2nd renewal chains from 1st renewal, locks updated room price, and preserves 1st renewal and original contract")
  void sequentialSecondRenewal_chainsFromFirstRenewal_andKeepsOriginalsImmutable() {
    OnboardingResponse onboarded =
        onboardAndMarkFirstInvoicePaid(
            bed1, "079203890006", "renew.seq2@example.com", "0901890006");
    UUID contractId = onboarded.contract().id();

    updateRoomBasePrice(new BigDecimal("1800000"));
    testClock.setClock(Clock.fixed(Instant.parse("2027-02-10T02:00:00Z"), TimeConfig.DORM_ZONE));
    contractService.renewContract(
        contractId, new RenewContractRequest(LocalDate.of(2027, 8, 31)), null);

    updateRoomBasePrice(new BigDecimal("2000000"));
    testClock.setClock(Clock.fixed(Instant.parse("2027-08-15T02:00:00Z"), TimeConfig.DORM_ZONE));
    ContractDetailResponse secondRes =
        contractService.renewContract(
            contractId, new RenewContractRequest(LocalDate.of(2028, 2, 29)), null);

    assertThat(secondRes.endDate()).isEqualTo(LocalDate.of(2028, 2, 29));
    assertThat(secondRes.monthlyRent()).isEqualByComparingTo("2000000");
    assertThat(secondRes.renewals()).hasSize(2);
    assertThat(secondRes.renewals().get(0).sequenceNo()).isEqualTo(1);
    assertThat(secondRes.renewals().get(0).monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(secondRes.renewals().get(1).sequenceNo()).isEqualTo(2);
    assertThat(secondRes.renewals().get(1).previousEndOn()).isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(secondRes.renewals().get(1).effectiveFrom()).isEqualTo(LocalDate.of(2027, 9, 1));
    assertThat(secondRes.renewals().get(1).newEndOn()).isEqualTo(LocalDate.of(2028, 2, 29));
    assertThat(secondRes.renewals().get(1).monthlyRent()).isEqualByComparingTo("2000000");

    ContractDetailResponse reloaded = contractService.getContractById(contractId);
    assertThat(reloaded.renewals()).hasSize(2);
    assertThat(reloaded.renewals().get(0).monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(reloaded.renewals().get(1).monthlyRent()).isEqualByComparingTo("2000000");

    Contract dbContract = contractRepository.findById(contractId).orElseThrow();
    assertThat(dbContract.getOriginalMonthlyRent()).isEqualByComparingTo("1500000");
    assertThat(dbContract.getOriginalEndOn()).isEqualTo(LocalDate.of(2027, 2, 28));
  }

  @Test
  @DisplayName(
      "FR-10 uploadDocuments with RENEWAL_ANNEX + renewalId persists against PostgreSQL FK/CHECK constraints and rejects invalid links")
  void uploadDocuments_renewalAnnexWithRenewalId_persistsAgainstPostgresConstraints() {
    OnboardingResponse onboardA =
        onboardAndMarkFirstInvoicePaid(
            bed1, "079203890007", "doc.annex.a@example.com", "0901890007");
    UUID contractAId = onboardA.contract().id();
    UUID userAId = onboardA.tenant().userId();

    OnboardingResponse onboardB =
        onboardAndMarkFirstInvoicePaid(
            bed2, "079203890008", "doc.annex.b@example.com", "0901890008");
    UUID contractBId = onboardB.contract().id();

    testClock.setClock(Clock.fixed(Instant.parse("2027-02-15T02:00:00Z"), TimeConfig.DORM_ZONE));
    ContractDetailResponse renewedA =
        contractService.renewContract(
            contractAId, new RenewContractRequest(LocalDate.of(2027, 8, 31)), null);
    ContractDetailResponse renewedB =
        contractService.renewContract(
            contractBId, new RenewContractRequest(LocalDate.of(2027, 8, 31)), null);
    UUID renewalAId = renewedA.renewals().getFirst().id();
    UUID renewalBId = renewedB.renewals().getFirst().id();

    MockMultipartFile validAnnexPdf =
        new MockMultipartFile(
            "files",
            "phu-luc-sau.pdf",
            "application/pdf",
            "%PDF-1.4 deferred annex".getBytes(StandardCharsets.UTF_8));

    List<ContractDocumentResponse> uploaded =
        contractService.uploadDocuments(
            contractAId,
            ContractDocumentCategory.RENEWAL_ANNEX,
            renewalAId,
            List.of(validAnnexPdf));

    assertThat(uploaded).hasSize(1);
    ContractDocumentResponse docRes = uploaded.getFirst();
    assertThat(docRes.renewalId()).isEqualTo(renewalAId);
    assertThat(docRes.fileType()).isEqualTo("application/pdf");
    assertThat(docRes.documentCategory()).isEqualTo(ContractDocumentCategory.RENEWAL_ANNEX);

    UUID dbRenewalId =
        jdbcTemplate.queryForObject(
            "SELECT renewal_id FROM contract_documents WHERE id = ?", UUID.class, docRes.id());
    assertThat(dbRenewalId).isEqualTo(renewalAId);

    // Tenant history & detail return renewalId and tenant-scoped fileUrl
    List<ContractDetailResponse> tenantHistory = contractService.getMyContractHistory(userAId);
    assertThat(tenantHistory).hasSize(1);
    assertThat(tenantHistory.getFirst().documents()).hasSize(1);
    assertThat(tenantHistory.getFirst().documents().getFirst().renewalId()).isEqualTo(renewalAId);
    assertThat(tenantHistory.getFirst().documents().getFirst().fileUrl())
        .isEqualTo(
            "/api/v1/tenant/contracts/" + contractAId + "/documents/" + docRes.id() + "/content");

    ContractDetailResponse tenantDetail = contractService.getMyContractById(userAId, contractAId);
    assertThat(tenantDetail.documents().getFirst().renewalId()).isEqualTo(renewalAId);

    // Rejections (missing renewalId, cross-contract renewalId, non-annex with renewalId)
    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractAId,
                    ContractDocumentCategory.RENEWAL_ANNEX,
                    null,
                    List.of(validAnnexPdf)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.INVALID_DOCUMENT_RENEWAL_LINK);

    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractAId,
                    ContractDocumentCategory.RENEWAL_ANNEX,
                    renewalBId,
                    List.of(validAnnexPdf)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.INVALID_DOCUMENT_RENEWAL_LINK);

    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractAId,
                    ContractDocumentCategory.ORIGINAL_CONTRACT,
                    renewalAId,
                    List.of(validAnnexPdf)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.INVALID_DOCUMENT_RENEWAL_LINK);
  }

  @Test
  @DisplayName(
      "findAllByStatusAndEffectiveEndOnLessThanEqual & ContractStatusScheduler use latest renewal end date and never mutate Contract.status")
  void effectiveEndDateQueryAndScheduler_respectLatestRenewalAndNeverMutateStatus() {
    // Contract 1: unrenewed ACTIVE (originalEndOn = 2027-02-28)
    OnboardingResponse unrenewedOnboard =
        onboardAndMarkFirstInvoicePaid(
            bed1, "079203890009", "sched.unrenewed@example.com", "0901890009");
    UUID unrenewedId = unrenewedOnboard.contract().id();

    // Contract 2: ACTIVE renewed to 2027-08-31
    OnboardingResponse renewedOnboard =
        onboardAndMarkFirstInvoicePaid(
            bed2, "079203890010", "sched.renewed@example.com", "0901890010");
    UUID renewedId = renewedOnboard.contract().id();

    // Contract 3: LIQUIDATED (originalEndOn = 2027-02-28)
    OnboardingResponse liquidatedOnboard =
        onboardAndMarkFirstInvoicePaid(
            bed3, "079203890011", "sched.liquidated@example.com", "0901890011");
    UUID liquidatedId = liquidatedOnboard.contract().id();

    testClock.setClock(Clock.fixed(Instant.parse("2027-02-15T02:00:00Z"), TimeConfig.DORM_ZONE));
    contractService.renewContract(
        renewedId, new RenewContractRequest(LocalDate.of(2027, 8, 31)), null);
    contractService.terminateContract(
        null,
        liquidatedId,
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.AVAILABLE)
            .handoverConfirmed(true)
            .build());

    // Threshold 2027-03-15 -> unrenewed ACTIVE included; renewed ACTIVE (2027-08-31) & LIQUIDATED
    // excluded
    List<Contract> dueByMarch =
        contractRepository.findAllByStatusAndEffectiveEndOnLessThanEqual(
            ContractStatus.ACTIVE, LocalDate.of(2027, 3, 15));
    assertThat(dueByMarch).extracting(Contract::getId).containsExactly(unrenewedId);

    // Threshold 2027-08-31 -> both unrenewed and renewed ACTIVE contracts included
    List<Contract> dueByAugust =
        contractRepository.findAllByStatusAndEffectiveEndOnLessThanEqual(
            ContractStatus.ACTIVE, LocalDate.of(2027, 8, 31));
    assertThat(dueByAugust)
        .extracting(Contract::getId)
        .containsExactlyInAnyOrder(unrenewedId, renewedId);

    // 2nd renewal extends Contract 2 to 2028-02-29 -> excluded again at 2027-08-31
    testClock.setClock(Clock.fixed(Instant.parse("2027-08-15T02:00:00Z"), TimeConfig.DORM_ZONE));
    contractService.renewContract(
        renewedId, new RenewContractRequest(LocalDate.of(2028, 2, 29)), null);
    List<Contract> dueByAugustAfterSecondRenewal =
        contractRepository.findAllByStatusAndEffectiveEndOnLessThanEqual(
            ContractStatus.ACTIVE, LocalDate.of(2027, 8, 31));
    assertThat(dueByAugustAfterSecondRenewal)
        .extracting(Contract::getId)
        .containsExactly(unrenewedId);

    // Scheduler execution executes cleanly without mutating status
    testClock.setClock(Clock.fixed(Instant.parse("2027-02-15T02:00:00Z"), TimeConfig.DORM_ZONE));
    contractStatusScheduler.updateExpiringContracts();

    assertThat(contractRepository.findById(unrenewedId).orElseThrow().getStatus())
        .isEqualTo(ContractStatus.ACTIVE);
    assertThat(contractRepository.findById(renewedId).orElseThrow().getStatus())
        .isEqualTo(ContractStatus.ACTIVE);
    assertThat(contractRepository.findById(liquidatedId).orElseThrow().getStatus())
        .isEqualTo(ContractStatus.LIQUIDATED);
  }
}
