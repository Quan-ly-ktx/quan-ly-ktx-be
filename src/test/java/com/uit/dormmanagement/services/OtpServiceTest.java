package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uit.dormmanagement.constants.RedisKeyConstants;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.AuthErrorCode;
import com.uit.dormmanagement.services.impl.OtpServiceImpl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private OtpServiceImpl otpService;

  @Test
  @DisplayName("generateAndStoreOtp should generate 6-digit OTP, store in Redis with cooldown")
  void shouldGenerateAndStoreOtp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String email = "test@dorm.vn";

    String otp = otpService.generateAndStoreOtp(email);

    assertThat(otp).matches("^[0-9]{6}$");
    verify(valueOperations)
        .set(eq(RedisKeyConstants.otpKey(email)), eq(otp), eq(OtpServiceImpl.OTP_TTL));
    verify(valueOperations)
        .set(
            eq(RedisKeyConstants.otpCooldownKey(email)),
            eq("SENT"),
            eq(OtpServiceImpl.OTP_COOLDOWN_TTL));
  }

  @Test
  @DisplayName("verifyOtp with matching OTP should return true and delete keys")
  void shouldVerifyOtpSuccessfully() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String email = "test@dorm.vn";
    String otp = "123456";
    String otpKey = RedisKeyConstants.otpKey(email);
    String attemptsKey = RedisKeyConstants.otpAttemptsKey(email);

    when(valueOperations.get(attemptsKey)).thenReturn("0");
    when(valueOperations.get(otpKey)).thenReturn("123456");

    boolean result = otpService.verifyOtp(email, otp);

    assertThat(result).isTrue();
    verify(redisTemplate).delete(eq(otpKey));
    verify(redisTemplate).delete(eq(attemptsKey));
  }

  @Test
  @DisplayName("verifyOtp with incorrect OTP should increment attempt count and return false")
  void shouldFailOnIncorrectOtp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String email = "test@dorm.vn";
    String attemptsKey = RedisKeyConstants.otpAttemptsKey(email);

    when(valueOperations.get(attemptsKey)).thenReturn("1");
    when(valueOperations.get(RedisKeyConstants.otpKey(email))).thenReturn("123456");
    when(valueOperations.increment(attemptsKey)).thenReturn(2L);

    boolean result = otpService.verifyOtp(email, "999999");

    assertThat(result).isFalse();
    verify(valueOperations).increment(eq(attemptsKey));
  }

  @Test
  @DisplayName("verifyOtp should throw BaseAppException when attempts >= 3")
  void shouldThrowWhenMaxAttemptsExceeded() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String email = "test@dorm.vn";
    String attemptsKey = RedisKeyConstants.otpAttemptsKey(email);

    when(valueOperations.get(attemptsKey)).thenReturn("3");

    assertThatThrownBy(() -> otpService.verifyOtp(email, "123456"))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
  }

  @Test
  @DisplayName("checkCooldown should throw BaseAppException if cooldown key exists")
  void shouldThrowWhenCooldownActive() {
    String email = "test@dorm.vn";
    when(redisTemplate.hasKey(RedisKeyConstants.otpCooldownKey(email))).thenReturn(true);

    assertThatThrownBy(() -> otpService.checkCooldown(email))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.OTP_COOLDOWN_ACTIVE);
  }

  @Test
  @DisplayName("password reset token workflow should generate and verify token")
  void shouldHandlePasswordResetTokenWorkflow() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String email = "test@dorm.vn";
    String key = RedisKeyConstants.passwordResetTokenKey(email);

    String token = otpService.generateAndStorePasswordResetToken(email);
    assertThat(token).isNotBlank();

    when(valueOperations.get(key)).thenReturn(token);

    boolean valid = otpService.verifyPasswordResetToken(email, token);
    assertThat(valid).isTrue();
    verify(redisTemplate).delete(eq(key));
  }

  @Test
  @DisplayName("logs should mask email and never emit raw email or OTP")
  void logsShouldMaskEmailAndOmitOtp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String rawEmail = "tenant.secret@dorm.vn";

    Logger logger = (Logger) LoggerFactory.getLogger(OtpServiceImpl.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      String otp = otpService.generateAndStoreOtp(rawEmail);

      String otpKey = RedisKeyConstants.otpKey(rawEmail);
      String attemptsKey = RedisKeyConstants.otpAttemptsKey(rawEmail);
      when(valueOperations.get(attemptsKey)).thenReturn("0");
      when(valueOperations.get(otpKey)).thenReturn(otp);
      otpService.verifyOtp(rawEmail, "000000");
      otpService.verifyOtp(rawEmail, otp);

      String token = otpService.generateAndStorePasswordResetToken(rawEmail);
      when(valueOperations.get(RedisKeyConstants.passwordResetTokenKey(rawEmail)))
          .thenReturn(token);
      otpService.verifyPasswordResetToken(rawEmail, token);

      List<String> messages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("t***@dorm.vn"));
      assertThat(messages)
          .noneMatch(msg -> msg.contains(rawEmail) || msg.contains(otp) || msg.contains(token));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
