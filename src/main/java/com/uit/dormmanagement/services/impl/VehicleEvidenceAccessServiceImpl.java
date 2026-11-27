package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleChangeRequestRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.VehicleEvidenceAccessService;
import com.uit.dormmanagement.services.VehicleEvidenceStorage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleEvidenceAccessServiceImpl implements VehicleEvidenceAccessService {

  private final VehicleRepository vehicleRepository;
  private final VehicleChangeRequestRepository vehicleChangeRequestRepository;
  private final TenantRepository tenantRepository;
  private final VehicleEvidenceStorage vehicleEvidenceStorage;

  @Override
  @Transactional(readOnly = true)
  public VehicleEvidenceContent loadVehicleEvidence(
      UUID actorUserId, boolean isManager, UUID vehicleId, String side) {

    Vehicle vehicle =
        vehicleRepository
            .findById(vehicleId)
            .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

    if (!isManager) {
      Tenant callerTenant =
          tenantRepository
              .findByUserId(actorUserId)
              .orElseThrow(() -> new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND));

      if (!vehicle.getTenant().getId().equals(callerTenant.getId())) {
        // Return 404 to avoid disclosing other tenants' vehicles
        throw new BaseAppException(VehicleErrorCode.VEHICLE_NOT_FOUND);
      }
    }

    String key;
    if ("front".equalsIgnoreCase(side)) {
      key = vehicle.getRegistrationFrontFileKey();
    } else if ("back".equalsIgnoreCase(side)) {
      key = vehicle.getRegistrationBackFileKey();
    } else {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }

    if (key == null || key.isBlank()) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }

    return vehicleEvidenceStorage.load(key);
  }

  @Override
  @Transactional(readOnly = true)
  public VehicleEvidenceContent loadChangeRequestEvidence(
      UUID actorUserId, boolean isManager, UUID requestId, String side) {

    VehicleChangeRequest request =
        vehicleChangeRequestRepository
            .findById(requestId)
            .orElseThrow(
                () -> new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_FOUND));

    if (!isManager) {
      Tenant callerTenant =
          tenantRepository
              .findByUserId(actorUserId)
              .orElseThrow(
                  () -> new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_FOUND));

      if (!request.getTenant().getId().equals(callerTenant.getId())) {
        // Return 404 to avoid disclosing other tenants' change requests
        throw new BaseAppException(VehicleErrorCode.VEHICLE_CHANGE_REQUEST_NOT_FOUND);
      }
    }

    String key;
    if ("front".equalsIgnoreCase(side)) {
      key = request.getProposedRegistrationFrontFileKey();
    } else if ("back".equalsIgnoreCase(side)) {
      key = request.getProposedRegistrationBackFileKey();
    } else {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }

    if (key == null || key.isBlank()) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }

    return vehicleEvidenceStorage.load(key);
  }
}
