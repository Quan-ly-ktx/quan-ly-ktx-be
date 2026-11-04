package com.uit.dormmanagement.dtos.response.onboarding;

import lombok.Builder;

@Builder
public record GeneratedAccountInfo(
    String username, String email, String phone, String initialPassword, String roomBed) {}
