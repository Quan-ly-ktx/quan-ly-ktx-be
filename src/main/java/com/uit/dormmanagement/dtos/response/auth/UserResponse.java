package com.uit.dormmanagement.dtos.response.auth;

import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import java.util.UUID;

public record UserResponse(UUID id, String email, UserRole role, UserStatus status) {

  public static UserResponse fromEntity(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getRole(),
        user.getStatus());
  }
}
