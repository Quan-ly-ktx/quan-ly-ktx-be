package com.uit.dormmanagement.dtos.response.invoice;

import com.uit.dormmanagement.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record InvoiceDetailResponse(
    UUID id,
    String invoiceCode,
    UUID contractId,
    String contractCode,
    UUID tenantId,
    String tenantCode,
    String tenantName,
    String tenantPhone,
    String tenantIdCardNumber,
    String tenantEmail,
    String roomNumber,
    String bedCode,
    LocalDate billingPeriod,
    BigDecimal amount,
    LocalDate dueOn,
    InvoiceStatus status,
    boolean overdue,
    long overdueDays,
    boolean proofAvailable,
    Instant proofSubmittedAt,
    InvoiceProofResponse proof,
    PaymentTransactionResponse payment,
    VietQrInfoResponse vietQr,
    Instant issuedAt) {}
