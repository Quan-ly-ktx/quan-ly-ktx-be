package com.uit.dormmanagement.dtos.request.tenant;

import com.uit.dormmanagement.enums.GenderPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record ManagerUpdateTenantRequest(
    @NotBlank(message = "Họ và tên không được để trống") String fullName,
    @Pattern(regexp = "^[0-9]{12}$", message = "Số CCCD phải có đúng 12 chữ số")
        String idCardNumber,
    GenderPolicy gender,
    @Email(message = "Email không đúng định dạng") String email,
    @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "Số điện thoại không đúng định dạng")
        String phone,
    @NotBlank(message = "Địa chỉ thường trú không được để trống") String permanentAddress,
    LocalDate dateOfBirth,
    String emergencyContactName,
    @Pattern(
            regexp = "^$|^(0|\\+84)[0-9]{9}$",
            message = "Số điện thoại liên hệ khẩn cấp không đúng định dạng")
        String emergencyContactPhone) {}
