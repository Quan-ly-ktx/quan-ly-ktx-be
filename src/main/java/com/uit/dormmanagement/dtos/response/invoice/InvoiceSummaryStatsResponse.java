package com.uit.dormmanagement.dtos.response.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record InvoiceSummaryStatsResponse(
    LocalDate billingPeriod,
    BigDecimal paidAmount,
    long paidCount,
    BigDecimal unpaidInTermAmount,
    long unpaidInTermCount,
    BigDecimal overdueAmount,
    long overdueCount,
    BigDecimal pendingConfirmationAmount,
    long pendingConfirmationCount,
    BigDecimal totalExpectedAmount,
    long totalInvoiceCount) {}
