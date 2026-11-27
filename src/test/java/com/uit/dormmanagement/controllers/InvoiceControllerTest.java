package com.uit.dormmanagement.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceBatchIssuanceResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofContent;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryStatsResponse;
import com.uit.dormmanagement.dtos.response.invoice.PaymentTransactionResponse;
import com.uit.dormmanagement.dtos.response.invoice.VietQrInfoResponse;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.InvoiceErrorCode;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.InvoiceBatchService;
import com.uit.dormmanagement.services.InvoiceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
class InvoiceControllerTest {

  @Mock private InvoiceService invoiceService;
  @Mock private InvoiceBatchService invoiceBatchService;

  @InjectMocks private AdminInvoiceController adminInvoiceController;
  @InjectMocks private TenantInvoiceController tenantInvoiceController;

  private MockMvc adminMockMvc;
  private MockMvc tenantMockMvc;
  private final ResponseFactory responseFactory = new ResponseFactory();

  private UUID invoiceId;
  private UUID contractId;
  private UUID tenantId;
  private UUID tenantUserId;
  private UUID managerUserId;
  private InvoiceSummaryResponse sampleSummary;
  private InvoiceDetailResponse sampleDebtDetail;
  private InvoiceDetailResponse samplePendingDetail;
  private InvoiceDetailResponse samplePaidDetail;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(adminInvoiceController, "responseFactory", responseFactory);
    ReflectionTestUtils.setField(tenantInvoiceController, "responseFactory", responseFactory);

    invoiceId = UUID.randomUUID();
    contractId = UUID.randomUUID();
    tenantId = UUID.randomUUID();
    tenantUserId = UUID.randomUUID();
    managerUserId = UUID.randomUUID();

    UserPrincipal tenantPrincipal =
        UserPrincipal.create(
            User.builder()
                .id(tenantUserId)
                .email("tenant.invoice@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    UserPrincipal managerPrincipal =
        UserPrincipal.create(
            User.builder()
                .id(managerUserId)
                .email("manager.invoice@uit.edu.vn")
                .passwordHash("hashed")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

    adminMockMvc =
        MockMvcBuilders.standaloneSetup(adminInvoiceController)
            .setCustomArgumentResolvers(
                principalResolverFor(managerPrincipal), new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    tenantMockMvc =
        MockMvcBuilders.standaloneSetup(tenantInvoiceController)
            .setCustomArgumentResolvers(principalResolverFor(tenantPrincipal))
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    sampleSummary =
        InvoiceSummaryResponse.builder()
            .id(invoiceId)
            .invoiceCode("INV-202610-HD-2026-0010")
            .contractId(contractId)
            .contractCode("HD-2026-0010")
            .tenantId(tenantId)
            .tenantCode("NT-2026-010")
            .tenantName("Nguyen Van A")
            .tenantPhone("0901234567")
            .tenantIdCardNumber("079203001122")
            .tenantEmail("tenant.invoice@uit.edu.vn")
            .roomNumber("P.301")
            .bedCode("G01")
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 10, 5))
            .status(InvoiceStatus.DEBT)
            .overdue(true)
            .overdueDays(5L)
            .proofAvailable(false)
            .issuedAt(Instant.parse("2026-09-25T00:00:00Z"))
            .build();

    sampleDebtDetail =
        InvoiceDetailResponse.builder()
            .id(invoiceId)
            .invoiceCode("INV-202610-HD-2026-0010")
            .contractId(contractId)
            .contractCode("HD-2026-0010")
            .tenantId(tenantId)
            .tenantCode("NT-2026-010")
            .tenantName("Nguyen Van A")
            .tenantPhone("0901234567")
            .tenantIdCardNumber("079203001122")
            .tenantEmail("tenant.invoice@uit.edu.vn")
            .roomNumber("P.301")
            .bedCode("G01")
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 10, 5))
            .status(InvoiceStatus.DEBT)
            .overdue(true)
            .overdueDays(5L)
            .proofAvailable(false)
            .vietQr(
                VietQrInfoResponse.builder()
                    .bankBin("970405")
                    .bankName("Agribank")
                    .accountNumber("1234567890")
                    .accountName("TEST KTX HUB")
                    .amount(new BigDecimal("1500000"))
                    .transferContent("KTX INV-202610-HD-2026-0010")
                    .qrPayload("0002010102126304ABCD")
                    .build())
            .issuedAt(Instant.parse("2026-09-25T00:00:00Z"))
            .build();

