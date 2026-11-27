package com.uit.dormmanagement.dtos.response.invoice;

import com.uit.dormmanagement.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PaymentTransactionResponse(
    UUID id,
    String receiptCode,
    BigDecimal amount,
    PaymentMethod paymentMethod,
    UUID confirmedByStaffId,
    String confirmedByStaffName,
    Instant confirmedAt) {}
