package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.enums.ResidentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
  Optional<Tenant> findByTenantCode(String tenantCode);

  Optional<Tenant> findByIdCardNumber(String idCardNumber);

  Optional<Tenant> findByUser_Email(String email);

  Optional<Tenant> findByPhone(String phone);

  Optional<Tenant> findByUserId(UUID userId);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Tenant t WHERE t.id = :id")
  Optional<Tenant> findByIdForUpdate(@Param("id") UUID id);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Tenant t WHERE t.user.id = :userId")
  Optional<Tenant> findByUserIdForUpdate(@Param("userId") UUID userId);

  boolean existsByTenantCode(String tenantCode);

  boolean existsByIdCardNumber(String idCardNumber);

  boolean existsByIdCardNumberAndResidentStatus(String idCardNumber, ResidentStatus residentStatus);

  List<Tenant> findByResidentStatus(ResidentStatus residentStatus);

  long countByTenantCodeStartingWith(String prefix);

  @Query(
      value =
          """
          SELECT COALESCE(MAX(CAST(SUBSTRING(t.tenant_code FROM 4) AS integer)), 0) + 1
          FROM tenants t
          WHERE t.tenant_code ~ '^NT-[0-9]+$'
          """,
      nativeQuery = true)
  long findNextTenantCodeSequence();

  @Query(
      value =
          """
          SELECT DISTINCT t FROM Tenant t
          LEFT JOIN Contract c ON c.tenant.id = t.id AND c.status = 'ACTIVE'
          WHERE (:roomId IS NULL OR c.bed.room.id = :roomId)
            AND (:status IS NULL OR t.residentStatus = :status)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(t.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.tenantCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.idCardNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.user.email) LIKE LOWER(CONCAT('%', :search, '%')))
          """,
      countQuery =
          """
          SELECT COUNT(DISTINCT t) FROM Tenant t
          LEFT JOIN Contract c ON c.tenant.id = t.id AND c.status = 'ACTIVE'
          WHERE (:roomId IS NULL OR c.bed.room.id = :roomId)
            AND (:status IS NULL OR t.residentStatus = :status)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(t.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.tenantCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.idCardNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(t.user.email) LIKE LOWER(CONCAT('%', :search, '%')))
          """)
  Page<Tenant> findActiveTenantsWithFilters(
      @Param("search") String search,
      @Param("roomId") UUID roomId,
      @Param("status") ResidentStatus status,
      Pageable pageable);
}
