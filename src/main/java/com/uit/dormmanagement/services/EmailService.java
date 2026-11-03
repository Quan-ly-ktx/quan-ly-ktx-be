package com.uit.dormmanagement.services;

public interface EmailService {

  void sendForgotPasswordOtp(String toEmail, String fullName, String otp);
}
