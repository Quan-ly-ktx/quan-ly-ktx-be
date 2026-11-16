package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.impl.VehicleServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

  @Mock private VehicleRepository vehicleRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private StorageService storageService;

  @InjectMocks private VehicleServiceImpl vehicleService;

  private UUID userId;
  private UUID tenantId;
  private UUID vehicleId;
  private Tenant tenant;
  private Vehicle vehicle;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    tenantId = UUID.randomUUID();
    vehicleId = UUID.randomUUID();

    tenant =
        Tenant.builder()
            .id(tenantId)
            .tenantCode("SV-2026-0001")
            .fullName("Nguyễn Hoàng Nam")
            .idCardNumber("079204001234")
            .gender(GenderPolicy.MALE)
            .phone("0901234567")
            .permanentAddress("123 Lê Lợi, Q1, TP.HCM")
            .residentStatus(ResidentStatus.ACTIVE)
            .build();

    vehicle =
        Vehicle.builder()
            .id(vehicleId)
            .tenant(tenant)
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X3 789.12")
            .brand("Honda")
            .color("Đen")
            .cardCode("THE-0187")
            .status(VehicleStatus.ISSUED)
            .createdAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("getVehicles: should return paged vehicle responses")
  void getVehicles_shouldReturnPagedResponses() {
    Pageable pageable = PageRequest.of(0, 10);
    when(vehicleRepository.findVehiclesWithFilters(
            eq("Honda"), eq(VehicleStatus.ISSUED), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(vehicle), pageable, 1));

    Page<VehicleResponse> page =
        vehicleService.getVehicles(pageable, "  Honda  ", VehicleStatus.ISSUED);

    assertThat(page.getTotalElements()).isEqualTo(1);
    VehicleResponse res = page.getContent().getFirst();
    assertThat(res.licensePlate()).isEqualTo("59-X3 789.12");
    assertThat(res.tenantName()).isEqualTo("Nguyễn Hoàng Nam");
  }

  @Test
  @DisplayName("getVehicleById: when found should return vehicle response")
  void getVehicleById_whenFound_shouldReturn() {
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));

    VehicleResponse res = vehicleService.getVehicleById(vehicleId);

    assertThat(res.id()).isEqualTo(vehicleId);
    assertThat(res.licensePlate()).isEqualTo("59-X3 789.12");
  }

  @Test
  @DisplayName("registerVehicle: when tenant already has active vehicle should throw VEHICLE_QUOTA_EXCEEDED")
  void registerVehicle_whenQuotaExceeded_shouldThrow() {
    TenantRegisterVehicleRequest request =
        TenantRegisterVehicleRequest.builder()
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X3 999.99")
            .brand("Honda")
            .color("Đỏ")
            .build();

    MockMultipartFile front =
        new MockMultipartFile("frontImage", "front.jpg", "image/jpeg", new byte[] {1, 2});
    MockMultipartFile back =
        new MockMultipartFile("backImage", "back.jpg", "image/jpeg", new byte[] {3, 4});

    when(tenantRepository.findByUserId(userId)).thenReturn(Optional.of(tenant));
    when(vehicleRepository.countByTenantIdAndStatusIn(eq(tenantId), any()))
        .thenReturn(1L);

    assertThatThrownBy(() -> vehicleService.registerVehicle(userId, request, front, back))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode() == VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED);
  }

  @Test
  @DisplayName("registerVehicle: when valid should upload images and save pending vehicle")
  void registerVehicle_whenValid_shouldUploadAndSave() {
    TenantRegisterVehicleRequest request =
        TenantRegisterVehicleRequest.builder()
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X3 999.99")
            .brand("Honda")
            .color("Đỏ")
            .build();

    MockMultipartFile front =
        new MockMultipartFile("frontImage", "front.jpg", "image/jpeg", new byte[] {1, 2});
    MockMultipartFile back =
        new MockMultipartFile("backImage", "back.jpg", "image/jpeg", new byte[] {3, 4});

    when(tenantRepository.findByUserId(userId)).thenReturn(Optional.of(tenant));
    when(vehicleRepository.countByTenantIdAndStatusIn(eq(tenantId), any()))
        .thenReturn(0L);
    when(vehicleRepository.existsByLicensePlateAndStatusIn(
            eq("59-X3 999.99"), any()))
        .thenReturn(false);
    when(storageService.uploadFile(front, "vehicles")).thenReturn("/uploads/vehicles/front.jpg");
    when(storageService.uploadFile(back, "vehicles")).thenReturn("/uploads/vehicles/back.jpg");
    when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

    VehicleResponse res = vehicleService.registerVehicle(userId, request, front, back);

    assertThat(res.status()).isEqualTo(VehicleStatus.PENDING_CARD);
    assertThat(res.registrationFrontUrl()).isEqualTo("/uploads/vehicles/front.jpg");
    assertThat(res.registrationBackUrl()).isEqualTo("/uploads/vehicles/back.jpg");
    assertThat(res.licensePlate()).isEqualTo("59-X3 999.99");
  }

  @Test
  @DisplayName("issueCard: when vehicle is pending and cardCode unique should activate vehicle")
  void issueCard_whenPendingAndUniqueCard_shouldActivate() {
    vehicle.setStatus(VehicleStatus.PENDING_CARD);
    vehicle.setCardCode(null);

    IssueCardRequest req = IssueCardRequest.builder().cardCode("THE-0187").build();
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
    when(vehicleRepository.existsByCardCodeAndStatus(
            "THE-0187", VehicleStatus.ISSUED))
        .thenReturn(false);
    when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

    VehicleResponse res = vehicleService.issueCard(vehicleId, req);

    assertThat(res.status()).isEqualTo(VehicleStatus.ISSUED);
    assertThat(res.cardCode()).isEqualTo("THE-0187");
  }

  @Test
  @DisplayName(
      "issueCard: when cardCode already in use by active vehicle should throw CARD_CODE_DUPLICATED")
  void issueCard_whenCardCodeDuplicated_shouldThrow() {
    vehicle.setStatus(VehicleStatus.PENDING_CARD);
    IssueCardRequest req = IssueCardRequest.builder().cardCode("THE-0187").build();
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
    when(vehicleRepository.existsByCardCodeAndStatus(
            "THE-0187", VehicleStatus.ISSUED))
        .thenReturn(true);

    assertThatThrownBy(() -> vehicleService.issueCard(vehicleId, req))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == VehicleErrorCode.CARD_CODE_DUPLICATED);
  }

  @Test
  @DisplayName("revokeCard: should transition vehicle status to REVOKED")
  void revokeCard_shouldSetStatusRevoked() {
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
    when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

    VehicleResponse res = vehicleService.revokeCard(vehicleId);

    assertThat(res.status()).isEqualTo(VehicleStatus.REVOKED);
  }

  @Test
  @DisplayName("deleteVehicle: should cancel pending vehicle")
  void deleteVehicle_shouldCancelPendingVehicle() {
    vehicle.setStatus(VehicleStatus.PENDING_CARD);
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));

    vehicleService.deleteVehicle(vehicleId);

    verify(vehicleRepository).save(vehicle);
    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.CANCELLED);
    assertThat(vehicle.getCancelledAt()).isNotNull();
  }
}
