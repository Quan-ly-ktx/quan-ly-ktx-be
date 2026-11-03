package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.enums.ResidentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
  Optional<Tenant> findByTenantCode(String tenantCode);

  Optional<Tenant> findByIdCardNumber(String idCardNumber);

  Optional<Tenant> findByUserId(UUID userId);

  boolean existsByTenantCode(String tenantCode);

  boolean existsByIdCardNumber(String idCardNumber);

  boolean existsByEmail(String email);

  List<Tenant> findByResidentStatus(ResidentStatus residentStatus);
}
