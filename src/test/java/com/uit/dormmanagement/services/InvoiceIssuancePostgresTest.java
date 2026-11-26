package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.configs.TimeConfig;
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
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRenewalRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(InvoiceIssuancePostgresTest.TestClockConfig.class)
@Transactional
class InvoiceIssuancePostgresTest {

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

    void setClock(Clock clock) {
      this.delegate = clock;
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
  @Autowired private InvoiceIssuanceService invoiceIssuanceService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ContractRenewalRepository contractRenewalRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private RoomService roomService;
  @Autowired private EntityManager entityManager;

  private Tenant tenant;
  private Tenant otherTenant;
  private Contract liquidatedContract;
  private Contract activeContract;
  private Contract otherActiveContract;

  @BeforeEach
  void setUp() {
    testClock.reset();

    var roomResp =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.910",
                9,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    List<Bed> beds = bedRepository.findByRoomId(roomResp.id());

    User tenantUser =
        userRepository.save(
            User.builder()
                .email("tenant_inv_pg@test.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    tenant =
        tenantRepository.save(
            Tenant.builder()
                .user(tenantUser)
                .tenantCode("NT-INV-01")
                .fullName("Nguyen Van Invoice")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203881101")
                .phone("0901881101")
                .permanentAddress("Thu Duc, TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    User otherUser =
        userRepository.save(
            User.builder()
                .email("other_inv_pg@test.vn")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    otherTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(otherUser)
                .tenantCode("NT-INV-02")
                .fullName("Tran Van Other")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203881102")
                .phone("0901881102")
                .permanentAddress("Quan 1, TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    liquidatedContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-2026-9101")
                .tenant(tenant)
                .bed(beds.get(0))
                .roomCodeSnapshot("P.910")
                .bedCodeSnapshot(beds.get(0).getBedCode())
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
                .contractCode("HD-2026-9102")
                .tenant(tenant)
                .bed(beds.get(1))
                .roomCodeSnapshot("P.910")
                .bedCodeSnapshot(beds.get(1).getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    otherActiveContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-2026-9103")
                .tenant(otherTenant)
                .bed(beds.get(2))
                .roomCodeSnapshot("P.910")
                .bedCodeSnapshot(beds.get(2).getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 2, 28))
                .originalMonthlyRent(new BigDecimal("1500000"))
                .depositAmount(new BigDecimal("1500000"))
                .status(ContractStatus.ACTIVE)
                .build());

    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void resetClock() {
    testClock.reset();
  }

  @Test
  @DisplayName(
      "issueMonthlyInvoiceIfAbsent persists under Flyway constraints and is idempotent on repeat")
  void issueMonthlyInvoiceIfAbsent_persistsAndIsIdempotent() {
    LocalDate period = LocalDate.of(2026, 9, 1);

    Invoice first =
        invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            activeContract, period, new BigDecimal("1500000.00"));
    entityManager.flush();
    entityManager.clear();

    Invoice loaded = invoiceRepository.findById(first.getId()).orElseThrow();
    assertThat(loaded.getInvoiceCode()).isEqualTo("INV-202609-HD-2026-9102");
    assertThat(loaded.getBillingPeriod()).isEqualTo(period);
    assertThat(loaded.getDueOn()).isEqualTo(LocalDate.of(2026, 9, 5));
    assertThat(loaded.getAmount()).isEqualByComparingTo("1500000");
    assertThat(loaded.getAmount().scale()).isZero();
    assertThat(loaded.getStatus()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(loaded.getProofFileKey()).isNull();
    assertThat(loaded.getProofSubmittedAt()).isNull();
    assertThat(loaded.getIssuedAt()).isNotNull();

    Invoice second =
        invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            activeContract, period, new BigDecimal("2000000"));
    entityManager.flush();

    assertThat(second.getId()).isEqualTo(loaded.getId());
    assertThat(second.getAmount()).isEqualByComparingTo("1500000");
    assertThat(invoiceRepository.findByContractId(activeContract.getId())).hasSize(1);
  }

  @Test
  @DisplayName(
      "hasUnsettledInvoices detects DEBT and PENDING_CONFIRMATION across LIQUIDATED and ACTIVE contracts")
  void hasUnsettledInvoices_checksLiquidatedAndActiveContracts() {
    assertThat(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).isFalse();

    // Unrelated tenant's DEBT must not affect target tenant
    invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
        otherActiveContract, LocalDate.of(2026, 9, 1), new BigDecimal("1500000"));
    entityManager.flush();
    assertThat(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).isFalse();

    // 1. DEBT on historical LIQUIDATED contract -> true
    Invoice historicalInvoice =
        invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            liquidatedContract, LocalDate.of(2026, 6, 1), new BigDecimal("1400000"));
    entityManager.flush();
    assertThat(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).isTrue();

    // 2. PENDING_CONFIRMATION on historical LIQUIDATED contract -> true
    historicalInvoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    historicalInvoice.setProofFileKey("invoices/proof-hist-1.jpg");
    historicalInvoice.setProofSubmittedAt(Instant.parse("2026-06-03T08:00:00Z"));
    invoiceRepository.saveAndFlush(historicalInvoice);
    assertThat(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).isTrue();

    // 3. Historical settled to PAID -> false
    historicalInvoice.setStatus(InvoiceStatus.PAID);
    invoiceRepository.saveAndFlush(historicalInvoice);
    assertThat(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).isFalse();

    // 4. DEBT on ACTIVE contract -> true
    Invoice activeInvoice =
        invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            activeContract, LocalDate.of(2026, 9, 1), new BigDecimal("1500000"));
    entityManager.flush();
    assertThat(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).isTrue();

    // 5. PENDING_CONFIRMATION on ACTIVE contract -> true
    activeInvoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    activeInvoice.setProofFileKey("invoices/proof-active-1.jpg");
    activeInvoice.setProofSubmittedAt(Instant.parse("2026-09-03T08:00:00Z"));
    invoiceRepository.saveAndFlush(activeInvoice);
    assertThat(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).isTrue();

    // 6. All PAID -> false
    activeInvoice.setStatus(InvoiceStatus.PAID);
    invoiceRepository.saveAndFlush(activeInvoice);
    assertThat(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).isFalse();
  }

  @Test
  @DisplayName(
      "issueRenewalCatchUpInvoiceIfEligible enforces Asia/Ho_Chi_Minh day 25 and persists renewal monthlyRent")
  void issueRenewalCatchUpInvoiceIfEligible_persistsCatchUpWithRenewalRent() {
    Contract attachedActive = contractRepository.findById(activeContract.getId()).orElseThrow();
    ContractRenewal renewal =
        contractRenewalRepository.saveAndFlush(
            ContractRenewal.builder()
                .contract(attachedActive)
                .sequenceNo(1)
                .signedOn(LocalDate.of(2027, 2, 25))
                .previousEndOn(LocalDate.of(2027, 2, 28))
                .effectiveFrom(LocalDate.of(2027, 3, 1))
                .newEndOn(LocalDate.of(2027, 8, 31))
                .monthlyRent(new BigDecimal("1750000"))
                .build());

    // Day 24 in Asia/Ho_Chi_Minh (2027-02-24T23:59:59+07:00) -> empty
    testClock.setClock(Clock.fixed(Instant.parse("2027-02-24T16:59:59Z"), ZoneOffset.UTC));
    assertThat(invoiceIssuanceService.issueRenewalCatchUpInvoiceIfEligible(attachedActive, renewal))
        .isEmpty();

    // Day 25 in Asia/Ho_Chi_Minh (2027-02-25T00:30:00+07:00) -> issues March catch-up invoice
    testClock.setClock(Clock.fixed(Instant.parse("2027-02-24T17:30:00Z"), ZoneOffset.UTC));
    var issued =
        invoiceIssuanceService.issueRenewalCatchUpInvoiceIfEligible(attachedActive, renewal);
    assertThat(issued).isPresent();
    entityManager.flush();
    entityManager.clear();

    Invoice persisted = invoiceRepository.findById(issued.get().getId()).orElseThrow();
    assertThat(persisted.getInvoiceCode()).isEqualTo("INV-202703-HD-2026-9102");
    assertThat(persisted.getBillingPeriod()).isEqualTo(LocalDate.of(2027, 3, 1));
    assertThat(persisted.getDueOn()).isEqualTo(LocalDate.of(2027, 3, 5));
    assertThat(persisted.getAmount()).isEqualByComparingTo("1750000");
    assertThat(persisted.getAmount().scale()).isZero();
    assertThat(persisted.getStatus()).isEqualTo(InvoiceStatus.DEBT);

    // Repeat call remains idempotent
    var repeated =
        invoiceIssuanceService.issueRenewalCatchUpInvoiceIfEligible(attachedActive, renewal);
    assertThat(repeated).isPresent();
    assertThat(repeated.get().getId()).isEqualTo(persisted.getId());
    assertThat(invoiceRepository.findByContractId(attachedActive.getId())).hasSize(1);
  }
}
