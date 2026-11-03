package com.uit.dormmanagement.services;

public interface OtpService {

  String generateAndStoreOtp(String email);

  boolean verifyOtp(String email, String otp);

  void checkCooldown(String email);

  String generateAndStorePasswordResetToken(String email);

  boolean verifyPasswordResetToken(String email, String token);
}
