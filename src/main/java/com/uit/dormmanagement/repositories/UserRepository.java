package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);

  Optional<User> findByPhone(String phone);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByPhone(String phone);

  List<User> findByRole(UserRole role);
}
