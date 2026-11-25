package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest;
import com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest;
import com.uit.dormmanagement.dtos.response.auth.OneTimePasswordResponse;
import com.uit.dormmanagement.dtos.response.auth.UserProfileResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractSummaryResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantSummaryResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import java.math.BigDecimal;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.exceptions.error.WebErrorCode;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.PasswordRecoveryService;
import com.uit.dormmanagement.services.RandomPasswordGenerator;
import com.uit.dormmanagement.services.TenantService;
import com.uit.dormmanagement.services.UserService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final StaffProfileRepository staffProfileRepository;
  private final ContractRepository contractRepository;
  private final VehicleRepository vehicleRepository;
  private final PasswordRecoveryService passwordRecoveryService;
  private final UserTokenInvalidationService userTokenInvalidationService;
  private final RandomPasswordGenerator randomPasswordGenerator;
  private final PasswordEncoder passwordEncoder;
  private final UserService userService;

  private static final List<ContractStatus> ACTIVE_CONTRACT_STATUSES =
      List.of(ContractStatus.ACTIVE);

  @Override
  @Transactional(readOnly = true)
  public Page<TenantSummaryResponse> getTenants(
      Pageable pageable, String search, UUID roomId, ResidentStatus status) {
    String query = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
    Page<Tenant> tenantsPage =
        tenantRepository.findActiveTenantsWithFilters(query, roomId, status, pageable);

    return tenantsPage.map(this::toSummaryResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public TenantDetailResponse getTenantById(UUID id) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    return toDetailResponse(tenant);
  }

  @Override
  @Transactional
  public TenantDetailResponse updateTenantAsManager(
      UUID managerUserId, UUID tenantId, ManagerUpdateTenantRequest request) {
    if (managerUserId != null) {
      staffProfileRepository
          .findByUserId(managerUserId)
          .filter(
              s ->
                  s.getUser().getRole() == UserRole.MANAGER
                      && s.getUser().getStatus() == UserStatus.ACTIVE)
          .orElseThrow(() -> new BaseAppException(WebErrorCode.FORBIDDEN));
    }

    Tenant preRead =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));
    UUID targetUserId = preRead.getUser().getId();

    User lockedUser =
        userRepository
            .findByIdForUpdate(targetUserId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));
    Tenant lockedTenant =
        tenantRepository
            .findByIdForUpdate(tenantId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    if (!lockedTenant.getUser().getId().equals(lockedUser.getId())) {
      throw new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND);
    }

    lockedTenant.setFullName(request.fullName().trim());
    lockedTenant.setPhone(request.phone().trim());
    lockedTenant.setPermanentAddress(request.permanentAddress().trim());
    lockedTenant.setDateOfBirth(request.dateOfBirth());
    lockedTenant.setEmergencyContactName(
        request.emergencyContactName() != null && !request.emergencyContactName().isBlank()
            ? request.emergencyContactName().trim()
            : null);
    lockedTenant.setEmergencyContactPhone(
        request.emergencyContactPhone() != null && !request.emergencyContactPhone().isBlank()
            ? request.emergencyContactPhone().trim()
            : null);

    if (request.idCardNumber() != null && !request.idCardNumber().isBlank()) {
      String cleanCccd = request.idCardNumber().trim();
      if (!cleanCccd.equals(lockedTenant.getIdCardNumber())) {
        if (tenantRepository.existsByIdCardNumber(cleanCccd)) {
          throw new BaseAppException(TenantErrorCode.ID_CARD_NUMBER_DUPLICATED);
        }
        lockedTenant.setIdCardNumber(cleanCccd);
      }
    }

    boolean emailChanged = false;
    if (request.email() != null && !request.email().isBlank()) {
      String cleanEmail = request.email().trim().toLowerCase(Locale.ROOT);
      if (!cleanEmail.equals(lockedUser.getEmail())) {
        if (userRepository.existsByEmail(cleanEmail)) {
          throw new BaseAppException(TenantErrorCode.EMAIL_DUPLICATED);
        }
        lockedUser.setEmail(cleanEmail);
        emailChanged = true;
      }
    }

    if (request.gender() != null && request.gender() != lockedTenant.getGender()) {
      Optional<Contract> activeContractOpt =
          contractRepository.findFirstByTenantIdAndStatusIn(
              tenantId, Set.of(ContractStatus.ACTIVE));
      if (activeContractOpt.isPresent()) {
        Bed activeBed = activeContractOpt.get().getBed();
        Room activeRoom = activeBed != null ? activeBed.getRoom() : null;
        if (activeRoom != null && activeRoom.getGenderPolicy() != request.gender()) {
          throw new BaseAppException(TenantErrorCode.GENDER_MISMATCH_CURRENT_ROOM);
        }
      }
      lockedTenant.setGender(request.gender());
    }

    userRepository.save(lockedUser);
    Tenant saved = tenantRepository.save(lockedTenant);

    if (emailChanged) {
      passwordRecoveryService.invalidateRecoveryCredentials(lockedUser.getId());
    }

    log.info("Manager updated profile for tenant {} ({})", saved.getTenantCode(), saved.getId());
    return toDetailResponse(saved);
  }

  @Override
  @Transactional
  public UserProfileResponse updateOwnContact(
      UUID tenantUserId, TenantContactUpdateRequest request) {
    if (!request.hasAtLeastOneField()) {
      throw new BaseAppException(TenantErrorCode.NO_CONTACT_FIELDS_PROVIDED);
    }

    Tenant tenant =
        tenantRepository
            .findByUserIdForUpdate(tenantUserId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    if (request.phone() != null) {
      if (request.phone().isBlank()) {
        throw new BaseAppException(WebErrorCode.VALIDATION_FAILED);
      }
      tenant.setPhone(request.phone().trim());
    }

    if (request.permanentAddress() != null) {
      if (request.permanentAddress().isBlank()) {
        throw new BaseAppException(WebErrorCode.VALIDATION_FAILED);
      }
      tenant.setPermanentAddress(request.permanentAddress().trim());
    }

    if (request.emergencyContactName() != null) {
      tenant.setEmergencyContactName(
          !request.emergencyContactName().isBlank() ? request.emergencyContactName().trim() : null);
    }

    if (request.emergencyContactPhone() != null) {
      tenant.setEmergencyContactPhone(
          !request.emergencyContactPhone().isBlank()
              ? request.emergencyContactPhone().trim()
              : null);
    }

    tenantRepository.save(tenant);
    return userService.getCurrentUserProfile(tenantUserId);
  }

  @Override
  @Transactional
  public OneTimePasswordResponse resetTenantPasswordAsManager(UUID managerUserId, UUID tenantId) {
    if (managerUserId != null) {
      staffProfileRepository
          .findByUserId(managerUserId)
          .filter(
              s ->
                  s.getUser().getRole() == UserRole.MANAGER
                      && s.getUser().getStatus() == UserStatus.ACTIVE)
          .orElseThrow(() -> new BaseAppException(WebErrorCode.FORBIDDEN));
    }

    Tenant preRead =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));
    UUID targetUserId = preRead.getUser().getId();

    User targetUser =
        userRepository
            .findByIdForUpdate(targetUserId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    if (targetUser.getRole() != UserRole.TENANT) {
      throw new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND);
    }

    String rawPassword = randomPasswordGenerator.generate();
    targetUser.setPasswordHash(passwordEncoder.encode(rawPassword));
    userRepository.save(targetUser);

    passwordRecoveryService.invalidateRecoveryCredentials(targetUserId);
    userTokenInvalidationService.invalidateAll(targetUserId);

    log.info("Manager reset password for tenant: id={}, userId={}", tenantId, targetUserId);
    return new OneTimePasswordResponse(rawPassword);
  }

  private TenantSummaryResponse toSummaryResponse(Tenant tenant) {
    Optional<Contract> activeContractOpt =
        contractRepository.findFirstByTenantIdAndStatusIn(
            tenant.getId(), ACTIVE_CONTRACT_STATUSES);

    String roomNumber = null;
    String bedCode = null;
    java.time.LocalDate contractEndDate = null;
    BigDecimal monthlyRent = null;

    if (activeContractOpt.isPresent()) {
      Contract contract = activeContractOpt.get();
      contractEndDate = contract.getEndDate();
      monthlyRent = contract.getMonthlyRent();
      Bed bed = contract.getBed();
      if (bed != null) {
        bedCode = bed.getBedCode();
        Room room = bed.getRoom();
        if (room != null) {
          roomNumber = room.getRoomNumber();
        }
      }
    }

    return TenantSummaryResponse.builder()
        .id(tenant.getId())
        .tenantCode(tenant.getTenantCode())
        .fullName(tenant.getFullName())
        .gender(tenant.getGender())
        .idCardNumber(tenant.getIdCardNumber())
        .phone(tenant.getPhone())
        .email(tenant.getUser().getEmail())
        .residentStatus(tenant.getResidentStatus())
        .roomNumber(roomNumber)
        .bedCode(bedCode)
        .contractEndDate(contractEndDate)
        .monthlyRent(monthlyRent)
        .build();
  }

  private TenantDetailResponse toDetailResponse(Tenant tenant) {
    Optional<Contract> activeContractOpt =
        contractRepository.findFirstByTenantIdAndStatusIn(
            tenant.getId(), ACTIVE_CONTRACT_STATUSES);

    UUID currentBedId = null;
    String currentBedCode = null;
    UUID currentRoomId = null;
    String currentRoomNumber = null;
    ContractDetailResponse currentContractResponse = null;

    if (activeContractOpt.isPresent()) {
      Contract activeContract = activeContractOpt.get();
      Bed bed = activeContract.getBed();
      if (bed != null) {
        currentBedId = bed.getId();
        currentBedCode = bed.getBedCode();
        Room room = bed.getRoom();
        if (room != null) {
          currentRoomId = room.getId();
          currentRoomNumber = room.getRoomNumber();
        }
      }
      currentContractResponse =
          toContractDetailResponse(activeContract, currentBedCode, currentRoomNumber);
    }

    List<Vehicle> vehicles = vehicleRepository.findByTenantId(tenant.getId());
    List<VehicleResponse> vehicleResponses =
        vehicles.stream().map(v -> toVehicleResponse(v, tenant.getFullName())).toList();

    List<Contract> contractHistory = contractRepository.findByTenantId(tenant.getId());
    List<ContractSummaryResponse> contractHistoryResponses =
        contractHistory.stream().map(this::toContractSummaryResponse).toList();

    return TenantDetailResponse.builder()
        .id(tenant.getId())
        .userId(tenant.getUser() != null ? tenant.getUser().getId() : null)
        .tenantCode(tenant.getTenantCode())
        .fullName(tenant.getFullName())
        .gender(tenant.getGender())
        .idCardNumber(tenant.getIdCardNumber())
        .phone(tenant.getPhone())
        .email(tenant.getUser().getEmail())
        .permanentAddress(tenant.getPermanentAddress())
        .dateOfBirth(tenant.getDateOfBirth())
        .emergencyContactName(tenant.getEmergencyContactName())
        .emergencyContactPhone(tenant.getEmergencyContactPhone())
        .residentStatus(tenant.getResidentStatus())
        .currentBedId(currentBedId)
        .currentBedCode(currentBedCode)
        .currentRoomId(currentRoomId)
        .currentRoomNumber(currentRoomNumber)
        .currentContract(currentContractResponse)
        .vehicles(vehicleResponses)
        .contractHistory(contractHistoryResponses)
        .createdAt(tenant.getCreatedAt())
        .build();
  }

  private ContractDetailResponse toContractDetailResponse(
      Contract contract, String bedCode, String roomNumber) {
    List<ContractDocumentResponse> docResponses =
        contract.getDocuments() != null
            ? contract.getDocuments().stream()
                .map(
                    doc ->
                        ContractDocumentResponse.builder()
                            .id(doc.getId())
                            .contractId(contract.getId())
                            .fileName(doc.getFileName())
                            .fileUrl(doc.getFileUrl())
                            .fileType(doc.getFileType())
                            .fileSize(doc.getFileSize())
                            .documentCategory(doc.getDocumentCategory())
                            .uploadedAt(doc.getUploadedAt())
                            .build())
                .toList()
            : List.of();

    Bed bed = contract.getBed();
    Room room = bed != null ? bed.getRoom() : null;

    return ContractDetailResponse.builder()
        .id(contract.getId())
        .contractCode(contract.getContractCode())
        .tenantId(contract.getTenant() != null ? contract.getTenant().getId() : null)
        .tenantName(contract.getTenant() != null ? contract.getTenant().getFullName() : null)
        .tenantPhone(contract.getTenant() != null ? contract.getTenant().getPhone() : null)
        .bedId(bed != null ? bed.getId() : null)
        .bedCode(bedCode != null ? bedCode : (bed != null ? bed.getBedCode() : null))
        .roomId(room != null ? room.getId() : null)
        .roomNumber(roomNumber != null ? roomNumber : (room != null ? room.getRoomNumber() : null))
        .startDate(contract.getStartDate())
        .endDate(contract.getEndDate())
        .monthlyRent(contract.getMonthlyRent())
        .depositAmount(contract.getDepositAmount())
        .status(contract.getStatus())
        .contractDocumentUrl(contract.getContractDocumentUrl())
        .signedAt(contract.getSignedAt())
        .createdAt(contract.getCreatedAt())
        .documents(docResponses)
        .build();
  }

  private ContractSummaryResponse toContractSummaryResponse(Contract contract) {
    Bed bed = contract.getBed();
    Room room = bed != null ? bed.getRoom() : null;

    return ContractSummaryResponse.builder()
        .id(contract.getId())
        .contractCode(contract.getContractCode())
        .tenantId(contract.getTenant() != null ? contract.getTenant().getId() : null)
        .tenantName(contract.getTenant() != null ? contract.getTenant().getFullName() : null)
        .tenantPhone(contract.getTenant() != null ? contract.getTenant().getPhone() : null)
        .roomNumber(room != null ? room.getRoomNumber() : null)
        .bedCode(bed != null ? bed.getBedCode() : null)
        .startDate(contract.getStartDate())
        .endDate(contract.getEndDate())
        .monthlyRent(contract.getMonthlyRent())
        .depositAmount(contract.getDepositAmount())
        .status(contract.getStatus())
        .build();
  }

  private VehicleResponse toVehicleResponse(Vehicle vehicle, String tenantName) {
    return VehicleResponse.builder()
        .id(vehicle.getId())
        .tenantId(vehicle.getTenant() != null ? vehicle.getTenant().getId() : null)
        .tenantName(tenantName)
        .vehicleType(vehicle.getVehicleType())
        .licensePlate(vehicle.getLicensePlate())
        .brand(vehicle.getBrand())
        .color(vehicle.getColor())
        .cardCode(vehicle.getCardCode())
        .status(vehicle.getStatus())
        .registrationFrontAvailable(
            vehicle.getRegistrationFrontFileKey() != null
                && !vehicle.getRegistrationFrontFileKey().isBlank())
        .registrationBackAvailable(
            vehicle.getRegistrationBackFileKey() != null
                && !vehicle.getRegistrationBackFileKey().isBlank())
        .rejectionReason(vehicle.getRejectionReason())
        .rejectedByStaffId(
            vehicle.getRejectedByStaff() != null ? vehicle.getRejectedByStaff().getId() : null)
        .rejectedAt(vehicle.getRejectedAt())
        .issuedAt(vehicle.getIssuedAt())
        .revokedAt(vehicle.getRevokedAt())
        .cancelledAt(vehicle.getCancelledAt())
        .replacedAt(vehicle.getReplacedAt())
        .replacedVehicleId(
            vehicle.getReplacedVehicle() != null ? vehicle.getReplacedVehicle().getId() : null)
        .replacedByVehicleId(
            vehicle.getReplacedByVehicle() != null
                ? vehicle.getReplacedByVehicle().getId()
                : null)
        .createdAt(vehicle.getCreatedAt())
        .build();
  }
}
