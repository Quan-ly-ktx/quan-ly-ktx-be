package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.VehicleStatus;
import java.util.Collection;
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
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
  Optional<Vehicle> findByLicensePlate(String licensePlate);

  Optional<Vehicle> findByCardCode(String cardCode);

  boolean existsByLicensePlate(String licensePlate);

  boolean existsByCardCode(String cardCode);

  List<Vehicle> findByTenantId(UUID tenantId);

  List<Vehicle> findByStatus(VehicleStatus status);

  List<Vehicle> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

  long countByTenantIdAndStatusInAndDeletedAtIsNull(
      UUID tenantId, Collection<VehicleStatus> statuses);

  Optional<Vehicle> findFirstByTenantIdAndStatusInAndDeletedAtIsNull(
      UUID tenantId, Collection<VehicleStatus> statuses);

  boolean existsByLicensePlateAndStatusInAndDeletedAtIsNull(
      String licensePlate, Collection<VehicleStatus> statuses);

  boolean existsByCardCodeAndStatusAndDeletedAtIsNull(String cardCode, VehicleStatus status);

  @Query(
      value =
          """
          SELECT v FROM Vehicle v
          WHERE v.deletedAt IS NULL
            AND (:status IS NULL OR v.status = :status)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(v.licensePlate) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.color) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.cardCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.tenant.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.tenant.phone) LIKE LOWER(CONCAT('%', :search, '%')))
          """,
      countQuery =
          """
          SELECT COUNT(v) FROM Vehicle v
          WHERE v.deletedAt IS NULL
            AND (:status IS NULL OR v.status = :status)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(v.licensePlate) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.color) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.cardCode) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.tenant.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.tenant.phone) LIKE LOWER(CONCAT('%', :search, '%')))
          """)
  Page<Vehicle> findVehiclesWithFilters(
      @Param("search") String search, @Param("status") VehicleStatus status, Pageable pageable);
}
