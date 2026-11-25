package com.uit.dormmanagement.dtos.response.vehicle;

import org.springframework.core.io.Resource;

public record VehicleEvidenceContent(
    Resource resource,
    String contentType,
    long contentLength
) {}
