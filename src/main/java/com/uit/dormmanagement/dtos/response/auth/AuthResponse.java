package com.uit.dormmanagement.dtos.response.auth;

public record AuthResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {}
