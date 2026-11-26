package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.auth.ChangePasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.LoginRequest;
import com.uit.dormmanagement.dtos.request.auth.PasswordRecoveryRequest;
import com.uit.dormmanagement.dtos.request.auth.RefreshTokenRequest;
import com.uit.dormmanagement.dtos.request.auth.ResetPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.VerifyRecoveryOtpRequest;
import com.uit.dormmanagement.dtos.response.auth.AuthResponse;
import com.uit.dormmanagement.dtos.response.auth.PasswordRecoveryRequestedResponse;
import com.uit.dormmanagement.dtos.response.auth.UserResponse;
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
import com.uit.dormmanagement.services.AuthService;
import com.uit.dormmanagement.services.EmailService;
import com.uit.dormmanagement.services.LoginAttemptService;
import com.uit.dormmanagement.services.OtpService;
import com.uit.dormmanagement.services.PasswordRecoveryService;
import com.uit.dormmanagement.services.TokenRevocationService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import com.uit.dormmanagement.utils.LogMaskingUtils;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private static final String BEARER_PREFIX = "Bearer ";

  private final UserRepository userRepository;
  private final StaffProfileRepository staffProfileRepository;
  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LoginAttemptService loginAttemptService;
  private final TokenRevocationService tokenRevocationService;
  private final OtpService otpService;
  private final EmailService emailService;
  private final UserTokenInvalidationService userTokenInvalidationService;
  private final PasswordRecoveryService passwordRecoveryService;

  @Override
  @Transactional
  public AuthResponse login(LoginRequest request) {
    String identifier = request.identifier().trim();

    if (loginAttemptService.isLocked(identifier)) {
      log.warn(
          "Login attempt blocked: identifier [{}] is temporarily locked",
          LogMaskingUtils.maskIdentifier(identifier));
      throw new BaseAppException(AuthErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
    }

    Optional<User> userOpt = findUserByIdentifier(identifier);
    if (userOpt.isEmpty()) {
      loginAttemptService.recordFailedAttempt(identifier);
      if (loginAttemptService.isLocked(identifier)) {
        throw new BaseAppException(AuthErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
      }
      log.warn(
          "Login failed: identifier [{}] not found", LogMaskingUtils.maskIdentifier(identifier));
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    User user = userOpt.get();

    if (user.getStatus() == UserStatus.LOCKED) {
      loginAttemptService.recordFailedAttempt(identifier);
      if (loginAttemptService.isLocked(identifier)) {
        throw new BaseAppException(AuthErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
      }
      log.warn("Login failed: account is permanently locked for user id: {}", user.getId());
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      loginAttemptService.recordFailedAttempt(identifier);
      if (loginAttemptService.isLocked(identifier)) {
        throw new BaseAppException(AuthErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
      }
      log.warn(
          "Login failed: invalid password for identifier [{}]",
          LogMaskingUtils.maskIdentifier(identifier));
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    loginAttemptService.resetAttempts(identifier);

    return generateAuthResponse(user);
  }

  @Override
  @Transactional
  public AuthResponse refreshToken(RefreshTokenRequest request) {
    if (request == null || !StringUtils.hasText(request.refreshToken())) {
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    String token = request.refreshToken().trim();
    TokenPayload payload;
    try {
      payload = jwtService.parseAndValidateToken(token);
    } catch (Exception e) {
      log.warn("Failed to parse/validate refresh token: {}", e.getMessage());
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    if (payload == null
        || !"REFRESH".equals(payload.tokenType())
        || payload.userId() == null
        || !StringUtils.hasText(payload.jti())) {
      log.warn("Invalid refresh token payload");
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    UUID userId = payload.userId();
    String jti = payload.jti();

    if (!StringUtils.hasText(payload.sessionGeneration())
        || !userTokenInvalidationService.isCurrentGeneration(userId, payload.sessionGeneration())) {
      log.warn("Refresh token session generation mismatch or missing for user: {}", userId);
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    if (!tokenRevocationService.isRefreshTokenValid(userId, jti)) {
      log.warn("Refresh token reuse detected or token revoked for user: {}, jti: {}", userId, jti);
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN));

    if (user.getStatus() != UserStatus.ACTIVE) {
      log.warn("Cannot refresh token: user status is {}", user.getStatus());
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    tokenRevocationService.revokeRefreshToken(userId, jti);

    return generateAuthResponse(user);
  }

  @Override
  public void logout(String authHeader, RefreshTokenRequest request) {
    if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    String accessToken = authHeader.substring(BEARER_PREFIX.length()).trim();
    if (!StringUtils.hasText(accessToken)) {
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    if (request == null || !StringUtils.hasText(request.refreshToken())) {
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    String refreshToken = request.refreshToken().trim();

    TokenPayload accessPayload;
    TokenPayload refreshPayload;
    try {
      accessPayload = jwtService.parseAndValidateToken(accessToken);
      refreshPayload = jwtService.parseAndValidateToken(refreshToken);
    } catch (Exception e) {
      log.warn("Token validation failed during logout: {}", e.getMessage());
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    if (accessPayload == null
        || !"ACCESS".equals(accessPayload.tokenType())
        || accessPayload.userId() == null
        || !StringUtils.hasText(accessPayload.jti())) {
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    if (refreshPayload == null
        || !"REFRESH".equals(refreshPayload.tokenType())
        || refreshPayload.userId() == null
        || !StringUtils.hasText(refreshPayload.jti())) {
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    // Common ownership validation
    if (!accessPayload.userId().equals(refreshPayload.userId())) {
      log.warn(
          "Logout rejected: ownership mismatch (access user {} vs refresh user {})",
          accessPayload.userId(),
          refreshPayload.userId());
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    UUID userId = accessPayload.userId();

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS));
    if (user.getStatus() != UserStatus.ACTIVE) {
      log.warn("Logout rejected: user status is not ACTIVE");
      throw new BaseAppException(AuthErrorCode.ACCOUNT_LOCKED);
    }

    if (!StringUtils.hasText(refreshPayload.sessionGeneration())
        || !userTokenInvalidationService.isCurrentGeneration(
            userId, refreshPayload.sessionGeneration())) {
      log.warn("Logout rejected: refresh token has stale or missing session generation");
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    if (!tokenRevocationService.isRefreshTokenValid(userId, refreshPayload.jti())) {
      log.warn("Logout rejected: refresh token jti is not active");
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    // Both tokens valid and verified: blacklist access token and revoke refresh token
    long remainingMs = jwtService.getRemainingExpirationMs(accessToken);
    tokenRevocationService.blacklistAccessToken(accessPayload.jti(), remainingMs);
    tokenRevocationService.revokeRefreshToken(userId, refreshPayload.jti());
    log.info(
        "Successfully logged out user: {}, revoked refresh jti: {}", userId, refreshPayload.jti());
  }

  @Override
  public void logoutAll(String authHeader) {
    if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    String accessToken = authHeader.substring(BEARER_PREFIX.length()).trim();
    TokenPayload payload;
    try {
      payload = jwtService.parseAndValidateToken(accessToken);
    } catch (Exception e) {
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    if (payload == null || payload.userId() == null) {
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    if (payload.jti() != null) {
      long remainingMs = jwtService.getRemainingExpirationMs(accessToken);
      tokenRevocationService.blacklistAccessToken(payload.jti(), remainingMs);
    }
    userTokenInvalidationService.invalidateAll(payload.userId());
    log.info("Invalidated all sessions and rotated generation for user id: {}", payload.userId());
  }

  @Override
  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseAppException(AuthErrorCode.USER_NOT_FOUND));

    if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
      throw new BaseAppException(AuthErrorCode.INVALID_OLD_PASSWORD);
    }

    if (!request.newPassword().equals(request.confirmPassword())) {
      throw new BaseAppException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
    }

    if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
      throw new BaseAppException(AuthErrorCode.PASSWORD_SAME_AS_OLD);
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);

    userTokenInvalidationService.invalidateAll(userId);
    log.info("Password changed successfully and all sessions invalidated for user id: {}", userId);
  }

  @Override
  public PasswordRecoveryRequestedResponse forgotPassword(PasswordRecoveryRequest request) {
    return passwordRecoveryService.request(request.identifier());
  }

  @Override
  public VerifyResetPasswordOtpResponse verifyForgotPasswordOtp(VerifyRecoveryOtpRequest request) {
    String resetToken = passwordRecoveryService.verify(request.recoveryRequestId(), request.otp());
    return new VerifyResetPasswordOtpResponse(resetToken);
  }

  @Override
  public void resetPassword(ResetPasswordRequest request) {
    passwordRecoveryService.reset(
        request.resetToken(), request.newPassword(), request.confirmPassword());
  }

  private AuthResponse generateAuthResponse(User user) {
    String accessJti = UUID.randomUUID().toString();
    String refreshJti = UUID.randomUUID().toString();
    String generation = userTokenInvalidationService.getOrCreateGeneration(user.getId());

    String accessToken = jwtService.generateAccessToken(user, accessJti, generation);
    String refreshToken = jwtService.generateRefreshToken(user, refreshJti, generation);

    tokenRevocationService.recordRefreshToken(
        user.getId(), refreshJti, jwtService.getRefreshTokenExpirationSeconds());

    return new AuthResponse(
        accessToken,
        refreshToken,
        "Bearer",
        jwtService.getAccessTokenExpirationSeconds(),
        UserResponse.fromEntity(user));
  }

  private Optional<User> findUserByIdentifier(String identifier) {
    String normalizedIdentifier = identifier.trim();
    Optional<User> byEmail =
        userRepository
            .findByEmail(normalizedIdentifier.toLowerCase(java.util.Locale.ROOT))
            .filter(user -> user.getRole() != UserRole.TENANT);
    if (byEmail.isPresent()) {
      return byEmail;
    }
    return tenantRepository
        .findByIdCardNumber(normalizedIdentifier)
        .map(com.uit.dormmanagement.entities.Tenant::getUser);
  }

  private String resolveUserFullName(User user) {
    return switch (user.getRole()) {
      case MANAGER, TECHNICIAN ->
          staffProfileRepository
              .findByUserId(user.getId())
              .map(profile -> profile.getFullName())
              .orElse(user.getEmail());
      case TENANT ->
          tenantRepository
              .findByUserId(user.getId())
              .map(tenant -> tenant.getFullName())
              .orElse(user.getEmail());
    };
  }
}
