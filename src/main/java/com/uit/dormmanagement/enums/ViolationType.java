package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ViolationType {
  LATE_RETURN("Về muộn"),
  NOISE("Gây ồn"),
  HYGIENE("Vệ sinh"),
  OUTSIDE_GUEST("Khách ngoài"),
  SMOKING_FIRE("Hút thuốc / cháy nổ"),
  OTHER("Khác");

  private final String displayName;
}
