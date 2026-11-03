package com.uit.dormmanagement.dtos.response.room;

import com.uit.dormmanagement.enums.BedStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record BedSlotResponse(
    UUID id,
    String bedCode,
    String slotKey,
    BigDecimal price,
    BedStatus status,
    OccupantSummaryResponse occupant) {}
