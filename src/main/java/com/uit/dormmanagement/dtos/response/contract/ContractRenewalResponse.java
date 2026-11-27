package com.uit.dormmanagement.dtos.response.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ContractRenewalResponse(
    UUID id,
    Integer sequenceNo,
    LocalDate signedOn,
    LocalDate previousEndOn,
    LocalDate effectiveFrom,
    LocalDate newEndOn,
    BigDecimal monthlyRent,
    Instant createdAt) {}
