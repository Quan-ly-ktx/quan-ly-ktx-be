package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.VehicleStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
  Optional<Vehicle> findByLicensePlate(String licensePlate);

  Optional<Vehicle> findByCardCode(String cardCode);

  boolean existsByLicensePlate(String licensePlate);

  boolean existsByCardCode(String cardCode);

  List<Vehicle> findByTenantId(UUID tenantId);

  List<Vehicle> findByStatus(VehicleStatus status);
}
