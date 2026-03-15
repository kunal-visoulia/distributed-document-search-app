package com.docsearch.controller;

import com.docsearch.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
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
@Tag(name = "Health", description = "Service health check")
public class HealthController {

    private final RestHighLevelClient osClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Operation(summary = "Health check with dependency status")
    @GetMapping("/api/v1/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> deps = new LinkedHashMap<>();
        boolean allHealthy = true;

        deps.put("opensearch", checkOpenSearch());
        if (!"healthy".equals(((Map<?, ?>) deps.get("opensearch")).get("status")))
            allHealthy = false;

        deps.put("redis", checkRedis());
        if (!"healthy".equals(((Map<?, ?>) deps.get("redis")).get("status")))
            allHealthy = false;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceStatus", allHealthy ? "healthy" : "degraded");
        data.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        data.put("dependencies", deps);

        if (allHealthy) {
            return ResponseEntity.ok(ApiResponse.success("All dependencies healthy", data));
        } else {
            return ResponseEntity.status(503)
                    .body(ApiResponse.success("Service degraded", data));
        }
    }

    private Map<String, Object> checkOpenSearch() {
        long start = System.currentTimeMillis();
        try {
            ClusterHealthResponse health = osClient.cluster()
                    .health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
            return Map.of(
                    "status", "healthy",
                    "latencyMs", System.currentTimeMillis() - start,
                    "cluster", health.getStatus().name().toLowerCase());
        } catch (Exception e) {
            return Map.of(
                    "status", "unhealthy",
                    "latencyMs", System.currentTimeMillis() - start,
                    "details", e.getMessage() != null ? e.getMessage() : "Connection failed");
        }
    }

    private Map<String, Object> checkRedis() {
        long start = System.currentTimeMillis();
        try {
            redisTemplate.getConnectionFactory().getConnection().commands().ping();
            return Map.of(
                    "status", "healthy",
                    "latencyMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            return Map.of(
                    "status", "unhealthy",
                    "latencyMs", System.currentTimeMillis() - start,
                    "details", e.getMessage() != null ? e.getMessage() : "Connection failed");
        }
    }
}