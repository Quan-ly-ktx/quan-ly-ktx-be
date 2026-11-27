package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.response.invoice.VietQrInfoResponse;
import com.uit.dormmanagement.entities.Invoice;
import java.math.BigDecimal;
import java.util.Optional;

public interface VietQrPayloadService {

  Optional<VietQrInfoResponse> buildForInvoice(Invoice invoice);

  Optional<VietQrInfoResponse> buildForPayment(String invoiceCode, BigDecimal amount);
}
