package com.uit.dormmanagement.dtos.response.onboarding;

import com.uit.dormmanagement.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OnboardingInvoiceSummary(
    UUID id,
    String invoiceCode,
    LocalDate billingPeriod,
    BigDecimal amount,
    LocalDate dueOn,
    InvoiceStatus status) {}
