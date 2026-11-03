package com.uit.dormmanagement.dtos.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTechnicianRequest(
    @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
        String fullName,
    @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        String email,
    @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(
            regexp = "^0[0-9]{9}$",
            message = "Số điện thoại phải gồm 10 chữ số bắt đầu bằng số 0")
        String phone,
    @NotBlank(message = "Mật khẩu khởi tạo không được để trống")
        @Size(min = 6, max = 64, message = "Mật khẩu khởi tạo cần tối thiểu 6 ký tự")
        String password) {}
