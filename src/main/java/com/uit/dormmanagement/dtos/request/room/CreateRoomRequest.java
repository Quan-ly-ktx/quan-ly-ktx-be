package com.uit.dormmanagement.dtos.request.room;

import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;

public record CreateRoomRequest(
    @NotBlank(message = "Số phòng không được để trống")
        @Pattern(
            regexp = "^P\\.[0-9]{3,4}$",
            message = "Số phòng phải đúng định dạng, ví dụ: P.302, P.1001")
        String roomNumber,
    @NotNull(message = "Tầng không được để trống")
        @Min(value = 1, message = "Tầng phải từ 1 đến 10")
        @Max(value = 10, message = "Tầng phải từ 1 đến 10")
        Integer floor,
    @NotNull(message = "Loại phòng không được để trống") RoomType roomType,
    @NotNull(message = "Chính sách giới tính không được để trống") GenderPolicy genderPolicy,
    @NotNull(message = "Đơn giá thuê tiêu chuẩn không được để trống")
        @DecimalMin(value = "0.0", message = "Đơn giá thuê phòng không được âm")
        BigDecimal basePrice,
    List<@Valid RoomAmenityRequest> amenities) {}
