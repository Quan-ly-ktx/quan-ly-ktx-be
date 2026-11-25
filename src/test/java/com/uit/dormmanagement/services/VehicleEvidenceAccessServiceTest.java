package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleChangeRequestRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.impl.VehicleEvidenceAccessServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

@ExtendWith(MockitoExtension.class)
class VehicleEvidenceAccessServiceTest {

  @Mock private VehicleRepository vehicleRepository;
  @Mock private VehicleChangeRequestRepository vehicleChangeRequestRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private VehicleEvidenceStorage vehicleEvidenceStorage;

  @InjectMocks private VehicleEvidenceAccessServiceImpl accessService;

  private UUID ownerUserId;
  private UUID otherUserId;
  private UUID vehicleId;
  private UUID requestId;
  private Tenant ownerTenant;
  private Tenant otherTenant;
  private Vehicle vehicle;
  private VehicleChangeRequest request;

  @BeforeEach
  void setUp() {
    ownerUserId = UUID.randomUUID();
    otherUserId = UUID.randomUUID();
    vehicleId = UUID.randomUUID();
    requestId = UUID.randomUUID();

    ownerTenant = Tenant.builder().id(UUID.randomUUID()).build();
    otherTenant = Tenant.builder().id(UUID.randomUUID()).build();

    vehicle =
        Vehicle.builder()
            .id(vehicleId)
            .tenant(ownerTenant)
            .registrationFrontFileKey("vehicles/" + vehicleId + "/front-123.jpg")
            .registrationBackFileKey("vehicles/" + vehicleId + "/back-123.jpg")
            .build();

    request =
        VehicleChangeRequest.builder()
            .id(requestId)
            .tenant(ownerTenant)
            .proposedRegistrationFrontFileKey("vehicles/req-" + requestId + "/front-456.jpg")
            .build();
  }

  @Test
  @DisplayName("Manager can access any vehicle evidence")
  void loadVehicleEvidence_managerAccess_shouldSucceed() {
    VehicleEvidenceContent expectedContent =
        new VehicleEvidenceContent(new ByteArrayResource(new byte[10]), "image/jpeg", 10);
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
    when(vehicleEvidenceStorage.load("vehicles/" + vehicleId + "/front-123.jpg"))
        .thenReturn(expectedContent);

    VehicleEvidenceContent result =
        accessService.loadVehicleEvidence(UUID.randomUUID(), true, vehicleId, "front");

    assertThat(result).isEqualTo(expectedContent);
  }

  @Test
  @DisplayName("Owning tenant can access own vehicle evidence")
  void loadVehicleEvidence_ownerAccess_shouldSucceed() {
    VehicleEvidenceContent expectedContent =
        new VehicleEvidenceContent(new ByteArrayResource(new byte[10]), "image/jpeg", 10);
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
    when(tenantRepository.findByUserId(ownerUserId)).thenReturn(Optional.of(ownerTenant));
    when(vehicleEvidenceStorage.load("vehicles/" + vehicleId + "/front-123.jpg"))
        .thenReturn(expectedContent);

    VehicleEvidenceContent result =
        accessService.loadVehicleEvidence(ownerUserId, false, vehicleId, "front");

    assertThat(result).isEqualTo(expectedContent);
  }

  @Test
  @DisplayName("Non-owning tenant receives 404 for another tenant's vehicle evidence")
  void loadVehicleEvidence_nonOwnerAccess_shouldThrowNotFound() {
    when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
    when(tenantRepository.findByUserId(otherUserId)).thenReturn(Optional.of(otherTenant));

    assertThatThrownBy(() -> accessService.loadVehicleEvidence(otherUserId, false, vehicleId, "front"))
        .isInstanceOf(BaseAppException.class)
        .matches(e -> ((BaseAppException) e).getErrorCode() == VehicleErrorCode.VEHICLE_NOT_FOUND);
  }

  @Test
  @DisplayName("Manager can access change request evidence")
  void loadChangeRequestEvidence_managerAccess_shouldSucceed() {
    VehicleEvidenceContent expectedContent =
        new VehicleEvidenceContent(new ByteArrayResource(new byte[10]), "image/jpeg", 10);
    when(vehicleChangeRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
    when(vehicleEvidenceStorage.load("vehicles/req-" + requestId + "/front-456.jpg"))
        .thenReturn(expectedContent);

    VehicleEvidenceContent result =
        accessService.loadChangeRequestEvidence(UUID.randomUUID(), true, requestId, "front");

    assertThat(result).isEqualTo(expectedContent);
  }

  @Test
  @DisplayName("Missing optional back evidence throws VEHICLE_EVIDENCE_NOT_FOUND")
  void loadChangeRequestEvidence_missingBack_shouldThrowNotFound() {
    when(vehicleChangeRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> accessService.loadChangeRequestEvidence(UUID.randomUUID(), true, requestId, "back"))
        .isInstanceOf(BaseAppException.class)
        .matches(e -> ((BaseAppException) e).getErrorCode() == VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
  }
}
