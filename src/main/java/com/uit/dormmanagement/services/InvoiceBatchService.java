package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.response.invoice.InvoiceBatchIssuanceResponse;
import java.time.LocalDate;

public interface InvoiceBatchService {

  InvoiceBatchIssuanceResponse issueMonthlyBatch(LocalDate requestedBillingPeriod);
}
