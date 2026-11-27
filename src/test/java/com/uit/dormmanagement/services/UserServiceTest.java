package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uit.dormmanagement.dtos.request.auth.CreateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianStatusRequest;
import com.uit.dormmanagement.dtos.response.auth.CreateTechnicianResponse;
import com.uit.dormmanagement.dtos.response.auth.OneTimePasswordResponse;
import com.uit.dormmanagement.dtos.response.auth.TechnicianResponse;
import com.uit.dormmanagement.dtos.response.auth.UserProfileResponse;
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
import com.uit.dormmanagement.services.impl.UserServiceImpl;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private StaffProfileRepository staffProfileRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TokenRevocationService tokenRevocationService;
  @Mock private UserTokenInvalidationService userTokenInvalidationService;
  @Mock private RandomPasswordGenerator randomPasswordGenerator;
  @Mock private PasswordRecoveryService passwordRecoveryService;

  @InjectMocks private UserServiceImpl userService;

  @Test
  @DisplayName("getCurrentUserProfile should return staff info for MANAGER")
  void shouldReturnStaffProfileForManager() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .email("manager@dorm.vn")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .build();

    StaffProfile staff =
        StaffProfile.builder()
            .id(UUID.randomUUID())
            .user(user)
            .fullName("Nguyễn Văn Quản Lý")
            .phone("0901000001")
            .build();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(staffProfileRepository.findByUserId(userId)).thenReturn(Optional.of(staff));

    UserProfileResponse response = userService.getCurrentUserProfile(userId);

    assertThat(response.id()).isEqualTo(userId);
    assertThat(response.email()).isEqualTo("manager@dorm.vn");
    assertThat(response.role()).isEqualTo(UserRole.MANAGER);
    assertThat(response.staffProfile()).isNotNull();
    assertThat(response.staffProfile().fullName()).isEqualTo("Nguyễn Văn Quản Lý");
    assertThat(response.tenantProfile()).isNull();
  }

  @Test
  @DisplayName("getCurrentUserProfile should return tenant info for TENANT")
  void shouldReturnTenantProfileForTenant() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .email("tenant@dorm.vn")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();

    Tenant tenant =
        Tenant.builder()
            .id(UUID.randomUUID())
            .user(user)
            .tenantCode("TN-0001")
            .fullName("Lê Khách Thuê")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079201000001")
            .phone("0903000003")
            .permanentAddress("Thủ Đức")
            .dateOfBirth(LocalDate.of(2001, 5, 15))
            .residentStatus(ResidentStatus.ACTIVE)
            .build();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(tenantRepository.findByUserId(userId)).thenReturn(Optional.of(tenant));

    UserProfileResponse response = userService.getCurrentUserProfile(userId);

    assertThat(response.id()).isEqualTo(userId);
    assertThat(response.role()).isEqualTo(UserRole.TENANT);
    assertThat(response.tenantProfile()).isNotNull();
    assertThat(response.tenantProfile().tenantCode()).isEqualTo("TN-0001");
    assertThat(response.tenantProfile().idCardNumber()).isEqualTo("079201000001");
    assertThat(response.staffProfile()).isNull();
  }

  @Test
  @DisplayName("getCurrentUserProfile should throw USER_NOT_FOUND when user does not exist")
  void shouldThrowWhenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getCurrentUserProfile(userId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("createTechnician should generate random password, create user and staff profile")
  void shouldCreateTechnicianSuccessfully() {
    CreateTechnicianRequest request =
        new CreateTechnicianRequest("Võ Văn Thịnh", "thinh.vv@dorm.vn", "0908112245");

    when(userRepository.existsByEmail("thinh.vv@dorm.vn")).thenReturn(false);
    when(randomPasswordGenerator.generate()).thenReturn("GenPassword@123");
    when(passwordEncoder.encode("GenPassword@123")).thenReturn("$2a$12$encodedHash");

    User savedUser =
        User.builder()
            .id(UUID.randomUUID())
            .email("thinh.vv@dorm.vn")
            .passwordHash("$2a$12$encodedHash")
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);

    StaffProfile savedProfile =
        StaffProfile.builder()
            .id(UUID.randomUUID())
            .user(savedUser)
            .fullName("Võ Văn Thịnh")
            .phone("0908112245")
            .createdAt(Instant.now())
            .build();
    when(staffProfileRepository.save(any(StaffProfile.class))).thenReturn(savedProfile);

    CreateTechnicianResponse response = userService.createTechnician(request);

    assertThat(response.initialPassword()).isEqualTo("GenPassword@123");
    assertThat(response.technician().fullName()).isEqualTo("Võ Văn Thịnh");
    assertThat(response.technician().email()).isEqualTo("thinh.vv@dorm.vn");
    assertThat(response.technician().userStatus()).isEqualTo(UserStatus.ACTIVE);
    verify(userRepository).saveAndFlush(any(User.class));
    verify(staffProfileRepository).save(any(StaffProfile.class));
  }

  @Test
  @DisplayName(
      "resetTechnicianPassword should generate temporary password, update hash, advance recovery version, and invalidate all sessions")
  void shouldResetTechnicianPasswordSuccessfully() {
    UUID staffId = UUID.randomUUID();
    UUID techUserId = UUID.randomUUID();
    User techUser =
        User.builder()
            .id(techUserId)
            .email("thinh.vv@dorm.vn")
            .passwordHash("$2a$12$oldHash")
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    StaffProfile profile =
        StaffProfile.builder()
            .id(staffId)
            .user(techUser)
            .fullName("Võ Văn Thịnh")
            .phone("0908112245")
            .build();

    when(staffProfileRepository.findByIdAndUser_Role(staffId, UserRole.TECHNICIAN))
        .thenReturn(Optional.of(profile));
    when(userRepository.findByIdForUpdate(techUserId)).thenReturn(Optional.of(techUser));
    when(randomPasswordGenerator.generate()).thenReturn("TempPass@123");
    when(passwordEncoder.encode("TempPass@123")).thenReturn("$2a$12$newTempHash");

    OneTimePasswordResponse response = userService.resetTechnicianPassword(staffId);

    assertThat(response.temporaryPassword()).isEqualTo("TempPass@123");
    assertThat(techUser.getPasswordHash()).isEqualTo("$2a$12$newTempHash");
    verify(userRepository).save(techUser);
    verify(passwordRecoveryService).invalidateRecoveryCredentials(techUserId);
    verify(userTokenInvalidationService).invalidateAll(techUserId);
  }

  @Test
  @DisplayName("createTechnician should throw DUPLICATE_EMAIL when email already exists")
  void shouldRejectDuplicateEmail() {
    CreateTechnicianRequest request =
        new CreateTechnicianRequest("Thợ Điện", "thinh.vv@dorm.vn", "0908112245");
    when(userRepository.existsByEmail("thinh.vv@dorm.vn")).thenReturn(true);

    assertThatThrownBy(() -> userService.createTechnician(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DUPLICATE_EMAIL);
  }

  @Test
  @DisplayName("getTechnicians should return paginated list")
  void shouldReturnPaginatedTechnicians() {
    Pageable pageable = PageRequest.of(0, 10);
    User user =
        User.builder()
            .id(UUID.randomUUID())
            .email("thinh.vv@dorm.vn")
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    StaffProfile profile =
        StaffProfile.builder()
            .id(UUID.randomUUID())
            .user(user)
            .fullName("Võ Văn Thịnh")
            .phone("0908112245")
            .build();

    Page<StaffProfile> page = new PageImpl<>(List.of(profile), pageable, 1);
    when(staffProfileRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
        .thenReturn(page);

    Page<TechnicianResponse> responsePage = userService.getTechnicians(pageable, true, "thinh");

    assertThat(responsePage.getTotalElements()).isEqualTo(1);
    assertThat(responsePage.getContent().get(0).fullName()).isEqualTo("Võ Văn Thịnh");
  }

  @Test
  @DisplayName("updateTechnicianStatus to false should lock user and revoke all sessions")
  void shouldLockTechnicianAndRevokeSessions() {
    UUID staffId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .email("thinh.vv@dorm.vn")
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    StaffProfile profile =
        StaffProfile.builder()
            .id(staffId)
            .user(user)
            .fullName("Võ Văn Thịnh")
            .phone("0908112245")
            .build();

    when(staffProfileRepository.findByIdAndUser_Role(staffId, UserRole.TECHNICIAN))
        .thenReturn(Optional.of(profile));
    when(staffProfileRepository.save(any(StaffProfile.class))).thenReturn(profile);

    TechnicianResponse response =
        userService.updateTechnicianStatus(staffId, new UpdateTechnicianStatusRequest(false));

    assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    verify(userRepository).save(user);
    verify(userTokenInvalidationService).invalidateAll(userId);
  }

  @Test
  @DisplayName("updateTechnician should update profile and user email and phone")
  void shouldUpdateTechnicianProfileSuccessfully() {
    UUID staffId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .email("thinh.vv@dorm.vn")
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    StaffProfile profile =
        StaffProfile.builder()
            .id(staffId)
            .user(user)
            .fullName("Võ Văn Thịnh")
            .phone("0908112245")
            .build();

    UpdateTechnicianRequest request =
        new UpdateTechnicianRequest("Võ Văn Thịnh Updated", "thinh.new@dorm.vn", "0909999888");

    when(staffProfileRepository.findByIdAndUser_Role(staffId, UserRole.TECHNICIAN))
        .thenReturn(Optional.of(profile));
    when(userRepository.existsByEmail("thinh.new@dorm.vn")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(staffProfileRepository.save(any(StaffProfile.class))).thenReturn(profile);

    TechnicianResponse response = userService.updateTechnician(staffId, request);

    assertThat(profile.getFullName()).isEqualTo("Võ Văn Thịnh Updated");
    assertThat(user.getEmail()).isEqualTo("thinh.new@dorm.vn");
    assertThat(profile.getPhone()).isEqualTo("0909999888");
    verify(userRepository).saveAndFlush(user);
    verify(staffProfileRepository).save(profile);
  }

  @Test
  @DisplayName(
      "createTechnician and updateTechnician logs mask email, phone, and generated password")
  void technicianLogs_shouldMaskEmailPhoneAndPassword() {
    Logger logger = (Logger) LoggerFactory.getLogger(UserServiceImpl.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);

    try {
      String rawEmail = "tech.secret@dorm.vn";
      String rawPhone = "0908112245";
      String rawPassword = "GenSecretPassword@9";

      when(userRepository.existsByEmail(rawEmail)).thenReturn(false);
      when(randomPasswordGenerator.generate()).thenReturn(rawPassword);
      when(passwordEncoder.encode(rawPassword)).thenReturn("hashed");
      User savedUser =
          User.builder()
              .id(UUID.randomUUID())
              .email(rawEmail)
              .role(UserRole.TECHNICIAN)
              .status(UserStatus.ACTIVE)
              .build();
      when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
      when(staffProfileRepository.save(any(StaffProfile.class)))
          .thenReturn(
              StaffProfile.builder()
                  .id(UUID.randomUUID())
                  .user(savedUser)
                  .fullName("Tech")
                  .phone(rawPhone)
                  .build());

      userService.createTechnician(new CreateTechnicianRequest("Tech", rawEmail, rawPhone));

      UUID staffId = UUID.randomUUID();
      StaffProfile existing =
          StaffProfile.builder()
              .id(staffId)
              .user(savedUser)
              .fullName("Tech")
              .phone(rawPhone)
              .build();
      when(staffProfileRepository.findByIdAndUser_Role(staffId, UserRole.TECHNICIAN))
          .thenReturn(Optional.of(existing));
      String updatedEmail = "updated.tech@dorm.vn";
      String updatedPhone = "0909777888";
      when(userRepository.existsByEmail(updatedEmail)).thenReturn(false);

      userService.updateTechnician(
          staffId, new UpdateTechnicianRequest("Tech 2", updatedEmail, updatedPhone));

      List<String> messages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("t***@dorm.vn"));
      assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("u***@dorm.vn"));
      assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("***888"));
      assertThat(messages)
          .noneMatch(
              msg ->
                  msg.contains(rawEmail)
                      || msg.contains(updatedEmail)
                      || msg.contains(rawPhone)
                      || msg.contains(updatedPhone)
                      || msg.contains(rawPassword));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  @DisplayName(
      "createTechnician and updateTechnician map uq_users_email_canonical race to DUPLICATE_EMAIL while rethrowing CHECK")
  void technicianEmailConstraintRaceClassification() {
    CreateTechnicianRequest createReq =
        new CreateTechnicianRequest("Thợ Điện", "race.tech@dorm.vn", "0908112245");
    when(userRepository.existsByEmail("race.tech@dorm.vn")).thenReturn(false);
    when(randomPasswordGenerator.generate()).thenReturn("GenPassword@123");
    when(passwordEncoder.encode("GenPassword@123")).thenReturn("$2a$12$encodedHash");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "dup",
                new ConstraintViolationException(
                    "dup", new SQLException("dup", "23505"), "uq_users_email_canonical")));

    assertThatThrownBy(() -> userService.createTechnician(createReq))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DUPLICATE_EMAIL);

    UUID staffId = UUID.randomUUID();
    User techUser =
        User.builder()
            .id(UUID.randomUUID())
            .email("old@dorm.vn")
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    StaffProfile profile =
        StaffProfile.builder()
            .id(staffId)
            .user(techUser)
            .fullName("Tech")
            .phone("0908112245")
            .build();
    when(staffProfileRepository.findByIdAndUser_Role(staffId, UserRole.TECHNICIAN))
        .thenReturn(Optional.of(profile));
    when(userRepository.existsByEmail("new.race@dorm.vn")).thenReturn(false);

    UpdateTechnicianRequest updateReq =
        new UpdateTechnicianRequest("Tech", "new.race@dorm.vn", "0908112245");
    assertThatThrownBy(() -> userService.updateTechnician(staffId, updateReq))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DUPLICATE_EMAIL);

    // CHECK 23514 must propagate instead of converting to DUPLICATE_EMAIL
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "check",
                new ConstraintViolationException(
                    "check", new SQLException("check", "23514"), "ck_users_email_canonical")));
    assertThatThrownBy(() -> userService.updateTechnician(staffId, updateReq))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
