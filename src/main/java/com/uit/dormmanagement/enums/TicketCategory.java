package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketCategory {
  ELECTRIC("Điện"),
  WATER("Nước"),
  INTERNET("Internet"),
  FURNITURE("Nội thất"),
  AMENITY("Tiện ích"),
  OTHER("Khác");

  private final String displayName;
}
