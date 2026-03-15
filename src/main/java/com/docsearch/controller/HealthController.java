package com.docsearch.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final RestHighLevelClient esClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/api/v1/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> deps = new LinkedHashMap<>();
        boolean allHealthy = true;

        // OpenSearch
        deps.put("opensearch", checkOpenSearch());
        if (!"healthy".equals(((Map<?, ?>) deps.get("opensearch")).get("status")))
            allHealthy = false;

        // Redis
        deps.put("redis", checkRedis());
        if (!"healthy".equals(((Map<?, ?>) deps.get("redis")).get("status")))
            allHealthy = false;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", allHealthy ? "healthy" : "degraded");
        response.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        response.put("dependencies", deps);

        return allHealthy ? ResponseEntity.ok(response) : ResponseEntity.status(503).body(response);
    }

    private Map<String, Object> checkOpenSearch() {
        long start = System.currentTimeMillis();
        try {
            var health = esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
            return Map.of("status", "healthy",
                    "latencyMs", System.currentTimeMillis() - start,
                    "cluster", health.getStatus().name().toLowerCase());
        } catch (Exception e) {
            return Map.of("status", "unhealthy",
                    "latencyMs", System.currentTimeMillis() - start,
                    "details", e.getMessage() != null ? e.getMessage() : "Connection failed");
        }
    }

    private Map<String, Object> checkRedis() {
        long start = System.currentTimeMillis();
        try {
            redisTemplate.getConnectionFactory().getConnection().commands().ping();
            return Map.of("status", "healthy",
                    "latencyMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            return Map.of("status", "unhealthy",
                    "latencyMs", System.currentTimeMillis() - start,
                    "details", e.getMessage() != null ? e.getMessage() : "Connection failed");
        }
    }
}