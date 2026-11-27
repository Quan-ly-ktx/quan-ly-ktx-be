package com.uit.dormmanagement.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.contract.CheckoutContractRequest;
import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleChangeRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentContent;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleChangeRequestResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.RoomCodeRegistry;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
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
import com.uit.dormmanagement.repositories.VehicleChangeRequestRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.schedulers.ContractStatusScheduler;
import com.uit.dormmanagement.services.ContractService;
import com.uit.dormmanagement.services.OnboardingService;
import com.uit.dormmanagement.services.VehicleChangeRequestService;
import com.uit.dormmanagement.services.VehicleService;
import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.http.HttpStatus;
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
  @Autowired private VehicleChangeRequestService vehicleChangeRequestService;
  @Autowired private ContractService contractService;
  @Autowired private ContractStatusScheduler contractStatusScheduler;

  @Autowired private RoomRepository roomRepository;
  @Autowired private RoomCodeRegistryRepository roomCodeRegistryRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private VehicleChangeRequestRepository vehicleChangeRequestRepository;
  @Autowired private UserRepository userRepository;

  private Room testRoom;
  private Bed testBed;
  private Bed secondBed;
  private Bed thirdBed;

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
    List<Bed> savedBeds = bedRepository.saveAll(beds);
    testBed = savedBeds.get(0);
    secondBed = savedBeds.get(1);
    thirdBed = savedBeds.get(2);
  }

  @AfterEach
  void resetClock() {
    testClock.reset();
  }

  @Test
  @DisplayName(
      "E2E Phase 3 Full Resident Lifecycle: Day-01 Guard -> Onboarding -> Vehicle & Change Request -> Docs & Day-25+ Renewal/Scheduler -> Maintenance Checkout -> Post-Checkout History/Stream -> Debt-Blocked & Re-Onboarding")
  void testCompleteResidentLifecycle() throws IOException {
    // -------------------------------------------------------------------------
    // Stage 1: Day-01 Rule & Direct Onboarding (FR-09, T03)
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
            .endDate(LocalDate.of(2027, 8, 31))
            .depositAmount(new BigDecimal("1500000.00"))
            .build();

    testClock.setClock(Clock.fixed(Instant.parse("2026-08-31T10:00:00Z"), TimeConfig.DORM_ZONE));
    assertThatThrownBy(() -> onboardingService.onboardTenant(onboardReq))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.ONBOARDING_ONLY_ON_DAY_ONE);

    testClock.setClock(Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));
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

    Bed occupiedBed = bedRepository.findById(testBed.getId()).orElseThrow();
    assertThat(occupiedBed.getStatus()).isEqualTo(BedStatus.OCCUPIED);
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
    // Stage 2: Vehicle Registration, Card Issue, Quota & Pending Change Request (FR-08, BR-10)
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

    IssueCardRequest issueCardReq = IssueCardRequest.builder().cardCode("THE-E2E-001").build();
    VehicleResponse issuedRes = vehicleService.issueCard(vehicleId, issueCardReq);

    assertThat(issuedRes.status()).isEqualTo(VehicleStatus.ISSUED);
    assertThat(issuedRes.cardCode()).isEqualTo("THE-E2E-001");

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

    CreateVehicleChangeRequest changeReq =
        CreateVehicleChangeRequest.builder()
            .currentVehicleId(vehicleId)
            .proposedVehicleType(VehicleType.ELECTRIC_MOTORBIKE)
            .proposedLicensePlate("59-X3 555.66")
            .proposedBrand("VinFast")
            .proposedColor("Xám")
            .build();
    VehicleChangeRequestResponse changeRequestRes =
        vehicleChangeRequestService.create(userId, changeReq, frontImage, backImage);
    assertThat(changeRequestRes.status()).isEqualTo(VehicleChangeRequestStatus.PENDING);
    UUID changeRequestId = changeRequestRes.id();

    // -------------------------------------------------------------------------
    // Stage 3: Contract Document Upload, Renewal with Day-25+ Catch-Up & Scheduler (FR-10, FR-12,
    // T04, T05)
    // -------------------------------------------------------------------------
    byte[] originalContractBytes = "%PDF-1.4 hop dong goc".getBytes(StandardCharsets.UTF_8);
    MockMultipartFile contractDoc =
        new MockMultipartFile(
            "files", "hop_dong_goc.pdf", "application/pdf", originalContractBytes);
    List<ContractDocumentResponse> uploadedDocs =
        contractService.uploadDocuments(
            contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(contractDoc));
    assertThat(uploadedDocs).hasSize(1);
    assertThat(uploadedDocs.getFirst().renewalId()).isNull();
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
            "annexDocuments",
            "phu_luc_gia_han.pdf",
            "application/pdf",
            "%PDF-1.4 phu luc gia han".getBytes(StandardCharsets.UTF_8));

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
    // Day 26 >= 25 in Asia/Ho_Chi_Minh triggers T05 catch-up invoice for 2027-09-01
    testClock.setClock(Clock.fixed(Instant.parse("2027-08-26T03:00:00Z"), TimeConfig.DORM_ZONE));

    ContractDetailResponse renewedContract =
        contractService.renewContract(contractId, renewReq, List.of(annexDoc));
    assertThat(renewedContract.endDate()).isEqualTo(LocalDate.of(2028, 8, 31));
    assertThat(renewedContract.monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(renewedContract.status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(renewedContract.renewals()).hasSize(1);
    assertThat(renewedContract.renewals().getFirst().sequenceNo()).isEqualTo(1);
    assertThat(renewedContract.renewals().getFirst().signedOn())
        .isEqualTo(LocalDate.of(2027, 8, 26));
    assertThat(renewedContract.renewals().getFirst().previousEndOn())
        .isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(renewedContract.renewals().getFirst().effectiveFrom())
        .isEqualTo(LocalDate.of(2027, 9, 1));
    assertThat(renewedContract.renewals().getFirst().newEndOn())
        .isEqualTo(LocalDate.of(2028, 8, 31));
    assertThat(renewedContract.renewals().getFirst().monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(renewedContract.documents()).hasSize(2);

    UUID renewalId = renewedContract.renewals().getFirst().id();

    Contract afterRenewDbContract = contractRepository.findById(contractId).orElseThrow();
    assertThat(afterRenewDbContract.getOriginalMonthlyRent()).isEqualByComparingTo("1500000");
    assertThat(afterRenewDbContract.getOriginalEndOn()).isEqualTo(LocalDate.of(2027, 8, 31));

    Invoice catchUpInvoice =
        invoiceRepository
            .findByContractIdAndBillingPeriod(contractId, LocalDate.of(2027, 9, 1))
            .orElseThrow();
    assertThat(catchUpInvoice.getAmount()).isEqualByComparingTo("1800000");
    assertThat(catchUpInvoice.getDueOn()).isEqualTo(LocalDate.of(2027, 9, 5));
    assertThat(catchUpInvoice.getStatus()).isEqualTo(InvoiceStatus.DEBT);

    MockMultipartFile extraAnnexPdf =
        new MockMultipartFile(
            "files",
            "phu_luc_bo_sung.pdf",
            "application/pdf",
            "%PDF-1.4 phu luc bo sung".getBytes(StandardCharsets.UTF_8));
    List<ContractDocumentResponse> extraAnnexDocs =
        contractService.uploadDocuments(
            contractId, ContractDocumentCategory.RENEWAL_ANNEX, renewalId, List.of(extraAnnexPdf));
    assertThat(extraAnnexDocs).hasSize(1);
    assertThat(extraAnnexDocs.getFirst().renewalId()).isEqualTo(renewalId);
    assertThat(extraAnnexDocs.getFirst().documentCategory())
        .isEqualTo(ContractDocumentCategory.RENEWAL_ANNEX);

    assertThat(
            contractRepository.findAllByStatusAndEffectiveEndOnLessThanEqual(
                ContractStatus.ACTIVE, LocalDate.of(2027, 9, 25)))
        .isEmpty();
    contractStatusScheduler.updateExpiringContracts();
    assertThat(contractRepository.findById(contractId).orElseThrow().getStatus())
        .isEqualTo(ContractStatus.ACTIVE);

    // -------------------------------------------------------------------------
    // Stage 4: Checkout with MAINTENANCE Bed Choice, Pending Request Cancellation & Debt
    // Preservation (FR-13, T07)
    // -------------------------------------------------------------------------
    ContractDetailResponse terminatedContract =
        contractService.terminateContract(
            contractId,
            CheckoutContractRequest.builder()
                .bedStatusAfterCheckout(BedStatus.MAINTENANCE)
                .handoverConfirmed(true)
                .build());
    assertThat(terminatedContract.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(terminatedContract.checkedOutAt()).isNotNull();

    Contract dbContract = contractRepository.findById(contractId).orElseThrow();
    assertThat(dbContract.getStatus()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(dbContract.getCheckedOutAt()).isNotNull();

    Bed dbBed = bedRepository.findById(testBed.getId()).orElseThrow();
    assertThat(dbBed.getStatus()).isEqualTo(BedStatus.MAINTENANCE);
    assertThat(bedRepository.countByRoomIdAndStatus(testRoom.getId(), BedStatus.OCCUPIED)).isZero();

    Tenant dbTenant = tenantRepository.findById(tenantId).orElseThrow();
    assertThat(dbTenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);

    User dbUser = userRepository.findById(userId).orElseThrow();
    assertThat(dbUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

    Vehicle dbVehicle = vehicleRepository.findById(vehicleId).orElseThrow();
    assertThat(dbVehicle.getStatus()).isEqualTo(VehicleStatus.REVOKED);
    assertThat(dbVehicle.getRevokedAt()).isNotNull();
    assertThat(dbVehicle.getCardCode()).isEqualTo("THE-E2E-001");

    VehicleChangeRequest dbChangeRequest =
        vehicleChangeRequestRepository.findById(changeRequestId).orElseThrow();
    assertThat(dbChangeRequest.getStatus()).isEqualTo(VehicleChangeRequestStatus.CANCELLED);
    assertThat(dbChangeRequest.getProcessedAt()).isNotNull();

    Invoice preservedDebtInvoice = invoiceRepository.findById(catchUpInvoice.getId()).orElseThrow();
    assertThat(preservedDebtInvoice.getStatus()).isEqualTo(InvoiceStatus.DEBT);

    // -------------------------------------------------------------------------
    // Stage 5: Post-Checkout Tenant Contract History & Document Streaming (FR-07, FR-10 step 6,
    // FR-13 step 6)
    // -------------------------------------------------------------------------
    assertThat(contractService.getMyContract(userId)).isNull();

    List<ContractDetailResponse> postCheckoutHistory = contractService.getMyContractHistory(userId);
    assertThat(postCheckoutHistory).hasSize(1);
    ContractDetailResponse historyEntry = postCheckoutHistory.getFirst();
    assertThat(historyEntry.id()).isEqualTo(contractId);
    assertThat(historyEntry.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(historyEntry.renewals()).hasSize(1);
    assertThat(historyEntry.documents()).hasSize(3);
    assertThat(historyEntry.documents())
        .allSatisfy(
            doc ->
                assertThat(doc.fileUrl())
                    .startsWith("/api/v1/tenant/contracts/" + contractId + "/documents/")
                    .endsWith("/content")
                    .doesNotContain("/uploads/"));

    ContractDetailResponse liquidatedDetail = contractService.getMyContractById(userId, contractId);
    assertThat(liquidatedDetail.id()).isEqualTo(contractId);
    assertThat(liquidatedDetail.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(liquidatedDetail.documents()).hasSize(3);

    ContractDocumentContent streamedContent =
        contractService.loadDocumentContentForTenant(
            userId, contractId, uploadedDocs.getFirst().id());
    assertThat(streamedContent.contentType()).isEqualTo("application/pdf");
    assertThat(streamedContent.originalFilename()).isEqualTo("hop_dong_goc.pdf");
    try (InputStream is = streamedContent.resource().getInputStream()) {
      assertThat(is.readAllBytes()).isEqualTo(originalContractBytes);
    }

    // -------------------------------------------------------------------------
    // Stage 6: Physical Card Reuse, Returning-Tenant Debt Block & Day-01 Re-Onboarding (FR-09,
    // BR-04 §6)
    // -------------------------------------------------------------------------
    testClock.setClock(Clock.fixed(Instant.parse("2027-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));

    OnboardingRequest newTenantReq =
        OnboardingRequest.builder()
            .fullName("Trần Văn Bình")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203999992")
            .phone("0982223344")
            .email("binh.tran.e2e@example.com")
            .permanentAddress("456 Hai Bà Trưng, TP.HCM")
            .bedId(secondBed.getId())
            .startDate(LocalDate.of(2027, 9, 1))
            .endDate(LocalDate.of(2028, 8, 31))
            .depositAmount(new BigDecimal("1800000.00"))
            .build();

    OnboardingResponse newOnboardRes = onboardingService.onboardTenant(newTenantReq);
    assertThat(newOnboardRes).isNotNull();
    assertThat(newOnboardRes.firstInvoice()).isNotNull();
    assertThat(newOnboardRes.firstInvoice().billingPeriod()).isEqualTo(LocalDate.of(2027, 9, 1));

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
        vehicleService.issueCard(newVehicleRes.id(), new IssueCardRequest("THE-E2E-001"));
    assertThat(reusedIssuedRes.cardCode()).isEqualTo("THE-E2E-001");
    assertThat(reusedIssuedRes.status()).isEqualTo(VehicleStatus.ISSUED);

    // Returning tenant (079203999991) cannot re-onboard while 2027-09-01 catch-up invoice is DEBT
    testClock.setClock(Clock.fixed(Instant.parse("2028-09-01T02:00:00Z"), TimeConfig.DORM_ZONE));
    OnboardingRequest reOnboardReq =
        OnboardingRequest.builder()
            .fullName("Nguyễn Văn An")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203999991")
            .phone("0981112233")
            .email("an.nguyen.e2e@example.com")
            .permanentAddress("123 Cách Mạng Tháng 8, TP.HCM")
            .bedId(thirdBed.getId())
            .startDate(LocalDate.of(2028, 9, 1))
            .endDate(LocalDate.of(2029, 8, 31))
            .depositAmount(new BigDecimal("1800000.00"))
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(reOnboardReq))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> {
              BaseAppException bae = (BaseAppException) ex;
              return bae.getErrorCode() == ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES
                  && bae.getErrorCode().getHttpStatus() == HttpStatus.CONFLICT;
            });

    preservedDebtInvoice.setStatus(InvoiceStatus.PAID);
    invoiceRepository.saveAndFlush(preservedDebtInvoice);

    OnboardingResponse reOnboardRes = onboardingService.onboardTenant(reOnboardReq);
    assertThat(reOnboardRes.tenant().id()).isEqualTo(tenantId);
    assertThat(reOnboardRes.tenant().userId()).isEqualTo(userId);
    assertThat(reOnboardRes.account().initialPassword()).isNull();
    assertThat(reOnboardRes.tenant().residentStatus()).isEqualTo(ResidentStatus.ACTIVE);
    assertThat(tenantRepository.findById(tenantId).orElseThrow().getResidentStatus())
        .isEqualTo(ResidentStatus.ACTIVE);
    assertThat(bedRepository.findById(thirdBed.getId()).orElseThrow().getStatus())
        .isEqualTo(BedStatus.OCCUPIED);
    assertThat(reOnboardRes.contract().id()).isNotEqualTo(contractId);
    assertThat(reOnboardRes.contract().status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(reOnboardRes.firstInvoice().billingPeriod()).isEqualTo(LocalDate.of(2028, 9, 1));
    assertThat(reOnboardRes.firstInvoice().dueOn()).isEqualTo(LocalDate.of(2028, 9, 5));
    assertThat(reOnboardRes.firstInvoice().amount()).isEqualByComparingTo("1800000");
    assertThat(reOnboardRes.firstInvoice().status()).isEqualTo(InvoiceStatus.DEBT);

    List<ContractDetailResponse> returningHistory = contractService.getMyContractHistory(userId);
    assertThat(returningHistory).hasSize(2);
    assertThat(returningHistory.get(0).id()).isEqualTo(reOnboardRes.contract().id());
    assertThat(returningHistory.get(0).status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(returningHistory.get(1).id()).isEqualTo(contractId);
    assertThat(returningHistory.get(1).status()).isEqualTo(ContractStatus.LIQUIDATED);
  }
}
