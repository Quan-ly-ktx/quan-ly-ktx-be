package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.constants.RedisKeyConstants;
import com.uit.dormmanagement.services.impl.UserTokenInvalidationServiceImpl;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class UserTokenInvalidationServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private TokenRevocationService tokenRevocationService;

  @InjectMocks private UserTokenInvalidationServiceImpl invalidationService;

  @Test
  @DisplayName("getOrCreateGeneration should return existing generation when present and refresh TTL")
  void shouldReturnExistingGenerationWhenPresent() {
    UUID userId = UUID.randomUUID();
    String key = RedisKeyConstants.sessionGenerationKey(userId);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(key)).thenReturn("existing-gen-123");

    String gen = invalidationService.getOrCreateGeneration(userId);

    assertThat(gen).isEqualTo("existing-gen-123");
    verify(redisTemplate).expire(eq(key), eq(UserTokenInvalidationServiceImpl.GENERATION_TTL));
  }

  @Test
  @DisplayName("getOrCreateGeneration should create new unpredictable generation when missing")
  void shouldCreateNewGenerationWhenMissing() {
    UUID userId = UUID.randomUUID();
    String key = RedisKeyConstants.sessionGenerationKey(userId);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(key)).thenReturn(null);

    String gen = invalidationService.getOrCreateGeneration(userId);

    assertThat(gen).isNotBlank();
    verify(valueOperations).set(eq(key), eq(gen), eq(UserTokenInvalidationServiceImpl.GENERATION_TTL));
  }

  @Test
  @DisplayName("isCurrentGeneration should fail closed when Redis key is missing")
  void isCurrentGenerationShouldFailClosedWhenMissing() {
    UUID userId = UUID.randomUUID();
    String key = RedisKeyConstants.sessionGenerationKey(userId);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(key)).thenReturn(null);

    boolean valid = invalidationService.isCurrentGeneration(userId, "any-gen");

    assertThat(valid).isFalse();
  }

  @Test
  @DisplayName("isCurrentGeneration should return true when generation matches")
  void isCurrentGenerationShouldReturnTrueWhenMatching() {
    UUID userId = UUID.randomUUID();
    String key = RedisKeyConstants.sessionGenerationKey(userId);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(key)).thenReturn("current-gen");

    assertThat(invalidationService.isCurrentGeneration(userId, "current-gen")).isTrue();
    assertThat(invalidationService.isCurrentGeneration(userId, "old-gen")).isFalse();
  }

  @Test
  @DisplayName("invalidateAll should store a new generation and revoke all user sessions")
  void invalidateAllShouldRotateGenerationAndRevokeSessions() {
    UUID userId = UUID.randomUUID();
    String key = RedisKeyConstants.sessionGenerationKey(userId);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    invalidationService.invalidateAll(userId);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).set(eq(key), captor.capture(), eq(UserTokenInvalidationServiceImpl.GENERATION_TTL));
    assertThat(captor.getValue()).isNotBlank();
    verify(tokenRevocationService).revokeAllUserSessions(userId);
  }
}
