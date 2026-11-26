package com.uit.dormmanagement.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.contract.CheckoutContractRequest;
import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.RoomCodeRegistry;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.ContractService;
import com.uit.dormmanagement.services.OnboardingService;
import com.uit.dormmanagement.services.VehicleService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(ResidentLifecycleE2ETest.TestClockConfig.class)
@Transactional
class ResidentLifecycleE2ETest {

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
  @Autowired private OnboardingService onboardingService;
  @Autowired private VehicleService vehicleService;
  @Autowired private ContractService contractService;

  @Autowired private RoomRepository roomRepository;
  @Autowired private RoomCodeRegistryRepository roomCodeRegistryRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private UserRepository userRepository;

  private Room testRoom;
  private Bed testBed;

  @BeforeEach
  void setUp() {
    testClock.reset();
    testRoom =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.302")
                .floor(3)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds((short) 4)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    roomCodeRegistryRepository.save(
        RoomCodeRegistry.builder().code("P.302").room(testRoom).current(true).build());

    java.util.List<Bed> beds = new java.util.ArrayList<>();
    for (int bedNumber = 1; bedNumber <= 4; bedNumber++) {
      String bedCode = String.format("G%02d", bedNumber);
      beds.add(
          Bed.builder()
              .room(testRoom)
              .bedCode(bedCode)
              .slotKey("P.302-" + bedCode)
              .status(BedStatus.AVAILABLE)
              .build());
    }
    testBed = bedRepository.saveAll(beds).getFirst();
  }

  @AfterEach
  void resetClock() {
    testClock.reset();
  }

  @Test
  @DisplayName(
      "E2E Full Resident Lifecycle: Onboarding -> Register Vehicle -> Issue Card -> Quota Block -> Renew -> Checkout -> Re-assign Card")
  void testCompleteResidentLifecycle() {
    // -------------------------------------------------------------------------
    // Step 1: Direct Onboarding 1-step (FR-09, day 01 Asia/Ho_Chi_Minh)
    // -------------------------------------------------------------------------
    testClock.setClock(Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));

