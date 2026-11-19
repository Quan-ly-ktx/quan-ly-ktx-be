package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import jakarta.persistence.LockModeType;
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
public interface VehicleChangeRequestRepository
    extends JpaRepository<VehicleChangeRequest, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM VehicleChangeRequest r WHERE r.id = :id")
  Optional<VehicleChangeRequest> findByIdForUpdate(@Param("id") UUID id);

  @Query("SELECT r.currentVehicle.id FROM VehicleChangeRequest r WHERE r.id = :id")
  Optional<UUID> findCurrentVehicleIdById(@Param("id") UUID id);

  List<VehicleChangeRequest> findByTenantId(UUID tenantId);

  List<VehicleChangeRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  List<VehicleChangeRequest> findByCurrentVehicleId(UUID vehicleId);

  List<VehicleChangeRequest> findByStatus(VehicleChangeRequestStatus status);

  boolean existsByTenantIdAndStatus(UUID tenantId, VehicleChangeRequestStatus status);

  boolean existsByCurrentVehicleIdAndStatus(
      UUID currentVehicleId, VehicleChangeRequestStatus status);

  Optional<VehicleChangeRequest> findFirstByTenantIdAndStatus(
      UUID tenantId, VehicleChangeRequestStatus status);

  @Query(
      value =
          """
          SELECT r FROM VehicleChangeRequest r
          WHERE (:status IS NULL OR r.status = :status)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(r.proposedLicensePlate) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.proposedBrand) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.proposedColor) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.currentVehicle.licensePlate) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.tenant.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.tenant.phone) LIKE LOWER(CONCAT('%', :search, '%')))
          """,
      countQuery =
          """
          SELECT COUNT(r) FROM VehicleChangeRequest r
          WHERE (:status IS NULL OR r.status = :status)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(r.proposedLicensePlate) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.proposedBrand) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.proposedColor) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.currentVehicle.licensePlate) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.tenant.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(r.tenant.phone) LIKE LOWER(CONCAT('%', :search, '%')))
          """)
  Page<VehicleChangeRequest> findRequestsWithFilters(
      @Param("search") String search,
      @Param("status") VehicleChangeRequestStatus status,
      Pageable pageable);
}
