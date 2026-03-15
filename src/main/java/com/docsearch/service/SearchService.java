package com.docsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.docsearch.document.DocumentEntity;
import com.docsearch.dto.SearchResponse;
import com.docsearch.dto.SearchResponse.SearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Full-text search using OpenSearch (via ES-compatible client).
 *
 * Features:
 * - BM25 relevance scoring with title boosted 3x
 * - Fuzzy matching (typo tolerance)
 * - Highlighting on title and content
 * - Faceted aggregations on docType and tags
 *
 * Uses ElasticsearchClient (not Repository) because Repository
 * doesn't support highlighting, aggregations, or custom scoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final ElasticsearchClient esClient;
    private final TenantIndexService tenantIndex;
    private final CacheService cache;

    public SearchResponse search(String tenantId, String query) {

        // Check cache
        var cached = cache.getSearchResult(tenantId, query, SearchResponse.class);
        if (cached.isPresent()) return cached.get();

        long start = System.currentTimeMillis();
        String indexName = tenantIndex.resolveIndexName(tenantId);

        try {
            // Ensure index exists
            tenantIndex.resolveIndex(tenantId);

            var boolQuery = BoolQuery.of(bq -> {
                if (query != null && !query.isBlank()) {
                    bq.must(m -> m.multiMatch(mm -> mm
                            .query(query)
                            .fields("title^3", "content")
                            .type(TextQueryType.BestFields)
                            .fuzziness("AUTO")
                            .prefixLength(2)
                    ));
                } else {
                    bq.must(m -> m.matchAll(ma -> ma));
                }
                return bq;
            });

            var response = esClient.search(s -> s
                    .index(indexName)
                    .query(q -> q.bool(boolQuery))
                    .size(20)
                    .highlight(h -> h
                            .fields("title", hf -> hf
                                    .preTags("<em>").postTags("</em>")
                                    .numberOfFragments(1))
                            .fields("content", hf -> hf
                                    .preTags("<em>").postTags("</em>")
                                    .fragmentSize(200)
                                    .numberOfFragments(3))
                    )
                    .aggregations("doc_types", a -> a
                            .terms(t -> t.field("docType").size(20)))
                    .aggregations("tags", a -> a
                            .terms(t -> t.field("tags").size(50)))
                    .source(src -> src.filter(f -> f.excludes("content"))),
                    DocumentEntity.class
            );

            List<SearchHit> hits = response.hits().hits().stream()
                    .map(this::mapHit).toList();

            Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
            facets.put("docType", extractBuckets(response, "doc_types"));
            facets.put("tags", extractBuckets(response, "tags"));

            long totalHits = response.hits().total() != null
                    ? response.hits().total().value() : 0;

            SearchResponse result = SearchResponse.builder()
                    .query(query)
                    .totalHits(totalHits)
                    .tookMs(System.currentTimeMillis() - start)
                    .results(hits)
                    .facets(facets)
                    .build();

            cache.putSearchResult(tenantId, query, result);
            return result;

        } catch (Exception e) {
            log.error("Search error tenant={} query='{}': {}", tenantId, query, e.getMessage(), e);
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    private SearchHit mapHit(Hit<DocumentEntity> hit) {
        DocumentEntity src = hit.source();
        Map<String, List<String>> highlights = new HashMap<>();
        if (hit.highlight() != null) hit.highlight().forEach(highlights::put);

        return SearchHit.builder()
                .id(src != null ? src.getId() : hit.id())
                .title(src != null ? src.getTitle() : null)
                .score(hit.score() != null ? hit.score().floatValue() : 0f)
                .highlights(highlights)
                .tags(src != null ? src.getTags() : null)
                .docType(src != null ? src.getDocType() : null)
                .createdAt(src != null ? src.getCreatedAt() : null)
                .build();
    }

    private Map<String, Long> extractBuckets(
            co.elastic.clients.elasticsearch.core.SearchResponse<?> response, String aggName) {
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            var agg = response.aggregations().get(aggName);
            if (agg != null && agg.isSterms()) {
                agg.sterms().buckets().array().forEach(b ->
                        result.put(b.key().stringValue(), b.docCount()));
            }
        } catch (Exception e) {
            log.warn("Aggregation {} extraction failed: {}", aggName, e.getMessage());
        }
        return result;
    }
}
