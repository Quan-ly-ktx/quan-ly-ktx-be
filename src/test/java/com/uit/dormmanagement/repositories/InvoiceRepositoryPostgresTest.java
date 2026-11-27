package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractRenewal;
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
import com.uit.dormmanagement.services.RoomService;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Transactional
class InvoiceRepositoryPostgresTest {

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

  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ContractRenewalRepository contractRenewalRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private RoomService roomService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  private Tenant tenantOne;
  private Tenant tenantTwo;
  private Contract liquidatedContract;
  private Contract activeContract;
  private Contract renewedContract;
  private ContractRenewal renewalOne;
  private ContractRenewal renewalTwo;
  private Invoice invDebtOverdue;
  private Invoice invPendingOld;
  private Invoice invPaidSameDue;
  private Invoice invDebtOnDueBoundary;
  private Invoice invOtherTenantFuture;

  @BeforeEach
  void setUp() {
    var roomA =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.811",
                8,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    var roomB =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.812",
                8,
                RoomType.FOUR_BEDS,
                GenderPolicy.FEMALE,
                new BigDecimal("1600000"),
                List.of()));
    List<Bed> bedsA = bedRepository.findByRoomId(roomA.id());
    List<Bed> bedsB = bedRepository.findByRoomId(roomB.id());

    User userOne =
        userRepository.save(
            User.builder()
                .email("alpha.resident@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    tenantOne =
        tenantRepository.save(
            Tenant.builder()
                .user(userOne)
                .tenantCode("NT-REPO-01")
                .fullName("Nguyen Van Minh")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203009901")
                .phone("0901999901")
                .permanentAddress("Thu Duc, TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    User userTwo =
        userRepository.save(
            User.builder()
                .email("beta.student@uit.edu.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    tenantTwo =
        tenantRepository.save(
            Tenant.builder()
                .user(userTwo)
                .tenantCode("NT-REPO-02")
                .fullName("Tran Thi Lan")
                .gender(GenderPolicy.FEMALE)
                .idCardNumber("079203009902")
                .phone("0901999902")
                .permanentAddress("Quan 3, TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    liquidatedContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-2026-8101")
                .tenant(tenantOne)
                .bed(bedsA.get(0))
                .roomCodeSnapshot("P.811")
                .bedCodeSnapshot(bedsA.get(0).getBedCode())
                .startOn(LocalDate.of(2026, 1, 1))
                .originalEndOn(LocalDate.of(2026, 6, 30))
                .originalMonthlyRent(new BigDecimal("1400000"))
                .depositAmount(new BigDecimal("1400000"))
                .status(ContractStatus.LIQUIDATED)
                .checkedOutAt(Instant.parse("2026-06-30T10:00:00Z"))
                .build());

    activeContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-2026-8102")
                .tenant(tenantOne)
                .bed(bedsA.get(1))
                .roomCodeSnapshot("P.811")
                .bedCodeSnapshot(bedsA.get(1).getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    renewedContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-2026-8103")
                .tenant(tenantTwo)
                .bed(bedsB.get(0))
                .roomCodeSnapshot("P.812")
                .bedCodeSnapshot(bedsB.get(0).getBedCode())
                .startOn(LocalDate.of(2026, 8, 1))
                .originalEndOn(LocalDate.of(2027, 1, 31))
                .originalMonthlyRent(new BigDecimal("1600000"))
                .depositAmount(new BigDecimal("1600000"))
                .status(ContractStatus.ACTIVE)
                .build());

    renewalOne =
        contractRenewalRepository.save(
            ContractRenewal.builder()
                .contract(renewedContract)
                .sequenceNo(1)
                .signedOn(LocalDate.of(2027, 1, 20))
                .previousEndOn(LocalDate.of(2027, 1, 31))
                .effectiveFrom(LocalDate.of(2027, 2, 1))
                .newEndOn(LocalDate.of(2027, 7, 31))
                .monthlyRent(new BigDecimal("1650000"))
                .build());

    renewalTwo =
        contractRenewalRepository.save(
            ContractRenewal.builder()
                .contract(renewedContract)
                .sequenceNo(2)
                .signedOn(LocalDate.of(2027, 7, 15))
                .previousEndOn(LocalDate.of(2027, 7, 31))
                .effectiveFrom(LocalDate.of(2027, 8, 1))
                .newEndOn(LocalDate.of(2027, 12, 31))
                .monthlyRent(new BigDecimal("1800000"))
                .build());

    invPaidSameDue =
        invoiceRepository.save(
            Invoice.builder()
                .invoiceCode("INV-202606-HD-2026-8101")
                .contract(liquidatedContract)
                .billingPeriod(LocalDate.of(2026, 6, 1))
                .amount(new BigDecimal("1400000"))
                .dueOn(LocalDate.of(2026, 6, 5))
                .status(InvoiceStatus.PAID)
                .build());

    invPendingOld =
        invoiceRepository.save(
            Invoice.builder()
                .invoiceCode("INV-202608-HD-2026-8103")
                .contract(renewedContract)
                .billingPeriod(LocalDate.of(2026, 8, 1))
                .amount(new BigDecimal("1600000"))
                .dueOn(LocalDate.of(2026, 8, 5))
                .status(InvoiceStatus.PENDING_CONFIRMATION)
                .proofFileKey("invoices/proof-8103.jpg")
                .proofSubmittedAt(Instant.parse("2026-08-04T09:00:00Z"))
                .build());

    invDebtOverdue =
        invoiceRepository.save(
            Invoice.builder()
                .invoiceCode("INV-202609-HD-2026-8102")
                .contract(activeContract)
                .billingPeriod(LocalDate.of(2026, 9, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 9, 5))
                .status(InvoiceStatus.DEBT)
                .build());

    invDebtOnDueBoundary =
        invoiceRepository.save(
            Invoice.builder()
                .invoiceCode("INV-202610-HD-2026-8102")
                .contract(activeContract)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 10, 5))
                .status(InvoiceStatus.DEBT)
                .build());

    invOtherTenantFuture =
        invoiceRepository.save(
            Invoice.builder()
                .invoiceCode("INV-202610-HD-2026-8103")
                .contract(renewedContract)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1600000"))
                .dueOn(LocalDate.of(2026, 10, 10))
                .status(InvoiceStatus.DEBT)
                .build());

    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("findByIdForUpdate loads existing invoice with pessimistic write lock and empty when absent")
  void findByIdForUpdate_shouldLoadExistingOrEmpty() {
    assertThat(invoiceRepository.findByIdForUpdate(invDebtOverdue.getId()))
        .isPresent()
        .get()
        .extracting(Invoice::getInvoiceCode)
        .isEqualTo("INV-202609-HD-2026-8102");
    assertThat(invoiceRepository.findByIdForUpdate(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("Tenant invoice queries isolate by tenant, order billingPeriod DESC then issuedAt DESC, and filter by status")
  void tenantInvoiceQueries_shouldOrderAndFilterByStatus() {
    // Insert a second invoice for the same billing period on liquidatedContract with an explicit earlier issued_at
    UUID olderSamePeriodId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO invoices(id, invoice_code, contract_id, billing_period, amount, due_on, status, issued_at) "
            + "VALUES (?, 'INV-202609-HD-2026-8101', ?, '2026-09-01', 1400000, '2026-09-05', 'DEBT', TIMESTAMPTZ '2026-08-25T00:00:00Z')",
        olderSamePeriodId,
        liquidatedContract.getId());
    entityManager.clear();

    List<Invoice> allForTenantOne =
        invoiceRepository.findByContractTenantIdOrderByBillingPeriodDescIssuedAtDesc(
            tenantOne.getId());
    assertThat(allForTenantOne)
        .extracting(Invoice::getInvoiceCode)
        .containsExactly(
            "INV-202610-HD-2026-8102",
            "INV-202609-HD-2026-8102",
            "INV-202609-HD-2026-8101",
            "INV-202606-HD-2026-8101");

    List<Invoice> debtForTenantOne =
        invoiceRepository.findByContractTenantIdAndStatusOrderByBillingPeriodDescIssuedAtDesc(
            tenantOne.getId(), InvoiceStatus.DEBT);
    assertThat(debtForTenantOne)
        .extracting(Invoice::getInvoiceCode)
        .containsExactly(
            "INV-202610-HD-2026-8102", "INV-202609-HD-2026-8102", "INV-202609-HD-2026-8101");

    List<Invoice> paidForTenantOne =
        invoiceRepository.findByContractTenantIdAndStatusOrderByBillingPeriodDescIssuedAtDesc(
            tenantOne.getId(), InvoiceStatus.PAID);
    assertThat(paidForTenantOne)
        .extracting(Invoice::getInvoiceCode)
        .containsExactly("INV-202606-HD-2026-8101");
  }

  @Test
  @DisplayName("findInvoicesWithFilters supports 5-field search, status, billingPeriod, overdueOnly intersection, and paging")
  void findInvoicesWithFilters_shouldFilterSearchAndRespectOverdueRules() {
    LocalDate today = LocalDate.of(2026, 10, 5);
    PageRequest pageDesc =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "billingPeriod", "invoiceCode"));

    // 1. Unfiltered & blank search
    Page<Invoice> all =
        invoiceRepository.findInvoicesWithFilters(null, null, null, false, today, pageDesc);
    assertThat(all.getTotalElements()).isEqualTo(5);
    assertThat(
            invoiceRepository
                .findInvoicesWithFilters("", null, null, false, today, pageDesc)
                .getTotalElements())
        .isEqualTo(5);

    // 2. Search across all 5 fields (case-insensitive)
    assertThat(
            invoiceRepository
                .findInvoicesWithFilters("inv-202608-hd", null, null, false, today, pageDesc)
                .getContent())
        .extracting(Invoice::getId)
        .containsExactly(invPendingOld.getId());

    assertThat(
            invoiceRepository
                .findInvoicesWithFilters("van minh", null, null, false, today, pageDesc)
                .getContent())
        .extracting(Invoice::getId)
        .containsExactlyInAnyOrder(
            invDebtOnDueBoundary.getId(), invDebtOverdue.getId(), invPaidSameDue.getId());

    assertThat(
            invoiceRepository
                .findInvoicesWithFilters("p.812", null, null, false, today, pageDesc)
                .getContent())
        .extracting(Invoice::getId)
        .containsExactlyInAnyOrder(invOtherTenantFuture.getId(), invPendingOld.getId());

    assertThat(
            invoiceRepository
                .findInvoicesWithFilters("079203009901", null, null, false, today, pageDesc)
                .getContent())
        .extracting(Invoice::getId)
        .containsExactlyInAnyOrder(
            invDebtOnDueBoundary.getId(), invDebtOverdue.getId(), invPaidSameDue.getId());

    assertThat(
            invoiceRepository
                .findInvoicesWithFilters("BETA.STUDENT@UIT", null, null, false, today, pageDesc)
                .getContent())
        .extracting(Invoice::getId)
        .containsExactlyInAnyOrder(invOtherTenantFuture.getId(), invPendingOld.getId());

    // 3. Status + billingPeriod filters
    assertThat(
            invoiceRepository
                .findInvoicesWithFilters(
                    null, InvoiceStatus.DEBT, LocalDate.of(2026, 10, 1), false, today, pageDesc)
                .getContent())
        .extracting(Invoice::getId)
        .containsExactlyInAnyOrder(invDebtOnDueBoundary.getId(), invOtherTenantFuture.getId());

    // 4. overdueOnly excludes dueOn == today, dueOn > today, PENDING_CONFIRMATION, and PAID
    assertThat(
            invoiceRepository
                .findInvoicesWithFilters(null, null, null, true, today, pageDesc)
                .getContent())
        .extracting(Invoice::getId)
        .containsExactly(invDebtOverdue.getId());

    assertThat(
            invoiceRepository
                .findInvoicesWithFilters(null, InvoiceStatus.DEBT, null, true, today, pageDesc)
                .getContent())
        .extracting(Invoice::getId)
        .containsExactly(invDebtOverdue.getId());

    // 5. overdueOnly intersects cleanly with non-DEBT status -> 0 rows
    assertThat(
            invoiceRepository
                .findInvoicesWithFilters(null, InvoiceStatus.PAID, null, true, today, pageDesc)
                .getTotalElements())
        .isZero();
    assertThat(
            invoiceRepository
                .findInvoicesWithFilters(
                    null, InvoiceStatus.PENDING_CONFIRMATION, null, true, today, pageDesc)
                .getTotalElements())
        .isZero();

    // 6. Pagination slice
    Page<Invoice> page0 =
        invoiceRepository.findInvoicesWithFilters(
            null,
            null,
            null,
            false,
            today,
            PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "invoiceCode")));
    assertThat(page0.getTotalElements()).isEqualTo(5);
    assertThat(page0.getTotalPages()).isEqualTo(3);
    assertThat(page0.getContent()).hasSize(2);
  }

  @Test
  @DisplayName("findAllForSummary returns status, dueOn, and amount across all or single billingPeriod")
  void findAllForSummary_shouldReturnAllOrFilteredProjections() {
    List<InvoiceRepository.InvoiceSummaryProjection> allSummaries =
        invoiceRepository.findAllForSummary(null);
    assertThat(allSummaries).hasSize(5);
    assertThat(allSummaries)
        .extracting(InvoiceRepository.InvoiceSummaryProjection::getStatus)
        .containsExactlyInAnyOrder(
            InvoiceStatus.PAID,
            InvoiceStatus.PENDING_CONFIRMATION,
            InvoiceStatus.DEBT,
            InvoiceStatus.DEBT,
            InvoiceStatus.DEBT);

    List<InvoiceRepository.InvoiceSummaryProjection> octSummaries =
        invoiceRepository.findAllForSummary(LocalDate.of(2026, 10, 1));
    assertThat(octSummaries).hasSize(2);
    assertThat(octSummaries)
        .extracting(InvoiceRepository.InvoiceSummaryProjection::getDueOn)
        .containsExactlyInAnyOrder(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10));
    assertThat(octSummaries)
        .extracting(InvoiceRepository.InvoiceSummaryProjection::getAmount)
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactlyInAnyOrder(new BigDecimal("1500000"), new BigDecimal("1600000"));
  }

  @Test
  @DisplayName("findActiveContractsCoveringBillingPeriod evaluates effective term across original and renewed bounds")
  void findActiveContractsCoveringBillingPeriod_shouldRespectEffectiveTermAndOrder() {
    assertThat(
            contractRepository.findActiveContractsCoveringBillingPeriod(
                ContractStatus.ACTIVE, LocalDate.of(2026, 7, 1)))
        .isEmpty();

    assertThat(
            contractRepository.findActiveContractsCoveringBillingPeriod(
                ContractStatus.ACTIVE, LocalDate.of(2026, 8, 1)))
        .extracting(Contract::getId)
        .containsExactly(renewedContract.getId());

    assertThat(
            contractRepository.findActiveContractsCoveringBillingPeriod(
                ContractStatus.ACTIVE, LocalDate.of(2026, 9, 1)))
        .extracting(Contract::getId)
        .containsExactly(renewedContract.getId(), activeContract.getId());

    assertThat(
            contractRepository.findActiveContractsCoveringBillingPeriod(
                ContractStatus.ACTIVE, LocalDate.of(2027, 2, 1)))
        .extracting(Contract::getId)
        .containsExactly(renewedContract.getId(), activeContract.getId());

    assertThat(
            contractRepository.findActiveContractsCoveringBillingPeriod(
                ContractStatus.ACTIVE, LocalDate.of(2027, 3, 1)))
        .extracting(Contract::getId)
        .containsExactly(renewedContract.getId());

    assertThat(
            contractRepository.findActiveContractsCoveringBillingPeriod(
                ContractStatus.ACTIVE, LocalDate.of(2027, 12, 1)))
        .extracting(Contract::getId)
        .containsExactly(renewedContract.getId());

    assertThat(
            contractRepository.findActiveContractsCoveringBillingPeriod(
                ContractStatus.ACTIVE, LocalDate.of(2028, 1, 1)))
        .isEmpty();
  }

  @Test
  @DisplayName("findCoveringRenewalsOrderBySequenceDesc selects exact renewal covering billingPeriod")
  void findCoveringRenewalsOrderBySequenceDesc_shouldSelectExactRenewalForPeriod() {
    assertThat(
            contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
                renewedContract.getId(), LocalDate.of(2027, 1, 1)))
        .isEmpty();

    assertThat(
            contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
                renewedContract.getId(), LocalDate.of(2027, 2, 1)))
        .extracting(ContractRenewal::getId)
        .containsExactly(renewalOne.getId());

    assertThat(
            contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
                renewedContract.getId(), LocalDate.of(2027, 7, 1)))
        .extracting(ContractRenewal::getId)
        .containsExactly(renewalOne.getId());

    assertThat(
            contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
                renewedContract.getId(), LocalDate.of(2027, 8, 1)))
        .extracting(ContractRenewal::getId)
        .containsExactly(renewalTwo.getId());

    assertThat(
            contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
                renewedContract.getId(), LocalDate.of(2028, 1, 1)))
        .isEmpty();

    assertThat(
            contractRenewalRepository.findCoveringRenewalsOrderBySequenceDesc(
                activeContract.getId(), LocalDate.of(2027, 2, 1)))
        .isEmpty();
  }
}
