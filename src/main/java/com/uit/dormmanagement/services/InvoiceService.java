package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofContent;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface InvoiceService {

  InvoiceDetailResponse submitProof(UUID tenantUserId, UUID invoiceId, MultipartFile file);

  InvoiceDetailResponse rejectProof(UUID invoiceId);

  InvoiceProofContent loadProofContentForAdmin(UUID invoiceId);

  InvoiceProofContent loadProofContentForTenant(UUID tenantUserId, UUID invoiceId);

  InvoiceDetailResponse confirmCashPayment(UUID managerUserId, UUID invoiceId);

  InvoiceDetailResponse confirmTransferPayment(UUID managerUserId, UUID invoiceId);
}
