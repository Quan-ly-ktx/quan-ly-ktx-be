package com.uit.dormmanagement.dtos.response.invoice;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record VietQrInfoResponse(
    String bankBin,
    String bankName,
    String accountNumber,
    String accountName,
    BigDecimal amount,
    String transferContent,
    String qrPayload) {}
