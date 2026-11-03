package com.uit.dormmanagement.dtos.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "Tên đăng nhập, email hoặc số điện thoại không được để trống")
        @Size(max = 255, message = "Định danh không được vượt quá 255 ký tự")
        String identifier,
    @NotBlank(message = "Mật khẩu không được để trống")
        @Size(max = 128, message = "Mật khẩu không được vượt quá 128 ký tự")
        String password) {}
