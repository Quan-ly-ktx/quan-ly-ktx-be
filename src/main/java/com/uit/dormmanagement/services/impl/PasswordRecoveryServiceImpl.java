package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.constants.RedisKeyConstants;
import com.uit.dormmanagement.dtos.response.auth.PasswordRecoveryRequestedResponse;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.AuthErrorCode;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.EmailService;
import com.uit.dormmanagement.services.PasswordRecoveryService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

  public static final Duration COOLDOWN_DURATION = Duration.ofSeconds(60);
  public static final Duration HOURLY_LIMIT_DURATION = Duration.ofHours(1);
  public static final int MAX_HOURLY_REQUESTS = 5;
  public static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
  public static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(15);
  public static final Duration VERSION_TTL = Duration.ofDays(30);

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final StringRedisTemplate redisTemplate;
  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final StaffProfileRepository staffProfileRepository;
  private final EmailService emailService;
  private final PasswordEncoder passwordEncoder;
  private final UserTokenInvalidationService userTokenInvalidationService;

  @Override
  public PasswordRecoveryRequestedResponse request(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      throw new BaseAppException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    String normalizedIdentifier = identifier.trim();
    String identifierHash = hashString(normalizedIdentifier.toLowerCase(Locale.ROOT));

    // Rate limiting
    checkAndApplyRateLimits(identifierHash);

    UUID recoveryRequestId = UUID.randomUUID();

    // Account resolution
    Optional<User> userOpt = findUserByIdentifier(normalizedIdentifier);
    if (userOpt.isEmpty() || userOpt.get().getStatus() != UserStatus.ACTIVE) {
      log.info("Password recovery requested for non-existent or inactive identifier");
      return new PasswordRecoveryRequestedResponse(recoveryRequestId, CHALLENGE_TTL.toSeconds());
    }

    User user = userOpt.get();
    String destinationEmail = user.getEmail();
    String fullName = resolveUserFullName(user);

    long recoveryVersion = getCurrentRecoveryVersion(user.getId());
    String otp = generateOtp();
    String otpHash = hashString(otp);

    // Store challenge in Redis
    String challengeKey = RedisKeyConstants.recoveryChallengeKey(recoveryRequestId);
    String challengePayload = user.getId() + ":" + recoveryVersion + ":" + otpHash + ":0";
    redisTemplate.opsForValue().set(challengeKey, challengePayload, CHALLENGE_TTL);

    // Send email outside DB lock
    try {
      emailService.sendForgotPasswordOtp(destinationEmail, fullName, otp);
      log.info("Sent recovery OTP to user id: {}", user.getId());
    } catch (Exception e) {
      log.error("Failed to send recovery OTP email: {}", e.getMessage());
    }

    return new PasswordRecoveryRequestedResponse(recoveryRequestId, CHALLENGE_TTL.toSeconds());
  }

  @Override
  public String verify(UUID recoveryRequestId, String otp) {
    if (recoveryRequestId == null || otp == null || otp.isBlank()) {
      throw new BaseAppException(AuthErrorCode.INVALID_OTP);
    }

    String challengeKey = RedisKeyConstants.recoveryChallengeKey(recoveryRequestId);
    String challengePayload = redisTemplate.opsForValue().get(challengeKey);
    if (challengePayload == null || challengePayload.isBlank()) {
      throw new BaseAppException(AuthErrorCode.INVALID_OTP);
    }

    String[] parts = challengePayload.split(":");
    if (parts.length < 4) {
      redisTemplate.delete(challengeKey);
      throw new BaseAppException(AuthErrorCode.INVALID_OTP);
    }

    UUID userId = UUID.fromString(parts[0]);
    long challengeVersion = Long.parseLong(parts[1]);
    String expectedOtpHash = parts[2];
    int attempts = Integer.parseInt(parts[3]);

    // Check version
    long currentVersion = getCurrentRecoveryVersion(userId);
    if (currentVersion != challengeVersion) {
      redisTemplate.delete(challengeKey);
      throw new BaseAppException(AuthErrorCode.INVALID_OTP);
    }

    String submittedOtpHash = hashString(otp.trim());
    if (!expectedOtpHash.equals(submittedOtpHash)) {
      attempts++;
      if (attempts >= 3) {
        redisTemplate.delete(challengeKey);
        log.warn(
            "Recovery challenge failed 3 times, invalidating request id: {}", recoveryRequestId);
        throw new BaseAppException(AuthErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
      } else {
        String updatedPayload =
            userId + ":" + challengeVersion + ":" + expectedOtpHash + ":" + attempts;
        Long remainingTtl = redisTemplate.getExpire(challengeKey);
        if (remainingTtl != null && remainingTtl > 0) {
          redisTemplate
              .opsForValue()
              .set(challengeKey, updatedPayload, Duration.ofSeconds(remainingTtl));
        }
        throw new BaseAppException(AuthErrorCode.INVALID_OTP);
      }
    }

    // OTP is valid! Invalidate challenge and issue reset token
    redisTemplate.delete(challengeKey);

    String resetToken = UUID.randomUUID().toString();
    String resetTokenKey = RedisKeyConstants.recoveryResetTokenKey(resetToken);
    String resetPayload = userId + ":" + challengeVersion;
    redisTemplate.opsForValue().set(resetTokenKey, resetPayload, RESET_TOKEN_TTL);

    log.info("Recovery OTP verified successfully for user id: {}", userId);
    return resetToken;
  }

  @Override
  @Transactional
  public void reset(String resetToken, String newPassword, String confirmPassword) {
    if (resetToken == null || resetToken.isBlank()) {
      throw new BaseAppException(AuthErrorCode.INVALID_RESET_TOKEN);
    }

    if (!newPassword.equals(confirmPassword)) {
      throw new BaseAppException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
    }

    String resetTokenKey = RedisKeyConstants.recoveryResetTokenKey(resetToken.trim());
    String resetPayload = redisTemplate.opsForValue().get(resetTokenKey);
    if (resetPayload == null || resetPayload.isBlank()) {
      throw new BaseAppException(AuthErrorCode.INVALID_RESET_TOKEN);
    }

    String[] parts = resetPayload.split(":");
    if (parts.length < 2) {
      redisTemplate.delete(resetTokenKey);
      throw new BaseAppException(AuthErrorCode.INVALID_RESET_TOKEN);
    }

    UUID userId = UUID.fromString(parts[0]);
    long tokenVersion = Long.parseLong(parts[1]);

    // User-row lock
    User user =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new BaseAppException(AuthErrorCode.INVALID_RESET_TOKEN));

    long currentVersion = getCurrentRecoveryVersion(userId);
    if (currentVersion != tokenVersion) {
      redisTemplate.delete(resetTokenKey);
      throw new BaseAppException(AuthErrorCode.INVALID_RESET_TOKEN);
    }

    // Atomically consume reset token
    redisTemplate.delete(resetTokenKey);

    if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
      throw new BaseAppException(AuthErrorCode.PASSWORD_SAME_AS_OLD);
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    // Invalidate recovery credentials and all active sessions
    invalidateRecoveryCredentials(userId);
    userTokenInvalidationService.invalidateAll(userId);

    log.info("Password reset successfully for user id: {}", userId);
  }

  @Override
  public void invalidateRecoveryCredentials(UUID userId) {
    if (userId == null) {
      return;
    }
    String versionKey = RedisKeyConstants.userRecoveryVersionKey(userId);
    redisTemplate.opsForValue().increment(versionKey);
    redisTemplate.expire(versionKey, VERSION_TTL);
    log.debug("Advanced recovery version for user id: {}", userId);
  }

  private void checkAndApplyRateLimits(String identifierHash) {
    String cooldownKey = RedisKeyConstants.recoveryCooldownKey(identifierHash);
    if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
      throw new BaseAppException(AuthErrorCode.OTP_COOLDOWN_ACTIVE);
    }

    String hourlyKey = RedisKeyConstants.recoveryHourlyKey(identifierHash);
    Long currentAttempts = redisTemplate.opsForValue().increment(hourlyKey);
    if (currentAttempts != null && currentAttempts == 1) {
      redisTemplate.expire(hourlyKey, HOURLY_LIMIT_DURATION);
    }
    if (currentAttempts != null && currentAttempts > MAX_HOURLY_REQUESTS) {
      throw new BaseAppException(AuthErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
    }

    redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_DURATION);
  }

  private long getCurrentRecoveryVersion(UUID userId) {
    String versionKey = RedisKeyConstants.userRecoveryVersionKey(userId);
    String versionStr = redisTemplate.opsForValue().get(versionKey);
    if (versionStr == null || versionStr.isBlank()) {
      redisTemplate.opsForValue().set(versionKey, "1", VERSION_TTL);
      return 1L;
    }
    try {
      return Long.parseLong(versionStr);
    } catch (NumberFormatException e) {
      redisTemplate.opsForValue().set(versionKey, "1", VERSION_TTL);
      return 1L;
    }
  }

  private Optional<User> findUserByIdentifier(String identifier) {
    String normalized = identifier.trim();
    if (normalized.matches("^\\d{12}$")) {
      return tenantRepository
          .findByIdCardNumber(normalized)
          .map(com.uit.dormmanagement.entities.Tenant::getUser);
    }
    return userRepository
        .findByEmail(normalized.toLowerCase(Locale.ROOT))
        .filter(user -> user.getRole() != UserRole.TENANT);
  }

  private String resolveUserFullName(User user) {
    return switch (user.getRole()) {
      case MANAGER, TECHNICIAN ->
          staffProfileRepository
              .findByUserId(user.getId())
              .map(com.uit.dormmanagement.entities.StaffProfile::getFullName)
              .orElse(user.getEmail());
      case TENANT ->
          tenantRepository
              .findByUserId(user.getId())
              .map(com.uit.dormmanagement.entities.Tenant::getFullName)
              .orElse(user.getEmail());
    };
  }

  private String generateOtp() {
    int code = SECURE_RANDOM.nextInt(1_000_000);
    return String.format("%06d", code);
  }

  private String hashString(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
