package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.constants.RedisKeyConstants;
import com.uit.dormmanagement.services.LoginAttemptService;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

  public static final int MAX_ATTEMPTS = 5;
  public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
  public static final Duration ATTEMPTS_TTL = Duration.ofMinutes(15);
  private static final String LOCKED_VALUE = "LOCKED";

  private final StringRedisTemplate redisTemplate;

  @Override
  public void recordFailedAttempt(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return;
    }
    String attemptsKey = RedisKeyConstants.loginAttemptsKey(identifier);
    Long currentAttempts = redisTemplate.opsForValue().increment(attemptsKey);
    if (currentAttempts != null && currentAttempts == 1) {
      redisTemplate.expire(attemptsKey, ATTEMPTS_TTL);
    }

    log.warn(
        "Failed login attempt for identifier: {}, attempt count: {}/{}",
        identifier,
        currentAttempts,
        MAX_ATTEMPTS);

    if (currentAttempts != null && currentAttempts >= MAX_ATTEMPTS) {
      String lockedKey = RedisKeyConstants.loginLockedKey(identifier);
      redisTemplate.opsForValue().set(lockedKey, LOCKED_VALUE, LOCK_DURATION);
      log.warn(
          "Identifier: {} has been locked for 15 minutes due to exceeding max attempts",
          identifier);
    }
  }

  @Override
  public void resetAttempts(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return;
    }
    redisTemplate.delete(RedisKeyConstants.loginAttemptsKey(identifier));
    redisTemplate.delete(RedisKeyConstants.loginLockedKey(identifier));
    log.debug("Reset login attempts and lock status for identifier: {}", identifier);
  }

  @Override
  public boolean isLocked(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return false;
    }
    String lockedKey = RedisKeyConstants.loginLockedKey(identifier);
    return Boolean.TRUE.equals(redisTemplate.hasKey(lockedKey));
  }

  @Override
  public long getRemainingLockMinutes(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return 0L;
    }
    String lockedKey = RedisKeyConstants.loginLockedKey(identifier);
    Long expireSeconds = redisTemplate.getExpire(lockedKey, TimeUnit.SECONDS);
    if (expireSeconds == null || expireSeconds <= 0) {
      return 0L;
    }
    return (expireSeconds + 59) / 60;
  }
}
