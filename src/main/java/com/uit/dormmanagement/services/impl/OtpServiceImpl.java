package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.constants.RedisKeyConstants;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.AuthErrorCode;
import com.uit.dormmanagement.services.OtpService;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

  public static final Duration OTP_TTL = Duration.ofMinutes(5);
  public static final Duration OTP_COOLDOWN_TTL = Duration.ofSeconds(60);
  public static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(10);
  public static final int MAX_OTP_ATTEMPTS = 3;
  private static final String COOLDOWN_VALUE = "SENT";
  private static final int OTP_BOUND = 1_000_000;

  private final StringRedisTemplate redisTemplate;
  private final SecureRandom secureRandom = initSecureRandom();

  @Override
  public String generateAndStoreOtp(String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email cannot be null or blank");
    }
    String otp = generateOtp();
    String otpKey = RedisKeyConstants.otpKey(email);
    String cooldownKey = RedisKeyConstants.otpCooldownKey(email);
    String attemptsKey = RedisKeyConstants.otpAttemptsKey(email);

    redisTemplate.opsForValue().set(otpKey, otp, OTP_TTL);
    redisTemplate.opsForValue().set(cooldownKey, COOLDOWN_VALUE, OTP_COOLDOWN_TTL);
    redisTemplate.delete(attemptsKey);

    log.debug("Generated and stored OTP for email: {}", email);
    return otp;
  }

  @Override
  public boolean verifyOtp(String email, String otp) {
    if (email == null || otp == null) {
      return false;
    }

    String attemptsKey = RedisKeyConstants.otpAttemptsKey(email);
    String currentAttemptsStr = redisTemplate.opsForValue().get(attemptsKey);
    int currentAttempts = currentAttemptsStr != null ? Integer.parseInt(currentAttemptsStr) : 0;

    if (currentAttempts >= MAX_OTP_ATTEMPTS) {
      log.warn("Exceeded maximum OTP attempts for email: {}", email);
      throw new BaseAppException(AuthErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
    }

    String otpKey = RedisKeyConstants.otpKey(email);
    String storedOtp = redisTemplate.opsForValue().get(otpKey);

    if (storedOtp != null
        && java.security.MessageDigest.isEqual(
            storedOtp.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            otp.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      redisTemplate.delete(otpKey);
      redisTemplate.delete(attemptsKey);
      log.debug("Successfully verified and deleted OTP for email: {}", email);
      return true;
    }

    Long newAttempts = redisTemplate.opsForValue().increment(attemptsKey);
    if (newAttempts != null && newAttempts == 1) {
      redisTemplate.expire(attemptsKey, OTP_TTL);
    }
    log.debug(
        "Failed OTP verification for email: {}, attempt: {}/{}",
        email,
        newAttempts,
        MAX_OTP_ATTEMPTS);
    return false;
  }

  @Override
  public void checkCooldown(String email) {
    if (email == null || email.isBlank()) {
      return;
    }
    String cooldownKey = RedisKeyConstants.otpCooldownKey(email);
    Boolean hasCooldown = redisTemplate.hasKey(cooldownKey);
    if (Boolean.TRUE.equals(hasCooldown)) {
      throw new BaseAppException(AuthErrorCode.OTP_COOLDOWN_ACTIVE);
    }
  }

  @Override
  public String generateAndStorePasswordResetToken(String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email cannot be null or blank");
    }
    String token = UUID.randomUUID().toString();
    String key = RedisKeyConstants.passwordResetTokenKey(email);
    redisTemplate.opsForValue().set(key, token, PASSWORD_RESET_TOKEN_TTL);
    log.debug("Generated and stored password reset token for email: {}", email);
    return token;
  }

  @Override
  public boolean verifyPasswordResetToken(String email, String token) {
    if (email == null || token == null) {
      return false;
    }
    String key = RedisKeyConstants.passwordResetTokenKey(email);
    String storedToken = redisTemplate.opsForValue().get(key);
    if (storedToken != null
        && java.security.MessageDigest.isEqual(
            storedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            token.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      redisTemplate.delete(key);
      log.debug("Successfully verified and deleted password reset token for email: {}", email);
      return true;
    }
    return false;
  }

  private String generateOtp() {
    int number = secureRandom.nextInt(OTP_BOUND);
    return String.format("%06d", number);
  }

  private static SecureRandom initSecureRandom() {
    try {
      return SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException e) {
      log.warn("Strong SecureRandom not available, falling back to default instance", e);
      return new SecureRandom();
    }
  }
}
