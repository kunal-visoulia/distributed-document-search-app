package com.docsearch.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResponse {
    private String query;
    private long totalHits;
    private long tookMs;
    private List<SearchHit> results;
    private Map<String, Map<String, Long>> facets;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchHit {
        private String id;
        private String title;
        private float score;
        private Map<String, List<String>> highlights;
        private List<String> tags;
        private String docType;
        private Instant createdAt;
    }
}