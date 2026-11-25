package com.uit.dormmanagement.security;

import java.time.Instant;
import java.util.UUID;

public record TokenPayload(
    UUID userId,
    String username,
    String email,
    String role,
    String jti,
    String tokenType,
    Instant issuedAt,
    Instant expiresAt,
    String sessionGeneration) {

  public TokenPayload(
      UUID userId,
      String username,
      String email,
      String role,
      String jti,
      String tokenType,
      Instant issuedAt,
      Instant expiresAt) {
    this(userId, username, email, role, jti, tokenType, issuedAt, expiresAt, null);
  }
}
