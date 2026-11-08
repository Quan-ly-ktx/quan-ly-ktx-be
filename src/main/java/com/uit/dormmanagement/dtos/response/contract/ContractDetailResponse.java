package com.uit.dormmanagement.dtos.response.contract;

import com.uit.dormmanagement.enums.ContractStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ContractDetailResponse(
    UUID id,
    String contractCode,
    UUID tenantId,
    String tenantName,
    String tenantPhone,
    UUID bedId,
    String bedCode,
    UUID roomId,
    String roomNumber,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal monthlyRent,
    BigDecimal depositAmount,
    ContractStatus status,
    String contractDocumentUrl,
    Instant signedAt,
    Instant createdAt,
    List<ContractDocumentResponse> documents) {}
