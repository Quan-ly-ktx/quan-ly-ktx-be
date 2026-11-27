package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.expense.CreateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.expense.UpdateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseReceiptContent;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseReceiptResponse;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.entities.FacilityExpense;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.DataIntegrityViolationClassifier;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.exceptions.error.SystemErrorCode;
import com.uit.dormmanagement.repositories.FacilityExpenseRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.services.FacilityExpenseService;
import com.uit.dormmanagement.services.PrivateObjectStorage;
import com.uit.dormmanagement.services.StoredObjectContent;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityExpenseServiceImpl implements FacilityExpenseService {

  private static final long MAX_RECEIPT_FILE_SIZE = 5L * 1024 * 1024;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_AMOUNT_PRECISION = 14;
  private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
  private static final Sort DEFAULT_SORT =
      Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"));
  private static final String SALARY_MONTH_UNIQUE_CONSTRAINT = "uq_facility_expenses_salary_month";

  private static final Map<String, String> EXTENSION_TO_MIME =
      Map.of(
          "jpg", MediaType.IMAGE_JPEG_VALUE,
          "jpeg", MediaType.IMAGE_JPEG_VALUE,
          "png", MediaType.IMAGE_PNG_VALUE,
          "webp", "image/webp",
          "pdf", MediaType.APPLICATION_PDF_VALUE);

  private static final Map<String, Set<String>> EXTENSION_TO_ALLOWED_DECLARED_MIMES =
      Map.of(
          "jpg", Set.of("image/jpeg", "image/pjpeg"),
          "jpeg", Set.of("image/jpeg", "image/pjpeg"),
          "png", Set.of("image/png"),
          "webp", Set.of("image/webp"),
          "pdf", Set.of("application/pdf", "application/x-pdf"));

  private final FacilityExpenseRepository facilityExpenseRepository;
  private final StaffProfileRepository staffProfileRepository;
  private final PrivateObjectStorage privateObjectStorage;

  public static String receiptContentPath(UUID expenseId) {
    return "/api/v1/admin/expenses/" + expenseId + "/receipt/content";
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<FacilityExpenseResponse> getExpenses(
      String period, ExpenseType expenseType, String search, Pageable pageable) {
    LocalDate startDate = null;
    LocalDate endDate = null;
    if (period != null && !period.isBlank()) {
      if (!PERIOD_PATTERN.matcher(period).matches()) {
        throw new BaseAppException(ExpenseErrorCode.INVALID_REPORT_PERIOD);
      }
      YearMonth yearMonth = YearMonth.parse(period);
      startDate = yearMonth.atDay(1);
      endDate = yearMonth.atEndOfMonth();
    }

    Pageable effectivePageable = resolvePageable(pageable);
    String trimmedSearch = normalizeOptionalText(search);

    Page<FacilityExpenseResponse> page =
        facilityExpenseRepository
            .findExpensesWithFilters(
                startDate, endDate, expenseType, trimmedSearch, effectivePageable)
            .map(FacilityExpenseServiceImpl::toResponse);
    return PageResponse.from(page);
  }

  @Override
  @Transactional(readOnly = true)
  public FacilityExpenseResponse getExpenseById(UUID id) {
    if (id == null) {
      throw new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND);
    }
    FacilityExpense expense =
        facilityExpenseRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND));
    return toResponse(expense);
  }

  @Override
  @Transactional
  public FacilityExpenseResponse createExpense(
      UUID managerUserId, CreateFacilityExpenseRequest request) {
    StaffProfile staff = resolveManagerStaff(managerUserId);
    BigDecimal normalizedAmount = validateAndNormalizeAmount(request.amount());
    String trimmedTitle = request.title().trim();
    String trimmedInvoiceCode = normalizeOptionalText(request.invoiceCode());
    String trimmedNotes = normalizeOptionalText(request.notes());

    if (request.expenseType() == ExpenseType.SALARY) {
      LocalDate monthStart = request.expenseDate().withDayOfMonth(1);
      LocalDate monthEnd =
          request.expenseDate().withDayOfMonth(request.expenseDate().lengthOfMonth());
      if (facilityExpenseRepository.existsByExpenseTypeAndExpenseDateBetween(
          ExpenseType.SALARY, monthStart, monthEnd)) {
        throw new BaseAppException(ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);
      }
    }

    FacilityExpense expense =
        FacilityExpense.builder()
            .recordedByStaff(staff)
            .expenseType(request.expenseType())
            .title(trimmedTitle)
            .amount(normalizedAmount)
            .expenseDate(request.expenseDate())
            .invoiceCode(trimmedInvoiceCode)
            .notes(trimmedNotes)
            .build();

    FacilityExpense saved = saveAndTranslateSalaryConflict(expense);
    log.info(
        "Created facility expense {} ({}) on {}",
        saved.getId(),
        saved.getExpenseType(),
        saved.getExpenseDate());
    return toResponse(saved);
  }

  @Override
  @Transactional
  public FacilityExpenseResponse updateExpense(UUID id, UpdateFacilityExpenseRequest request) {
    if (id == null) {
      throw new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND);
    }
    FacilityExpense expense =
        facilityExpenseRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND));

    BigDecimal normalizedAmount = validateAndNormalizeAmount(request.amount());
    String trimmedTitle = request.title().trim();
    String trimmedInvoiceCode = normalizeOptionalText(request.invoiceCode());
    String trimmedNotes = normalizeOptionalText(request.notes());

    if (request.expenseType() == ExpenseType.SALARY) {
      LocalDate monthStart = request.expenseDate().withDayOfMonth(1);
      LocalDate monthEnd =
          request.expenseDate().withDayOfMonth(request.expenseDate().lengthOfMonth());
      if (facilityExpenseRepository.existsByExpenseTypeAndExpenseDateBetweenAndIdNot(
          ExpenseType.SALARY, monthStart, monthEnd, id)) {
        throw new BaseAppException(ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);
      }
    }

    expense.setExpenseType(request.expenseType());
    expense.setTitle(trimmedTitle);
    expense.setAmount(normalizedAmount);
    expense.setExpenseDate(request.expenseDate());
    expense.setInvoiceCode(trimmedInvoiceCode);
    expense.setNotes(trimmedNotes);

    FacilityExpense saved = saveAndTranslateSalaryConflict(expense);
    log.info(
        "Updated facility expense {} ({}) on {}",
        saved.getId(),
        saved.getExpenseType(),
        saved.getExpenseDate());
    return toResponse(saved);
  }

  @Override
  @Transactional
  public void deleteExpense(UUID id) {
    if (id == null) {
      throw new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND);
    }
    FacilityExpense expense =
        facilityExpenseRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND));

    String oldKey = expense.getInvoiceFileKey();
    facilityExpenseRepository.delete(expense);
    facilityExpenseRepository.flush();
    schedulePostCommitDelete(oldKey);
    log.info("Deleted facility expense {}", id);
  }

  @Override
  @Transactional
  public FacilityExpenseResponse uploadReceipt(UUID id, MultipartFile file) {
    ValidatedReceiptFile validated = validateReceiptFile(file);

    if (id == null) {
      throw new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND);
    }
    FacilityExpense expense =
        facilityExpenseRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND));

    String objectKey =
        "expenses/" + id + "/receipt/" + UUID.randomUUID() + "." + validated.extension();
    List<String> uploadedKeys = new ArrayList<>(1);
    registerRollbackCleanup(uploadedKeys);

    try {
      try (InputStream is = file.getInputStream()) {
        privateObjectStorage.putObject(
            objectKey, validated.canonicalMimeType(), is, file.getSize());
      }
      uploadedKeys.add(objectKey);

      String previousKey = expense.getInvoiceFileKey();
      expense.setInvoiceFileKey(objectKey);

      FacilityExpense saved = facilityExpenseRepository.saveAndFlush(expense);
      if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(objectKey)) {
        schedulePostCommitDelete(previousKey);
      }

      log.info("Uploaded receipt for facility expense {}", saved.getId());
      return toResponse(saved);
    } catch (RuntimeException | IOException e) {
      cleanupUploadedKeys(uploadedKeys);
      if (e instanceof BaseAppException bae) {
        throw bae;
      }
      if (e instanceof RuntimeException re) {
        throw re;
      }
      throw new BaseAppException(
          SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu tệp chứng từ");
    }
  }

  @Override
  @Transactional
  public FacilityExpenseResponse deleteReceipt(UUID id) {
    if (id == null) {
      throw new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND);
    }
    FacilityExpense expense =
        facilityExpenseRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND));

    String oldKey = expense.getInvoiceFileKey();
    if (oldKey == null || oldKey.isBlank()) {
      throw new BaseAppException(ExpenseErrorCode.EXPENSE_RECEIPT_NOT_FOUND);
    }

    expense.setInvoiceFileKey(null);
    FacilityExpense saved = facilityExpenseRepository.saveAndFlush(expense);
    schedulePostCommitDelete(oldKey);

    log.info("Deleted receipt for facility expense {}", saved.getId());
    return toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public FacilityExpenseReceiptContent loadReceiptContent(UUID id) {
    if (id == null) {
      throw new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND);
    }
    FacilityExpense expense =
        facilityExpenseRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND));

    String receiptKey = expense.getInvoiceFileKey();
    if (receiptKey == null || receiptKey.isBlank()) {
      throw new BaseAppException(ExpenseErrorCode.EXPENSE_RECEIPT_NOT_FOUND);
    }

    StoredObjectContent stored;
    try {
      stored = privateObjectStorage.getObject(receiptKey);
    } catch (ObjectStorageNotFoundException | IllegalArgumentException e) {
      throw new BaseAppException(ExpenseErrorCode.EXPENSE_RECEIPT_NOT_FOUND);
    }

    String contentType = resolveSafeReceiptContentType(stored.contentType(), receiptKey);
    String extension = resolveSafeReceiptExtension(receiptKey, contentType);
    String filename = "expense-" + id + "-receipt." + extension;

    return FacilityExpenseReceiptContent.builder()
        .resource(stored.resource())
        .contentType(contentType)
        .contentLength(stored.contentLength())
        .filename(filename)
        .build();
  }

  private StaffProfile resolveManagerStaff(UUID managerUserId) {
    if (managerUserId == null) {
      throw new BaseAppException(ExpenseErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);
    }
    StaffProfile staff =
        staffProfileRepository
            .findByUserId(managerUserId)
            .orElseThrow(
                () -> new BaseAppException(ExpenseErrorCode.MANAGER_STAFF_PROFILE_REQUIRED));
    if (staff.getUser() != null && staff.getUser().getRole() != UserRole.MANAGER) {
      throw new BaseAppException(ExpenseErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);
    }
    return staff;
  }

  private BigDecimal validateAndNormalizeAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BaseAppException(ExpenseErrorCode.INVALID_EXPENSE_AMOUNT);
    }
    BigDecimal stripped = amount.stripTrailingZeros();
    long integerDigits = (long) stripped.precision() - (long) stripped.scale();
    if (stripped.scale() > 0 || integerDigits > MAX_AMOUNT_PRECISION) {
      throw new BaseAppException(ExpenseErrorCode.INVALID_EXPENSE_AMOUNT);
    }
    return amount.setScale(0, RoundingMode.UNNECESSARY);
  }

  private FacilityExpense saveAndTranslateSalaryConflict(FacilityExpense expense) {
    try {
      return facilityExpenseRepository.saveAndFlush(expense);
    } catch (DataIntegrityViolationException dive) {
      if (DataIntegrityViolationClassifier.matchesUniqueConstraint(
          dive, SALARY_MONTH_UNIQUE_CONSTRAINT)) {
        throw new BaseAppException(ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS);
      }
      throw dive;
    }
  }

  private Pageable resolvePageable(Pageable pageable) {
    if (pageable == null || pageable.isUnpaged()) {
      Sort sort =
          pageable != null && pageable.getSort().isSorted() ? pageable.getSort() : DEFAULT_SORT;
      return PageRequest.of(0, DEFAULT_PAGE_SIZE, sort);
    }
    if (pageable.getSort().isUnsorted()) {
      return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
    }
    return pageable;
  }

  private static String normalizeOptionalText(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private record ValidatedReceiptFile(String extension, String canonicalMimeType) {}

  private ValidatedReceiptFile validateReceiptFile(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() <= 0) {
      throw new BaseAppException(ExpenseErrorCode.RECEIPT_FILE_REQUIRED);
    }
    if (file.getSize() > MAX_RECEIPT_FILE_SIZE) {
      throw new BaseAppException(ExpenseErrorCode.RECEIPT_FILE_SIZE_EXCEEDED);
    }

    String filename = file.getOriginalFilename();
    if (filename == null || filename.isBlank()) {
      throw new BaseAppException(ExpenseErrorCode.UNSUPPORTED_RECEIPT_FORMAT);
    }

    String normalizedSlashes = filename.replace('\\', '/');
    int lastSlash = normalizedSlashes.lastIndexOf('/');
    String nameOnly =
        lastSlash >= 0 ? normalizedSlashes.substring(lastSlash + 1) : normalizedSlashes;
    int dotIndex = nameOnly.lastIndexOf('.');
    if (dotIndex <= 0
        || dotIndex == nameOnly.length() - 1
        || nameOnly.substring(0, dotIndex).isBlank()) {
      throw new BaseAppException(ExpenseErrorCode.UNSUPPORTED_RECEIPT_FORMAT);
    }

    String extension = nameOnly.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    String canonicalMime = EXTENSION_TO_MIME.get(extension);
    if (canonicalMime == null) {
      throw new BaseAppException(ExpenseErrorCode.UNSUPPORTED_RECEIPT_FORMAT);
    }

    String rawContentType = file.getContentType();
    if (rawContentType != null && !rawContentType.isBlank()) {
      int semicolonIndex = rawContentType.indexOf(';');
      String baseContentType =
          (semicolonIndex >= 0 ? rawContentType.substring(0, semicolonIndex) : rawContentType)
              .trim()
              .toLowerCase(Locale.ROOT);
      if (!baseContentType.isEmpty()
          && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(baseContentType)) {
        Set<String> allowedDeclared = EXTENSION_TO_ALLOWED_DECLARED_MIMES.get(extension);
        if (allowedDeclared == null || !allowedDeclared.contains(baseContentType)) {
          throw new BaseAppException(ExpenseErrorCode.UNSUPPORTED_RECEIPT_FORMAT);
        }
      }
    }

    byte[] header = new byte[12];
    int bytesRead;
    try (InputStream is = file.getInputStream()) {
      bytesRead = is.readNBytes(header, 0, header.length);
    } catch (IOException e) {
      throw new BaseAppException(
          SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu tệp chứng từ");
    }
    if (!matchesMagicBytes(extension, header, bytesRead)) {
      throw new BaseAppException(ExpenseErrorCode.UNSUPPORTED_RECEIPT_FORMAT);
    }

    return new ValidatedReceiptFile(extension, canonicalMime);
  }

  private static boolean matchesMagicBytes(String extension, byte[] header, int bytesRead) {
    return switch (extension) {
      case "jpg", "jpeg" ->
          bytesRead >= 3
              && (header[0] & 0xFF) == 0xFF
              && (header[1] & 0xFF) == 0xD8
              && (header[2] & 0xFF) == 0xFF;
      case "png" ->
          bytesRead >= 8
              && (header[0] & 0xFF) == 0x89
              && header[1] == 0x50
              && header[2] == 0x4E
              && header[3] == 0x47
              && header[4] == 0x0D
              && header[5] == 0x0A
              && header[6] == 0x1A
              && header[7] == 0x0A;
      case "webp" ->
          bytesRead >= 12
              && header[0] == 'R'
              && header[1] == 'I'
              && header[2] == 'F'
              && header[3] == 'F'
              && header[8] == 'W'
              && header[9] == 'E'
              && header[10] == 'B'
              && header[11] == 'P';
      case "pdf" ->
          bytesRead >= 5
              && header[0] == '%'
              && header[1] == 'P'
              && header[2] == 'D'
              && header[3] == 'F'
              && header[4] == '-';
      default -> false;
    };
  }

  private static String resolveSafeReceiptContentType(String storedContentType, String receiptKey) {
    if (storedContentType != null && !storedContentType.isBlank()) {
      int semicolonIndex = storedContentType.indexOf(';');
      String base =
          (semicolonIndex >= 0 ? storedContentType.substring(0, semicolonIndex) : storedContentType)
              .trim()
              .toLowerCase(Locale.ROOT);
      if ("image/jpeg".equals(base) || "image/pjpeg".equals(base)) {
        return MediaType.IMAGE_JPEG_VALUE;
      }
      if ("image/png".equals(base)) {
        return MediaType.IMAGE_PNG_VALUE;
      }
      if ("image/webp".equals(base)) {
        return "image/webp";
      }
      if ("application/pdf".equals(base) || "application/x-pdf".equals(base)) {
        return MediaType.APPLICATION_PDF_VALUE;
      }
    }
    String lowerKey = receiptKey.toLowerCase(Locale.ROOT);
    if (lowerKey.endsWith(".pdf")) {
      return MediaType.APPLICATION_PDF_VALUE;
    }
    if (lowerKey.endsWith(".png")) {
      return MediaType.IMAGE_PNG_VALUE;
    }
    if (lowerKey.endsWith(".webp")) {
      return "image/webp";
    }
    return MediaType.IMAGE_JPEG_VALUE;
  }

  private static String resolveSafeReceiptExtension(String receiptKey, String contentType) {
    String lowerKey = receiptKey.toLowerCase(Locale.ROOT);
    if (lowerKey.endsWith(".pdf")) {
      return "pdf";
    }
    if (lowerKey.endsWith(".png")) {
      return "png";
    }
    if (lowerKey.endsWith(".webp")) {
      return "webp";
    }
    if (lowerKey.endsWith(".jpeg")) {
      return "jpeg";
    }
    if (lowerKey.endsWith(".jpg")) {
      return "jpg";
    }
    if (MediaType.APPLICATION_PDF_VALUE.equals(contentType)) {
      return "pdf";
    }
    if (MediaType.IMAGE_PNG_VALUE.equals(contentType)) {
      return "png";
    }
    if ("image/webp".equals(contentType)) {
      return "webp";
    }
    return "jpg";
  }

  private void registerRollbackCleanup(List<String> uploadedKeys) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              cleanupUploadedKeys(uploadedKeys);
            }
          }
        });
  }

  private void cleanupUploadedKeys(List<String> uploadedKeys) {
    for (String key : uploadedKeys) {
      deleteObjectBestEffort(key);
    }
    uploadedKeys.clear();
  }

  private void schedulePostCommitDelete(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      return;
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              deleteObjectBestEffort(storageKey);
            }
          });
    } else {
      deleteObjectBestEffort(storageKey);
    }
  }

  private void deleteObjectBestEffort(String storageKey) {
    try {
      privateObjectStorage.deleteObject(storageKey);
    } catch (Exception e) {
      log.warn("Best-effort expense receipt deletion failed: {}", e.getClass().getSimpleName());
    }
  }

  public static FacilityExpenseResponse toResponse(FacilityExpense expense) {
    StaffProfile staff = expense.getRecordedByStaff();
    String key = expense.getInvoiceFileKey();
    FacilityExpenseReceiptResponse receipt =
        (key != null && !key.isBlank())
            ? FacilityExpenseReceiptResponse.builder()
                .available(true)
                .contentUrl(receiptContentPath(expense.getId()))
                .build()
            : null;

    return FacilityExpenseResponse.builder()
        .id(expense.getId())
        .expenseType(expense.getExpenseType())
        .title(expense.getTitle())
        .amount(expense.getAmount())
        .expenseDate(expense.getExpenseDate())
        .invoiceCode(expense.getInvoiceCode())
        .notes(expense.getNotes())
        .recordedByStaffId(staff != null ? staff.getId() : null)
        .recordedByStaffName(staff != null ? staff.getFullName() : null)
        .receipt(receipt)
        .createdAt(expense.getCreatedAt())
        .updatedAt(expense.getUpdatedAt())
        .build();
  }
}
