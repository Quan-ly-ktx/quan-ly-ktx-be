package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.DataIntegrityViolationClassifier;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.math.BigDecimal;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(OnboardingPostgresTest.TestClockConfig.class)
class OnboardingPostgresTest {

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
  @Autowired private OnboardingService onboardingService;
  @Autowired private RoomService roomService;
  @MockitoSpyBean private InvoiceIssuanceService invoiceIssuanceService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private RoomCodeRegistryRepository roomCodeRegistryRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID roomId;
  private Bed bed1;
  private Bed bed2;

  @BeforeEach
  void setUp() {
    testClock.reset();
    Mockito.reset(invoiceIssuanceService);

    var roomResp =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.801",
                8,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    roomId = roomResp.id();
    List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(roomId);
    bed1 = beds.get(0);
    bed2 = beds.get(1);
  }

  @AfterEach
  void tearDown() {
    Mockito.reset(invoiceIssuanceService);
    testClock.reset();
    jdbcTemplate.execute(
        "TRUNCATE TABLE payment_transactions, invoices, contract_documents, contract_renewals, "
            + "contracts, room_code_registry, beds, room_amenities, rooms, tenants, users CASCADE");
  }

  @Test
  @DisplayName(
      "Atomic new-tenant onboarding persists User, Tenant, Contract, OCCUPIED Bed, protected room code, and DEBT first-month Invoice")
  void atomicNewTenantOnboarding_persistsAllEntitiesAndProtectsRoomCode() {
    OnboardingRequest request =
        OnboardingRequest.builder()
            .fullName("  Nguyen Van Postgres ")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203880001")
            .phone("0901880001")
            .email(" PG.NewTenant@Example.com ")
            .permanentAddress("123 Dien Bien Phu, TP.HCM")
            .bedId(bed1.getId())
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(new BigDecimal("1500000.00"))
            .build();

    OnboardingResponse response = onboardingService.onboardTenant(request);

    assertThat(response.account().username()).isEqualTo("079203880001");
    assertThat(response.account().email()).isEqualTo("pg.newtenant@example.com");
    assertThat(response.account().initialPassword()).isNotBlank().isNotEqualTo("Password123@");

    User persistedUser = userRepository.findById(response.tenant().userId()).orElseThrow();
    assertThat(persistedUser.getEmail()).isEqualTo("pg.newtenant@example.com");
    assertThat(persistedUser.getRole()).isEqualTo(UserRole.TENANT);
    assertThat(persistedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(
            passwordEncoder.matches(
                response.account().initialPassword(), persistedUser.getPasswordHash()))
        .isTrue();

    Tenant persistedTenant = tenantRepository.findById(response.tenant().id()).orElseThrow();
    assertThat(persistedTenant.getFullName()).isEqualTo("Nguyen Van Postgres");
    assertThat(persistedTenant.getResidentStatus()).isEqualTo(ResidentStatus.ACTIVE);

    Contract persistedContract =
        contractRepository.findById(response.contract().id()).orElseThrow();
    assertThat(persistedContract.getRoomCodeSnapshot()).isEqualTo("P.801");
    assertThat(persistedContract.getBedCodeSnapshot()).isEqualTo(bed1.getBedCode());
    assertThat(persistedContract.getOriginalMonthlyRent()).isEqualByComparingTo("1500000");
    assertThat(persistedContract.getOriginalMonthlyRent().scale()).isZero();
    assertThat(persistedContract.getDepositAmount()).isEqualByComparingTo("1500000");
    assertThat(persistedContract.getDepositAmount().scale()).isZero();
    assertThat(persistedContract.getStatus()).isEqualTo(ContractStatus.ACTIVE);

    Bed persistedBed = bedRepository.findById(bed1.getId()).orElseThrow();
    assertThat(persistedBed.getStatus()).isEqualTo(BedStatus.OCCUPIED);

    assertThat(roomCodeRegistryRepository.existsByRoom_IdAndUsedInBusinessTrue(roomId)).isTrue();

    Invoice persistedInvoice =
        invoiceRepository.findById(response.firstInvoice().id()).orElseThrow();
    assertThat(persistedInvoice.getInvoiceCode())
        .isEqualTo("INV-202609-" + persistedContract.getContractCode());
    assertThat(persistedInvoice.getBillingPeriod()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(persistedInvoice.getDueOn()).isEqualTo(LocalDate.of(2026, 9, 5));
    assertThat(persistedInvoice.getAmount()).isEqualByComparingTo("1500000");
    assertThat(persistedInvoice.getStatus()).isEqualTo(InvoiceStatus.DEBT);
  }

  @Test
  @DisplayName(
      "Rollback atomicity: if first-month invoice issuance throws, no User, Tenant, Contract, OCCUPIED bed, or used_in_business persists")
  void rollbackAtomicity_whenInvoiceIssuanceFails_rollsBackAllWrites() {
    doThrow(new RuntimeException("Simulated invoice issuance failure"))
        .when(invoiceIssuanceService)
        .issueMonthlyInvoiceIfAbsent(any(), any(), any());

    OnboardingRequest request =
        OnboardingRequest.builder()
            .fullName("Nguyen Van Rollback")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203880002")
            .phone("0901880002")
            .email("rollback@example.com")
            .permanentAddress("456 Nguyen Trai, TP.HCM")
            .bedId(bed1.getId())
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(new BigDecimal("1500000"))
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Simulated invoice issuance failure");

    assertThat(userRepository.findByEmail("rollback@example.com")).isEmpty();
    assertThat(tenantRepository.findByIdCardNumber("079203880002")).isEmpty();
    assertThat(contractRepository.findAll()).isEmpty();
    assertThat(invoiceRepository.findAll()).isEmpty();
    assertThat(bedRepository.findById(bed1.getId()).orElseThrow().getStatus())
        .isEqualTo(BedStatus.AVAILABLE);
    assertThat(roomCodeRegistryRepository.existsByRoom_IdAndUsedInBusinessTrue(roomId)).isFalse();
  }

  @Test
  @DisplayName(
      "Returning checked-out tenant blocked on DEBT and PENDING_CONFIRMATION, succeeds once PAID with password preserved and initialPassword=null")
  void returningTenant_debtAndPendingConfirmationBlockThenPaidSucceeds() {
    String originalHash = "$2a$12$existingPrehashedPasswordValueForTestOnly1234567890";
    User priorUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("returning.old@example.com")
                .passwordHash(originalHash)
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant priorTenant =
        tenantRepository.saveAndFlush(
            Tenant.builder()
                .user(priorUser)
                .tenantCode("NT-0088")
                .fullName("Tran Van Returning")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203880003")
                .phone("0901880003")
                .permanentAddress("Old Address")
                .residentStatus(ResidentStatus.CHECKED_OUT)
                .build());
    Contract priorContract =
        contractRepository.saveAndFlush(
            Contract.builder()
                .contractCode("HD-2026-0088")
                .tenant(priorTenant)
                .bed(bed2)
                .roomCodeSnapshot("P.801")
                .bedCodeSnapshot(bed2.getBedCode())
                .startOn(LocalDate.of(2026, 1, 1))
                .originalEndOn(LocalDate.of(2026, 6, 30))
                .originalMonthlyRent(new BigDecimal("1400000"))
                .depositAmount(new BigDecimal("1400000"))
                .status(ContractStatus.LIQUIDATED)
                .checkedOutAt(Instant.parse("2026-06-30T10:00:00Z"))
                .build());
    Invoice priorInvoice =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202606-HD-2026-0088")
                .contract(priorContract)
                .billingPeriod(LocalDate.of(2026, 6, 1))
                .amount(new BigDecimal("1400000"))
                .dueOn(LocalDate.of(2026, 6, 5))
                .status(InvoiceStatus.DEBT)
                .build());

    OnboardingRequest returnRequest =
        OnboardingRequest.builder()
            .fullName("Tran Van Returning Updated")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203880003")
            .phone("0909880003")
            .email("returning.new@example.com")
            .permanentAddress("New Address, Thu Duc")
            .bedId(bed1.getId())
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(new BigDecimal("1500000"))
            .build();

    // 1. Blocked while prior invoice is DEBT
    assertThatThrownBy(() -> onboardingService.onboardTenant(returnRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES);

    // 2. Still blocked while prior invoice is PENDING_CONFIRMATION
    priorInvoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    priorInvoice.setProofFileKey("invoices/proof-return-1.jpg");
    priorInvoice.setProofSubmittedAt(Instant.parse("2026-07-01T08:00:00Z"));
    invoiceRepository.saveAndFlush(priorInvoice);

    assertThatThrownBy(() -> onboardingService.onboardTenant(returnRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES);

    // 3. Succeeds once settled to PAID
    priorInvoice.setStatus(InvoiceStatus.PAID);
    invoiceRepository.saveAndFlush(priorInvoice);

    OnboardingResponse response = onboardingService.onboardTenant(returnRequest);

    assertThat(response.tenant().id()).isEqualTo(priorTenant.getId());
    assertThat(response.tenant().userId()).isEqualTo(priorUser.getId());
    assertThat(response.tenant().tenantCode()).isEqualTo("NT-0088");
    assertThat(response.account().username()).isEqualTo("079203880003");
    assertThat(response.account().email()).isEqualTo("returning.new@example.com");
    assertThat(response.account().initialPassword()).isNull();
    assertThat(response.firstInvoice()).isNotNull();
    assertThat(response.firstInvoice().status()).isEqualTo(InvoiceStatus.DEBT);

    User reloadedUser = userRepository.findById(priorUser.getId()).orElseThrow();
    assertThat(reloadedUser.getEmail()).isEqualTo("returning.new@example.com");
    assertThat(reloadedUser.getPasswordHash()).isEqualTo(originalHash);

    assertThat(bedRepository.findById(bed1.getId()).orElseThrow().getStatus())
        .isEqualTo(BedStatus.OCCUPIED);
  }

  @Test
  @DisplayName(
      "Onboarding into an archived room is rejected with 409 ROOM_ARCHIVED on real PostgreSQL")
  void archivedRoom_shouldRejectWithRoomArchived() {
    roomService.deleteRoom(roomId);

    OnboardingRequest request =
        OnboardingRequest.builder()
            .fullName("Nguyen Van Archived")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203880004")
            .phone("0901880004")
            .email("archived@example.com")
            .permanentAddress("789 Le Van Viet")
            .bedId(bed1.getId())
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(new BigDecimal("1500000"))
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ROOM_ARCHIVED);

    assertThat(userRepository.findByEmail("archived@example.com")).isEmpty();
    assertThat(bedRepository.findById(bed1.getId()).orElseThrow().getStatus())
        .isEqualTo(BedStatus.AVAILABLE);
  }

  @Test
  @DisplayName(
      "Real PostgreSQL partial unique indexes uq_contracts_active_bed and uq_contracts_active_tenant classify to domain 409 codes")
  void activeContractPartialUniqueIndexes_classifyToDomain409() {
    OnboardingResponse onboarded =
        onboardingService.onboardTenant(
            OnboardingRequest.builder()
                .fullName("Tenant One")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203880005")
                .phone("0901880005")
                .email("tenant1.race@example.com")
                .permanentAddress("Addr 1")
                .bedId(bed1.getId())
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2027, 2, 28))
                .depositAmount(new BigDecimal("1500000"))
                .build());

    User user2 =
        userRepository.saveAndFlush(
            User.builder()
                .email("tenant2.race@example.com")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    Tenant tenant2 =
        tenantRepository.saveAndFlush(
            Tenant.builder()
                .user(user2)
                .tenantCode("NT-RACE-02")
                .fullName("Tenant Two")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203880006")
                .phone("0901880006")
                .permanentAddress("Addr 2")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    DataIntegrityViolationException bedRace =
        catchThrowableOfType(
            DataIntegrityViolationException.class,
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
                        + "VALUES ('HD-RACE-BED', ?, ?, 'P.801', ?, '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
                    tenant2.getId(),
                    bed1.getId(),
                    bed1.getBedCode()));
    assertThat(bedRace).isNotNull();
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(bedRace))
        .contains(ContractErrorCode.BED_ALREADY_RENTED);

    DataIntegrityViolationException tenantRace =
        catchThrowableOfType(
            DataIntegrityViolationException.class,
            () ->
                jdbcTemplate.update(
                    "INSERT INTO contracts(contract_code, tenant_id, bed_id, room_code_snapshot, bed_code_snapshot, start_on, original_end_on, original_monthly_rent, deposit_amount, status) "
                        + "VALUES ('HD-RACE-TEN', ?, ?, 'P.801', ?, '2026-09-01', '2027-02-28', 1500000, 1500000, 'ACTIVE')",
                    onboarded.tenant().id(),
                    bed2.getId(),
                    bed2.getBedCode()));
    assertThat(tenantRace).isNotNull();
    assertThat(DataIntegrityViolationClassifier.classifyGlobal(tenantRace))
        .contains(ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT);
  }
}
