package com.docsearch.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    OPENSEARCH_ERROR("DS001", "OpenSearch operation failed"),
    INVALID_TENANT("DS002", "Invalid or missing tenant identifier"),
    DOCUMENT_NOT_FOUND("DS003", "Document does not exist in this tenant's index"),
    VALIDATION_ERROR("DS004", "Request validation failed"),
    RATE_LIMIT_EXCEEDED("DS005", "Too many requests for this tenant"),
    TENANT_MISMATCH("DS006", "X-Tenant-Id header must match tenant query parameter"),
    INTERNAL_ERROR("DS007", "An unexpected error occurred");

    private final String code;
    private final String description;
}