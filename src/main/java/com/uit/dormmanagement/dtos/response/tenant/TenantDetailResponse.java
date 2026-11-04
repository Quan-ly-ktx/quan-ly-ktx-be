package com.uit.dormmanagement.dtos.response.tenant;

import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractSummaryResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record TenantDetailResponse(
    UUID id,
    UUID userId,
    String tenantCode,
    String fullName,
    GenderPolicy gender,
    String idCardNumber,
    String phone,
    String email,
    String permanentAddress,
    LocalDate dateOfBirth,
    String emergencyContactName,
    String emergencyContactPhone,
    ResidentStatus residentStatus,
    UUID currentBedId,
    String currentBedCode,
    UUID currentRoomId,
    String currentRoomNumber,
    ContractDetailResponse currentContract,
    List<VehicleResponse> vehicles,
    List<ContractSummaryResponse> contractHistory,
    Instant createdAt) {

  public String getCurrentRoomNumber() {
    return currentRoomNumber;
  }
}
