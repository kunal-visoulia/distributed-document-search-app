package com.docsearch.controller;

import com.docsearch.dto.*;
import com.docsearch.exception.ErrorCode;
import com.docsearch.service.DocumentService;
import com.docsearch.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document CRUD and search operations")
public class DocumentController {

    private final DocumentService documentService;
    private final SearchService searchService;

    @Operation(summary = "Index a new document")
    @PostMapping("/documents")
    public ResponseEntity<ApiResponse<DocumentResponse>> create(
            @Valid @RequestBody CreateDocumentRequest request,
            @Parameter(hidden = true) HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        DocumentResponse response = documentService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document indexed successfully", response));
    }

    @Operation(summary = "Full-text search with fuzzy matching, highlighting, and facets")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestParam("q") String query,
            @RequestParam("tenant") String tenantParam,
            @Parameter(hidden = true) HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);

        if (!tenantId.equals(tenantParam)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure(
                            ErrorCode.TENANT_MISMATCH.getDescription(),
                            List.of(ApiResponse.ApiError.builder()
                                    .code(ErrorCode.TENANT_MISMATCH.getCode())
                                    .description(ErrorCode.TENANT_MISMATCH.getDescription())
                                    .build())));
        }

        SearchResponse response = searchService.search(tenantId, query);
        return ResponseEntity.ok(ApiResponse.success("Search completed", response));
    }

    @Operation(summary = "Retrieve document by ID")
    @GetMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<DocumentResponse>> getById(
            @PathVariable String id,
            @Parameter(hidden = true) HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        DocumentResponse response = documentService.getById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Document retrieved", response));
    }

    @Operation(summary = "Delete a document")
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String id,
            @Parameter(hidden = true) HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        documentService.delete(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }

    private String getTenantId(HttpServletRequest req) {
        return (String) req.getAttribute("tenantId");
    }

    @Operation(summary = "Bulk index multiple documents")
    @PostMapping("/documents/bulk")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> bulkCreate(
            @Valid @RequestBody BulkCreateRequest request,
            @Parameter(hidden = true) HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        List<DocumentResponse> responses = documentService.bulkCreate(tenantId, request.getDocuments());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(responses.size() + " documents indexed", responses));
    }
}