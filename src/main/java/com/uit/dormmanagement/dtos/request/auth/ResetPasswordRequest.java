package com.uit.dormmanagement.dtos.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        String email,
    @NotBlank(message = "Reset token không được để trống") String resetToken,
    @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 64, message = "Mật khẩu mới phải có độ dài từ 8 đến 64 ký tự")
        String newPassword,
    @NotBlank(message = "Xác nhận mật khẩu không được để trống") String confirmPassword) {}
