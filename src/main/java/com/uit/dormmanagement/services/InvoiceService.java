package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofContent;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryStatsResponse;
import com.uit.dormmanagement.enums.InvoiceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface InvoiceService {

  Page<InvoiceSummaryResponse> getAdminInvoices(
      Pageable pageable,
      String search,
      InvoiceStatus status,
      LocalDate billingPeriod,
      Boolean overdueOnly);

  InvoiceSummaryStatsResponse getAdminInvoiceSummary(LocalDate billingPeriod);

  InvoiceDetailResponse getAdminInvoiceById(UUID invoiceId);

  List<InvoiceDetailResponse> getTenantInvoices(UUID tenantUserId, InvoiceStatus status);

  InvoiceDetailResponse getTenantInvoiceById(UUID tenantUserId, UUID invoiceId);

  InvoiceDetailResponse submitProof(UUID tenantUserId, UUID invoiceId, MultipartFile file);

  InvoiceDetailResponse rejectProof(UUID invoiceId);

  InvoiceProofContent loadProofContentForAdmin(UUID invoiceId);

  InvoiceProofContent loadProofContentForTenant(UUID tenantUserId, UUID invoiceId);

  InvoiceDetailResponse confirmCashPayment(UUID managerUserId, UUID invoiceId);

  InvoiceDetailResponse confirmTransferPayment(UUID managerUserId, UUID invoiceId);
}
