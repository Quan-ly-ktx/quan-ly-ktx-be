package com.uit.dormmanagement.constants;

import java.util.UUID;

public final class RedisKeyConstants {

  private static final String PREFIX = "dorm:auth:";

  private RedisKeyConstants() {
    // Utility class
  }

  public static String tokenBlacklistKey(String jti) {
    return PREFIX + "blacklist:" + jti;
  }

  public static String refreshTokenKey(UUID userId, String jti) {
    return PREFIX + "refresh:" + userId + ":" + jti;
  }

  public static String userSessionsKey(UUID userId) {
    return PREFIX + "sessions:" + userId;
  }

  public static String sessionGenerationKey(UUID userId) {
    return PREFIX + "generation:" + userId;
  }

  public static String recoveryCooldownKey(String identifierHash) {
    return PREFIX + "recovery:cooldown:" + identifierHash;
  }

  public static String recoveryHourlyKey(String identifierHash) {
    return PREFIX + "recovery:hourly:" + identifierHash;
  }

  public static String recoveryChallengeKey(UUID recoveryRequestId) {
    return PREFIX + "recovery:challenge:" + recoveryRequestId;
  }

  public static String recoveryResetTokenKey(String resetToken) {
    return PREFIX + "recovery:reset:" + resetToken;
  }

  public static String userRecoveryVersionKey(UUID userId) {
    return PREFIX + "recovery:version:" + userId;
  }

  public static String loginAttemptsKey(String identifier) {
    return PREFIX + "login:attempts:" + identifier.toLowerCase().trim();
  }

  public static String loginLockedKey(String identifier) {
    return PREFIX + "login:locked:" + identifier.toLowerCase().trim();
  }

  public static String otpKey(String email) {
    return PREFIX + "otp:forgot_pwd:" + email.toLowerCase().trim();
  }

  public static String otpCooldownKey(String email) {
    return PREFIX + "otp:cooldown:" + email.toLowerCase().trim();
  }

  public static String otpAttemptsKey(String email) {
    return PREFIX + "otp:attempts:" + email.toLowerCase().trim();
  }

  public static String passwordResetTokenKey(String email) {
    return PREFIX + "pwd_reset_token:" + email.toLowerCase().trim();
  }
}
