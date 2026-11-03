package com.uit.dormmanagement.dtos.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "Mật khẩu hiện tại không được để trống") String oldPassword,
    @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 64, message = "Mật khẩu mới phải có độ dài từ 8 đến 64 ký tự")
        String newPassword,
    @NotBlank(message = "Xác nhận mật khẩu không được để trống") String confirmPassword) {}
