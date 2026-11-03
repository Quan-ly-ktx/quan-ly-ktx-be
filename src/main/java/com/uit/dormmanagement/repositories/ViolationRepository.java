package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Violation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ViolationRepository extends JpaRepository<Violation, UUID> {
  Optional<Violation> findByViolationCode(String violationCode);

  boolean existsByViolationCode(String violationCode);

  List<Violation> findByTenantId(UUID tenantId);
}
