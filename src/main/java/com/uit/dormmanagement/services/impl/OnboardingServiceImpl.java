package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.onboarding.GeneratedAccountInfo;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingInvoiceSummary;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.DataIntegrityViolationClassifier;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.InvoiceIssuanceService;
import com.uit.dormmanagement.services.OnboardingService;
import com.uit.dormmanagement.services.PasswordRecoveryService;
import com.uit.dormmanagement.services.RandomPasswordGenerator;
import com.uit.dormmanagement.utils.LogMaskingUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final RoomRepository roomRepository;
  private final BedRepository bedRepository;
  private final ContractRepository contractRepository;
  private final RoomCodeRegistryRepository roomCodeRegistryRepository;
  private final InvoiceIssuanceService invoiceIssuanceService;
  private final PasswordRecoveryService passwordRecoveryService;
  private final RandomPasswordGenerator randomPasswordGenerator;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  private record ResolvedTenantAccount(
      Tenant tenant, User user, String initialPassword, boolean emailChanged) {}

  @Override
  @Transactional
  public OnboardingResponse onboardTenant(OnboardingRequest request) {
    BigDecimal normalizedDeposit = validateDatesAndDeposit(request);
    if (request.bedId() == null) {
      throw new BaseAppException(RoomErrorCode.BED_NOT_FOUND);
    }

    Bed preReadBed =
        bedRepository
            .findById(request.bedId())
            .orElseThrow(() -> new BaseAppException(RoomErrorCode.BED_NOT_FOUND));
    UUID roomId = preReadBed.getRoom() != null ? preReadBed.getRoom().getId() : null;
    if (roomId == null) {
      throw new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND);
    }

    // Acquire PESSIMISTIC_WRITE locks in strict Room -> Bed order to avoid deadlocks.
    Room room =
        roomRepository
            .findByIdUnfilteredForUpdate(roomId)
            .orElseThrow(() -> new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND));
    if (room.getArchivedAt() != null) {
      throw new BaseAppException(ContractErrorCode.ROOM_ARCHIVED);
    }

    Bed bed =
        bedRepository
            .findByIdForUpdate(request.bedId())
            .orElseThrow(() -> new BaseAppException(RoomErrorCode.BED_NOT_FOUND));
    if (bed.getStatus() != BedStatus.AVAILABLE) {
      throw new BaseAppException(ContractErrorCode.BED_NOT_AVAILABLE);
    }
    if (contractRepository.existsByBedIdAndStatus(bed.getId(), ContractStatus.ACTIVE)) {
      throw new BaseAppException(ContractErrorCode.BED_ALREADY_RENTED);
    }
    if (request.gender() != room.getGenderPolicy()) {
      throw new BaseAppException(ContractErrorCode.GENDER_MISMATCH);
    }

    BigDecimal monthlyRent = validateAndNormalizeMonthlyRent(room.getBasePrice());

    String idCardNumber = request.idCardNumber().trim();
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    String phone = request.phone().trim();

    Optional<Tenant> existingTenantOpt = tenantRepository.findByIdCardNumber(idCardNumber);
    ResolvedTenantAccount resolved =
        existingTenantOpt.isPresent()
            ? handleReturningTenant(existingTenantOpt.get(), request, email, phone)
            : handleNewTenant(request, idCardNumber, email, phone);

    Tenant tenant = resolved.tenant();
    User user = resolved.user();

    Contract contract = createContract(tenant, bed, room, request, monthlyRent, normalizedDeposit);

    bed.setStatus(BedStatus.OCCUPIED);
    bedRepository.saveAndFlush(bed);

    roomCodeRegistryRepository.markRoomCodesUsedInBusiness(room.getId());

    Invoice firstInvoice =
        invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            contract, contract.getStartOn(), contract.getOriginalMonthlyRent());

    if (resolved.emailChanged()) {
      passwordRecoveryService.invalidateRecoveryCredentials(user.getId());
    }

    ContractDetailResponse contractResponse = toContractDetailResponse(contract, tenant, bed, room);
    TenantDetailResponse tenantResponse =
        toTenantDetailResponse(tenant, user, bed, room, contractResponse);
    GeneratedAccountInfo accountInfo =
        GeneratedAccountInfo.builder()
            .username(tenant.getIdCardNumber())
            .email(user.getEmail())
            .phone(tenant.getPhone())
            .initialPassword(resolved.initialPassword())
            .roomBed(room.getRoomNumber() + " - " + bed.getBedCode())
            .build();
    OnboardingInvoiceSummary invoiceSummary =
        OnboardingInvoiceSummary.builder()
            .id(firstInvoice.getId())
            .invoiceCode(firstInvoice.getInvoiceCode())
            .billingPeriod(firstInvoice.getBillingPeriod())
            .amount(firstInvoice.getAmount())
            .dueOn(firstInvoice.getDueOn())
            .status(firstInvoice.getStatus())
            .build();

    return OnboardingResponse.builder()
        .tenant(tenantResponse)
        .contract(contractResponse)
        .account(accountInfo)
        .firstInvoice(invoiceSummary)
        .build();
  }

  private BigDecimal validateDatesAndDeposit(OnboardingRequest request) {
    LocalDate today = LocalDate.now(clock.withZone(TimeConfig.DORM_ZONE));
    if (today.getDayOfMonth() != 1) {
      throw new BaseAppException(ContractErrorCode.ONBOARDING_ONLY_ON_DAY_ONE);
    }
    if (request.startDate() == null || !request.startDate().equals(today)) {
      throw new BaseAppException(
          ContractErrorCode.INVALID_CONTRACT_DATES,
          "Ngày bắt đầu hợp đồng phải trùng với ngày 01 hiện tại theo múi giờ cơ sở");
    }
    if (request.endDate() == null
        || !request.endDate().isAfter(request.startDate())
        || !isLastDayOfMonth(request.endDate())) {
      throw new BaseAppException(
          ContractErrorCode.INVALID_CONTRACT_DATES,
          "Ngày kết thúc hợp đồng phải sau ngày bắt đầu và là ngày cuối tháng");
    }
    return validateAndNormalizeDeposit(request.depositAmount());
  }

  private static boolean isLastDayOfMonth(LocalDate date) {
    return date.equals(date.withDayOfMonth(date.lengthOfMonth()));
  }

  private static BigDecimal validateAndNormalizeDeposit(BigDecimal depositAmount) {
    if (depositAmount == null
        || depositAmount.compareTo(BigDecimal.ZERO) < 0
        || depositAmount.stripTrailingZeros().scale() > 0) {
      throw new BaseAppException(ContractErrorCode.INVALID_DEPOSIT_AMOUNT);
    }
    BigDecimal normalized = depositAmount.setScale(0, RoundingMode.UNNECESSARY);
    if (normalized.precision() > 14) {
      throw new BaseAppException(ContractErrorCode.INVALID_DEPOSIT_AMOUNT);
    }
    return normalized;
  }

  private static BigDecimal validateAndNormalizeMonthlyRent(BigDecimal basePrice) {
    if (basePrice == null
        || basePrice.compareTo(BigDecimal.ZERO) <= 0
        || basePrice.stripTrailingZeros().scale() > 0) {
      throw new IllegalStateException("Room basePrice must be a positive integer VND value");
    }
    BigDecimal normalized = basePrice.setScale(0, RoundingMode.UNNECESSARY);
    if (normalized.precision() > 14) {
      throw new IllegalStateException("Room basePrice exceeds numeric(14,0)");
    }
    return normalized;
  }

  private ResolvedTenantAccount handleReturningTenant(
      Tenant existingTenant, OnboardingRequest request, String email, String phone) {
    User existingUser = existingTenant.getUser();
    if (existingUser == null || existingUser.getId() == null) {
      throw new IllegalStateException("Existing tenant has no linked user");
    }

    // Lock User -> Tenant in consistent Phase 2 lock order.
    User lockedUser =
        userRepository
            .findByIdForUpdate(existingUser.getId())
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));
    Tenant lockedTenant =
        tenantRepository
            .findByIdForUpdate(existingTenant.getId())
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    if (lockedTenant.getResidentStatus() != ResidentStatus.CHECKED_OUT
        || contractRepository.existsByTenantIdAndStatus(
            lockedTenant.getId(), ContractStatus.ACTIVE)) {
      throw new BaseAppException(ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT);
    }

    if (invoiceIssuanceService.hasUnsettledInvoices(lockedTenant.getId())) {
      throw new BaseAppException(ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES);
    }

    boolean emailChanged = !email.equalsIgnoreCase(lockedUser.getEmail());
    if (emailChanged) {
      if (userRepository.existsByEmail(email)) {
        throw new BaseAppException(TenantErrorCode.EMAIL_DUPLICATED);
      }
      lockedUser.setEmail(email);
    }
    lockedUser.setStatus(UserStatus.ACTIVE);

    lockedTenant.setFullName(request.fullName().trim());
    lockedTenant.setGender(request.gender());
    lockedTenant.setPhone(phone);
    lockedTenant.setPermanentAddress(request.permanentAddress().trim());
    lockedTenant.setDateOfBirth(request.dateOfBirth());
    lockedTenant.setEmergencyContactName(trimToNull(request.emergencyContactName()));
    lockedTenant.setEmergencyContactPhone(trimToNull(request.emergencyContactPhone()));
    lockedTenant.setResidentStatus(ResidentStatus.ACTIVE);

    User savedUser;
    Tenant savedTenant;
    try {
      savedUser = userRepository.saveAndFlush(lockedUser);
      savedTenant = tenantRepository.saveAndFlush(lockedTenant);
    } catch (DataIntegrityViolationException e) {
      throw mapPersistenceConflict(e);
    }

    log.info(
        "Re-onboarded tenant: id={}, tenantCode={}, idCard={}",
        savedTenant.getId(),
        savedTenant.getTenantCode(),
        LogMaskingUtils.maskIdCard(savedTenant.getIdCardNumber()));
    return new ResolvedTenantAccount(savedTenant, savedUser, null, emailChanged);
  }

  private ResolvedTenantAccount handleNewTenant(
      OnboardingRequest request, String idCardNumber, String email, String phone) {
    if (userRepository.existsByEmail(email)) {
      throw new BaseAppException(TenantErrorCode.EMAIL_DUPLICATED);
    }

    String rawPassword = randomPasswordGenerator.generate();

    User savedUser;
    Tenant savedTenant;
    String tenantCode;
    try {
      User user =
          User.builder()
              .email(email)
              .passwordHash(passwordEncoder.encode(rawPassword))
              .role(UserRole.TENANT)
              .status(UserStatus.ACTIVE)
              .build();
      savedUser = userRepository.saveAndFlush(user);

      tenantCode = String.format("NT-%04d", tenantRepository.findNextTenantCodeSequence());

      Tenant tenant =
          Tenant.builder()
              .user(savedUser)
              .tenantCode(tenantCode)
              .fullName(request.fullName().trim())
              .gender(request.gender())
              .idCardNumber(idCardNumber)
              .phone(phone)
              .permanentAddress(request.permanentAddress().trim())
              .dateOfBirth(request.dateOfBirth())
              .emergencyContactName(trimToNull(request.emergencyContactName()))
              .emergencyContactPhone(trimToNull(request.emergencyContactPhone()))
              .residentStatus(ResidentStatus.ACTIVE)
              .build();
      savedTenant = tenantRepository.saveAndFlush(tenant);
    } catch (DataIntegrityViolationException e) {
      throw mapPersistenceConflict(e);
    }

    log.info(
        "Created new tenant: id={}, tenantCode={}, idCard={}",
        savedTenant.getId(),
        tenantCode,
        LogMaskingUtils.maskIdCard(idCardNumber));
    return new ResolvedTenantAccount(savedTenant, savedUser, rawPassword, false);
  }

  private Contract createContract(
      Tenant tenant,
      Bed bed,
      Room room,
      OnboardingRequest request,
      BigDecimal monthlyRent,
      BigDecimal normalizedDeposit) {
    int startYear = request.startDate().getYear();
    String contractCode =
        String.format(
            "HD-%d-%04d", startYear, contractRepository.findNextContractCodeSequence(startYear));

    Contract contract =
        Contract.builder()
            .contractCode(contractCode)
            .tenant(tenant)
            .bed(bed)
            .roomCodeSnapshot(room.getRoomNumber())
            .bedCodeSnapshot(bed.getBedCode())
            .startOn(request.startDate())
            .originalEndOn(request.endDate())
            .originalMonthlyRent(monthlyRent)
            .depositAmount(normalizedDeposit)
            .status(ContractStatus.ACTIVE)
            .build();

    Contract saved;
    try {
      saved = contractRepository.saveAndFlush(contract);
    } catch (DataIntegrityViolationException e) {
      throw mapPersistenceConflict(e);
    }

    log.info(
        "Created contract: id={}, contractCode={}, tenantId={}, bedId={}",
        saved.getId(),
        contractCode,
        tenant.getId(),
        bed.getId());
    return saved;
  }

  private RuntimeException mapPersistenceConflict(DataIntegrityViolationException e) {
    if (DataIntegrityViolationClassifier.matchesUniqueConstraint(e, "uq_users_email_canonical")) {
      return new BaseAppException(TenantErrorCode.EMAIL_DUPLICATED);
    }
    if (DataIntegrityViolationClassifier.matchesUniqueConstraint(e, "tenants_id_card_number_key")) {
      return new BaseAppException(TenantErrorCode.ID_CARD_NUMBER_DUPLICATED);
    }
    if (DataIntegrityViolationClassifier.matchesUniqueConstraint(e, "uq_contracts_active_bed")) {
      return new BaseAppException(ContractErrorCode.BED_ALREADY_RENTED);
    }
    if (DataIntegrityViolationClassifier.matchesUniqueConstraint(e, "uq_contracts_active_tenant")) {
      return new BaseAppException(ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT);
    }
    return e;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
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
        .bedCode(contract.getBedCodeSnapshot())
        .roomId(room.getId())
        .roomNumber(contract.getRoomCodeSnapshot())
        .startDate(contract.getStartDate())
        .endDate(contract.getEndDate())
        .monthlyRent(contract.getMonthlyRent())
        .depositAmount(contract.getDepositAmount())
        .status(contract.getStatus())
        .signedAt(contract.getSignedAt())
        .createdAt(contract.getCreatedAt())
        .documents(List.of())
        .build();
  }

  private TenantDetailResponse toTenantDetailResponse(
      Tenant tenant, User user, Bed bed, Room room, ContractDetailResponse contractResponse) {
    return TenantDetailResponse.builder()
        .id(tenant.getId())
        .userId(user.getId())
        .tenantCode(tenant.getTenantCode())
        .fullName(tenant.getFullName())
        .gender(tenant.getGender())
        .idCardNumber(tenant.getIdCardNumber())
        .phone(tenant.getPhone())
        .email(user.getEmail())
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
