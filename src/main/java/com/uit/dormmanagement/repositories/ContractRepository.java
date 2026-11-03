package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.enums.ContractStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {
  Optional<Contract> findByContractCode(String contractCode);

  boolean existsByContractCode(String contractCode);

  List<Contract> findByTenantId(UUID tenantId);

  List<Contract> findByBedId(UUID bedId);

  List<Contract> findByStatus(ContractStatus status);

  Optional<Contract> findByBedIdAndStatus(UUID bedId, ContractStatus status);

  Optional<Contract> findByTenantIdAndStatus(UUID tenantId, ContractStatus status);
}
