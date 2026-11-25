package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.auth.ChangePasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.LoginRequest;
import com.uit.dormmanagement.dtos.request.auth.PasswordRecoveryRequest;
import com.uit.dormmanagement.dtos.request.auth.RefreshTokenRequest;
import com.uit.dormmanagement.dtos.request.auth.ResetPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.VerifyRecoveryOtpRequest;
import com.uit.dormmanagement.dtos.response.auth.AuthResponse;
import com.uit.dormmanagement.dtos.response.auth.PasswordRecoveryRequestedResponse;
import com.uit.dormmanagement.dtos.response.auth.VerifyResetPasswordOtpResponse;
import java.util.UUID;

public interface AuthService {

  AuthResponse login(LoginRequest request);

  AuthResponse refreshToken(RefreshTokenRequest request);

  void logout(String authHeader, RefreshTokenRequest request);

  void logoutAll(String authHeader);

  void changePassword(UUID userId, ChangePasswordRequest request);

  PasswordRecoveryRequestedResponse forgotPassword(PasswordRecoveryRequest request);

  VerifyResetPasswordOtpResponse verifyForgotPasswordOtp(VerifyRecoveryOtpRequest request);

  void resetPassword(ResetPasswordRequest request);
}
