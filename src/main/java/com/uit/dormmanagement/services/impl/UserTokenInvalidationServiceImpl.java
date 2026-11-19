package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.constants.RedisKeyConstants;
import com.uit.dormmanagement.services.TokenRevocationService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserTokenInvalidationServiceImpl implements UserTokenInvalidationService {

  public static final Duration GENERATION_TTL = Duration.ofDays(14);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final StringRedisTemplate redisTemplate;
  private final TokenRevocationService tokenRevocationService;

  @Override
  public String getOrCreateGeneration(UUID userId) {
    if (userId == null) {
      throw new IllegalArgumentException("User ID cannot be null");
    }
    String key = RedisKeyConstants.sessionGenerationKey(userId);
    String existing = redisTemplate.opsForValue().get(key);
    if (existing != null && !existing.isBlank()) {
      redisTemplate.expire(key, GENERATION_TTL);
      return existing;
    }
    String newGeneration = generateRandomGeneration();
    redisTemplate.opsForValue().set(key, newGeneration, GENERATION_TTL);
    log.debug("Created session generation for user: {}", userId);
    return newGeneration;
  }

  @Override
  public boolean isCurrentGeneration(UUID userId, String generation) {
    if (userId == null || generation == null || generation.isBlank()) {
      return false;
    }
    String key = RedisKeyConstants.sessionGenerationKey(userId);
    String current = redisTemplate.opsForValue().get(key);
    if (current == null || current.isBlank()) {
      log.debug("Session generation key missing in Redis for user: {}", userId);
      return false;
    }
    return generation.equals(current);
  }

  @Override
  public void invalidateAll(UUID userId) {
    if (userId == null) {
      return;
    }
    String key = RedisKeyConstants.sessionGenerationKey(userId);
    String newGeneration = generateRandomGeneration();
    redisTemplate.opsForValue().set(key, newGeneration, GENERATION_TTL);
    tokenRevocationService.revokeAllUserSessions(userId);
    log.info("Invalidated all tokens for user: {}", userId);
  }

  private String generateRandomGeneration() {
    byte[] randomBytes = new byte[32];
    SECURE_RANDOM.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}
