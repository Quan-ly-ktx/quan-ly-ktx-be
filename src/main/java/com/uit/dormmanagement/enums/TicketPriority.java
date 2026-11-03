package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketPriority {
  URGENT("Khẩn cấp", 0),
  HIGH("Cao", 1),
  NORMAL("Bình thường", 2);

  private final String displayName;
  private final int rank;
}
