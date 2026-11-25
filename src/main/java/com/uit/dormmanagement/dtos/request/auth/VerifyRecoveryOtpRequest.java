package com.uit.dormmanagement.dtos.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record VerifyRecoveryOtpRequest(
    @NotNull(message = "Mã yêu cầu khôi phục không được để trống")
    UUID recoveryRequestId,

    @NotBlank(message = "Mã OTP không được để trống")
    @Pattern(regexp = "^\\d{6}$", message = "Mã OTP phải gồm 6 chữ số")
    String otp
) {}
