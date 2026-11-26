package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uit.dormmanagement.dtos.request.auth.ChangePasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.LoginRequest;
import com.uit.dormmanagement.dtos.request.auth.PasswordRecoveryRequest;
import com.uit.dormmanagement.dtos.request.auth.RefreshTokenRequest;
import com.uit.dormmanagement.dtos.request.auth.ResetPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.VerifyRecoveryOtpRequest;
import com.uit.dormmanagement.dtos.response.auth.AuthResponse;
import com.uit.dormmanagement.dtos.response.auth.PasswordRecoveryRequestedResponse;
import com.uit.dormmanagement.dtos.response.auth.VerifyResetPasswordOtpResponse;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
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
  @Mock private UserTokenInvalidationService userTokenInvalidationService;
  @Mock private PasswordRecoveryService passwordRecoveryService;

  @InjectMocks private AuthServiceImpl authService;

  private User testUser;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    testUser =
        User.builder()
            .id(userId)
            .email("manager@dorm.vn")
            .passwordHash("$2a$12$hashedPassword")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .build();
  }

  @Test
  @DisplayName("login should succeed with valid credentials and return AuthResponse")
  void shouldLoginSuccessfully() {
    LoginRequest request = new LoginRequest("manager@dorm.vn", "Password@123");

    when(loginAttemptService.isLocked("manager@dorm.vn")).thenReturn(false);
    when(userRepository.findByEmail("manager@dorm.vn")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("Password@123", testUser.getPasswordHash())).thenReturn(true);
    when(userTokenInvalidationService.getOrCreateGeneration(userId)).thenReturn("gen-123");
    when(jwtService.generateAccessToken(eq(testUser), anyString(), eq("gen-123")))
        .thenReturn("mock-access-token");
    when(jwtService.generateRefreshToken(eq(testUser), anyString(), eq("gen-123")))
        .thenReturn("mock-refresh-token");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(86400L);
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

    AuthResponse response = authService.login(request);

    assertThat(response.accessToken()).isEqualTo("mock-access-token");
    assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.user().email()).isEqualTo("manager@dorm.vn");

    verify(loginAttemptService).resetAttempts("manager@dorm.vn");
    verify(tokenRevocationService).recordRefreshToken(eq(userId), anyString(), eq(604800L));
  }

  @Test
  @DisplayName("tenant login should resolve the account through the unique CCCD")
  void tenantLoginShouldResolveCccdToUser() {
    User tenantUser =
        User.builder()
            .id(userId)
            .email("tenant@example.test")
            .passwordHash("tenant-hash")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();
    Tenant tenant =
        Tenant.builder()
            .user(tenantUser)
            .idCardNumber("079203001187")
            .residentStatus(ResidentStatus.ACTIVE)
            .gender(GenderPolicy.MALE)
            .build();

    when(loginAttemptService.isLocked("079203001187")).thenReturn(false);
    when(userRepository.findByEmail("079203001187")).thenReturn(Optional.empty());
    when(tenantRepository.findByIdCardNumber("079203001187")).thenReturn(Optional.of(tenant));
    when(passwordEncoder.matches("Password@123", "tenant-hash")).thenReturn(true);
    when(userTokenInvalidationService.getOrCreateGeneration(userId)).thenReturn("gen-123");
    when(jwtService.generateAccessToken(eq(tenantUser), anyString(), eq("gen-123")))
        .thenReturn("access");
    when(jwtService.generateRefreshToken(eq(tenantUser), anyString(), eq("gen-123")))
        .thenReturn("refresh");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(86400L);
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

    AuthResponse response = authService.login(new LoginRequest("079203001187", "Password@123"));

    assertThat(response.user().email()).isEqualTo("tenant@example.test");
    verify(loginAttemptService).resetAttempts("079203001187");
  }

  @Test
  @DisplayName("tenant email must not resolve as a login identifier")
  void tenantEmailMustNotBeUsedForLogin() {
    User tenantUser =
        User.builder()
            .id(userId)
            .email("tenant@example.test")
            .passwordHash("tenant-hash")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();
    when(loginAttemptService.isLocked("tenant@example.test")).thenReturn(false);
    when(userRepository.findByEmail("tenant@example.test")).thenReturn(Optional.of(tenantUser));
    when(tenantRepository.findByIdCardNumber("tenant@example.test")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("tenant@example.test", "Password@123")))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName(
      "login should throw ACCOUNT_TEMPORARILY_LOCKED when identifier is locked by rate limit")
  void shouldRejectLoginWhenAccountIsTemporarilyLocked() {
    LoginRequest request = new LoginRequest("manager@dorm.vn", "Password@123");
    when(loginAttemptService.isLocked("manager@dorm.vn")).thenReturn(true);

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
  }

  @Test
  @DisplayName(
      "login should return generic INVALID_CREDENTIALS when user status is LOCKED to avoid enumeration")
  void permanentlyLockedLoginUsesGenericCredentialsError() {
    LoginRequest request = new LoginRequest("manager@dorm.vn", "Password@123");
    testUser.setStatus(UserStatus.LOCKED);

    when(loginAttemptService.isLocked("manager@dorm.vn")).thenReturn(false);
    when(userRepository.findByEmail("manager@dorm.vn")).thenReturn(Optional.of(testUser));

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);

    verify(loginAttemptService).recordFailedAttempt("manager@dorm.vn");
  }

  @Test
  @DisplayName(
      "login should throw ACCOUNT_TEMPORARILY_LOCKED when 6th failure locks the identifier")
  void sixthFailureDuringLoginThrowsAccountTemporarilyLocked() {
    LoginRequest request = new LoginRequest("manager@dorm.vn", "WrongPassword");

    when(loginAttemptService.isLocked("manager@dorm.vn")).thenReturn(false, true);
    when(userRepository.findByEmail("manager@dorm.vn")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("WrongPassword", testUser.getPasswordHash())).thenReturn(false);

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_TEMPORARILY_LOCKED);

    verify(loginAttemptService).recordFailedAttempt("manager@dorm.vn");
  }

  @Test
  @DisplayName(
      "login should record failed attempt and throw INVALID_CREDENTIALS when password fails")
  void shouldRejectLoginWhenPasswordMismatch() {
    LoginRequest request = new LoginRequest("manager@dorm.vn", "WrongPassword");

    when(loginAttemptService.isLocked("manager@dorm.vn")).thenReturn(false);
    when(userRepository.findByEmail("manager@dorm.vn")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("WrongPassword", testUser.getPasswordHash())).thenReturn(false);

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);

    verify(loginAttemptService).recordFailedAttempt("manager@dorm.vn");
  }

  @Test
  @DisplayName("refreshToken should rotate tokens when refresh token is valid")
  void shouldRefreshTokenSuccessfully() {
    String oldRefreshToken = "valid-refresh-token";
    RefreshTokenRequest request = new RefreshTokenRequest(oldRefreshToken);
    String jti = "refresh-jti-1";
    String gen = "gen-123";

    TokenPayload payload =
        new TokenPayload(
            userId,
            "manager@dorm.vn",
            "manager@dorm.vn",
            "MANAGER",
            jti,
            "REFRESH",
            Instant.now(),
            Instant.now().plusSeconds(604800),
            gen);

    when(jwtService.parseAndValidateToken(oldRefreshToken)).thenReturn(payload);
    when(userTokenInvalidationService.isCurrentGeneration(userId, gen)).thenReturn(true);
    when(tokenRevocationService.isRefreshTokenValid(userId, jti)).thenReturn(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
    when(userTokenInvalidationService.getOrCreateGeneration(userId)).thenReturn(gen);
    when(jwtService.generateAccessToken(eq(testUser), anyString(), eq(gen)))
        .thenReturn("new-access-token");
    when(jwtService.generateRefreshToken(eq(testUser), anyString(), eq(gen)))
        .thenReturn("new-refresh-token");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(86400L);
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);

    AuthResponse response = authService.refreshToken(request);

    assertThat(response.accessToken()).isEqualTo("new-access-token");
    assertThat(response.refreshToken()).isEqualTo("new-refresh-token");

    verify(tokenRevocationService).revokeRefreshToken(userId, jti);
    verify(tokenRevocationService).recordRefreshToken(eq(userId), anyString(), eq(604800L));
  }

  @Test
  @DisplayName("refreshToken should reject reused or revoked token without revoking other sessions")
  void shouldRejectReusedOrRevokedRefreshTokenWithoutRevokingAllSessions() {
    String oldRefreshToken = "reused-token";
    RefreshTokenRequest request = new RefreshTokenRequest(oldRefreshToken);
    String jti = "reused-jti";
    String gen = "gen-123";

    TokenPayload payload =
        new TokenPayload(
            userId,
            "manager@dorm.vn",
            "manager@dorm.vn",
            "MANAGER",
            jti,
            "REFRESH",
            Instant.now(),
            Instant.now().plusSeconds(604800),
            gen);

    when(jwtService.parseAndValidateToken(oldRefreshToken)).thenReturn(payload);
    when(userTokenInvalidationService.isCurrentGeneration(userId, gen)).thenReturn(true);
    when(tokenRevocationService.isRefreshTokenValid(userId, jti)).thenReturn(false);

    assertThatThrownBy(() -> authService.refreshToken(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_REFRESH_TOKEN);

    verify(tokenRevocationService, never()).revokeAllUserSessions(any());
  }

  @Test
  @DisplayName(
      "logout should reject when refresh token belongs to a different user without modifying either token")
  void logoutShouldRejectDifferentUserRefreshTokenWithoutModifyingEitherToken() {
    String authHeader = "Bearer access-token-abc";
    RefreshTokenRequest request = new RefreshTokenRequest("refresh-token-xyz");
    UUID otherUserId = UUID.randomUUID();

    TokenPayload accessPayload =
        new TokenPayload(
            userId,
            "manager@dorm.vn",
            "manager@dorm.vn",
            "MANAGER",
            "access-jti-1",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "gen-123");

    TokenPayload refreshPayload =
        new TokenPayload(
            otherUserId,
            "other@dorm.vn",
            "other@dorm.vn",
            "TENANT",
            "refresh-jti-2",
            "REFRESH",
            Instant.now(),
            Instant.now().plusSeconds(604800),
            "gen-456");

    when(jwtService.parseAndValidateToken("access-token-abc")).thenReturn(accessPayload);
    when(jwtService.parseAndValidateToken("refresh-token-xyz")).thenReturn(refreshPayload);

    assertThatThrownBy(() -> authService.logout(authHeader, request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_REFRESH_TOKEN);

    verify(tokenRevocationService, never()).blacklistAccessToken(anyString(), anyLong());
    verify(tokenRevocationService, never()).revokeRefreshToken(any(), anyString());
  }

  @Test
  @DisplayName(
      "logout should blacklist access token and revoke refresh token when both are valid and owned by user")
  void logoutShouldSucceedWhenBothTokensAreValidAndOwnedByUser() {
    String authHeader = "Bearer access-token-abc";
    RefreshTokenRequest request = new RefreshTokenRequest("refresh-token-xyz");

    TokenPayload accessPayload =
        new TokenPayload(
            userId,
            "manager@dorm.vn",
            "manager@dorm.vn",
            "MANAGER",
            "access-jti-1",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "gen-123");

    TokenPayload refreshPayload =
        new TokenPayload(
            userId,
            "manager@dorm.vn",
            "manager@dorm.vn",
            "MANAGER",
            "refresh-jti-2",
            "REFRESH",
            Instant.now(),
            Instant.now().plusSeconds(604800),
            "gen-123");

    when(jwtService.parseAndValidateToken("access-token-abc")).thenReturn(accessPayload);
    when(jwtService.parseAndValidateToken("refresh-token-xyz")).thenReturn(refreshPayload);
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
    when(userTokenInvalidationService.isCurrentGeneration(userId, "gen-123")).thenReturn(true);
    when(tokenRevocationService.isRefreshTokenValid(userId, "refresh-jti-2")).thenReturn(true);
    when(jwtService.getRemainingExpirationMs("access-token-abc")).thenReturn(1800000L);

    authService.logout(authHeader, request);

    verify(tokenRevocationService).blacklistAccessToken("access-jti-1", 1800000L);
    verify(tokenRevocationService).revokeRefreshToken(userId, "refresh-jti-2");
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
    verify(userTokenInvalidationService).invalidateAll(userId);
  }

  @Test
  @DisplayName("logoutAll should blacklist access token and invalidate all sessions for user")
  void logoutAllShouldBlacklistAccessAndInvalidateAllTokens() {
    String authHeader = "Bearer access-token-abc";
    TokenPayload accessPayload =
        new TokenPayload(
            userId,
            "manager@dorm.vn",
            "manager@dorm.vn",
            "MANAGER",
            "access-jti-1",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "gen-123");

    when(jwtService.parseAndValidateToken("access-token-abc")).thenReturn(accessPayload);
    when(jwtService.getRemainingExpirationMs("access-token-abc")).thenReturn(1800000L);

    authService.logoutAll(authHeader);

    verify(tokenRevocationService).blacklistAccessToken("access-jti-1", 1800000L);
    verify(userTokenInvalidationService).invalidateAll(userId);
  }

  @Test
  @DisplayName("forgotPassword should delegate to passwordRecoveryService")
  void shouldDelegateForgotPassword() {
    PasswordRecoveryRequest request = new PasswordRecoveryRequest("manager@dorm.vn");
    UUID reqId = UUID.randomUUID();
    PasswordRecoveryRequestedResponse expected = new PasswordRecoveryRequestedResponse(reqId, 300L);
    when(passwordRecoveryService.request("manager@dorm.vn")).thenReturn(expected);

    PasswordRecoveryRequestedResponse response = authService.forgotPassword(request);

    assertThat(response.recoveryRequestId()).isEqualTo(reqId);
    verify(passwordRecoveryService).request("manager@dorm.vn");
  }

  @Test
  @DisplayName("verifyForgotPasswordOtp should delegate to passwordRecoveryService")
  void shouldDelegateVerifyOtp() {
    UUID reqId = UUID.randomUUID();
    VerifyRecoveryOtpRequest request = new VerifyRecoveryOtpRequest(reqId, "123456");
    when(passwordRecoveryService.verify(reqId, "123456")).thenReturn("reset-tok-xyz");

    VerifyResetPasswordOtpResponse response = authService.verifyForgotPasswordOtp(request);

    assertThat(response.resetToken()).isEqualTo("reset-tok-xyz");
    verify(passwordRecoveryService).verify(reqId, "123456");
  }

  @Test
  @DisplayName("resetPassword should delegate to passwordRecoveryService")
  void shouldDelegateResetPassword() {
    ResetPasswordRequest request =
        new ResetPasswordRequest("reset-token", "NewPass@123", "NewPass@123");

    authService.resetPassword(request);

    verify(passwordRecoveryService).reset("reset-token", "NewPass@123", "NewPass@123");
  }

  @Test
  @DisplayName("login logs should mask raw CCCD and full email")
  void loginLogs_shouldMaskRawCccdAndEmail() {
    String rawCccd = "079203001187";
    String rawEmail = "unknown.staff@dorm.vn";

    Logger logger = (Logger) LoggerFactory.getLogger(AuthServiceImpl.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.WARN);

    try {
      when(loginAttemptService.isLocked(rawCccd)).thenReturn(false);
      when(userRepository.findByEmail(rawCccd)).thenReturn(Optional.empty());
      when(tenantRepository.findByIdCardNumber(rawCccd)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> authService.login(new LoginRequest(rawCccd, "Password@123")))
          .isInstanceOf(BaseAppException.class);

      when(loginAttemptService.isLocked(rawEmail)).thenReturn(true);
      assertThatThrownBy(() -> authService.login(new LoginRequest(rawEmail, "Password@123")))
          .isInstanceOf(BaseAppException.class);

      List<String> messages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("***187"));
      assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("u***@dorm.vn"));
      assertThat(messages).noneMatch(msg -> msg.contains(rawCccd) || msg.contains(rawEmail));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
