package com.uit.dormmanagement.dtos.request.auth;

import jakarta.validation.constraints.NotNull;

public record UpdateTechnicianStatusRequest(
    @NotNull(message = "Trạng thái hoạt động không được để trống") Boolean active) {}
