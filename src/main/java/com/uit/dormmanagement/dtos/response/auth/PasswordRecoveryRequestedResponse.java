package com.uit.dormmanagement.dtos.response.auth;

import java.util.UUID;

public record PasswordRecoveryRequestedResponse(UUID recoveryRequestId, long expiresInSeconds) {}
