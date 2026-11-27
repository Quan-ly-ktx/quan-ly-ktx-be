package com.uit.dormmanagement.dtos.response.invoice;

import java.time.Instant;
import lombok.Builder;

@Builder
public record InvoiceProofResponse(boolean available, Instant submittedAt, String contentUrl) {}
