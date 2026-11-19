package com.uit.dormmanagement.dtos.request.vehicle;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RejectVehicleChangeRequest(
    @NotBlank(message = "Lý do từ chối không được để trống") String reason
) {}
