package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.auth.CreateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianStatusRequest;
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
import com.uit.dormmanagement.services.TokenRevocationService;
import com.uit.dormmanagement.services.UserService;
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
                  staff.getId(), staff.getFullName(), staff.getPhone(), staff.isActive());
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
                  tenant.getEmail(),
                  tenant.getPermanentAddress(),
                  tenant.getDateOfBirth(),
                  tenant.getEmergencyContactName(),
                  tenant.getEmergencyContactPhone(),
                  tenant.getResidentStatus());
        }
      }
    }

    return new UserProfileResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getPhone(),
        user.getRole(),
        user.getStatus(),
        staffInfo,
        tenantInfo);
  }

  @Override
  @Transactional
  public TechnicianResponse createTechnician(CreateTechnicianRequest request) {
    String email = request.email().trim().toLowerCase();
    String phone = request.phone().trim();

    if (userRepository.existsByEmail(email)) {
      throw new BaseAppException(AuthErrorCode.DUPLICATE_EMAIL);
    }
    if (userRepository.existsByPhone(phone)) {
      throw new BaseAppException(AuthErrorCode.DUPLICATE_PHONE);
    }

    String baseUsername = email.split("@")[0].toLowerCase().replaceAll("[^a-zA-Z0-9._-]", "");
    if (baseUsername.isBlank()) {
      baseUsername = "technician";
    }
    String username = baseUsername;
    int count = 1;
    while (userRepository.existsByUsername(username)) {
      username = baseUsername + count++;
    }

    User user =
        User.builder()
            .username(username)
            .email(email)
            .phone(phone)
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.ACTIVE)
            .build();
    User savedUser = userRepository.save(user);

    StaffProfile profile =
        StaffProfile.builder()
            .user(savedUser)
            .fullName(request.fullName().trim())
            .phone(phone)
            .active(true)
            .build();
    StaffProfile savedProfile = staffProfileRepository.save(profile);

    log.info(
        "Manager created new technician account: id={}, username={}, email={}",
        savedProfile.getId(),
        savedUser.getUsername(),
        savedUser.getEmail());

    return TechnicianResponse.fromEntity(savedProfile);
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
            predicates.add(cb.equal(root.get("active"), active));
          }

          if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            Predicate nameMatch = cb.like(cb.lower(root.get("fullName")), pattern);
            Predicate emailMatch = cb.like(cb.lower(userJoin.get("email")), pattern);
            Predicate phoneMatch = cb.like(userJoin.get("phone"), "%" + search.trim() + "%");
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

    profile.setActive(newActive);
    User user = profile.getUser();
    if (user != null) {
      user.setStatus(newActive ? UserStatus.ACTIVE : UserStatus.LOCKED);
      userRepository.save(user);

      if (!newActive) {
        tokenRevocationService.revokeAllUserSessions(user.getId());
        log.info("Locked technician and revoked all sessions: user id={}", user.getId());
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

    if (!newPhone.equals(user.getPhone()) && userRepository.existsByPhone(newPhone)) {
      throw new BaseAppException(AuthErrorCode.DUPLICATE_PHONE);
    }

    user.setEmail(newEmail);
    user.setPhone(newPhone);
    userRepository.save(user);

    profile.setFullName(newFullName);
    profile.setPhone(newPhone);
    StaffProfile savedProfile = staffProfileRepository.save(profile);

    log.info(
        "Manager updated technician profile: id={}, email={}, phone={}", id, newEmail, newPhone);
    return TechnicianResponse.fromEntity(savedProfile);
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
