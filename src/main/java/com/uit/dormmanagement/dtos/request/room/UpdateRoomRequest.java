package com.uit.dormmanagement.dtos.request.room;

import com.uit.dormmanagement.enums.GenderPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record UpdateRoomRequest(
    GenderPolicy genderPolicy,
    @NotNull(message = "Đơn giá thuê tiêu chuẩn không được để trống")
        @DecimalMin(value = "0.0", message = "Đơn giá thuê phòng không được âm")
        BigDecimal basePrice,
    List<@Valid RoomAmenityRequest> amenities) {}
