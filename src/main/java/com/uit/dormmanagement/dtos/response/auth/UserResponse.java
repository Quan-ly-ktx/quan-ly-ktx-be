package com.uit.dormmanagement.dtos.response.auth;

import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import java.util.UUID;

public record UserResponse(
    UUID id, String username, String email, String phone, UserRole role, UserStatus status) {

  public static UserResponse fromEntity(User user) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getPhone(),
        user.getRole(),
        user.getStatus());
  }
}
