package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleChangeRequest;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleChangeRequestResponse;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.entities.VehicleChangeRequest;
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
import com.uit.dormmanagement.services.VehicleChangeRequestService;
import com.uit.dormmanagement.services.VehicleEvidenceStorage;
import com.uit.dormmanagement.services.VehicleRegistrationValidator;
import java.time.Instant;
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
public class VehicleChangeRequestServiceImpl implements VehicleChangeRequestService {

  private final VehicleRepository vehicleRepository;
  private final VehicleChangeRequestRepository vehicleChangeRequestRepository;
  private final TenantRepository tenantRepository;
  private final StaffProfileRepository staffProfileRepository;
  private final VehicleRegistrationValidator vehicleRegistrationValidator;
  private final VehicleEvidenceStorage vehicleEvidenceStorage;

  @Override
  @Transactional
  public VehicleChangeRequestResponse create(
      UUID tenantUserId,
      CreateVehicleChangeRequest request,
      MultipartFile front,
      MultipartFile back) {

    Tenant tenant =
        tenantRepository
            .findByUserId(tenantUserId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    if (tenant.getResidentStatus() != ResidentStatus.ACTIVE) {
      throw new BaseAppException(TenantErrorCode.TENANT_NOT_ACTIVE);
    }

    Vehicle currentVehicle =
        vehicleRepository
            .findByIdForUpdate(request.currentVehicleId())
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    if (!currentVehicle.getTenant().getId().equals(tenant.getId())) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND);
    }

