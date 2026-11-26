package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.services.impl.OnboardingServiceImpl;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

  private static final Instant DAY_01_VN_INSTANT = Instant.parse("2026-09-01T02:00:00Z");

  @Mock private TenantRepository tenantRepository;
  @Mock private UserRepository userRepository;
  @Mock private RoomRepository roomRepository;
  @Mock private BedRepository bedRepository;
  @Mock private ContractRepository contractRepository;
  @Mock private RoomCodeRegistryRepository roomCodeRegistryRepository;
  @Mock private InvoiceIssuanceService invoiceIssuanceService;
  @Mock private PasswordRecoveryService passwordRecoveryService;
  @Mock private RandomPasswordGenerator randomPasswordGenerator;
  @Mock private PasswordEncoder passwordEncoder;

  private OnboardingServiceImpl onboardingService;

  private Room maleRoom;
  private Room archivedMaleRoom;
  private Room femaleRoom;
  private Bed availableMaleBed;
  private Bed occupiedMaleBed;
  private Bed maintenanceMaleBed;
  private Bed availableFemaleBed;
  private Bed archivedRoomBed;
  private OnboardingRequest validRequest;
  private UUID bedId;

  @BeforeEach
  void setUp() {
    onboardingService =
        createServiceWithClock(Clock.fixed(DAY_01_VN_INSTANT, TimeConfig.DORM_ZONE));

    maleRoom =
        Room.builder()
            .id(UUID.randomUUID())
            .roomNumber("P.302")
            .floor(3)
            .genderPolicy(GenderPolicy.MALE)
            .totalBeds((short) 4)
            .basePrice(new BigDecimal("1500000.00"))
            .build();

    archivedMaleRoom =
        Room.builder()
            .id(UUID.randomUUID())
            .roomNumber("P.399")
            .floor(3)
            .genderPolicy(GenderPolicy.MALE)
            .totalBeds((short) 4)
            .basePrice(new BigDecimal("1500000"))
            .archivedAt(Instant.parse("2026-08-01T00:00:00Z"))
            .build();

    femaleRoom =
        Room.builder()
            .id(UUID.randomUUID())
            .roomNumber("P.303")
            .floor(3)
            .genderPolicy(GenderPolicy.FEMALE)
            .totalBeds((short) 4)
            .basePrice(new BigDecimal("1500000.00"))
            .build();

    bedId = UUID.randomUUID();
    availableMaleBed =
        Bed.builder()
            .id(bedId)
            .room(maleRoom)
            .bedCode("G01")
            .slotKey("P.302-G01")
            .status(BedStatus.AVAILABLE)
            .build();

    occupiedMaleBed =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(maleRoom)
            .bedCode("G02")
            .slotKey("P.302-G02")
            .status(BedStatus.OCCUPIED)
            .build();

    maintenanceMaleBed =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(maleRoom)
            .bedCode("G03")
            .slotKey("P.302-G03")
            .status(BedStatus.MAINTENANCE)
            .build();

    availableFemaleBed =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(femaleRoom)
            .bedCode("G01")
            .slotKey("P.303-G01")
            .status(BedStatus.AVAILABLE)
            .build();

    archivedRoomBed =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(archivedMaleRoom)
            .bedCode("G01")
            .slotKey("P.399-G01")
            .status(BedStatus.AVAILABLE)
            .build();

    validRequest =
        OnboardingRequest.builder()
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079200001234")
            .phone("0901234567")
            .email("  NguyenVanA@Gmail.com ")
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

  private OnboardingServiceImpl createServiceWithClock(Clock clock) {
    return new OnboardingServiceImpl(
        tenantRepository,
        userRepository,
        roomRepository,
        bedRepository,
        contractRepository,
        roomCodeRegistryRepository,
        invoiceIssuanceService,
        passwordRecoveryService,
        randomPasswordGenerator,
        passwordEncoder,
        clock);
  }

  private void stubLocksForBed(Bed bed, Room room) {
    when(bedRepository.findById(bed.getId())).thenReturn(Optional.of(bed));
    when(roomRepository.findByIdUnfilteredForUpdate(room.getId())).thenReturn(Optional.of(room));
    when(bedRepository.findByIdForUpdate(bed.getId())).thenReturn(Optional.of(bed));
  }

  private void stubNewTenantPersistence(String rawPassword, String encodedPassword) {
    when(tenantRepository.findByIdCardNumber("079200001234")).thenReturn(Optional.empty());
    when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
    when(randomPasswordGenerator.generate()).thenReturn(rawPassword);
    when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
    when(tenantRepository.findNextTenantCodeSequence()).thenReturn(1L);
    when(contractRepository.findNextContractCodeSequence(2026)).thenReturn(1L);

    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(
            invocation -> {
              User u = invocation.getArgument(0);
              u.setId(UUID.randomUUID());
              return u;
            });
    when(tenantRepository.saveAndFlush(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant t = invocation.getArgument(0);
              t.setId(UUID.randomUUID());
              return t;
            });
    when(contractRepository.saveAndFlush(any(Contract.class)))
        .thenAnswer(
            invocation -> {
              Contract c = invocation.getArgument(0);
              c.setId(UUID.randomUUID());
              return c;
            });
    when(bedRepository.saveAndFlush(any(Bed.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            any(Contract.class), any(LocalDate.class), any(BigDecimal.class)))
        .thenAnswer(
            invocation -> {
              Contract contract = invocation.getArgument(0);
              LocalDate period = invocation.getArgument(1);
              BigDecimal amount = invocation.getArgument(2);
              return Invoice.builder()
                  .id(UUID.randomUUID())
                  .invoiceCode("INV-202609-" + contract.getContractCode())
                  .contract(contract)
                  .billingPeriod(period)
                  .amount(amount)
                  .dueOn(period.withDayOfMonth(5))
                  .status(InvoiceStatus.DEBT)
                  .build();
            });
  }

  @Test
  @DisplayName(
      "onboardTenant new tenant on day 01 enforces Room->Bed lock, generates password, marks code used, issues first invoice, and masks logs")
  void onboardTenant_newTenantOnDayOne_shouldPersistAtomicallyAndMaskSensitiveData() {
    String generatedPassword = "Xy9#mK2$pQ7!";
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);
    stubNewTenantPersistence(generatedPassword, "encoded_hash_abc");

    Logger logger = (Logger) LoggerFactory.getLogger(OnboardingServiceImpl.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);

    OnboardingResponse response;
    try {
      response = onboardingService.onboardTenant(validRequest);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    InOrder order = inOrder(bedRepository, roomRepository);
    order.verify(bedRepository).findById(bedId);
    order.verify(roomRepository).findByIdUnfilteredForUpdate(maleRoom.getId());
    order.verify(bedRepository).findByIdForUpdate(bedId);

    verify(randomPasswordGenerator).generate();
    verify(roomCodeRegistryRepository).markRoomCodesUsedInBusiness(maleRoom.getId());
    verify(passwordRecoveryService, never()).invalidateRecoveryCredentials(any());

    assertThat(response.tenant().tenantCode()).isEqualTo("NT-0001");
    assertThat(response.tenant().email()).isEqualTo("nguyenvana@gmail.com");
    assertThat(response.tenant().residentStatus()).isEqualTo(ResidentStatus.ACTIVE);

    assertThat(response.contract().contractCode()).isEqualTo("HD-2026-0001");
    assertThat(response.contract().roomNumber()).isEqualTo("P.302");
    assertThat(response.contract().bedCode()).isEqualTo("G01");
    assertThat(response.contract().monthlyRent()).isEqualByComparingTo("1500000");
    assertThat(response.contract().monthlyRent().scale()).isZero();
    assertThat(response.contract().depositAmount()).isEqualByComparingTo("1500000");
    assertThat(response.contract().depositAmount().scale()).isZero();
    assertThat(response.contract().status()).isEqualTo(ContractStatus.ACTIVE);

    assertThat(response.account().username()).isEqualTo("079200001234");
    assertThat(response.account().email()).isEqualTo("nguyenvana@gmail.com");
    assertThat(response.account().phone()).isEqualTo("0901234567");
    assertThat(response.account().initialPassword()).isEqualTo(generatedPassword);
    assertThat(response.account().initialPassword()).isNotEqualTo("Password123@");
    assertThat(response.account().roomBed()).isEqualTo("P.302 - G01");

    assertThat(response.firstInvoice()).isNotNull();
    assertThat(response.firstInvoice().invoiceCode()).isEqualTo("INV-202609-HD-2026-0001");
    assertThat(response.firstInvoice().billingPeriod()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(response.firstInvoice().dueOn()).isEqualTo(LocalDate.of(2026, 9, 5));
    assertThat(response.firstInvoice().amount()).isEqualByComparingTo("1500000");
    assertThat(response.firstInvoice().status()).isEqualTo(InvoiceStatus.DEBT);

    assertThat(availableMaleBed.getStatus()).isEqualTo(BedStatus.OCCUPIED);

    ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
    verify(invoiceIssuanceService)
        .issueMonthlyInvoiceIfAbsent(
            contractCaptor.capture(), eq(LocalDate.of(2026, 9, 1)), eq(new BigDecimal("1500000")));
    assertThat(contractCaptor.getValue().getRoomCodeSnapshot()).isEqualTo("P.302");
    assertThat(contractCaptor.getValue().getBedCodeSnapshot()).isEqualTo("G01");

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(userCaptor.capture());
    assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.TENANT);
    assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded_hash_abc");

    List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("***234"));
    assertThat(messages).noneMatch(msg -> msg.contains("079200001234"));
    assertThat(messages).noneMatch(msg -> msg.contains("nguyenvana@gmail.com"));
    assertThat(messages).noneMatch(msg -> msg.contains(generatedPassword));
  }

  @Test
  @DisplayName(
      "onboardTenant rejects execution on day 02..31 in Asia/Ho_Chi_Minh even when startDate is day 01")
  void onboardTenant_whenNotDayOneInDormZone_shouldRejectWithDayOneOnly() {
    OnboardingServiceImpl dayTwoService =
        createServiceWithClock(
            Clock.fixed(Instant.parse("2026-09-02T02:00:00Z"), TimeConfig.DORM_ZONE));

    assertThatThrownBy(() -> dayTwoService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ONBOARDING_ONLY_ON_DAY_ONE);

    // 2026-08-31T16:59:59Z == 2026-08-31T23:59:59+07:00 (still Aug 31 in Asia/Ho_Chi_Minh)
    OnboardingServiceImpl aug31LateService =
        createServiceWithClock(Clock.fixed(Instant.parse("2026-08-31T16:59:59Z"), ZoneOffset.UTC));
    OnboardingRequest augRequest =
        OnboardingRequest.builder()
            .fullName(validRequest.fullName())
            .gender(validRequest.gender())
            .idCardNumber(validRequest.idCardNumber())
            .phone(validRequest.phone())
            .email(validRequest.email())
            .permanentAddress(validRequest.permanentAddress())
            .bedId(bedId)
            .startDate(LocalDate.of(2026, 8, 1))
            .endDate(LocalDate.of(2026, 8, 31))
            .depositAmount(validRequest.depositAmount())
            .build();

    assertThatThrownBy(() -> aug31LateService.onboardTenant(augRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ONBOARDING_ONLY_ON_DAY_ONE);

    verify(bedRepository, never()).findById(any());
  }

  @Test
  @DisplayName(
      "onboardTenant accepts UTC 2026-08-31T17:30:00Z (2026-09-01 00:30 +07:00) and rejects 2026-09-01T17:00:00Z (2026-09-02 00:00 +07:00)")
  void onboardTenant_utcToHoChiMinhBoundary_shouldFollowDormZoneDay01() {
    OnboardingServiceImpl midnightPlusService =
        createServiceWithClock(Clock.fixed(Instant.parse("2026-08-31T17:30:00Z"), ZoneOffset.UTC));

    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);
    stubNewTenantPersistence("Pass@123456A", "encoded");

    OnboardingResponse response = midnightPlusService.onboardTenant(validRequest);
    assertThat(response.contract().startDate()).isEqualTo(LocalDate.of(2026, 9, 1));

    OnboardingServiceImpl nextDayService =
        createServiceWithClock(Clock.fixed(Instant.parse("2026-09-01T17:00:00Z"), ZoneOffset.UTC));
    assertThatThrownBy(() -> nextDayService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ONBOARDING_ONLY_ON_DAY_ONE);
  }

  @Test
  @DisplayName(
      "onboardTenant rejects startDate != today on day 01 and rejects non-month-end endDate")
  void onboardTenant_whenStartDateMismatchOrNonMonthEndEndDate_shouldThrowInvalidDates() {
    OnboardingRequest pastStart =
        OnboardingRequest.builder()
            .fullName(validRequest.fullName())
            .gender(validRequest.gender())
            .idCardNumber(validRequest.idCardNumber())
            .phone(validRequest.phone())
            .email(validRequest.email())
            .permanentAddress(validRequest.permanentAddress())
            .bedId(bedId)
            .startDate(LocalDate.of(2026, 8, 1))
            .endDate(LocalDate.of(2026, 9, 30))
            .depositAmount(validRequest.depositAmount())
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(pastStart))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.INVALID_CONTRACT_DATES);

    OnboardingRequest futureStart =
        OnboardingRequest.builder()
            .fullName(validRequest.fullName())
            .gender(validRequest.gender())
            .idCardNumber(validRequest.idCardNumber())
            .phone(validRequest.phone())
            .email(validRequest.email())
            .permanentAddress(validRequest.permanentAddress())
            .bedId(bedId)
            .startDate(LocalDate.of(2026, 10, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(validRequest.depositAmount())
            .build();

    assertThatThrownBy(() -> onboardingService.onboardTenant(futureStart))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.INVALID_CONTRACT_DATES);

    for (LocalDate badEndDate :
        List.of(
            LocalDate.of(2027, 9, 1),
            LocalDate.of(2026, 9, 15),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 8, 31))) {
      OnboardingRequest badEndReq =
          OnboardingRequest.builder()
              .fullName(validRequest.fullName())
              .gender(validRequest.gender())
              .idCardNumber(validRequest.idCardNumber())
              .phone(validRequest.phone())
              .email(validRequest.email())
              .permanentAddress(validRequest.permanentAddress())
              .bedId(bedId)
              .startDate(LocalDate.of(2026, 9, 1))
              .endDate(badEndDate)
              .depositAmount(validRequest.depositAmount())
              .build();

      assertThatThrownBy(() -> onboardingService.onboardTenant(badEndReq))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.INVALID_CONTRACT_DATES);
    }

    verify(bedRepository, never()).findById(any());
  }

  @Test
  @DisplayName(
      "onboardTenant accepts valid month-end dates including same-month, non-leap Feb, and leap Feb 29")
  void onboardTenant_validMonthEndDates_shouldSucceed() {
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);
    stubNewTenantPersistence("Pass@123456A", "encoded");

    for (LocalDate validEndDate :
        List.of(LocalDate.of(2026, 9, 30), LocalDate.of(2027, 2, 28), LocalDate.of(2028, 2, 29))) {
      availableMaleBed.setStatus(BedStatus.AVAILABLE);
      OnboardingRequest req =
          OnboardingRequest.builder()
              .fullName(validRequest.fullName())
              .gender(validRequest.gender())
              .idCardNumber(validRequest.idCardNumber())
              .phone(validRequest.phone())
              .email(validRequest.email())
              .permanentAddress(validRequest.permanentAddress())
              .bedId(bedId)
              .startDate(LocalDate.of(2026, 9, 1))
              .endDate(validEndDate)
              .depositAmount(new BigDecimal("0.00"))
              .build();

      OnboardingResponse res = onboardingService.onboardTenant(req);
      assertThat(res.contract().endDate()).isEqualTo(validEndDate);
      assertThat(res.contract().depositAmount()).isEqualByComparingTo("0");
      assertThat(res.contract().depositAmount().scale()).isZero();
    }
  }

  @Test
  @DisplayName("onboardTenant rejects null, negative, or fractional depositAmount")
  void onboardTenant_whenInvalidDepositAmount_shouldThrowInvalidDepositAmount() {
    for (BigDecimal badDeposit :
        new BigDecimal[] {null, new BigDecimal("-1"), new BigDecimal("1500000.50")}) {
      OnboardingRequest badReq =
          OnboardingRequest.builder()
              .fullName(validRequest.fullName())
              .gender(validRequest.gender())
              .idCardNumber(validRequest.idCardNumber())
              .phone(validRequest.phone())
              .email(validRequest.email())
              .permanentAddress(validRequest.permanentAddress())
              .bedId(bedId)
              .startDate(LocalDate.of(2026, 9, 1))
              .endDate(LocalDate.of(2027, 2, 28))
              .depositAmount(badDeposit)
              .build();

      assertThatThrownBy(() -> onboardingService.onboardTenant(badReq))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.INVALID_DEPOSIT_AMOUNT);
    }
  }

  @Test
  @DisplayName(
      "onboardTenant precondition checks: archived room, unavailable bed, occupied contract, gender mismatch")
  void onboardTenant_roomAndBedPreconditions_shouldRejectAppropriately() {
    // Missing bed
    UUID missingBedId = UUID.randomUUID();
    when(bedRepository.findById(missingBedId)).thenReturn(Optional.empty());
    OnboardingRequest missingBedReq =
        OnboardingRequest.builder()
            .fullName(validRequest.fullName())
            .gender(GenderPolicy.MALE)
            .idCardNumber(validRequest.idCardNumber())
            .phone(validRequest.phone())
            .email(validRequest.email())
            .permanentAddress(validRequest.permanentAddress())
            .bedId(missingBedId)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(validRequest.depositAmount())
            .build();
    assertThatThrownBy(() -> onboardingService.onboardTenant(missingBedReq))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.BED_NOT_FOUND);

    // Missing room -> ROOM_NOT_FOUND
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(availableMaleBed));
    when(roomRepository.findByIdUnfilteredForUpdate(maleRoom.getId())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);

    // Archived room -> 409 ROOM_ARCHIVED
    when(bedRepository.findById(archivedRoomBed.getId())).thenReturn(Optional.of(archivedRoomBed));
    when(roomRepository.findByIdUnfilteredForUpdate(archivedMaleRoom.getId()))
        .thenReturn(Optional.of(archivedMaleRoom));
    OnboardingRequest archivedReq =
        OnboardingRequest.builder()
            .fullName(validRequest.fullName())
            .gender(GenderPolicy.MALE)
            .idCardNumber(validRequest.idCardNumber())
            .phone(validRequest.phone())
            .email(validRequest.email())
            .permanentAddress(validRequest.permanentAddress())
            .bedId(archivedRoomBed.getId())
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(validRequest.depositAmount())
            .build();
    assertThatThrownBy(() -> onboardingService.onboardTenant(archivedReq))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ROOM_ARCHIVED);
    verify(bedRepository, never()).findByIdForUpdate(archivedRoomBed.getId());

    // Occupied or maintenance bed -> BED_NOT_AVAILABLE
    for (Bed badBed : List.of(occupiedMaleBed, maintenanceMaleBed)) {
      stubLocksForBed(badBed, maleRoom);
      OnboardingRequest badBedReq =
          OnboardingRequest.builder()
              .fullName(validRequest.fullName())
              .gender(GenderPolicy.MALE)
              .idCardNumber(validRequest.idCardNumber())
              .phone(validRequest.phone())
              .email(validRequest.email())
              .permanentAddress(validRequest.permanentAddress())
              .bedId(badBed.getId())
              .startDate(LocalDate.of(2026, 9, 1))
              .endDate(LocalDate.of(2027, 2, 28))
              .depositAmount(validRequest.depositAmount())
              .build();
      assertThatThrownBy(() -> onboardingService.onboardTenant(badBedReq))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.BED_NOT_AVAILABLE);
    }

    // Active contract already on AVAILABLE bed -> BED_ALREADY_RENTED
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(true);
    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.BED_ALREADY_RENTED);

    // Gender mismatch
    stubLocksForBed(availableFemaleBed, femaleRoom);
    when(contractRepository.existsByBedIdAndStatus(
            availableFemaleBed.getId(), ContractStatus.ACTIVE))
        .thenReturn(false);
    OnboardingRequest genderMismatchReq =
        OnboardingRequest.builder()
            .fullName(validRequest.fullName())
            .gender(GenderPolicy.MALE)
            .idCardNumber(validRequest.idCardNumber())
            .phone(validRequest.phone())
            .email(validRequest.email())
            .permanentAddress(validRequest.permanentAddress())
            .bedId(availableFemaleBed.getId())
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(validRequest.depositAmount())
            .build();
    assertThatThrownBy(() -> onboardingService.onboardTenant(genderMismatchReq))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.GENDER_MISMATCH);
  }

  @Test
  @DisplayName(
      "onboardTenant returning tenant with ACTIVE status or active contract throws TENANT_ALREADY_HAS_ACTIVE_CONTRACT")
  void onboardTenant_returningTenantActive_shouldThrow409() {
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);

    UUID existingUserId = UUID.randomUUID();
    UUID existingTenantId = UUID.randomUUID();
    User existingUser =
        User.builder()
            .id(existingUserId)
            .email("old@gmail.com")
            .passwordHash("existing_hash")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();
    Tenant activeTenant =
        Tenant.builder()
            .id(existingTenantId)
            .user(existingUser)
            .idCardNumber("079200001234")
            .residentStatus(ResidentStatus.ACTIVE)
            .build();

    when(tenantRepository.findByIdCardNumber("079200001234")).thenReturn(Optional.of(activeTenant));
    when(userRepository.findByIdForUpdate(existingUserId)).thenReturn(Optional.of(existingUser));
    when(tenantRepository.findByIdForUpdate(existingTenantId))
        .thenReturn(Optional.of(activeTenant));

    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT);

    InOrder lockOrder = inOrder(userRepository, tenantRepository);
    lockOrder.verify(userRepository).findByIdForUpdate(existingUserId);
    lockOrder.verify(tenantRepository).findByIdForUpdate(existingTenantId);
    verify(contractRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "onboardTenant returning checked-out tenant with unsettled invoices throws TENANT_HAS_UNSETTLED_INVOICES")
  void onboardTenant_returningTenantWithUnsettledInvoices_shouldThrow409() {
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);

    UUID existingUserId = UUID.randomUUID();
    UUID existingTenantId = UUID.randomUUID();
    User existingUser =
        User.builder()
            .id(existingUserId)
            .email("nguyenvana@gmail.com")
            .passwordHash("existing_hash")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();
    Tenant checkedOutTenant =
        Tenant.builder()
            .id(existingTenantId)
            .user(existingUser)
            .tenantCode("NT-0099")
            .idCardNumber("079200001234")
            .residentStatus(ResidentStatus.CHECKED_OUT)
            .build();

    when(tenantRepository.findByIdCardNumber("079200001234"))
        .thenReturn(Optional.of(checkedOutTenant));
    when(userRepository.findByIdForUpdate(existingUserId)).thenReturn(Optional.of(existingUser));
    when(tenantRepository.findByIdForUpdate(existingTenantId))
        .thenReturn(Optional.of(checkedOutTenant));
    when(contractRepository.existsByTenantIdAndStatus(existingTenantId, ContractStatus.ACTIVE))
        .thenReturn(false);
    when(invoiceIssuanceService.hasUnsettledInvoices(existingTenantId)).thenReturn(true);

    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES);

    verify(contractRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "onboardTenant returning checked-out tenant without debt reuses Tenant/User, keeps passwordHash, returns initialPassword=null")
  void onboardTenant_returningCheckedOutTenantSameEmail_shouldReuseAndKeepPassword() {
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);

    UUID existingUserId = UUID.randomUUID();
    UUID existingTenantId = UUID.randomUUID();
    User existingUser =
        User.builder()
            .id(existingUserId)
            .email("nguyenvana@gmail.com")
            .passwordHash("preserved_bcrypt_hash")
            .role(UserRole.TENANT)
            .status(UserStatus.LOCKED)
            .build();
    Tenant checkedOutTenant =
        Tenant.builder()
            .id(existingTenantId)
            .user(existingUser)
            .tenantCode("NT-0099")
            .fullName("Old Name")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079200001234")
            .phone("0900000000")
            .permanentAddress("Old Address")
            .residentStatus(ResidentStatus.CHECKED_OUT)
            .build();

    when(tenantRepository.findByIdCardNumber("079200001234"))
        .thenReturn(Optional.of(checkedOutTenant));
    when(userRepository.findByIdForUpdate(existingUserId)).thenReturn(Optional.of(existingUser));
    when(tenantRepository.findByIdForUpdate(existingTenantId))
        .thenReturn(Optional.of(checkedOutTenant));
    when(contractRepository.existsByTenantIdAndStatus(existingTenantId, ContractStatus.ACTIVE))
        .thenReturn(false);
    when(invoiceIssuanceService.hasUnsettledInvoices(existingTenantId)).thenReturn(false);
    when(contractRepository.findNextContractCodeSequence(2026)).thenReturn(10L);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(tenantRepository.saveAndFlush(any(Tenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(contractRepository.saveAndFlush(any(Contract.class)))
        .thenAnswer(
            invocation -> {
              Contract c = invocation.getArgument(0);
              c.setId(UUID.randomUUID());
              return c;
            });
    when(bedRepository.saveAndFlush(any(Bed.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            any(Contract.class), any(LocalDate.class), any(BigDecimal.class)))
        .thenAnswer(
            invocation -> {
              Contract c = invocation.getArgument(0);
              LocalDate period = invocation.getArgument(1);
              return Invoice.builder()
                  .id(UUID.randomUUID())
                  .invoiceCode("INV-202609-" + c.getContractCode())
                  .contract(c)
                  .billingPeriod(period)
                  .amount(invocation.getArgument(2))
                  .dueOn(period.withDayOfMonth(5))
                  .status(InvoiceStatus.DEBT)
                  .build();
            });

    OnboardingResponse response = onboardingService.onboardTenant(validRequest);

    assertThat(response.tenant().id()).isEqualTo(existingTenantId);
    assertThat(response.tenant().tenantCode()).isEqualTo("NT-0099");
    assertThat(response.tenant().fullName()).isEqualTo("Nguyen Van A");
    assertThat(response.tenant().residentStatus()).isEqualTo(ResidentStatus.ACTIVE);
    assertThat(response.contract().contractCode()).isEqualTo("HD-2026-0010");
    assertThat(response.account().username()).isEqualTo("079200001234");
    assertThat(response.account().initialPassword()).isNull();
    assertThat(existingUser.getPasswordHash()).isEqualTo("preserved_bcrypt_hash");
    assertThat(existingUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(response.firstInvoice()).isNotNull();

    verify(randomPasswordGenerator, never()).generate();
    verify(passwordEncoder, never()).encode(any());
    verify(tenantRepository, never()).findNextTenantCodeSequence();
    verify(passwordRecoveryService, never()).invalidateRecoveryCredentials(any());
    verify(roomCodeRegistryRepository).markRoomCodesUsedInBusiness(maleRoom.getId());
  }

  @Test
  @DisplayName(
      "onboardTenant returning tenant with changed email updates email and invalidates recovery credentials")
  void onboardTenant_returningTenantChangedEmail_shouldInvalidateRecoveryCredentials() {
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);

    UUID existingUserId = UUID.randomUUID();
    UUID existingTenantId = UUID.randomUUID();
    User existingUser =
        User.builder()
            .id(existingUserId)
            .email("old.email@gmail.com")
            .passwordHash("preserved_bcrypt_hash")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();
    Tenant checkedOutTenant =
        Tenant.builder()
            .id(existingTenantId)
            .user(existingUser)
            .tenantCode("NT-0099")
            .idCardNumber("079200001234")
            .residentStatus(ResidentStatus.CHECKED_OUT)
            .build();

    when(tenantRepository.findByIdCardNumber("079200001234"))
        .thenReturn(Optional.of(checkedOutTenant));
    when(userRepository.findByIdForUpdate(existingUserId)).thenReturn(Optional.of(existingUser));
    when(tenantRepository.findByIdForUpdate(existingTenantId))
        .thenReturn(Optional.of(checkedOutTenant));
    when(contractRepository.existsByTenantIdAndStatus(existingTenantId, ContractStatus.ACTIVE))
        .thenReturn(false);
    when(invoiceIssuanceService.hasUnsettledInvoices(existingTenantId)).thenReturn(false);
    when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
    when(contractRepository.findNextContractCodeSequence(2026)).thenReturn(11L);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(tenantRepository.saveAndFlush(any(Tenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(contractRepository.saveAndFlush(any(Contract.class)))
        .thenAnswer(
            invocation -> {
              Contract c = invocation.getArgument(0);
              c.setId(UUID.randomUUID());
              return c;
            });
    when(bedRepository.saveAndFlush(any(Bed.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(invoiceIssuanceService.issueMonthlyInvoiceIfAbsent(
            any(Contract.class), any(LocalDate.class), any(BigDecimal.class)))
        .thenReturn(
            Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceCode("INV-202609-HD-2026-0011")
                .billingPeriod(LocalDate.of(2026, 9, 1))
                .amount(new BigDecimal("1500000"))
                .dueOn(LocalDate.of(2026, 9, 5))
                .status(InvoiceStatus.DEBT)
                .build());

    OnboardingResponse response = onboardingService.onboardTenant(validRequest);

    assertThat(response.account().email()).isEqualTo("nguyenvana@gmail.com");
    assertThat(response.account().initialPassword()).isNull();
    assertThat(existingUser.getPasswordHash()).isEqualTo("preserved_bcrypt_hash");
    verify(passwordRecoveryService).invalidateRecoveryCredentials(existingUserId);
  }

  @Test
  @DisplayName("onboardTenant returning tenant with duplicate new email throws EMAIL_DUPLICATED")
  void onboardTenant_returningTenantDuplicateNewEmail_shouldThrow409() {
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);

    UUID existingUserId = UUID.randomUUID();
    UUID existingTenantId = UUID.randomUUID();
    User existingUser =
        User.builder()
            .id(existingUserId)
            .email("old.email@gmail.com")
            .passwordHash("hash")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();
    Tenant checkedOutTenant =
        Tenant.builder()
            .id(existingTenantId)
            .user(existingUser)
            .idCardNumber("079200001234")
            .residentStatus(ResidentStatus.CHECKED_OUT)
            .build();

    when(tenantRepository.findByIdCardNumber("079200001234"))
        .thenReturn(Optional.of(checkedOutTenant));
    when(userRepository.findByIdForUpdate(existingUserId)).thenReturn(Optional.of(existingUser));
    when(tenantRepository.findByIdForUpdate(existingTenantId))
        .thenReturn(Optional.of(checkedOutTenant));
    when(contractRepository.existsByTenantIdAndStatus(existingTenantId, ContractStatus.ACTIVE))
        .thenReturn(false);
    when(invoiceIssuanceService.hasUnsettledInvoices(existingTenantId)).thenReturn(false);
    when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(true);

    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", TenantErrorCode.EMAIL_DUPLICATED);
  }

  @Test
  @DisplayName("onboardTenant when duplicate email for new tenant throws EMAIL_DUPLICATED")
  void onboardTenant_whenDuplicateEmail_shouldThrowException() {
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);
    when(tenantRepository.findByIdCardNumber("079200001234")).thenReturn(Optional.empty());
    when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(true);

    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", TenantErrorCode.EMAIL_DUPLICATED);

    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("onboardTenant allows a phone number already used by another contact")
  void onboardTenant_whenPhoneAlreadyUsed_shouldAllowOnboarding() {
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);
    stubNewTenantPersistence("Xy9#mK2$pQ7!", "encoded_hash_abc");

    OnboardingResponse response = onboardingService.onboardTenant(validRequest);

    assertThat(response).isNotNull();
    assertThat(response.account().phone()).isEqualTo("0901234567");
    verify(tenantRepository, never()).findByPhone(any());
  }

  @Test
  @DisplayName("onboardTenant maps DB unique race exceptions to domain 409 codes")
  void onboardTenant_dataIntegrityRaces_shouldMapToDomain409Codes() {
    stubLocksForBed(availableMaleBed, maleRoom);
    when(contractRepository.existsByBedIdAndStatus(bedId, ContractStatus.ACTIVE)).thenReturn(false);
    when(tenantRepository.findByIdCardNumber("079200001234")).thenReturn(Optional.empty());
    when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
    when(randomPasswordGenerator.generate()).thenReturn("Xy9#mK2$pQ7!");
    when(passwordEncoder.encode("Xy9#mK2$pQ7!")).thenReturn("encoded");

    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(uniqueConstraintViolation("uq_users_email_canonical"));
    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", TenantErrorCode.EMAIL_DUPLICATED);

    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              u.setId(UUID.randomUUID());
              return u;
            });
    when(tenantRepository.findNextTenantCodeSequence()).thenReturn(1L);
    when(tenantRepository.saveAndFlush(any(Tenant.class)))
        .thenThrow(uniqueConstraintViolation("tenants_id_card_number_key"));
    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", TenantErrorCode.ID_CARD_NUMBER_DUPLICATED);

    when(tenantRepository.saveAndFlush(any(Tenant.class)))
        .thenAnswer(
            inv -> {
              Tenant t = inv.getArgument(0);
              t.setId(UUID.randomUUID());
              return t;
            });
    when(contractRepository.findNextContractCodeSequence(2026)).thenReturn(1L);
    when(contractRepository.saveAndFlush(any(Contract.class)))
        .thenThrow(uniqueConstraintViolation("uq_contracts_active_bed"));
    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.BED_ALREADY_RENTED);

    when(contractRepository.saveAndFlush(any(Contract.class)))
        .thenThrow(uniqueConstraintViolation("uq_contracts_active_tenant"));
    assertThatThrownBy(() -> onboardingService.onboardTenant(validRequest))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ContractErrorCode.TENANT_ALREADY_HAS_ACTIVE_CONTRACT);
  }

  private static DataIntegrityViolationException uniqueConstraintViolation(String constraintName) {
    SQLException sqlEx = new SQLException("duplicate key value", "23505");
    ConstraintViolationException cve =
        new ConstraintViolationException("could not execute statement", sqlEx, constraintName);
    return new DataIntegrityViolationException("integrity error", cve);
  }
}
