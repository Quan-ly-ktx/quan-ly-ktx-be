package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Amenity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmenityRepository extends JpaRepository<Amenity, UUID> {
  Optional<Amenity> findByCode(String code);

  boolean existsByCode(String code);
}
