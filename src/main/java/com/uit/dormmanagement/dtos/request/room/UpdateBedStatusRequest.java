package com.uit.dormmanagement.dtos.request.room;

import com.uit.dormmanagement.enums.BedStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateBedStatusRequest(
    @NotNull(message = "Trạng thái giường không được để trống") BedStatus status) {}