    if (currentVehicle.getStatus() != VehicleStatus.ISSUED
        || currentVehicle.getCardCode() == null
        || currentVehicle.getCardCode().isBlank()) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_INVALID_STATE_TRANSITION);
    }

    if (vehicleChangeRequestRepository.existsByTenantIdAndStatus(
        tenant.getId(), VehicleChangeRequestStatus.PENDING)) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_ALREADY_EXISTS);
    }

    VehicleRegistrationValidator.ValidatedVehicleRegistration validated =
        vehicleRegistrationValidator.validate(
            request.proposedVehicleType(),
            request.proposedLicensePlate(),
            request.proposedBrand(),
            request.proposedColor(),
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

      VehicleChangeRequest changeRequest =
          VehicleChangeRequest.builder()
              .tenant(tenant)
              .currentVehicle(currentVehicle)
              .proposedVehicleType(validated.vehicleType())
              .proposedLicensePlate(validated.licensePlate())
              .proposedBrand(validated.brand())
              .proposedColor(validated.color())
              .proposedRegistrationFrontFileKey(frontKey)
              .proposedRegistrationBackFileKey(backKey)
              .status(VehicleChangeRequestStatus.PENDING)
              .build();

      VehicleChangeRequest saved = vehicleChangeRequestRepository.saveAndFlush(changeRequest);
      log.info(
          "Tenant {} submitted vehicle change request {} for vehicle {}",
          tenant.getTenantCode(),
          saved.getId(),
          currentVehicle.getId());
      return toResponse(saved);
    } catch (Exception e) {
      if (frontKey != null) vehicleEvidenceStorage.delete(frontKey);
      if (backKey != null) vehicleEvidenceStorage.delete(backKey);
      if (e instanceof DataIntegrityViolationException) {
        throw new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_ALREADY_EXISTS);
      }
      if (e instanceof BaseAppException) {
        throw (BaseAppException) e;
      }
      throw e;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<VehicleChangeRequestResponse> getTenantRequests(UUID tenantUserId) {
    Tenant tenant =
        tenantRepository
            .findByUserId(tenantUserId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    return vehicleChangeRequestRepository
        .findByTenantIdOrderByCreatedAtDesc(tenant.getId())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public VehicleChangeRequestResponse getTenantRequestById(UUID tenantUserId, UUID requestId) {
    Tenant tenant =
        tenantRepository
            .findByUserId(tenantUserId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    VehicleChangeRequest request =
        vehicleChangeRequestRepository
            .findById(requestId)
            .orElseThrow(
                () -> new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_FOUND));

    if (!request.getTenant().getId().equals(tenant.getId())) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_FOUND);
    }

    return toResponse(request);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<VehicleChangeRequestResponse> getAdminRequests(
      Pageable pageable, String search, VehicleChangeRequestStatus status) {
    return vehicleChangeRequestRepository
        .findRequestsWithFilters(search, status, pageable)
        .map(this::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public VehicleChangeRequestResponse getAdminRequestById(UUID requestId) {
    VehicleChangeRequest request =
        vehicleChangeRequestRepository
            .findById(requestId)
            .orElseThrow(
                () -> new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_FOUND));
    return toResponse(request);
  }

  @Override
  @Transactional
  public VehicleChangeRequestResponse approve(UUID managerUserId, UUID requestId) {
    StaffProfile staff = null;
    if (managerUserId != null) {
      staff = staffProfileRepository.findByUserId(managerUserId).orElse(null);
    }

    UUID currentVehicleId =
        vehicleChangeRequestRepository
            .findCurrentVehicleIdById(requestId)
            .orElseThrow(
                () -> new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_FOUND));

    // Enforce lock ordering: Vehicle FIRST, then Request
    Vehicle currentVehicle =
        vehicleRepository
            .findByIdForUpdate(currentVehicleId)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    VehicleChangeRequest request =
        vehicleChangeRequestRepository
            .findByIdForUpdate(requestId)
            .orElseThrow(
                () -> new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_FOUND));

    // Recheck invariants under lock
    if (request.getStatus() != VehicleChangeRequestStatus.PENDING) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_PENDING);
    }

    if (currentVehicle.getStatus() != VehicleStatus.ISSUED) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_INVALID_STATE_TRANSITION);
    }

    if (!currentVehicle.getTenant().getId().equals(request.getTenant().getId())) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_INVALID_STATE_TRANSITION);
    }

    if (request.getTenant().getResidentStatus() != ResidentStatus.ACTIVE) {
      throw new BaseAppException(TenantErrorCode.TENANT_NOT_ACTIVE);
    }

    String preservedCardCode = currentVehicle.getCardCode();
    if (preservedCardCode == null || preservedCardCode.isBlank()) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_INVALID_STATE_TRANSITION);
    }

    // Step A: Mark old vehicle REPLACED and flush to release partial unique indexes
    currentVehicle.setStatus(VehicleStatus.REPLACED);
    currentVehicle.setReplacedAt(Instant.now());
    vehicleRepository.saveAndFlush(currentVehicle);

    // Step B: Create successor vehicle with same card code
    Vehicle successor =
        Vehicle.builder()
            .tenant(request.getTenant())
            .vehicleType(request.getProposedVehicleType())
            .licensePlate(request.getProposedLicensePlate())
            .brand(request.getProposedBrand())
            .color(request.getProposedColor())
            .cardCode(preservedCardCode)
            .status(VehicleStatus.ISSUED)
            .registrationFrontFileKey(request.getProposedRegistrationFrontFileKey())
            .registrationBackFileKey(request.getProposedRegistrationBackFileKey())
            .issuedAt(Instant.now())
            .replacedVehicle(currentVehicle)
            .build();

    Vehicle savedSuccessor = vehicleRepository.saveAndFlush(successor);

    // Step C: Link old vehicle to successor
    currentVehicle.setReplacedByVehicle(savedSuccessor);
    vehicleRepository.saveAndFlush(currentVehicle);

    // Step D: Mark Request APPROVED
    request.setStatus(VehicleChangeRequestStatus.APPROVED);
    request.setNewVehicle(savedSuccessor);
    request.setProcessedByStaff(staff);
    request.setProcessedAt(Instant.now());
    VehicleChangeRequest savedReq = vehicleChangeRequestRepository.saveAndFlush(request);

    log.info(
        "Manager approved vehicle change request {}: old vehicle {} replaced by {}",
        requestId,
        currentVehicle.getId(),
        savedSuccessor.getId());

    return toResponse(savedReq);
  }

  @Override
  @Transactional
  public VehicleChangeRequestResponse reject(UUID managerUserId, UUID requestId, String reason) {
    StaffProfile staff = null;
    if (managerUserId != null) {
      staff = staffProfileRepository.findByUserId(managerUserId).orElse(null);
    }

    VehicleChangeRequest request =
        vehicleChangeRequestRepository
            .findByIdForUpdate(requestId)
            .orElseThrow(
                () -> new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_FOUND));

    if (request.getStatus() != VehicleChangeRequestStatus.PENDING) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_PENDING);
    }

    request.setStatus(VehicleChangeRequestStatus.REJECTED);
    request.setRejectionReason(reason != null ? reason.trim() : null);
    request.setProcessedByStaff(staff);
    request.setProcessedAt(Instant.now());

    VehicleChangeRequest saved = vehicleChangeRequestRepository.save(request);
    log.info("Manager rejected vehicle change request: {}", requestId);
    return toResponse(saved);
  }

  private VehicleChangeRequestResponse toResponse(VehicleChangeRequest req) {
    return VehicleChangeRequestResponse.builder()
        .id(req.getId())
        .tenantId(req.getTenant().getId())
        .tenantName(req.getTenant().getFullName())
        .currentVehicleId(req.getCurrentVehicle().getId())
        .currentVehicleLicensePlate(req.getCurrentVehicle().getLicensePlate())
        .currentVehicleCardCode(req.getCurrentVehicle().getCardCode())
        .proposedVehicleType(req.getProposedVehicleType())
        .proposedLicensePlate(req.getProposedLicensePlate())
        .proposedBrand(req.getProposedBrand())
        .proposedColor(req.getProposedColor())
        .proposedFrontEvidenceAvailable(
            req.getProposedRegistrationFrontFileKey() != null
                && !req.getProposedRegistrationFrontFileKey().isBlank())
        .proposedBackEvidenceAvailable(
            req.getProposedRegistrationBackFileKey() != null
                && !req.getProposedRegistrationBackFileKey().isBlank())
        .status(req.getStatus())
        .rejectionReason(req.getRejectionReason())
        .processedByStaffId(
            req.getProcessedByStaff() != null ? req.getProcessedByStaff().getId() : null)
        .processedAt(req.getProcessedAt())
        .newVehicleId(req.getNewVehicle() != null ? req.getNewVehicle().getId() : null)
        .createdAt(req.getCreatedAt())
        .build();
  }
}
