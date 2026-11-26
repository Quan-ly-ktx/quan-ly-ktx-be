package com.uit.dormmanagement.utils;

import java.util.regex.Pattern;

/** Utility methods for masking sensitive user identifiers before writing operational logs. */
public final class LogMaskingUtils {

  private static final String MASK = "***";
  private static final Pattern TWELVE_DIGIT_CCCD = Pattern.compile("^[0-9]{12}$");

  private LogMaskingUtils() {}

  /** Masks an email address as {@code <first-char>***@<domain>} (or {@code ***} when malformed). */
  public static String maskEmail(String email) {
    if (email == null || email.isBlank()) {
      return MASK;
    }
    String trimmed = email.trim();
    int atIndex = trimmed.indexOf('@');
    if (atIndex < 0) {
      return maskTail(trimmed);
    }
    if (atIndex == 0 || atIndex == trimmed.length() - 1) {
      return MASK;
    }
    String local = trimmed.substring(0, atIndex);
    String domain = trimmed.substring(atIndex + 1);
    if (domain.isBlank()) {
      return MASK;
    }
    String maskedLocal = local.length() <= 1 ? MASK : local.charAt(0) + MASK;
    return maskedLocal + "@" + domain;
  }

  /** Masks a 12-digit CCCD/ID card number, keeping at most the last 3 characters. */
  public static String maskIdCard(String idCard) {
    if (idCard == null || idCard.isBlank()) {
      return MASK;
    }
    return maskTail(idCard.trim());
  }

  /** Masks a phone number, keeping at most the last 3 characters. */
  public static String maskPhone(String phone) {
    if (phone == null || phone.isBlank()) {
      return MASK;
    }
    return maskTail(phone.trim());
  }

  /**
   * Masks a generic login/lookup identifier (email, 12-digit CCCD, or arbitrary username/string).
   */
  public static String maskIdentifier(String value) {
    if (value == null || value.isBlank()) {
      return MASK;
    }
    String trimmed = value.trim();
    if (trimmed.contains("@")) {
      return maskEmail(trimmed);
    }
    if (TWELVE_DIGIT_CCCD.matcher(trimmed).matches()) {
      return maskIdCard(trimmed);
    }
    return maskTail(trimmed);
  }

  private static String maskTail(String trimmed) {
    if (trimmed.length() <= 3) {
      return MASK;
    }
    return MASK + trimmed.substring(trimmed.length() - 3);
  }
}
