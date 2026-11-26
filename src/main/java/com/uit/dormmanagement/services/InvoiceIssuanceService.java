package com.uit.dormmanagement.services;

import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractRenewal;
import com.uit.dormmanagement.entities.Invoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceIssuanceService {

  boolean hasUnsettledInvoices(UUID tenantId);

  Invoice issueMonthlyInvoiceIfAbsent(
      Contract contract, LocalDate billingPeriod, BigDecimal amount);

  Optional<Invoice> issueRenewalCatchUpInvoiceIfEligible(
      Contract contract, ContractRenewal renewal);
}
