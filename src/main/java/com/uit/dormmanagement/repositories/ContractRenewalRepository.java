package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.ContractRenewal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRenewalRepository extends JpaRepository<ContractRenewal, UUID> {
  List<ContractRenewal> findByContractId(UUID contractId);

  List<ContractRenewal> findByContractIdOrderBySequenceNoAsc(UUID contractId);

  Optional<ContractRenewal> findFirstByContractIdOrderBySequenceNoDesc(UUID contractId);

  boolean existsByContractIdAndSequenceNo(UUID contractId, Integer sequenceNo);

  @Query(
      """
      SELECT r FROM ContractRenewal r
      WHERE r.contract.id = :contractId
        AND r.effectiveFrom <= :billingPeriod
        AND r.newEndOn >= :billingPeriod
      ORDER BY r.sequenceNo DESC
      """)
  List<ContractRenewal> findCoveringRenewalsOrderBySequenceDesc(
      @Param("contractId") UUID contractId, @Param("billingPeriod") LocalDate billingPeriod);
}
