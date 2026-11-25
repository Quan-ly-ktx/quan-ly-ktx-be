package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleChangeRequestRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.VehicleEvidenceStorage;
import com.uit.dormmanagement.services.VehicleRegistrationValidator;
import com.uit.dormmanagement.services.VehicleService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final StaffProfileRepository staffProfileRepository;
  private final VehicleChangeRequestRepository vehicleChangeRequestRepository;
  private final VehicleRegistrationValidator vehicleRegistrationValidator;
  private final VehicleEvidenceStorage vehicleEvidenceStorage;

  @Override
  @Transactional(readOnly = true)
  public Page<VehicleResponse> getVehicles(Pageable pageable, String search, VehicleStatus status) {
    return vehicleRepository
        .findVehiclesWithFilters(search, status, pageable)
        .map(this::toResponse);
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
  public VehicleResponse createVehicleAdmin(
      CreateVehicleAdminRequest request, MultipartFile front, MultipartFile back) {
    Tenant tenant =
        tenantRepository
            .findById(request.tenantId())
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    if (tenant.getResidentStatus() != ResidentStatus.ACTIVE) {
      throw new BaseAppException(TenantErrorCode.TENANT_NOT_ACTIVE);
    }

    validateVehicleQuota(tenant.getId());

    VehicleRegistrationValidator.ValidatedVehicleRegistration validated =
        vehicleRegistrationValidator.validate(
            request.vehicleType(),
            request.licensePlate(),
            request.brand(),
            request.color(),
            front,
            back);

    UUID storageId = UUID.randomUUID();
    String frontKey = null;
    String backKey = null;

    try {
      if (validated.frontEvidence() != null && !validated.frontEvidence().isEmpty()) {
        frontKey = vehicleEvidenceStorage.store(storageId, "front", validated.frontEvidence());
      }
      if (validated.backEvidence() != null && !validated.backEvidence().isEmpty()) {
        backKey = vehicleEvidenceStorage.store(storageId, "back", validated.backEvidence());
      }

      Vehicle vehicle =
          Vehicle.builder()
              .tenant(tenant)
              .vehicleType(validated.vehicleType())
              .licensePlate(validated.licensePlate())
              .brand(validated.brand())
              .color(validated.color())
              .status(VehicleStatus.PENDING_CARD)
              .cardCode(null)
              .registrationFrontFileKey(frontKey)
              .registrationBackFileKey(backKey)
              .build();

      Vehicle saved = vehicleRepository.saveAndFlush(vehicle);
      log.info("Admin created vehicle {} for tenant {}", saved.getId(), tenant.getTenantCode());
      return toResponse(saved);
    } catch (Exception e) {
      if (frontKey != null) vehicleEvidenceStorage.delete(frontKey);
      if (backKey != null) vehicleEvidenceStorage.delete(backKey);
      if (e instanceof DataIntegrityViolationException) {
        throw new BaseAppException(VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED);
      }
      if (e instanceof BaseAppException) {
        throw (BaseAppException) e;
      }
      throw e;
    }
  }

  @Override
  @Transactional
  public VehicleResponse createVehicleAdmin(CreateVehicleAdminRequest request) {
    return createVehicleAdmin(request, null, null);
  }

  @Override
  @Transactional
  public VehicleResponse issueCard(UUID managerUserId, UUID id, IssueCardRequest request) {
    Vehicle vehicle =
        vehicleRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    if (vehicle.getStatus() == VehicleStatus.ISSUED) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_ALREADY_ACTIVE);
    }
    if (vehicle.getStatus() != VehicleStatus.PENDING_CARD) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_NOT_PENDING);
    }

    if (vehicle.getTenant().getResidentStatus() != ResidentStatus.ACTIVE) {
      throw new BaseAppException(TenantErrorCode.TENANT_NOT_ACTIVE);
    }

    String cleanCardCode = request.cardCode() != null ? request.cardCode().trim().toUpperCase() : null;
    if (cleanCardCode == null || cleanCardCode.isBlank()) {
      throw new BaseAppException(VehicleErrorCode.CARD_CODE_DUPLICATED);
    }

    if (vehicleRepository.existsByCardCodeAndStatus(cleanCardCode, VehicleStatus.ISSUED)) {
      throw new BaseAppException(VehicleErrorCode.CARD_CODE_DUPLICATED);
    }

    vehicle.setStatus(VehicleStatus.ISSUED);
    vehicle.setCardCode(cleanCardCode);
    vehicle.setIssuedAt(Instant.now());

    Vehicle saved = vehicleRepository.saveAndFlush(vehicle);
    log.info("Issued card {} to vehicle {}", cleanCardCode, saved.getId());
    return toResponse(saved);
  }

  @Override
  @Transactional
  public VehicleResponse issueCard(UUID id, IssueCardRequest request) {
    return issueCard(null, id, request);
  }

  @Override
  @Transactional
  public VehicleResponse rejectVehicle(UUID managerUserId, UUID id, String reason) {
    Vehicle vehicle =
        vehicleRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    if (vehicle.getStatus() != VehicleStatus.PENDING_CARD) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_INVALID_STATE_TRANSITION);
    }

    StaffProfile staff = null;
    if (managerUserId != null) {
      staff = staffProfileRepository.findByUserId(managerUserId).orElse(null);
    }

    vehicle.setStatus(VehicleStatus.REJECTED);
    vehicle.setRejectionReason(reason != null ? reason.trim() : null);
    vehicle.setRejectedAt(Instant.now());
    vehicle.setRejectedByStaff(staff);

    Vehicle saved = vehicleRepository.save(vehicle);
    log.info("Rejected vehicle registration: {}", saved.getId());
    return toResponse(saved);
  }

  @Override
  @Transactional
  public VehicleResponse revokeCard(UUID managerUserId, UUID id) {
    Vehicle vehicle =
        vehicleRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    if (vehicle.getStatus() != VehicleStatus.ISSUED) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_INVALID_STATE_TRANSITION);
    }

    boolean hasPendingRequest =
        vehicleChangeRequestRepository.existsByCurrentVehicleIdAndStatus(
            id, VehicleChangeRequestStatus.PENDING);
    if (hasPendingRequest) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_HAS_PENDING_CHANGE_REQUEST);
    }

    vehicle.setStatus(VehicleStatus.REVOKED);
    vehicle.setRevokedAt(Instant.now());

    Vehicle saved = vehicleRepository.save(vehicle);
    log.info("Revoked card for vehicle {}", saved.getId());
    return toResponse(saved);
  }

  @Override
  @Transactional
  public VehicleResponse revokeCard(UUID id) {
    return revokeCard(null, id);
  }

  @Override
  @Transactional
  public void deleteVehicle(UUID id) {
    Vehicle vehicle =
        vehicleRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    if (vehicle.getStatus() != VehicleStatus.PENDING_CARD) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_INVALID_STATE_TRANSITION);
    }

    vehicle.setStatus(VehicleStatus.CANCELLED);
    vehicle.setCancelledAt(Instant.now());

    vehicleRepository.save(vehicle);
    log.info("Cancelled pending vehicle registration: {}", id);
  }

  @Override
  @Transactional(readOnly = true)
  public VehicleResponse getMyVehicle(UUID userId) {
    Tenant tenant =
        tenantRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    return vehicleRepository
        .findFirstByTenantIdAndStatusIn(
            tenant.getId(), List.of(VehicleStatus.PENDING_CARD, VehicleStatus.ISSUED))
        .map(this::toResponse)
        .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<VehicleResponse> getVehicleHistory(UUID userId) {
    Tenant tenant =
        tenantRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    return vehicleRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).stream()
        .map(this::toResponse)
        .toList();
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

    VehicleRegistrationValidator.ValidatedVehicleRegistration validated =
        vehicleRegistrationValidator.validate(
            request.vehicleType(),
            request.licensePlate(),
            request.brand(),
            request.color(),
            frontImage,
            backImage);

    UUID storageId = UUID.randomUUID();
    String frontKey = null;
    String backKey = null;

    try {
      if (validated.frontEvidence() != null && !validated.frontEvidence().isEmpty()) {
        frontKey = vehicleEvidenceStorage.store(storageId, "front", validated.frontEvidence());
      }
      if (validated.backEvidence() != null && !validated.backEvidence().isEmpty()) {
        backKey = vehicleEvidenceStorage.store(storageId, "back", validated.backEvidence());
      }

      Vehicle vehicle =
          Vehicle.builder()
              .tenant(tenant)
              .vehicleType(validated.vehicleType())
              .licensePlate(validated.licensePlate())
              .brand(validated.brand())
              .color(validated.color())
              .status(VehicleStatus.PENDING_CARD)
              .cardCode(null)
              .registrationFrontFileKey(frontKey)
              .registrationBackFileKey(backKey)
              .build();

      Vehicle saved = vehicleRepository.saveAndFlush(vehicle);
      log.info("Tenant {} registered vehicle {}", tenant.getTenantCode(), saved.getId());
      return toResponse(saved);
    } catch (Exception e) {
      if (frontKey != null) vehicleEvidenceStorage.delete(frontKey);
      if (backKey != null) vehicleEvidenceStorage.delete(backKey);
      if (e instanceof DataIntegrityViolationException) {
        throw new BaseAppException(VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED);
      }
      if (e instanceof BaseAppException) {
        throw (BaseAppException) e;
      }
      throw e;
    }
  }

  private void validateVehicleQuota(UUID tenantId) {
    long count =
        vehicleRepository.countByTenantIdAndStatusIn(
            tenantId, List.of(VehicleStatus.PENDING_CARD, VehicleStatus.ISSUED));
    if (count >= 1) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_QUOTA_EXCEEDED);
    }
  }

  private VehicleResponse toResponse(Vehicle vehicle) {
    return VehicleResponse.builder()
        .id(vehicle.getId())
        .tenantId(vehicle.getTenant().getId())
        .tenantName(vehicle.getTenant().getFullName())
        .vehicleType(vehicle.getVehicleType())
        .licensePlate(vehicle.getLicensePlate())
        .brand(vehicle.getBrand())
        .color(vehicle.getColor())
        .cardCode(vehicle.getCardCode())
        .status(vehicle.getStatus())
        .registrationFrontAvailable(
            vehicle.getRegistrationFrontFileKey() != null
                && !vehicle.getRegistrationFrontFileKey().isBlank())
        .registrationBackAvailable(
            vehicle.getRegistrationBackFileKey() != null
                && !vehicle.getRegistrationBackFileKey().isBlank())
        .rejectionReason(vehicle.getRejectionReason())
        .rejectedByStaffId(
            vehicle.getRejectedByStaff() != null ? vehicle.getRejectedByStaff().getId() : null)
        .rejectedAt(vehicle.getRejectedAt())
        .issuedAt(vehicle.getIssuedAt())
        .revokedAt(vehicle.getRevokedAt())
        .cancelledAt(vehicle.getCancelledAt())
        .replacedAt(vehicle.getReplacedAt())
        .replacedVehicleId(
            vehicle.getReplacedVehicle() != null ? vehicle.getReplacedVehicle().getId() : null)
        .replacedByVehicleId(
            vehicle.getReplacedByVehicle() != null
                ? vehicle.getReplacedByVehicle().getId()
                : null)
        .createdAt(vehicle.getCreatedAt())
        .build();
  }
}
