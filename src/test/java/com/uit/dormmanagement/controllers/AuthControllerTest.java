package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.dormmanagement.dtos.request.auth.LoginRequest;
import com.uit.dormmanagement.dtos.request.auth.PasswordRecoveryRequest;
import com.uit.dormmanagement.dtos.request.auth.RefreshTokenRequest;
import com.uit.dormmanagement.dtos.request.auth.ResetPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.VerifyRecoveryOtpRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.auth.AuthResponse;
import com.uit.dormmanagement.dtos.response.auth.PasswordRecoveryRequestedResponse;
import com.uit.dormmanagement.dtos.response.auth.UserResponse;
import com.uit.dormmanagement.dtos.response.auth.VerifyResetPasswordOtpResponse;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.services.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Mock private AuthService authService;

  @InjectMocks private AuthController authController;

  @BeforeEach
  void setUp() {
    ResponseFactory responseFactory = new ResponseFactory();
    authController.setResponseFactory(responseFactory);
    objectMapper = new ObjectMapper();

    mockMvc =
        MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();
  }

  @Test
  @DisplayName("POST /api/v1/auth/login should return 200 with AuthResponse on valid credentials")
  void shouldLoginSuccessfully() throws Exception {
    LoginRequest request = new LoginRequest("manager", "Password@123");
    UserResponse userResponse =
        new UserResponse(UUID.randomUUID(), "manager@dorm.vn", UserRole.MANAGER, UserStatus.ACTIVE);
    AuthResponse authResponse =
        new AuthResponse("access-token-xyz", "refresh-token-xyz", "Bearer", 86400L, userResponse);

    when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
        .andExpect(jsonPath("$.data.accessToken").value("access-token-xyz"))
        .andExpect(jsonPath("$.data.user.email").value("manager@dorm.vn"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/login should return 400 when identifier or password is blank")
  void shouldFailValidationOnBlankCredentials() throws Exception {
    LoginRequest request = new LoginRequest("", "");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400));
  }

  @Test
  @DisplayName("POST /api/v1/auth/refresh-token should return 200 with new AuthResponse")
  void shouldRefreshTokenSuccessfully() throws Exception {
    RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
    UserResponse userResponse =
        new UserResponse(UUID.randomUUID(), "manager@dorm.vn", UserRole.MANAGER, UserStatus.ACTIVE);
    AuthResponse authResponse =
        new AuthResponse("new-access-token", "new-refresh-token", "Bearer", 86400L, userResponse);

    when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(authResponse);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/logout should return 200 and invoke authService.logout")
  void shouldLogoutSuccessfully() throws Exception {
    RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Đăng xuất thành công"));

    verify(authService).logout(eq("Bearer some-access-token"), any(RefreshTokenRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/auth/logout without body should return 400 Bad Request")
  void shouldRejectLogoutWithoutBody() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some-access-token"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/auth/logout-all should return 200 and invoke authService.logoutAll")
  void shouldLogoutAllSuccessfully() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some-access-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Đăng xuất tất cả các phiên thành công"));

    verify(authService).logoutAll(anyString());
  }

  @Test
  @DisplayName("POST /api/v1/auth/forgot-password should return 200 on valid identifier")
  void shouldHandleForgotPassword() throws Exception {
    PasswordRecoveryRequest request = new PasswordRecoveryRequest("user@dorm.vn");
    UUID reqId = UUID.randomUUID();
    PasswordRecoveryRequestedResponse response = new PasswordRecoveryRequestedResponse(reqId, 300L);

    when(authService.forgotPassword(any(PasswordRecoveryRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.recoveryRequestId").value(reqId.toString()));

    verify(authService).forgotPassword(any(PasswordRecoveryRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/auth/verify-forgot-password-otp should return reset token")
  void shouldVerifyOtpSuccessfully() throws Exception {
    UUID reqId = UUID.randomUUID();
    VerifyRecoveryOtpRequest request = new VerifyRecoveryOtpRequest(reqId, "123456");
    VerifyResetPasswordOtpResponse response = new VerifyResetPasswordOtpResponse("reset-token-abc");

    when(authService.verifyForgotPasswordOtp(any(VerifyRecoveryOtpRequest.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/auth/verify-forgot-password-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resetToken").value("reset-token-abc"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/reset-password should return 200 on successful password reset")
  void shouldResetPasswordSuccessfully() throws Exception {
    ResetPasswordRequest request =
        new ResetPasswordRequest("reset-token", "NewPassword@123", "NewPassword@123");

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200));

    verify(authService).resetPassword(any(ResetPasswordRequest.class));
  }
}
