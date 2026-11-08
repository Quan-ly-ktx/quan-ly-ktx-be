package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContractDocumentCategory {
  ORIGINAL_CONTRACT("Hợp đồng gốc"),
  RENEWAL_ANNEX("Phụ lục gia hạn"),
  CHECKOUT_HANDOVER("Biên bản bàn giao trả phòng"),
  OTHER("Khác");

  private final String displayName;
}
