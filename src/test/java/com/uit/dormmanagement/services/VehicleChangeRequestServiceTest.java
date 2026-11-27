package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleChangeRequest;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleChangeRequestResponse;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleChangeRequestRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.impl.VehicleChangeRequestServiceImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class VehicleChangeRequestServiceTest {

  @Mock private VehicleRepository vehicleRepository;
  @Mock private VehicleChangeRequestRepository vehicleChangeRequestRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private StaffProfileRepository staffProfileRepository;
  @Mock private VehicleRegistrationValidator vehicleRegistrationValidator;
  @Mock private VehicleEvidenceStorage vehicleEvidenceStorage;

  @InjectMocks private VehicleChangeRequestServiceImpl service;

  private UUID tenantUserId;
  private UUID managerUserId;
  private UUID currentVehicleId;
  private UUID requestId;
  private Tenant tenant;
  private Vehicle currentVehicle;
  private VehicleChangeRequest request;

  @BeforeEach
  void setUp() {
    tenantUserId = UUID.randomUUID();
    managerUserId = UUID.randomUUID();
    currentVehicleId = UUID.randomUUID();
    requestId = UUID.randomUUID();

    tenant =
        Tenant.builder()
            .id(UUID.randomUUID())
            .tenantCode("NT-001")
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .residentStatus(ResidentStatus.ACTIVE)
            .build();

    currentVehicle =
        Vehicle.builder()
            .id(currentVehicleId)
            .tenant(tenant)
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X1 111.11")
            .brand("Honda")
            .color("Do")
            .cardCode("CARD-999")
            .status(VehicleStatus.ISSUED)
            .issuedAt(Instant.now().minusSeconds(3600))
            .build();

    request =
        VehicleChangeRequest.builder()
            .id(requestId)
            .tenant(tenant)
            .currentVehicle(currentVehicle)
            .proposedVehicleType(VehicleType.MANUAL_MOTORCYCLE)
            .proposedLicensePlate("59-X2 222.22")
            .proposedBrand("Yamaha")
            .proposedColor("Xanh")
            .status(VehicleChangeRequestStatus.PENDING)
            .createdAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("create: should create PENDING request without mutating current vehicle")
  void create_shouldCreatePendingRequest() {
    CreateVehicleChangeRequest dto =
        CreateVehicleChangeRequest.builder()
            .currentVehicleId(currentVehicleId)
            .proposedVehicleType(VehicleType.MANUAL_MOTORCYCLE)
            .proposedLicensePlate("59-X2 222.22")
            .proposedBrand("Yamaha")
            .proposedColor("Xanh")
            .build();

    MockMultipartFile front = new MockMultipartFile("front", "f.jpg", "image/jpeg", new byte[] {1});
    MockMultipartFile back = new MockMultipartFile("back", "b.jpg", "image/jpeg", new byte[] {2});

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(vehicleRepository.findByIdForUpdate(currentVehicleId))
        .thenReturn(Optional.of(currentVehicle));
    when(vehicleChangeRequestRepository.existsByTenantIdAndStatus(
            tenant.getId(), VehicleChangeRequestStatus.PENDING))
        .thenReturn(false);

    VehicleRegistrationValidator.ValidatedVehicleRegistration validated =
        new VehicleRegistrationValidator.ValidatedVehicleRegistration(
            VehicleType.MANUAL_MOTORCYCLE, "59-X2 222.22", "Yamaha", "Xanh", front, back);
    when(vehicleRegistrationValidator.validate(any(), any(), any(), any(), any(), any()))
        .thenReturn(validated);

    when(vehicleChangeRequestRepository.saveAndFlush(any(VehicleChangeRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    VehicleChangeRequestResponse res = service.create(tenantUserId, dto, front, back);

    assertThat(res.status()).isEqualTo(VehicleChangeRequestStatus.PENDING);
    assertThat(res.currentVehicleId()).isEqualTo(currentVehicleId);
    assertThat(res.proposedLicensePlate()).isEqualTo("59-X2 222.22");
    assertThat(currentVehicle.getStatus())
        .isEqualTo(VehicleStatus.ISSUED); // current vehicle untouched
  }

  @Test
  @DisplayName("create: when tenant already has pending request should throw ALREADY_EXISTS")
  void create_whenAlreadyPending_shouldThrow() {
    CreateVehicleChangeRequest dto =
        CreateVehicleChangeRequest.builder().currentVehicleId(currentVehicleId).build();

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(vehicleRepository.findByIdForUpdate(currentVehicleId))
        .thenReturn(Optional.of(currentVehicle));
    when(vehicleChangeRequestRepository.existsByTenantIdAndStatus(
            tenant.getId(), VehicleChangeRequestStatus.PENDING))
        .thenReturn(true);

    assertThatThrownBy(() -> service.create(tenantUserId, dto, null, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            e ->
                ((BaseAppException) e).getErrorCode()
                    == VehicleErrorCode.VEHICLE_CHANGE_REQUEST_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("approve: should atomically replace old vehicle with successor using same card code")
  void approve_shouldReplaceAtomically() {
    StaffProfile staff = StaffProfile.builder().id(UUID.randomUUID()).build();
    when(vehicleChangeRequestRepository.findCurrentVehicleIdById(requestId))
        .thenReturn(Optional.of(currentVehicleId));
    when(vehicleRepository.findByIdForUpdate(currentVehicleId))
        .thenReturn(Optional.of(currentVehicle));
    when(vehicleChangeRequestRepository.findByIdForUpdate(requestId))
        .thenReturn(Optional.of(request));
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(staff));
    when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
    when(vehicleChangeRequestRepository.saveAndFlush(any(VehicleChangeRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    VehicleChangeRequestResponse res = service.approve(managerUserId, requestId);

    assertThat(res.status()).isEqualTo(VehicleChangeRequestStatus.APPROVED);
    assertThat(res.processedByStaffId()).isEqualTo(staff.getId());
    assertThat(currentVehicle.getStatus()).isEqualTo(VehicleStatus.REPLACED);
    assertThat(currentVehicle.getReplacedAt()).isNotNull();
    assertThat(request.getNewVehicle()).isNotNull();
    assertThat(request.getNewVehicle().getCardCode()).isEqualTo("CARD-999"); // same RFID card!
    assertThat(request.getNewVehicle().getStatus()).isEqualTo(VehicleStatus.ISSUED);
    assertThat(request.getNewVehicle().getReplacedVehicle()).isEqualTo(currentVehicle);
    assertThat(currentVehicle.getReplacedByVehicle()).isEqualTo(request.getNewVehicle());
  }

  @Test
  @DisplayName("approve: when request is already approved should throw NOT_PENDING")
  void approve_whenAlreadyProcessed_shouldThrow() {
    request.setStatus(VehicleChangeRequestStatus.APPROVED);
    when(vehicleChangeRequestRepository.findCurrentVehicleIdById(requestId))
        .thenReturn(Optional.of(currentVehicleId));
    when(vehicleRepository.findByIdForUpdate(currentVehicleId))
        .thenReturn(Optional.of(currentVehicle));
    when(vehicleChangeRequestRepository.findByIdForUpdate(requestId))
        .thenReturn(Optional.of(request));

    assertThatThrownBy(() -> service.approve(managerUserId, requestId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            e ->
                ((BaseAppException) e).getErrorCode()
                    == VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_PENDING);
  }

  @Test
  @DisplayName("reject: should reject request with reason and leave current vehicle untouched")
  void reject_shouldRejectRequest() {
    StaffProfile staff = StaffProfile.builder().id(UUID.randomUUID()).build();
    when(vehicleChangeRequestRepository.findByIdForUpdate(requestId))
        .thenReturn(Optional.of(request));
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(staff));
    when(vehicleChangeRequestRepository.save(any(VehicleChangeRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    VehicleChangeRequestResponse res = service.reject(managerUserId, requestId, "Hồ sơ không khớp");

    assertThat(res.status()).isEqualTo(VehicleChangeRequestStatus.REJECTED);
    assertThat(res.rejectionReason()).isEqualTo("Hồ sơ không khớp");
    assertThat(res.processedByStaffId()).isEqualTo(staff.getId());
    assertThat(currentVehicle.getStatus()).isEqualTo(VehicleStatus.ISSUED); // unchanged!
  }
}
