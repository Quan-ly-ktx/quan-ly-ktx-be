package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.constants.RedisKeyConstants;
import com.uit.dormmanagement.dtos.response.auth.PasswordRecoveryRequestedResponse;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.AuthErrorCode;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.impl.PasswordRecoveryServiceImpl;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private UserRepository userRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private StaffProfileRepository staffProfileRepository;
  @Mock private EmailService emailService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserTokenInvalidationService userTokenInvalidationService;

  @InjectMocks private PasswordRecoveryServiceImpl recoveryService;

  private User managerUser;
  private UUID managerId;

  @BeforeEach
  void setUp() {
    managerId = UUID.randomUUID();
    managerUser =
        User.builder()
            .id(managerId)
            .email("manager@dorm.vn")
            .passwordHash("$2a$12$oldHashedPassword")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .build();
  }

  @Test
  @DisplayName("request should return indistinguishable response when user is not found")
  void requestShouldReturnIndistinguishableResponseForMissingUser() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.hasKey(anyString())).thenReturn(false);
    when(valueOperations.increment(anyString())).thenReturn(1L);
    when(userRepository.findByEmail("unknown@dorm.vn")).thenReturn(Optional.empty());

    PasswordRecoveryRequestedResponse response = recoveryService.request("unknown@dorm.vn");

    assertThat(response.recoveryRequestId()).isNotNull();
    assertThat(response.expiresInSeconds()).isEqualTo(300L);
    verify(emailService, never()).sendForgotPasswordOtp(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("request should send OTP email and return response when manager user exists")
  void requestShouldSendOtpForValidManager() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.hasKey(anyString())).thenReturn(false);
    when(valueOperations.increment(anyString())).thenReturn(1L);
    when(userRepository.findByEmail("manager@dorm.vn")).thenReturn(Optional.of(managerUser));
    when(staffProfileRepository.findByUserId(managerId))
        .thenReturn(Optional.of(StaffProfile.builder().fullName("Quản lý A").build()));

    PasswordRecoveryRequestedResponse response = recoveryService.request("manager@dorm.vn");

    assertThat(response.recoveryRequestId()).isNotNull();
    assertThat(response.expiresInSeconds()).isEqualTo(300L);
    verify(emailService).sendForgotPasswordOtp(eq("manager@dorm.vn"), eq("Quản lý A"), anyString());
  }

  @Test
  @DisplayName("request should reject when cooldown is active")
  void requestShouldRejectWhenCooldownActive() {
    when(redisTemplate.hasKey(anyString())).thenReturn(true);

    assertThatThrownBy(() -> recoveryService.request("manager@dorm.vn"))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.OTP_COOLDOWN_ACTIVE);
  }

  @Test
  @DisplayName("verify should reject with 400 when OTP is incorrect on first attempt")
  void verifyShouldRejectWrongOtpFirstAttempt() {
    UUID reqId = UUID.randomUUID();
    String key = RedisKeyConstants.recoveryChallengeKey(reqId);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    // userId:version:expectedHash:attempts
    when(valueOperations.get(key)).thenReturn(managerId + ":1:differentHash:0");
    when(valueOperations.get(RedisKeyConstants.userRecoveryVersionKey(managerId))).thenReturn("1");

    assertThatThrownBy(() -> recoveryService.verify(reqId, "123456"))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_OTP);
  }

  @Test
  @DisplayName("verify should invalidate challenge and throw 429 when OTP fails on 3rd attempt")
  void verifyShouldInvalidateChallengeOnThirdFailure() {
    UUID reqId = UUID.randomUUID();
    String key = RedisKeyConstants.recoveryChallengeKey(reqId);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    // attempts is already 2
    when(valueOperations.get(key)).thenReturn(managerId + ":1:differentHash:2");
    when(valueOperations.get(RedisKeyConstants.userRecoveryVersionKey(managerId))).thenReturn("1");

    assertThatThrownBy(() -> recoveryService.verify(reqId, "123456"))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);

    verify(redisTemplate).delete(key);
  }

  @Test
  @DisplayName("reset should update password, advance recovery version, and invalidate all sessions")
  void resetShouldSucceedAndInvalidateAllSessions() {
    String resetToken = "reset-token-xyz";
    String resetKey = RedisKeyConstants.recoveryResetTokenKey(resetToken);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(resetKey)).thenReturn(managerId + ":1");
    when(userRepository.findByIdForUpdate(managerId)).thenReturn(Optional.of(managerUser));
    when(valueOperations.get(RedisKeyConstants.userRecoveryVersionKey(managerId))).thenReturn("1");
    when(passwordEncoder.matches("NewPassword@123", managerUser.getPasswordHash())).thenReturn(false);
    when(passwordEncoder.encode("NewPassword@123")).thenReturn("$2a$12$newHash");

    recoveryService.reset(resetToken, "NewPassword@123", "NewPassword@123");

    assertThat(managerUser.getPasswordHash()).isEqualTo("$2a$12$newHash");
    verify(userRepository).save(managerUser);
    verify(redisTemplate).delete(resetKey);
    verify(userTokenInvalidationService).invalidateAll(managerId);
  }
}
