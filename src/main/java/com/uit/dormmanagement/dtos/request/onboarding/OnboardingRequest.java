package com.uit.dormmanagement.dtos.request.onboarding;

import com.uit.dormmanagement.enums.GenderPolicy;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OnboardingRequest(
    @NotBlank(message = "Họ tên không được để trống")
        @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
        String fullName,
    @NotNull(message = "Chính sách giới tính không được để trống") GenderPolicy gender,
    @NotBlank(message = "Số CCCD/CMND không được để trống")
        @Pattern(regexp = "^[0-9]{12}$", message = "Số CCCD phải đúng 12 chữ số")
        String idCardNumber,
    @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(
            regexp = "^0[0-9]{9}$",
            message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng số 0")
        String phone,
    @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        String email,
    @NotBlank(message = "Địa chỉ thường trú không được để trống") String permanentAddress,
    LocalDate dateOfBirth,
    String emergencyContactName,
    @Pattern(
            regexp = "^0[0-9]{9}$",
            message =
                "Số điện thoại người liên hệ khẩn cấp phải gồm 10 chữ số và bắt đầu bằng số 0")
        String emergencyContactPhone,
    @NotNull(message = "ID giường không được để trống") UUID bedId,
    @NotNull(message = "Ngày bắt đầu hợp đồng không được để trống") LocalDate startDate,
    @NotNull(message = "Ngày kết thúc hợp đồng không được để trống") LocalDate endDate,
    @NotNull(message = "Tiền đặt cọc không được để trống")
        @DecimalMin(value = "0.0", message = "Tiền đặt cọc không được âm")
        BigDecimal depositAmount) {}
