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
import com.uit.dormmanagement.entities.User;
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

  private UUID tenantId;
  private UUID userId;
  private Tenant tenant;
  private UUID vehicleId;
  private Vehicle vehicle;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    userId = UUID.randomUUID();
    User user = User.builder().id(userId).email("nam.nguyen@example.com").build();

    tenant =
        Tenant.builder()
            .id(tenantId)
            .user(user)
            .tenantCode("NT-0187")
            .fullName("Nguyễn Hoàng Nam")
            .gender(GenderPolicy.MALE)
            .residentStatus(ResidentStatus.ACTIVE)
            .build();

    vehicleId = UUID.randomUUID();
    vehicle =
        Vehicle.builder()
            .id(vehicleId)
            .tenant(tenant)
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X3 789.12")
            .brand("Honda")
            .color("Đen")
            .cardCode("THE-0187")
            .status(VehicleStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("getVehicles: should return paged vehicle responses")
  void getVehicles_shouldReturnPagedResponses() {
    Pageable pageable = PageRequest.of(0, 10);
    when(vehicleRepository.findVehiclesWithFilters(
            eq("Honda"), eq(VehicleStatus.ACTIVE), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(vehicle), pageable, 1));

    Page<VehicleResponse> page =
        vehicleService.getVehicles(pageable, "  Honda  ", VehicleStatus.ACTIVE);

    assertThat(page.getTotalElements()).isEqualTo(1);
    VehicleResponse res = page.getContent().getFirst();
    assertThat(res.licensePlate()).isEqualTo("59-X3 789.12");
    assertThat(res.tenantName()).isEqualTo("Nguyễn Hoàng Nam");
  }

  @Test
  @DisplayName(
      "registerVehicle: when tenant already has active or pending vehicle should throw VEHICLE_QUOTA_EXCEEDED")
  void registerVehicle_whenTenantAlreadyHasActiveVehicle_shouldThrowQuotaExceeded() {
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
    when(vehicleRepository.countByTenantIdAndStatusInAndDeletedAtIsNull(eq(tenantId), any()))
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
    when(vehicleRepository.countByTenantIdAndStatusInAndDeletedAtIsNull(eq(tenantId), any()))
        .thenReturn(0L);
    when(vehicleRepository.existsByLicensePlateAndStatusInAndDeletedAtIsNull(
            eq("59-X3 999.99"), any()))
        .thenReturn(false);
    when(storageService.uploadFile(front, "vehicles")).thenReturn("/uploads/vehicles/front.jpg");
    when(storageService.uploadFile(back, "vehicles")).thenReturn("/uploads/vehicles/back.jpg");
    when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

    VehicleResponse res = vehicleService.registerVehicle(userId, request, front, back);

    assertThat(res.status()).isEqualTo(VehicleStatus.PENDING);
    assertThat(res.registrationFrontUrl()).isEqualTo("/uploads/vehicles/front.jpg");
    assertThat(res.registrationBackUrl()).isEqualTo("/uploads/vehicles/back.jpg");
    assertThat(res.licensePlate()).isEqualTo("59-X3 999.99");
  }

  @Test
  @DisplayName("issueCard: when vehicle is pending and cardCode unique should activate vehicle")
  void issueCard_whenPendingAndUniqueCard_shouldActivate() {
    vehicle.setStatus(VehicleStatus.PENDING);
    vehicle.setCardCode(null);

    IssueCardRequest req = IssueCardRequest.builder().cardCode("THE-0187").build();
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
    when(vehicleRepository.existsByCardCodeAndStatusAndDeletedAtIsNull(
            "THE-0187", VehicleStatus.ACTIVE))
        .thenReturn(false);
    when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

    VehicleResponse res = vehicleService.issueCard(vehicleId, req);

    assertThat(res.status()).isEqualTo(VehicleStatus.ACTIVE);
    assertThat(res.cardCode()).isEqualTo("THE-0187");
  }

  @Test
  @DisplayName(
      "issueCard: when cardCode already in use by active vehicle should throw CARD_CODE_DUPLICATED")
  void issueCard_whenCardCodeDuplicated_shouldThrow() {
    vehicle.setStatus(VehicleStatus.PENDING);
    IssueCardRequest req = IssueCardRequest.builder().cardCode("THE-0187").build();
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
    when(vehicleRepository.existsByCardCodeAndStatusAndDeletedAtIsNull(
            "THE-0187", VehicleStatus.ACTIVE))
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
  @DisplayName("deleteVehicle: should soft delete vehicle")
  void deleteVehicle_shouldSoftDelete() {
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));

    vehicleService.deleteVehicle(vehicleId);

    verify(vehicleRepository).save(vehicle);
    assertThat(vehicle.getDeletedAt()).isNotNull();
  }
}
