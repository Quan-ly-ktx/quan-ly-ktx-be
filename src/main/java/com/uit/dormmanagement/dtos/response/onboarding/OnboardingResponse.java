package com.uit.dormmanagement.dtos.response.onboarding;

import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import lombok.Builder;

@Builder
public record OnboardingResponse(
    TenantDetailResponse tenant, ContractDetailResponse contract, GeneratedAccountInfo account) {}
