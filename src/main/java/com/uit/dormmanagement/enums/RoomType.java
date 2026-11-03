package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoomType {
  FOUR_BEDS("4 giường", 4),
  SIX_BEDS("6 giường", 6),
  EIGHT_BEDS("8 giường", 8);

  private final String displayName;
  private final int bedCount;
}
