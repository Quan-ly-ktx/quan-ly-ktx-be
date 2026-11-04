package com.uit.dormmanagement.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.ContractService;
import com.uit.dormmanagement.services.OnboardingService;
import com.uit.dormmanagement.services.VehicleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ResidentLifecycleE2ETest {

  @Autowired private OnboardingService onboardingService;
  @Autowired private VehicleService vehicleService;
  @Autowired private ContractService contractService;

  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private UserRepository userRepository;

  private Room testRoom;
  private Bed testBed;

  @BeforeEach
  void setUp() {
    testRoom =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.E2E-302")
                .floor(3)
                .roomType(RoomType.FOUR_BEDS)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds(4)
                .occupiedBeds(0)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    testBed =
        bedRepository.save(
            Bed.builder()
                .room(testRoom)
                .bedCode("G01")
                .slotKey("P.E2E-302-G01")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.AVAILABLE)
                .build());
  }

  @Test
  @DisplayName(
      "E2E Full Resident Lifecycle: Onboarding -> Register Vehicle -> Issue Card -> Quota Block -> Renew -> Checkout -> Re-assign Card")
  void testCompleteResidentLifecycle() {
    // -------------------------------------------------------------------------
    // Step 1: Direct Onboarding 1-step (FR-09)
    // -------------------------------------------------------------------------
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
            .endDate(LocalDate.of(2027, 9, 1))
            .depositAmount(new BigDecimal("1500000.00"))
            .build();

    OnboardingResponse onboardRes = onboardingService.onboardTenant(onboardReq);

    assertThat(onboardRes).isNotNull();
    UUID tenantId = onboardRes.tenant().id();
    UUID contractId = onboardRes.contract().id();
    UUID userId = onboardRes.tenant().userId();

    // Verify database state after onboarding
    Bed occupiedBed = bedRepository.findById(testBed.getId()).orElseThrow();
    assertThat(occupiedBed.getStatus()).isEqualTo(BedStatus.OCCUPIED);

    Room updatedRoom = roomRepository.findById(testRoom.getId()).orElseThrow();
    assertThat(updatedRoom.getOccupiedBeds()).isEqualTo(1);

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

    MockMultipartFile frontImage =
        new MockMultipartFile("frontImage", "cavet_front.jpg", "image/jpeg", new byte[] {1, 2, 3});
    MockMultipartFile backImage =
        new MockMultipartFile("backImage", "cavet_back.jpg", "image/jpeg", new byte[] {4, 5, 6});

    VehicleResponse vehicleRes =
        vehicleService.registerVehicle(userId, registerVehicleReq, frontImage, backImage);

    assertThat(vehicleRes).isNotNull();
    assertThat(vehicleRes.status()).isEqualTo(VehicleStatus.PENDING);
    assertThat(vehicleRes.registrationFrontUrl()).isNotBlank();
    assertThat(vehicleRes.registrationBackUrl()).isNotBlank();

    UUID vehicleId = vehicleRes.id();

    // -------------------------------------------------------------------------
    // Step 3: Admin Issues Physical RFID Card (FR-08 / BR-10)
    // -------------------------------------------------------------------------
    IssueCardRequest issueCardReq = IssueCardRequest.builder().cardCode("THE-E2E-001").build();
    VehicleResponse issuedRes = vehicleService.issueCard(vehicleId, issueCardReq);

    assertThat(issuedRes.status()).isEqualTo(VehicleStatus.ACTIVE);
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

    RenewContractRequest renewReq =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 9, 1)).build();
    MockMultipartFile annexDoc =
        new MockMultipartFile(
            "annexDocuments", "phu_luc_gia_han.pdf", "application/pdf", new byte[] {40, 50});

    ContractDetailResponse renewedContract =
        contractService.renewContract(contractId, renewReq, List.of(annexDoc));
    assertThat(renewedContract.endDate()).isEqualTo(LocalDate.of(2028, 9, 1));
    assertThat(renewedContract.status()).isEqualTo(ContractStatus.ACTIVE);

    // -------------------------------------------------------------------------
    // Step 6: Checkout 4-in-1 Transaction (FR-07, FR-13, BR-04)
    // -------------------------------------------------------------------------
    ContractDetailResponse terminatedContract = contractService.terminateContract(contractId);
    assertThat(terminatedContract.status()).isEqualTo(ContractStatus.TERMINATED);

    // 1. Contract is TERMINATED
    Contract dbContract = contractRepository.findById(contractId).orElseThrow();
    assertThat(dbContract.getStatus()).isEqualTo(ContractStatus.TERMINATED);

    // 2. Bed is AVAILABLE & Room occupied beds is 0
    Bed dbBed = bedRepository.findById(testBed.getId()).orElseThrow();
    assertThat(dbBed.getStatus()).isEqualTo(BedStatus.AVAILABLE);

    Room dbRoom = roomRepository.findById(testRoom.getId()).orElseThrow();
    assertThat(dbRoom.getOccupiedBeds()).isEqualTo(0);

    // 3. Tenant is CHECKED_OUT with deletedAt NULL
    Tenant dbTenant = tenantRepository.findById(tenantId).orElseThrow();
    assertThat(dbTenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);
    assertThat(dbTenant.getDeletedAt()).isNull();

    // 4. Vehicle card is REVOKED
    Vehicle dbVehicle = vehicleRepository.findById(vehicleId).orElseThrow();
    assertThat(dbVehicle.getStatus()).isEqualTo(VehicleStatus.REVOKED);

    // -------------------------------------------------------------------------
    // Step 7: Physical Card & Bed Reissuing to New Tenant (BR-10 & V7 Migration)
    // -------------------------------------------------------------------------
    OnboardingRequest newTenantReq =
        OnboardingRequest.builder()
            .fullName("Trần Thị Bình")
            .gender(GenderPolicy.MALE) // Same male room
            .idCardNumber("079203999992")
            .phone("0982223344")
            .email("binh.tran.e2e@example.com")
            .permanentAddress("456 Hai Bà Trưng, TP.HCM")
            .bedId(testBed.getId())
            .startDate(LocalDate.of(2027, 9, 2))
            .endDate(LocalDate.of(2028, 9, 2))
            .depositAmount(new BigDecimal("1500000.00"))
            .build();

    OnboardingResponse newOnboardRes = onboardingService.onboardTenant(newTenantReq);
    assertThat(newOnboardRes).isNotNull();

    // Re-assign the exact same physical RFID card "THE-E2E-001" to new tenant's vehicle
    CreateVehicleAdminRequest newVehicleReq =
        CreateVehicleAdminRequest.builder()
            .tenantId(newOnboardRes.tenant().id())
            .vehicleType(VehicleType.MANUAL_MOTORCYCLE)
            .licensePlate("59-X9 888.77")
            .brand("Yamaha")
            .color("Xanh GP")
            .cardCode("THE-E2E-001") // Reused card!
            .build();

    VehicleResponse newVehicleRes = vehicleService.createVehicleAdmin(newVehicleReq);
    assertThat(newVehicleRes.cardCode()).isEqualTo("THE-E2E-001");
    assertThat(newVehicleRes.status()).isEqualTo(VehicleStatus.ACTIVE);
  }
}
