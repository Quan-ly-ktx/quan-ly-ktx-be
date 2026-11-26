package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.contract.CheckoutContractRequest;
import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentContent;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractSummaryResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractDocument;
import com.uit.dormmanagement.entities.ContractRenewal;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.exceptions.error.SystemErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractDocumentRepository;
import com.uit.dormmanagement.repositories.ContractRenewalRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleChangeRequestRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.impl.ContractServiceImpl;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {

  private static final Instant DEFAULT_RENEWAL_INSTANT = Instant.parse("2027-08-15T02:00:00Z");
  private static final byte[] VALID_PDF_BYTES =
      "%PDF-1.4 test-doc".getBytes(StandardCharsets.UTF_8);
  private static final byte[] VALID_JPEG_BYTES =
      new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
  private static final byte[] VALID_WEBP_BYTES =
      new byte[] {
        'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
      };
  private static final byte[] VALID_DOC_BYTES =
      new byte[] {
        (byte) 0xD0,
        (byte) 0xCF,
        0x11,
        (byte) 0xE0,
        (byte) 0xA1,
        (byte) 0xB1,
        0x1A,
        (byte) 0xE1,
        0x00
      };
  private static final byte[] VALID_DOCX_BYTES =
      new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};

  @Mock private ContractRepository contractRepository;
  @Mock private ContractRenewalRepository contractRenewalRepository;
  @Mock private ContractDocumentRepository contractDocumentRepository;
  @Mock private RoomRepository roomRepository;
  @Mock private BedRepository bedRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private VehicleRepository vehicleRepository;
  @Mock private VehicleChangeRequestRepository vehicleChangeRequestRepository;
  @Mock private StaffProfileRepository staffProfileRepository;
  @Mock private PrivateObjectStorage privateObjectStorage;
  @Mock private InvoiceIssuanceService invoiceIssuanceService;

  private ContractServiceImpl contractService;

  private UUID contractId;
  private UUID tenantUserId;
  private Contract contract;
  private Room room;
  private Bed bed;
  private Tenant tenant;
  private Vehicle vehicle;

  private ContractServiceImpl createServiceWithClock(Clock clock) {
    return new ContractServiceImpl(
        contractRepository,
        contractRenewalRepository,
        contractDocumentRepository,
        roomRepository,
        bedRepository,
        tenantRepository,
        vehicleRepository,
        vehicleChangeRequestRepository,
        staffProfileRepository,
        privateObjectStorage,
        invoiceIssuanceService,
        clock);
  }

  @BeforeEach
  void setUp() {
    contractService =
        createServiceWithClock(Clock.fixed(DEFAULT_RENEWAL_INSTANT, TimeConfig.DORM_ZONE));

    contractId = UUID.randomUUID();
    tenantUserId = UUID.randomUUID();

    room =
        Room.builder()
            .id(UUID.randomUUID())
            .roomNumber("P.302")
            .basePrice(new BigDecimal("1800000.00"))
            .build();
    bed =
        Bed.builder()
            .id(UUID.randomUUID())
            .bedCode("G02")
            .status(BedStatus.OCCUPIED)
            .room(room)
            .build();

    tenant =
        Tenant.builder()
            .id(UUID.randomUUID())
            .tenantCode("NT-0187")
            .fullName("Nguyễn Hoàng Nam")
            .gender(GenderPolicy.MALE)
            .phone("0987654321")
            .residentStatus(ResidentStatus.ACTIVE)
            .build();

    contract =
        Contract.builder()
            .id(contractId)
            .contractCode("HD-2026-0187")
            .tenant(tenant)
            .bed(bed)
            .roomCodeSnapshot("P.401")
            .bedCodeSnapshot("G1")
            .startOn(LocalDate.of(2026, 9, 1))
            .originalEndOn(LocalDate.of(2027, 8, 31))
            .originalMonthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .documents(new ArrayList<>())
            .renewals(new ArrayList<>())
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
            .build();
  }

  @AfterEach
  void cleanUpTransactionSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("getContracts: should return paged summary responses")
  void getContracts_shouldReturnPagedSummaries() {
    Pageable pageable = PageRequest.of(0, 10);
    when(contractRepository.findContractsWithFilters(
            eq("HD-2026"), eq(ContractStatus.ACTIVE), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(contract), pageable, 1));

    Page<ContractSummaryResponse> page =
        contractService.getContracts(pageable, "  HD-2026  ", ContractStatus.ACTIVE);

    assertThat(page.getTotalElements()).isEqualTo(1);
    ContractSummaryResponse summary = page.getContent().getFirst();
    assertThat(summary.contractCode()).isEqualTo("HD-2026-0187");
    assertThat(summary.tenantName()).isEqualTo("Nguyễn Hoàng Nam");
    assertThat(summary.roomNumber()).isEqualTo("P.302");
  }

  @Test
  @DisplayName(
      "renewContract: locks contract, captures current room basePrice, appends immutable renewal, invokes catch-up, and uploads optional annex")
  void renewContract_whenValidWithAnnex_shouldLockRoomPriceAppendRenewalAndAttachDoc() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 2, 29)).build();
    MockMultipartFile annexDoc =
        new MockMultipartFile("annexDocuments", "phu-luc.pdf", "application/pdf", VALID_PDF_BYTES);

    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contractId))
        .thenReturn(Optional.empty());
    when(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).thenReturn(false);
    when(contractRenewalRepository.saveAndFlush(any(ContractRenewal.class)))
        .thenAnswer(
            inv -> {
              ContractRenewal r = inv.getArgument(0);
              r.setId(UUID.randomUUID());
              return r;
            });
    when(contractDocumentRepository.saveAndFlush(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              if (d.getId() == null) {
                d.setId(UUID.randomUUID());
              }
              return d;
            });

    ContractDetailResponse res =
        contractService.renewContract(contractId, request, List.of(annexDoc));

    verify(contractRepository).findByIdForUpdate(contractId);
    verify(contractRepository, never()).save(any());

    ArgumentCaptor<ContractRenewal> renewalCaptor = ArgumentCaptor.forClass(ContractRenewal.class);
    verify(contractRenewalRepository).saveAndFlush(renewalCaptor.capture());
    ContractRenewal persisted = renewalCaptor.getValue();
    assertThat(persisted.getSequenceNo()).isEqualTo(1);
    assertThat(persisted.getSignedOn()).isEqualTo(LocalDate.of(2027, 8, 15));
    assertThat(persisted.getPreviousEndOn()).isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(persisted.getEffectiveFrom()).isEqualTo(LocalDate.of(2027, 9, 1));
    assertThat(persisted.getNewEndOn()).isEqualTo(LocalDate.of(2028, 2, 29));
    assertThat(persisted.getMonthlyRent()).isEqualByComparingTo("1800000");
    assertThat(persisted.getMonthlyRent().scale()).isZero();

    verify(invoiceIssuanceService).issueRenewalCatchUpInvoiceIfEligible(contract, persisted);

    assertThat(contract.getOriginalEndOn()).isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(contract.getOriginalMonthlyRent()).isEqualByComparingTo("1500000.00");

    assertThat(res.endDate()).isEqualTo(LocalDate.of(2028, 2, 29));
    assertThat(res.monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(res.status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(res.renewals()).hasSize(1);
    assertThat(res.renewals().getFirst().sequenceNo()).isEqualTo(1);
    assertThat(res.renewals().getFirst().monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(res.renewals().getFirst().effectiveFrom()).isEqualTo(LocalDate.of(2027, 9, 1));
    assertThat(res.renewals().getFirst().newEndOn()).isEqualTo(LocalDate.of(2028, 2, 29));

    assertThat(contract.getDocuments()).hasSize(1);
    ContractDocument savedDoc = contract.getDocuments().getFirst();
    assertThat(savedDoc.getRenewal()).isSameAs(persisted);
    assertThat(savedDoc.getDocumentCategory()).isEqualTo(ContractDocumentCategory.RENEWAL_ANNEX);
    assertThat(savedDoc.getStorageFileKey()).startsWith("contracts/" + contractId + "/");
    assertThat(savedDoc.getStorageFileKey()).doesNotContain("/uploads/");
    assertThat(res.documents().getFirst().renewalId()).isEqualTo(persisted.getId());
    assertThat(res.documents().getFirst().fileUrl())
        .isEqualTo(
            "/api/v1/admin/contracts/"
                + contractId
                + "/documents/"
                + savedDoc.getId()
                + "/content");
  }

  @Test
  @DisplayName("renewContract: succeeds without annexDocuments when null or empty")
  void renewContract_whenNoAnnexDocuments_shouldSucceedWithoutStorageCalls() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 8, 31)).build();

    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contractId))
        .thenReturn(Optional.empty());
    when(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).thenReturn(false);
    when(contractRenewalRepository.saveAndFlush(any(ContractRenewal.class)))
        .thenAnswer(
            inv -> {
              ContractRenewal r = inv.getArgument(0);
              r.setId(UUID.randomUUID());
              return r;
            });

    ContractDetailResponse resNull = contractService.renewContract(contractId, request, null);
    assertThat(resNull.endDate()).isEqualTo(LocalDate.of(2028, 8, 31));
    assertThat(resNull.documents()).isEmpty();
    assertThat(resNull.renewals()).hasSize(1);
    verifyNoInteractions(privateObjectStorage, contractDocumentRepository);
  }

  @Test
  @DisplayName("renewContract: cleans up uploaded annex object if a subsequent upload fails")
  void renewContract_whenAnnexUploadFails_shouldCleanupAndRethrow() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 8, 31)).build();
    MockMultipartFile file1 =
        new MockMultipartFile("annexDocuments", "p1.pdf", "application/pdf", VALID_PDF_BYTES);
    MockMultipartFile file2 =
        new MockMultipartFile("annexDocuments", "p2.png", "image/png", VALID_PNG_BYTES);

    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contractId))
        .thenReturn(Optional.empty());
    when(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).thenReturn(false);
    when(contractRenewalRepository.saveAndFlush(any(ContractRenewal.class)))
        .thenAnswer(
            inv -> {
              ContractRenewal r = inv.getArgument(0);
              r.setId(UUID.randomUUID());
              return r;
            });
    when(contractDocumentRepository.saveAndFlush(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              d.setId(UUID.randomUUID());
              return d;
            });

    doNothing()
        .when(privateObjectStorage)
        .putObject(any(), eq("application/pdf"), any(InputStream.class), anyLong());
    doThrow(new BaseAppException(SystemErrorCode.EXTERNAL_SERVICE_ERROR))
        .when(privateObjectStorage)
        .putObject(any(), eq("image/png"), any(InputStream.class), anyLong());

    assertThatThrownBy(
            () -> contractService.renewContract(contractId, request, List.of(file1, file2)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == SystemErrorCode.EXTERNAL_SERVICE_ERROR);

    ArgumentCaptor<String> putKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(privateObjectStorage)
        .putObject(
            putKeyCaptor.capture(),
            eq("application/pdf"),
            any(InputStream.class),
            eq((long) VALID_PDF_BYTES.length));
    verify(privateObjectStorage).deleteObject(putKeyCaptor.getValue());
  }

  @Test
  @DisplayName(
      "renewContract: sequential 2nd renewal chains from 1st renewal, locks updated room price, and leaves 1st renewal untouched")
  void renewContract_secondSequentialRenewal_shouldChainAndUseUpdatedRoomPrice() {
    ContractRenewal firstRenewal =
        ContractRenewal.builder()
            .id(UUID.randomUUID())
            .contract(contract)
            .sequenceNo(1)
            .signedOn(LocalDate.of(2027, 8, 10))
            .previousEndOn(LocalDate.of(2027, 8, 31))
            .effectiveFrom(LocalDate.of(2027, 9, 1))
            .newEndOn(LocalDate.of(2028, 2, 29))
            .monthlyRent(new BigDecimal("1800000"))
            .build();
    contract.getRenewals().add(firstRenewal);
    room.setBasePrice(new BigDecimal("2000000"));

    ContractServiceImpl laterService =
        createServiceWithClock(
            Clock.fixed(Instant.parse("2028-02-26T03:00:00Z"), TimeConfig.DORM_ZONE));

    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 8, 31)).build();
    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contractId))
        .thenReturn(Optional.of(firstRenewal));
    when(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).thenReturn(false);
    when(contractRenewalRepository.saveAndFlush(any(ContractRenewal.class)))
        .thenAnswer(
            inv -> {
              ContractRenewal r = inv.getArgument(0);
              r.setId(UUID.randomUUID());
              return r;
            });

    ContractDetailResponse res = laterService.renewContract(contractId, request, List.of());

    assertThat(res.endDate()).isEqualTo(LocalDate.of(2028, 8, 31));
    assertThat(res.monthlyRent()).isEqualByComparingTo("2000000");
    assertThat(res.renewals()).hasSize(2);
    assertThat(res.renewals().get(0).sequenceNo()).isEqualTo(1);
    assertThat(res.renewals().get(0).monthlyRent()).isEqualByComparingTo("1800000");
    assertThat(res.renewals().get(1).sequenceNo()).isEqualTo(2);
    assertThat(res.renewals().get(1).signedOn()).isEqualTo(LocalDate.of(2028, 2, 26));
    assertThat(res.renewals().get(1).previousEndOn()).isEqualTo(LocalDate.of(2028, 2, 29));
    assertThat(res.renewals().get(1).effectiveFrom()).isEqualTo(LocalDate.of(2028, 3, 1));
    assertThat(res.renewals().get(1).newEndOn()).isEqualTo(LocalDate.of(2028, 8, 31));
    assertThat(res.renewals().get(1).monthlyRent()).isEqualByComparingTo("2000000");
    assertThat(firstRenewal.getMonthlyRent()).isEqualByComparingTo("1800000");
    assertThat(contract.getOriginalMonthlyRent()).isEqualByComparingTo("1500000.00");
  }

  @Test
  @DisplayName(
      "renewContract: rejects missing, liquidated, inactive tenant, non-occupied bed, or archived/missing room")
  void renewContract_preconditionRejections() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 8, 31)).build();

    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.CONTRACT_NOT_FOUND);

    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    contract.setStatus(ContractStatus.LIQUIDATED);
    assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.CONTRACT_ALREADY_TERMINATED);

    contract.setStatus(ContractStatus.ACTIVE);
    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
    assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == TenantErrorCode.TENANT_NOT_ACTIVE);

    tenant.setResidentStatus(ResidentStatus.ACTIVE);
    bed.setStatus(BedStatus.AVAILABLE);
    assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.BED_NOT_AVAILABLE);

    bed.setStatus(BedStatus.OCCUPIED);
    room.setArchivedAt(Instant.now());
    assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.ROOM_ARCHIVED);

    room.setArchivedAt(null);
    bed.setRoom(null);
    assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == RoomErrorCode.ROOM_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "renewContract: enforces Asia/Ho_Chi_Minh expiry boundary (rejects 409 CONTRACT_409_EXPIRED at +07:00 midnight)")
  void renewContract_enforcesDormZoneExpiryBoundary() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 8, 31)).build();

    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contractId))
        .thenReturn(Optional.empty());

    // 2027-08-31T17:00:00Z == 2027-09-01T00:00:00+07:00 (expired by 1 second in dorm zone)
    ContractServiceImpl expiredService =
        createServiceWithClock(Clock.fixed(Instant.parse("2027-08-31T17:00:00Z"), ZoneOffset.UTC));
    assertThatThrownBy(() -> expiredService.renewContract(contractId, request, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.CONTRACT_EXPIRED);
    verify(invoiceIssuanceService, never()).hasUnsettledInvoices(any());
    verify(contractRenewalRepository, never()).saveAndFlush(any());

    // 2027-08-31T16:59:59Z == 2027-08-31T23:59:59+07:00 (still on end date -> allowed)
    when(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).thenReturn(false);
    when(contractRenewalRepository.saveAndFlush(any(ContractRenewal.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    ContractServiceImpl lastSecondService =
        createServiceWithClock(Clock.fixed(Instant.parse("2027-08-31T16:59:59Z"), ZoneOffset.UTC));

    ContractDetailResponse response = lastSecondService.renewContract(contractId, request, null);
    assertThat(response.renewals()).hasSize(1);
    assertThat(response.renewals().getFirst().signedOn()).isEqualTo(LocalDate.of(2027, 8, 31));
  }

  @Test
  @DisplayName("renewContract: rejects when tenant has unsettled invoices (409)")
  void renewContract_whenUnsettledInvoices_shouldThrowConflict() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 8, 31)).build();

    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contractId))
        .thenReturn(Optional.empty());
    when(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).thenReturn(true);

    assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES);
    verify(contractRenewalRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "renewContract: rejects null, non-after, non-month-end (including leap-year 2028-02-28), and non-day1 continuity dates")
  void renewContract_whenInvalidDates_shouldThrowInvalidContractDates() {
    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contractId))
        .thenReturn(Optional.empty());
    when(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).thenReturn(false);

    for (RenewContractRequest badRequest :
        List.of(
            new RenewContractRequest(null),
            new RenewContractRequest(LocalDate.of(2027, 7, 31)),
            new RenewContractRequest(LocalDate.of(2027, 8, 31)),
            new RenewContractRequest(LocalDate.of(2027, 9, 15)),
            new RenewContractRequest(LocalDate.of(2028, 2, 28)))) {
      assertThatThrownBy(() -> contractService.renewContract(contractId, badRequest, null))
          .isInstanceOf(BaseAppException.class)
          .matches(
              ex ->
                  ((BaseAppException) ex).getErrorCode()
                      == ContractErrorCode.INVALID_CONTRACT_DATES);
    }
    assertThatThrownBy(() -> contractService.renewContract(contractId, null, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode() == ContractErrorCode.INVALID_CONTRACT_DATES);

    // Non-month-end currentEndDate breaks effectiveFrom day-01 continuity
    contract.setOriginalEndOn(LocalDate.of(2027, 8, 15));
    assertThatThrownBy(
            () ->
                contractService.renewContract(
                    contractId, new RenewContractRequest(LocalDate.of(2027, 9, 30)), null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode() == ContractErrorCode.INVALID_CONTRACT_DATES);
    verify(contractRenewalRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "renewContract: translates concurrent unique constraint races to 409 CONTRACT_RENEWAL_CONFLICT and rethrows non-unique violations")
  void renewContract_uniqueConstraintRace_shouldTranslateToConflict() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 8, 31)).build();
    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contractId))
        .thenReturn(Optional.empty());
    when(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).thenReturn(false);

    for (String constraint :
        List.of("uq_contract_renewals_sequence", "uq_contract_renewals_effective")) {
      DataIntegrityViolationException uniqueRace =
          new DataIntegrityViolationException(
              "duplicate",
              new ConstraintViolationException(
                  "dup", new SQLException("duplicate key", "23505"), constraint));
      when(contractRenewalRepository.saveAndFlush(any(ContractRenewal.class)))
          .thenThrow(uniqueRace);

      assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
          .isInstanceOf(BaseAppException.class)
          .matches(
              ex ->
                  ((BaseAppException) ex).getErrorCode()
                      == ContractErrorCode.CONTRACT_RENEWAL_CONFLICT);
    }

    DataIntegrityViolationException checkViolation =
        new DataIntegrityViolationException(
            "check failure",
            new ConstraintViolationException(
                "check", new SQLException("check failed", "23514"), "ck_contract_renewals_dates"));
    when(contractRenewalRepository.saveAndFlush(any(ContractRenewal.class)))
        .thenThrow(checkViolation);

    assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
        .isSameAs(checkViolation);
  }

  @Test
  @DisplayName(
      "terminateContract (Checkout): acquires ordered pessimistic locks, revokes ISSUED, cancels PENDING_CARD & PENDING change requests, preserves historical vehicles, and uses Clock")
  void terminateContract_available_shouldLockInOrderTransitionAtomicallyAndPreserveHistory() {
    UUID managerUserId = UUID.randomUUID();
    StaffProfile managerStaff =
        StaffProfile.builder()
            .id(UUID.randomUUID())
            .fullName("Manager A")
            .phone("0909111222")
            .build();

    Vehicle pendingCardVehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .vehicleType(VehicleType.BICYCLE)
            .brand("Thống Nhất")
            .color("Xanh")
            .cardCode(null)
            .status(VehicleStatus.PENDING_CARD)
            .build();
    Instant oldReplacedAt = Instant.parse("2027-01-01T00:00:00Z");
    Instant oldRejectedAt = Instant.parse("2027-02-01T00:00:00Z");
    Instant oldRevokedAt = Instant.parse("2027-03-01T00:00:00Z");
    Instant oldCancelledAt = Instant.parse("2027-04-01T00:00:00Z");
    Vehicle replacedVehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X1 100.01")
            .brand("Honda")
            .color("Đỏ")
            .cardCode("THE-0187")
            .status(VehicleStatus.REPLACED)
            .replacedAt(oldReplacedAt)
            .build();
    Vehicle rejectedVehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .vehicleType(VehicleType.BICYCLE)
            .brand("Giant")
            .color("Trắng")
            .cardCode(null)
            .status(VehicleStatus.REJECTED)
            .rejectedAt(oldRejectedAt)
            .rejectionReason("Ảnh mờ")
            .build();
    Vehicle previouslyRevokedVehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .vehicleType(VehicleType.BICYCLE)
            .brand("Asama")
            .color("Bạc")
            .cardCode("THE-OLD-01")
            .status(VehicleStatus.REVOKED)
            .revokedAt(oldRevokedAt)
            .build();
    Vehicle previouslyCancelledVehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .vehicleType(VehicleType.BICYCLE)
            .brand("Martin")
            .color("Xám")
            .cardCode(null)
            .status(VehicleStatus.CANCELLED)
            .cancelledAt(oldCancelledAt)
            .build();

    VehicleChangeRequest pendingChangeRequest =
        VehicleChangeRequest.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .currentVehicle(vehicle)
            .proposedVehicleType(VehicleType.MANUAL_MOTORCYCLE)
            .proposedLicensePlate("59-X2 222.33")
            .proposedBrand("Yamaha")
            .proposedColor("Xanh")
            .status(VehicleChangeRequestStatus.PENDING)
            .build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(roomRepository.findByIdUnfilteredForUpdate(room.getId())).thenReturn(Optional.of(room));
    when(bedRepository.findByIdForUpdate(bed.getId())).thenReturn(Optional.of(bed));
    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(tenantRepository.findByIdForUpdate(tenant.getId())).thenReturn(Optional.of(tenant));
    when(vehicleRepository.findByTenantIdForUpdateOrderById(tenant.getId()))
        .thenReturn(
            List.of(
                vehicle,
                pendingCardVehicle,
                replacedVehicle,
                rejectedVehicle,
                previouslyRevokedVehicle,
                previouslyCancelledVehicle));
    when(vehicleChangeRequestRepository.findByTenantIdAndStatusForUpdateOrderById(
            tenant.getId(), VehicleChangeRequestStatus.PENDING))
        .thenReturn(List.of(pendingChangeRequest));
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(contractRepository.saveAndFlush(any(Contract.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(bedRepository.saveAndFlush(any(Bed.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
    when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
    when(vehicleChangeRequestRepository.saveAndFlush(any(VehicleChangeRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    CheckoutContractRequest request =
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.AVAILABLE)
            .handoverConfirmed(true)
            .build();

    ContractDetailResponse res =
        contractService.terminateContract(managerUserId, contractId, request);

    InOrder lockOrder =
        Mockito.inOrder(
            roomRepository,
            bedRepository,
            contractRepository,
            tenantRepository,
            vehicleRepository,
            vehicleChangeRequestRepository);
    lockOrder.verify(roomRepository).findByIdUnfilteredForUpdate(room.getId());
    lockOrder.verify(bedRepository).findByIdForUpdate(bed.getId());
    lockOrder.verify(contractRepository).findByIdForUpdate(contractId);
    lockOrder.verify(tenantRepository).findByIdForUpdate(tenant.getId());
    lockOrder.verify(vehicleRepository).findByTenantIdForUpdateOrderById(tenant.getId());
    lockOrder
        .verify(vehicleChangeRequestRepository)
        .findByTenantIdAndStatusForUpdateOrderById(
            tenant.getId(), VehicleChangeRequestStatus.PENDING);

    assertThat(res.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(res.checkedOutAt()).isEqualTo(DEFAULT_RENEWAL_INSTANT);
    assertThat(contract.getStatus()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(contract.getCheckedOutAt()).isEqualTo(DEFAULT_RENEWAL_INSTANT);
    assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    assertThat(tenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);

    // ISSUED -> REVOKED (cardCode kept)
    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.REVOKED);
    assertThat(vehicle.getRevokedAt()).isEqualTo(DEFAULT_RENEWAL_INSTANT);
    assertThat(vehicle.getCancelledAt()).isNull();
    assertThat(vehicle.getCardCode()).isEqualTo("THE-0187");

    // PENDING_CARD -> CANCELLED (cardCode stays null, revokedAt stays null)
    assertThat(pendingCardVehicle.getStatus()).isEqualTo(VehicleStatus.CANCELLED);
    assertThat(pendingCardVehicle.getCancelledAt()).isEqualTo(DEFAULT_RENEWAL_INSTANT);
    assertThat(pendingCardVehicle.getRevokedAt()).isNull();
    assertThat(pendingCardVehicle.getCardCode()).isNull();

    // Historical vehicles remain completely untouched
    assertThat(replacedVehicle.getStatus()).isEqualTo(VehicleStatus.REPLACED);
    assertThat(replacedVehicle.getReplacedAt()).isEqualTo(oldReplacedAt);
    assertThat(replacedVehicle.getRevokedAt()).isNull();
    assertThat(rejectedVehicle.getStatus()).isEqualTo(VehicleStatus.REJECTED);
    assertThat(rejectedVehicle.getRejectedAt()).isEqualTo(oldRejectedAt);
    assertThat(previouslyRevokedVehicle.getStatus()).isEqualTo(VehicleStatus.REVOKED);
    assertThat(previouslyRevokedVehicle.getRevokedAt()).isEqualTo(oldRevokedAt);
    assertThat(previouslyCancelledVehicle.getStatus()).isEqualTo(VehicleStatus.CANCELLED);
    assertThat(previouslyCancelledVehicle.getCancelledAt()).isEqualTo(oldCancelledAt);

    verify(vehicleRepository).saveAndFlush(vehicle);
    verify(vehicleRepository).saveAndFlush(pendingCardVehicle);
    verify(vehicleRepository, never()).saveAndFlush(replacedVehicle);
    verify(vehicleRepository, never()).saveAndFlush(rejectedVehicle);
    verify(vehicleRepository, never()).saveAndFlush(previouslyRevokedVehicle);
    verify(vehicleRepository, never()).saveAndFlush(previouslyCancelledVehicle);

    // PENDING VehicleChangeRequest -> CANCELLED
    assertThat(pendingChangeRequest.getStatus()).isEqualTo(VehicleChangeRequestStatus.CANCELLED);
    assertThat(pendingChangeRequest.getProcessedAt()).isEqualTo(DEFAULT_RENEWAL_INSTANT);
    assertThat(pendingChangeRequest.getProcessedByStaff()).isSameAs(managerStaff);
    assertThat(pendingChangeRequest.getNewVehicle()).isNull();
    assertThat(pendingChangeRequest.getRejectionReason()).isNull();
    verify(vehicleChangeRequestRepository).saveAndFlush(pendingChangeRequest);

    verify(contractRepository).saveAndFlush(contract);
    verify(bedRepository).saveAndFlush(bed);
    verify(tenantRepository).saveAndFlush(tenant);
    verifyNoInteractions(invoiceIssuanceService);
  }

  @Test
  @DisplayName(
      "terminateContract (Checkout): supports MAINTENANCE bed status, ignores unsettled invoices, and allows null managerUserId")
  void terminateContract_maintenanceWithoutManager_shouldSucceedEvenWithUnsettledInvoices() {
    VehicleChangeRequest pendingChangeRequest =
        VehicleChangeRequest.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .currentVehicle(vehicle)
            .proposedVehicleType(VehicleType.SCOOTER)
            .proposedLicensePlate("59-X3 000.99")
            .proposedBrand("Honda")
            .proposedColor("Trắng")
            .status(VehicleChangeRequestStatus.PENDING)
            .build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(roomRepository.findByIdUnfilteredForUpdate(room.getId())).thenReturn(Optional.of(room));
    when(bedRepository.findByIdForUpdate(bed.getId())).thenReturn(Optional.of(bed));
    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(tenantRepository.findByIdForUpdate(tenant.getId())).thenReturn(Optional.of(tenant));
    when(vehicleRepository.findByTenantIdForUpdateOrderById(tenant.getId()))
        .thenReturn(List.of(vehicle));
    when(vehicleChangeRequestRepository.findByTenantIdAndStatusForUpdateOrderById(
            tenant.getId(), VehicleChangeRequestStatus.PENDING))
        .thenReturn(List.of(pendingChangeRequest));
    when(contractRepository.saveAndFlush(any(Contract.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(bedRepository.saveAndFlush(any(Bed.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
    when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
    when(vehicleChangeRequestRepository.saveAndFlush(any(VehicleChangeRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    CheckoutContractRequest request =
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.MAINTENANCE)
            .handoverConfirmed(true)
            .build();

    ContractDetailResponse res = contractService.terminateContract(contractId, request);

    assertThat(res.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(res.checkedOutAt()).isEqualTo(DEFAULT_RENEWAL_INSTANT);
    assertThat(bed.getStatus()).isEqualTo(BedStatus.MAINTENANCE);
    assertThat(tenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);
    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.REVOKED);
    assertThat(pendingChangeRequest.getStatus()).isEqualTo(VehicleChangeRequestStatus.CANCELLED);
    assertThat(pendingChangeRequest.getProcessedAt()).isEqualTo(DEFAULT_RENEWAL_INSTANT);
    assertThat(pendingChangeRequest.getProcessedByStaff()).isNull();
    verifyNoInteractions(staffProfileRepository, invoiceIssuanceService);
  }

  @Test
  @DisplayName(
      "terminateContract: rejects unconfirmed handover, invalid bed status, missing contract, and already liquidated contract")
  void terminateContract_rejections_shouldThrowExpectedErrorCodes() {
    assertThatThrownBy(() -> contractService.terminateContract(contractId, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.CHECKOUT_HANDOVER_NOT_CONFIRMED);

    CheckoutContractRequest unconfirmedFalse =
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.AVAILABLE)
            .handoverConfirmed(false)
            .build();
    assertThatThrownBy(() -> contractService.terminateContract(contractId, unconfirmedFalse))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.CHECKOUT_HANDOVER_NOT_CONFIRMED);

    CheckoutContractRequest unconfirmedNull =
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.AVAILABLE)
            .handoverConfirmed(null)
            .build();
    assertThatThrownBy(() -> contractService.terminateContract(contractId, unconfirmedNull))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.CHECKOUT_HANDOVER_NOT_CONFIRMED);

    CheckoutContractRequest nullBedStatus =
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(null)
            .handoverConfirmed(true)
            .build();
    assertThatThrownBy(() -> contractService.terminateContract(contractId, nullBedStatus))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.INVALID_CHECKOUT_BED_STATUS);

    CheckoutContractRequest occupiedBedStatus =
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.OCCUPIED)
            .handoverConfirmed(true)
            .build();
    assertThatThrownBy(() -> contractService.terminateContract(contractId, occupiedBedStatus))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.INVALID_CHECKOUT_BED_STATUS);

    verifyNoInteractions(
        roomRepository,
        bedRepository,
        contractRepository,
        tenantRepository,
        vehicleRepository,
        vehicleChangeRequestRepository);

    CheckoutContractRequest validRequest =
        CheckoutContractRequest.builder()
            .bedStatusAfterCheckout(BedStatus.AVAILABLE)
            .handoverConfirmed(true)
            .build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> contractService.terminateContract(contractId, validRequest))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.CONTRACT_NOT_FOUND);

    contract.setStatus(ContractStatus.LIQUIDATED);
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(roomRepository.findByIdUnfilteredForUpdate(room.getId())).thenReturn(Optional.of(room));
    when(bedRepository.findByIdForUpdate(bed.getId())).thenReturn(Optional.of(bed));
    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));

    assertThatThrownBy(() -> contractService.terminateContract(contractId, validRequest))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.CONTRACT_ALREADY_TERMINATED);

    verify(contractRepository, never()).saveAndFlush(any());
    verify(bedRepository, never()).saveAndFlush(any());
    verifyNoInteractions(tenantRepository, vehicleRepository, vehicleChangeRequestRepository);
  }

  @Test
  @DisplayName("uploadDocuments: when file size > 10MB should throw DOCUMENT_FILE_SIZE_EXCEEDED")
  void uploadDocuments_whenFileSizeExceeded_shouldThrow() {
    MockMultipartFile hugeFile =
        new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[11 * 1024 * 1024]);

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(hugeFile)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.DOCUMENT_FILE_SIZE_EXCEEDED);
  }

  @Test
  @DisplayName(
      "uploadDocuments: stores private object keys and returns authorized admin streaming URLs")
  void uploadDocuments_whenValid_shouldSaveDocsAndReturnStreamingUrls() {
    MockMultipartFile file1 =
        new MockMultipartFile("files", "page1.jpg", "image/jpeg", VALID_JPEG_BYTES);
    MockMultipartFile file2 =
        new MockMultipartFile("files", "page2.png", "image/png", VALID_PNG_BYTES);

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.saveAndFlush(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              if (d.getId() == null) {
                d.setId(UUID.randomUUID());
              }
              return d;
            });

    List<ContractDocumentResponse> docs =
        contractService.uploadDocuments(
            contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(file1, file2));

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(privateObjectStorage)
        .putObject(
            keyCaptor.capture(),
            eq("image/jpeg"),
            any(InputStream.class),
            eq((long) VALID_JPEG_BYTES.length));
    verify(privateObjectStorage)
        .putObject(
            keyCaptor.capture(),
            eq("image/png"),
            any(InputStream.class),
            eq((long) VALID_PNG_BYTES.length));

    assertThat(docs).hasSize(2);
    assertThat(docs.getFirst().renewalId()).isNull();
    assertThat(docs.getFirst().fileUrl())
        .isEqualTo(
            "/api/v1/admin/contracts/"
                + contractId
                + "/documents/"
                + docs.getFirst().id()
                + "/content");
    assertThat(docs.get(1).fileUrl())
        .isEqualTo(
            "/api/v1/admin/contracts/"
                + contractId
                + "/documents/"
                + docs.get(1).id()
                + "/content");
    assertThat(docs.getFirst().fileUrl())
        .doesNotContain("/uploads/")
        .doesNotContain(keyCaptor.getAllValues().getFirst());
    assertThat(keyCaptor.getAllValues().getFirst()).startsWith("contracts/" + contractId + "/");
  }

  @Test
  @DisplayName("uploadDocuments: cleans up previously uploaded object when a subsequent file fails")
  void uploadDocuments_whenPartialUploadFails_shouldCleanupUploadedObject() {
    MockMultipartFile file1 =
        new MockMultipartFile("files", "page1.jpg", "image/jpeg", VALID_JPEG_BYTES);
    MockMultipartFile file2 =
        new MockMultipartFile("files", "page2.png", "image/png", VALID_PNG_BYTES);

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.saveAndFlush(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              if (d.getId() == null) {
                d.setId(UUID.randomUUID());
              }
              return d;
            });

    doNothing()
        .when(privateObjectStorage)
        .putObject(any(), eq("image/jpeg"), any(InputStream.class), anyLong());
    doThrow(new BaseAppException(SystemErrorCode.EXTERNAL_SERVICE_ERROR))
        .when(privateObjectStorage)
        .putObject(any(), eq("image/png"), any(InputStream.class), anyLong());

    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(file1, file2)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == SystemErrorCode.EXTERNAL_SERVICE_ERROR);

    ArgumentCaptor<String> putKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(privateObjectStorage)
        .putObject(
            putKeyCaptor.capture(),
            eq("image/jpeg"),
            any(InputStream.class),
            eq((long) VALID_JPEG_BYTES.length));
    verify(privateObjectStorage).deleteObject(putKeyCaptor.getValue());
  }

  @Test
  @DisplayName(
      "uploadDocuments: accepts all 7 supported extensions and browser MIME aliases while persisting canonical MIME")
  void uploadDocuments_acceptsAllSevenExtensionsAndAliases_storesCanonicalMime() {
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.saveAndFlush(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              if (d.getId() == null) {
                d.setId(UUID.randomUUID());
              }
              return d;
            });

    List<MultipartFile> validFiles =
        List.of(
            new MockMultipartFile(
                "files", "hopdong.PDF", "application/x-pdf; charset=utf-8", VALID_PDF_BYTES),
            new MockMultipartFile("files", "scan1.jpg", "image/pjpeg", VALID_JPEG_BYTES),
            new MockMultipartFile(
                "files", "scan2.jpeg", "application/octet-stream", VALID_JPEG_BYTES),
            new MockMultipartFile("files", "scan3.png", "image/png", VALID_PNG_BYTES),
            new MockMultipartFile("files", "scan4.webp", null, VALID_WEBP_BYTES),
            new MockMultipartFile("files", "legacy.doc", "application/x-msword", VALID_DOC_BYTES),
            new MockMultipartFile(
                "files", "modern.docx", "application/x-zip-compressed", VALID_DOCX_BYTES),
            new MockMultipartFile("files", "modern2.docx", "application/zip", VALID_DOCX_BYTES));

    List<ContractDocumentResponse> responses =
        contractService.uploadDocuments(
            contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, null, validFiles);

    assertThat(responses)
        .extracting(ContractDocumentResponse::fileType)
        .containsExactly(
            "application/pdf",
            "image/jpeg",
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
  }

  @Test
  @DisplayName(
      "uploadDocuments: rejects spoofed magic bytes, truncated headers, mismatched Content-Type, invalid filenames, and 0-byte files")
  void uploadDocuments_rejectsSpoofedMagicBytesMismatchedContentTypeAndInvalidFiles() {
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    byte[] htmlBytes = "<html>spoofed</html>".getBytes(StandardCharsets.UTF_8);
    byte[] riffAviBytes =
        new byte[] {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'A', 'V', 'I', ' '};

    List<MockMultipartFile> invalidFiles =
        List.of(
            new MockMultipartFile("files", "spoof.pdf", "application/pdf", htmlBytes),
            new MockMultipartFile("files", "cross.pdf", "application/pdf", VALID_PNG_BYTES),
            new MockMultipartFile(
                "files", "short.pdf", "application/pdf", "%PDF".getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile("files", "spoof.jpg", "image/jpeg", htmlBytes),
            new MockMultipartFile(
                "files", "short.jpeg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8}),
            new MockMultipartFile("files", "spoof.png", "image/png", htmlBytes),
            new MockMultipartFile(
                "files",
                "short.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A}),
            new MockMultipartFile("files", "spoof.webp", "image/webp", riffAviBytes),
            new MockMultipartFile(
                "files",
                "short.webp",
                "image/webp",
                new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B'}),
            new MockMultipartFile("files", "spoof.doc", "application/msword", VALID_DOCX_BYTES),
            new MockMultipartFile(
                "files",
                "short.doc",
                "application/msword",
                new byte[] {
                  (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A
                }),
            new MockMultipartFile(
                "files",
                "spoof.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                VALID_DOC_BYTES),
            new MockMultipartFile(
                "files",
                "short.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[] {0x50, 0x4B, 0x03}),
            new MockMultipartFile("files", "mismatch.pdf", "image/png", VALID_PDF_BYTES),
            new MockMultipartFile("files", "mismatch2.pdf", "text/plain", VALID_PDF_BYTES),
            new MockMultipartFile("files", "mismatch3.jpg", "text/html", VALID_JPEG_BYTES),
            new MockMultipartFile(
                "files", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile("files", "noext", "application/pdf", VALID_PDF_BYTES),
            new MockMultipartFile("files", ".pdf", "application/pdf", VALID_PDF_BYTES),
            new MockMultipartFile("files", "   ", "application/pdf", VALID_PDF_BYTES),
            new MockMultipartFile("files", "empty.pdf", "application/pdf", new byte[0]));

    for (MockMultipartFile badFile : invalidFiles) {
      assertThatThrownBy(
              () ->
                  contractService.uploadDocuments(
                      contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(badFile)))
          .as(
              "Expected UNSUPPORTED_DOCUMENT_FORMAT for %s (%s)",
              badFile.getOriginalFilename(), badFile.getContentType())
          .isInstanceOf(BaseAppException.class)
          .matches(
              ex ->
                  ((BaseAppException) ex).getErrorCode()
                      == ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT);
    }

    verifyNoInteractions(privateObjectStorage, contractDocumentRepository);
  }

  @Test
  @DisplayName(
      "uploadDocuments: rolls back and cleans up earlier object when second file fails magic-byte check")
  void uploadDocuments_whenSecondFileFailsMagicBytes_shouldCleanupFirstUpload() {
    MockMultipartFile validFile =
        new MockMultipartFile("files", "page1.jpg", "image/jpeg", VALID_JPEG_BYTES);
    MockMultipartFile spoofedSecond =
        new MockMultipartFile(
            "files", "page2.png", "image/png", "not-a-png-header".getBytes(StandardCharsets.UTF_8));

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.saveAndFlush(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              d.setId(UUID.randomUUID());
              return d;
            });

    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractId,
                    ContractDocumentCategory.ORIGINAL_CONTRACT,
                    List.of(validFile, spoofedSecond)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT);

    ArgumentCaptor<String> putKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(privateObjectStorage)
        .putObject(
            putKeyCaptor.capture(),
            eq("image/jpeg"),
            any(InputStream.class),
            eq((long) VALID_JPEG_BYTES.length));
    verify(privateObjectStorage).deleteObject(putKeyCaptor.getValue());
  }

  @Test
  @DisplayName(
      "renewContract: rejects >10MB or spoofed annex files and cleans up earlier uploaded annex on later failure")
  void renewContract_whenAnnexInvalidOrOversized_shouldRejectAndCleanupPriorUploads() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 8, 31)).build();
    when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contractId))
        .thenReturn(Optional.empty());
    when(invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())).thenReturn(false);
    when(contractRenewalRepository.saveAndFlush(any(ContractRenewal.class)))
        .thenAnswer(
            inv -> {
              ContractRenewal r = inv.getArgument(0);
              r.setId(UUID.randomUUID());
              return r;
            });

    MockMultipartFile oversized =
        new MockMultipartFile(
            "annexDocuments", "huge.pdf", "application/pdf", new byte[11 * 1024 * 1024]);
    assertThatThrownBy(() -> contractService.renewContract(contractId, request, List.of(oversized)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.DOCUMENT_FILE_SIZE_EXCEEDED);
    verifyNoInteractions(privateObjectStorage);

    MockMultipartFile validPdf =
        new MockMultipartFile("annexDocuments", "a1.pdf", "application/pdf", VALID_PDF_BYTES);
    MockMultipartFile spoofedPng =
        new MockMultipartFile(
            "annexDocuments",
            "a2.png",
            "image/png",
            "spoofed-png-bytes".getBytes(StandardCharsets.UTF_8));
    when(contractDocumentRepository.saveAndFlush(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              d.setId(UUID.randomUUID());
              return d;
            });

    assertThatThrownBy(
            () -> contractService.renewContract(contractId, request, List.of(validPdf, spoofedPng)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(privateObjectStorage)
        .putObject(
            keyCaptor.capture(),
            eq("application/pdf"),
            any(InputStream.class),
            eq((long) VALID_PDF_BYTES.length));
    verify(privateObjectStorage).deleteObject(keyCaptor.getValue());
  }

  @Test
  @DisplayName(
      "uploadDocuments: validates RENEWAL_ANNEX renewalId linkage and rejects mismatched or invalid links before upload")
  void uploadDocuments_renewalIdValidation_enforcesCategoryAndContractOwnership() {
    UUID validRenewalId = UUID.randomUUID();
    ContractRenewal sameContractRenewal =
        ContractRenewal.builder().id(validRenewalId).contract(contract).sequenceNo(1).build();
    MockMultipartFile validPdf =
        new MockMultipartFile("files", "annex.pdf", "application/pdf", VALID_PDF_BYTES);

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.findById(validRenewalId))
        .thenReturn(Optional.of(sameContractRenewal));
    when(contractDocumentRepository.saveAndFlush(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              d.setId(UUID.randomUUID());
              return d;
            });

    List<ContractDocumentResponse> uploaded =
        contractService.uploadDocuments(
            contractId, ContractDocumentCategory.RENEWAL_ANNEX, validRenewalId, List.of(validPdf));
    assertThat(uploaded).hasSize(1);
    assertThat(uploaded.getFirst().renewalId()).isEqualTo(validRenewalId);
    assertThat(contract.getDocuments().getFirst().getRenewal()).isSameAs(sameContractRenewal);

    // Missing renewalId on RENEWAL_ANNEX -> 400 CONTRACT_400_DOCUMENT_RENEWAL
    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractId, ContractDocumentCategory.RENEWAL_ANNEX, null, List.of(validPdf)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.INVALID_DOCUMENT_RENEWAL_LINK);

    // Non-existent renewalId -> 400
    UUID unknownRenewalId = UUID.randomUUID();
    when(contractRenewalRepository.findById(unknownRenewalId)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractId,
                    ContractDocumentCategory.RENEWAL_ANNEX,
                    unknownRenewalId,
                    List.of(validPdf)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.INVALID_DOCUMENT_RENEWAL_LINK);

    // Cross-contract renewalId -> 400
    UUID otherRenewalId = UUID.randomUUID();
    Contract otherContract = Contract.builder().id(UUID.randomUUID()).build();
    when(contractRenewalRepository.findById(otherRenewalId))
        .thenReturn(
            Optional.of(
                ContractRenewal.builder().id(otherRenewalId).contract(otherContract).build()));
    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractId,
                    ContractDocumentCategory.RENEWAL_ANNEX,
                    otherRenewalId,
                    List.of(validPdf)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.INVALID_DOCUMENT_RENEWAL_LINK);

    // Non-null renewalId on non-RENEWAL_ANNEX categories -> 400
    for (ContractDocumentCategory nonAnnexCategory :
        new ContractDocumentCategory[] {
          null,
          ContractDocumentCategory.ORIGINAL_CONTRACT,
          ContractDocumentCategory.CHECKOUT_HANDOVER,
          ContractDocumentCategory.OTHER
        }) {
      assertThatThrownBy(
              () ->
                  contractService.uploadDocuments(
                      contractId, nonAnnexCategory, validRenewalId, List.of(validPdf)))
          .isInstanceOf(BaseAppException.class)
          .matches(
              ex ->
                  ((BaseAppException) ex).getErrorCode()
                      == ContractErrorCode.INVALID_DOCUMENT_RENEWAL_LINK);
    }

    // Only the single valid upload hit storage
    verify(privateObjectStorage)
        .putObject(
            any(),
            eq("application/pdf"),
            any(InputStream.class),
            eq((long) VALID_PDF_BYTES.length));
  }

  @Test
  @DisplayName("deleteDocument: deletes directly when no transaction synchronization is active")
  void deleteDocument_withoutTxSync_shouldDeleteDirectly() {
    UUID docId = UUID.randomUUID();
    String key = "contracts/" + contractId + "/doc-1.jpg";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("page1.jpg")
            .storageFileKey(key)
            .mimeType("image/jpeg")
            .build();
    contract.getDocuments().add(doc);

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

    contractService.deleteDocument(contractId, docId);

    verify(contractDocumentRepository).delete(doc);
    verify(contractDocumentRepository).flush();
    verify(privateObjectStorage).deleteObject(key);
  }

  @Test
  @DisplayName("deleteDocument: defers storage deletion until afterCommit when tx sync is active")
  void deleteDocument_withTxSync_shouldDeleteOnlyAfterCommit() {
    UUID docId = UUID.randomUUID();
    String key = "contracts/" + contractId + "/doc-tx.pdf";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("doc-tx.pdf")
            .storageFileKey(key)
            .mimeType("application/pdf")
            .build();
    contract.getDocuments().add(doc);

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

    TransactionSynchronizationManager.initSynchronization();
    contractService.deleteDocument(contractId, docId);

    verify(contractDocumentRepository).delete(doc);
    verify(privateObjectStorage, never()).deleteObject(any());

    List<TransactionSynchronization> syncs =
        TransactionSynchronizationManager.getSynchronizations();
    assertThat(syncs).hasSize(1);
    syncs.getFirst().afterCommit();
    verify(privateObjectStorage).deleteObject(key);
  }

  @Test
  @DisplayName("getMyContract: exposes tenant-scoped streaming URL and never raw storage key")
  void getMyContract_shouldExposeTenantStreamingUrls() {
    UUID docId = UUID.randomUUID();
    String rawKey = "contracts/" + contractId + "/secret-uuid_contract.pdf";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("contract.pdf")
            .storageFileKey(rawKey)
            .mimeType("application/pdf")
            .documentType(ContractDocumentCategory.ORIGINAL_CONTRACT)
            .build();
    contract.getDocuments().add(doc);

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(contractRepository.findFirstByTenantIdAndStatusIn(eq(tenant.getId()), any()))
        .thenReturn(Optional.of(contract));

    ContractDetailResponse response = contractService.getMyContract(tenantUserId);

    assertThat(response.documents()).hasSize(1);
    assertThat(response.documents().getFirst().fileUrl())
        .isEqualTo("/api/v1/tenant/contracts/" + contractId + "/documents/" + docId + "/content")
        .doesNotContain(rawKey);
  }

  @Test
  @DisplayName(
      "loadDocumentContentForAdmin: streams owned document and rejects cross-contract/missing")
  void loadDocumentContentForAdmin_shouldVerifyContractAndStream() {
    UUID docId = UUID.randomUUID();
    String key = "contracts/" + contractId + "/file.pdf";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("file.pdf")
            .storageFileKey(key)
            .mimeType("application/pdf")
            .build();
    StoredObjectContent stored =
        new StoredObjectContent(new ByteArrayResource(new byte[] {1, 2, 3}), "application/pdf", 3);

    when(contractDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(privateObjectStorage.getObject(key)).thenReturn(stored);

    ContractDocumentContent content =
        contractService.loadDocumentContentForAdmin(contractId, docId);
    assertThat(content.contentType()).isEqualTo("application/pdf");
    assertThat(content.contentLength()).isEqualTo(3);
    assertThat(content.originalFilename()).isEqualTo("file.pdf");

    assertThatThrownBy(() -> contractService.loadDocumentContentForAdmin(UUID.randomUUID(), docId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.DOCUMENT_NOT_FOUND);

    when(privateObjectStorage.getObject(key))
        .thenThrow(new ObjectStorageNotFoundException("missing"));
    assertThatThrownBy(() -> contractService.loadDocumentContentForAdmin(contractId, docId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.DOCUMENT_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "loadDocumentContentForTenant: allows CHECKED_OUT owner and returns 404 for non-owner")
  void loadDocumentContentForTenant_shouldAllowCheckedOutOwnerAndHideNonOwner() {
    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
    contract.setStatus(ContractStatus.LIQUIDATED);
    UUID docId = UUID.randomUUID();
    String key = "contracts/" + contractId + "/checkedout.pdf";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("checkedout.pdf")
            .storageFileKey(key)
            .mimeType("application/pdf")
            .build();
    StoredObjectContent stored =
        new StoredObjectContent(new ByteArrayResource(new byte[] {7, 8}), "application/pdf", 2);

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(privateObjectStorage.getObject(key)).thenReturn(stored);

    ContractDocumentContent content =
        contractService.loadDocumentContentForTenant(tenantUserId, contractId, docId);
    assertThat(content.contentLength()).isEqualTo(2);

    UUID otherUserId = UUID.randomUUID();
    Tenant otherTenant = Tenant.builder().id(UUID.randomUUID()).build();
    when(tenantRepository.findByUserId(otherUserId)).thenReturn(Optional.of(otherTenant));

    assertThatThrownBy(
            () -> contractService.loadDocumentContentForTenant(otherUserId, contractId, docId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.CONTRACT_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "getMyContractHistory: allows ACTIVE and CHECKED_OUT tenants to list active/liquidated contracts with tenant-scoped URLs and renewalId")
  void getMyContractHistory_allowsActiveAndCheckedOut_returnsTenantUrls() {
    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
    UUID renewalId = UUID.randomUUID();
    ContractRenewal renewal =
        ContractRenewal.builder()
            .id(renewalId)
            .contract(contract)
            .sequenceNo(1)
            .signedOn(LocalDate.of(2027, 8, 10))
            .previousEndOn(LocalDate.of(2027, 8, 31))
            .effectiveFrom(LocalDate.of(2027, 9, 1))
            .newEndOn(LocalDate.of(2028, 2, 29))
            .monthlyRent(new BigDecimal("1800000"))
            .build();
    contract.getRenewals().add(renewal);

    UUID docId = UUID.randomUUID();
    String rawKey = "contracts/" + contractId + "/annex-secret.pdf";
    ContractDocument annexDoc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .renewal(renewal)
            .originalFilename("annex.pdf")
            .storageFileKey(rawKey)
            .mimeType("application/pdf")
            .documentType(ContractDocumentCategory.RENEWAL_ANNEX)
            .build();
    contract.getDocuments().add(annexDoc);

    UUID olderContractId = UUID.randomUUID();
    Contract olderLiquidated =
        Contract.builder()
            .id(olderContractId)
            .contractCode("HD-2025-0010")
            .tenant(tenant)
            .bed(bed)
            .roomCodeSnapshot("P.302")
            .bedCodeSnapshot("G02")
            .startOn(LocalDate.of(2025, 9, 1))
            .originalEndOn(LocalDate.of(2026, 8, 31))
            .originalMonthlyRent(new BigDecimal("1500000"))
            .depositAmount(new BigDecimal("1500000"))
            .status(ContractStatus.LIQUIDATED)
            .checkedOutAt(Instant.parse("2026-08-31T10:00:00Z"))
            .documents(List.of())
            .renewals(List.of())
            .build();

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(contractRepository.findByTenantIdOrderByStartOnDescCreatedAtDesc(tenant.getId()))
        .thenReturn(List.of(contract, olderLiquidated));

    List<ContractDetailResponse> history = contractService.getMyContractHistory(tenantUserId);

    assertThat(history).hasSize(2);
    assertThat(history.get(0).id()).isEqualTo(contractId);
    assertThat(history.get(0).documents()).hasSize(1);
    assertThat(history.get(0).documents().getFirst().renewalId()).isEqualTo(renewalId);
    assertThat(history.get(0).documents().getFirst().fileUrl())
        .isEqualTo("/api/v1/tenant/contracts/" + contractId + "/documents/" + docId + "/content")
        .doesNotContain(rawKey)
        .doesNotContain("/api/v1/admin/");
    assertThat(history.get(1).id()).isEqualTo(olderContractId);
    assertThat(history.get(1).status()).isEqualTo(ContractStatus.LIQUIDATED);

    UUID missingUserId = UUID.randomUUID();
    when(tenantRepository.findByUserId(missingUserId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> contractService.getMyContractHistory(missingUserId))
        .isInstanceOf(BaseAppException.class)
        .matches(ex -> ((BaseAppException) ex).getErrorCode() == TenantErrorCode.TENANT_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "getMyContractById: returns owner detail for ACTIVE/LIQUIDATED contracts and 404 for non-owner or missing tenant/contract")
  void getMyContractById_enforcesOwnershipAndReturns404() {
    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
    contract.setStatus(ContractStatus.LIQUIDATED);
    UUID docId = UUID.randomUUID();
    String rawKey = "contracts/" + contractId + "/handover.pdf";
    contract
        .getDocuments()
        .add(
            ContractDocument.builder()
                .id(docId)
                .contract(contract)
                .originalFilename("handover.pdf")
                .storageFileKey(rawKey)
                .mimeType("application/pdf")
                .documentType(ContractDocumentCategory.CHECKOUT_HANDOVER)
                .build());

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    ContractDetailResponse detail = contractService.getMyContractById(tenantUserId, contractId);
    assertThat(detail.id()).isEqualTo(contractId);
    assertThat(detail.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(detail.documents().getFirst().fileUrl())
        .isEqualTo("/api/v1/tenant/contracts/" + contractId + "/documents/" + docId + "/content")
        .doesNotContain(rawKey)
        .doesNotContain("/api/v1/admin/");

    // Non-owner tenant -> 404 CONTRACT_404
    UUID otherUserId = UUID.randomUUID();
    Tenant otherTenant = Tenant.builder().id(UUID.randomUUID()).build();
    when(tenantRepository.findByUserId(otherUserId)).thenReturn(Optional.of(otherTenant));
    assertThatThrownBy(() -> contractService.getMyContractById(otherUserId, contractId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.CONTRACT_NOT_FOUND);

    // Missing contract -> 404 CONTRACT_404
    UUID missingContractId = UUID.randomUUID();
    when(contractRepository.findById(missingContractId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> contractService.getMyContractById(tenantUserId, missingContractId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.CONTRACT_NOT_FOUND);

    // Missing tenant profile -> 404 CONTRACT_404
    UUID missingUserId = UUID.randomUUID();
    when(tenantRepository.findByUserId(missingUserId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> contractService.getMyContractById(missingUserId, contractId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.CONTRACT_NOT_FOUND);
  }
}
