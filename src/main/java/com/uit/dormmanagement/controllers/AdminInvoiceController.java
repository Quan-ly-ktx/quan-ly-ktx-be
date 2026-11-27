package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.invoice.IssueInvoiceBatchRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceBatchIssuanceResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofContent;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryStatsResponse;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.InvoiceBatchService;
import com.uit.dormmanagement.services.InvoiceService;
import com.uit.dormmanagement.utils.StreamingResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Invoices (Admin)",
    description =
        "Invoice management, monthly batch issuance, and payment reconciliation for managers (FR-15, FR-16)")
@RestController
@RequestMapping("/api/v1/admin/invoices")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class AdminInvoiceController extends AbstractBaseController {

  private final InvoiceService invoiceService;
  private final InvoiceBatchService invoiceBatchService;

  @Operation(
      summary = "Get invoices list",
      description =
          "Retrieve paged list of invoices with optional search, status, billing period, and overdue filters")
  @GetMapping
  public ResponseEntity<SingleResponse<PageResponse<InvoiceSummaryResponse>>> getInvoices(
      Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate billingPeriod,
      @RequestParam(required = false) Boolean overdueOnly) {
    Page<InvoiceSummaryResponse> page =
        invoiceService.getAdminInvoices(pageable, search, status, billingPeriod, overdueOnly);
    return responseFactory.successPage(page, "Lấy danh sách hóa đơn thành công");
  }

  @Operation(
      summary = "Get invoice summary stats",
      description =
          "Retrieve aggregated invoice amounts and counts across paid, in-term unpaid, overdue, and pending confirmation")
  @GetMapping("/summary")
  public ResponseEntity<SingleResponse<InvoiceSummaryStatsResponse>> getInvoiceSummary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate billingPeriod) {
    InvoiceSummaryStatsResponse summary = invoiceService.getAdminInvoiceSummary(billingPeriod);
    return responseFactory.success(summary, "Lấy tổng hợp thống kê hóa đơn thành công");
  }

  @Operation(
      summary = "Get invoice detail",
      description = "Retrieve full invoice details including proof metadata, payment, and VietQR")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<InvoiceDetailResponse>> getInvoiceById(
      @PathVariable UUID id) {
    InvoiceDetailResponse detail = invoiceService.getAdminInvoiceById(id);
    return responseFactory.success(detail, "Lấy chi tiết hóa đơn thành công");
  }

  @Operation(
      summary = "Issue monthly invoice batch",
      description =
          "Trigger idempotent monthly invoice issuance for eligible active contracts (FR-15)")
  @PostMapping("/issue-batch")
  public ResponseEntity<SingleResponse<InvoiceBatchIssuanceResponse>> issueMonthlyBatch(
      @RequestBody(required = false) @Valid IssueInvoiceBatchRequest request) {
    LocalDate billingPeriod = request != null ? request.billingPeriod() : null;
    InvoiceBatchIssuanceResponse result = invoiceBatchService.issueMonthlyBatch(billingPeriod);
    return responseFactory.success(result, "Phát hành hóa đơn định kỳ thành công");
  }

  @Operation(
      summary = "Stream invoice payment proof content",
      description = "Stream private payment proof image for manager review")
  @RequestMapping(
      value = "/{id}/proof/content",
      method = {RequestMethod.GET, RequestMethod.HEAD})
  public ResponseEntity<Resource> getProofContent(@PathVariable UUID id, HttpMethod httpMethod) {
    InvoiceProofContent content = invoiceService.loadProofContentForAdmin(id);
    return StreamingResponseUtils.okInline(
        content.resource(),
        content.contentType(),
        content.contentLength(),
        content.downloadFilename(),
        httpMethod);
  }

  @Operation(
      summary = "Confirm cash payment",
      description = "Atomically confirm cash payment for a DEBT invoice (FR-16)")
  @PatchMapping("/{id}/confirm-cash")
  public ResponseEntity<SingleResponse<InvoiceDetailResponse>> confirmCashPayment(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    UUID managerUserId = principal != null ? principal.getId() : null;
    InvoiceDetailResponse response = invoiceService.confirmCashPayment(managerUserId, id);
    return responseFactory.success(response, "Xác nhận thanh toán tiền mặt thành công");
  }

  @Operation(
      summary = "Confirm VietQR transfer payment",
      description =
          "Atomically confirm VietQR bank transfer for a PENDING_CONFIRMATION invoice (FR-16)")
  @PatchMapping("/{id}/confirm-transfer")
  public ResponseEntity<SingleResponse<InvoiceDetailResponse>> confirmTransferPayment(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    UUID managerUserId = principal != null ? principal.getId() : null;
    InvoiceDetailResponse response = invoiceService.confirmTransferPayment(managerUserId, id);
    return responseFactory.success(response, "Xác nhận thanh toán chuyển khoản thành công");
  }

  @Operation(
      summary = "Reject payment proof",
      description =
          "Reject submitted payment proof and revert invoice from PENDING_CONFIRMATION back to DEBT")
  @PatchMapping("/{id}/reject-proof")
  public ResponseEntity<SingleResponse<InvoiceDetailResponse>> rejectProof(@PathVariable UUID id) {
    InvoiceDetailResponse response = invoiceService.rejectProof(id);
    return responseFactory.success(response, "Đã từ chối minh chứng thanh toán và yêu cầu gửi lại");
  }
}
