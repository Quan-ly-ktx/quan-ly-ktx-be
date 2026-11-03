package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GenderPolicy {
  MALE("Nam"),
  FEMALE("Nữ");

  private final String displayName;
}
