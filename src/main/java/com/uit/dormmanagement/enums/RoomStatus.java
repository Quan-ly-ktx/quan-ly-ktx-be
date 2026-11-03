package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoomStatus {
  AVAILABLE("Trống"),
  OCCUPIED("Đang ở"),
  MAINTENANCE("Bảo trì");

  private final String displayName;
}
