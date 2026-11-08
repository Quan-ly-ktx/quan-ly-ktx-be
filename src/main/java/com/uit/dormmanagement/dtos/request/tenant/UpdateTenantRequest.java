package com.uit.dormmanagement.dtos.request.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record UpdateTenantRequest(
    @NotBlank(message = "Họ tên không được để trống")
        @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
        String fullName,
    @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(
            regexp = "^0[0-9]{9}$",
            message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng số 0")
        String phone,
    String permanentAddress,
    LocalDate dateOfBirth,
    String emergencyContactName,
    @Pattern(
            regexp = "^0[0-9]{9}$",
            message =
                "Số điện thoại người liên hệ khẩn cấp phải gồm 10 chữ số và bắt đầu bằng số 0")
        String emergencyContactPhone) {}
