package com.docsearch.controller;

import com.docsearch.dto.*;
import com.docsearch.service.DocumentService;
import com.docsearch.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final SearchService searchService;

    /**
     * POST /documents — Index a new document.
     *
     * Prototype: sync write to OpenSearch → 201.
     * Production: publish to Kafka → 202 Accepted.
     */
    @PostMapping("/documents")
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request,
            HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        DocumentResponse response = documentService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /search?q={query}&tenant={tenantId} — Search documents.
     *
     * Matches the exact endpoint signature from the assignment.
     * Tenant validated: header must match query param.
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam("tenant") String tenantParam,
            HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);

        // Defense in depth: header tenant must match query param tenant
        if (!tenantId.equals(tenantParam)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        SearchResponse response = searchService.search(tenantId, query);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /documents/{id} — Retrieve document details.
     *
     * Reads from OpenSearch translog (strongly consistent).
     */
    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> getById(
            @PathVariable String id,
            HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        DocumentResponse response = documentService.getById(tenantId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /documents/{id} — Remove a document.
     *
     * Prototype: sync delete from OpenSearch → 200.
     * Production: publish to Kafka → 202 Accepted.
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> delete(
            @PathVariable String id,
            HttpServletRequest httpReq) {
        String tenantId = getTenantId(httpReq);
        DocumentResponse response = documentService.delete(tenantId, id);
        return ResponseEntity.ok(response);
    }

    private String getTenantId(HttpServletRequest req) {
        return (String) req.getAttribute("tenantId");
    }
}
