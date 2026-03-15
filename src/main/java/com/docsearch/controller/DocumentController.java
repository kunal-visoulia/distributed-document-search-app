package com.docsearch.controller;

import com.docsearch.dto.CreateDocumentRequest;
import com.docsearch.dto.DocumentResponse;
import com.docsearch.dto.SearchResponse;
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

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document CRUD and search operations")
public class DocumentController {

    private final DocumentService documentService;
    private final SearchService searchService;

    @Operation(summary = "Index a new document")
    @PostMapping("/documents")
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request,
            @Parameter(hidden = true) HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        DocumentResponse response = documentService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Full-text search with fuzzy matching, highlighting, and facets")
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam("tenant") String tenantParam,
            @Parameter(hidden = true) HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);

        if (!tenantId.equals(tenantParam)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        SearchResponse response = searchService.search(tenantId, query);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Retrieve document by ID")
    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> getById(
            @PathVariable String id,
            @Parameter(hidden = true) HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        DocumentResponse response = documentService.getById(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a document")
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> delete(
            @PathVariable String id,
            @Parameter(hidden = true) HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        DocumentResponse response = documentService.delete(tenantId, id);
        return ResponseEntity.ok(response);
    }

    private String getTenantId(HttpServletRequest req) {
        return (String) req.getAttribute("tenantId");
    }
}