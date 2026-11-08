package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.tenant.UpdateTenantRequest;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantSummaryResponse;
import com.uit.dormmanagement.enums.ResidentStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TenantService {

  Page<TenantSummaryResponse> getTenants(
      Pageable pageable, String search, UUID roomId, ResidentStatus status);

  TenantDetailResponse getTenantById(UUID id);

  TenantDetailResponse updateTenant(UUID id, UpdateTenantRequest request);
}
