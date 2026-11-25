package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import java.util.UUID;

public interface VehicleEvidenceAccessService {
  VehicleEvidenceContent loadVehicleEvidence(
      UUID actorUserId, boolean isManager, UUID vehicleId, String side);

  VehicleEvidenceContent loadChangeRequestEvidence(
      UUID actorUserId, boolean isManager, UUID requestId, String side);
}
