package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.StaffProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
  Optional<StaffProfile> findByUserId(UUID userId);

  List<StaffProfile> findByActiveTrue();
}
