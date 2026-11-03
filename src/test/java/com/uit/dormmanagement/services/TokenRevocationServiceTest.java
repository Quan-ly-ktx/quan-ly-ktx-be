package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.constants.RedisKeyConstants;
import com.uit.dormmanagement.services.impl.TokenRevocationServiceImpl;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private SetOperations<String, String> setOperations;

  @InjectMocks private TokenRevocationServiceImpl tokenRevocationService;

  @BeforeEach
  void setUp() {
    // lenient for tests that use valueOperations or setOperations
  }

  @Test
  @DisplayName("recordRefreshToken should save token with TTL and add jti to user session set")
  void shouldRecordRefreshToken() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForSet()).thenReturn(setOperations);

    UUID userId = UUID.randomUUID();
    String jti = "test-jti-123";
    String token = "dummy-refresh-token";
    long ttl = 604800L;

    tokenRevocationService.recordRefreshToken(userId, jti, token, ttl);

    verify(valueOperations)
        .set(
            eq(RedisKeyConstants.refreshTokenKey(userId, jti)),
            eq(token),
            eq(Duration.ofSeconds(ttl)));
    verify(setOperations).add(eq(RedisKeyConstants.userSessionsKey(userId)), eq(jti));
    verify(redisTemplate)
        .expire(eq(RedisKeyConstants.userSessionsKey(userId)), eq(Duration.ofSeconds(ttl)));
  }

  @Test
  @DisplayName("isRefreshTokenValid should return true if key exists in Redis")
  void shouldCheckRefreshTokenValidity() {
    UUID userId = UUID.randomUUID();
    String jti = "test-jti";
    String key = RedisKeyConstants.refreshTokenKey(userId, jti);

    when(redisTemplate.hasKey(key)).thenReturn(true);

    boolean isValid = tokenRevocationService.isRefreshTokenValid(userId, jti);
    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("blacklistAccessToken should store REVOKED marker with remaining TTL")
  void shouldBlacklistAccessToken() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String jti = "test-access-jti";
    long remainingMs = 15000L;

    tokenRevocationService.blacklistAccessToken(jti, remainingMs);

    verify(valueOperations)
        .set(
            eq(RedisKeyConstants.tokenBlacklistKey(jti)),
            eq("REVOKED"),
            eq(Duration.ofMillis(remainingMs)));
  }

  @Test
  @DisplayName("blacklistAccessToken should skip if remaining TTL <= 0")
  void shouldSkipBlacklistingWhenExpired() {
    tokenRevocationService.blacklistAccessToken("expired-jti", 0L);
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  @DisplayName("revokeAllUserSessions should delete all refresh tokens and session set")
  void shouldRevokeAllUserSessions() {
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    UUID userId = UUID.randomUUID();
    String sessionKey = RedisKeyConstants.userSessionsKey(userId);
    when(setOperations.members(sessionKey)).thenReturn(Set.of("jti-1", "jti-2"));

    tokenRevocationService.revokeAllUserSessions(userId);

    verify(redisTemplate).delete(eq(RedisKeyConstants.refreshTokenKey(userId, "jti-1")));
    verify(redisTemplate).delete(eq(RedisKeyConstants.refreshTokenKey(userId, "jti-2")));
    verify(redisTemplate).delete(eq(sessionKey));
  }
}
