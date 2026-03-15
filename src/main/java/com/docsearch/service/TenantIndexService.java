package com.docsearch.service;

import com.docsearch.document.DocumentEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

/**
 * Single place in the codebase that constructs index names.
 * All services call resolveIndex(tenantId) — never build index names themselves.
 *
 * Naming: docs_{tenantId}
 * Creates index lazily on first request for a new tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantIndexService {

    private final ElasticsearchOperations esOps;
    private static final String PREFIX = "docs_";

    public IndexCoordinates resolveIndex(String tenantId) {
        String indexName = PREFIX + tenantId;
        IndexCoordinates coords = IndexCoordinates.of(indexName);

        IndexOperations indexOps = esOps.indexOps(coords);
        if (!indexOps.exists()) {
            log.info("Creating index for new tenant: {}", indexName);
            indexOps.create();
            indexOps.putMapping(esOps.indexOps(DocumentEntity.class).createMapping());
            log.info("Index {} created with mappings", indexName);
        }
        return coords;
    }

    public String resolveIndexName(String tenantId) {
        return PREFIX + tenantId;
    }
}
