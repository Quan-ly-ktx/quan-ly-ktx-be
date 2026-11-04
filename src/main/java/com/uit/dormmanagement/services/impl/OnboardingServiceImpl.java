package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.onboarding.GeneratedAccountInfo;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.OnboardingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingServiceImpl implements OnboardingService {

  private static final String DEFAULT_PASSWORD = "Password123@";

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final BedRepository bedRepository;
  private final RoomRepository roomRepository;
  private final ContractRepository contractRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public OnboardingResponse onboardTenant(OnboardingRequest request) {
    Bed bed =
        bedRepository
            .findById(request.bedId())
            .orElseThrow(() -> new BaseAppException(RoomErrorCode.BED_NOT_FOUND));

    Room room = bed.getRoom();
    if (room == null) {
      throw new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND);
    }

    if (request.gender() != room.getGenderPolicy()) {
      throw new BaseAppException(ContractErrorCode.GENDER_MISMATCH);
    }

    if (bed.getStatus() != BedStatus.AVAILABLE) {
      throw new BaseAppException(ContractErrorCode.BED_NOT_AVAILABLE);
    }

    if (request.startDate() == null
        || request.endDate() == null
        || !request.endDate().isAfter(request.startDate())) {
      throw new BaseAppException(ContractErrorCode.INVALID_CONTRACT_DATES);
    }

    String idCardNumber = request.idCardNumber().trim();
    String email = request.email().trim().toLowerCase();
    String phone = request.phone().trim();

    Optional<Tenant> existingTenantOpt = tenantRepository.findByIdCardNumber(idCardNumber);
    Tenant tenant;
    User user;

    if (existingTenantOpt.isPresent()) {
      Tenant existingTenant = existingTenantOpt.get();
      if (existingTenant.getResidentStatus() != ResidentStatus.CHECKED_OUT) {
        throw new BaseAppException(TenantErrorCode.ID_CARD_NUMBER_DUPLICATED);
      }

      user = existingTenant.getUser();
      if (user != null) {
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
          throw new BaseAppException(TenantErrorCode.EMAIL_DUPLICATED);
        }
        if (!phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
          throw new BaseAppException(TenantErrorCode.PHONE_DUPLICATED);
        }
        user.setEmail(email);
        user.setUsername(email);
        user.setPhone(phone);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);
      } else {
        if (userRepository.existsByEmail(email)) {
          throw new BaseAppException(TenantErrorCode.EMAIL_DUPLICATED);
        }
        if (userRepository.existsByPhone(phone)) {
          throw new BaseAppException(TenantErrorCode.PHONE_DUPLICATED);
        }
        user =
            User.builder()
                .username(email)
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);
        existingTenant.setUser(user);
      }

      existingTenant.setFullName(request.fullName().trim());
      existingTenant.setGender(request.gender());
      existingTenant.setPhone(phone);
      existingTenant.setEmail(email);
      existingTenant.setPermanentAddress(request.permanentAddress().trim());
      existingTenant.setDateOfBirth(request.dateOfBirth());
      existingTenant.setEmergencyContactName(
          request.emergencyContactName() != null ? request.emergencyContactName().trim() : null);
      existingTenant.setEmergencyContactPhone(
          request.emergencyContactPhone() != null ? request.emergencyContactPhone().trim() : null);
      existingTenant.setResidentStatus(ResidentStatus.ACTIVE);
      tenant = tenantRepository.save(existingTenant);
      log.info(
          "Re-onboarded tenant: id={}, tenantCode={}, idCard={}",
          tenant.getId(),
          tenant.getTenantCode(),
          idCardNumber);
    } else {
      if (tenantRepository.existsByIdCardNumber(idCardNumber)) {
        throw new BaseAppException(TenantErrorCode.ID_CARD_NUMBER_DUPLICATED);
      }
      if (userRepository.existsByEmail(email) || tenantRepository.existsByEmail(email)) {
        throw new BaseAppException(TenantErrorCode.EMAIL_DUPLICATED);
      }
      if (userRepository.existsByPhone(phone) || tenantRepository.existsByPhone(phone)) {
        throw new BaseAppException(TenantErrorCode.PHONE_DUPLICATED);
      }

      user =
          User.builder()
              .username(email)
              .email(email)
              .phone(phone)
              .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
              .role(UserRole.TENANT)
              .status(UserStatus.ACTIVE)
              .build();
      user = userRepository.save(user);

      String tenantCode = String.format("NT-%04d", tenantRepository.findNextTenantCodeSequence());

      tenant =
          Tenant.builder()
              .user(user)
              .tenantCode(tenantCode)
              .fullName(request.fullName().trim())
              .gender(request.gender())
              .idCardNumber(idCardNumber)
              .phone(phone)
              .email(email)
              .permanentAddress(request.permanentAddress().trim())
              .dateOfBirth(request.dateOfBirth())
              .emergencyContactName(
                  request.emergencyContactName() != null
                      ? request.emergencyContactName().trim()
                      : null)
              .emergencyContactPhone(
                  request.emergencyContactPhone() != null
                      ? request.emergencyContactPhone().trim()
                      : null)
              .residentStatus(ResidentStatus.ACTIVE)
              .build();
      tenant = tenantRepository.save(tenant);
      log.info(
          "Created new tenant: id={}, tenantCode={}, idCard={}",
          tenant.getId(),
          tenantCode,
          idCardNumber);
    }

    int startYear = request.startDate().getYear();
    String contractCode =
        String.format(
            "HD-%d-%04d", startYear, contractRepository.findNextContractCodeSequence(startYear));
    BigDecimal monthlyRent = bed.getPrice() != null ? bed.getPrice() : room.getBasePrice();

    Contract contract =
        Contract.builder()
            .contractCode(contractCode)
            .tenant(tenant)
            .bed(bed)
            .startDate(request.startDate())
            .endDate(request.endDate())
            .monthlyRent(monthlyRent)
            .depositAmount(request.depositAmount())
            .status(ContractStatus.ACTIVE)
            .signedAt(Instant.now())
            .build();
    contract = contractRepository.save(contract);
    log.info(
        "Created contract: id={}, contractCode={}, tenantId={}, bedId={}",
        contract.getId(),
        contractCode,
        tenant.getId(),
        bed.getId());

    bed.setStatus(BedStatus.OCCUPIED);
    bedRepository.save(bed);

    room.setOccupiedBeds(room.getOccupiedBeds() + 1);
    roomRepository.save(room);

    ContractDetailResponse contractResponse = toContractDetailResponse(contract, tenant, bed, room);
    TenantDetailResponse tenantResponse =
        toTenantDetailResponse(tenant, user, bed, room, contractResponse);
    GeneratedAccountInfo accountInfo =
        GeneratedAccountInfo.builder()
            .username(user.getUsername())
            .email(user.getEmail())
            .phone(user.getPhone())
            .initialPassword(DEFAULT_PASSWORD)
            .roomBed(room.getRoomNumber() + " - " + bed.getBedCode())
            .build();

    return OnboardingResponse.builder()
        .tenant(tenantResponse)
        .contract(contractResponse)
        .account(accountInfo)
        .build();
  }

  private ContractDetailResponse toContractDetailResponse(
      Contract contract, Tenant tenant, Bed bed, Room room) {
    return ContractDetailResponse.builder()
        .id(contract.getId())
        .contractCode(contract.getContractCode())
        .tenantId(tenant.getId())
        .tenantName(tenant.getFullName())
        .tenantPhone(tenant.getPhone())
        .bedId(bed.getId())
        .bedCode(bed.getBedCode())
        .roomId(room.getId())
        .roomNumber(room.getRoomNumber())
        .startDate(contract.getStartDate())
        .endDate(contract.getEndDate())
        .monthlyRent(contract.getMonthlyRent())
        .depositAmount(contract.getDepositAmount())
        .status(contract.getStatus())
        .contractDocumentUrl(contract.getContractDocumentUrl())
        .signedAt(contract.getSignedAt())
        .createdAt(contract.getCreatedAt())
        .documents(List.of())
        .build();
  }

  private TenantDetailResponse toTenantDetailResponse(
      Tenant tenant, User user, Bed bed, Room room, ContractDetailResponse contractResponse) {
    return TenantDetailResponse.builder()
        .id(tenant.getId())
        .userId(user != null ? user.getId() : null)
        .tenantCode(tenant.getTenantCode())
        .fullName(tenant.getFullName())
        .gender(tenant.getGender())
        .idCardNumber(tenant.getIdCardNumber())
        .phone(tenant.getPhone())
        .email(tenant.getEmail())
        .permanentAddress(tenant.getPermanentAddress())
        .dateOfBirth(tenant.getDateOfBirth())
        .emergencyContactName(tenant.getEmergencyContactName())
        .emergencyContactPhone(tenant.getEmergencyContactPhone())
        .residentStatus(tenant.getResidentStatus())
        .currentBedId(bed.getId())
        .currentBedCode(bed.getBedCode())
        .currentRoomId(room.getId())
        .currentRoomNumber(room.getRoomNumber())
        .currentContract(contractResponse)
        .vehicles(List.of())
        .contractHistory(List.of())
        .createdAt(tenant.getCreatedAt())
        .build();
  }
}
