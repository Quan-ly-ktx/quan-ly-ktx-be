package com.uit.dormmanagement.dtos.response.vehicle;

import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import com.uit.dormmanagement.enums.VehicleType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record VehicleChangeRequestResponse(
    UUID id,
    UUID tenantId,
    String tenantName,
    UUID currentVehicleId,
    String currentVehicleLicensePlate,
    String currentVehicleCardCode,
    VehicleType proposedVehicleType,
    String proposedLicensePlate,
    String proposedBrand,
    String proposedColor,
    boolean proposedFrontEvidenceAvailable,
    boolean proposedBackEvidenceAvailable,
    VehicleChangeRequestStatus status,
    String rejectionReason,
    UUID processedByStaffId,
    Instant processedAt,
    UUID newVehicleId,
    Instant createdAt
) {}
