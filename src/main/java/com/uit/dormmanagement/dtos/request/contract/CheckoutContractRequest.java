package com.uit.dormmanagement.dtos.request.contract;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.uit.dormmanagement.enums.BedStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CheckoutContractRequest(
    @NotNull(message = "Phải chọn trạng thái giường sau bàn giao") @JsonAlias("bedStatus")
        BedStatus bedStatusAfterCheckout,
    @NotNull(message = "Phải xác nhận đã hoàn tất bàn giao phòng")
        @AssertTrue(message = "Phải xác nhận đã hoàn tất bàn giao phòng")
        Boolean handoverConfirmed) {}
