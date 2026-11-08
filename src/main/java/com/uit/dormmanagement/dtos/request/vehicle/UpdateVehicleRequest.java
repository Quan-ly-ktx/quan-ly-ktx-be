package com.uit.dormmanagement.dtos.request.vehicle;

import com.uit.dormmanagement.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record UpdateVehicleRequest(
    @NotNull(message = "Loại xe không được để trống") VehicleType vehicleType,
    @NotBlank(message = "Biển số xe không được để trống") String licensePlate,
    @NotBlank(message = "Hãng xe không được để trống") String brand,
    @NotBlank(message = "Màu xe không được để trống") String color) {}
