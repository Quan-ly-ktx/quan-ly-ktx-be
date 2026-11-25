package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.ContractRenewal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRenewalRepository extends JpaRepository<ContractRenewal, UUID> {
  List<ContractRenewal> findByContractId(UUID contractId);

  List<ContractRenewal> findByContractIdOrderBySequenceNoAsc(UUID contractId);

  Optional<ContractRenewal> findFirstByContractIdOrderBySequenceNoDesc(UUID contractId);

  boolean existsByContractIdAndSequenceNo(UUID contractId, Integer sequenceNo);
}
