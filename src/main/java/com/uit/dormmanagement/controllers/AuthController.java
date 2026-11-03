package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.auth.ChangePasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.ForgotPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.LoginRequest;
import com.uit.dormmanagement.dtos.request.auth.RefreshTokenRequest;
import com.uit.dormmanagement.dtos.request.auth.ResetPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.VerifyOtpRequest;
import com.uit.dormmanagement.dtos.response.auth.AuthResponse;
import com.uit.dormmanagement.dtos.response.auth.VerifyResetPasswordOtpResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.base.SuccessResponse;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Authentication",
    description = "User authentication, JWT token lifecycle, and password recovery")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController extends AbstractBaseController {

  private final AuthService authService;

  @Operation(
      summary = "User login",
      description = "Authenticate using username, email, or phone with password")
  @PostMapping("/login")
  public ResponseEntity<SingleResponse<AuthResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return success(response, "Đăng nhập thành công");
  }

  @Operation(
      summary = "Refresh access token",
      description = "Obtain a new access token using a valid refresh token")
  @PostMapping("/refresh-token")
  public ResponseEntity<SingleResponse<AuthResponse>> refreshToken(
      @Valid @RequestBody RefreshTokenRequest request) {
    AuthResponse response = authService.refreshToken(request);
    return success(response, "Cấp lại token thành công");
  }

  @Operation(
      summary = "User logout",
      description = "Blacklist the current access token and revoke the refresh token")
  @PostMapping("/logout")
  public ResponseEntity<SuccessResponse> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @Valid @RequestBody(required = false) RefreshTokenRequest request) {
    authService.logout(authHeader, request);
    return success("Đăng xuất thành công");
  }

  @Operation(
      summary = "Logout all sessions",
      description = "Revoke all active sessions and refresh tokens for the current user")
  @PostMapping("/logout-all")
  public ResponseEntity<SuccessResponse> logoutAll(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    authService.logoutAll(authHeader);
    return success("Đăng xuất tất cả các phiên thành công");
  }

  @Operation(
      summary = "Change password",
      description = "Change password for the authenticated user")
  @PostMapping("/change-password")
  public ResponseEntity<SuccessResponse> changePassword(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(principal.getId(), request);
    return success("Đổi mật khẩu thành công. Vui lòng đăng nhập lại");
  }

  @Operation(
      summary = "Request password reset OTP",
      description = "Send a 6-digit OTP code to the registered email")
  @PostMapping("/forgot-password")
  public ResponseEntity<SuccessResponse> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return success("Nếu email tồn tại trong hệ thống, mã OTP đã được gửi đến hộp thư");
  }

  @Operation(
      summary = "Verify password reset OTP",
      description = "Verify 6-digit OTP and obtain a single-use reset token")
  @PostMapping("/verify-forgot-password-otp")
  public ResponseEntity<SingleResponse<VerifyResetPasswordOtpResponse>> verifyForgotPasswordOtp(
      @Valid @RequestBody VerifyOtpRequest request) {
    VerifyResetPasswordOtpResponse response = authService.verifyForgotPasswordOtp(request);
    return success(response, "Xác thực mã OTP thành công");
  }

  @Operation(
      summary = "Reset password",
      description = "Reset password using the single-use reset token")
  @PostMapping("/reset-password")
  public ResponseEntity<SuccessResponse> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return success("Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới");
  }
}
