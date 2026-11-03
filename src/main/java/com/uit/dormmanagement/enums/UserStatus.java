package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
  ACTIVE("Hoạt động"),
  INACTIVE("Tạm dừng"),
  LOCKED("Đã khóa");

  private final String displayName;
}
