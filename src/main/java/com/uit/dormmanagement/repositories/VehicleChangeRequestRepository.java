package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.VehicleChangeRequest;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleChangeRequestRepository
    extends JpaRepository<VehicleChangeRequest, UUID> {
  List<VehicleChangeRequest> findByTenantId(UUID tenantId);

  List<VehicleChangeRequest> findByCurrentVehicleId(UUID vehicleId);

  List<VehicleChangeRequest> findByStatus(VehicleChangeRequestStatus status);

  boolean existsByTenantIdAndStatus(UUID tenantId, VehicleChangeRequestStatus status);

  Optional<VehicleChangeRequest> findFirstByTenantIdAndStatus(
      UUID tenantId, VehicleChangeRequestStatus status);
}
