package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
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
import com.uit.dormmanagement.services.impl.OnboardingServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private UserRepository userRepository;
  @Mock private BedRepository bedRepository;
  @Mock private RoomRepository roomRepository;
  @Mock private ContractRepository contractRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private OnboardingServiceImpl onboardingService;

  private Room maleRoom;
  private Room femaleRoom;
  private Bed availableMaleBed;
  private Bed occupiedMaleBed;
  private Bed availableFemaleBed;
  private OnboardingRequest validRequest;
  private UUID bedId;

  @BeforeEach
  void setUp() {
    maleRoom =
        Room.builder()
            .id(UUID.randomUUID())
            .roomNumber("P.302")
            .floor(3)
            .roomType(RoomType.FOUR_BEDS)
            .genderPolicy(GenderPolicy.MALE)
            .totalBeds(4)
            .occupiedBeds(1)
            .basePrice(new BigDecimal("1500000.00"))
            .build();

    femaleRoom =
        Room.builder()
            .id(UUID.randomUUID())
            .roomNumber("P.303")
            .floor(3)
            .roomType(RoomType.FOUR_BEDS)
            .genderPolicy(GenderPolicy.FEMALE)
            .totalBeds(4)
            .occupiedBeds(0)
            .basePrice(new BigDecimal("1500000.00"))
            .build();

    bedId = UUID.randomUUID();
    availableMaleBed =
        Bed.builder()
            .id(bedId)
            .room(maleRoom)
            .bedCode("B01")
            .slotKey("P.302-B01")
            .price(new BigDecimal("1500000.00"))
            .status(BedStatus.AVAILABLE)
            .build();

    occupiedMaleBed =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(maleRoom)
            .bedCode("B02")
            .slotKey("P.302-B02")
            .price(new BigDecimal("1500000.00"))
            .status(BedStatus.OCCUPIED)
            .build();

    availableFemaleBed =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(femaleRoom)
            .bedCode("B01")
            .slotKey("P.303-B01")
            .price(new BigDecimal("1500000.00"))
            .status(BedStatus.AVAILABLE)
            .build();

    validRequest =
        OnboardingRequest.builder()
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079200001234")
            .phone("0901234567")
            .email("nguyenvana@gmail.com")
            .permanentAddress("123 Le Loi, Q1, TP.HCM")
            .dateOfBirth(LocalDate.of(2002, 5, 15))
            .emergencyContactName("Nguyen Van B")
            .emergencyContactPhone("0909876543")
            .bedId(bedId)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(new BigDecimal("1500000.00"))
            .build();
  }

  @Test
  @DisplayName(
      "onboardTenant when valid request should successfully create all entities and return response")
  void onboardTenant_whenValidRequest_shouldSuccessfullyCreateAllEntitiesAndReturnResponse() {
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableMaleBed));
    when(tenantRepository.findByIdCardNumber("079200001234")).thenReturn(Optional.empty());
    when(tenantRepository.existsByIdCardNumber("079200001234")).thenReturn(false);
    when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
    when(tenantRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
    when(userRepository.existsByPhone("0901234567")).thenReturn(false);
    when(tenantRepository.existsByPhone("0901234567")).thenReturn(false);
    when(passwordEncoder.encode("Password123@")).thenReturn("hashed_password_123");
    when(tenantRepository.findNextTenantCodeSequence()).thenReturn(1L);
    when(contractRepository.findNextContractCodeSequence(2026)).thenReturn(1L);

    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User u = invocation.getArgument(0);
              u.setId(UUID.randomUUID());
              return u;
            });
    when(tenantRepository.save(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant t = invocation.getArgument(0);
              t.setId(UUID.randomUUID());
              return t;
            });
    when(contractRepository.save(any(Contract.class)))
        .thenAnswer(
            invocation -> {
              Contract c = invocation.getArgument(0);
              c.setId(UUID.randomUUID());
              return c;
            });
    when(bedRepository.save(any(Bed.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    OnboardingResponse response = onboardingService.onboardTenant(validRequest);

    assertThat(response).isNotNull();
    assertThat(response.tenant()).isNotNull();
    assertThat(response.tenant().tenantCode()).isEqualTo("NT-0001");
    assertThat(response.tenant().fullName()).isEqualTo("Nguyen Van A");
    assertThat(response.tenant().residentStatus()).isEqualTo(ResidentStatus.ACTIVE);
    assertThat(response.tenant().currentRoomNumber()).isEqualTo("P.302");
    assertThat(response.tenant().currentBedCode()).isEqualTo("B01");

    assertThat(response.contract()).isNotNull();
    assertThat(response.contract().contractCode()).isEqualTo("HD-2026-0001");
    assertThat(response.contract().status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(response.contract().monthlyRent()).isEqualByComparingTo("1500000.00");
    assertThat(response.contract().depositAmount()).isEqualByComparingTo("1500000.00");

    assertThat(response.account()).isNotNull();
    assertThat(response.account().username()).isEqualTo("nguyenvana@gmail.com");
    assertThat(response.account().email()).isEqualTo("nguyenvana@gmail.com");
    assertThat(response.account().phone()).isEqualTo("0901234567");
    assertThat(response.account().initialPassword()).isEqualTo("Password123@");
    assertThat(response.account().roomBed()).isEqualTo("P.302 - B01");

    assertThat(availableMaleBed.getStatus()).isEqualTo(BedStatus.OCCUPIED);
    assertThat(maleRoom.getOccupiedBeds()).isEqualTo(2);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getUsername()).isEqualTo("nguyenvana@gmail.com");
    assertThat(savedUser.getRole()).isEqualTo(UserRole.TENANT);
    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(savedUser.getPasswordHash()).isEqualTo("hashed_password_123");
  }

  @Test
  @DisplayName("onboardTenant when gender mismatch should throw GENDER_MISMATCH")
  void onboardTenant_whenGenderMismatch_shouldThrowException() {
    when(bedRepository.findById(availableFemaleBed.getId()))
        .thenReturn(Optional.of(availableFemaleBed));

    OnboardingRequest request =
        OnboardingRequest.builder()
            .gender(GenderPolicy.MALE)
            .bedId(availableFemaleBed.getId())
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.GENDER_MISMATCH);

    verify(userRepository, never()).save(any());
    verify(contractRepository, never()).save(any());
  }

  @Test
  @DisplayName("onboardTenant when bed is not available should throw BED_NOT_AVAILABLE")
  void onboardTenant_whenBedNotAvailable_shouldThrowException() {
    when(bedRepository.findById(occupiedMaleBed.getId())).thenReturn(Optional.of(occupiedMaleBed));

    OnboardingRequest request =
        OnboardingRequest.builder()
            .gender(GenderPolicy.MALE)
            .bedId(occupiedMaleBed.getId())
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.BED_NOT_AVAILABLE);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("onboardTenant when bed not found should throw BED_NOT_FOUND")
  void onboardTenant_whenBedNotFound_shouldThrowException() {
    UUID nonExistentBedId = UUID.randomUUID();
    when(bedRepository.findById(nonExistentBedId)).thenReturn(Optional.empty());

    OnboardingRequest request =
        OnboardingRequest.builder()
            .gender(GenderPolicy.MALE)
            .bedId(nonExistentBedId)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.BED_NOT_FOUND);
  }

  @Test
  @DisplayName("onboardTenant when invalid contract dates should throw INVALID_CONTRACT_DATES")
  void onboardTenant_whenInvalidContractDates_shouldThrowException() {
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableMaleBed));

    OnboardingRequest request =
        OnboardingRequest.builder()
            .gender(GenderPolicy.MALE)
            .bedId(bedId)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2026, 8, 1)) // before start date
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.INVALID_CONTRACT_DATES);
  }

  @Test
  @DisplayName(
      "onboardTenant when active tenant already exists with idCard should throw ID_CARD_NUMBER_DUPLICATED")
  void onboardTenant_whenActiveTenantAlreadyExistsWithSameIdCard_shouldThrowException() {
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableMaleBed));

    Tenant activeTenant =
        Tenant.builder()
            .id(UUID.randomUUID())
            .idCardNumber("079200001234")
            .residentStatus(ResidentStatus.ACTIVE)
            .build();

    when(tenantRepository.findByIdCardNumber("079200001234")).thenReturn(Optional.of(activeTenant));

    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", TenantErrorCode.ID_CARD_NUMBER_DUPLICATED);

    verify(contractRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "onboardTenant when checked-out tenant returns should reactivate tenant and reuse user")
  void onboardTenant_whenCheckedOutTenantReturns_shouldReactivateTenantAndReuseUser() {
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableMaleBed));

    User existingUser =
        User.builder()
            .id(UUID.randomUUID())
            .username("nguyenvana@gmail.com")
            .email("nguyenvana@gmail.com")
            .phone("0901234567")
            .passwordHash("existing_hash")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();

    Tenant checkedOutTenant =
        Tenant.builder()
            .id(UUID.randomUUID())
            .user(existingUser)
            .tenantCode("NT-0099")
            .fullName("Old Name")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079200001234")
            .phone("0901234567")
            .email("nguyenvana@gmail.com")
            .permanentAddress("Old Address")
            .residentStatus(ResidentStatus.CHECKED_OUT)
            .build();

    when(tenantRepository.findByIdCardNumber("079200001234"))
        .thenReturn(Optional.of(checkedOutTenant));
    when(contractRepository.findNextContractCodeSequence(2026)).thenReturn(10L);

    when(tenantRepository.save(any(Tenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(contractRepository.save(any(Contract.class)))
        .thenAnswer(
            invocation -> {
              Contract c = invocation.getArgument(0);
              c.setId(UUID.randomUUID());
              return c;
            });
    when(bedRepository.save(any(Bed.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    OnboardingResponse response = onboardingService.onboardTenant(validRequest);

    assertThat(response).isNotNull();
    assertThat(response.tenant().tenantCode()).isEqualTo("NT-0099"); // Kept original tenant code
    assertThat(response.tenant().fullName()).isEqualTo("Nguyen Van A"); // Updated
    assertThat(response.tenant().residentStatus()).isEqualTo(ResidentStatus.ACTIVE); // Reactivated
    assertThat(response.contract().contractCode()).isEqualTo("HD-2026-0010");
    assertThat(response.account().initialPassword()).isEqualTo("Password123@");

    assertThat(availableMaleBed.getStatus()).isEqualTo(BedStatus.OCCUPIED);
    assertThat(maleRoom.getOccupiedBeds()).isEqualTo(2);

    // Verify tenant sequence was NOT called for re-onboarded tenant
    verify(tenantRepository, never()).findNextTenantCodeSequence();
  }

  @Test
  @DisplayName("onboardTenant when duplicate email for fresh tenant should throw EMAIL_DUPLICATED")
  void onboardTenant_whenDuplicateEmail_shouldThrowException() {
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableMaleBed));
    when(tenantRepository.findByIdCardNumber("079200001234")).thenReturn(Optional.empty());
    when(tenantRepository.existsByIdCardNumber("079200001234")).thenReturn(false);
    when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(true);

    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", TenantErrorCode.EMAIL_DUPLICATED);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("onboardTenant when duplicate phone for fresh tenant should throw PHONE_DUPLICATED")
  void onboardTenant_whenDuplicatePhone_shouldThrowException() {
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableMaleBed));
    when(tenantRepository.findByIdCardNumber("079200001234")).thenReturn(Optional.empty());
    when(tenantRepository.existsByIdCardNumber("079200001234")).thenReturn(false);
    when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
    when(tenantRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
    when(userRepository.existsByPhone("0901234567")).thenReturn(true);

    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", TenantErrorCode.PHONE_DUPLICATED);

    verify(userRepository, never()).save(any());
  }
}
