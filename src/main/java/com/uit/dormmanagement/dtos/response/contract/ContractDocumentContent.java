package com.uit.dormmanagement.dtos.response.contract;

import org.springframework.core.io.Resource;

public record ContractDocumentContent(
    Resource resource, String contentType, long contentLength, String originalFilename) {}
