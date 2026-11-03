package com.uit.dormmanagement.dtos.request.room;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RoomAmenityRequest(
    @NotNull(message = "ID tiện nghi không được để trống") UUID amenityId,
    @Min(value = 1, message = "Số lượng tiện nghi tối thiểu là 1") int quantity) {}
