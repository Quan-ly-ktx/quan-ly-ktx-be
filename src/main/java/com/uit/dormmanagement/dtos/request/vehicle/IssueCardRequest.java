package com.uit.dormmanagement.dtos.request.vehicle;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record IssueCardRequest(
    @NotBlank(message = "Mã thẻ xe không được để trống") String cardCode) {}
