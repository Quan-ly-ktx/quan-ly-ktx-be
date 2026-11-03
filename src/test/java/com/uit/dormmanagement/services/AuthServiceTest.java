package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.auth.ChangePasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.ForgotPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.LoginRequest;
import com.uit.dormmanagement.dtos.request.auth.RefreshTokenRequest;
import com.uit.dormmanagement.dtos.request.auth.ResetPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.VerifyOtpRequest;
import com.uit.dormmanagement.dtos.response.auth.AuthResponse;
import com.uit.dormmanagement.dtos.response.auth.VerifyResetPasswordOtpResponse;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.AuthErrorCode;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.security.JwtService;
import com.uit.dormmanagement.security.TokenPayload;
import com.uit.dormmanagement.services.impl.AuthServiceImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private StaffProfileRepository staffProfileRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private LoginAttemptService loginAttemptService;
  @Mock private TokenRevocationService tokenRevocationService;
  @Mock private OtpService otpService;
  @Mock private EmailService emailService;

  @InjectMocks private AuthServiceImpl authService;

  private User testUser;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    testUser =
        User.builder()
            .id(userId)
            .username("manager01")
            .email("manager@dorm.vn")
            .phone("0901000001")
            .passwordHash("$2a$12$hashedPassword")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .build();
  }

  @Test
  @DisplayName("login should succeed with valid credentials and return AuthResponse")
  void shouldLoginSuccessfully() {
    LoginRequest request = new LoginRequest("manager01", "Password@123");

    when(loginAttemptService.isLocked("manager01")).thenReturn(false);
    when(userRepository.findByUsername("manager01")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("Password@123", testUser.getPasswordHash())).thenReturn(true);
    when(jwtService.generateAccessToken(eq(testUser), anyString())).thenReturn("mock-access-token");
    when(jwtService.generateRefreshToken(eq(testUser), anyString()))
        .thenReturn("mock-refresh-token");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(86400L);
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

    AuthResponse response = authService.login(request);

    assertThat(response.accessToken()).isEqualTo("mock-access-token");
    assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.user().username()).isEqualTo("manager01");

    verify(loginAttemptService).resetAttempts("manager01");
    verify(tokenRevocationService)
        .recordRefreshToken(eq(userId), anyString(), eq("mock-refresh-token"), eq(604800L));
  }

  @Test
  @DisplayName(
      "login should throw ACCOUNT_TEMPORARILY_LOCKED when identifier is locked by rate limit")
  void shouldRejectLoginWhenAccountIsTemporarilyLocked() {
    LoginRequest request = new LoginRequest("manager01", "Password@123");
    when(loginAttemptService.isLocked("manager01")).thenReturn(true);

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_TEMPORARILY_LOCKED);

    verify(userRepository, never()).findByUsername(anyString());
  }

  @Test
  @DisplayName("login should throw ACCOUNT_LOCKED when user status is LOCKED by admin")
  void shouldRejectLoginWhenUserStatusIsLockedByAdmin() {
    LoginRequest request = new LoginRequest("manager01", "Password@123");
    testUser.setStatus(UserStatus.LOCKED);

    when(loginAttemptService.isLocked("manager01")).thenReturn(false);
    when(userRepository.findByUsername("manager01")).thenReturn(Optional.of(testUser));

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_LOCKED);
  }

  @Test
  @DisplayName(
      "login should record failed attempt and throw INVALID_CREDENTIALS when password fails")
  void shouldRejectLoginWhenPasswordMismatch() {
    LoginRequest request = new LoginRequest("manager01", "WrongPassword");

    when(loginAttemptService.isLocked("manager01")).thenReturn(false);
    when(userRepository.findByUsername("manager01")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("WrongPassword", testUser.getPasswordHash())).thenReturn(false);

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);

    verify(loginAttemptService).recordFailedAttempt("manager01");
  }

  @Test
  @DisplayName("refreshToken should rotate tokens when refresh token is valid")
  void shouldRefreshTokenSuccessfully() {
    String oldRefreshToken = "valid-refresh-token";
    RefreshTokenRequest request = new RefreshTokenRequest(oldRefreshToken);
    String jti = "refresh-jti-1";

    TokenPayload payload =
        new TokenPayload(
            userId,
            "manager01",
            "manager@dorm.vn",
            "MANAGER",
            jti,
            "REFRESH",
            Instant.now(),
            Instant.now().plusSeconds(604800));

    when(jwtService.parseAndValidateToken(oldRefreshToken)).thenReturn(payload);
    when(tokenRevocationService.isRefreshTokenValid(userId, jti)).thenReturn(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
    when(jwtService.generateAccessToken(eq(testUser), anyString())).thenReturn("new-access-token");
    when(jwtService.generateRefreshToken(eq(testUser), anyString()))
        .thenReturn("new-refresh-token");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(86400L);
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

    AuthResponse response = authService.refreshToken(request);

    assertThat(response.accessToken()).isEqualTo("new-access-token");
    assertThat(response.refreshToken()).isEqualTo("new-refresh-token");

    verify(tokenRevocationService).revokeRefreshToken(userId, jti);
    verify(tokenRevocationService)
        .recordRefreshToken(eq(userId), anyString(), eq("new-refresh-token"), anyLong());
  }

  @Test
  @DisplayName("refreshToken should revoke all sessions when refresh token is invalid or reused")
  void shouldRevokeAllSessionsOnTokenReuse() {
    String oldRefreshToken = "reused-token";
    RefreshTokenRequest request = new RefreshTokenRequest(oldRefreshToken);
    String jti = "reused-jti";

    TokenPayload payload =
        new TokenPayload(
            userId,
            "manager01",
            "manager@dorm.vn",
            "MANAGER",
            jti,
            "REFRESH",
            Instant.now(),
            Instant.now().plusSeconds(604800));

    when(jwtService.parseAndValidateToken(oldRefreshToken)).thenReturn(payload);
    when(tokenRevocationService.isRefreshTokenValid(userId, jti)).thenReturn(false);

    assertThatThrownBy(() -> authService.refreshToken(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TOKEN_REVOKED);

    verify(tokenRevocationService).revokeAllUserSessions(userId);
  }

  @Test
  @DisplayName("changePassword should update password and revoke all sessions")
  void shouldChangePasswordSuccessfully() {
    ChangePasswordRequest request =
        new ChangePasswordRequest("OldPassword@123", "NewPassword@123", "NewPassword@123");

    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("OldPassword@123", testUser.getPasswordHash())).thenReturn(true);
    when(passwordEncoder.matches("NewPassword@123", testUser.getPasswordHash())).thenReturn(false);
    when(passwordEncoder.encode("NewPassword@123")).thenReturn("$2a$12$newHash");

    authService.changePassword(userId, request);

    assertThat(testUser.getPasswordHash()).isEqualTo("$2a$12$newHash");
    verify(userRepository).save(testUser);
    verify(tokenRevocationService).revokeAllUserSessions(userId);
  }

  @Test
  @DisplayName("forgotPassword should generate OTP and send email when user exists")
  void shouldSendForgotPasswordOtp() {
    ForgotPasswordRequest request = new ForgotPasswordRequest("manager@dorm.vn");
    when(userRepository.findByEmail("manager@dorm.vn")).thenReturn(Optional.of(testUser));
    when(otpService.generateAndStoreOtp("manager@dorm.vn")).thenReturn("654321");
    when(staffProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

    authService.forgotPassword(request);

    verify(otpService).checkCooldown("manager@dorm.vn");
    verify(otpService).generateAndStoreOtp("manager@dorm.vn");
    verify(emailService).sendForgotPasswordOtp(eq("manager@dorm.vn"), anyString(), eq("654321"));
  }

  @Test
  @DisplayName("verifyForgotPasswordOtp should return reset token on valid OTP")
  void shouldVerifyOtpAndReturnResetToken() {
    VerifyOtpRequest request = new VerifyOtpRequest("manager@dorm.vn", "123456");
    when(otpService.verifyOtp("manager@dorm.vn", "123456")).thenReturn(true);
    when(otpService.generateAndStorePasswordResetToken("manager@dorm.vn"))
        .thenReturn("reset-tok-xyz");

    VerifyResetPasswordOtpResponse response = authService.verifyForgotPasswordOtp(request);

    assertThat(response.resetToken()).isEqualTo("reset-tok-xyz");
  }

  @Test
  @DisplayName("resetPassword should update password and revoke all sessions")
  void shouldResetPasswordSuccessfully() {
    ResetPasswordRequest request =
        new ResetPasswordRequest("manager@dorm.vn", "reset-token", "NewPass@123", "NewPass@123");

    when(otpService.verifyPasswordResetToken("manager@dorm.vn", "reset-token")).thenReturn(true);
    when(userRepository.findByEmail("manager@dorm.vn")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("NewPass@123", testUser.getPasswordHash())).thenReturn(false);
    when(passwordEncoder.encode("NewPass@123")).thenReturn("$2a$12$resetHash");

    authService.resetPassword(request);

    assertThat(testUser.getPasswordHash()).isEqualTo("$2a$12$resetHash");
    verify(userRepository).save(testUser);
    verify(tokenRevocationService).revokeAllUserSessions(userId);
  }
}
