package com.uit.dormmanagement.dtos.request.auth;

import jakarta.validation.constraints.NotBlank;

public record PasswordRecoveryRequest(
    @NotBlank(message = "Tên đăng nhập hoặc định danh không được để trống")
    String identifier
) {}