    samplePendingDetail =
        InvoiceDetailResponse.builder()
            .id(invoiceId)
            .invoiceCode("INV-202610-HD-2026-0010")
            .contractId(contractId)
            .status(InvoiceStatus.PENDING_CONFIRMATION)
            .overdue(false)
            .overdueDays(0L)
            .proofAvailable(true)
            .proofSubmittedAt(Instant.parse("2026-10-08T09:00:00Z"))
            .proof(
                InvoiceProofResponse.builder()
                    .available(true)
                    .submittedAt(Instant.parse("2026-10-08T09:00:00Z"))
                    .contentUrl("/api/v1/tenant/invoices/" + invoiceId + "/proof/content")
                    .build())
            .build();

    samplePaidDetail =
        InvoiceDetailResponse.builder()
            .id(invoiceId)
            .invoiceCode("INV-202610-HD-2026-0010")
            .contractId(contractId)
            .status(InvoiceStatus.PAID)
            .overdue(false)
            .overdueDays(0L)
            .payment(
                PaymentTransactionResponse.builder()
                    .id(UUID.randomUUID())
                    .receiptCode("PT-INV-202610-HD-2026-0010")
                    .amount(new BigDecimal("1500000"))
                    .paymentMethod(PaymentMethod.CASH)
                    .confirmedByStaffId(UUID.randomUUID())
                    .confirmedByStaffName("Tran Quan Ly")
                    .confirmedAt(Instant.parse("2026-10-10T03:00:00Z"))
                    .build())
            .build();
  }

  @Test
  @DisplayName("GET /api/v1/admin/invoices: forwards filters and returns paged summaries")
  void getAdminInvoices_forwardsFiltersAndReturnsPage() throws Exception {
    Page<InvoiceSummaryResponse> page =
        new PageImpl<>(List.of(sampleSummary), PageRequest.of(0, 10), 1);
    LocalDate period = LocalDate.of(2026, 10, 1);
    when(invoiceService.getAdminInvoices(
            any(), eq("P.301"), eq(InvoiceStatus.DEBT), eq(period), eq(true)))
        .thenReturn(page);

    adminMockMvc
        .perform(
            get("/api/v1/admin/invoices")
                .param("page", "0")
                .param("size", "10")
                .param("search", "P.301")
                .param("status", "DEBT")
                .param("billingPeriod", "2026-10-01")
                .param("overdueOnly", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Lấy danh sách hóa đơn thành công"))
        .andExpect(jsonPath("$.data.items[0].invoiceCode").value("INV-202610-HD-2026-0010"))
        .andExpect(jsonPath("$.data.items[0].overdue").value(true))
        .andExpect(jsonPath("$.data.items[0].overdueDays").value(5));
  }

  @Test
  @DisplayName(
      "GET /api/v1/admin/invoices/summary: returns stats with and without billingPeriod filter")
  void getAdminInvoiceSummary_returnsStats() throws Exception {
    LocalDate period = LocalDate.of(2026, 10, 1);
    InvoiceSummaryStatsResponse stats =
        InvoiceSummaryStatsResponse.builder()
            .billingPeriod(period)
            .paidAmount(new BigDecimal("1500000"))
            .paidCount(1L)
            .unpaidInTermAmount(new BigDecimal("1500000"))
            .unpaidInTermCount(1L)
            .overdueAmount(new BigDecimal("1500000"))
            .overdueCount(1L)
            .pendingConfirmationAmount(new BigDecimal("1500000"))
            .pendingConfirmationCount(1L)
            .totalExpectedAmount(new BigDecimal("6000000"))
            .totalInvoiceCount(4L)
            .build();
    when(invoiceService.getAdminInvoiceSummary(period)).thenReturn(stats);
    when(invoiceService.getAdminInvoiceSummary(null))
        .thenReturn(
            InvoiceSummaryStatsResponse.builder()
                .billingPeriod(null)
                .paidAmount(BigDecimal.ZERO)
                .paidCount(0L)
                .unpaidInTermAmount(BigDecimal.ZERO)
                .unpaidInTermCount(0L)
                .overdueAmount(BigDecimal.ZERO)
                .overdueCount(0L)
                .pendingConfirmationAmount(BigDecimal.ZERO)
                .pendingConfirmationCount(0L)
                .totalExpectedAmount(BigDecimal.ZERO)
                .totalInvoiceCount(0L)
                .build());

    adminMockMvc
        .perform(get("/api/v1/admin/invoices/summary").param("billingPeriod", "2026-10-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Lấy tổng hợp thống kê hóa đơn thành công"))
        .andExpect(jsonPath("$.data.billingPeriod").value("2026-10-01"))
        .andExpect(jsonPath("$.data.paidCount").value(1))
        .andExpect(jsonPath("$.data.overdueCount").value(1))
        .andExpect(jsonPath("$.data.pendingConfirmationCount").value(1))
        .andExpect(jsonPath("$.data.totalExpectedAmount").value(6000000))
        .andExpect(jsonPath("$.data.totalInvoiceCount").value(4));

    adminMockMvc
        .perform(get("/api/v1/admin/invoices/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalInvoiceCount").value(0));
  }

  @Test
  @DisplayName("GET /api/v1/admin/invoices/{id}: returns invoice detail")
  void getAdminInvoiceById_returnsDetail() throws Exception {
    when(invoiceService.getAdminInvoiceById(invoiceId)).thenReturn(sampleDebtDetail);

    adminMockMvc
        .perform(get("/api/v1/admin/invoices/{id}", invoiceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Lấy chi tiết hóa đơn thành công"))
        .andExpect(jsonPath("$.data.invoiceCode").value("INV-202610-HD-2026-0010"))
        .andExpect(jsonPath("$.data.vietQr.bankBin").value("970405"));
  }

  @Test
  @DisplayName(
      "POST /api/v1/admin/invoices/issue-batch: supports empty body and explicit billingPeriod body")
  void issueMonthlyBatch_supportsEmptyAndExplicitBody() throws Exception {
    InvoiceBatchIssuanceResponse defaultBatch =
        InvoiceBatchIssuanceResponse.builder()
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .eligibleContractCount(2)
            .issuedCount(2)
            .skippedExistingCount(0)
            .issuedInvoices(List.of(sampleSummary))
            .build();
    InvoiceBatchIssuanceResponse novBatch =
        InvoiceBatchIssuanceResponse.builder()
            .billingPeriod(LocalDate.of(2026, 11, 1))
            .eligibleContractCount(1)
            .issuedCount(1)
            .skippedExistingCount(0)
            .issuedInvoices(List.of(sampleSummary))
            .build();

    when(invoiceBatchService.issueMonthlyBatch(null)).thenReturn(defaultBatch);
    when(invoiceBatchService.issueMonthlyBatch(LocalDate.of(2026, 11, 1))).thenReturn(novBatch);

    // Empty body
    adminMockMvc
        .perform(post("/api/v1/admin/invoices/issue-batch"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Phát hành hóa đơn định kỳ thành công"))
        .andExpect(jsonPath("$.data.billingPeriod").value("2026-10-01"))
        .andExpect(jsonPath("$.data.issuedCount").value(2));

    // Explicit JSON body
    adminMockMvc
        .perform(
            post("/api/v1/admin/invoices/issue-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"billingPeriod\":\"2026-11-01\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.billingPeriod").value("2026-11-01"))
        .andExpect(jsonPath("$.data.issuedCount").value(1));

    verify(invoiceBatchService).issueMonthlyBatch(null);
    verify(invoiceBatchService).issueMonthlyBatch(LocalDate.of(2026, 11, 1));
  }

  @Test
  @DisplayName(
      "GET and HEAD /api/v1/admin/invoices/{id}/proof/content: stream bytes, headers, empty HEAD body, and close stream")
  void adminProofContent_getAndHeadStreamWithHeadersAndCloseResource() throws Exception {
    byte[] bytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    when(invoiceService.loadProofContentForAdmin(invoiceId))
        .thenReturn(
            new InvoiceProofContent(
                new ByteArrayResource(bytes),
                MediaType.IMAGE_PNG_VALUE,
                bytes.length,
                "INV-202610-HD-2026-0010-proof.png"));

    adminMockMvc
        .perform(get("/api/v1/admin/invoices/{id}/proof/content", invoiceId))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length)))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"INV-202610-HD-2026-0010-proof.png\""))
        .andExpect(content().bytes(bytes));

    TrackingCloseableResource headResource = new TrackingCloseableResource(bytes);
    when(invoiceService.loadProofContentForAdmin(invoiceId))
        .thenReturn(
            new InvoiceProofContent(
                headResource,
                MediaType.IMAGE_PNG_VALUE,
                bytes.length,
                "INV-202610-HD-2026-0010-proof.png"));

    adminMockMvc
        .perform(head("/api/v1/admin/invoices/{id}/proof/content", invoiceId))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length)))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"INV-202610-HD-2026-0010-proof.png\""))
        .andExpect(content().bytes(new byte[0]));
    assertThat(headResource.closed.get()).isTrue();
    assertThat(headResource.streamOpened.get()).isFalse();
  }

  @Test
  @DisplayName(
      "PATCH confirm-cash, confirm-transfer, and reject-proof delegate to service and return 200")
  void adminReconciliationActions_delegateAndReturn200() throws Exception {
    when(invoiceService.confirmCashPayment(managerUserId, invoiceId)).thenReturn(samplePaidDetail);
    InvoiceDetailResponse transferPaid =
        InvoiceDetailResponse.builder()
            .id(invoiceId)
            .status(InvoiceStatus.PAID)
            .payment(
                PaymentTransactionResponse.builder()
                    .id(UUID.randomUUID())
                    .receiptCode("PT-INV-202610-HD-2026-0010")
                    .amount(new BigDecimal("1500000"))
                    .paymentMethod(PaymentMethod.VIETQR_TRANSFER)
                    .build())
            .build();
    when(invoiceService.confirmTransferPayment(managerUserId, invoiceId)).thenReturn(transferPaid);
    when(invoiceService.rejectProof(invoiceId)).thenReturn(sampleDebtDetail);

    adminMockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/confirm-cash", invoiceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Xác nhận thanh toán tiền mặt thành công"))
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.payment.paymentMethod").value("CASH"));

    adminMockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/confirm-transfer", invoiceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Xác nhận thanh toán chuyển khoản thành công"))
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.payment.paymentMethod").value("VIETQR_TRANSFER"));

    adminMockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/reject-proof", invoiceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(
            jsonPath("$.message").value("Đã từ chối minh chứng thanh toán và yêu cầu gửi lại"))
        .andExpect(jsonPath("$.data.status").value("DEBT"));

    verify(invoiceService).confirmCashPayment(managerUserId, invoiceId);
    verify(invoiceService).confirmTransferPayment(managerUserId, invoiceId);
    verify(invoiceService).rejectProof(invoiceId);
  }

  @Test
  @DisplayName("GET /api/v1/tenant/invoices: supports unfiltered and status-filtered queries")
  void getTenantInvoices_supportsOptionalStatus() throws Exception {
    when(invoiceService.getTenantInvoices(tenantUserId, null))
        .thenReturn(List.of(sampleDebtDetail));
    when(invoiceService.getTenantInvoices(tenantUserId, InvoiceStatus.DEBT))
        .thenReturn(List.of(sampleDebtDetail));

    tenantMockMvc
        .perform(get("/api/v1/tenant/invoices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Lấy danh sách hóa đơn của tôi thành công"))
        .andExpect(jsonPath("$.data[0].invoiceCode").value("INV-202610-HD-2026-0010"));

    tenantMockMvc
        .perform(get("/api/v1/tenant/invoices").param("status", "DEBT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("DEBT"));
  }

  @Test
  @DisplayName("GET /api/v1/tenant/invoices/{id}: returns owned invoice detail")
  void getTenantInvoiceById_returnsDetail() throws Exception {
    when(invoiceService.getTenantInvoiceById(tenantUserId, invoiceId)).thenReturn(sampleDebtDetail);

    tenantMockMvc
        .perform(get("/api/v1/tenant/invoices/{id}", invoiceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Lấy chi tiết hóa đơn thành công"))
        .andExpect(jsonPath("$.data.vietQr.transferContent").value("KTX INV-202610-HD-2026-0010"));
  }

  @Test
  @DisplayName(
      "POST /api/v1/tenant/invoices/{id}/proof: multipart upload returns 200 and missing file delegates to 400")
  void submitProof_uploadsMultipartAndMapsMissingFile() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1, 2, 3, 4});
    when(invoiceService.submitProof(eq(tenantUserId), eq(invoiceId), any()))
        .thenReturn(samplePendingDetail);

    tenantMockMvc
        .perform(multipart("/api/v1/tenant/invoices/{id}/proof", invoiceId).file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Tải lên minh chứng thanh toán thành công"))
        .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"))
        .andExpect(
            jsonPath("$.data.proof.contentUrl")
                .value("/api/v1/tenant/invoices/" + invoiceId + "/proof/content"));

    when(invoiceService.submitProof(tenantUserId, invoiceId, null))
        .thenThrow(new BaseAppException(InvoiceErrorCode.PROOF_FILE_REQUIRED));

    tenantMockMvc
        .perform(multipart("/api/v1/tenant/invoices/{id}/proof", invoiceId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("INVOICE_400_PROOF_REQUIRED"));
  }

  @Test
  @DisplayName(
      "GET and HEAD /api/v1/tenant/invoices/{id}/proof/content: stream bytes, headers, empty HEAD body, and close stream")
  void tenantProofContent_getAndHeadStreamWithHeadersAndCloseResource() throws Exception {
    byte[] bytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    when(invoiceService.loadProofContentForTenant(tenantUserId, invoiceId))
        .thenReturn(
            new InvoiceProofContent(
                new ByteArrayResource(bytes),
                MediaType.IMAGE_JPEG_VALUE,
                bytes.length,
                "INV-202610-HD-2026-0010-proof.jpg"));

    tenantMockMvc
        .perform(get("/api/v1/tenant/invoices/{id}/proof/content", invoiceId))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length)))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"INV-202610-HD-2026-0010-proof.jpg\""))
        .andExpect(content().bytes(bytes));

    TrackingCloseableResource headResource = new TrackingCloseableResource(bytes);
    when(invoiceService.loadProofContentForTenant(tenantUserId, invoiceId))
        .thenReturn(
            new InvoiceProofContent(
                headResource,
                MediaType.IMAGE_JPEG_VALUE,
                bytes.length,
                "INV-202610-HD-2026-0010-proof.jpg"));

    tenantMockMvc
        .perform(head("/api/v1/tenant/invoices/{id}/proof/content", invoiceId))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length)))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"INV-202610-HD-2026-0010-proof.jpg\""))
        .andExpect(content().bytes(new byte[0]));
    assertThat(headResource.closed.get()).isTrue();
    assertThat(headResource.streamOpened.get()).isFalse();
  }

  @Test
  @DisplayName("Domain error codes map to expected 400, 403, 404, and 409 HTTP responses")
  void domainErrorMappings_map400_403_404_409() throws Exception {
    // 400 INVALID_BILLING_PERIOD
    when(invoiceService.getAdminInvoiceSummary(LocalDate.of(2026, 10, 15)))
        .thenThrow(new BaseAppException(InvoiceErrorCode.INVALID_BILLING_PERIOD));
    adminMockMvc
        .perform(get("/api/v1/admin/invoices/summary").param("billingPeriod", "2026-10-15"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("INVOICE_400_INVALID_PERIOD"));

    // 400 UNSUPPORTED_PROOF_FORMAT
    MockMultipartFile badFile =
        new MockMultipartFile("file", "bad.gif", "image/gif", new byte[] {1, 2, 3});
    when(invoiceService.submitProof(eq(tenantUserId), eq(invoiceId), any()))
        .thenThrow(new BaseAppException(InvoiceErrorCode.UNSUPPORTED_PROOF_FORMAT));
    tenantMockMvc
        .perform(multipart("/api/v1/tenant/invoices/{id}/proof", invoiceId).file(badFile))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("INVOICE_400_PROOF_FORMAT"));

    // 403 MANAGER_STAFF_PROFILE_REQUIRED
    doThrow(new BaseAppException(InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED))
        .when(invoiceService)
        .confirmCashPayment(managerUserId, invoiceId);
    adminMockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/confirm-cash", invoiceId))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.httpStatus").value(403))
        .andExpect(jsonPath("$.errorCode").value("INVOICE_403_STAFF_PROFILE"));

    // 404 INVOICE_NOT_FOUND
    doThrow(new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND))
        .when(invoiceService)
        .getAdminInvoiceById(invoiceId);
    adminMockMvc
        .perform(get("/api/v1/admin/invoices/{id}", invoiceId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.httpStatus").value(404))
        .andExpect(jsonPath("$.errorCode").value("INVOICE_404"));

    // 404 INVOICE_PROOF_NOT_FOUND
    doThrow(new BaseAppException(InvoiceErrorCode.INVOICE_PROOF_NOT_FOUND))
        .when(invoiceService)
        .loadProofContentForTenant(tenantUserId, invoiceId);
    tenantMockMvc
        .perform(get("/api/v1/tenant/invoices/{id}/proof/content", invoiceId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.httpStatus").value(404))
        .andExpect(jsonPath("$.errorCode").value("INVOICE_404_PROOF"));

    // 409 INVOICE_NOT_DEBT
    doThrow(new BaseAppException(InvoiceErrorCode.INVOICE_NOT_DEBT))
        .when(invoiceService)
        .confirmCashPayment(managerUserId, invoiceId);
    adminMockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/confirm-cash", invoiceId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.httpStatus").value(409))
        .andExpect(jsonPath("$.errorCode").value("INVOICE_409_NOT_DEBT"));

    // 409 INVOICE_NOT_PENDING_CONFIRMATION
    doThrow(new BaseAppException(InvoiceErrorCode.INVOICE_NOT_PENDING_CONFIRMATION))
        .when(invoiceService)
        .rejectProof(invoiceId);
    adminMockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/reject-proof", invoiceId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.httpStatus").value(409))
        .andExpect(jsonPath("$.errorCode").value("INVOICE_409_NOT_PENDING"));

    // 409 INVOICE_ALREADY_PAID
    doThrow(new BaseAppException(InvoiceErrorCode.INVOICE_ALREADY_PAID))
        .when(invoiceService)
        .confirmTransferPayment(managerUserId, invoiceId);
    adminMockMvc
        .perform(patch("/api/v1/admin/invoices/{id}/confirm-transfer", invoiceId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.httpStatus").value(409))
        .andExpect(jsonPath("$.errorCode").value("INVOICE_409_ALREADY_PAID"));

    verifyNoInteractions(invoiceBatchService);
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
