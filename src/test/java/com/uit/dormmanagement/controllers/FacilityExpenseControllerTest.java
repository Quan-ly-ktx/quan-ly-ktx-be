package com.uit.dormmanagement.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uit.dormmanagement.dtos.request.expense.CreateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.expense.UpdateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseReceiptContent;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseReceiptResponse;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.FacilityExpenseService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class FacilityExpenseControllerTest {

  @Mock private FacilityExpenseService facilityExpenseService;

  @InjectMocks private AdminFacilityExpenseController controller;

  private MockMvc mockMvc;
  private final ResponseFactory responseFactory = new ResponseFactory();

  private UUID expenseId;
  private UUID managerUserId;
  private UUID staffId;
  private FacilityExpenseResponse sampleExpense;
  private FacilityExpenseResponse sampleExpenseWithReceipt;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(controller, "responseFactory", responseFactory);

    expenseId = UUID.randomUUID();
    managerUserId = UUID.randomUUID();
    staffId = UUID.randomUUID();

    UserPrincipal managerPrincipal =
        UserPrincipal.create(
            User.builder()
                .id(managerUserId)
                .email("manager.expense@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(
                principalResolverFor(managerPrincipal), new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    sampleExpense =
        FacilityExpenseResponse.builder()
            .id(expenseId)
            .expenseType(ExpenseType.ELECTRICITY)
            .title("Tiền điện EVN tháng 10/2026")
            .amount(new BigDecimal("2500000"))
            .expenseDate(LocalDate.of(2026, 10, 12))
            .invoiceCode("EVN-2026-10")
            .notes("Thanh toán định kỳ")
            .recordedByStaffId(staffId)
            .recordedByStaffName("Nguyen Quan Ly")
            .receipt(null)
            .createdAt(Instant.parse("2026-10-12T04:00:00Z"))
            .updatedAt(Instant.parse("2026-10-12T04:00:00Z"))
            .build();

    sampleExpenseWithReceipt =
        FacilityExpenseResponse.builder()
            .id(expenseId)
            .expenseType(ExpenseType.ELECTRICITY)
            .title("Tiền điện EVN tháng 10/2026")
            .amount(new BigDecimal("2500000"))
            .expenseDate(LocalDate.of(2026, 10, 12))
            .invoiceCode("EVN-2026-10")
            .notes("Thanh toán định kỳ")
            .recordedByStaffId(staffId)
            .recordedByStaffName("Nguyen Quan Ly")
            .receipt(
                FacilityExpenseReceiptResponse.builder()
                    .available(true)
                    .contentUrl("/api/v1/admin/expenses/" + expenseId + "/receipt/content")
                    .build())
            .createdAt(Instant.parse("2026-10-12T04:00:00Z"))
            .updatedAt(Instant.parse("2026-10-12T04:05:00Z"))
            .build();
  }

  @Test
  @DisplayName("GET /api/v1/admin/expenses: supports unfiltered and filtered paged queries")
  void getExpenses_withAndWithoutFilters() throws Exception {
    PageResponse<FacilityExpenseResponse> page =
        PageResponse.<FacilityExpenseResponse>builder()
            .items(List.of(sampleExpense))
            .page(0)
            .size(10)
            .totalElements(1)
            .totalPages(1)
            .last(true)
            .build();

    when(facilityExpenseService.getExpenses(isNull(), isNull(), isNull(), any())).thenReturn(page);
    when(facilityExpenseService.getExpenses(
            eq("2026-10"), eq(ExpenseType.ELECTRICITY), eq("EVN"), any()))
        .thenReturn(page);

    mockMvc
        .perform(get("/api/v1/admin/expenses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Lấy danh sách chi phí vận hành thành công"))
        .andExpect(jsonPath("$.data.items[0].id").value(expenseId.toString()))
        .andExpect(jsonPath("$.data.items[0].expenseType").value("ELECTRICITY"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    mockMvc
        .perform(
            get("/api/v1/admin/expenses")
                .param("period", "2026-10")
                .param("expenseType", "ELECTRICITY")
                .param("search", "EVN")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.items[0].invoiceCode").value("EVN-2026-10"));

    when(facilityExpenseService.getExpenses(eq("2026-99"), isNull(), isNull(), any()))
        .thenThrow(new BaseAppException(ExpenseErrorCode.INVALID_REPORT_PERIOD));
    mockMvc
        .perform(get("/api/v1/admin/expenses").param("period", "2026-99"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_INVALID_PERIOD"));
  }

  @Test
  @DisplayName("GET /api/v1/admin/expenses/{id}: returns 200 detail or 404 when missing")
  void getExpenseById_returns200And404() throws Exception {
    when(facilityExpenseService.getExpenseById(expenseId)).thenReturn(sampleExpense);

    mockMvc
        .perform(get("/api/v1/admin/expenses/{id}", expenseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Lấy chi tiết chi phí vận hành thành công"))
        .andExpect(jsonPath("$.data.id").value(expenseId.toString()))
        .andExpect(jsonPath("$.data.amount").value(2500000));

    UUID missingId = UUID.randomUUID();
    when(facilityExpenseService.getExpenseById(missingId))
        .thenThrow(new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/admin/expenses/{id}", missingId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.httpStatus").value(404))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_404"));
  }

  @Test
  @DisplayName(
      "POST /api/v1/admin/expenses: returns 201 with manager principal, 400 invalid amount/DTO, and 409 duplicate salary")
  void createExpense_returns201AndMapsValidationAndConflictErrors() throws Exception {
    when(facilityExpenseService.createExpense(
            eq(managerUserId), any(CreateFacilityExpenseRequest.class)))
        .thenReturn(sampleExpense);

    mockMvc
        .perform(
            post("/api/v1/admin/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "ELECTRICITY",
                      "title": "Tiền điện EVN tháng 10/2026",
                      "amount": 2500000,
                      "expenseDate": "2026-10-12",
                      "invoiceCode": "EVN-2026-10",
                      "notes": "Thanh toán định kỳ"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.message").value("Tạo chi phí vận hành thành công"))
        .andExpect(jsonPath("$.data.id").value(expenseId.toString()));

    verify(facilityExpenseService)
        .createExpense(eq(managerUserId), any(CreateFacilityExpenseRequest.class));

    // Bean validation failure on blank title
    mockMvc
        .perform(
            post("/api/v1/admin/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "ELECTRICITY",
                      "title": "   ",
                      "amount": 2500000,
                      "expenseDate": "2026-10-12"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));

    // Invalid amount -> 400 EXPENSE_400_AMOUNT
    when(facilityExpenseService.createExpense(
            eq(managerUserId), any(CreateFacilityExpenseRequest.class)))
        .thenThrow(new BaseAppException(ExpenseErrorCode.INVALID_EXPENSE_AMOUNT));
    mockMvc
        .perform(
            post("/api/v1/admin/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "OTHER",
                      "title": "Chi phí lỗi",
                      "amount": -100,
                      "expenseDate": "2026-10-12"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_AMOUNT"));

    // Duplicate salary in month -> 409 EXPENSE_409_SALARY_MONTH
    when(facilityExpenseService.createExpense(
            eq(managerUserId), any(CreateFacilityExpenseRequest.class)))
        .thenThrow(new BaseAppException(ExpenseErrorCode.SALARY_EXPENSE_ALREADY_EXISTS));
    mockMvc
        .perform(
            post("/api/v1/admin/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "SALARY",
                      "title": "Lương tháng 10",
                      "amount": 15000000,
                      "expenseDate": "2026-10-15"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.httpStatus").value(409))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_409_SALARY_MONTH"));
  }

  @Test
  @DisplayName("PUT /api/v1/admin/expenses/{id}: returns 200 on success and 400 on invalid amount")
  void updateExpense_returns200And400() throws Exception {
    FacilityExpenseResponse updated =
        FacilityExpenseResponse.builder()
            .id(expenseId)
            .expenseType(ExpenseType.WATER)
            .title("Tiền nước Sawaco")
            .amount(new BigDecimal("950000"))
            .expenseDate(LocalDate.of(2026, 10, 14))
            .invoiceCode("SWC-2026-10")
            .notes("Đã điều chỉnh")
            .recordedByStaffId(staffId)
            .recordedByStaffName("Nguyen Quan Ly")
            .build();
    when(facilityExpenseService.updateExpense(
            eq(expenseId), any(UpdateFacilityExpenseRequest.class)))
        .thenReturn(updated);

    mockMvc
        .perform(
            put("/api/v1/admin/expenses/{id}", expenseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "WATER",
                      "title": "Tiền nước Sawaco",
                      "amount": 950000,
                      "expenseDate": "2026-10-14",
                      "invoiceCode": "SWC-2026-10",
                      "notes": "Đã điều chỉnh"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Cập nhật chi phí vận hành thành công"))
        .andExpect(jsonPath("$.data.expenseType").value("WATER"))
        .andExpect(jsonPath("$.data.amount").value(950000));

    when(facilityExpenseService.updateExpense(
            eq(expenseId), any(UpdateFacilityExpenseRequest.class)))
        .thenThrow(new BaseAppException(ExpenseErrorCode.INVALID_EXPENSE_AMOUNT));
    mockMvc
        .perform(
            put("/api/v1/admin/expenses/{id}", expenseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "expenseType": "WATER",
                      "title": "Tiền nước Sawaco",
                      "amount": 0,
                      "expenseDate": "2026-10-14"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_AMOUNT"));
  }

  @Test
  @DisplayName("DELETE /api/v1/admin/expenses/{id}: returns 200 on delete and 404 when missing")
  void deleteExpense_returns200And404() throws Exception {
    doNothing().when(facilityExpenseService).deleteExpense(expenseId);

    mockMvc
        .perform(delete("/api/v1/admin/expenses/{id}", expenseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Xóa chi phí vận hành thành công"));

    UUID missingId = UUID.randomUUID();
    doThrow(new BaseAppException(ExpenseErrorCode.EXPENSE_NOT_FOUND))
        .when(facilityExpenseService)
        .deleteExpense(missingId);

    mockMvc
        .perform(delete("/api/v1/admin/expenses/{id}", missingId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.httpStatus").value(404))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_404"));
  }

  @Test
  @DisplayName(
      "POST /api/v1/admin/expenses/{id}/receipt: guards missing/empty file at controller and maps 400 errors")
  void uploadReceipt_handlesSuccessAnd400Validation() throws Exception {
    // Missing file part -> controller MINOR-1 guard rejects before calling service
    mockMvc
        .perform(multipart("/api/v1/admin/expenses/{id}/receipt", expenseId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_RECEIPT_REQUIRED"));

    // Empty file -> controller MINOR-1 guard rejects before calling service
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);
    mockMvc
        .perform(multipart("/api/v1/admin/expenses/{id}/receipt", expenseId).file(emptyFile))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_RECEIPT_REQUIRED"));

    verify(facilityExpenseService, never()).uploadReceipt(any(), any());

    // Valid PNG upload -> 200
    MockMultipartFile validFile =
        new MockMultipartFile("file", "receipt.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2});
    when(facilityExpenseService.uploadReceipt(eq(expenseId), any()))
        .thenReturn(sampleExpenseWithReceipt);

    mockMvc
        .perform(multipart("/api/v1/admin/expenses/{id}/receipt", expenseId).file(validFile))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Tải lên hóa đơn/chứng từ thành công"))
        .andExpect(jsonPath("$.data.receipt.available").value(true))
        .andExpect(
            jsonPath("$.data.receipt.contentUrl")
                .value("/api/v1/admin/expenses/" + expenseId + "/receipt/content"));

    // Oversized -> 400 EXPENSE_400_RECEIPT_SIZE
    when(facilityExpenseService.uploadReceipt(eq(expenseId), any()))
        .thenThrow(new BaseAppException(ExpenseErrorCode.RECEIPT_FILE_SIZE_EXCEEDED));
    mockMvc
        .perform(multipart("/api/v1/admin/expenses/{id}/receipt", expenseId).file(validFile))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_RECEIPT_SIZE"));

    // Unsupported format -> 400 EXPENSE_400_RECEIPT_FORMAT
    MockMultipartFile gifFile =
        new MockMultipartFile("file", "bad.gif", MediaType.IMAGE_GIF_VALUE, new byte[] {1, 2, 3});
    when(facilityExpenseService.uploadReceipt(eq(expenseId), any()))
        .thenThrow(new BaseAppException(ExpenseErrorCode.UNSUPPORTED_RECEIPT_FORMAT));
    mockMvc
        .perform(multipart("/api/v1/admin/expenses/{id}/receipt", expenseId).file(gifFile))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_RECEIPT_FORMAT"));
  }

  @Test
  @DisplayName(
      "DELETE /api/v1/admin/expenses/{id}/receipt: returns 200 on deletion and 404 when receipt missing")
  void deleteReceipt_returns200And404() throws Exception {
    when(facilityExpenseService.deleteReceipt(expenseId)).thenReturn(sampleExpense);

    mockMvc
        .perform(delete("/api/v1/admin/expenses/{id}/receipt", expenseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Xóa hóa đơn/chứng từ thành công"))
        .andExpect(jsonPath("$.data.receipt").value(Matchers.nullValue()));

    when(facilityExpenseService.deleteReceipt(expenseId))
        .thenThrow(new BaseAppException(ExpenseErrorCode.EXPENSE_RECEIPT_NOT_FOUND));

    mockMvc
        .perform(delete("/api/v1/admin/expenses/{id}/receipt", expenseId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.httpStatus").value(404))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_404_RECEIPT"));
  }

  @Test
  @DisplayName(
      "GET and HEAD /api/v1/admin/expenses/{id}/receipt/content: stream inline headers, bytes, empty HEAD body, and close resource")
  void getAndHeadReceiptContent_streamHeadersAndCloseResource() throws Exception {
    byte[] pngBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    String expectedFilename = "expense-" + expenseId + "-receipt.png";
    String expectedDisposition = "inline; filename=\"" + expectedFilename + "\"";

    when(facilityExpenseService.loadReceiptContent(expenseId))
        .thenReturn(
            FacilityExpenseReceiptContent.builder()
                .resource(new ByteArrayResource(pngBytes))
                .contentType(MediaType.IMAGE_PNG_VALUE)
                .contentLength(pngBytes.length)
                .filename(expectedFilename)
                .build());

    mockMvc
        .perform(get("/api/v1/admin/expenses/{id}/receipt/content", expenseId))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(pngBytes.length)))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedDisposition))
        .andExpect(content().bytes(pngBytes));

    TrackingCloseableResource headResource = new TrackingCloseableResource(pngBytes);
    when(facilityExpenseService.loadReceiptContent(expenseId))
        .thenReturn(
            FacilityExpenseReceiptContent.builder()
                .resource(headResource)
                .contentType(MediaType.IMAGE_PNG_VALUE)
                .contentLength(pngBytes.length)
                .filename(expectedFilename)
                .build());

    mockMvc
        .perform(head("/api/v1/admin/expenses/{id}/receipt/content", expenseId))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(pngBytes.length)))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedDisposition))
        .andExpect(content().bytes(new byte[0]));

    assertThat(headResource.closed.get()).isTrue();
    assertThat(headResource.streamOpened.get()).isFalse();
    verify(facilityExpenseService, times(2)).loadReceiptContent(expenseId);
    verifyNoMoreInteractions(facilityExpenseService);
  }

  private static HandlerMethodArgumentResolver principalResolverFor(UserPrincipal principal) {
    return new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
            && parameter.getParameterType().isAssignableFrom(UserPrincipal.class);
      }

      @Override
      public Object resolveArgument(
          MethodParameter parameter,
          ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest,
          WebDataBinderFactory binderFactory) {
        return principal;
      }
    };
  }

  private static final class TrackingCloseableResource extends InputStreamResource
      implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean streamOpened = new AtomicBoolean(false);

    private TrackingCloseableResource(byte[] bytes) {
      super(new ByteArrayInputStream(bytes));
    }

    @Override
    public InputStream getInputStream() throws IOException, IllegalStateException {
      streamOpened.set(true);
      return super.getInputStream();
    }

    @Override
    public void close() {
      closed.set(true);
    }
  }
}
