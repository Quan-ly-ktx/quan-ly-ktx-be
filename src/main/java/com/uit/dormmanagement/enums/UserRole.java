package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
  MANAGER("Quản lý"),
  TECHNICIAN("Kỹ thuật viên"),
  TENANT("Khách thuê");

  private final String displayName;
}
