package com.uit.dormmanagement.dtos.response.finance;

import com.uit.dormmanagement.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record FinancialReportPaidInvoiceItem(
    UUID invoiceId,
    String invoiceCode,
    String tenantName,
    String roomNumber,
    String bedCode,
    BigDecimal amount,
    PaymentMethod paymentMethod,
    Instant confirmedAt) {}
