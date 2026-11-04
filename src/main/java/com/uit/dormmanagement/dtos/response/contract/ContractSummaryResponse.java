package com.uit.dormmanagement.dtos.response.contract;

import com.uit.dormmanagement.enums.ContractStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ContractSummaryResponse(
    UUID id,
    String contractCode,
    UUID tenantId,
    String tenantName,
    String tenantPhone,
    String roomNumber,
    String bedCode,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal monthlyRent,
    BigDecimal depositAmount,
    ContractStatus status) {}
