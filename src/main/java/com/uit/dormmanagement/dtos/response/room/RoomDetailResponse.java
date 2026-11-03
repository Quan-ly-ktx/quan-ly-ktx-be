package com.uit.dormmanagement.dtos.response.room;

import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RoomDetailResponse(
    UUID id,
    String roomNumber,
    int floor,
    RoomType roomType,
    GenderPolicy genderPolicy,
    int totalBeds,
    int occupiedBeds,
    BigDecimal basePrice,
    List<AmenityItemResponse> amenities,
    List<BedSlotResponse> beds) {}
