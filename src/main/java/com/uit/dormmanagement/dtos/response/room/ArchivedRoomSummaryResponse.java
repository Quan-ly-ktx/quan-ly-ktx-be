package com.uit.dormmanagement.dtos.response.room;

import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ArchivedRoomSummaryResponse(
    UUID id,
    String roomNumber,
    int floor,
    RoomType roomType,
    GenderPolicy genderPolicy,
    int totalBeds,
    int occupiedBeds,
    int availableBeds,
    int maintenanceBeds,
    BigDecimal basePrice,
    List<AmenityItemResponse> amenities,
    Instant archivedAt) {}
