package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.enums.ContractStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Contract c WHERE c.id = :id")
  Optional<Contract> findByIdForUpdate(@Param("id") UUID id);

  Optional<Contract> findByContractCode(String contractCode);

  boolean existsByContractCode(String contractCode);

  List<Contract> findByTenantId(UUID tenantId);

  List<Contract> findByBedId(UUID bedId);

  List<Contract> findByStatus(ContractStatus status);

  Optional<Contract> findByBedIdAndStatus(UUID bedId, ContractStatus status);

  Optional<Contract> findByTenantIdAndStatus(UUID tenantId, ContractStatus status);

  boolean existsByBedIdAndStatus(UUID bedId, ContractStatus status);

  boolean existsByTenantIdAndStatus(UUID tenantId, ContractStatus status);

  boolean existsByBedIdAndStatusIn(UUID bedId, Collection<ContractStatus> statuses);

  boolean existsByBed_Room_IdAndStatusIn(UUID roomId, Collection<ContractStatus> statuses);

  boolean existsByBed_Room_Id(UUID roomId);

  boolean existsByBed_IdIn(Collection<UUID> bedIds);

  List<Contract> findByBedIdInAndStatusIn(
      Collection<UUID> bedIds, Collection<ContractStatus> statuses);

  Optional<Contract> findFirstByBedIdAndStatusIn(UUID bedId, Collection<ContractStatus> statuses);

  Optional<Contract> findFirstByTenantIdAndStatusIn(
      UUID tenantId, Collection<ContractStatus> statuses);

  List<Contract> findAllByStatusAndOriginalEndOnLessThanEqual(
      ContractStatus status, LocalDate thresholdDate);

  List<Contract> findByTenantIdOrderByStartOnDescCreatedAtDesc(UUID tenantId);

  @Query(
      """
      SELECT c FROM Contract c
      WHERE c.status = :status
        AND COALESCE(
              (SELECT MAX(r.newEndOn) FROM ContractRenewal r WHERE r.contract = c),
              c.originalEndOn) <= :thresholdDate
      ORDER BY c.startOn ASC, c.id ASC
      """)
  List<Contract> findAllByStatusAndEffectiveEndOnLessThanEqual(
      @Param("status") ContractStatus status, @Param("thresholdDate") LocalDate thresholdDate);

  long countByContractCodeStartingWith(String prefix);

  @Query(
      value =
          """
          SELECT COALESCE(MAX(CAST(SUBSTRING(c.contract_code FROM 9) AS integer)), 0) + 1
          FROM contracts c
          WHERE c.contract_code ~ ('^HD-' || :year || '-[0-9]+$')
          """,
      nativeQuery = true)
  long findNextContractCodeSequence(@Param("year") int year);

  @Query(
      value =
          """
          SELECT c FROM Contract c
          WHERE (:status IS NULL OR c.status = :status)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(c.contractCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.tenant.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.tenant.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.tenant.tenantCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.bed.bedCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.bed.room.roomNumber) LIKE LOWER(CONCAT('%', :search, '%')))
          """,
      countQuery =
          """
          SELECT COUNT(c) FROM Contract c
          WHERE (:status IS NULL OR c.status = :status)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(c.contractCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.tenant.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.tenant.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.tenant.tenantCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.bed.bedCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.bed.room.roomNumber) LIKE LOWER(CONCAT('%', :search, '%')))
          """)
  Page<Contract> findContractsWithFilters(
      @Param("search") String search, @Param("status") ContractStatus status, Pageable pageable);
}
