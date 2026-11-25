package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.constants.RedisKeyConstants;
import com.uit.dormmanagement.services.impl.LoginAttemptServiceImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private LoginAttemptServiceImpl loginAttemptService;

  @Test
  @DisplayName("recordFailedAttempt on first failure should increment and set TTL")
  void shouldIncrementAndSetTtlOnFirstAttempt() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String identifier = "manager";
    String attemptsKey = RedisKeyConstants.loginAttemptsKey(identifier);

    when(valueOperations.increment(attemptsKey)).thenReturn(1L);

    loginAttemptService.recordFailedAttempt(identifier);

    verify(valueOperations).increment(eq(attemptsKey));
    verify(redisTemplate).expire(eq(attemptsKey), eq(LoginAttemptServiceImpl.ATTEMPTS_TTL));
  }

  @Test
  @DisplayName("first 5 failures should not lock the account")
  void firstFiveFailuresDoNotLock() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String identifier = "manager";
    String attemptsKey = RedisKeyConstants.loginAttemptsKey(identifier);
    String lockedKey = RedisKeyConstants.loginLockedKey(identifier);

    for (long attempt = 1; attempt <= 5; attempt++) {
      when(valueOperations.increment(attemptsKey)).thenReturn(attempt);
      loginAttemptService.recordFailedAttempt(identifier);
    }

    verify(valueOperations, never()).set(eq(lockedKey), anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("sixth consecutive failure should lock the account for 15 minutes")
  void sixthFailureCreatesFifteenMinuteLock() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String identifier = "manager";
    String attemptsKey = RedisKeyConstants.loginAttemptsKey(identifier);
    String lockedKey = RedisKeyConstants.loginLockedKey(identifier);

    when(valueOperations.increment(attemptsKey)).thenReturn(6L);

    loginAttemptService.recordFailedAttempt(identifier);

    verify(valueOperations).set(eq(lockedKey), eq("LOCKED"), eq(Duration.ofMinutes(15)));
  }

  @Test
  @DisplayName("canonical email and CCCD produce consistent keys")
  void canonicalIdentifierKeysTest() {
    String mixedEmail = "  Manager@Dorm.VN  ";
    String cccd = " 079203001187 ";

    assertThat(RedisKeyConstants.loginAttemptsKey(mixedEmail))
        .isEqualTo("dorm:auth:login:attempts:manager@dorm.vn");
    assertThat(RedisKeyConstants.loginLockedKey(mixedEmail))
        .isEqualTo("dorm:auth:login:locked:manager@dorm.vn");

    assertThat(RedisKeyConstants.loginAttemptsKey(cccd))
        .isEqualTo("dorm:auth:login:attempts:079203001187");
    assertThat(RedisKeyConstants.loginLockedKey(cccd))
        .isEqualTo("dorm:auth:login:locked:079203001187");
  }

  @Test
  @DisplayName("resetAttempts should delete both attempts key and locked key")
  void shouldResetAttemptsAndLock() {
    String identifier = "manager";
    loginAttemptService.resetAttempts(identifier);

    verify(redisTemplate).delete(eq(RedisKeyConstants.loginAttemptsKey(identifier)));
    verify(redisTemplate).delete(eq(RedisKeyConstants.loginLockedKey(identifier)));
  }

  @Test
  @DisplayName("isLocked should return true when lock key exists")
  void shouldReturnTrueWhenLocked() {
    String identifier = "manager";
    when(redisTemplate.hasKey(RedisKeyConstants.loginLockedKey(identifier))).thenReturn(true);

    boolean locked = loginAttemptService.isLocked(identifier);
    assertThat(locked).isTrue();
  }

  @Test
  @DisplayName("getRemainingLockMinutes should calculate minutes correctly")
  void shouldCalculateRemainingMinutes() {
    String identifier = "manager";
    String lockedKey = RedisKeyConstants.loginLockedKey(identifier);
    when(redisTemplate.getExpire(lockedKey, TimeUnit.SECONDS)).thenReturn(850L); // ~15 mins

    long minutes = loginAttemptService.getRemainingLockMinutes(identifier);
    assertThat(minutes).isEqualTo(15L);
  }
}