    OnboardingRequest onboardReq =
        OnboardingRequest.builder()
            .fullName("Nguyễn Văn An")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203999991")
            .phone("0981112233")
            .email("an.nguyen.e2e@example.com")
            .permanentAddress("123 Cách Mạng Tháng 8, TP.HCM")
            .bedId(testBed.getId())
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 8, 31))
            .depositAmount(new BigDecimal("1500000.00"))
            .build();

    OnboardingResponse onboardRes = onboardingService.onboardTenant(onboardReq);

    assertThat(onboardRes).isNotNull();
    assertThat(onboardRes.account().username()).isEqualTo("079203999991");
    assertThat(onboardRes.account().initialPassword()).isNotBlank().isNotEqualTo("Password123@");
    assertThat(onboardRes.firstInvoice()).isNotNull();
    assertThat(onboardRes.firstInvoice().billingPeriod()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(onboardRes.firstInvoice().dueOn()).isEqualTo(LocalDate.of(2026, 9, 5));
    assertThat(onboardRes.firstInvoice().status()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(onboardRes.firstInvoice().amount()).isEqualByComparingTo("1500000");

    UUID tenantId = onboardRes.tenant().id();
    UUID contractId = onboardRes.contract().id();
    UUID userId = onboardRes.tenant().userId();

    // Verify database state after onboarding
    Bed occupiedBed = bedRepository.findById(testBed.getId()).orElseThrow();
    assertThat(occupiedBed.getStatus()).isEqualTo(BedStatus.OCCUPIED);

    Room updatedRoom = roomRepository.findById(testRoom.getId()).orElseThrow();
    assertThat(bedRepository.countByRoomIdAndStatus(testRoom.getId(), BedStatus.OCCUPIED))
        .isEqualTo(1);
    assertThat(roomCodeRegistryRepository.existsByRoom_IdAndUsedInBusinessTrue(testRoom.getId()))
        .isTrue();

    Invoice firstInvoice = invoiceRepository.findById(onboardRes.firstInvoice().id()).orElseThrow();
    assertThat(firstInvoice.getStatus()).isEqualTo(InvoiceStatus.DEBT);

    User createdUser = userRepository.findById(userId).orElseThrow();
    assertThat(createdUser.getRole()).isEqualTo(UserRole.TENANT);
    assertThat(createdUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

    Tenant createdTenant = tenantRepository.findById(tenantId).orElseThrow();
    assertThat(createdTenant.getResidentStatus()).isEqualTo(ResidentStatus.ACTIVE);

    Contract createdContract = contractRepository.findById(contractId).orElseThrow();
    assertThat(createdContract.getStatus()).isEqualTo(ContractStatus.ACTIVE);

    // -------------------------------------------------------------------------
    // Step 2: Tenant Registers Vehicle with 2 Cà Vẹt Photos (FR-08)
    // -------------------------------------------------------------------------
    TenantRegisterVehicleRequest registerVehicleReq =
        TenantRegisterVehicleRequest.builder()
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X1 123.45")
            .brand("Honda")
            .color("Trắng Đen")
            .build();

    byte[] validJpegBytes =
        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    MockMultipartFile frontImage =
        new MockMultipartFile("frontImage", "cavet_front.jpg", "image/jpeg", validJpegBytes);
    MockMultipartFile backImage =
        new MockMultipartFile("backImage", "cavet_back.jpg", "image/jpeg", validJpegBytes);

    VehicleResponse vehicleRes =
        vehicleService.registerVehicle(userId, registerVehicleReq, frontImage, backImage);

    assertThat(vehicleRes).isNotNull();
    assertThat(vehicleRes.status()).isEqualTo(VehicleStatus.PENDING_CARD);
    assertThat(vehicleRes.registrationFrontAvailable()).isTrue();
    assertThat(vehicleRes.registrationBackAvailable()).isTrue();

    UUID vehicleId = vehicleRes.id();

    // -------------------------------------------------------------------------
    // Step 3: Admin Issues Physical RFID Card (FR-08 / BR-10)
    // -------------------------------------------------------------------------
    IssueCardRequest issueCardReq = IssueCardRequest.builder().cardCode("THE-E2E-001").build();
    VehicleResponse issuedRes = vehicleService.issueCard(vehicleId, issueCardReq);

    assertThat(issuedRes.status()).isEqualTo(VehicleStatus.ISSUED);
    assertThat(issuedRes.cardCode()).isEqualTo("THE-E2E-001");

    // -------------------------------------------------------------------------
    // Step 4: Strict Vehicle Quota Enforcement (1 vehicle / tenant)
    // -------------------------------------------------------------------------
    TenantRegisterVehicleRequest secondVehicleReq =
        TenantRegisterVehicleRequest.builder()
            .vehicleType(VehicleType.ELECTRIC_MOTORBIKE)
            .licensePlate("59-X2 999.88")
            .brand("VinFast")
            .color("Xanh")
            .build();

    assertThatThrownBy(
            () -> vehicleService.registerVehicle(userId, secondVehicleReq, frontImage, backImage))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode() == VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED);

    // -------------------------------------------------------------------------
    // Step 5: Multi-file Contract Evidence Upload & Contract Renewal (FR-10, FR-12)
    // -------------------------------------------------------------------------
    MockMultipartFile contractDoc =
        new MockMultipartFile(
            "files", "hop_dong_goc.pdf", "application/pdf", new byte[] {10, 20, 30});
    List<ContractDocumentResponse> uploadedDocs =
        contractService.uploadDocuments(
            contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(contractDoc));
    assertThat(uploadedDocs).hasSize(1);
    assertThat(uploadedDocs.getFirst().fileUrl())
        .isEqualTo(
            "/api/v1/admin/contracts/"
                + contractId
                + "/documents/"
                + uploadedDocs.getFirst().id()
                + "/content")
        .doesNotContain("/uploads/");

    RenewContractRequest renewReq =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 8, 31)).build();
    MockMultipartFile annexDoc =
        new MockMultipartFile(
            "annexDocuments", "phu_luc_gia_han.pdf", "application/pdf", new byte[] {40, 50});

    // Blocked while first invoice is still DEBT (BR-04 §6, FR-12)
    assertThatThrownBy(() -> contractService.renewContract(contractId, renewReq, List.of(annexDoc)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES);

    firstInvoice.setStatus(InvoiceStatus.PAID);
    invoiceRepository.saveAndFlush(firstInvoice);

    testRoom.setBasePrice(new BigDecimal("1800000.00"));
    roomRepository.saveAndFlush(testRoom);
    testClock.setClock(Clock.fixed(Instant.parse("2027-08-15T02:00:00Z"), TimeConfig.DORM_ZONE));

    ContractDetailResponse renewedContract =
        contractService.renewContract(contractId, renewReq, List.of(annexDoc));
    assertThat(renewedContract.endDate()).isEqualTo(LocalDate.of(2028, 8, 31));
    assertThat(renewedContract.monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(renewedContract.status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(renewedContract.renewals()).hasSize(1);
    assertThat(renewedContract.renewals().getFirst().sequenceNo()).isEqualTo(1);
    assertThat(renewedContract.renewals().getFirst().previousEndOn())
        .isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(renewedContract.renewals().getFirst().effectiveFrom())
        .isEqualTo(LocalDate.of(2027, 9, 1));
    assertThat(renewedContract.renewals().getFirst().newEndOn())
        .isEqualTo(LocalDate.of(2028, 8, 31));
    assertThat(renewedContract.renewals().getFirst().monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(renewedContract.documents()).hasSize(2);

    Contract afterRenewDbContract = contractRepository.findById(contractId).orElseThrow();
    assertThat(afterRenewDbContract.getOriginalMonthlyRent()).isEqualByComparingTo("1500000");
    assertThat(afterRenewDbContract.getOriginalEndOn()).isEqualTo(LocalDate.of(2027, 8, 31));

    // -------------------------------------------------------------------------
    // Step 6: Checkout 4-in-1 Transaction (FR-07, FR-13, BR-04)
    // -------------------------------------------------------------------------
    ContractDetailResponse terminatedContract =
        contractService.terminateContract(
            contractId,
            CheckoutContractRequest.builder()
                .bedStatusAfterCheckout(BedStatus.AVAILABLE)
                .handoverConfirmed(true)
                .build());
    assertThat(terminatedContract.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(terminatedContract.checkedOutAt()).isNotNull();

    // 1. Contract is LIQUIDATED with checkedOutAt populated
    Contract dbContract = contractRepository.findById(contractId).orElseThrow();
    assertThat(dbContract.getStatus()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(dbContract.getCheckedOutAt()).isNotNull();

    // 2. Bed is AVAILABLE & Room occupied beds is 0
    Bed dbBed = bedRepository.findById(testBed.getId()).orElseThrow();
    assertThat(dbBed.getStatus()).isEqualTo(BedStatus.AVAILABLE);

    Room dbRoom = roomRepository.findById(testRoom.getId()).orElseThrow();
    assertThat(bedRepository.countByRoomIdAndStatus(testRoom.getId(), BedStatus.OCCUPIED)).isZero();

    // 3. Tenant is CHECKED_OUT with deletedAt NULL
    Tenant dbTenant = tenantRepository.findById(tenantId).orElseThrow();
    assertThat(dbTenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);

    // 4. Vehicle card is REVOKED
    Vehicle dbVehicle = vehicleRepository.findById(vehicleId).orElseThrow();
    assertThat(dbVehicle.getStatus()).isEqualTo(VehicleStatus.REVOKED);
    assertThat(dbVehicle.getRevokedAt()).isNotNull();

    // -------------------------------------------------------------------------
    // Step 7: Physical Card & Bed Reissuing to New Tenant (BR-10 & Day-01 FR-09)
    // -------------------------------------------------------------------------
    testClock.setClock(Clock.fixed(Instant.parse("2027-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));

    OnboardingRequest newTenantReq =
        OnboardingRequest.builder()
            .fullName("Trần Thị Bình")
            .gender(GenderPolicy.MALE) // Same male room
            .idCardNumber("079203999992")
            .phone("0982223344")
            .email("binh.tran.e2e@example.com")
            .permanentAddress("456 Hai Bà Trưng, TP.HCM")
            .bedId(testBed.getId())
            .startDate(LocalDate.of(2027, 9, 1))
            .endDate(LocalDate.of(2028, 8, 31))
            .depositAmount(new BigDecimal("1500000.00"))
            .build();

    OnboardingResponse newOnboardRes = onboardingService.onboardTenant(newTenantReq);
    assertThat(newOnboardRes).isNotNull();
    assertThat(newOnboardRes.firstInvoice()).isNotNull();
    assertThat(newOnboardRes.firstInvoice().billingPeriod()).isEqualTo(LocalDate.of(2027, 9, 1));

    // Re-assign the exact same physical RFID card "THE-E2E-001" to new tenant's vehicle
    CreateVehicleAdminRequest newVehicleReq =
        CreateVehicleAdminRequest.builder()
            .tenantId(newOnboardRes.tenant().id())
            .vehicleType(VehicleType.MANUAL_MOTORCYCLE)
            .licensePlate("59-X9 888.77")
            .brand("Yamaha")
            .color("Xanh GP")
            .build();

    VehicleResponse newVehicleRes =
        vehicleService.createVehicleAdmin(newVehicleReq, frontImage, backImage);
    VehicleResponse reusedIssuedRes =
        vehicleService.issueCard(
            newVehicleRes.id(),
            new com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest("THE-E2E-001"));
    assertThat(reusedIssuedRes.cardCode()).isEqualTo("THE-E2E-001");
    assertThat(reusedIssuedRes.status()).isEqualTo(VehicleStatus.ISSUED);
  }
}
