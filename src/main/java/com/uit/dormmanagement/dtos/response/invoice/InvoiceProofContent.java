package com.uit.dormmanagement.dtos.response.invoice;

import org.springframework.core.io.Resource;

public record InvoiceProofContent(
    Resource resource, String contentType, long contentLength, String downloadFilename) {}
