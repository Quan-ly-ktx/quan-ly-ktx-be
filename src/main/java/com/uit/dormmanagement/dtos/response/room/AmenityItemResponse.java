package com.uit.dormmanagement.dtos.response.room;

import java.util.UUID;

public record AmenityItemResponse(
    UUID amenityId, String code, String name, String category, int quantity) {}
