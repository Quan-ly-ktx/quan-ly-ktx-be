package com.uit.dormmanagement.dtos.response.invoice;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

@Builder
public record InvoiceBatchIssuanceResponse(
    LocalDate billingPeriod,
    int eligibleContractCount,
    int issuedCount,
    int skippedExistingCount,
    List<InvoiceSummaryResponse> issuedInvoices) {

  public InvoiceBatchIssuanceResponse {
    issuedInvoices = issuedInvoices != null ? List.copyOf(issuedInvoices) : List.of();
  }
}
