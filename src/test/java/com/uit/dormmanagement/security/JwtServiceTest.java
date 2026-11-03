package com.uit.dormmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET_BASE64 =
      "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
  private static final long ACCESS_EXPIRATION_MS = 3600000L;
  private static final long REFRESH_EXPIRATION_MS = 604800000L;

  private JwtService jwtService;
  private User demoUser;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService(SECRET_BASE64, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
    demoUser =
        User.builder()
            .id(UUID.randomUUID())
            .username("manager01")
            .email("manager@dorm.vn")
            .phone("0901000001")
            .passwordHash("$2a$12$hash")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .build();
  }

  @Test
  @DisplayName("generateAccessToken and parseAndValidateToken should work correctly")
  void shouldGenerateAndValidateAccessToken() {
    String jti = UUID.randomUUID().toString();
    String token = jwtService.generateAccessToken(demoUser, jti);

    assertThat(token).isNotBlank();

    TokenPayload payload = jwtService.parseAndValidateToken(token);
    assertThat(payload.userId()).isEqualTo(demoUser.getId());
    assertThat(payload.username()).isEqualTo("manager01");
    assertThat(payload.email()).isEqualTo("manager@dorm.vn");
    assertThat(payload.role()).isEqualTo("MANAGER");
    assertThat(payload.jti()).isEqualTo(jti);
    assertThat(payload.tokenType()).isEqualTo("ACCESS");
    assertThat(payload.expiresAt()).isAfter(payload.issuedAt());
  }

  @Test
  @DisplayName("generateRefreshToken should encode REFRESH token type")
  void shouldGenerateAndValidateRefreshToken() {
    String jti = UUID.randomUUID().toString();
    String token = jwtService.generateRefreshToken(demoUser, jti);

    assertThat(token).isNotBlank();

    TokenPayload payload = jwtService.parseAndValidateToken(token);
    assertThat(payload.userId()).isEqualTo(demoUser.getId());
    assertThat(payload.username()).isEqualTo("manager01");
    assertThat(payload.jti()).isEqualTo(jti);
    assertThat(payload.tokenType()).isEqualTo("REFRESH");
  }

  @Test
  @DisplayName("getRemainingExpirationMs should return positive value for fresh token")
  void shouldReturnPositiveRemainingTime() {
    String jti = UUID.randomUUID().toString();
    String token = jwtService.generateAccessToken(demoUser, jti);

    long remaining = jwtService.getRemainingExpirationMs(token);
    assertThat(remaining).isPositive().isLessThanOrEqualTo(ACCESS_EXPIRATION_MS);
  }

  @Test
  @DisplayName("Constructor should fail when secret is blank or too short")
  void shouldRejectInvalidSecret() {
    assertThatThrownBy(() -> new JwtService("")).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new JwtService("short-secret"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
