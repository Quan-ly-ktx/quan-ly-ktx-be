package com.uit.dormmanagement.services;

import org.springframework.core.io.Resource;

public record StoredObjectContent(Resource resource, String contentType, long contentLength) {}
