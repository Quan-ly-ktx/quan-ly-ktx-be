package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.auth.ChangePasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.ForgotPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.LoginRequest;
import com.uit.dormmanagement.dtos.request.auth.RefreshTokenRequest;
import com.uit.dormmanagement.dtos.request.auth.ResetPasswordRequest;
import com.uit.dormmanagement.dtos.request.auth.VerifyOtpRequest;
import com.uit.dormmanagement.dtos.response.auth.AuthResponse;
import com.uit.dormmanagement.dtos.response.auth.UserResponse;
import com.uit.dormmanagement.dtos.response.auth.VerifyResetPasswordOtpResponse;
import com.uit.dormmanagement.entities.User;
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
import com.uit.dormmanagement.services.TokenRevocationService;
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

  @Override
  @Transactional
  public AuthResponse login(LoginRequest request) {
    String identifier = request.identifier().trim();

    if (loginAttemptService.isLocked(identifier)) {
      log.warn(
          "Login attempt blocked: identifier [{}] is temporarily locked",
          maskIdentifier(identifier));
      throw new BaseAppException(AuthErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
    }

    Optional<User> userOpt = findUserByIdentifier(identifier);
    if (userOpt.isEmpty()) {
      loginAttemptService.recordFailedAttempt(identifier);
      log.warn("Login failed: identifier [{}] not found", maskIdentifier(identifier));
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    User user = userOpt.get();

    if (user.getStatus() == UserStatus.LOCKED) {
      log.warn("Login failed: account is permanently locked for user id: {}", user.getId());
      throw new BaseAppException(AuthErrorCode.ACCOUNT_LOCKED);
    }

    if (user.getStatus() == UserStatus.INACTIVE) {
      log.warn("Login failed: account is inactive for user id: {}", user.getId());
      throw new BaseAppException(AuthErrorCode.ACCOUNT_INACTIVE);
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      loginAttemptService.recordFailedAttempt(identifier);
      log.warn("Login failed: invalid password for identifier [{}]", maskIdentifier(identifier));
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    loginAttemptService.resetAttempts(identifier);

    return generateAuthResponse(user);
  }

  @Override
  @Transactional
  public AuthResponse refreshToken(RefreshTokenRequest request) {
    String token = request.refreshToken().trim();
    TokenPayload payload;
    try {
      payload = jwtService.parseAndValidateToken(token);
    } catch (Exception e) {
      log.warn("Failed to parse/validate refresh token: {}", e.getMessage());
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    if (payload == null || !"REFRESH".equals(payload.tokenType()) || payload.userId() == null) {
      log.warn("Invalid refresh token payload");
      throw new BaseAppException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    UUID userId = payload.userId();
    String jti = payload.jti();

    if (!tokenRevocationService.isRefreshTokenValid(userId, jti)) {
      log.warn(
          "Refresh token reuse detected or token revoked for user: {}, revoking all sessions",
          userId);
      tokenRevocationService.revokeAllUserSessions(userId);
      throw new BaseAppException(AuthErrorCode.TOKEN_REVOKED);
    }

    tokenRevocationService.revokeRefreshToken(userId, jti);

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseAppException(AuthErrorCode.USER_NOT_FOUND));

    if (user.getStatus() != UserStatus.ACTIVE) {
      log.warn("Cannot refresh token: user status is {}", user.getStatus());
      throw new BaseAppException(AuthErrorCode.ACCOUNT_INACTIVE);
    }

    return generateAuthResponse(user);
  }

  @Override
  public void logout(String authHeader, RefreshTokenRequest request) {
    if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
      String accessToken = authHeader.substring(BEARER_PREFIX.length()).trim();
      try {
        TokenPayload payload = jwtService.parseAndValidateToken(accessToken);
        if (payload != null && payload.jti() != null) {
          long remainingMs = jwtService.getRemainingExpirationMs(accessToken);
          tokenRevocationService.blacklistAccessToken(payload.jti(), remainingMs);
          log.info("Blacklisted access token jti: {}", payload.jti());
        }
      } catch (Exception e) {
        log.debug("Access token parsing during logout failed: {}", e.getMessage());
      }
    }

    if (request != null && StringUtils.hasText(request.refreshToken())) {
      try {
        TokenPayload refreshPayload =
            jwtService.parseAndValidateToken(request.refreshToken().trim());
        if (refreshPayload != null
            && refreshPayload.jti() != null
            && refreshPayload.userId() != null) {
          tokenRevocationService.revokeRefreshToken(refreshPayload.userId(), refreshPayload.jti());
          log.info("Revoked refresh token jti: {}", refreshPayload.jti());
        }
      } catch (Exception e) {
        log.debug("Refresh token parsing during logout failed: {}", e.getMessage());
      }
    }
  }

  @Override
  public void logoutAll(String authHeader) {
    if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
      String accessToken = authHeader.substring(BEARER_PREFIX.length()).trim();
      try {
        TokenPayload payload = jwtService.parseAndValidateToken(accessToken);
        if (payload != null && payload.userId() != null) {
          if (payload.jti() != null) {
            long remainingMs = jwtService.getRemainingExpirationMs(accessToken);
            tokenRevocationService.blacklistAccessToken(payload.jti(), remainingMs);
          }
          tokenRevocationService.revokeAllUserSessions(payload.userId());
          log.info("Revoked all sessions for user id: {}", payload.userId());
        }
      } catch (Exception e) {
        log.warn("Failed to logout all sessions: {}", e.getMessage());
      }
    }
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

    tokenRevocationService.revokeAllUserSessions(userId);
    log.info("Password changed successfully for user id: {}", userId);
  }

  @Override
  public void forgotPassword(ForgotPasswordRequest request) {
    String email = request.email().trim().toLowerCase();
    Optional<User> userOpt = userRepository.findByEmail(email);

    if (userOpt.isEmpty()) {
      // Prevent email enumeration
      log.info("Forgot password requested for non-existent email: {}", email);
      return;
    }

    User user = userOpt.get();
    otpService.checkCooldown(email);
    String otp = otpService.generateAndStoreOtp(email);

    String fullName = resolveUserFullName(user);
    emailService.sendForgotPasswordOtp(email, fullName, otp);
    log.info("Sent forgot password OTP to: {}", email);
  }

  @Override
  public VerifyResetPasswordOtpResponse verifyForgotPasswordOtp(VerifyOtpRequest request) {
    String email = request.email().trim().toLowerCase();
    boolean isValid = otpService.verifyOtp(email, request.otp());
    if (!isValid) {
      throw new BaseAppException(AuthErrorCode.INVALID_OTP);
    }

    String resetToken = otpService.generateAndStorePasswordResetToken(email);
    return new VerifyResetPasswordOtpResponse(resetToken);
  }

  @Override
  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    String email = request.email().trim().toLowerCase();

    boolean isTokenValid = otpService.verifyPasswordResetToken(email, request.resetToken());
    if (!isTokenValid) {
      throw new BaseAppException(AuthErrorCode.INVALID_RESET_TOKEN);
    }

    if (!request.newPassword().equals(request.confirmPassword())) {
      throw new BaseAppException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
    }

    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new BaseAppException(AuthErrorCode.USER_NOT_FOUND));

    if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
      throw new BaseAppException(AuthErrorCode.PASSWORD_SAME_AS_OLD);
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);

    tokenRevocationService.revokeAllUserSessions(user.getId());
    log.info("Successfully reset password for email: {}", email);
  }

  private AuthResponse generateAuthResponse(User user) {
    String accessJti = UUID.randomUUID().toString();
    String refreshJti = UUID.randomUUID().toString();

    String accessToken = jwtService.generateAccessToken(user, accessJti);
    String refreshToken = jwtService.generateRefreshToken(user, refreshJti);

    tokenRevocationService.recordRefreshToken(
        user.getId(), refreshJti, refreshToken, jwtService.getRefreshTokenExpirationSeconds());

    return new AuthResponse(
        accessToken,
        refreshToken,
        "Bearer",
        jwtService.getAccessTokenExpirationSeconds(),
        UserResponse.fromEntity(user));
  }

  private Optional<User> findUserByIdentifier(String identifier) {
    Optional<User> userOpt = userRepository.findByUsername(identifier);
    if (userOpt.isEmpty()) {
      userOpt = userRepository.findByEmail(identifier);
    }
    if (userOpt.isEmpty()) {
      userOpt = userRepository.findByPhone(identifier);
    }
    return userOpt;
  }

  private String resolveUserFullName(User user) {
    return switch (user.getRole()) {
      case MANAGER, TECHNICIAN ->
          staffProfileRepository
              .findByUserId(user.getId())
              .map(profile -> profile.getFullName())
              .orElse(user.getUsername());
      case TENANT ->
          tenantRepository
              .findByUserId(user.getId())
              .map(tenant -> tenant.getFullName())
              .orElse(user.getUsername());
    };
  }

  private String maskIdentifier(String identifier) {
    if (identifier == null || identifier.length() <= 3) {
      return "***";
    }
    return "***" + identifier.substring(identifier.length() - 3);
  }
}
