package com.uit.dormmanagement.dtos.request.room;

import com.uit.dormmanagement.enums.GenderPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;

public record UpdateRoomRequest(
    @Pattern(
        regexp = "^P\\.[0-9]{3,4}$",
        message = "Số phòng phải đúng định dạng, ví dụ: P.302, P.1001")
    String roomNumber,
    GenderPolicy genderPolicy,
    @NotNull(message = "Đơn giá thuê tiêu chuẩn không được để trống")
        @DecimalMin(value = "0", inclusive = false, message = "Đơn giá thuê phòng phải lớn hơn 0")
        BigDecimal basePrice,
    List<@Valid RoomAmenityRequest> amenities) {
  public UpdateRoomRequest(
      GenderPolicy genderPolicy, BigDecimal basePrice, List<@Valid RoomAmenityRequest> amenities) {
    this(null, genderPolicy, basePrice, amenities);
  }
}
