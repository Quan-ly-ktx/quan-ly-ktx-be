package com.uit.dormmanagement.dtos.response.vehicle;

import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record VehicleResponse(
    UUID id,
    UUID tenantId,
    String tenantName,
    VehicleType vehicleType,
    String licensePlate,
    String brand,
    String color,
    String cardCode,
    VehicleStatus status,
    boolean registrationFrontAvailable,
    boolean registrationBackAvailable,
    String rejectionReason,
    UUID rejectedByStaffId,
    Instant rejectedAt,
    Instant issuedAt,
    Instant revokedAt,
    Instant cancelledAt,
    Instant replacedAt,
    UUID replacedVehicleId,
    UUID replacedByVehicleId,
    Instant createdAt) {}
