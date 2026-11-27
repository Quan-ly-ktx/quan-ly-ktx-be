package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofContent;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.InvoiceService;
import com.uit.dormmanagement.utils.StreamingResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(
    name = "Invoices (Tenant)",
    description =
        "Tenant invoice viewer, VietQR details, and payment proof submission (FR-16, FR-26)")
@RestController
@RequestMapping("/api/v1/tenant/invoices")
@PreAuthorize("hasRole('TENANT')")
@RequiredArgsConstructor
public class TenantInvoiceController extends AbstractBaseController {

  private final InvoiceService invoiceService;

  @Operation(
      summary = "Get my invoices",
      description = "Retrieve all invoices belonging to the logged-in tenant with optional status")
  @GetMapping
  public ResponseEntity<SingleResponse<List<InvoiceDetailResponse>>> getMyInvoices(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) InvoiceStatus status) {
    UUID tenantUserId = principal != null ? principal.getId() : null;
    List<InvoiceDetailResponse> invoices = invoiceService.getTenantInvoices(tenantUserId, status);
    return responseFactory.success(invoices, "Lấy danh sách hóa đơn của tôi thành công");
  }

  @Operation(
      summary = "Get my invoice detail",
      description = "Retrieve details of a specific invoice owned by the logged-in tenant")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<InvoiceDetailResponse>> getMyInvoiceById(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    UUID tenantUserId = principal != null ? principal.getId() : null;
    InvoiceDetailResponse detail = invoiceService.getTenantInvoiceById(tenantUserId, id);
    return responseFactory.success(detail, "Lấy chi tiết hóa đơn thành công");
  }

  @Operation(
      summary = "Submit payment proof",
      description =
          "Upload bank transfer receipt image (JPG/PNG/WEBP, max 5 MB) for an unpaid DEBT invoice")
  @PostMapping(value = "/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<SingleResponse<InvoiceDetailResponse>> submitProof(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @RequestParam(value = "file", required = false) MultipartFile file) {
    UUID tenantUserId = principal != null ? principal.getId() : null;
    InvoiceDetailResponse detail = invoiceService.submitProof(tenantUserId, id, file);
    return responseFactory.success(detail, "Tải lên minh chứng thanh toán thành công");
  }

  @Operation(
      summary = "Stream own invoice payment proof content",
      description = "Stream private payment proof image for the owning tenant")
  @RequestMapping(
      value = "/{id}/proof/content",
      method = {RequestMethod.GET, RequestMethod.HEAD})
  public ResponseEntity<Resource> getProofContent(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      HttpMethod httpMethod) {
    UUID tenantUserId = principal != null ? principal.getId() : null;
    InvoiceProofContent content = invoiceService.loadProofContentForTenant(tenantUserId, id);
    return StreamingResponseUtils.okInline(
        content.resource(),
        content.contentType(),
        content.contentLength(),
        content.downloadFilename(),
        httpMethod);
  }
}
