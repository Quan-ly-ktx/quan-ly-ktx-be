package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoomType {
  FOUR_BEDS("4 giường"),
  SIX_BEDS("6 giường"),
  EIGHT_BEDS("8 giường"),
  SLEEPBOX("Sleepbox");

  private final String displayName;
}
