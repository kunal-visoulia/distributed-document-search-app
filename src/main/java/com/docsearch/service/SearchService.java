package com.docsearch.service;

import com.docsearch.dto.SearchResponse;
import com.docsearch.dto.SearchResponse.SearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Full-text search using OpenSearch RestHighLevelClient directly.
 * <p>
 * Features:
 * - BM25 relevance scoring with title boosted 3x
 * - Fuzzy matching (typo tolerance via fuzziness=AUTO)
 * - Highlighting on title and content
 * - Faceted aggregations on docType and tags
 * - Source filtering (content excluded from results)
 * <p>
 * Uses RestHighLevelClient (not ElasticsearchOperations) because
 * ElasticsearchOperations doesn't support highlighting, aggregations,
 * or custom scoring parameters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final RestHighLevelClient client;
    private final TenantIndexService tenantIndex;
    private final CacheService cache;

    public SearchResponse search(String tenantId, String query) {
        long start = System.currentTimeMillis();

        var cached = cache.getSearchResult(tenantId, query, SearchResponse.class);
        if (cached.isPresent()) {
            SearchResponse cachedResponse = cached.get();
            cachedResponse.setTookMs(System.currentTimeMillis() - start);
            return cachedResponse;
        }
        String indexName = tenantIndex.resolveIndexName(tenantId);

        try {
            tenantIndex.resolveIndex(tenantId);

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            if (query != null && !query.isBlank()) {
                boolQuery.must(QueryBuilders.multiMatchQuery(query, "title", "content")
                        .field("title", 3.0f)
                        .fuzziness("AUTO")
                        .prefixLength(2));
            } else {
                boolQuery.must(QueryBuilders.matchAllQuery());
            }

            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                    .query(boolQuery)
                    .size(20)
                    .highlighter(new HighlightBuilder()
                            .field("title", 150, 1)
                            .field("content", 200, 3)
                            .preTags("<em>")
                            .postTags("</em>"))
                    .aggregation(AggregationBuilders.terms("doc_types").field("docType").size(20))
                    .aggregation(AggregationBuilders.terms("tags").field("tags").size(50))
                    .fetchSource(null, new String[]{"content"});

            SearchRequest searchRequest = new SearchRequest(indexName).source(sourceBuilder);

            org.opensearch.action.search.SearchResponse osResponse =
                    client.search(searchRequest, RequestOptions.DEFAULT);

            List<SearchHit> hits = new ArrayList<>();
            for (org.opensearch.search.SearchHit hit : osResponse.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();

                Map<String, List<String>> highlights = new HashMap<>();
                if (hit.getHighlightFields() != null) {
                    hit.getHighlightFields().forEach((field, highlight) -> {
                        List<String> fragments = new ArrayList<>();
                        for (var fragment : highlight.getFragments()) {
                            fragments.add(fragment.string());
                        }
                        highlights.put(field, fragments);
                    });
                }

                @SuppressWarnings("unchecked")
                List<String> tags = source.get("tags") != null
                        ? (List<String>) source.get("tags") : null;

                Instant createdAt = null;
                if (source.get("createdAt") != null) {
                    createdAt = Instant.ofEpochMilli(Long.parseLong(source.get("createdAt").toString()));
                }

                hits.add(SearchHit.builder()
                        .id(hit.getId())
                        .title(source.get("title") != null ? source.get("title").toString() : null)
                        .score(hit.getScore())
                        .highlights(highlights)
                        .tags(tags)
                        .docType(source.get("docType") != null ? source.get("docType").toString() : null)
                        .createdAt(createdAt)
                        .build());
            }

            Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
            facets.put("docType", extractBuckets(osResponse, "doc_types"));
            facets.put("tags", extractBuckets(osResponse, "tags"));

            long totalHits = osResponse.getHits().getTotalHits() != null
                    ? osResponse.getHits().getTotalHits().value : 0;

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
            log.error("Search failed for tenant={} query='{}': {}", tenantId, query, e.getMessage());
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Long> extractBuckets(
            org.opensearch.action.search.SearchResponse response, String aggName) {
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            Terms agg = response.getAggregations().get(aggName);
            if (agg != null) {
                agg.getBuckets().forEach(bucket ->
                        result.put(bucket.getKeyAsString(), bucket.getDocCount()));
            }
        } catch (Exception e) {
            log.warn("Failed to extract aggregation '{}': {}", aggName, e.getMessage());
        }
        return result;
    }
}