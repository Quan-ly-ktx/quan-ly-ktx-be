package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.response.auth.PasswordRecoveryRequestedResponse;
import java.util.UUID;

public interface PasswordRecoveryService {

  PasswordRecoveryRequestedResponse request(String identifier);

  String verify(UUID recoveryRequestId, String otp);

  void reset(String resetToken, String newPassword, String confirmPassword);

  void invalidateRecoveryCredentials(UUID userId);
}
