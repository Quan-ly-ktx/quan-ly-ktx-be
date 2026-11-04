package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.dtos.request.vehicle.UpdateVehicleRequest;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.StorageService;
import com.uit.dormmanagement.services.VehicleService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

  private final VehicleRepository vehicleRepository;
  private final TenantRepository tenantRepository;
  private final StorageService storageService;

  private static final List<VehicleStatus> OCCUPIED_VEHICLE_STATUSES =
      List.of(VehicleStatus.PENDING, VehicleStatus.ACTIVE);

  @Override
  @Transactional(readOnly = true)
  public Page<VehicleResponse> getVehicles(Pageable pageable, String search, VehicleStatus status) {
    String query = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
    Page<Vehicle> vehicles = vehicleRepository.findVehiclesWithFilters(query, status, pageable);
    return vehicles.map(this::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public VehicleResponse getVehicleById(UUID id) {
    Vehicle vehicle =
        vehicleRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));
    return toResponse(vehicle);
  }

  @Override
  @Transactional
  public VehicleResponse createVehicleAdmin(CreateVehicleAdminRequest request) {
    Tenant tenant =
        tenantRepository
            .findById(request.tenantId())
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    if (tenant.getResidentStatus() != ResidentStatus.ACTIVE) {
      throw new BaseAppException(TenantErrorCode.TENANT_NOT_ACTIVE);
    }

    validateVehicleQuota(tenant.getId());

    String plate = request.licensePlate().trim().toUpperCase();
    validateLicensePlateUnique(plate, null);

    VehicleStatus initialStatus = VehicleStatus.PENDING;
    String cleanCardCode = null;

    if (request.cardCode() != null && !request.cardCode().trim().isEmpty()) {
      cleanCardCode = request.cardCode().trim();
      validateCardCodeUnique(cleanCardCode, null);
      initialStatus = VehicleStatus.ACTIVE;
    }

    Vehicle vehicle =
        Vehicle.builder()
            .tenant(tenant)
            .vehicleType(request.vehicleType())
            .licensePlate(plate)
            .brand(request.brand().trim())
            .color(request.color().trim())
            .cardCode(cleanCardCode)
            .status(initialStatus)
            .registrationFrontUrl(request.registrationFrontUrl())
            .registrationBackUrl(request.registrationBackUrl())
            .build();

    Vehicle saved = vehicleRepository.save(vehicle);
    log.info(
        "Admin created vehicle {} for tenant {}", saved.getLicensePlate(), tenant.getTenantCode());
    return toResponse(saved);
  }

  @Override
  @Transactional
  public VehicleResponse updateVehicle(UUID id, UpdateVehicleRequest request) {
    Vehicle vehicle =
        vehicleRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    String plate = request.licensePlate().trim().toUpperCase();
    if (!plate.equalsIgnoreCase(vehicle.getLicensePlate())) {
      validateLicensePlateUnique(plate, id);
    }

    vehicle.setVehicleType(request.vehicleType());
    vehicle.setLicensePlate(plate);
    vehicle.setBrand(request.brand().trim());
    vehicle.setColor(request.color().trim());

    Vehicle saved = vehicleRepository.save(vehicle);
    log.info("Updated vehicle {} ({})", saved.getLicensePlate(), saved.getId());
    return toResponse(saved);
  }

  @Override
  @Transactional
  public VehicleResponse issueCard(UUID id, IssueCardRequest request) {
    Vehicle vehicle =
        vehicleRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    if (vehicle.getStatus() == VehicleStatus.ACTIVE) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_ALREADY_ACTIVE);
    }
    if (vehicle.getStatus() != VehicleStatus.PENDING) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_NOT_PENDING);
    }

    String cardCode = request.cardCode().trim();
    validateCardCodeUnique(cardCode, id);

    vehicle.setCardCode(cardCode);
    vehicle.setStatus(VehicleStatus.ACTIVE);

    Vehicle saved = vehicleRepository.save(vehicle);
    log.info("Issued card {} to vehicle {}", cardCode, saved.getLicensePlate());
    return toResponse(saved);
  }

  @Override
  @Transactional
  public VehicleResponse revokeCard(UUID id) {
    Vehicle vehicle =
        vehicleRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    vehicle.setStatus(VehicleStatus.REVOKED);
    Vehicle saved = vehicleRepository.save(vehicle);
    log.info("Revoked vehicle card for vehicle {}", saved.getLicensePlate());
    return toResponse(saved);
  }

  @Override
  @Transactional
  public void deleteVehicle(UUID id) {
    Vehicle vehicle =
        vehicleRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    vehicle.setDeletedAt(Instant.now());
    vehicleRepository.save(vehicle);
    log.info("Soft deleted vehicle {}", vehicle.getLicensePlate());
  }

  @Override
  @Transactional(readOnly = true)
  public VehicleResponse getMyVehicle(UUID userId) {
    Tenant tenant =
        tenantRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    return vehicleRepository
        .findFirstByTenantIdAndStatusInAndDeletedAtIsNull(tenant.getId(), OCCUPIED_VEHICLE_STATUSES)
        .map(this::toResponse)
        .orElse(null);
  }

  @Override
  @Transactional
  public VehicleResponse registerVehicle(
      UUID userId,
      TenantRegisterVehicleRequest request,
      MultipartFile frontImage,
      MultipartFile backImage) {
    Tenant tenant =
        tenantRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    if (tenant.getResidentStatus() != ResidentStatus.ACTIVE) {
      throw new BaseAppException(TenantErrorCode.TENANT_NOT_ACTIVE);
    }

    validateVehicleQuota(tenant.getId());

    String plate = request.licensePlate().trim().toUpperCase();
    validateLicensePlateUnique(plate, null);

    String frontUrl = storageService.uploadFile(frontImage, "vehicles");
    String backUrl = storageService.uploadFile(backImage, "vehicles");

    Vehicle vehicle =
        Vehicle.builder()
            .tenant(tenant)
            .vehicleType(request.vehicleType())
            .licensePlate(plate)
            .brand(request.brand().trim())
            .color(request.color().trim())
            .status(VehicleStatus.PENDING)
            .registrationFrontUrl(frontUrl)
            .registrationBackUrl(backUrl)
            .build();

    Vehicle saved = vehicleRepository.save(vehicle);
    log.info("Tenant {} registered vehicle {}", tenant.getTenantCode(), saved.getLicensePlate());
    return toResponse(saved);
  }

  private void validateVehicleQuota(UUID tenantId) {
    long count =
        vehicleRepository.countByTenantIdAndStatusInAndDeletedAtIsNull(
            tenantId, OCCUPIED_VEHICLE_STATUSES);
    if (count >= 1) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED);
    }
  }

  private void validateLicensePlateUnique(String plate, UUID currentVehicleId) {
    boolean exists =
        vehicleRepository.existsByLicensePlateAndStatusInAndDeletedAtIsNull(
            plate, OCCUPIED_VEHICLE_STATUSES);
    if (exists) {
      throw new BaseAppException(VehicleErrorCode.LICENSE_PLATE_DUPLICATED);
    }
  }

  private void validateCardCodeUnique(String cardCode, UUID currentVehicleId) {
    boolean exists =
        vehicleRepository.existsByCardCodeAndStatusAndDeletedAtIsNull(
            cardCode, VehicleStatus.ACTIVE);
    if (exists) {
      throw new BaseAppException(VehicleErrorCode.CARD_CODE_DUPLICATED);
    }
  }

  private VehicleResponse toResponse(Vehicle vehicle) {
    String tenantName = vehicle.getTenant() != null ? vehicle.getTenant().getFullName() : null;
    return VehicleResponse.builder()
        .id(vehicle.getId())
        .tenantId(vehicle.getTenant() != null ? vehicle.getTenant().getId() : null)
        .tenantName(tenantName)
        .vehicleType(vehicle.getVehicleType())
        .licensePlate(vehicle.getLicensePlate())
        .brand(vehicle.getBrand())
        .color(vehicle.getColor())
        .cardCode(vehicle.getCardCode())
        .status(vehicle.getStatus())
        .registrationFrontUrl(vehicle.getRegistrationFrontUrl())
        .registrationBackUrl(vehicle.getRegistrationBackUrl())
        .createdAt(vehicle.getCreatedAt())
        .build();
  }
}
