package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest;
import com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest;
import com.uit.dormmanagement.dtos.response.auth.OneTimePasswordResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantSummaryResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractDocument;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.exceptions.error.WebErrorCode;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.impl.TenantServiceImpl;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private UserRepository userRepository;
  @Mock private StaffProfileRepository staffProfileRepository;
  @Mock private ContractRepository contractRepository;
  @Mock private VehicleRepository vehicleRepository;
  @Mock private PasswordRecoveryService passwordRecoveryService;
  @Mock private UserTokenInvalidationService userTokenInvalidationService;
  @Mock private RandomPasswordGenerator randomPasswordGenerator;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserService userService;

  @InjectMocks private TenantServiceImpl tenantService;

  private UUID tenantId;
  private UUID managerUserId;
  private StaffProfile managerStaff;
  private Tenant tenant;
  private User user;
  private Room room;
  private Bed bed;
  private Contract activeContract;
  private Vehicle vehicle;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    managerUserId = UUID.randomUUID();

    User managerUser =
        User.builder()
            .id(managerUserId)
            .email("manager@dorm.vn")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .build();
    managerStaff = StaffProfile.builder().id(UUID.randomUUID()).user(managerUser).build();

    user =
        User.builder()
            .id(UUID.randomUUID())
            .email("nam.nguyen@example.com")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();

    tenant =
        Tenant.builder()
            .id(tenantId)
            .user(user)
            .tenantCode("NT-0187")
            .fullName("Nguyễn Hoàng Nam")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203001187")
            .phone("0987654321")
            .permanentAddress("123 Lê Lợi, TP.HCM")
            .residentStatus(ResidentStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

    room =
        Room.builder()
            .id(UUID.randomUUID())
            .roomNumber("P.302")
            .genderPolicy(GenderPolicy.MALE)
            .build();
    bed = Bed.builder().id(UUID.randomUUID()).bedCode("G02").room(room).build();

    activeContract =
        Contract.builder()
            .id(UUID.randomUUID())
            .contractCode("HD-2026-0187")
            .tenant(tenant)
            .bed(bed)
            .roomCodeSnapshot("P.302")
            .bedCodeSnapshot("G02")
            .startOn(LocalDate.of(2026, 9, 1))
            .originalEndOn(LocalDate.of(2027, 9, 1))
            .originalMonthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

    vehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X3 789.12")
            .brand("Honda")
            .color("Đen")
            .cardCode("THE-0187")
            .status(VehicleStatus.ISSUED)
            .createdAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("getTenants: should return paged summary responses with active contract details")
  void getTenants_shouldReturnPagedSummaries() {
    Pageable pageable = PageRequest.of(0, 10);
    when(tenantRepository.findActiveTenantsWithFilters(eq("Nam"), any(), any(), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(tenant), pageable, 1));
    when(contractRepository.findFirstByTenantIdAndStatusIn(eq(tenantId), any()))
        .thenReturn(Optional.of(activeContract));

    Page<TenantSummaryResponse> page = tenantService.getTenants(pageable, "  Nam  ", null, null);

    assertThat(page.getTotalElements()).isEqualTo(1);
    TenantSummaryResponse summary = page.getContent().getFirst();
    assertThat(summary.tenantCode()).isEqualTo("NT-0187");
    assertThat(summary.fullName()).isEqualTo("Nguyễn Hoàng Nam");
    assertThat(summary.roomNumber()).isEqualTo("P.302");
    assertThat(summary.bedCode()).isEqualTo("G02");
    assertThat(summary.contractEndDate()).isEqualTo(LocalDate.of(2027, 9, 1));
    assertThat(summary.monthlyRent()).isEqualByComparingTo("1500000.00");
  }

  @Test
  @DisplayName("getTenantById: when not found should throw TENANT_NOT_FOUND")
  void getTenantById_whenNotFound_shouldThrow() {
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> tenantService.getTenantById(tenantId))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == TenantErrorCode.TENANT_NOT_FOUND);
  }

  @Test
  @DisplayName("getTenantById: when found should return complete detail response")
  void getTenantById_whenFound_shouldReturnDetail() {
    UUID docId = UUID.randomUUID();
    String rawStorageKey = "contracts/" + activeContract.getId() + "/secret-key.pdf";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(activeContract)
            .documentType(ContractDocumentCategory.ORIGINAL_CONTRACT)
            .originalFilename("hop-dong.pdf")
            .mimeType("application/pdf")
            .storageFileKey(rawStorageKey)
            .build();
    activeContract.getDocuments().add(doc);

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(contractRepository.findFirstByTenantIdAndStatusIn(eq(tenantId), any()))
        .thenReturn(Optional.of(activeContract));
    when(contractRepository.findByTenantId(tenantId)).thenReturn(List.of(activeContract));
    when(vehicleRepository.findByTenantId(tenantId)).thenReturn(List.of(vehicle));

    TenantDetailResponse detail = tenantService.getTenantById(tenantId);

    assertThat(detail.id()).isEqualTo(tenantId);
    assertThat(detail.tenantCode()).isEqualTo("NT-0187");
    assertThat(detail.currentRoomNumber()).isEqualTo("P.302");
    assertThat(detail.currentBedCode()).isEqualTo("G02");
    assertThat(detail.currentContract()).isNotNull();
    assertThat(detail.currentContract().contractCode()).isEqualTo("HD-2026-0187");
    assertThat(detail.currentContract().documents()).hasSize(1);
    assertThat(detail.currentContract().documents().getFirst().fileUrl())
        .isEqualTo(
            "/api/v1/admin/contracts/"
                + activeContract.getId()
                + "/documents/"
                + docId
                + "/content")
        .doesNotContain(rawStorageKey)
        .doesNotContain("/uploads/");
    assertThat(detail.vehicles()).hasSize(1);
    assertThat(detail.vehicles().getFirst().licensePlate()).isEqualTo("59-X3 789.12");
    assertThat(detail.contractHistory()).hasSize(1);
  }

  @Test
  @DisplayName(
      "updateTenantAsManager: should update fields, lock User and Tenant, and invalidate recovery on email change")
  void updateTenantAsManager_whenValidWithEmailChange_shouldUpdateAndInvalidateRecovery() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));
    when(userRepository.existsByEmail("new.email@example.com")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    ManagerUpdateTenantRequest request =
        ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam Mới")
            .phone("0912345678")
            .permanentAddress("456 Hai Bà Trưng, TP.HCM")
            .email("new.email@example.com")
            .build();

    TenantDetailResponse response =
        tenantService.updateTenantAsManager(managerUserId, tenantId, request);

    assertThat(response.fullName()).isEqualTo("Nguyễn Hoàng Nam Mới");
    assertThat(response.phone()).isEqualTo("0912345678");
    assertThat(user.getEmail()).isEqualTo("new.email@example.com");
    verify(passwordRecoveryService).invalidateRecoveryCredentials(user.getId());
  }

  @Test
  @DisplayName(
      "updateTenantAsManager: duplicate idCardNumber should throw ID_CARD_NUMBER_DUPLICATED")
  void updateTenantAsManager_whenDuplicateCccd_shouldThrow() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.existsByIdCardNumber("079203009999")).thenReturn(true);

    ManagerUpdateTenantRequest request =
        ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam")
            .phone("0987654321")
            .permanentAddress("123 Lê Lợi")
            .idCardNumber("079203009999")
            .build();

    assertThatThrownBy(() -> tenantService.updateTenantAsManager(managerUserId, tenantId, request))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == TenantErrorCode.ID_CARD_NUMBER_DUPLICATED);
  }

  @Test
  @DisplayName(
      "updateTenantAsManager: gender mismatch with active room gender policy should throw GENDER_MISMATCH_CURRENT_ROOM")
  void updateTenantAsManager_whenGenderMismatch_shouldThrow() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));
    when(contractRepository.findFirstByTenantIdAndStatusIn(eq(tenantId), any()))
        .thenReturn(Optional.of(activeContract));

    ManagerUpdateTenantRequest request =
        ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam")
            .phone("0987654321")
            .permanentAddress("123 Lê Lợi")
            .gender(GenderPolicy.FEMALE)
            .build();

    assertThatThrownBy(() -> tenantService.updateTenantAsManager(managerUserId, tenantId, request))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == TenantErrorCode.GENDER_MISMATCH_CURRENT_ROOM);
  }

  @Test
  @DisplayName("updateOwnContact: tenant updates own contact fields successfully")
  void updateOwnContact_whenValid_shouldUpdateAndReturnProfile() {
    UUID tenantUserId = user.getId();
    when(tenantRepository.findByUserIdForUpdate(tenantUserId)).thenReturn(Optional.of(tenant));
    when(userService.getCurrentUserProfile(tenantUserId)).thenReturn(null);

    TenantContactUpdateRequest request =
        TenantContactUpdateRequest.builder()
            .phone("0909999888")
            .permanentAddress("789 Trần Hưng Đạo")
            .build();

    tenantService.updateOwnContact(tenantUserId, request);

    assertThat(tenant.getPhone()).isEqualTo("0909999888");
    assertThat(tenant.getPermanentAddress()).isEqualTo("789 Trần Hưng Đạo");
    verify(tenantRepository).save(tenant);
  }

  @Test
  @DisplayName("updateOwnContact: should throw when no fields are provided")
  void updateOwnContact_whenNoFields_shouldThrow() {
    UUID tenantUserId = user.getId();
    TenantContactUpdateRequest request = TenantContactUpdateRequest.builder().build();

    assertThatThrownBy(() -> tenantService.updateOwnContact(tenantUserId, request))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == TenantErrorCode.NO_CONTACT_FIELDS_PROVIDED);
  }

  @Test
  @DisplayName(
      "resetTenantPasswordAsManager: manager resets tenant password, saves hash, and invalidates sessions")
  void resetTenantPasswordAsManager_shouldResetAndInvalidate() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    when(randomPasswordGenerator.generate()).thenReturn("GeneratedPass123@");
    when(passwordEncoder.encode("GeneratedPass123@")).thenReturn("hashedPassword");

    OneTimePasswordResponse response =
        tenantService.resetTenantPasswordAsManager(managerUserId, tenantId);

    assertThat(response.temporaryPassword()).isEqualTo("GeneratedPass123@");
    assertThat(user.getPasswordHash()).isEqualTo("hashedPassword");
    verify(userRepository).save(user);
    verify(passwordRecoveryService).invalidateRecoveryCredentials(user.getId());
    verify(userTokenInvalidationService).invalidateAll(user.getId());
  }

  @Test
  @DisplayName(
      "updateTenantAsManager: supplied blank idCardNumber or email throws VALIDATION_FAILED without locking")
  void updateTenantAsManager_whenSuppliedBlankIdentity_shouldThrow400() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));

    ManagerUpdateTenantRequest blankCccd =
        ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam")
            .idCardNumber("   ")
            .phone("0987654321")
            .permanentAddress("123 Lê Lợi")
            .build();
    assertThatThrownBy(
            () -> tenantService.updateTenantAsManager(managerUserId, tenantId, blankCccd))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == WebErrorCode.VALIDATION_FAILED);

    ManagerUpdateTenantRequest blankEmail =
        ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam")
            .email("")
            .phone("0987654321")
            .permanentAddress("123 Lê Lợi")
            .build();
    assertThatThrownBy(
            () -> tenantService.updateTenantAsManager(managerUserId, tenantId, blankEmail))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == WebErrorCode.VALIDATION_FAILED);

    verify(userRepository, never()).saveAndFlush(any());
    verify(tenantRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "updateTenantAsManager: omitted identity fields retain existing values while clearing null/blank optional contact fields")
  void updateTenantAsManager_whenOmittedIdentity_retainsIdentityAndClearsOptionalContacts() {
    tenant.setDateOfBirth(LocalDate.of(2000, 1, 1));
    tenant.setEmergencyContactName("Old Contact");
    tenant.setEmergencyContactPhone("0901111222");

    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    ManagerUpdateTenantRequest request =
        ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam")
            .idCardNumber(null)
            .email(null)
            .phone("0987654321")
            .permanentAddress("123 Lê Lợi")
            .dateOfBirth(null)
            .emergencyContactName("   ")
            .emergencyContactPhone("")
            .build();

    TenantDetailResponse response =
        tenantService.updateTenantAsManager(managerUserId, tenantId, request);

    assertThat(response.idCardNumber()).isEqualTo("079203001187");
    assertThat(response.email()).isEqualTo("nam.nguyen@example.com");
    assertThat(response.dateOfBirth()).isNull();
    assertThat(response.emergencyContactName()).isNull();
    assertThat(response.emergencyContactPhone()).isNull();
    verify(passwordRecoveryService, never()).invalidateRecoveryCredentials(any());
  }

  @Test
  @DisplayName(
      "updateOwnContact: blank phone/address throws VALIDATION_FAILED while blank emergency fields clear")
  void updateOwnContact_blankRequiredRejectedAndBlankOptionalCleared() {
    UUID tenantUserId = user.getId();
    tenant.setEmergencyContactName("Old Name");
    tenant.setEmergencyContactPhone("0901111222");
    when(tenantRepository.findByUserIdForUpdate(tenantUserId)).thenReturn(Optional.of(tenant));

    TenantContactUpdateRequest blankPhone =
        TenantContactUpdateRequest.builder().phone("   ").build();
    assertThatThrownBy(() -> tenantService.updateOwnContact(tenantUserId, blankPhone))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == WebErrorCode.VALIDATION_FAILED);

    TenantContactUpdateRequest blankAddr =
        TenantContactUpdateRequest.builder().permanentAddress("").build();
    assertThatThrownBy(() -> tenantService.updateOwnContact(tenantUserId, blankAddr))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == WebErrorCode.VALIDATION_FAILED);

    TenantContactUpdateRequest clearEmergency =
        TenantContactUpdateRequest.builder()
            .emergencyContactName("  ")
            .emergencyContactPhone("")
            .build();
    tenantService.updateOwnContact(tenantUserId, clearEmergency);
    assertThat(tenant.getEmergencyContactName()).isNull();
    assertThat(tenant.getEmergencyContactPhone()).isNull();
  }

  @Test
  @DisplayName(
      "updateTenantAsManager: database race on email/CCCD maps to Tenant 409, while CHECK 23514 rethrows")
  void updateTenantAsManager_constraintRaceClassification() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));

    // Email race -> TenantErrorCode.EMAIL_DUPLICATED
    when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "dup",
                new ConstraintViolationException(
                    "dup", new SQLException("dup", "23505"), "uq_users_email_canonical")));

    ManagerUpdateTenantRequest emailReq =
        ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam")
            .email("race@example.com")
            .phone("0987654321")
            .permanentAddress("123 Lê Lợi")
            .build();

    assertThatThrownBy(() -> tenantService.updateTenantAsManager(managerUserId, tenantId, emailReq))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == TenantErrorCode.EMAIL_DUPLICATED);

    // CCCD race -> TenantErrorCode.ID_CARD_NUMBER_DUPLICATED
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantRepository.existsByIdCardNumber("079203009998")).thenReturn(false);
    when(tenantRepository.saveAndFlush(any(Tenant.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "dup",
                new ConstraintViolationException(
                    "dup", new SQLException("dup", "23505"), "tenants_id_card_number_key")));

    ManagerUpdateTenantRequest cccdReq =
        ManagerUpdateTenantRequest.builder()
            .fullName("Nguyễn Hoàng Nam")
            .idCardNumber("079203009998")
            .phone("0987654321")
            .permanentAddress("123 Lê Lợi")
            .build();

    assertThatThrownBy(() -> tenantService.updateTenantAsManager(managerUserId, tenantId, cccdReq))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == TenantErrorCode.ID_CARD_NUMBER_DUPLICATED);

    // CHECK 23514 -> NOT mapped to 409 (propagates as DataIntegrityViolationException -> 400)
    when(tenantRepository.saveAndFlush(any(Tenant.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "check",
                new ConstraintViolationException(
                    "check", new SQLException("check", "23514"), "ck_tenants_check")));

    assertThatThrownBy(() -> tenantService.updateTenantAsManager(managerUserId, tenantId, cccdReq))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
