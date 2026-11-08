package com.uit.dormmanagement.dtos.response.contract;

import com.uit.dormmanagement.enums.ContractDocumentCategory;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ContractDocumentResponse(
    UUID id,
    UUID contractId,
    String fileName,
    String fileUrl,
    String fileType,
    Long fileSize,
    ContractDocumentCategory documentCategory,
    Instant uploadedAt) {}
