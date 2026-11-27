package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.expense.CreateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.expense.UpdateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseReceiptContent;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.entities.FacilityExpense;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.repositories.FacilityExpenseRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.services.impl.FacilityExpenseServiceImpl;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class FacilityExpenseServiceImplTest {

  private static final byte[] VALID_JPEG_BYTES =
      new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
  private static final byte[] VALID_WEBP_BYTES =
      new byte[] {
        'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
      };
  private static final byte[] VALID_PDF_BYTES = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes();

  @Mock private FacilityExpenseRepository facilityExpenseRepository;
  @Mock private StaffProfileRepository staffProfileRepository;
  @Mock private PrivateObjectStorage privateObjectStorage;

  private FacilityExpenseServiceImpl service;
  private UUID managerUserId;
  private UUID managerStaffId;
  private UUID expenseId;
  private User managerUser;
  private StaffProfile managerStaff;

  @BeforeEach
  void setUp() {
    service =
        new FacilityExpenseServiceImpl(
            facilityExpenseRepository, staffProfileRepository, privateObjectStorage);
    managerUserId = UUID.randomUUID();
    managerStaffId = UUID.randomUUID();
    expenseId = UUID.randomUUID();

    managerUser =
        User.builder()
            .id(managerUserId)
            .email("manager.expense@uit.edu.vn")
            .role(UserRole.MANAGER)
            .build();
    managerStaff =
        StaffProfile.builder()
            .id(managerStaffId)
            .user(managerUser)
            .fullName("Nguyen Quan Ly")
            .phone("0909111222")
            .build();
  }

  @AfterEach
  void tearDownTxSync() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName(
      "createExpense: creates across all 5 ExpenseTypes, trims text, converts blank optional fields to null, and leaves receipt null")
  void createExpense_allTypesAndTrimming_succeeds() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(facilityExpenseRepository.existsByExpenseTypeAndExpenseDateBetween(
            eq(ExpenseType.SALARY), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
        .thenReturn(false);
    when(facilityExpenseRepository.saveAndFlush(any(FacilityExpense.class)))
        .thenAnswer(
            inv -> {
              FacilityExpense e = inv.getArgument(0);
              e.setId(UUID.randomUUID());
              e.setCreatedAt(Instant.parse("2026-10-15T01:00:00Z"));
              e.setUpdatedAt(Instant.parse("2026-10-15T01:00:00Z"));
              return e;
            });

    for (ExpenseType type : ExpenseType.values()) {
      CreateFacilityExpenseRequest request =
          new CreateFacilityExpenseRequest(
              type,
              "  Chi phi " + type.name() + "  ",
              new BigDecimal("1500000.00"),
              LocalDate.of(2026, 10, 15),
              "   ",
              "  Ghi chu chi tiet  ");

      FacilityExpenseResponse response = service.createExpense(managerUserId, request);

      assertThat(response.id()).isNotNull();
      assertThat(response.expenseType()).isEqualTo(type);
      assertThat(response.title()).isEqualTo("Chi phi " + type.name());
      assertThat(response.amount()).isEqualByComparingTo("1500000");
      assertThat(response.amount().scale()).isZero();
      assertThat(response.expenseDate()).isEqualTo(LocalDate.of(2026, 10, 15));
      assertThat(response.invoiceCode()).isNull();
      assertThat(response.notes()).isEqualTo("Ghi chu chi tiet");
      assertThat(response.recordedByStaffId()).isEqualTo(managerStaffId);
      assertThat(response.recordedByStaffName()).isEqualTo("Nguyen Quan Ly");
      assertThat(response.receipt()).isNull();
    }

    // 14-digit boundary and blank notes
    CreateFacilityExpenseRequest maxBoundary =
        new CreateFacilityExpenseRequest(
            ExpenseType.OTHER,
            "Chi phi lon",
            new BigDecimal("99999999999999"),
            LocalDate.of(2026, 11, 1),
            "  HD-001  ",
            "   ");
    FacilityExpenseResponse boundaryResponse = service.createExpense(managerUserId, maxBoundary);
    assertThat(boundaryResponse.amount()).isEqualByComparingTo("99999999999999");
    assertThat(boundaryResponse.invoiceCode()).isEqualTo("HD-001");
    assertThat(boundaryResponse.notes()).isNull();
  }

  @Test
  @DisplayName(
      "createExpense & updateExpense: invalid amounts (null, 0, negative, decimal, >14 digits, large exponent) throw 400 EXPENSE_400_AMOUNT")
  void invalidAmounts_throwBadRequest() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    FacilityExpense existing = buildExpense(ExpenseType.ELECTRICITY, null);
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.of(existing));

    List<BigDecimal> badAmounts =
        java.util.Arrays.asList(
            null,
            BigDecimal.ZERO,
            new BigDecimal("0.00"),
            new BigDecimal("-100"),
            new BigDecimal("100.50"),
            new BigDecimal("100000000000000"),
            new BigDecimal("1E+100"));

    for (BigDecimal badAmount : badAmounts) {
      CreateFacilityExpenseRequest createReq =
          new CreateFacilityExpenseRequest(
              ExpenseType.ELECTRICITY,
              "Tien dien",
              badAmount,
              LocalDate.of(2026, 10, 10),
              null,
              null);
      assertThatThrownBy(() -> service.createExpense(managerUserId, createReq))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.INVALID_EXPENSE_AMOUNT);

      UpdateFacilityExpenseRequest updateReq =
          new UpdateFacilityExpenseRequest(
              ExpenseType.ELECTRICITY,
              "Tien dien",
              badAmount,
              LocalDate.of(2026, 10, 10),
              null,
              null);
      assertThatThrownBy(() -> service.updateExpense(expenseId, updateReq))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.INVALID_EXPENSE_AMOUNT);
    }

    verify(facilityExpenseRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "createExpense: missing StaffProfile, null user ID, or non-MANAGER staff throws 403 EXPENSE_403_STAFF_PROFILE")
  void createExpense_invalidManagerStaff_throwsForbidden() {
    CreateFacilityExpenseRequest request =
        new CreateFacilityExpenseRequest(
            ExpenseType.WATER,
            "Tien nuoc",
            new BigDecimal("500000"),
            LocalDate.of(2026, 10, 10),
            null,
            null);

    assertThatThrownBy(() -> service.createExpense(null, request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.createExpense(managerUserId, request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    UUID techUserId = UUID.randomUUID();
    StaffProfile techStaff =
        StaffProfile.builder()
            .id(UUID.randomUUID())
            .user(User.builder().id(techUserId).role(UserRole.TECHNICIAN).build())
            .fullName("Le Ky Thuat")
            .phone("0909888777")
            .build();
    when(staffProfileRepository.findByUserId(techUserId)).thenReturn(Optional.of(techStaff));
    assertThatThrownBy(() -> service.createExpense(techUserId, request))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    verify(facilityExpenseRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "SALARY uniqueness: duplicate in same month (pre-check and DB constraint fallback) throws 409 EXPENSE_409_SALARY_MONTH; different months allowed")
  void salaryUniqueness_enforcedAcrossCreateAndUpdate() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));

    // 1. Pre-check conflict on create
    when(facilityExpenseRepository.existsByExpenseTypeAndExpenseDateBetween(
            ExpenseType.SALARY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        .thenReturn(true);
    CreateFacilityExpenseRequest octSalary =
        new CreateFacilityExpenseRequest(
            ExpenseType.SALARY,
            "Luong thang 10",
            new BigDecimal("35000000"),
            LocalDate.of(2026, 10, 28),
            null,
            null);
    assertThatThrownBy(() -> service.createExpense(managerUserId, octSalary))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);

    // 2. Concurrent race fallback on create via DataIntegrityViolationException
    when(facilityExpenseRepository.existsByExpenseTypeAndExpenseDateBetween(
            ExpenseType.SALARY, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        .thenReturn(false);
    DataIntegrityViolationException salaryConstraintEx =
        new DataIntegrityViolationException(
            "duplicate",
            new ConstraintViolationException(
                "unique", new SQLException("dup", "23505"), "uq_facility_expenses_salary_month"));
    when(facilityExpenseRepository.saveAndFlush(any(FacilityExpense.class)))
        .thenThrow(salaryConstraintEx);

    CreateFacilityExpenseRequest novSalary =
        new CreateFacilityExpenseRequest(
            ExpenseType.SALARY,
            "Luong thang 11",
            new BigDecimal("35000000"),
            LocalDate.of(2026, 11, 15),
            null,
            null);
    assertThatThrownBy(() -> service.createExpense(managerUserId, novSalary))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);

    // 3. Different month (e.g. leap-year Feb 2028) allowed
    when(facilityExpenseRepository.existsByExpenseTypeAndExpenseDateBetween(
            ExpenseType.SALARY, LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)))
        .thenReturn(false);
    when(facilityExpenseRepository.saveAndFlush(any(FacilityExpense.class)))
        .thenAnswer(
            inv -> {
              FacilityExpense e = inv.getArgument(0);
              e.setId(UUID.randomUUID());
              return e;
            });
    CreateFacilityExpenseRequest febSalary =
        new CreateFacilityExpenseRequest(
            ExpenseType.SALARY,
            "Luong thang 02/2028",
            new BigDecimal("36000000"),
            LocalDate.of(2028, 2, 20),
            null,
            null);
    FacilityExpenseResponse febCreated = service.createExpense(managerUserId, febSalary);
    assertThat(febCreated.expenseType()).isEqualTo(ExpenseType.SALARY);
    assertThat(febCreated.expenseDate()).isEqualTo(LocalDate.of(2028, 2, 20));

    // 4. Duplicate SALARY in target month on update (pre-check and DB constraint fallback)
    FacilityExpense existing = buildExpense(ExpenseType.ELECTRICITY, "expenses/1/receipt/a.pdf");
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.of(existing));
    when(facilityExpenseRepository.existsByExpenseTypeAndExpenseDateBetweenAndIdNot(
            ExpenseType.SALARY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), expenseId))
        .thenReturn(true);

    UpdateFacilityExpenseRequest moveToOctSalary =
        new UpdateFacilityExpenseRequest(
            ExpenseType.SALARY,
            "Luong thang 10",
            new BigDecimal("30000000"),
            LocalDate.of(2026, 10, 5),
            null,
            null);
    assertThatThrownBy(() -> service.updateExpense(expenseId, moveToOctSalary))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);
  }

  @Test
  @DisplayName(
      "updateExpense: updates fields while preserving recordedByStaff and receipt; missing ID throws 404 EXPENSE_404")
  void updateExpense_preservesImmutableFieldsAndHandlesNotFound() {
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.empty());
    UpdateFacilityExpenseRequest req =
        new UpdateFacilityExpenseRequest(
            ExpenseType.INTERNET,
            "  Internet VNPT  ",
            new BigDecimal("850000"),
            LocalDate.of(2026, 10, 20),
            "  INV-NET-10  ",
            "   ");
    assertThatThrownBy(() -> service.updateExpense(expenseId, req))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.EXPENSE_NOT_FOUND);

    String existingKey = "expenses/" + expenseId + "/receipt/original.pdf";
    FacilityExpense existing = buildExpense(ExpenseType.ELECTRICITY, existingKey);
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.of(existing));
    when(facilityExpenseRepository.saveAndFlush(any(FacilityExpense.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    FacilityExpenseResponse updated = service.updateExpense(expenseId, req);
    assertThat(updated.expenseType()).isEqualTo(ExpenseType.INTERNET);
    assertThat(updated.title()).isEqualTo("Internet VNPT");
    assertThat(updated.amount()).isEqualByComparingTo("850000");
    assertThat(updated.expenseDate()).isEqualTo(LocalDate.of(2026, 10, 20));
    assertThat(updated.invoiceCode()).isEqualTo("INV-NET-10");
    assertThat(updated.notes()).isNull();
    assertThat(updated.recordedByStaffId()).isEqualTo(managerStaffId);
    assertThat(updated.receipt()).isNotNull();
    assertThat(updated.receipt().available()).isTrue();
    assertThat(updated.receipt().contentUrl())
        .isEqualTo("/api/v1/admin/expenses/" + expenseId + "/receipt/content");
    assertThat(existing.getInvoiceFileKey()).isEqualTo(existingKey);
  }

  @Test
  @DisplayName(
      "getExpenses & getExpenseById: validates period regex, applies default sort/size, trims search, and handles 404")
  void getExpensesAndGetById_validatesPeriodAndAppliesDefaults() {
    for (String badPeriod :
        List.of("2026-13", "2026-00", "2026-1", "2026/10", "2026-10-01", "abc")) {
      assertThatThrownBy(() -> service.getExpenses(badPeriod, null, null, Pageable.unpaged()))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.INVALID_REPORT_PERIOD);
    }

    FacilityExpense entity = buildExpense(ExpenseType.WATER, null);
    Page<FacilityExpense> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
    when(facilityExpenseRepository.findExpensesWithFilters(
            eq(LocalDate.of(2026, 10, 1)),
            eq(LocalDate.of(2026, 10, 31)),
            eq(ExpenseType.WATER),
            eq("sawaco"),
            any(Pageable.class)))
        .thenReturn(page);

    PageResponse<FacilityExpenseResponse> result =
        service.getExpenses("2026-10", ExpenseType.WATER, "  sawaco  ", null);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().getFirst().id()).isEqualTo(expenseId);
    assertThat(result.getTotalElements()).isEqualTo(1);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(facilityExpenseRepository)
        .findExpensesWithFilters(
            eq(LocalDate.of(2026, 10, 1)),
            eq(LocalDate.of(2026, 10, 31)),
            eq(ExpenseType.WATER),
            eq("sawaco"),
            pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    assertThat(pageableCaptor.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt")));

    // Blank period and blank search -> nulls
    when(facilityExpenseRepository.findExpensesWithFilters(
            eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
        .thenReturn(Page.empty());
    service.getExpenses("   ", null, "  ", PageRequest.of(1, 10));
    verify(facilityExpenseRepository)
        .findExpensesWithFilters(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(
                PageRequest.of(
                    1, 10, Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt")))));

    // getExpenseById
    when(facilityExpenseRepository.findById(expenseId)).thenReturn(Optional.of(entity));
    assertThat(service.getExpenseById(expenseId).id()).isEqualTo(expenseId);

    UUID missingId = UUID.randomUUID();
    when(facilityExpenseRepository.findById(missingId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getExpenseById(missingId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.EXPENSE_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "uploadReceipt: validates required, <=5MB boundary, magic bytes (JPG/JPEG/PNG/WEBP/PDF), rejects spoofed files, and locks entity before S3 upload (MINOR-3)")
  void uploadReceipt_validatesFormatsSizeAndChecksEntityBeforeS3() {
    // 1. Missing / empty -> 400 RECEIPT_FILE_REQUIRED
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
    assertThatThrownBy(() -> service.uploadReceipt(expenseId, null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.RECEIPT_FILE_REQUIRED);
    assertThatThrownBy(() -> service.uploadReceipt(expenseId, emptyFile))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.RECEIPT_FILE_REQUIRED);

    // 2. > 5MB -> 400 RECEIPT_FILE_SIZE_EXCEEDED
    byte[] tooLarge = new byte[(5 * 1024 * 1024) + 1];
    System.arraycopy(VALID_PDF_BYTES, 0, tooLarge, 0, VALID_PDF_BYTES.length);
    MockMultipartFile oversized =
        new MockMultipartFile("file", "big.pdf", "application/pdf", tooLarge);
    assertThatThrownBy(() -> service.uploadReceipt(expenseId, oversized))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.RECEIPT_FILE_SIZE_EXCEEDED);

    // 3. Spoofed magic bytes / unsupported extension / MIME mismatch -> 400
    // UNSUPPORTED_RECEIPT_FORMAT
    List<MockMultipartFile> invalidFiles =
        List.of(
            new MockMultipartFile("file", "spoof.pdf", "application/pdf", VALID_PNG_BYTES),
            new MockMultipartFile("file", "spoof.png", "image/png", "not-a-png".getBytes()),
            new MockMultipartFile("file", "spoof.jpg", "image/jpeg", VALID_PDF_BYTES),
            new MockMultipartFile("file", "spoof.webp", "image/webp", "RIFF1234ABCD".getBytes()),
            new MockMultipartFile(
                "file", "script.exe", "application/octet-stream", VALID_PDF_BYTES),
            new MockMultipartFile("file", "noextension", "image/png", VALID_PNG_BYTES),
            new MockMultipartFile("file", "wrongmime.pdf", "image/png", VALID_PDF_BYTES));
    for (MockMultipartFile badFile : invalidFiles) {
      assertThatThrownBy(() -> service.uploadReceipt(expenseId, badFile))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.UNSUPPORTED_RECEIPT_FORMAT);
    }
    verifyNoInteractions(privateObjectStorage);

    // 4. Missing expense must fail BEFORE any S3 putObject (MINOR-3)
    MockMultipartFile validPdf =
        new MockMultipartFile("file", "receipt.pdf", "application/pdf", VALID_PDF_BYTES);
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.uploadReceipt(expenseId, validPdf))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.EXPENSE_NOT_FOUND);
    verifyNoInteractions(privateObjectStorage);

    // 5. All 5 valid formats (including 5MB boundary) succeed
    byte[] exactFiveMbPdf = new byte[5 * 1024 * 1024];
    System.arraycopy(VALID_PDF_BYTES, 0, exactFiveMbPdf, 0, VALID_PDF_BYTES.length);
    FacilityExpense entity = buildExpense(ExpenseType.ELECTRICITY, null);
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.of(entity));
    when(facilityExpenseRepository.saveAndFlush(any(FacilityExpense.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    List<MockMultipartFile> validFiles =
        List.of(
            new MockMultipartFile("file", "bill.jpg", "image/jpeg", VALID_JPEG_BYTES),
            new MockMultipartFile("file", "bill.jpeg", "image/pjpeg", VALID_JPEG_BYTES),
            new MockMultipartFile("file", "bill.png", "image/png", VALID_PNG_BYTES),
            new MockMultipartFile(
                "file", "bill.webp", "application/octet-stream", VALID_WEBP_BYTES),
            new MockMultipartFile("file", "bill.pdf", "application/pdf", exactFiveMbPdf));
    List<String> expectedMimes =
        List.of("image/jpeg", "image/jpeg", "image/png", "image/webp", "application/pdf");
    List<String> expectedExts = List.of(".jpg", ".jpeg", ".png", ".webp", ".pdf");

    for (int i = 0; i < validFiles.size(); i++) {
      entity.setInvoiceFileKey(null);
      MockMultipartFile f = validFiles.get(i);
      FacilityExpenseResponse res = service.uploadReceipt(expenseId, f);

      assertThat(entity.getInvoiceFileKey())
          .startsWith("expenses/" + expenseId + "/receipt/")
          .endsWith(expectedExts.get(i));
      assertThat(res.receipt()).isNotNull();
      assertThat(res.receipt().available()).isTrue();
      assertThat(res.receipt().contentUrl())
          .isEqualTo("/api/v1/admin/expenses/" + expenseId + "/receipt/content")
          .doesNotContain(entity.getInvoiceFileKey());

      verify(privateObjectStorage)
          .putObject(
              eq(entity.getInvoiceFileKey()),
              eq(expectedMimes.get(i)),
              any(InputStream.class),
              eq(f.getSize()));
    }
  }

  @Test
  @DisplayName(
      "uploadReceipt, deleteReceipt, deleteExpense: transaction sync deletes old key afterCommit and cleans uploaded key on rollback")
  void s3LifecycleHooks_cleanUpOnCommitAndRollback() {
    String oldKey = "expenses/" + expenseId + "/receipt/old.jpg";
    FacilityExpense entity = buildExpense(ExpenseType.OTHER, oldKey);
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.of(entity));
    lenient()
        .when(facilityExpenseRepository.saveAndFlush(any(FacilityExpense.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Rollback on uploadReceipt deletes newly uploaded key and leaves oldKey untouched
    TransactionSynchronizationManager.initSynchronization();
    MockMultipartFile png = new MockMultipartFile("file", "new.png", "image/png", VALID_PNG_BYTES);
    service.uploadReceipt(expenseId, png);
    String newKey = entity.getInvoiceFileKey();
    assertThat(newKey).isNotEqualTo(oldKey);
    verify(privateObjectStorage, never()).deleteObject(any());

    for (TransactionSynchronization sync :
        TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }
    verify(privateObjectStorage).deleteObject(newKey);
    verify(privateObjectStorage, never()).deleteObject(oldKey);
    TransactionSynchronizationManager.clearSynchronization();

    // Commit on replace upload deletes oldKey on afterCommit
    entity.setInvoiceFileKey(oldKey);
    TransactionSynchronizationManager.initSynchronization();
    service.uploadReceipt(expenseId, png);
    for (TransactionSynchronization sync :
        TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
      sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    }
    verify(privateObjectStorage).deleteObject(oldKey);
    TransactionSynchronizationManager.clearSynchronization();

    // deleteReceipt & deleteExpense also delete after commit
    String receiptForDelete = "expenses/" + expenseId + "/receipt/del.pdf";
    entity.setInvoiceFileKey(receiptForDelete);
    TransactionSynchronizationManager.initSynchronization();
    FacilityExpenseResponse afterDeleteReceipt = service.deleteReceipt(expenseId);
    assertThat(afterDeleteReceipt.receipt()).isNull();
    verify(privateObjectStorage, never()).deleteObject(receiptForDelete);
    for (TransactionSynchronization sync :
        TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
    }
    verify(privateObjectStorage).deleteObject(receiptForDelete);
    TransactionSynchronizationManager.clearSynchronization();

    String expenseKey = "expenses/" + expenseId + "/receipt/expdel.webp";
    entity.setInvoiceFileKey(expenseKey);
    TransactionSynchronizationManager.initSynchronization();
    service.deleteExpense(expenseId);
    verify(facilityExpenseRepository).delete(entity);
    verify(facilityExpenseRepository).flush();
    verify(privateObjectStorage, never()).deleteObject(expenseKey);
    for (TransactionSynchronization sync :
        TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
    }
    verify(privateObjectStorage).deleteObject(expenseKey);
  }

  @Test
  @DisplayName(
      "deleteReceipt, deleteExpense, loadReceiptContent: handle 404 expense/receipt and synthetic filename")
  void deleteAndLoadReceipt_errorsAndSyntheticFilename() {
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.deleteReceipt(expenseId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.EXPENSE_NOT_FOUND);
    assertThatThrownBy(() -> service.deleteExpense(expenseId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.EXPENSE_NOT_FOUND);

    FacilityExpense noReceipt = buildExpense(ExpenseType.WATER, null);
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.of(noReceipt));
    when(facilityExpenseRepository.findById(expenseId)).thenReturn(Optional.of(noReceipt));

    assertThatThrownBy(() -> service.deleteReceipt(expenseId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.EXPENSE_RECEIPT_NOT_FOUND);
    assertThatThrownBy(() -> service.loadReceiptContent(expenseId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.EXPENSE_RECEIPT_NOT_FOUND);

    String missingInS3 = "expenses/" + expenseId + "/receipt/missing.pdf";
    FacilityExpense withMissingS3 = buildExpense(ExpenseType.WATER, missingInS3);
    when(facilityExpenseRepository.findById(expenseId)).thenReturn(Optional.of(withMissingS3));
    doThrow(new ObjectStorageNotFoundException("gone"))
        .when(privateObjectStorage)
        .getObject(missingInS3);

    assertThatThrownBy(() -> service.loadReceiptContent(expenseId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.EXPENSE_RECEIPT_NOT_FOUND);

    String pdfKey = "expenses/" + expenseId + "/receipt/invoice-2026.pdf";
    FacilityExpense withPdf = buildExpense(ExpenseType.ELECTRICITY, pdfKey);
    when(facilityExpenseRepository.findById(expenseId)).thenReturn(Optional.of(withPdf));
    when(privateObjectStorage.getObject(pdfKey))
        .thenReturn(
            new StoredObjectContent(
                new ByteArrayResource(VALID_PDF_BYTES), "application/pdf", VALID_PDF_BYTES.length));

    FacilityExpenseReceiptContent content = service.loadReceiptContent(expenseId);
    assertThat(content.contentType()).isEqualTo("application/pdf");
    assertThat(content.contentLength()).isEqualTo(VALID_PDF_BYTES.length);
    assertThat(content.filename()).isEqualTo("expense-" + expenseId + "-receipt.pdf");
  }

  @Test
  @DisplayName(
      "DTO Bean Validation: enforces NotNull/NotBlank/Size while delegating amount bounds to service (MINOR-1)")
  void dtoValidation_doesNotConstrainAmountValues() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      CreateFacilityExpenseRequest zeroAmountReq =
          new CreateFacilityExpenseRequest(
              ExpenseType.OTHER, "Title", BigDecimal.ZERO, LocalDate.of(2026, 10, 1), null, null);
      CreateFacilityExpenseRequest decimalAmountReq =
          new CreateFacilityExpenseRequest(
              ExpenseType.OTHER,
              "Title",
              new BigDecimal("100.50"),
              LocalDate.of(2026, 10, 1),
              null,
              null);
      UpdateFacilityExpenseRequest negativeAmountReq =
          new UpdateFacilityExpenseRequest(
              ExpenseType.OTHER,
              "Title",
              new BigDecimal("-500"),
              LocalDate.of(2026, 10, 1),
              null,
              null);

      assertThat(validator.validate(zeroAmountReq)).isEmpty();
      assertThat(validator.validate(decimalAmountReq)).isEmpty();
      assertThat(validator.validate(negativeAmountReq)).isEmpty();

      CreateFacilityExpenseRequest blankTitleReq =
          new CreateFacilityExpenseRequest(
              ExpenseType.OTHER,
              "   ",
              new BigDecimal("1000"),
              LocalDate.of(2026, 10, 1),
              null,
              null);
      Set<ConstraintViolation<CreateFacilityExpenseRequest>> titleViolations =
          validator.validate(blankTitleReq);
      assertThat(titleViolations).isNotEmpty();
    }
  }

  @Test
  @DisplayName("uploadReceipt: IOException reading stream wraps safely and cleans uploaded keys")
  void uploadReceipt_ioExceptionReadingStream_throwsInternalServerError() throws Exception {
    FacilityExpense entity = buildExpense(ExpenseType.OTHER, null);
    when(facilityExpenseRepository.findByIdForUpdate(expenseId)).thenReturn(Optional.of(entity));

    MultipartFile failingFile = org.mockito.Mockito.mock(MultipartFile.class);
    when(failingFile.isEmpty()).thenReturn(false);
    when(failingFile.getSize()).thenReturn(128L);
    when(failingFile.getOriginalFilename()).thenReturn("bill.png");
    when(failingFile.getContentType()).thenReturn("image/png");
    when(failingFile.getInputStream())
        .thenReturn(new java.io.ByteArrayInputStream(VALID_PNG_BYTES))
        .thenThrow(new IOException("disk read error"));

    assertThatThrownBy(() -> service.uploadReceipt(expenseId, failingFile))
        .isInstanceOf(BaseAppException.class)
        .hasMessageContaining("Không thể đọc dữ liệu tệp chứng từ");
    verify(privateObjectStorage, never()).putObject(any(), any(), any(), anyLong());
  }

  private FacilityExpense buildExpense(ExpenseType type, String invoiceFileKey) {
    return FacilityExpense.builder()
        .id(expenseId)
        .recordedByStaff(managerStaff)
        .expenseType(type)
        .title("Tien dien KTX")
        .amount(new BigDecimal("2500000"))
        .expenseDate(LocalDate.of(2026, 10, 15))
        .invoiceCode("EVN-2026-10")
        .invoiceFileKey(invoiceFileKey)
        .notes("Hoa don dien thang 10")
        .createdAt(Instant.parse("2026-10-15T01:00:00Z"))
        .updatedAt(Instant.parse("2026-10-15T01:00:00Z"))
        .build();
  }
}
