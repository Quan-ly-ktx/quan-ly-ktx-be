package com.uit.dormmanagement.dtos.response.auth;

import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record TechnicianResponse(
    UUID id,
    UUID userId,
    String username,
    String email,
    String phone,
    String fullName,
    boolean active,
    UserStatus userStatus,
    Instant createdAt) {

  public static TechnicianResponse fromEntity(StaffProfile profile) {
    User user = profile.getUser();
    return new TechnicianResponse(
        profile.getId(),
        user != null ? user.getId() : null,
        user != null ? user.getUsername() : null,
        user != null ? user.getEmail() : null,
        profile.getPhone(),
        profile.getFullName(),
        profile.isActive(),
        user != null ? user.getStatus() : null,
        profile.getCreatedAt());
  }
}
