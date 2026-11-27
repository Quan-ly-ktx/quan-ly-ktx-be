package com.uit.dormmanagement.services.impl;

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
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.AuthErrorCode;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.PasswordRecoveryService;
import com.uit.dormmanagement.services.RandomPasswordGenerator;
import com.uit.dormmanagement.services.TokenRevocationService;
import com.uit.dormmanagement.services.UserService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final StaffProfileRepository staffProfileRepository;
  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenRevocationService tokenRevocationService;
  private final UserTokenInvalidationService userTokenInvalidationService;
  private final RandomPasswordGenerator randomPasswordGenerator;
  private final PasswordRecoveryService passwordRecoveryService;

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getCurrentUserProfile(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseAppException(AuthErrorCode.USER_NOT_FOUND));

    UserProfileResponse.StaffInfo staffInfo = null;
    UserProfileResponse.TenantInfo tenantInfo = null;

    switch (user.getRole()) {
      case MANAGER, TECHNICIAN -> {
        StaffProfile staff = staffProfileRepository.findByUserId(userId).orElse(null);
        if (staff != null) {
          staffInfo =
              new UserProfileResponse.StaffInfo(
                  staff.getId(), staff.getFullName(), staff.getPhone());
        }
      }
      case TENANT -> {
        Tenant tenant = tenantRepository.findByUserId(userId).orElse(null);
        if (tenant != null) {
          tenantInfo =
              new UserProfileResponse.TenantInfo(
                  tenant.getId(),
                  tenant.getTenantCode(),
                  tenant.getFullName(),
                  tenant.getGender().name(),
                  tenant.getIdCardNumber(),
                  tenant.getPhone(),
                  tenant.getUser().getEmail(),
                  tenant.getPermanentAddress(),
                  tenant.getDateOfBirth(),
                  tenant.getEmergencyContactName(),
                  tenant.getEmergencyContactPhone(),
                  tenant.getResidentStatus());
        }
      }
    }

    return new UserProfileResponse(
        user.getId(), user.getEmail(), user.getRole(), user.getStatus(), staffInfo, tenantInfo);
  }

  @Override
  @Transactional
  public CreateTechnicianResponse createTechnician(CreateTechnicianRequest request) {
    String email = request.email().trim().toLowerCase();
    String phone = request.phone().trim();

    if (userRepository.existsByEmail(email)) {
      throw new BaseAppException(AuthErrorCode.DUPLICATE_EMAIL);
    }

    String generatedPassword = randomPasswordGenerator.generate();
    User user =
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(generatedPassword))
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    User savedUser = userRepository.save(user);

    StaffProfile profile =
        StaffProfile.builder()
            .user(savedUser)
            .fullName(request.fullName().trim())
            .phone(phone)
            .build();
    StaffProfile savedProfile = staffProfileRepository.save(profile);

    log.info(
        "Manager created new technician account: id={}, email={}",
        savedProfile.getId(),
        savedUser.getEmail());

    return new CreateTechnicianResponse(
        TechnicianResponse.fromEntity(savedProfile), generatedPassword);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TechnicianResponse> getTechnicians(Pageable pageable, Boolean active, String search) {
    Specification<StaffProfile> spec =
        (root, query, cb) -> {
          Join<StaffProfile, User> userJoin = root.join("user");
          List<Predicate> predicates = new ArrayList<>();

          predicates.add(cb.equal(userJoin.get("role"), UserRole.TECHNICIAN));

          if (active != null) {
            predicates.add(
                cb.equal(userJoin.get("status"), active ? UserStatus.ACTIVE : UserStatus.LOCKED));
          }

          if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            Predicate nameMatch = cb.like(cb.lower(root.get("fullName")), pattern);
            Predicate emailMatch = cb.like(cb.lower(userJoin.get("email")), pattern);
            Predicate phoneMatch = cb.like(root.get("phone"), "%" + search.trim() + "%");
            predicates.add(cb.or(nameMatch, emailMatch, phoneMatch));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<StaffProfile> page = staffProfileRepository.findAll(spec, pageable);
    return page.map(TechnicianResponse::fromEntity);
  }

  @Override
  @Transactional(readOnly = true)
  public TechnicianResponse getTechnicianById(UUID id) {
    StaffProfile profile = findTechnicianByIdOrUserId(id);
    return TechnicianResponse.fromEntity(profile);
  }

  @Override
  @Transactional
  public TechnicianResponse updateTechnicianStatus(UUID id, UpdateTechnicianStatusRequest request) {
    StaffProfile profile = findTechnicianByIdOrUserId(id);
    boolean newActive = Boolean.TRUE.equals(request.active());

    User user = profile.getUser();
    if (user != null) {
      user.setStatus(newActive ? UserStatus.ACTIVE : UserStatus.LOCKED);
      userRepository.save(user);

      if (!newActive) {
        userTokenInvalidationService.invalidateAll(user.getId());
        log.info("Locked technician and invalidated all sessions: user id={}", user.getId());
      }
    }
    StaffProfile updatedProfile = staffProfileRepository.save(profile);
    log.info("Updated technician status: staff id={}, active={}", id, newActive);
    return TechnicianResponse.fromEntity(updatedProfile);
  }

  @Override
  @Transactional
  public TechnicianResponse updateTechnician(UUID id, UpdateTechnicianRequest request) {
    StaffProfile profile = findTechnicianByIdOrUserId(id);
    User user = profile.getUser();

    String newEmail = request.email().trim().toLowerCase();
    String newPhone = request.phone().trim();
    String newFullName = request.fullName().trim();

    if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
      throw new BaseAppException(AuthErrorCode.DUPLICATE_EMAIL);
    }

    user.setEmail(newEmail);
    userRepository.save(user);

    profile.setFullName(newFullName);
    profile.setPhone(newPhone);
    StaffProfile savedProfile = staffProfileRepository.save(profile);

    log.info(
        "Manager updated technician profile: id={}, email={}, phone={}", id, newEmail, newPhone);
    return TechnicianResponse.fromEntity(savedProfile);
  }

  @Override
  @Transactional
  public OneTimePasswordResponse resetTechnicianPassword(UUID id) {
    StaffProfile profile = findTechnicianByIdOrUserId(id);
    User user = profile.getUser();
    if (user == null) {
      throw new BaseAppException(AuthErrorCode.TECHNICIAN_NOT_FOUND);
    }

    User lockedUser =
        userRepository
            .findByIdForUpdate(user.getId())
            .orElseThrow(() -> new BaseAppException(AuthErrorCode.TECHNICIAN_NOT_FOUND));

    String generatedPassword = randomPasswordGenerator.generate();
    lockedUser.setPasswordHash(passwordEncoder.encode(generatedPassword));
    userRepository.save(lockedUser);

    passwordRecoveryService.invalidateRecoveryCredentials(lockedUser.getId());
    userTokenInvalidationService.invalidateAll(lockedUser.getId());

    log.info(
        "Manager reset password for technician: staff id={}, user id={}", id, lockedUser.getId());
    return new OneTimePasswordResponse(generatedPassword);
  }

  private StaffProfile findTechnicianByIdOrUserId(UUID id) {
    Optional<StaffProfile> profileOpt =
        staffProfileRepository.findByIdAndUser_Role(id, UserRole.TECHNICIAN);
    if (profileOpt.isEmpty()) {
      profileOpt = staffProfileRepository.findByUserId(id);
      if (profileOpt.isPresent() && profileOpt.get().getUser().getRole() != UserRole.TECHNICIAN) {
        profileOpt = Optional.empty();
      }
    }
    return profileOpt.orElseThrow(() -> new BaseAppException(AuthErrorCode.TECHNICIAN_NOT_FOUND));
  }
}
