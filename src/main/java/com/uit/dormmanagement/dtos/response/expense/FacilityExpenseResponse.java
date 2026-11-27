package com.uit.dormmanagement.dtos.response.expense;

import com.uit.dormmanagement.enums.ExpenseType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record FacilityExpenseResponse(
    UUID id,
    ExpenseType expenseType,
    String title,
    BigDecimal amount,
    LocalDate expenseDate,
    String invoiceCode,
    String notes,
    UUID recordedByStaffId,
    String recordedByStaffName,
    FacilityExpenseReceiptResponse receipt,
    Instant createdAt,
    Instant updatedAt) {}
