package com.uit.dormmanagement.dtos.request.contract;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record RenewContractRequest(
    @NotNull(message = "Ngày kết thúc mới không được để trống") LocalDate newEndDate) {}
