package com.uit.dormmanagement.dtos.response.auth;

import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import java.time.LocalDate;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String username,
    String email,
    String phone,
    UserRole role,
    UserStatus status,
    StaffInfo staffProfile,
    TenantInfo tenantProfile) {

  public record StaffInfo(UUID id, String fullName, String phone, boolean active) {}

  public record TenantInfo(
      UUID id,
      String tenantCode,
      String fullName,
      String gender,
      String idCardNumber,
      String phone,
      String email,
      String permanentAddress,
      LocalDate dateOfBirth,
      String emergencyContactName,
      String emergencyContactPhone,
      ResidentStatus residentStatus) {}
}
