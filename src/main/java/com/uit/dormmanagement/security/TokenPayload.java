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
    Instant expiresAt) {}
