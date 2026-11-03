package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BedStatus {
  AVAILABLE("Sẵn sàng"),
  OCCUPIED("Đang thuê"),
  MAINTENANCE("Bảo trì");

  private final String displayName;
}
