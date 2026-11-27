package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.contract.CheckoutContractRequest;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.VehicleChangeRequestRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
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
@Import(ContractCheckoutPostgresTest.TestClockConfig.class)
class ContractCheckoutPostgresTest {

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
  @Autowired private VehicleService vehicleService;
  @Autowired private BedRepository bedRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private StaffProfileRepository staffProfileRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private VehicleChangeRequestRepository vehicleChangeRequestRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private User managerUser;
  private StaffProfile managerStaff;
  private Bed bed1;
  private Bed bed2;

  @BeforeEach
  void setUp() {
    testClock.reset();
    managerUser =
        userRepository.saveAndFlush(
            User.builder()
                .email("checkout.manager@example.com")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
    managerStaff =
        staffProfileRepository.saveAndFlush(
            StaffProfile.builder()
                .user(managerUser)
                .fullName("Manager Checkout")
                .phone("0901999888")
                .build());

    var roomResp =
        roomService.createRoom(
            new CreateRoomRequest(
                "P.902",
                9,
                RoomType.FOUR_BEDS,
                GenderPolicy.MALE,
                new BigDecimal("1500000"),
                List.of()));
    List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(roomResp.id());
    bed1 = beds.get(0);
    bed2 = beds.get(1);
  }

  @AfterEach
  void tearDown() {
    testClock.reset();
    jdbcTemplate.execute(
        "TRUNCATE TABLE vehicle_change_requests, vehicles, payment_transactions, invoices, "
            + "contract_documents, contract_renewals, contracts, room_code_registry, beds, "
            + "room_amenities, rooms, staff_profiles, tenants, users CASCADE");
  }

  private OnboardingResponse onboardTenant(Bed bed, String cccd, String email, String phone) {
    return onboardingService.onboardTenant(
        OnboardingRequest.builder()
            .fullName("Nguyen Van Checkout")
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
  }

  @Test
  @DisplayName(
      "Case 1: Checkout to MAINTENANCE revokes ISSUED vehicle, cancels PENDING change request, preserves REPLACED history & DEBT/PENDING_CONFIRMATION invoices")
  void
      checkout_withMaintenance_revokesIssuedVehicleCancelsChangeRequestPreservesHistoryAndInvoices() {
    testClock.setClock(Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));
    OnboardingResponse onboarded =
        onboardTenant(bed1, "079203910001", "checkout.case1@example.com", "0901910001");
    UUID contractId = onboarded.contract().id();
    UUID tenantId = onboarded.tenant().id();
    UUID userId = onboarded.tenant().userId();

    Contract contractEntity = contractRepository.findById(contractId).orElseThrow();
    Tenant tenantEntity = tenantRepository.findById(tenantId).orElseThrow();

    // Create a second invoice in PENDING_CONFIRMATION (first invoice from onboarding is DEBT)
    Instant proofSubmittedAt = Instant.parse("2026-10-02T05:00:00Z");
    Invoice pendingInvoice =
        invoiceRepository.saveAndFlush(
            Invoice.builder()
                .invoiceCode("INV-202610-" + contractEntity.getContractCode())
                .contract(contractEntity)
                .billingPeriod(LocalDate.of(2026, 10, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 10, 5))
                .status(InvoiceStatus.PENDING_CONFIRMATION)
                .proofFileKey("invoices/proof-checkout-case1.jpg")
                .proofSubmittedAt(proofSubmittedAt)
                .build());

    // Historical REPLACED vehicle + current ISSUED vehicle + PENDING change request
    Instant historicalReplacedAt = Instant.parse("2026-09-10T04:00:00Z");
    Vehicle historicalReplaced =
        vehicleRepository.saveAndFlush(
            Vehicle.builder()
                .tenant(tenantEntity)
                .vehicleType(VehicleType.SCOOTER)
                .licensePlate("59-X1 111.11")
                .brand("Honda")
                .color("Đỏ")
                .registrationFrontFileKey("vehicles/hist-front.jpg")
                .registrationBackFileKey("vehicles/hist-back.jpg")
                .cardCode("THE-CHK-001")
                .status(VehicleStatus.REPLACED)
                .issuedAt(Instant.parse("2026-09-02T02:00:00Z"))
                .replacedAt(historicalReplacedAt)
                .build());

    Vehicle issuedVehicle =
        vehicleRepository.saveAndFlush(
            Vehicle.builder()
                .tenant(tenantEntity)
                .vehicleType(VehicleType.MANUAL_MOTORCYCLE)
                .licensePlate("59-X2 222.22")
                .brand("Yamaha")
                .color("Đen")
                .registrationFrontFileKey("vehicles/cur-front.jpg")
                .registrationBackFileKey("vehicles/cur-back.jpg")
                .cardCode("THE-CHK-001")
                .status(VehicleStatus.ISSUED)
                .issuedAt(historicalReplacedAt)
                .replacedVehicle(historicalReplaced)
                .build());
    historicalReplaced.setReplacedByVehicle(issuedVehicle);
    vehicleRepository.saveAndFlush(historicalReplaced);

    VehicleChangeRequest pendingChangeRequest =
        vehicleChangeRequestRepository.saveAndFlush(
            VehicleChangeRequest.builder()
                .tenant(tenantEntity)
                .currentVehicle(issuedVehicle)
                .proposedVehicleType(VehicleType.ELECTRIC_MOTORBIKE)
                .proposedLicensePlate("59-X3 333.33")
                .proposedBrand("VinFast")
                .proposedColor("Trắng")
                .proposedRegistrationFrontFileKey("vehicles/prop-front.jpg")
                .proposedRegistrationBackFileKey("vehicles/prop-back.jpg")
                .status(VehicleChangeRequestStatus.PENDING)
                .build());

    Instant checkoutInstant = Instant.parse("2026-10-15T08:30:00Z");
    testClock.setClock(Clock.fixed(checkoutInstant, TimeConfig.DORM_ZONE));

    ContractDetailResponse response =
        contractService.terminateContract(
            managerUser.getId(),
            contractId,
            CheckoutContractRequest.builder()
                .bedStatusAfterCheckout(BedStatus.MAINTENANCE)
                .handoverConfirmed(true)
                .build());

    assertThat(response.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(response.checkedOutAt()).isEqualTo(checkoutInstant);

    Contract reloadedContract = contractRepository.findById(contractId).orElseThrow();
    assertThat(reloadedContract.getStatus()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(reloadedContract.getCheckedOutAt()).isEqualTo(checkoutInstant);

    Bed reloadedBed = bedRepository.findById(bed1.getId()).orElseThrow();
    assertThat(reloadedBed.getStatus()).isEqualTo(BedStatus.MAINTENANCE);
    assertThat(contractRepository.existsByBedIdAndStatus(bed1.getId(), ContractStatus.ACTIVE))
        .isFalse();
    assertThat(contractRepository.existsByTenantIdAndStatus(tenantId, ContractStatus.ACTIVE))
        .isFalse();

    Tenant reloadedTenant = tenantRepository.findById(tenantId).orElseThrow();
    assertThat(reloadedTenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);
    User reloadedUser = userRepository.findById(userId).orElseThrow();
    assertThat(reloadedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

    Vehicle reloadedIssued = vehicleRepository.findById(issuedVehicle.getId()).orElseThrow();
    assertThat(reloadedIssued.getStatus()).isEqualTo(VehicleStatus.REVOKED);
    assertThat(reloadedIssued.getRevokedAt()).isEqualTo(checkoutInstant);
    assertThat(reloadedIssued.getCardCode()).isEqualTo("THE-CHK-001");
    assertThat(reloadedIssued.getCancelledAt()).isNull();

    Vehicle reloadedHistorical =
        vehicleRepository.findById(historicalReplaced.getId()).orElseThrow();
    assertThat(reloadedHistorical.getStatus()).isEqualTo(VehicleStatus.REPLACED);
    assertThat(reloadedHistorical.getReplacedAt()).isEqualTo(historicalReplacedAt);
    assertThat(reloadedHistorical.getRevokedAt()).isNull();
    assertThat(reloadedHistorical.getCancelledAt()).isNull();

    VehicleChangeRequest reloadedChangeRequest =
        vehicleChangeRequestRepository.findById(pendingChangeRequest.getId()).orElseThrow();
    assertThat(reloadedChangeRequest.getStatus()).isEqualTo(VehicleChangeRequestStatus.CANCELLED);
    assertThat(reloadedChangeRequest.getProcessedAt()).isEqualTo(checkoutInstant);
    assertThat(reloadedChangeRequest.getProcessedByStaff()).isNotNull();
    assertThat(reloadedChangeRequest.getProcessedByStaff().getId()).isEqualTo(managerStaff.getId());
    assertThat(reloadedChangeRequest.getNewVehicle()).isNull();
    assertThat(reloadedChangeRequest.getRejectionReason()).isNull();

    // Unsettled DEBT and PENDING_CONFIRMATION invoices must remain untouched
    Invoice reloadedDebt = invoiceRepository.findById(onboarded.firstInvoice().id()).orElseThrow();
    assertThat(reloadedDebt.getStatus()).isEqualTo(InvoiceStatus.DEBT);
    Invoice reloadedPending = invoiceRepository.findById(pendingInvoice.getId()).orElseThrow();
    assertThat(reloadedPending.getStatus()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);
    assertThat(reloadedPending.getProofFileKey()).isEqualTo("invoices/proof-checkout-case1.jpg");
    assertThat(reloadedPending.getProofSubmittedAt()).isEqualTo(proofSubmittedAt);
  }

  @Test
  @DisplayName(
      "Case 2: Checkout to AVAILABLE cancels PENDING_CARD vehicle (cardCode=null) and releases bed & revoked RFID card for reassignment")
  void checkout_withAvailable_cancelsPendingCardVehicleAndReleasesBedAndCardForReassignment() {
    testClock.setClock(Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));
    OnboardingResponse tenantA =
        onboardTenant(bed1, "079203910002", "checkout.case2a@example.com", "0901910002");
    VehicleResponse tenantAVehicle =
        vehicleService.createVehicleAdmin(
            CreateVehicleAdminRequest.builder()
                .tenantId(tenantA.tenant().id())
                .vehicleType(VehicleType.BICYCLE)
                .brand("Thống Nhất")
                .color("Xanh")
                .build(),
            null,
            null);
    vehicleService.issueCard(
        managerUser.getId(), tenantAVehicle.id(), new IssueCardRequest("THE-CHK-REUSE-01"));

    contractService.terminateContract(
        managerUser.getId(),
        tenantA.contract().id(),
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.MAINTENANCE)
            .handoverConfirmed(true)
            .build());

    // Tenant B on bed2 has a PENDING_CARD vehicle (card_code IS NULL)
    OnboardingResponse tenantB =
        onboardTenant(bed2, "079203910003", "checkout.case2b@example.com", "0901910003");
    VehicleResponse pendingVehicle =
        vehicleService.createVehicleAdmin(
            CreateVehicleAdminRequest.builder()
                .tenantId(tenantB.tenant().id())
                .vehicleType(VehicleType.BICYCLE)
                .brand("Giant")
                .color("Trắng")
                .build(),
            null,
            null);
    assertThat(pendingVehicle.status()).isEqualTo(VehicleStatus.PENDING_CARD);

    Instant checkoutInstant = Instant.parse("2026-10-20T09:00:00Z");
    testClock.setClock(Clock.fixed(checkoutInstant, TimeConfig.DORM_ZONE));

    ContractDetailResponse responseB =
        contractService.terminateContract(
            managerUser.getId(),
            tenantB.contract().id(),
            CheckoutContractRequest.builder()
                .bedStatusAfterCheckout(BedStatus.AVAILABLE)
                .handoverConfirmed(true)
                .build());

    assertThat(responseB.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(responseB.checkedOutAt()).isEqualTo(checkoutInstant);

    Vehicle reloadedPendingVehicle = vehicleRepository.findById(pendingVehicle.id()).orElseThrow();
    assertThat(reloadedPendingVehicle.getStatus()).isEqualTo(VehicleStatus.CANCELLED);
    assertThat(reloadedPendingVehicle.getCancelledAt()).isEqualTo(checkoutInstant);
    assertThat(reloadedPendingVehicle.getCardCode()).isNull();
    assertThat(reloadedPendingVehicle.getRevokedAt()).isNull();

    assertThat(bedRepository.findById(bed2.getId()).orElseThrow().getStatus())
        .isEqualTo(BedStatus.AVAILABLE);
    assertThat(tenantRepository.findById(tenantB.tenant().id()).orElseThrow().getResidentStatus())
        .isEqualTo(ResidentStatus.CHECKED_OUT);

    // Advance to next month day 01 and onboard Tenant C onto freed bed2, then reuse
    // THE-CHK-REUSE-01
    testClock.setClock(Clock.fixed(Instant.parse("2026-11-01T02:00:00Z"), TimeConfig.DORM_ZONE));
    OnboardingResponse tenantC =
        onboardingService.onboardTenant(
            OnboardingRequest.builder()
                .fullName("Le Van New Resident")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203910004")
                .phone("0901910004")
                .email("checkout.case2c@example.com")
                .permanentAddress("456 Le Loi, TP.HCM")
                .bedId(bed2.getId())
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2027, 4, 30))
                .depositAmount(new BigDecimal("1500000"))
                .build());

    assertThat(bedRepository.findById(bed2.getId()).orElseThrow().getStatus())
        .isEqualTo(BedStatus.OCCUPIED);

    VehicleResponse tenantCVehicle =
        vehicleService.createVehicleAdmin(
            CreateVehicleAdminRequest.builder()
                .tenantId(tenantC.tenant().id())
                .vehicleType(VehicleType.BICYCLE)
                .brand("Martin")
                .color("Đen")
                .build(),
            null,
            null);
    VehicleResponse reusedCard =
        vehicleService.issueCard(
            managerUser.getId(), tenantCVehicle.id(), new IssueCardRequest("THE-CHK-REUSE-01"));
    assertThat(reusedCard.status()).isEqualTo(VehicleStatus.ISSUED);
    assertThat(reusedCard.cardCode()).isEqualTo("THE-CHK-REUSE-01");
  }

  @Test
  @DisplayName(
      "Case 3: Repeat checkout on an already liquidated contract throws CONTRACT_ALREADY_TERMINATED and preserves original checkedOutAt & bed status")
  void checkout_alreadyLiquidated_throwsAndPreservesOriginalCheckedOutAt() {
    Instant firstCheckoutInstant = Instant.parse("2026-10-10T04:00:00Z");
    testClock.setClock(Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));
    OnboardingResponse onboarded =
        onboardTenant(bed1, "079203910005", "checkout.case3@example.com", "0901910005");
    UUID contractId = onboarded.contract().id();

    testClock.setClock(Clock.fixed(firstCheckoutInstant, TimeConfig.DORM_ZONE));
    contractService.terminateContract(
        managerUser.getId(),
        contractId,
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.MAINTENANCE)
            .handoverConfirmed(true)
            .build());

    testClock.setClock(Clock.fixed(Instant.parse("2026-10-12T04:00:00Z"), TimeConfig.DORM_ZONE));
    CheckoutContractRequest secondReq =
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.AVAILABLE)
            .handoverConfirmed(true)
            .build();
    assertThatThrownBy(
            () -> contractService.terminateContract(managerUser.getId(), contractId, secondReq))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_ALREADY_TERMINATED);

    Contract reloaded = contractRepository.findById(contractId).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(reloaded.getCheckedOutAt()).isEqualTo(firstCheckoutInstant);
    assertThat(bedRepository.findById(bed1.getId()).orElseThrow().getStatus())
        .isEqualTo(BedStatus.MAINTENANCE);
  }
}
