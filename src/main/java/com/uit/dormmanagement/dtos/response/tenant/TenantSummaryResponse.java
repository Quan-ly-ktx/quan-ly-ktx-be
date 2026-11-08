package com.uit.dormmanagement.dtos.response.tenant;

import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record TenantSummaryResponse(
    UUID id,
    String tenantCode,
    String fullName,
    GenderPolicy gender,
    String idCardNumber,
    String phone,
    String email,
    ResidentStatus residentStatus,
    String roomNumber,
    String bedCode,
    LocalDate contractEndDate) {}
