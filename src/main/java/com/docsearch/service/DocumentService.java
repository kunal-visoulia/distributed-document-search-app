package com.docsearch.service;

import com.docsearch.document.DocumentEntity;
import com.docsearch.dto.CreateDocumentRequest;
import com.docsearch.dto.DocumentResponse;
import com.docsearch.exception.DocumentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Document CRUD operations against OpenSearch.
 *
 * Prototype: writes directly to OpenSearch (synchronous).
 * Production: POST/DELETE publish to Kafka. Consumer (Flink) does the actual write.
 *
 * Uses ElasticsearchOperations (not ElasticsearchRepository) because
 * we need dynamic index resolution per tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final ElasticsearchOperations esOps;
    private final TenantIndexService tenantIndex;
    private final CacheService cache;

    /**
     * Create and index a document.
     * Production: generate ID → publish to Kafka → return 202.
     * Consumer does PUT /docs_{tenant}/_doc/{id} (idempotent upsert).
     */
    public DocumentResponse create(String tenantId, CreateDocumentRequest req) {
        IndexCoordinates index = tenantIndex.resolveIndex(tenantId);

        String docId = "doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .title(req.getTitle())
                .content(req.getContent())
                .docType(req.getDocType())
                .tags(req.getTags())
                .metadata(req.getMetadata())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        esOps.save(doc, index);
        log.info("Indexed document {} in {}", docId, index.getIndexName());

        cache.invalidateSearchCache(tenantId);

        return DocumentResponse.from(doc, tenantId);
    }

    /**
     * Get document by ID. OpenSearch GET reads from translog (strongly consistent).
     */
    public DocumentResponse getById(String tenantId, String docId) {
        var cached = cache.getDocument(tenantId, docId, DocumentResponse.class);
        if (cached.isPresent()) return cached.get();

        IndexCoordinates index = tenantIndex.resolveIndex(tenantId);
        DocumentEntity doc = esOps.get(docId, DocumentEntity.class, index);
        if (doc == null) throw new DocumentNotFoundException(docId);

        DocumentResponse response = DocumentResponse.from(doc, tenantId);
        cache.putDocument(tenantId, docId, response);
        return response;
    }

    /**
     * Delete document. Production: invalidate doc cache → publish to Kafka → 202.
     * Consumer handles actual OpenSearch delete + search cache invalidation.
     */
    public DocumentResponse delete(String tenantId, String docId) {
        IndexCoordinates index = tenantIndex.resolveIndex(tenantId);

        DocumentEntity doc = esOps.get(docId, DocumentEntity.class, index);
        if (doc == null) throw new DocumentNotFoundException(docId);

        esOps.delete(docId, index);
        log.info("Deleted document {} from {}", docId, index.getIndexName());

        cache.invalidateDocument(tenantId, docId);
        cache.invalidateSearchCache(tenantId);

        return DocumentResponse.builder().id(docId).tenantId(tenantId).build();
    }
}