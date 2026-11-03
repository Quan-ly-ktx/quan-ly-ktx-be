package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VehicleType {
  SCOOTER("Xe tay ga"),
  MANUAL_MOTORCYCLE("Xe số"),
  ELECTRIC_MOTORBIKE("Xe máy điện"),
  ELECTRIC_BICYCLE("Xe đạp điện"),
  BICYCLE("Xe đạp");

  private final String displayName;
}
