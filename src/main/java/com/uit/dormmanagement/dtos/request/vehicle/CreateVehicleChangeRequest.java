package com.uit.dormmanagement.dtos.request.vehicle;

import com.uit.dormmanagement.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CreateVehicleChangeRequest(
    @NotNull(message = "ID phương tiện hiện tại không được để trống") UUID currentVehicleId,
    @NotNull(message = "Loại xe đề xuất không được để trống") VehicleType proposedVehicleType,
    String proposedLicensePlate,
    @NotBlank(message = "Hãng xe đề xuất không được để trống") String proposedBrand,
    @NotBlank(message = "Màu xe đề xuất không được để trống") String proposedColor) {}
