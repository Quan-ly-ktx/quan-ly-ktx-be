package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResidentStatus {
  ACTIVE("Đang ở"),
  CHECKED_OUT("Đã trả phòng"),
  RESERVED("Bảo lưu");

  private final String displayName;
}
