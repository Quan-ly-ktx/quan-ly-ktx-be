package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffProfileRepository
    extends JpaRepository<StaffProfile, UUID>, JpaSpecificationExecutor<StaffProfile> {
  Optional<StaffProfile> findByUserId(UUID userId);

  List<StaffProfile> findByUser_Status(UserStatus status);

  Optional<StaffProfile> findByIdAndUser_Role(UUID id, UserRole role);
}
