package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest;
import com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest;
import com.uit.dormmanagement.dtos.response.auth.OneTimePasswordResponse;
import com.uit.dormmanagement.dtos.response.auth.UserProfileResponse;
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

  TenantDetailResponse updateTenantAsManager(
      UUID managerUserId, UUID tenantId, ManagerUpdateTenantRequest request);

  UserProfileResponse updateOwnContact(UUID tenantUserId, TenantContactUpdateRequest request);

  OneTimePasswordResponse resetTenantPasswordAsManager(UUID managerUserId, UUID tenantId);
}
