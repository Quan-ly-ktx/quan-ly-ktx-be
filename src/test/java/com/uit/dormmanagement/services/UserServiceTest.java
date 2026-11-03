package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.auth.CreateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianStatusRequest;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

  @InjectMocks private UserServiceImpl userService;

  @Test
  @DisplayName("getCurrentUserProfile should return staff info for MANAGER")
  void shouldReturnStaffProfileForManager() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .username("manager")
            .email("manager@dorm.vn")
            .phone("0901000001")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .build();

    StaffProfile staff =
        StaffProfile.builder()
            .id(UUID.randomUUID())
            .user(user)
            .fullName("Nguyễn Văn Quản Lý")
            .phone("0901000001")
            .active(true)
            .build();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(staffProfileRepository.findByUserId(userId)).thenReturn(Optional.of(staff));

    UserProfileResponse response = userService.getCurrentUserProfile(userId);

    assertThat(response.id()).isEqualTo(userId);
    assertThat(response.username()).isEqualTo("manager");
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
            .username("tenant")
            .email("tenant@dorm.vn")
            .phone("0903000003")
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
            .email("tenant@dorm.vn")
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
  @DisplayName("createTechnician should create user with TECHNICIAN role and staff profile")
  void shouldCreateTechnicianSuccessfully() {
    CreateTechnicianRequest request =
        new CreateTechnicianRequest(
            "Võ Văn Thịnh", "thinh.vv@dorm.vn", "0908112245", "Password@123");

    when(userRepository.existsByEmail("thinh.vv@dorm.vn")).thenReturn(false);
    when(userRepository.existsByPhone("0908112245")).thenReturn(false);
    when(userRepository.existsByUsername("thinh.vv")).thenReturn(false);
    when(passwordEncoder.encode("Password@123")).thenReturn("$2a$12$encodedHash");

    User savedUser =
        User.builder()
            .id(UUID.randomUUID())
            .username("thinh.vv")
            .email("thinh.vv@dorm.vn")
            .phone("0908112245")
            .passwordHash("$2a$12$encodedHash")
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    when(userRepository.save(any(User.class))).thenReturn(savedUser);

    StaffProfile savedProfile =
        StaffProfile.builder()
            .id(UUID.randomUUID())
            .user(savedUser)
            .fullName("Võ Văn Thịnh")
            .phone("0908112245")
            .active(true)
            .createdAt(Instant.now())
            .build();
    when(staffProfileRepository.save(any(StaffProfile.class))).thenReturn(savedProfile);

    TechnicianResponse response = userService.createTechnician(request);

    assertThat(response.fullName()).isEqualTo("Võ Văn Thịnh");
    assertThat(response.email()).isEqualTo("thinh.vv@dorm.vn");
    assertThat(response.username()).isEqualTo("thinh.vv");
    assertThat(response.active()).isTrue();
    verify(userRepository).save(any(User.class));
    verify(staffProfileRepository).save(any(StaffProfile.class));
  }

  @Test
  @DisplayName("createTechnician should throw DUPLICATE_EMAIL when email already exists")
  void shouldRejectDuplicateEmail() {
    CreateTechnicianRequest request =
        new CreateTechnicianRequest("Thợ Điện", "thinh.vv@dorm.vn", "0908112245", "Password@123");
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
            .username("thinh.vv")
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
            .active(true)
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
            .username("thinh.vv")
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
            .active(true)
            .build();

    when(staffProfileRepository.findByIdAndUser_Role(staffId, UserRole.TECHNICIAN))
        .thenReturn(Optional.of(profile));
    when(staffProfileRepository.save(any(StaffProfile.class))).thenReturn(profile);

    TechnicianResponse response =
        userService.updateTechnicianStatus(staffId, new UpdateTechnicianStatusRequest(false));

    assertThat(profile.isActive()).isFalse();
    assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    verify(userRepository).save(user);
    verify(tokenRevocationService).revokeAllUserSessions(userId);
  }

  @Test
  @DisplayName("updateTechnician should update profile and user email and phone")
  void shouldUpdateTechnicianProfileSuccessfully() {
    UUID staffId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .username("thinh.vv")
            .email("thinh.vv@dorm.vn")
            .phone("0908112245")
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    StaffProfile profile =
        StaffProfile.builder()
            .id(staffId)
            .user(user)
            .fullName("Võ Văn Thịnh")
            .phone("0908112245")
            .active(true)
            .build();

    UpdateTechnicianRequest request =
        new UpdateTechnicianRequest("Võ Văn Thịnh Updated", "thinh.new@dorm.vn", "0909999888");

    when(staffProfileRepository.findByIdAndUser_Role(staffId, UserRole.TECHNICIAN))
        .thenReturn(Optional.of(profile));
    when(userRepository.existsByEmail("thinh.new@dorm.vn")).thenReturn(false);
    when(userRepository.existsByPhone("0909999888")).thenReturn(false);
    when(staffProfileRepository.save(any(StaffProfile.class))).thenReturn(profile);

    TechnicianResponse response = userService.updateTechnician(staffId, request);

    assertThat(profile.getFullName()).isEqualTo("Võ Văn Thịnh Updated");
    assertThat(user.getEmail()).isEqualTo("thinh.new@dorm.vn");
    assertThat(user.getPhone()).isEqualTo("0909999888");
    verify(userRepository).save(user);
    verify(staffProfileRepository).save(profile);
  }
}
