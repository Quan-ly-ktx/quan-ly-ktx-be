package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findByTenantId(UUID tenantId);

  List<Notification> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  List<Notification> findByTenantIdAndReadAtIsNull(UUID tenantId);

  long countByTenantIdAndReadAtIsNull(UUID tenantId);

  Optional<Notification> findByDedupeKey(String dedupeKey);

  boolean existsByDedupeKey(String dedupeKey);
}
